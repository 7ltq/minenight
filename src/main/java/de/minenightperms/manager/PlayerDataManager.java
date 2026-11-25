package de.minenightperms.manager;

import de.minenightperms.MineNightPerms;
import de.minenightperms.model.PlayerData;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDataManager {

    private final Map<UUID, PlayerData> players = new java.util.concurrent.ConcurrentHashMap<>();
    private final MineNightPerms plugin;

    public PlayerDataManager(MineNightPerms plugin) {
        this.plugin = plugin;
    }

    public void loadPlayers() {
        players.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("players");
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                PlayerData pd = new PlayerData(uuid, group -> plugin.getTebexRanks().effectiveGroup(uuid, group));
                pd.setGroupName(plugin.getConfig().getString("players." + key + ".group", "default"));
                players.put(uuid, pd);
            } catch (IllegalArgumentException ignored) {}
        }
    }

    public void savePlayers() {
        for (Map.Entry<UUID, PlayerData> entry : players.entrySet()) {
            plugin.getConfig().set("players." + entry.getKey() + ".group", entry.getValue().getAdministrativeGroupName());
        }
        plugin.saveConfig();
    }

    public PlayerData getPlayerData(UUID uuid) {
        return players.computeIfAbsent(uuid, id -> new PlayerData(id, group -> plugin.getTebexRanks().effectiveGroup(id, group)));
    }

    
    public PlayerData getOfflinePlayerData(String name) {
        for (var entry : players.entrySet()) {

            org.bukkit.OfflinePlayer op = plugin.getServer().getOfflinePlayer(entry.getKey());
            if (name.equalsIgnoreCase(op.getName())) return entry.getValue();
        }
        return null;
    }

    public void setPlayerGroup(UUID uuid, String group) {
        getPlayerData(uuid).setGroupName(group);
        savePlayers();
    }
}
