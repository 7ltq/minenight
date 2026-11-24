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
        vanished.clear();
        previousFlightStates.clear();
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage("This command requires a player.");
            return true;
        }
        switch (command.getName()) {
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
    }

    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return java.util.List.of();
    }
}
