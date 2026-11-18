package de.minenightperms.vault;

import de.minenightperms.MineNightPerms;
import de.minenightperms.model.Group;
import de.minenightperms.model.PlayerData;
import net.milkbowl.vault.permission.Permission;
import org.bukkit.OfflinePlayer;


public class MineNightPermsVaultProvider extends Permission {

    private final MineNightPerms plugin;

    public MineNightPermsVaultProvider(MineNightPerms plugin) {
        this.plugin = plugin;
    }

    @Override public String getName()              { return "MineNightPerms"; }
    @Override public boolean isEnabled()           { return plugin.core().isEnabled(); }
    @Override public boolean hasSuperPermsCompat() { return true; }
    @Override public boolean hasGroupSupport()     { return true; }


    @Override
    public boolean playerHas(String world, String player, String permission) {
        org.bukkit.entity.Player p = plugin.getServer().getPlayerExact(player);
        if (p != null) return playerHas(world, p, permission);

        PlayerData pd = plugin.getPlayerDataManager().getOfflinePlayerData(player);
        if (pd == null) return false;
        Group g = plugin.getGroupManager().getGroup(pd.getGroupName());
        return g != null && g.getPermissions().contains(permission);
    }

    @Override
    public boolean playerHas(String world, OfflinePlayer player, String permission) {
        if (player.isOnline() && player.getPlayer() != null)
            return player.getPlayer().hasPermission(permission);
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Group g = plugin.getGroupManager().getGroup(pd.getGroupName());
        return g != null && g.getPermissions().contains(permission);
    }


    @Override public boolean playerAdd(String world, String player, String permission)            { return false; }
    @Override public boolean playerRemove(String world, String player, String permission)         { return false; }
    @Override public boolean playerAdd(String world, OfflinePlayer player, String permission)     { return false; }
    @Override public boolean playerRemove(String world, OfflinePlayer player, String permission)  { return false; }


    @Override
    public String[] getPlayerGroups(String world, OfflinePlayer player) {
        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        return new String[]{ pd.getGroupName() };
    }

    @Override
    public String[] getPlayerGroups(String world, String player) {
        org.bukkit.entity.Player p = plugin.getServer().getPlayerExact(player);
        if (p != null) return getPlayerGroups(world, (OfflinePlayer) p);
        PlayerData pd = plugin.getPlayerDataManager().getOfflinePlayerData(player);
        return pd != null ? new String[]{ pd.getGroupName() } : new String[]{ "default" };
    }

    @Override
    public String getPrimaryGroup(String world, OfflinePlayer player) {
        return plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getGroupName();
    }

    @Override
    public String getPrimaryGroup(String world, String player) {
        org.bukkit.entity.Player p = plugin.getServer().getPlayerExact(player);
        if (p != null) return getPrimaryGroup(world, (OfflinePlayer) p);
        PlayerData pd = plugin.getPlayerDataManager().getOfflinePlayerData(player);
        return pd != null ? pd.getGroupName() : "default";
    }

    @Override
    public String[] getGroups() {
        return plugin.getGroupManager().getGroups().stream()
            .map(g -> g.getName()).toArray(String[]::new);
    }

    @Override
    public boolean groupHas(String world, String group, String permission) {
        Group g = plugin.getGroupManager().getGroup(group);
        return g != null && g.getPermissions().contains(permission);
    }

    @Override public boolean groupAdd(String world, String group, String permission)    { return false; }
    @Override public boolean groupRemove(String world, String group, String permission) { return false; }

    @Override public boolean playerInGroup(String world, OfflinePlayer player, String group) {
        return getPrimaryGroup(world, player).equalsIgnoreCase(group);
    }
    @Override public boolean playerInGroup(String world, String player, String group) {
        return getPrimaryGroup(world, player).equalsIgnoreCase(group);
    }
    @Override public boolean playerAddGroup(String world, OfflinePlayer player, String group)   { return false; }
    @Override public boolean playerAddGroup(String world, String player, String group)          { return false; }
    @Override public boolean playerRemoveGroup(String world, OfflinePlayer player, String group){ return false; }
    @Override public boolean playerRemoveGroup(String world, String player, String group)       { return false; }
}
