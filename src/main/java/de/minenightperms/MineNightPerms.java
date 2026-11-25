package de.minenightperms;

import de.minenightperms.commands.MineNightPermsCommand;
import de.minenightperms.gui.GuiListener;
import de.minenightperms.listeners.ChatListener;
import de.minenightperms.listeners.JoinQuitListener;
import de.minenightperms.manager.GroupManager;
import de.minenightperms.manager.PlayerDataManager;
import de.minenightperms.vault.MineNightPermsVaultProvider;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public class MineNightPerms extends de.minenight.allinone.MineNightModule {

    private static MineNightPerms instance;
    private GroupManager      groupManager;
    private PlayerDataManager playerDataManager;
    private de.minenightperms.manager.TebexRanks tebexRanks;
    private GuiListener       guiListener;
    private de.minenightperms.listeners.NameTagManager nameTagManager;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();
        groupManager      = new GroupManager(this);
        playerDataManager = new PlayerDataManager(this);
        guiListener       = new GuiListener(this);
        nameTagManager = new de.minenightperms.listeners.NameTagManager(this);

        groupManager.loadGroups();
        tebexRanks = new de.minenightperms.manager.TebexRanks(this);
        playerDataManager.loadPlayers();

        MineNightPermsCommand cmd = new MineNightPermsCommand(this);
        var pluginCmd = getCommand("minenightperms");
        if (pluginCmd != null) {
            pluginCmd.setExecutor(cmd);
            pluginCmd.setTabCompleter(cmd);
        }

        var pm = getServer().getPluginManager();
        pm.registerEvents(guiListener, core());
        pm.registerEvents(nameTagManager, core());
        nameTagManager.start();
        pm.registerEvents(new ChatListener(this), core());
        pm.registerEvents(new JoinQuitListener(this), core());
        pm.registerEvents(new de.minenightperms.listeners.PermissionListener(this), core());
        // Bukkit/Paper already hides commands according to actual permissions.
        // Do not rewrite PlayerCommandSendEvent: it hid default-true player commands
        // and added unverified aliases, causing client-side red command text.

        if (pm.getPlugin("Vault") != null) {
            getServer().getServicesManager().register(
                Permission.class,
                new MineNightPermsVaultProvider(this),
                core(),
                ServicePriority.Highest);
            getLogger().info("Vault hooked successfully!");
        } else {
            getLogger().warning("Vault not found!");
        }
        getLogger().info("MineNightPerms module enabled");
    }

    @Override
    public void onDisable() {
        if (nameTagManager != null) nameTagManager.stop();
        groupManager.saveGroups();
        playerDataManager.savePlayers();
        getLogger().info("MineNightPerms v3.4.5 disabled.");
    }

    public String getPlayerRole(java.util.UUID id) {
        var g = groupManager.getGroup(playerDataManager.getPlayerData(id).getGroupName());
        return g == null ? "" : g.getFormattedRole();
    }

    public String getPlayerNameFormat(java.util.UUID id, String playerName) {
        var g = groupManager.getGroup(playerDataManager.getPlayerData(id).getGroupName());
        return g == null ? playerName : g.getFormattedName(playerName);
    }

    public String getPlayerNameColorFormat(java.util.UUID id) {
        var g = groupManager.getGroup(playerDataManager.getPlayerData(id).getGroupName());
        return g == null ? "white" : g.getNameGradient();
    }

    /** Returns whether the player belongs to the built-in Media role. */
    public boolean isMediaPlayer(java.util.UUID id) {
        if (groupManager == null || playerDataManager == null) return false;
        String groupName = playerDataManager.getPlayerData(id).getGroupName();
        return "media".equalsIgnoreCase(groupName);
    }

    public void setNameTagVisible(org.bukkit.entity.Player viewer, java.util.UUID playerId, boolean visible) {
        if (nameTagManager != null) nameTagManager.setVisibleTo(viewer, playerId, visible);
    }

    public static MineNightPerms   getInstance()          { return instance; }
    public GroupManager      getGroupManager()       { return groupManager; }
    public de.minenightperms.manager.TebexRanks getTebexRanks() { return tebexRanks; }
    public PlayerDataManager getPlayerDataManager()  { return playerDataManager; }
    public GuiListener       getGuiListener()        { return guiListener; }
}
