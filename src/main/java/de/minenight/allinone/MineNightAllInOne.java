package de.minenight.allinone;

import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;

public final class MineNightAllInOne extends JavaPlugin {
    private static MineNightAllInOne instance;
    private final Map<String, MineNightModule> modules = new LinkedHashMap<>();
    private final java.util.Set<String> startedModules = new java.util.HashSet<>();
    public static MineNightAllInOne instance() { return instance; }
    public MineNightModule module(String name) { return modules.get(name); }

    @Override public void onEnable() {
        instance = this;
        register("perms", new de.minenightperms.MineNightPerms());
        register("tab", new de.minenight.tab.MineNightTab());
        register("performance", new de.minenight.performance.MineNightPerformance());
        register("entitycleanup", new de.minenight.performance.MineNightEntityCleanup());
        register("playeroptimizer", new de.minenight.playeroptimizer.MineNightPlayerOptimizer());
        register("totemsync", new de.minenight.totemsync.MineNightTotemSync());
        register("settings", new de.minenight.settings.MineNightSettings());
        register("moderation", new de.minenight.moderation.MineNightModeration());
        register("utility", new de.minenight.utility.MineNightUtility());
        register("tpa", new de.minenight.tpa.MineNightTPA());
        register("rtp", new de.minenight.rtp.MineNightRTP());
        register("void", new de.minenight.voidworld.MineNightVoid());
        register("spawn", new de.minenight.pracspawn.MineNightPracticeSpawn());
        register("rtpqueue", new de.minenight.rtpqueue.MineNightRTPQueue());
        register("kits", new KitModule());
        register("stafftools", new StaffToolsModule());
        register("restart", new RestartModule());
        register("screenshare", new ScreenshareModule());
        if (getServer().getPluginManager().isPluginEnabled("WorldEdit") ||
                getServer().getPluginManager().isPluginEnabled("FastAsyncWorldEdit")) {
            try {
                register("schematic", new SchematicModule());
            } catch (LinkageError error) {
                getLogger().warning("Schematic support could not initialize: " + error.getMessage());
            }
        } else {
            getLogger().warning("WorldEdit or FAWE is not installed. Schematic commands are unavailable; other MineNight modules will still start.");
            var schematicCommand = getCommand("mnschematic");
            if (schematicCommand != null) schematicCommand.setExecutor((sender, command, label, args) -> {
                sender.sendMessage("§cSchematic support requires WorldEdit or FastAsyncWorldEdit.");
                return true;
            });
        }
        for (Map.Entry<String, MineNightModule> entry : modules.entrySet()) {
            try {
                entry.getValue().onEnable();
                startedModules.add(entry.getKey());
                if (entry.getKey().equals("playeroptimizer")) getCommand("playeroptimizer").setExecutor(entry.getValue());
                if (entry.getKey().equals("tab")) getCommand("minenighttab").setExecutor(entry.getValue());
                if (entry.getKey().equals("performance")) {
                    for (String command : java.util.List.of("mnperf", "nomobs", "noblockdrops", "no")) getCommand(command).setExecutor(entry.getValue());
                }
                getLogger().info("Enabled module: " + entry.getKey());
            } catch (Exception error) {
                getLogger().severe("Failed to initialize module " + entry.getKey());
                error.printStackTrace();
            }
        }
        MineNightVoteListener voting = new MineNightVoteListener(this);
        voting.register();
        var voteTotalCommand = getCommand("votetotal");
        if (voteTotalCommand != null) voteTotalCommand.setExecutor(voting);
        MineNightTabCompleter suggestions = new MineNightTabCompleter();
        for (String name : java.util.List.of("tpa", "tpahere", "tpaccept", "tpadecline", "tpauto", "prac", "spawn", "minenighttab", "mnperf", "nomobs", "noblockdrops", "no", "rtpq", "settings", "back", "announcement", "playeroptimizer")) {
            var registered = getCommand(name);
            if (registered != null) registered.setTabCompleter(suggestions);
        }
    }
    public void publishMineNightAlert(String engine, java.util.UUID target, String check, String message) {
        MineNightModule module = modules.get("stafftools");
        if (module instanceof StaffToolsModule staff) staff.queueAlert(engine, target, check, message);
    }

    public void punishMineNightCheating(java.util.UUID target, String engine, String check) {
        if (!org.bukkit.Bukkit.isPrimaryThread()) {
            org.bukkit.Bukkit.getScheduler().runTask(this, () -> punishMineNightCheating(target, engine, check));
            return;
        }
        MineNightModule module = modules.get("moderation");
        if (module instanceof de.minenight.moderation.MineNightModeration moderation) moderation.banDetectedCheating(target, engine, check);
    }

    private void register(String key, MineNightModule module) {
        module.bind(this, key);
        modules.put(key, module);
    }
    @Override public void onDisable() {
        var keys = new java.util.ArrayList<>(modules.keySet());
        java.util.Collections.reverse(keys);
        for (String key : keys) {
            if (!startedModules.contains(key)) continue;
            try { modules.get(key).onDisable(); } catch (Exception error) { error.printStackTrace(); }
        }
        startedModules.clear();
        modules.clear();
        instance = null;
    }
    @Override public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return new de.minenight.voidworld.MineNightVoid().getDefaultWorldGenerator(worldName, id);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        sender.sendMessage("Unknown MineNight command configuration.");
        return true;
    }
}
