package de.minenight.allinone;

import java.util.List;
import java.util.Locale;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public final class AntiCheatModule extends MineNightModule {
    private Object engine;
    private boolean active;

    @Override public void onEnable() {
        if (!Bukkit.getPluginManager().isPluginEnabled("packetevents")) {
            getLogger().warning("MineNight AntiCheat requires PacketEvents; legacy monitor remains active.");
            return;
        }
        if (Bukkit.getPluginManager().isPluginEnabled("TotemGuard")) {
            getLogger().warning("Standalone TotemGuard detected; embedded engine will not start.");
            return;
        }
        try {
            Class<?> platform = Class.forName("com.deathmotion.totemguard.paper.TGPaperPlatform");
            engine = platform.getConstructor(JavaPlugin.class).newInstance(core());
            platform.getMethod("commonOnEnable").invoke(engine);
            active = (Boolean) platform.getMethod("isEnabled").invoke(engine);
            if (!active) {
                stopEngine();
                return;
            }
            for (String name : List.of("alerts", "sus", "totemcheck")) {
                var command = getCommand(name);
                command.setExecutor(this);
                command.setTabCompleter(this);
            }
            getLogger().info("MineNight AntiCheat: embedded engine active.");
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            getLogger().log(java.util.logging.Level.SEVERE, "MineNight AntiCheat startup failed; legacy monitor remains active.", error);
            stopEngine();
        }
    }

    public boolean active() { return active; }

    private void stopEngine() {
        active = false;
        if (engine == null) return;
        try {
            engine.getClass().getMethod("commonOnDisable").invoke(engine);
        } catch (ReflectiveOperationException | LinkageError | RuntimeException error) {
            getLogger().log(java.util.logging.Level.WARNING, "MineNight AntiCheat cleanup failed.", error);
        }
        engine = null;
    }

    @Override public void onDisable() { stopEngine(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minenight.anticheat.staff")) return true;
        if (!active) {
            sender.sendMessage("MineNight AntiCheat is not available.");
            return true;
        }
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("alerts") && args.length == 0) return Bukkit.dispatchCommand(sender, "mnac alerts");
        if (name.equals("sus") || name.equals("totemcheck")) {
            if (args.length != 1 || !args[0].matches("[A-Za-z0-9_]{1,16}")) {
                sender.sendMessage("Usage: /" + name + " <player>");
                return true;
            }
            return Bukkit.dispatchCommand(sender, "mnac " + (name.equals("sus") ? "profile " : "check ") + args[0]);
        }
        sender.sendMessage("Usage: /alerts");
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!active || !sender.hasPermission("minenight.anticheat.staff") || args.length != 1 || command.getName().equalsIgnoreCase("alerts")) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return Bukkit.getOnlinePlayers().stream().filter(p -> !(sender instanceof Player viewer) || viewer.canSee(p))
                .map(Player::getName).filter(n -> n.toLowerCase(Locale.ROOT).startsWith(prefix)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
