package de.minenight.allinone;

import java.io.File;
import java.io.InputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.logging.Logger;
import org.bukkit.Server;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;

public abstract class MineNightModule implements org.bukkit.command.CommandExecutor, org.bukkit.command.TabCompleter {
    private MineNightAllInOne core;
    private String id;
    private FileConfiguration config;
    private File configFile;

    public final void bind(MineNightAllInOne plugin, String moduleId) {
        this.core = plugin;
        this.id = moduleId;
        this.configFile = new File(plugin.getDataFolder(), moduleId + "/config.yml");
    }

    public final MineNightAllInOne core() { return core; }
    public final Server getServer() { return core.getServer(); }
    public final Logger getLogger() { return core.getLogger(); }
    public final String getName() { return "MineNight" + id; }
    public final boolean isEnabled() { return core.isEnabled(); }
    public final File getDataFolder() { return configFile.getParentFile(); }
    public final PluginCommand getCommand(String name) { return core.getCommand(name); }

    public void saveDefaultConfig() {
        if (!configFile.exists()) {
            getDataFolder().mkdirs();
            String legacyName = switch (id) {
                case "perms" -> "MineNightPerms";
                case "tab" -> "MineNightTab";
                case "performance" -> "MineNightPerformance";
                case "tpa" -> "MineNightTPA";
                case "rtp" -> "MineNightRTP";
                case "rtpqueue" -> "MineNightRTPQueue";
                case "spawn" -> "MineNightPracticeSpawn";
                case "void" -> "MineNightVoid";
                default -> "";
            };
            File legacy = new File(core.getDataFolder().getParentFile(), legacyName + "/config.yml");
            if (legacy.isFile()) {
                try {
                    Files.copy(legacy.toPath(), configFile.toPath());
                    if (id.equals("tpa")) {
                        File oldData = new File(legacy.getParentFile(), "data.yml");
                        if (oldData.isFile()) Files.copy(oldData.toPath(), new File(getDataFolder(), "data.yml").toPath());
                    }
                    core.getLogger().info("Imported " + id + " configuration from " + legacyName);
                } catch (IOException error) { throw new IllegalStateException(error); }
            }
            if (!configFile.exists()) try (InputStream stream = core.getResource("defaults/" + id + ".yml")) {
                if (stream != null) Files.copy(stream, configFile.toPath());
            } catch (IOException error) { throw new IllegalStateException("Cannot write " + configFile, error); }
        }
        reloadConfig();
    }
    public FileConfiguration getConfig() {
        if (config == null) reloadConfig();
        return config;
    }
    public void saveConfig() {
        try { getConfig().save(configFile); }
        catch (IOException error) { throw new IllegalStateException("Cannot save " + configFile, error); }
    }
    public void reloadConfig() {
        config = YamlConfiguration.loadConfiguration(configFile);
        try (InputStream stream = core.getResource("defaults/" + id + ".yml")) {
            if (stream != null) {
                try (java.io.InputStreamReader reader = new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)) {
                    config.setDefaults(YamlConfiguration.loadConfiguration(reader));
                }
            }
        } catch (IOException error) { throw new IllegalStateException(error); }
    }
    @Override public boolean onCommand(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) { return false; }
    @Override public java.util.List<String> onTabComplete(org.bukkit.command.CommandSender sender, org.bukkit.command.Command command, String label, String[] args) { return java.util.List.of(); }
    public abstract void onEnable();
    public void onDisable() { }
    public ChunkGenerator getDefaultWorldGenerator(String name, String id) { return null; }
}
