package de.minenight.allinone;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

public final class MineNightTabCompleter implements TabCompleter {
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (args.length == 0) return List.of();
        List<String> options = switch (name) {
            case "tpa", "tpahere" -> args.length == 1 ? players(sender) : List.of();
            case "tpaccept", "tpadecline" -> args.length == 1 ? players(sender) : List.of();
            case "prac" -> sender.hasPermission("minenightspawn.admin") ? switch (args.length) {
                case 1 -> List.of("spawn");
                case 2 -> equalsIgnoreCase(args[0], "spawn") ? List.of("set") : List.of();
                case 3 -> equalsIgnoreCase(args[0], "spawn") && equalsIgnoreCase(args[1], "set") ? List.of("here") : List.of();
                default -> List.of();
            } : List.of();
            case "minenighttab" -> args.length == 1 && sender.hasPermission("minenighttab.admin") ? List.of("reload") : List.of();
            case "rtpreload" -> List.of();
            case "nomobs", "noblockdrops" -> args.length == 1 && sender.isOp() ? List.of("on", "off", "status") : List.of();
            case "mnperf" -> sender.isOp() ? switch (args.length) {
                case 1 -> List.of("reload", "mobs", "drops");
                case 2 -> equalsIgnoreCase(args[0], "mobs") || equalsIgnoreCase(args[0], "drops") ? List.of("on", "off", "status") : List.of();
                default -> List.of();
            } : List.of();
            case "no" -> sender.isOp() ? switch (args.length) {
                case 1 -> List.of("block");
                case 2 -> equalsIgnoreCase(args[0], "block") ? List.of("drop") : List.of();
                case 3 -> equalsIgnoreCase(args[0], "block") && equalsIgnoreCase(args[1], "drop") ? List.of("on", "off", "status") : List.of();
                default -> List.of();
            } : List.of();
            default -> List.of();
        };
        return filter(options, args[args.length - 1]);
    }

    private static boolean equalsIgnoreCase(String a, String b) { return a.equalsIgnoreCase(b); }

    private static List<String> players(CommandSender sender) {
        List<String> names = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (sender instanceof Player from && player.getUniqueId().equals(from.getUniqueId())) continue;
            if (sender instanceof Player from && !from.hasPermission("minenight.moderation.staff") && !from.isOp() && !from.canSee(player)) continue;
            names.add(player.getName());
        }
        return names;
    }

    private static List<String> filter(Collection<String> options, String prefix) {
        String normalized = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(normalized)).sorted(String.CASE_INSENSITIVE_ORDER).toList();
    }
}
