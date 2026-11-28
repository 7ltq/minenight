package de.minenight.playeroptimizer;

import de.minenight.allinone.MineNightModule;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

public final class MineNightPlayerOptimizer extends MineNightModule implements Listener {
    private final Map<UUID, Integer> originalViewDistances = new HashMap<>();
    private BukkitTask task;
    private int activeDistance = -1;
    private double lastMspt = 0;
    private int slowSamples = 0;
    private int fastSamples = 0;

    @Override public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, core());
        start();
    }

    private void start() {
        if (task != null) task.cancel();
        task = null;
        restoreAll();
        slowSamples = 0;
        fastSamples = 0;
        activeDistance = -1;
        if (!getConfig().getBoolean("enabled", true)) return;
        int seconds = clamp(getConfig().getInt("evaluation-interval-seconds", 20), 5, 120);
        task = Bukkit.getScheduler().runTaskTimer(core(), this::evaluate, 40L, seconds * 20L);
    }

    private void evaluate() {
        if (!getConfig().getBoolean("enabled", true)) return;
        double mspt = Bukkit.getAverageTickTime();
        lastMspt = mspt;
        if (!Double.isFinite(mspt) || mspt <= 0) return;
        int min = clamp(getConfig().getInt("view-distance.minimum", 5), 2, 16);
        int max = clamp(getConfig().getInt("view-distance.maximum", 8), min, 32);
        double reduceAt = getConfig().getDouble("thresholds.reduce-above-mspt", 42.0);
        double restoreAt = getConfig().getDouble("thresholds.restore-below-mspt", 32.0);
        int samples = clamp(getConfig().getInt("thresholds.consecutive-samples", 3), 1, 15);
        if (activeDistance < 0) activeDistance = max;
        if (mspt > reduceAt && activeDistance > min) {
            slowSamples++;
            fastSamples = 0;
            if (slowSamples >= samples) {
                activeDistance--;
                slowSamples = 0;
            }
        } else if (mspt < restoreAt && activeDistance < max) {
            fastSamples++;
            slowSamples = 0;
            if (fastSamples >= samples) {
                activeDistance++;
                fastSamples = 0;
            }
        } else {
            slowSamples = 0;
            fastSamples = 0;
        }
        for (Player player : Bukkit.getOnlinePlayers()) apply(player);
    }

    private void apply(Player player) {
        if (de.minenight.tiertest.TierTestModule.locked(player)) return;
        if (activeDistance < 0 || !getConfig().getBoolean("enabled", true)) return;
        int current = player.getViewDistance();
        originalViewDistances.putIfAbsent(player.getUniqueId(), current);
        int initial = originalViewDistances.get(player.getUniqueId());
        int target = Math.min(initial > 0 ? initial : 32, activeDistance);
        if (target < 2) target = 2;
        if (current != target) player.setViewDistance(target);
    }

    private void restoreAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (de.minenight.tiertest.TierTestModule.locked(player)) continue;
            Integer original = originalViewDistances.get(player.getUniqueId());
            if (original != null && original >= 2 && player.getViewDistance() != original) {
                player.setViewDistance(original);
            }
        }
        originalViewDistances.clear();
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (activeDistance >= 0) Bukkit.getScheduler().runTaskLater(core(), () -> {
            if (event.getPlayer().isOnline()) apply(event.getPlayer());
        }, 20L);
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) {
        originalViewDistances.remove(event.getPlayer().getUniqueId());
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minenight.playeroptimizer.admin")) {
            sender.sendMessage("You do not have permission to use this command.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            start();
            sender.sendMessage("MineNight PlayerOptimizer configuration reloaded.");
            return true;
        }
        sender.sendMessage("MineNight PlayerOptimizer: " + (task != null ? "enabled" : "disabled"));
        sender.sendMessage(String.format(Locale.ROOT, "Average MSPT: %.2f | Target view distance: %s | Players: %d", lastMspt, activeDistance < 0 ? "default" : activeDistance, Bukkit.getOnlinePlayers().size()));
        sender.sendMessage("Only chunk view distance is adjusted. Simulation distance, PvP and packets are unchanged.");
        return true;
    }

    @Override public java.util.List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        return args.length == 1 && sender.hasPermission("minenight.playeroptimizer.admin") && "reload".startsWith(args[0].toLowerCase(Locale.ROOT)) ? java.util.List.of("reload") : java.util.List.of();
    }

    @Override public void onDisable() {
        if (task != null) task.cancel();
        task = null;
        restoreAll();
    }

    private int clamp(int value, int low, int high) {
        return Math.max(low, Math.min(high, value));
    }
}
