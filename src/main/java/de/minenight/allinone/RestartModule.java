package de.minenight.allinone;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

public final class RestartModule extends MineNightModule implements Listener {
    private BossBar bar;
    private BukkitTask task;
    private long deadline;
    private int duration;
    private int previous = -1;
    private boolean stopping;

    @Override public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, core());
        var command = getCommand("mnrestart");
        if (command == null) throw new IllegalStateException("mnrestart is not registered");
        command.setExecutor(this);
        command.setTabCompleter(this);
    }

    @Override public void onDisable() {
        clear();
    }

    private void clear() {
        if (task != null) task.cancel();
        task = null;
        if (bar != null) bar.removeAll();
        bar = null;
        previous = -1;
    }

    private void title(Player player, String heading, String subtitle) {
        player.showTitle(Title.title(Component.text(heading, NamedTextColor.AQUA),
                Component.text(subtitle, NamedTextColor.WHITE)));
    }

    private void announce(String heading, String subtitle) {
        for (Player player : Bukkit.getOnlinePlayers()) title(player, heading, subtitle);
    }

    private String time(int seconds) {
        return String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60);
    }

    private void start(int seconds) {
        clear();
        duration = seconds;
        deadline = System.nanoTime() + seconds * 1_000_000_000L;
        bar = Bukkit.createBossBar("Server restarting in " + time(seconds), BarColor.BLUE, BarStyle.SOLID);
        for (Player player : Bukkit.getOnlinePlayers()) bar.addPlayer(player);
        announce("Server Restart", "We'll be back shortly!");
        task = Bukkit.getScheduler().runTaskTimer(core(), this::tick, 0L, 5L);
    }

    private void tick() {
        int left = (int) Math.max(0, Math.ceil((deadline - System.nanoTime()) / 1_000_000_000.0));
        if (left == previous) return;
        previous = left;
        bar.setTitle("Server restarting in " + time(left));
        bar.setProgress(Math.max(0.0, Math.min(1.0, (double) left / duration)));
        if (left > 0 && left <= 10) {
            for (Player player : Bukkit.getOnlinePlayers()) countdown(player, left);
        }
        if (left == 0) restart();
    }

    private void countdown(Player player, int left) {
        player.showTitle(Title.title(Component.text(Integer.toString(left), left <= 3 ? NamedTextColor.RED : NamedTextColor.AQUA),
                Component.text("Server restarting — We'll be back shortly!", NamedTextColor.WHITE),
                Title.Times.times(Duration.ZERO, Duration.ofMillis(1250), Duration.ZERO)));
    }

    private void restart() {
        if (stopping) return;
        stopping = true;
        clear();
        announce("Server Restart", "We'll be back shortly!");
        Bukkit.savePlayers();
        for (World world : Bukkit.getWorlds()) world.save();
        Bukkit.getScheduler().runTaskLater(core(), Bukkit::shutdown, 20L);
    }

    @EventHandler public void onJoin(PlayerJoinEvent event) {
        if (bar != null) {
            bar.addPlayer(event.getPlayer());
            int left = (int) Math.max(0, Math.ceil((deadline - System.nanoTime()) / 1_000_000_000.0));
            if (left > 0 && left <= 10) countdown(event.getPlayer(), left);
            else title(event.getPlayer(), "Server Restart", "We'll be back shortly!");
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minenight.restart")) {
            sender.sendMessage("You do not have permission.");
            return true;
        }
        if (stopping) {
            sender.sendMessage("The server is already shutting down.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("cancel")) {
            if (task == null) {
                sender.sendMessage("No restart is scheduled.");
                return true;
            }
            clear();
            announce("Restart Cancelled", "The server will remain online.");
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("now")) {
            restart();
            return true;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("in")) {
            try {
                long amount = Long.parseLong(args[1]);
                long factor = switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "minute", "minutes", "m" -> 60L;
                    case "second", "seconds", "s" -> 1L;
                    default -> 0L;
                };
                if (factor > 0 && amount > 0 && amount <= 86400L / factor) {
                    start((int) (amount * factor));
                    sender.sendMessage("Restart scheduled in " + time(duration) + ".");
                    return true;
                }
            } catch (NumberFormatException ignored) {
            }
        }
        sender.sendMessage("Usage: /mnrestart in <amount> <seconds|minutes> | cancel | now");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("minenight.restart")) return List.of();
        List<String> options = switch (args.length) {
            case 1 -> List.of("in", "cancel", "now");
            case 2 -> args[0].equalsIgnoreCase("in") ? List.of("1", "2", "3", "5", "10", "15", "30", "60") : List.of();
            case 3 -> args[0].equalsIgnoreCase("in") ? ("1".equals(args[1]) ? List.of("second", "minute") : List.of("seconds", "minutes")) : List.of();
            default -> List.of();
        };
        if (args.length == 0) return options;
        String prefix = args[args.length - 1].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
