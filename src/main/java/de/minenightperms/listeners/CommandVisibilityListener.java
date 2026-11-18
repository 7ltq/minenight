package de.minenightperms.listeners;

import de.minenightperms.MineNightPerms;
import de.minenightperms.model.Group;
import de.minenightperms.model.PlayerData;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.permissions.Permission;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandSendEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;


public class CommandVisibilityListener implements Listener {

    private final MineNightPerms plugin;
    private final PermissionListener permHelper;

    public CommandVisibilityListener(MineNightPerms plugin) {
        this.plugin = plugin;
        this.permHelper = new PermissionListener(plugin);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCommandSend(PlayerCommandSendEvent event) {
        Player player = event.getPlayer();
        if (player.isOp()) return;

        PlayerData pd = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId());
        Group group = plugin.getGroupManager().getGroup(pd.getGroupName());
        if (group == null) return;

        Set<String> resolved = permHelper.resolveAll(group.getPermissions());

        List<String> toRemove = new ArrayList<>();
        for (String cmdName : event.getCommands()) {
            Command cmd = Bukkit.getCommandMap().getCommand(cmdName);
            if (cmd == null) continue;
            String requiredPerm = cmd.getPermission();
            if (requiredPerm == null || requiredPerm.isEmpty()) continue;

            if (!permHelper.hasResolvedPermission(resolved, requiredPerm) && !publicCommand(player, cmd)) {
                toRemove.add(cmdName);
            }
        }
        event.getCommands().removeAll(toRemove);

        for (Command cmd : Bukkit.getCommandMap().getKnownCommands().values()) {
            String requiredPerm = cmd.getPermission();
            if (requiredPerm == null || requiredPerm.isEmpty()) continue;
            if (!permHelper.hasResolvedPermission(resolved, requiredPerm) && !publicCommand(player, cmd)) continue;

            String name = cmd.getName().toLowerCase();
            if (!event.getCommands().contains(name)) {
                event.getCommands().add(name);
            }

            for (String alias : cmd.getAliases()) {
                String al = alias.toLowerCase();
                if (!event.getCommands().contains(al)) {
                    event.getCommands().add(al);
                }
            }
        }
    }
    private boolean publicCommand(Player player, Command command) {
        String permissionNode = command.getPermission();
        if (permissionNode == null || permissionNode.isBlank()) return true;
        Permission permission = Bukkit.getPluginManager().getPermission(permissionNode);
        // Respect existing explicit permission denials. Only public-by-default commands
        // should become visible without a manually added group permission.
        return permission != null && permission.getDefault() == org.bukkit.permissions.PermissionDefault.TRUE
                && player.hasPermission(permissionNode);
    }

}
