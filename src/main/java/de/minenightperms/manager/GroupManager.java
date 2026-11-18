package de.minenightperms.manager;

import de.minenightperms.MineNightPerms;
import de.minenightperms.model.Group;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GroupManager {

    private static final String MEDIA_PREFIX = "<#E52DBB>📹</#E52DBB><#00C8F0>+</#00C8F0>";

    
    public static final List<String> DEFAULT_PERMISSIONS = List.of(
        "minenight.kits.use",
        "minenightrtp.use",
        "minenightrtpq.use",
        "minenightspawn.use",
        "minenightvoid.teleport",
        "minenight.back",
        "kit.use",
        "rtpplugin.rtp",
        "tpaplugin.tpa",
        "spawn.use",
        "spawn.back",
        "perplayerkit.use",
        "kit.staff",
        "tombot.use"
    );

    private final Map<String, Group> groups = new LinkedHashMap<>();
    private final MineNightPerms plugin;

    public GroupManager(MineNightPerms plugin) { this.plugin = plugin; }


    public void loadGroups() {
        groups.clear();

        Group def = new Group("default");
        def.setPermissions(new ArrayList<>(DEFAULT_PERMISSIONS));
        groups.put("default", def);

        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("groups");
        if (sec != null) {
            for (String key : sec.getKeys(false)) {
                String path = "groups." + key + ".";
                String displayName = plugin.getConfig().getString(path + "displayName", key);
                Group g = new Group(displayName);
                g.setRoleGradient(plugin.getConfig().getString(path + "roleGradient", "white"));
                g.setRoleBold    (plugin.getConfig().getBoolean(path + "roleBold",    false));
                g.setNameGradient(plugin.getConfig().getString(path + "nameGradient", "white"));
                g.setNameBold    (plugin.getConfig().getBoolean(path + "nameBold",    false));
                g.setEmoji       (plugin.getConfig().getString(path + "emoji",        ""));
                g.setPrefix      (plugin.getConfig().getString(path + "prefix",       ""));
                g.setPriority    (plugin.getConfig().getInt(path + "priority",        0));
                g.setPermissions (plugin.getConfig().getStringList(path + "permissions"));
                groups.put(key.toLowerCase(), g);
            }
        }

        // Provision and migrate the built-in Media role. The role name remains "Media"
        // in menus, while the in-world nametag uses only the camera + cyan plus symbols.
        Group media = groups.get("media");
        boolean mediaChanged = false;
        if (media == null) {
            media = new Group("Media");
            media.setRoleGradient("#E52DBB");
            media.setRoleBold(false);
            media.setNameGradient("#E52DBB");
            media.setNameBold(false);
            media.setPrefix(MEDIA_PREFIX);
            media.setPriority(25);
            media.setPermissions(new ArrayList<>(DEFAULT_PERMISSIONS));
            groups.put("media", media);
            mediaChanged = true;
        } else {
            String oldPrefix = media.getPrefix();
            // Use the exact video-camera emoji requested for the Media nametag.
            // Camera and plus are one compact MiniMessage prefix, with explicit
            // pink/cyan colors and no inserted gap between the two symbols.
            // NameTagManager adds a single normal space before the player name.
            if (!MEDIA_PREFIX.equals(oldPrefix)) {
                media.setPrefix(MEDIA_PREFIX);
                mediaChanged = true;
            }
            if (!media.getEmoji().isBlank()) {
                media.setEmoji("");
                mediaChanged = true;
            }
            if (media.getRoleGradient() == null
                    || media.getRoleGradient().isBlank()
                    || media.getRoleGradient().equalsIgnoreCase("white")) {
                media.setRoleGradient("#E52DBB");
                mediaChanged = true;
            }
            if (media.getNameGradient() == null
                    || media.getNameGradient().isBlank()
                    || media.getNameGradient().equalsIgnoreCase("white")) {
                media.setNameGradient("#E52DBB");
                mediaChanged = true;
            }
        }
        if (mediaChanged) saveGroups();
    }


    public void saveGroups() {
        plugin.getConfig().set("groups", null);
        for (Map.Entry<String, Group> e : groups.entrySet()) {
            if (e.getKey().equalsIgnoreCase("default")) continue;
            Group  g    = e.getValue();
            String path = "groups." + e.getKey() + ".";
            plugin.getConfig().set(path + "displayName",  g.getName());
            plugin.getConfig().set(path + "roleGradient", g.getRoleGradient());
            plugin.getConfig().set(path + "roleBold",     g.isRoleBold());
            plugin.getConfig().set(path + "nameGradient", g.getNameGradient());
            plugin.getConfig().set(path + "nameBold",     g.isNameBold());
            plugin.getConfig().set(path + "emoji",        g.getEmoji());
            plugin.getConfig().set(path + "prefix",       g.getPrefix());
            plugin.getConfig().set(path + "priority",     g.getPriority());
            plugin.getConfig().set(path + "permissions",  g.getPermissions());
        }
        plugin.saveConfig();
    }


    public Group getGroup(String name) {
        if (name == null) return groups.get("default");
        Group g = groups.get(name.toLowerCase());
        return g != null ? g : groups.get("default");
    }

    public boolean groupExists(String name) {
        return name != null && groups.containsKey(name.toLowerCase());
    }

    public void createGroup(String name) {
        Group g = new Group(name);
        g.setPermissions(new ArrayList<>(DEFAULT_PERMISSIONS));
        groups.put(name.toLowerCase(), g);
        saveGroups();
    }

    public void deleteGroup(String name) {
        if (!name.equalsIgnoreCase("default")) {
            groups.remove(name.toLowerCase());
            saveGroups();
        }
    }

    public Collection<Group> getGroups() { return groups.values(); }
}
