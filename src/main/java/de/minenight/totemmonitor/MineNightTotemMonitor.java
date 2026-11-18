package de.minenight.totemmonitor;

import de.minenight.allinone.MineNightModule;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

public final class MineNightTotemMonitor extends MineNightModule implements Listener {
    private static final class Sample {
        long lastPopTick = -1;
        int lastSeenDelay = -1;
        int unchangedConsecutive;
        int previousSamples;
        int ticks;
        int flags;
        final Deque<Integer> delays = new ArrayDeque<>();
    }

    private final Map<UUID, Sample> samples = new HashMap<>();
    private long ticks;
    private int task = -1;

    @Override public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, core());
        task = Bukkit.getScheduler().scheduleSyncRepeatingTask(core(), this::tick, 1L, 1L);
    }

    @Override public void onDisable() {
        if (task != -1) Bukkit.getScheduler().cancelTask(task);
        samples.clear();
    }

    @EventHandler public void onQuit(PlayerQuitEvent event) { samples.remove(event.getPlayer().getUniqueId()); }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPop(EntityResurrectEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!getConfig().getBoolean("enabled", true)) return;
        Sample sample = samples.computeIfAbsent(player.getUniqueId(), unused -> new Sample());
        sample.lastPopTick = ticks;
        sample.ticks = 0;
    }

    private void tick() {
        ticks++;
        if (!getConfig().getBoolean("enabled", true)) return;
        // Visit only actively monitored players, not every online player each tick.
        for (var entry : samples.entrySet()) {
            Sample sample = entry.getValue();
            if (sample.lastPopTick < 0) continue;
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player == null) { sample.lastPopTick = -1; continue; }
            int delay = (int) (ticks - sample.lastPopTick);
            if (delay > 30) { sample.lastPopTick = -1; continue; }
            ItemStack offhand = player.getInventory().getItemInOffHand();
            if (offhand.getType() != Material.TOTEM_OF_UNDYING) continue;
            sample.delays.add(delay);
            while (sample.delays.size() > 15) sample.delays.removeFirst();
            sample.lastPopTick = -1;
            int min = Math.max(6, getConfig().getInt("checks.minimum-samples", 10));
            if (sample.delays.size() < min) continue;
            double avg = sample.delays.stream().mapToInt(Integer::intValue).average().orElse(0);
            double variance = sample.delays.stream().mapToDouble(value -> Math.pow(value - avg, 2)).average().orElse(0);
            double maxVariance = getConfig().getDouble("checks.maximum-variance-ticks-squared", 0.2);
            int maxDelay = getConfig().getInt("checks.maximum-average-delay-ticks", 3);
            if (avg <= maxDelay && variance <= maxVariance) {
                sample.unchangedConsecutive++;
                if (sample.unchangedConsecutive >= getConfig().getInt("checks.consecutive-windows", 4)) {
                    sample.flags++;
                    sample.unchangedConsecutive = 0;
                    alert(player, sample, avg, variance);
                }
            } else sample.unchangedConsecutive = 0;
            sample.delays.clear();
        }
    }

    private void alert(Player player, Sample sample, double average, double variance) {
        String message = String.format(java.util.Locale.ROOT, "§8[§dMineNight§8] §ePossible AutoTotem: §f%s §7(mean %.2f ticks, variance %.3f, flags %d)", player.getName(), average, variance, sample.flags);
        getLogger().warning(message.replaceAll("§[0-9a-fk-or]", ""));
        if (getConfig().getBoolean("alerts.staff", true)) for (Player staff : Bukkit.getOnlinePlayers()) {
            if (staff.hasPermission("minenight.totemmonitor.alerts")) staff.sendMessage(message);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minenight.totemmonitor.admin")) { sender.sendMessage("No permission."); return true; }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            reloadConfig();
            sender.sendMessage("MineNight Totem Monitor configuration reloaded.");
            return true;
        }
        sender.sendMessage("MineNight Totem Monitor: " + (getConfig().getBoolean("enabled", true) ? "enabled" : "disabled") + ". Staff alerts only; no automated bans. /totemmonitor reload");
        return true;
    }
}
