package de.minenight.allinone;

import java.util.Arrays;
import java.util.Comparator;
import java.util.logging.Level;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.RegisteredListener;

public final class TeleportDiagnostics implements Listener {
    private final MineNightAllInOne plugin;

    public TeleportDiagnostics(MineNightAllInOne plugin) {
        this.plugin = plugin;
    }

    public void dumpRegisteredListeners() {
        RegisteredListener[] listeners = PlayerTeleportEvent.getHandlerList().getRegisteredListeners();
        Arrays.sort(listeners, Comparator
                .comparing((RegisteredListener listener) -> listener.getPriority().ordinal())
                .thenComparing(listener -> listener.getPlugin().getName()));

        plugin.getLogger().info("[TeleportDiag] Registered PlayerTeleportEvent listeners:");
        if (listeners.length == 0) {
            plugin.getLogger().warning("[TeleportDiag] No PlayerTeleportEvent listeners are registered.");
            return;
        }

        for (RegisteredListener listener : listeners) {
            plugin.getLogger().info("[TeleportDiag] plugin=" + listener.getPlugin().getName()
                    + " priority=" + listener.getPriority()
                    + " ignoreCancelled=" + listener.isIgnoringCancelled()
                    + " listener=" + listener.getListener().getClass().getName());
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onLowest(PlayerTeleportEvent event) {
        logStage("LOWEST", event);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = false)
    public void onLow(PlayerTeleportEvent event) {
        logStage("LOW", event);
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = false)
    public void onNormal(PlayerTeleportEvent event) {
        logStage("NORMAL", event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onHigh(PlayerTeleportEvent event) {
        logStage("HIGH", event);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onHighest(PlayerTeleportEvent event) {
        logStage("HIGHEST", event);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onMonitor(PlayerTeleportEvent event) {
        logStage("MONITOR", event);
    }

    private void logStage(String stage, PlayerTeleportEvent event) {
        String destination = event.getTo() == null ? "null" : format(event.getTo());
        String origin = event.getFrom() == null ? "null" : format(event.getFrom());
        plugin.getLogger().info("[TeleportDiag] stage=" + stage
                + " player=" + event.getPlayer().getName()
                + " cause=" + event.getCause()
                + " cancelled=" + event.isCancelled()
                + " from=" + origin
                + " to=" + destination);
    }

    private String format(org.bukkit.Location location) {
        return location.getWorld().getName() + " "
                + String.format(java.util.Locale.ROOT, "%.2f %.2f %.2f",
                location.getX(), location.getY(), location.getZ());
    }
}