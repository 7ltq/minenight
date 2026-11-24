package de.minenight.allinone;

import de.minenightperms.MineNightPerms;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.EntityEffect;
import org.bukkit.entity.Entity;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

/** Staff-only utilities. No constantly running scheduler unless a totem check is active. */
public final class StaffToolsModule extends MineNightModule implements Listener {
    private static final String PREFIX = "§8[§9MineNight§8] §7";
    private static final long TOTEM_COOLDOWN_NANOS = 30_000_000_000L;
    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, FlightState> previousFlightStates = new HashMap<>();
    private final Map<UUID, TotemProbe> probes = new HashMap<>();
    private final Map<UUID, Long> lastChecks = new HashMap<>();
    private BukkitTask probeTask;

    private static final class FlightState {
        final boolean allowFlight;
        final boolean flying;

        FlightState(Player player) {
            this.allowFlight = player.getAllowFlight();
            this.flying = player.isFlying();
        }
    }

    private static final class TotemProbe {
        final UUID staff;
        final long startedNanos;
        int ticks = 0;
        TotemProbe(UUID staff) {
            this.staff = staff;
            this.startedNanos = System.nanoTime();
        }
    }

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, core());
        if (getCommand("vanish") != null) getCommand("vanish").setExecutor(this);
        if (getCommand("totemcheck") != null) getCommand("totemcheck").setExecutor(this);
    }

    @Override public void onDisable() {
        if (probeTask != null) probeTask.cancel();
        probeTask = null;
        for (UUID id : new HashSet<>(vanished)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                for (Player viewer : Bukkit.getOnlinePlayers()) setVanishedVisibility(viewer, player, false);
                restoreFlight(player);
            }
        }
        vanished.clear();
        previousFlightStates.clear();
        probes.clear();
        lastChecks.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("This command requires a player.");
            return true;
        }
        switch (command.getName().toLowerCase(Locale.ROOT)) {
            case "vanish" -> {
                if (!staff.hasPermission("minenight.staff.vanish")) {
                    staff.sendMessage(PREFIX + "No permission.");
                    return true;
                }
                if (args.length > 0) {
                    staff.sendMessage(PREFIX + "Usage: /vanish");
                    return true;
                }
                UUID id = staff.getUniqueId();
                boolean nowVanished = vanished.add(id);
                if (nowVanished) {
                    previousFlightStates.put(id, new FlightState(staff));
                    staff.setAllowFlight(true);
                    staff.setFlying(true);
                    staff.setFallDistance(0.0f);
                } else {
                    vanished.remove(id);
                }
                // Vanished players are visible only to server operators, regardless
                // of the separate seevanish permission node.
                for (Player viewer : Bukkit.getOnlinePlayers()) {
                    if (viewer.equals(staff)) continue;
                    setVanishedVisibility(viewer, staff, nowVanished && !viewer.isOp());
                }
                if (!nowVanished) restoreFlight(staff);
                staff.sendMessage(PREFIX + (nowVanished ? "Vanish enabled. Flight enabled; only operators can see you." : "Vanish disabled."));
            }
            case "totemcheck" -> {
                if (!staff.hasPermission("minenight.staff.totemcheck")) {
                    staff.sendMessage(PREFIX + "No permission.");
                    return true;
                }
                if (args.length != 1) {
                    staff.sendMessage(PREFIX + "Usage: /totemcheck <player>");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[0]);
                if (target == null || !target.isOnline()) {
                    staff.sendMessage(PREFIX + "Player not online.");
                    return true;
                }
                if (target.equals(staff)) {
                    staff.sendMessage(PREFIX + "Select another player.");
                    return true;
                }
                UUID id = target.getUniqueId();
                if (probes.containsKey(id)) {
                    staff.sendMessage(PREFIX + "A totem check is already active for that player.");
                    return true;
                }
                long now = System.nanoTime();
                Long previous = lastChecks.get(id);
                if (previous != null && now - previous < TOTEM_COOLDOWN_NANOS) {
                    staff.sendMessage(PREFIX + "Wait before checking this player again.");
                    return true;
                }
                ItemStack offhand = target.getInventory().getItemInOffHand();
                if (offhand.getType() != Material.TOTEM_OF_UNDYING) {
                    staff.sendMessage(PREFIX + "Player must have a totem equipped in the offhand.");
                    return true;
                }
                // Simulated pop: no damage or death; consume exactly one equipped totem.
                if (offhand.getAmount() <= 1) target.getInventory().setItemInOffHand(new ItemStack(Material.AIR));
                else offhand.setAmount(offhand.getAmount() - 1);
                target.playEffect(EntityEffect.TOTEM_RESURRECT);
                lastChecks.put(id, now);
                probes.put(id, new TotemProbe(staff.getUniqueId()));
                staff.sendMessage(PREFIX + "Totem check started for " + target.getName() + ". Measuring offhand re-equip (up to 10 seconds).");
                startTaskIfNeeded();
            }
            default -> { return false; }
        }
        return true;
    }

    private void startTaskIfNeeded() {
        if (probeTask != null || probes.isEmpty()) return;
        probeTask = Bukkit.getScheduler().runTaskTimer(core(), this::pollProbes, 1L, 1L);
    }

    private void pollProbes() {
        var iter = probes.entrySet().iterator();
        while (iter.hasNext()) {
            var entry = iter.next();
            Player target = Bukkit.getPlayer(entry.getKey());
            TotemProbe probe = entry.getValue();
            probe.ticks++;
            if (target == null || !target.isOnline()) {
                notifyStaff(probe.staff, "Totem check ended: player disconnected.");
                iter.remove();
                continue;
            }
            if (target.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
                long nanos = System.nanoTime() - probe.startedNanos;
                notifyStaff(probe.staff, String.format(Locale.ROOT,
                    "%s re-equipped a totem after %d ticks (~%.0f ms elapsed). This is not proof of cheating.",
                    target.getName(), probe.ticks, nanos / 1_000_000.0));
                iter.remove();
            } else if (probe.ticks >= 200) {
                notifyStaff(probe.staff, target.getName() + " did not re-equip a totem within 10 seconds.");
                iter.remove();
            }
        }
        if (probes.isEmpty() && probeTask != null) {
            probeTask.cancel();
            probeTask = null;
        }
    }

    private void notifyStaff(UUID staffId, String text) {
        Player staff = Bukkit.getPlayer(staffId);
        if (staff != null) staff.sendMessage(PREFIX + text);
        getLogger().info("[TotemCheck] " + text);
    }

    private void setVanishedVisibility(Player viewer, Player target, boolean hide) {
        if (hide) {
            viewer.hidePlayer(core(), target);
            for (Entity passenger : target.getPassengers()) {
                viewer.hideEntity(core(), passenger);
            }
            setNameTagVisible(viewer, target, false);
        } else {
            viewer.showPlayer(core(), target);
            for (Entity passenger : target.getPassengers()) {
                viewer.showEntity(core(), passenger);
            }
            setNameTagVisible(viewer, target, true);
        }
    }

    private void setNameTagVisible(Player viewer, Player target, boolean visible) {
        MineNightModule module = core().module("perms");
        if (module instanceof MineNightPerms perms) {
            perms.setNameTagVisible(viewer, target.getUniqueId(), visible);
        }
    }

    public boolean isVanished(UUID playerId) {
        return vanished.contains(playerId);
    }

    private void restoreFlight(Player player) {
        FlightState previous = previousFlightStates.remove(player.getUniqueId());
        if (previous == null) return;

        // Do not strip flight granted by Creative/Spectator mode while the player
        // was vanished. In Survival/Adventure, restore the exact prior state.
        boolean creativeFlight = player.getGameMode() == GameMode.CREATIVE
            || player.getGameMode() == GameMode.SPECTATOR;
        if (creativeFlight) {
            player.setAllowFlight(true);
            if (previous.flying) player.setFlying(true);
            return;
        }
        player.setFlying(false);
        player.setAllowFlight(previous.allowFlight);
        if (previous.allowFlight && previous.flying) player.setFlying(true);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player viewer = event.getPlayer();
        for (UUID id : vanished) {
            Player hidden = Bukkit.getPlayer(id);
            if (hidden != null) {
                setVanishedVisibility(viewer, hidden, !viewer.isOp());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        if (vanished.remove(id)) {
            event.quitMessage(null);
            restoreFlight(event.getPlayer());
        } else {
            previousFlightStates.remove(id);
        }
        TotemProbe probe = probes.remove(id);
        if (probe != null) notifyStaff(probe.staff, "Totem check ended: player disconnected.");
        lastChecks.remove(id);
    }

    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("totemcheck") && args.length == 1 && sender.hasPermission("minenight.staff.totemcheck")) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName)
                .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return java.util.List.of();
    }
}
