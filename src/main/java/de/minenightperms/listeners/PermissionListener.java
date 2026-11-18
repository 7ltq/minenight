package de.minenightperms.listeners;

import de.minenightperms.MineNightPerms;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;

import java.util.HashSet;
import java.util.List;
import java.util.Set;


public class PermissionListener implements Listener {

    private final MineNightPerms plugin;

    public PermissionListener(MineNightPerms plugin) { this.plugin = plugin; }

    public Set<String> resolveAll(List<String> permNodes) {
        Set<String> result = new HashSet<>();
        for (String node : permNodes) {
            if (node != null && !node.isBlank()) {
                resolveRecursive(node.trim(), result, new HashSet<>());
            }
        }
        return result;
    }

    public void resolveRecursive(String node, Set<String> result, Set<String> visited) {
        if (node.isEmpty() || !visited.add(node)) return;
        result.add(node);
        Permission perm = Bukkit.getPluginManager().getPermission(node);
        if (perm == null) return;
        for (var entry : perm.getChildren().entrySet()) {
            if (Boolean.TRUE.equals(entry.getValue())) {
                resolveRecursive(entry.getKey(), result, visited);
            }
        }
    }

    public boolean hasResolvedPermission(Set<String> resolved, String permission) {
        if (resolved.contains("*") || resolved.contains(permission)) return true;
        for (String p : resolved) {
            if (p.endsWith(".*") && permission.startsWith(p.substring(0, p.length() - 1))) return true;
        }
        return false;
    }
}
