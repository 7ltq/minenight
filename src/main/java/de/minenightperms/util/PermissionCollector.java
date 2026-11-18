package de.minenightperms.util;

import org.bukkit.Bukkit;
import org.bukkit.permissions.Permission;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class PermissionCollector {

    public static List<String> getAllPermissions() {
        List<String> perms = new ArrayList<>();
        for (Permission p : Bukkit.getPluginManager().getPermissions()) {
            perms.add(p.getName());
        }
        perms.sort(Comparator.naturalOrder());
        return perms;
    }
}
