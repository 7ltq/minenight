package de.minenightperms.commands;

import de.minenightperms.MineNightPerms;
import de.minenightperms.model.Group;
import de.minenightperms.util.GradientPresets;
import de.minenightperms.util.PermissionCollector;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class MineNightPermsCommand implements CommandExecutor, TabCompleter {

    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final MineNightPerms plugin;

    
    private static final List<String> NAMED_COLORS = List.of(
        "white", "gray", "dark_gray", "red", "dark_red", "green", "dark_green",
        "blue", "dark_blue", "aqua", "dark_aqua", "yellow", "gold",
        "dark_purple", "light_purple", "black"
    );

    public MineNightPermsCommand(MineNightPerms plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {

        if (args.length == 0) {
            if (!(sender instanceof Player player)) { sendHelp(sender); return true; }
            if (!player.hasPermission("minenightperms.admin")) {
                player.sendMessage(MM.deserialize("<red>No permission!")); return true;
            }
            plugin.getGuiListener().openMainMenu(player);
            return true;
        }

        if (!sender.hasPermission("minenightperms.admin")) {
            sender.sendMessage(MM.deserialize("<red>No permission!")); return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.getGroupManager().loadGroups();
            plugin.getPlayerDataManager().loadPlayers();
            sender.sendMessage(MM.deserialize("<green>✔ MineNightPerms reloaded!"));
            return true;
        }

        if (args[0].equalsIgnoreCase("create")) {
            if (args.length != 2 || !args[1].matches("[A-Za-z0-9_-]{1,32}")) {
                sender.sendMessage(MM.deserialize("<red>Usage: /mnperms create <name> (letters, numbers, _ or -)"));
                return true;
            }
            String name = args[1];
            if (plugin.getGroupManager().groupExists(name)) {
                sender.sendMessage(MM.deserialize("<red>This group already exists."));
                return true;
            }
            plugin.getGroupManager().createGroup(name);
            sender.sendMessage(MM.deserialize("<green>Group created: <white>" + name));
            if (sender instanceof Player player) {
                plugin.getGuiListener().openGroupList(player);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("group") && args.length >= 3) {
            String groupName = args[1];
            if (!plugin.getGroupManager().groupExists(groupName)) {
                sender.sendMessage(MM.deserialize(
                    "<red>Group <white>" + groupName + "<red> not found!"));
                return true;
            }
            Group g = plugin.getGroupManager().getGroup(groupName);
            assert g != null;

            String sub = args[2].toLowerCase();

            if (sub.equals("permission") && args.length >= 4) {
                return handlePermission(sender, g, groupName, args);
            }


            if (sub.equals("setprefix")) {
                if (args.length < 4) {
                    sender.sendMessage(MM.deserialize("<red>Usage: /minenightperms group <group> setprefix <text>"));
                    return true;
                }

                String prefix = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                g.setPrefix(prefix);
                plugin.getGroupManager().saveGroups();
                sender.sendMessage(MM.deserialize(
                    "<green>✔ Prefix for <white>" + groupName + "<green> set to: <white>" + prefix));
                return true;
            }

            if (sub.equals("setemoji")) {
                if (args.length < 4) {
                    sender.sendMessage(MM.deserialize("<red>Usage: /mnperms group <name> setemoji <text>"));
                    return true;
                }
                String emoji = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length));
                g.setEmoji(emoji);
                plugin.getGroupManager().saveGroups();
                sender.sendMessage(MM.deserialize("<green>Symbol updated for <white>" + groupName));
                return true;
            }


            if (sub.equals("clearprefix")) {
                g.setPrefix("");
                plugin.getGroupManager().saveGroups();
                sender.sendMessage(MM.deserialize(
                    "<green>✔ Prefix cleared for <white>" + groupName +
                    "<green> (now uses group name)"));
                return true;
            }


            if (sub.equals("setcolor") && args.length >= 5) {
                String target = args[3].toLowerCase();
                if (!target.equals("role") && !target.equals("name")) {
                    sender.sendMessage(MM.deserialize("<red>Usage: /minenightperms group <group> setcolor role|name <color>"));
                    return true;
                }

                String color = resolveColor(String.join(" ", java.util.Arrays.copyOfRange(args, 4, args.length)));
                if (target.equals("role")) {
                    g.setRoleGradient(color);
                    sender.sendMessage(MM.deserialize(
                        "<green>✔ Role color for <white>" + groupName + "<green> set to: <white>" + color));
                } else {
                    g.setNameGradient(color);
                    sender.sendMessage(MM.deserialize(
                        "<green>✔ Name color for <white>" + groupName + "<green> set to: <white>" + color));
                }
                plugin.getGroupManager().saveGroups();
                return true;
            }


            if (sub.equals("setbold") && args.length >= 4) {
                String target = args[3].toLowerCase();
                if (!target.equals("role") && !target.equals("name")) {
                    sender.sendMessage(MM.deserialize("<red>Usage: /minenightperms group <group> setbold role|name [true|false]"));
                    return true;
                }
                boolean value;
                if (args.length >= 5) {
                    value = !args[4].equalsIgnoreCase("false");
                } else {

                    value = target.equals("role") ? !g.isRoleBold() : !g.isNameBold();
                }
                if (target.equals("role")) {
                    g.setRoleBold(value);
                    sender.sendMessage(MM.deserialize(
                        "<green>✔ Role bold for <white>" + groupName + "<green>: " + (value ? "<green>ON" : "<red>OFF")));
                } else {
                    g.setNameBold(value);
                    sender.sendMessage(MM.deserialize(
                        "<green>✔ Name bold for <white>" + groupName + "<green>: " + (value ? "<green>ON" : "<red>OFF")));
                }
                plugin.getGroupManager().saveGroups();
                return true;
            }

            if (sub.equals("info")) {
                sender.sendMessage(MM.deserialize("<gradient:#8175AE:#D5DCE8><bold>══ Group: " + g.getName() + " ══</bold></gradient>"));
                sender.sendMessage(MM.deserialize("<gray>Priority:    <white>" + g.getPriority()));
                sender.sendMessage(MM.deserialize("<gray>Prefix:      <white>" + (g.getPrefix().isBlank() ? "(group name)" : g.getPrefix())));
                sender.sendMessage(MM.deserialize("<gray>Emoji:       <white>" + (g.getEmoji().isBlank() ? "(none)" : g.getEmoji())));
                sender.sendMessage(MM.deserialize("<gray>Role color:  <white>" + g.getRoleGradient()));
                sender.sendMessage(MM.deserialize("<gray>Role bold:   <white>" + g.isRoleBold()));
                sender.sendMessage(MM.deserialize("<gray>Name color:  <white>" + g.getNameGradient()));
                sender.sendMessage(MM.deserialize("<gray>Name bold:   <white>" + g.isNameBold()));
                sender.sendMessage(MM.deserialize("<gray>Permissions: <white>" + g.getPermissions().size()));
                String preview = g.getFormattedRole();
                if (!preview.isEmpty()) {
                    sender.sendMessage(MM.deserialize("<gray>Preview:     " + preview + " <white>PlayerName"));
                }
                return true;
            }

            sendHelp(sender);
            return true;
        }

        if (args[0].equalsIgnoreCase("player") && args.length >= 5
                && args[1].equalsIgnoreCase("set") && args[2].equalsIgnoreCase("role")) {
            Player target = Bukkit.getPlayer(args[3]);
            String group  = args[4];
            if (target == null) { sender.sendMessage(MM.deserialize("<red>Player not found!")); return true; }
            if (!plugin.getGroupManager().groupExists(group)) { sender.sendMessage(MM.deserialize("<red>Group not found!")); return true; }
            plugin.getPlayerDataManager().setPlayerGroup(target.getUniqueId(), group.toLowerCase());
            sender.sendMessage(MM.deserialize("<green>✔ <white>" + target.getName() + "<green> → <white>" + group));
            return true;
        }

        sendHelp(sender);
        return true;
    }


    private boolean handlePermission(CommandSender sender, Group g, String groupName, String[] args) {
        String sub = args[3].toLowerCase();

        if (sub.equals("set") && args.length >= 5) {
            String permArg = args[4];
            boolean value  = args.length < 6 || !args[5].equalsIgnoreCase("false");

            if (permArg.equalsIgnoreCase("all")) {
                List<String> all = new ArrayList<>(PermissionCollector.getAllPermissions());
                g.setPermissions(all);
                plugin.getGroupManager().saveGroups();
                sender.sendMessage(MM.deserialize(
                    "<green>✔ Set ALL <white>" + all.size() + "<green> permissions to <white>" + groupName));
                return true;
            }

            List<String> perms = new ArrayList<>(g.getPermissions());
            if (value) {
                if (!perms.contains(permArg)) {
                    perms.add(permArg);
                    sender.sendMessage(MM.deserialize("<green>✔ Added <white>" + permArg + "<green> to <white>" + groupName));
                } else {
                    sender.sendMessage(MM.deserialize("<yellow>Already has <white>" + permArg));
                }
            } else {
                perms.remove(permArg);
                sender.sendMessage(MM.deserialize("<green>✔ Removed <white>" + permArg + "<green> from <white>" + groupName));
            }
            g.setPermissions(perms);
            plugin.getGroupManager().saveGroups();
            return true;
        }

        if (sub.equals("unset") && args.length >= 5) {
            List<String> perms = new ArrayList<>(g.getPermissions());
            perms.remove(args[4]);
            g.setPermissions(perms);
            plugin.getGroupManager().saveGroups();
            sender.sendMessage(MM.deserialize("<green>✔ Removed <white>" + args[4] + "<green> from <white>" + groupName));
            return true;
        }

        if (sub.equals("info")) {
            List<String> perms = g.getPermissions();
            sender.sendMessage(MM.deserialize("<gold>══ Permissions: <white>" + groupName + " <gold>(" + perms.size() + ") ══"));
            if (perms.isEmpty()) {
                sender.sendMessage(MM.deserialize("<gray>  No permissions set."));
            } else {
                for (String p : perms)
                    sender.sendMessage(MM.deserialize("<green>  + <white>" + p));
            }
            return true;
        }

        sendHelp(sender);
        return true;
    }


    
    private String resolveColor(String input) {
        if (input == null || input.isBlank()) return "white";

        if (input.startsWith("<gradient")) return input;

        if (NAMED_COLORS.contains(input.toLowerCase())) return input.toLowerCase();

        for (var preset : GradientPresets.PRESETS) {

            String cleanName = preset.name().replaceAll("[^a-zA-Z0-9 ]", "").trim();
            if (cleanName.equalsIgnoreCase(input) || preset.name().equalsIgnoreCase(input)) {
                return preset.gradient();
            }
        }

        return input;
    }


    private void sendHelp(CommandSender s) {
        s.sendMessage(MM.deserialize("<gradient:#8175AE:#D5DCE8><bold>══ MineNightPerms Help ══</bold></gradient>"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms <gray>- Open GUI"));
        s.sendMessage(MM.deserialize("<yellow>/mnperms create <name> <gray>- Create a group"));
        s.sendMessage(MM.deserialize("<yellow>/mnperms group <name> setemoji <text>"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms reload <gray>- Reload full config"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms group <g> info"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms group <g> setprefix <text>"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms group <g> clearprefix"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms group <g> setcolor role|name <color|preset>"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms group <g> setbold role|name [true|false]"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms group <g> permission set <perm|all> [true/false]"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms group <g> permission unset <perm>"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms group <g> permission info"));
        s.sendMessage(MM.deserialize("<yellow>/minenightperms player set role <player> <group>"));
    }


    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("minenightperms.admin")) return List.of();
        String last = args[args.length - 1].toLowerCase();
        List<String> r = new ArrayList<>();

        if (args.length == 1) return filter(List.of("create", "group", "player", "reload"), last);

        if (args.length == 2) {
            if (args[0].equalsIgnoreCase("group")) {
                plugin.getGroupManager().getGroups().forEach(g -> r.add(g.getName()));
                return filter(r, last);
            }
            if (args[0].equalsIgnoreCase("player")) return filter(List.of("set"), last);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("group")) {
            return filter(List.of(
                "permission", "setprefix", "clearprefix", "setemoji",
                "setcolor", "setbold", "info"), last);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("group")) {
            return switch (args[2].toLowerCase()) {
                case "permission" -> filter(List.of("set", "unset", "info"), last);
                case "setcolor"   -> filter(List.of("role", "name"), last);
                case "setbold"    -> filter(List.of("role", "name"), last);
                default           -> List.of();
            };
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("group")) {
            return switch (args[2].toLowerCase()) {
                case "permission" -> {
                    String sub = args[3].toLowerCase();
                    if (sub.equals("set")) {
                        List<String> all = new ArrayList<>(PermissionCollector.getAllPermissions());
                        all.add(0, "all");
                        yield filter(all, last);
                    }
                    if (sub.equals("unset")) {
                        Group g = plugin.getGroupManager().getGroup(args[1]);
                        yield g != null ? filter(g.getPermissions(), last) : List.of();
                    }
                    yield List.of();
                }
                case "setcolor" -> {
                    List<String> options = new ArrayList<>(NAMED_COLORS);
                    GradientPresets.PRESETS.forEach(p -> options.add(p.name()));
                    yield filter(options, last);
                }
                case "setbold" -> filter(List.of("true", "false"), last);
                default        -> List.of();
            };
        }

        if (args.length == 6 && args[0].equalsIgnoreCase("group")
                && args[2].equalsIgnoreCase("permission")
                && args[3].equalsIgnoreCase("set")) {
            return filter(List.of("true", "false"), last);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("player")
                && args[1].equalsIgnoreCase("set")
                && args[2].equalsIgnoreCase("role")) {
            Bukkit.getOnlinePlayers().forEach(p -> r.add(p.getName()));
            return filter(r, last);
        }

        if (args.length == 5 && args[0].equalsIgnoreCase("player")) {
            plugin.getGroupManager().getGroups().forEach(g -> r.add(g.getName()));
            return filter(r, last);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("player")) {
            return filter(List.of("set"), last);
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("player")) {
            return filter(List.of("role"), last);
        }

        return r;
    }

    private List<String> filter(List<String> list, String input) {
        return list.stream()
            .filter(s -> s.toLowerCase().startsWith(input))
            .toList();
    }
}
