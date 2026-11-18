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
            parse("<gradient:#8175AE:#D5DCE8><bold>MineNightPerms</bold></gradient>"));

        inv.setItem(10, buildItem(Material.CHEST,
            "<green><bold>Manage Groups</bold></green>",
            List.of("<gray>Create, edit and delete groups")));

        inv.setItem(13, buildItem(Material.BOOK,
            "<gold><bold>Manage Permissions</bold></gold>",
            List.of("<gray>Add or remove permissions per group")));

        inv.setItem(16, buildItem(Material.NAME_TAG,
            "<aqua><bold>Assign Roles</bold></aqua>",
            List.of("<gray>Set a player's group")));

        fillGlass(inv, 27);
        return inv;
    }


    public static Inventory buildGroupList(MineNightPerms plugin) {
        List<Group> groupList = new ArrayList<>(plugin.getGroupManager().getGroups());
        int rows = Math.max(3, (int) Math.ceil((groupList.size() + 9) / 9.0) + 1);
        int size = Math.min(rows * 9, 54);
        Inventory inv = Bukkit.createInventory(null, size,
            parse("<gradient:#8175AE:#D5DCE8><bold>All Groups</bold></gradient>"));

        int slot = 0;
        for (Group g : groupList) {
            if (slot >= size - 9) break;
            String role    = g.getFormattedRole();
            String display = role.isEmpty() ? "<white>" + g.getName() : role;
            inv.setItem(slot++, buildItem(Material.PLAYER_HEAD,
                display,
                List.of(
                    "<gray>Group: <white>" + g.getName(),
                    "<gray>Left click to edit",
                    "<gray>Priority: <white>" + g.getPriority(),
                    "<gray>Permissions: <white>" + g.getPermissions().size())));
        }

        inv.setItem(size - 5, buildItem(Material.PAPER,
            "<green><bold>+ New Group</bold></green>",
            List.of("<gray>Click to create a new group")));
        inv.setItem(size - 1, buildItem(Material.ARROW, "<yellow>← Back</yellow>", null));
        fillGlass(inv, size);
        return inv;
    }









    public static Inventory buildGroupEditor(MineNightPerms plugin, Group g) {
        Inventory inv = Bukkit.createInventory(null, 45,
            parse("<gradient:#8175AE:#D5DCE8><bold>Edit: </bold></gradient><white>" + g.getName()));

        inv.setItem(10, buildItem(Material.GREEN_DYE,
            "<green><bold>Role Color</bold></green>",
            List.of("<gray>Choose Gradients or Solid Colors",
                    "<gray>Current: <white>" + g.getRoleGradient())));

        inv.setItem(12, buildItem(Material.RED_DYE,
            "<red><bold>Name Color</bold></red>",
            List.of("<gray>Choose Gradients or Solid Colors",
                    "<gray>Current: <white>" + g.getNameGradient())));

        inv.setItem(14, buildItem(Material.COMPARATOR,
            "<gold><bold>Permissions</bold></gold>",
            List.of("<gray>Manage permissions",
                    "<gray>Count: <white>" + g.getPermissions().size())));

        inv.setItem(19, buildItem(g.isRoleBold() ? Material.LIME_DYE : Material.GRAY_DYE,
            g.isRoleBold() ? "<green>ROLE BOLD: ON" : "<red>ROLE BOLD: OFF",
            List.of("<gray>Toggle bold for role prefix")));

        inv.setItem(21, buildItem(g.isNameBold() ? Material.LIME_DYE : Material.GRAY_DYE,
            g.isNameBold() ? "<green>NAME BOLD: ON" : "<red>NAME BOLD: OFF",
            List.of("<gray>Toggle bold for player name")));

        String emojiDisplay  = g.getEmoji().isBlank()  ? "<gray>None" : "<white>" + g.getEmoji();
        inv.setItem(23, buildItem(Material.FIREWORK_STAR,
            "<aqua><bold>Emoji Prefix</bold></aqua>",
            List.of("<gray>Emoji before role name (no color)",
                    "<gray>Current: " + emojiDisplay,
                    "<gray>Left click: pick emoji",
                    "<red>Right click: clear emoji")));

        String prefixDisplay = g.getPrefix().isBlank() ? "<gray>Not set (uses group name)" : "<white>" + g.getPrefix();
        inv.setItem(25, buildItem(Material.NAME_TAG,
            "<yellow><bold>Custom Prefix</bold></yellow>",
            List.of("<gray>Text shown as prefix (colored by Role Color)",
                    "<gray>Current: " + prefixDisplay,
                    "<gray>Left click: set prefix via chat",
                    "<red>Right click: clear prefix")));

        inv.setItem(28, buildItem(Material.ARROW,
            "<yellow><bold>Priority -</bold></yellow>",
            List.of("<gray>Left: -1   Right: -10")));

        inv.setItem(30, buildItem(Material.PAPER,
            "<white><bold>Priority: <aqua>" + g.getPriority() + "</bold>",
            List.of("<gray>Higher = higher role priority",
                    "<gray>Owner: 100, Admin: 90, ..., Default: 0")));

        inv.setItem(32, buildItem(Material.ARROW,
            "<yellow><bold>Priority +</bold></yellow>",
            List.of("<gray>Left: +1   Right: +10")));

        if (!g.getName().equalsIgnoreCase("default")) {
            inv.setItem(16, buildItem(Material.TNT,
                "<red><bold>Delete Group</bold></red>",
                List.of("<gray>Permanently delete this group",
                        "<red>Cannot be undone!")));
        }

        inv.setItem(34, buildItem(Material.LIME_STAINED_GLASS_PANE,
            "<green><bold>✔ Save & Apply</bold></green>",
            List.of("<gray>Save all changes")));

        inv.setItem(40, buildItem(Material.ARROW, "<yellow>← Back</yellow>", null));
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
            parse("<gradient:#8175AE:#D5DCE8><bold>Emoji Picker</bold></gradient><white>: " + groupName));

        for (int i = 0; i < EMOJIS.size() && i < 45; i++) {
            String sym = EMOJIS.get(i);
            boolean isCombo = sym.contains("+") || sym.contains("★") || sym.contains("◆")
                              || sym.contains("●") || sym.contains("▲") || sym.contains("✦") || sym.contains("✧");
            String label = isCombo
                ? "<aqua><bold>" + sym
                : "<white><bold>" + sym;
            List<String> lore = isCombo
                ? List.of("<gray>Symbol combo — click to set", "<aqua>Preview: " + sym + " Rolle")
                : List.of("<gray>Click to set as emoji prefix");
            inv.setItem(i, buildItem(Material.PAPER, label, lore));
        }

        inv.setItem(45, buildItem(Material.ARROW, "<yellow>← Back</yellow>", null));

        inv.setItem(46, buildItem(Material.WRITABLE_BOOK,
            "<gold><bold>✎ Custom Symbol...</bold></gold>",
            List.of(
                "<gray>Type any text as symbol in chat",
                "<gray>Examples: <white>📷+  +★  [VIP]  ≫",
                "<yellow>Click to open chat input")));

        inv.setItem(49, buildItem(Material.BARRIER, "<red><bold>✗ No Symbol</bold></red>",
            List.of("<gray>Remove emoji/symbol prefix")));

        fillGlass(inv, 54);
        return inv;
    }


    
    public static Inventory buildColorCategoryPicker(String title, String currentColor) {
        Inventory inv = Bukkit.createInventory(null, 27, parse(title));

        inv.setItem(11, buildItem(Material.FIREWORK_STAR,
            "<gradient:#00D5FF:#8B5CF6><bold>Gradients</bold></gradient>",
            List.of(
                "<gray>Choose a multi-color gradient",
                "<gray>Available: <white>" + GradientPresets.GRADIENTS.size(),
                "<yellow>Click to browse gradients"
            )));

        inv.setItem(15, buildItem(Material.LIME_DYE,
            "<aqua><bold>Solid Colors</bold></aqua>",
            List.of(
                "<gray>Choose one normal, single color",
                "<gray>Available: <white>" + GradientPresets.SOLID_COLORS.size(),
                "<yellow>Click to browse solid colors"
            )));

        inv.setItem(13, buildItem(Material.NAME_TAG,
            "<white><bold>Current Color</bold>",
            List.of("<gray>Current value:", "<white>" + (currentColor == null ? "white" : currentColor))));

        inv.setItem(22, buildItem(Material.ARROW, "<yellow>← Back</yellow>", null));
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
            inv.setItem(45, buildItem(Material.ARROW, "<yellow>← Prev</yellow>",
                List.of("<gray>Page " + page + "/" + (maxPage + 1))));
        }

        inv.setItem(49, buildItem(Material.PAPER,
            "<white>Page <yellow>" + (page + 1) + "<white>/<yellow>" + (maxPage + 1),
            List.of("<gray>" + total + " colors available", "<gray>Select a color to apply it")));

        if (page < maxPage) {
            inv.setItem(53, buildItem(Material.ARROW, "<yellow>Next →</yellow>",
                List.of("<gray>Page " + (page + 2) + "/" + (maxPage + 1))));
        }

        inv.setItem(47, buildItem(Material.BARRIER, "<yellow>← Back</yellow>", null));
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
            parse("<gradient:#8175AE:#D5DCE8><bold>Perms: </bold></gradient><white>" + g.getName()));

        int start = page * perPage;
        int end   = Math.min(start + perPage, total);
        List<String> groupPerms = g.getPermissions();

        for (int i = start; i < end; i++) {
            String perm = filtered.get(i);
            boolean has = groupPerms.contains(perm);
            inv.setItem(i - start, buildItem(
                has ? Material.LIME_DYE : Material.GRAY_DYE,
                (has ? "<green>" : "<gray>") + perm,
                List.of(has ? "<green>✔ Has permission" : "<red>✗ No permission",
                        "<gray>Click to toggle")));
        }

        if (page > 0)
            inv.setItem(45, buildItem(Material.ARROW, "<yellow>← Prev</yellow>",
                List.of("<gray>Page " + page + "/" + (maxPage + 1))));
        if (page < maxPage)
            inv.setItem(53, buildItem(Material.ARROW, "<yellow>Next →</yellow>",
                List.of("<gray>Page " + (page + 2) + "/" + (maxPage + 1))));

        inv.setItem(49, buildItem(Material.ARROW, "<yellow>← Back</yellow>", null));
        fillGlass(inv, 54);
        return inv;
    }


    public static Inventory buildPlayerList(MineNightPerms plugin) {
        var players = Bukkit.getOnlinePlayers();
        int size = Math.max(27, Math.min(((players.size() / 9) + 2) * 9, 54));
        Inventory inv = Bukkit.createInventory(null, size,
            parse("<gradient:#8175AE:#D5DCE8><bold>Assign Role</bold></gradient>"));

        int slot = 0;
        for (var p : players) {
            if (slot >= size - 9) break;
            var pd = plugin.getPlayerDataManager().getPlayerData(p.getUniqueId());
            inv.setItem(slot++, buildItem(Material.PLAYER_HEAD,
                "<white>" + p.getName(),
                List.of("<gray>Current group: <white>" + pd.getGroupName())));
        }

        inv.setItem(size - 1, buildItem(Material.ARROW, "<yellow>← Back</yellow>", null));
        fillGlass(inv, size);
        return inv;
    }


    public static Inventory buildGroupPick(MineNightPerms plugin, String playerName) {
        List<Group> groupList = new ArrayList<>(plugin.getGroupManager().getGroups());
        int size = Math.max(27, Math.min(((groupList.size() / 9) + 2) * 9, 54));
        Inventory inv = Bukkit.createInventory(null, size,
            parse("<gradient:#8175AE:#D5DCE8><bold>Role for: </bold></gradient><white>" + playerName));

        int slot = 0;
        for (Group g : groupList) {
            if (slot >= size - 9) break;
            String role    = g.getFormattedRole();
            String display = role.isEmpty() ? "<white>" + g.getName() : role;
            inv.setItem(slot++, buildItem(Material.PLAYER_HEAD, display,
                List.of("<gray>Group: <white>" + g.getName(), "<gray>Priority: <white>" + g.getPriority())));
        }

        inv.setItem(size - 1, buildItem(Material.ARROW, "<yellow>← Back</yellow>", null));
        fillGlass(inv, size);
        return inv;
    }
}
