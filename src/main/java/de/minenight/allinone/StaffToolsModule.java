package de.minenight.allinone;

import de.minenightperms.MineNightPerms;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class StaffToolsModule extends MineNightModule implements Listener {
    private static final String PREFIX = "§8[§9MineNight§8] §7";
    private record AlertKey(String engine, UUID target, String check) {}
    private record Alert(String engine, UUID target, String check, String message, int vl, boolean eligible) {}
    private record PendingAlert(String message, int count) {}
    private final java.util.concurrent.ArrayBlockingQueue<Alert> alerts = new java.util.concurrent.ArrayBlockingQueue<>(2048);
    private final Map<AlertKey, PendingAlert> pending = new HashMap<>();
    private final Map<AlertKey, Long> lastSent = new HashMap<>();
    private org.bukkit.scheduler.BukkitTask alertTask;
    private final Set<UUID> staffMode = new HashSet<>();
    private final Set<UUID> vanished = new HashSet<>();
    private final Map<UUID, FlightState> previousFlightStates = new HashMap<>();

    private static final class FlightState {
        final boolean allowFlight;
        final boolean flying;

        FlightState(Player player) {
            this.allowFlight = player.getAllowFlight();
            this.flying = player.isFlying();
        }
    }

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, core());
        alertTask = Bukkit.getScheduler().runTaskTimer(core(), this::flushAlerts, 20L, 20L);
        if (getCommand("staffmode") != null) getCommand("staffmode").setExecutor(this);
        if (getCommand("vanish") != null) getCommand("vanish").setExecutor(this);
    }

    @Override public void onDisable() {
        for (UUID id : new HashSet<>(vanished)) {
            Player player = Bukkit.getPlayer(id);
            if (player != null) {
                for (Player viewer : Bukkit.getOnlinePlayers()) setVanishedVisibility(viewer, player, false);
                restoreFlight(player);
            }
        }
        if (alertTask != null) alertTask.cancel();
        alerts.clear();
        pending.clear();
        lastSent.clear();
        staffMode.clear();
        vanished.clear();
        previousFlightStates.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("This command requires a player.");
            return true;
        }
        switch (command.getName()) {
            case "staffmode" -> {
                if (!staff.hasPermission("minenight.staff.mode")) {
                    staffMode.remove(staff.getUniqueId());
                    staff.sendMessage(PREFIX + "No permission.");
                    return true;
                }
                if (args.length != 0) {
                    staff.sendMessage(PREFIX + "Usage: /staffmode");
                    return true;
                }
                boolean enabled = staffMode.add(staff.getUniqueId());
                if (!enabled) staffMode.remove(staff.getUniqueId());
                staff.sendMessage(PREFIX + (enabled ? "Staff mode enabled." : "Staff mode disabled."));
            }
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
            default -> { return false; }
        }
        return true;
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

    public void queueAlert(String engine, UUID target, String check, String message, int vl, boolean eligible) {
        if (engine == null || target == null || check == null || message == null || message.length() > 16384) return;
        alerts.offer(new Alert(engine, target, check, message, vl, eligible));
    }

    private void flushAlerts() {
        Alert alert;
        for (int drained = 0; drained < 2048 && (alert = alerts.poll()) != null; drained++) {
            if (core().module("moderation") instanceof de.minenight.moderation.MineNightModeration moderation) {
                moderation.evaluateFlag(alert.target, alert.engine, alert.check, alert.vl, alert.eligible);
            }
            AlertKey key = new AlertKey(alert.engine, alert.target, alert.check);
            if (pending.size() >= 2048 && !pending.containsKey(key)) continue;
            PendingAlert old = pending.get(key);
            pending.put(key, new PendingAlert(alert.message, old == null ? 1 : old.count + 1));
        }
        long now = System.nanoTime();
        lastSent.entrySet().removeIf(e -> now - e.getValue() >= 60_000_000_000L);
        var viewers = Bukkit.getOnlinePlayers().stream().filter(this::isStaffMode).toList();
        if (viewers.isEmpty()) {
            pending.clear();
            lastSent.clear();
            return;
        }
        int budget = 20;
        var iterator = pending.entrySet().iterator();
        while (iterator.hasNext() && budget > 0) {
            var entry = iterator.next();
            Long last = lastSent.get(entry.getKey());
            if (last != null && now - last < 3_000_000_000L) continue;
            PendingAlert value = entry.getValue();
            net.kyori.adventure.text.Component message;
            try {
                message = net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().deserialize(value.message);
            } catch (RuntimeException ex) {
                iterator.remove();
                continue;
            }
            if (value.count > 1) message = message.append(net.kyori.adventure.text.Component.text(" (+" + (value.count - 1) + " flags)", net.kyori.adventure.text.format.NamedTextColor.GRAY));
            for (Player viewer : viewers) viewer.sendMessage(message);
            lastSent.put(entry.getKey(), now);
            iterator.remove();
            budget--;
        }
    }

    public boolean isStaffMode(Player player) {
        return player.hasPermission("minenight.staff.mode") && staffMode.contains(player.getUniqueId());
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
        staffMode.remove(id);
        pending.keySet().removeIf(key -> key.target.equals(id));
        lastSent.keySet().removeIf(key -> key.target.equals(id));
        if (core().module("moderation") instanceof de.minenight.moderation.MineNightModeration moderation) moderation.clearAntiCheatFlags(id);
        if (vanished.remove(id)) {
            event.quitMessage(null);
            restoreFlight(event.getPlayer());
        } else {
            previousFlightStates.remove(id);
        }
    }

    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return java.util.List.of();
    }
}
