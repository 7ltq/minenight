package de.minenightperms.gui;

import de.minenightperms.MineNightPerms;
import de.minenightperms.model.Group;
import de.minenightperms.util.GradientPresets;
import de.minenightperms.util.PermissionCollector;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class GuiManager {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private static Component parse(String s) { return MM.deserialize(s); }


    static ItemStack buildItem(Material mat, String name, List<String> lore) {
        ItemStack item = new ItemStack(mat);
        ItemMeta  meta = item.getItemMeta();
        if (meta == null) return item;
        meta.displayName(parse(name));
        if (lore != null)
            meta.lore(lore.stream().map(GuiManager::parse).collect(Collectors.toList()));
        item.setItemMeta(meta);
        return item;
    }

    private static void fillGlass(Inventory inv, int size) {
        ItemStack glass = buildItem(Material.GRAY_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < size; i++) {
            if (inv.getItem(i) == null) inv.setItem(i, glass);
        }
    }


    public static Inventory buildMainMenu() {
        Inventory inv = Bukkit.createInventory(null, 27,
            parse("<gradient:#4B63C4:#91B2FF><bold>MineNightPerms</bold></gradient>"));

        inv.setItem(10, buildItem(Material.CHEST,
            "<#91B2FF><bold>Manage Groups</bold></#91B2FF>",
            List.of("<#B7C7E8>Create, edit and delete groups")));

        inv.setItem(13, buildItem(Material.BOOK,
            "<#91B2FF><bold>Manage Permissions</bold></#91B2FF>",
            List.of("<#B7C7E8>Add or remove permissions per group")));

        inv.setItem(16, buildItem(Material.NAME_TAG,
            "<#91B2FF><bold>Assign Roles</bold></#91B2FF>",
            List.of("<#B7C7E8>Set a player's group")));

        fillGlass(inv, 27);
        return inv;
    }


    public static Inventory buildGroupList(MineNightPerms plugin) {
        List<Group> groupList = new ArrayList<>(plugin.getGroupManager().getGroups());
        int rows = Math.max(3, (int) Math.ceil((groupList.size() + 9) / 9.0) + 1);
        int size = Math.min(rows * 9, 54);
        Inventory inv = Bukkit.createInventory(null, size,
            parse("<gradient:#4B63C4:#91B2FF><bold>All Groups</bold></gradient>"));

        int slot = 0;
        for (Group g : groupList) {
            if (slot >= size - 9) break;
            String role    = g.getFormattedRole();
            String display = role.isEmpty() ? "<white>" + g.getName() : role;
            inv.setItem(slot++, buildItem(Material.NAME_TAG,
                display,
                List.of(
                    "<#B7C7E8>Group: <white>" + g.getName(),
                    "<#B7C7E8>Left click to edit",
                    "<#B7C7E8>Priority: <white>" + g.getPriority(),
                    "<#B7C7E8>Permissions: <white>" + g.getPermissions().size())));
        }

        inv.setItem(size - 5, buildItem(Material.PAPER,
            "<#91B2FF><bold>+ New Group</bold></#91B2FF>",
            List.of("<#B7C7E8>Click to create a new group")));
        inv.setItem(size - 1, buildItem(Material.ARROW, "<#91B2FF>← Back</#91B2FF>", null));
        fillGlass(inv, size);
        return inv;
    }









    public static Inventory buildGroupEditor(MineNightPerms plugin, Group g) {
        Inventory inv = Bukkit.createInventory(null, 45,
            parse("<gradient:#4B63C4:#91B2FF><bold>Edit: </bold></gradient><white>" + g.getName()));

        inv.setItem(10, buildItem(Material.GREEN_DYE,
            "<#91B2FF><bold>Role Color</bold></#91B2FF>",
            List.of("<#B7C7E8>Choose Gradients or Solid Colors",
                    "<#B7C7E8>Current: <white>" + g.getRoleGradient())));

        inv.setItem(12, buildItem(Material.RED_DYE,
            "<#6685E8><bold>Name Color</bold></#6685E8>",
            List.of("<#B7C7E8>Choose Gradients or Solid Colors",
                    "<#B7C7E8>Current: <white>" + g.getNameGradient())));

        inv.setItem(14, buildItem(Material.COMPARATOR,
            "<#91B2FF><bold>Permissions</bold></#91B2FF>",
            List.of("<#B7C7E8>Manage permissions",
                    "<#B7C7E8>Count: <white>" + g.getPermissions().size())));

        inv.setItem(19, buildItem(g.isRoleBold() ? Material.LIME_DYE : Material.GRAY_DYE,
            g.isRoleBold() ? "<#91B2FF>ROLE BOLD: ON" : "<#6685E8>ROLE BOLD: OFF",
            List.of("<#B7C7E8>Toggle bold for role prefix")));

        inv.setItem(21, buildItem(g.isNameBold() ? Material.LIME_DYE : Material.GRAY_DYE,
            g.isNameBold() ? "<#91B2FF>NAME BOLD: ON" : "<#6685E8>NAME BOLD: OFF",
            List.of("<#B7C7E8>Toggle bold for player name")));

        String emojiDisplay  = g.getEmoji().isBlank()  ? "<#B7C7E8>None" : "<white>" + g.getEmoji();
        inv.setItem(23, buildItem(Material.FIREWORK_STAR,
            "<#91B2FF><bold>Emoji Prefix</bold></#91B2FF>",
            List.of("<#B7C7E8>Emoji before role name (no color)",
                    "<#B7C7E8>Current: " + emojiDisplay,
                    "<#B7C7E8>Left click: pick emoji",
                    "<#6685E8>Right click: clear emoji")));

        String prefixDisplay = g.getPrefix().isBlank() ? "<#B7C7E8>Not set (uses group name)" : "<white>" + g.getPrefix();
        inv.setItem(25, buildItem(Material.NAME_TAG,
            "<#91B2FF><bold>Custom Prefix</bold></#91B2FF>",
            List.of("<#B7C7E8>Text shown as prefix (colored by Role Color)",
                    "<#B7C7E8>Current: " + prefixDisplay,
                    "<#B7C7E8>Left click: set prefix via chat",
                    "<#6685E8>Right click: clear prefix")));

        inv.setItem(28, buildItem(Material.ARROW,
            "<#91B2FF><bold>Priority -</bold></#91B2FF>",
            List.of("<#B7C7E8>Left: -1   Right: -10")));

        inv.setItem(30, buildItem(Material.PAPER,
            "<white><bold>Priority: <#91B2FF>" + g.getPriority() + "</bold>",
            List.of("<#B7C7E8>Higher = higher role priority",
                    "<#B7C7E8>Owner: 100, Admin: 90, ..., Default: 0")));

        inv.setItem(32, buildItem(Material.ARROW,
            "<#91B2FF><bold>Priority +</bold></#91B2FF>",
            List.of("<#B7C7E8>Left: +1   Right: +10")));

        if (!g.getName().equalsIgnoreCase("default")) {
            inv.setItem(16, buildItem(Material.TNT,
                "<#6685E8><bold>Delete Group</bold></#6685E8>",
                List.of("<#B7C7E8>Permanently delete this group",
                        "<#6685E8>Cannot be undone!")));
        }

        inv.setItem(34, buildItem(Material.LIME_STAINED_GLASS_PANE,
            "<#91B2FF><bold>✔ Save & Apply</bold></#91B2FF>",
            List.of("<#B7C7E8>Save all changes")));

        inv.setItem(40, buildItem(Material.ARROW, "<#91B2FF>← Back</#91B2FF>", null));
        fillGlass(inv, 45);
        return inv;
    }


    public static final List<String> EMOJIS = List.of(

        "👑", "🚀", "📷", "⭐", "❄", "🔥", "💀", "🌿",
        "🌊", "⚡", "🎯", "💎", "🎮", "🏆", "🌙", "☠",
        "🛡", "⚔", "🎭", "🔮", "💫", "🌈", "🦋", "🐉",
        "🦅", "🌺", "🍀", "🎵", "📢", "🔑", "💥", "🌟",
        "🎖", "🔱", "⚜", "🌠", "🪄", "🦁", "🐺", "🦊",

        "📷+", "⭐+", "🔥+", "💎+", "👑+", "🚀+", "🏆+",
        "+📷", "+⭐", "+🔥", "+💎",
        "★+",  "+★",  "✦+",  "+✦",  "✧+",  "+✧",
        "◆+",  "+◆",  "●+",  "+●",  "▲+",  "+▲"
    );

    public static Inventory buildEmojiPicker(String groupName) {
        Inventory inv = Bukkit.createInventory(null, 54,
            parse("<gradient:#4B63C4:#91B2FF><bold>Emoji Picker</bold></gradient><white>: " + groupName));

        for (int i = 0; i < EMOJIS.size() && i < 45; i++) {
            String sym = EMOJIS.get(i);
            boolean isCombo = sym.contains("+") || sym.contains("★") || sym.contains("◆")
                              || sym.contains("●") || sym.contains("▲") || sym.contains("✦") || sym.contains("✧");
            String label = isCombo
                ? "<#91B2FF><bold>" + sym
                : "<white><bold>" + sym;
            List<String> lore = isCombo
                ? List.of("<#B7C7E8>Symbol combo — click to set", "<#91B2FF>Preview: " + sym + " Rolle")
                : List.of("<#B7C7E8>Click to set as emoji prefix");
            inv.setItem(i, buildItem(Material.PAPER, label, lore));
        }

        inv.setItem(45, buildItem(Material.ARROW, "<#91B2FF>← Back</#91B2FF>", null));

        inv.setItem(46, buildItem(Material.WRITABLE_BOOK,
            "<#91B2FF><bold>✎ Custom Symbol...</bold></#91B2FF>",
            List.of(
                "<#B7C7E8>Type any text as symbol in chat",
                "<#B7C7E8>Examples: <white>📷+  +★  [VIP]  ≫",
                "<#91B2FF>Click to open chat input")));

        inv.setItem(49, buildItem(Material.BARRIER, "<#6685E8><bold>✗ No Symbol</bold></#6685E8>",
            List.of("<#B7C7E8>Remove emoji/symbol prefix")));

        fillGlass(inv, 54);
        return inv;
    }


    
    public static Inventory buildColorCategoryPicker(String title, String currentColor) {
        Inventory inv = Bukkit.createInventory(null, 27, parse(title));

        inv.setItem(11, buildItem(Material.FIREWORK_STAR,
            "<gradient:#4B63C4:#91B2FF><bold>Gradients</bold></gradient>",
            List.of(
                "<#B7C7E8>Choose a multi-color gradient",
                "<#B7C7E8>Available: <white>" + GradientPresets.GRADIENTS.size(),
                "<#91B2FF>Click to browse gradients"
            )));

        inv.setItem(15, buildItem(Material.LIME_DYE,
            "<#91B2FF><bold>Solid Colors</bold></#91B2FF>",
            List.of(
                "<#B7C7E8>Choose one normal, single color",
                "<#B7C7E8>Available: <white>" + GradientPresets.SOLID_COLORS.size(),
                "<#91B2FF>Click to browse solid colors"
            )));

        inv.setItem(13, buildItem(Material.NAME_TAG,
            "<white><bold>Current Color</bold>",
            List.of("<#B7C7E8>Current value:", "<white>" + (currentColor == null ? "white" : currentColor))));

        inv.setItem(22, buildItem(Material.ARROW, "<#91B2FF>← Back</#91B2FF>", null));
        fillGlass(inv, 27);
        return inv;
    }

    public static Inventory buildColorPicker(String title, int page,
                                             List<GradientPresets.GradientPreset> presets) {
        int total = presets.size();
        int perPage = GradientPresets.PER_PAGE;
        int maxPage = total == 0 ? 0 : (total - 1) / perPage;
        page = Math.max(0, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, 54, parse(title));
        int start = page * perPage;
        int end = Math.min(start + perPage, total);
        for (int i = start; i < end; i++) {
            inv.setItem(i - start, GradientPresets.buildItem(presets.get(i)));
        }

        if (page > 0) {
            inv.setItem(45, buildItem(Material.ARROW, "<#91B2FF>← Prev</#91B2FF>",
                List.of("<#B7C7E8>Page " + page + "/" + (maxPage + 1))));
        }

        inv.setItem(49, buildItem(Material.PAPER,
            "<white>Page <#91B2FF>" + (page + 1) + "<white>/<#91B2FF>" + (maxPage + 1),
            List.of("<#B7C7E8>" + total + " colors available", "<#B7C7E8>Select a color to apply it")));

        if (page < maxPage) {
            inv.setItem(53, buildItem(Material.ARROW, "<#91B2FF>Next →</#91B2FF>",
                List.of("<#B7C7E8>Page " + (page + 2) + "/" + (maxPage + 1))));
        }

        inv.setItem(47, buildItem(Material.BARRIER, "<#91B2FF>← Back</#91B2FF>", null));
        fillGlass(inv, 54);
        return inv;
    }


    public static Inventory buildPermissions(MineNightPerms plugin, Group g, int page, String filter) {
        List<String> all = PermissionCollector.getAllPermissions();
        List<String> filtered = filter.isBlank()
            ? all
            : all.stream()
                 .filter(p -> p.toLowerCase().contains(filter.toLowerCase()))
                 .collect(Collectors.toList());

        int perPage = 36;
        int total   = filtered.size();
        int maxPage = total == 0 ? 0 : (total - 1) / perPage;
        page = Math.max(0, Math.min(page, maxPage));

        Inventory inv = Bukkit.createInventory(null, 54,
            parse("<gradient:#4B63C4:#91B2FF><bold>Perms: </bold></gradient><white>" + g.getName()));

        int start = page * perPage;
        int end   = Math.min(start + perPage, total);
        List<String> groupPerms = g.getPermissions();

        for (int i = start; i < end; i++) {
            String perm = filtered.get(i);
            boolean has = groupPerms.contains(perm);
            inv.setItem(i - start, buildItem(
                has ? Material.LIME_DYE : Material.GRAY_DYE,
                (has ? "<#91B2FF>" : "<#B7C7E8>") + perm,
                List.of(has ? "<#91B2FF>✔ Has permission" : "<#6685E8>✗ No permission",
                        "<#B7C7E8>Click to toggle")));
        }

        if (page > 0)
            inv.setItem(45, buildItem(Material.ARROW, "<#91B2FF>← Prev</#91B2FF>",
                List.of("<#B7C7E8>Page " + page + "/" + (maxPage + 1))));
        if (page < maxPage)
            inv.setItem(53, buildItem(Material.ARROW, "<#91B2FF>Next →</#91B2FF>",
                List.of("<#B7C7E8>Page " + (page + 2) + "/" + (maxPage + 1))));

        inv.setItem(49, buildItem(Material.ARROW, "<#91B2FF>← Back</#91B2FF>", null));
        fillGlass(inv, 54);
        return inv;
    }


    public static Inventory buildPlayerList(MineNightPerms plugin, org.bukkit.entity.Player viewer) {
        var players = Bukkit.getOnlinePlayers();
        int size = Math.max(27, Math.min(((players.size() / 9) + 2) * 9, 54));
        Inventory inv = Bukkit.createInventory(null, size,
            parse("<gradient:#4B63C4:#91B2FF><bold>Assign Role</bold></gradient>"));

        int slot = 0;
        for (var p : players) {
            if (slot >= size - 9) break;
            var pd = plugin.getPlayerDataManager().getPlayerData(p.getUniqueId());
            ItemStack icon = buildItem(de.minenight.allinone.GuiPlayerIcons.material(viewer),
                "<white>" + p.getName(),
                List.of("<#B7C7E8>Current group: <white>" + pd.getGroupName()));
            de.minenight.allinone.GuiPlayerIcons.applyProfile(icon, p);
            inv.setItem(slot++, icon);
        }

        inv.setItem(size - 1, buildItem(Material.ARROW, "<#91B2FF>← Back</#91B2FF>", null));
        fillGlass(inv, size);
        return inv;
    }


    public static Inventory buildGroupPick(MineNightPerms plugin, String playerName) {
        List<Group> groupList = new ArrayList<>(plugin.getGroupManager().getGroups());
        int size = Math.max(27, Math.min(((groupList.size() / 9) + 2) * 9, 54));
        Inventory inv = Bukkit.createInventory(null, size,
            parse("<gradient:#4B63C4:#91B2FF><bold>Role for: </bold></gradient><white>" + playerName));

        int slot = 0;
        for (Group g : groupList) {
            if (slot >= size - 9) break;
            String role    = g.getFormattedRole();
            String display = role.isEmpty() ? "<white>" + g.getName() : role;
            inv.setItem(slot++, buildItem(Material.PLAYER_HEAD, display,
                List.of("<#B7C7E8>Group: <white>" + g.getName(), "<#B7C7E8>Priority: <white>" + g.getPriority())));
        }

        inv.setItem(size - 1, buildItem(Material.ARROW, "<#91B2FF>← Back</#91B2FF>", null));
        fillGlass(inv, size);
        return inv;
    }
}
