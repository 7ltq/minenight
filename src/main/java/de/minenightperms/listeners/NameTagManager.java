package de.minenightperms.listeners;

import de.minenightperms.MineNightPerms;
import de.minenightperms.model.Group;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class NameTagManager implements Listener {
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final Pattern HEX = Pattern.compile("#[0-9a-fA-F]{6}");
    private static final Pattern OWN_TEAM = Pattern.compile("mn[0-9a-f]{13}");
    private final MineNightPerms plugin;
    private BukkitTask task;

    public NameTagManager(MineNightPerms plugin) {
        this.plugin = plugin;
    }

    public void start() {
        removeOldDisplays();
        refresh();
        task = Bukkit.getScheduler().runTaskTimer(plugin.core(), this::refresh, 20L, 20L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (Scoreboard board : boards()) {
            for (Team team : new HashSet<>(board.getTeams())) {
                if (OWN_TEAM.matcher(team.getName()).matches()) team.unregister();
            }
        }
    }

    public void setVisibleTo(Player viewer, UUID playerId, boolean visible) {
    }

    private Set<Scoreboard> boards() {
        Set<Scoreboard> result = new HashSet<>();
        result.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player viewer : Bukkit.getOnlinePlayers()) result.add(viewer.getScoreboard());
        return result;
    }

    private String teamName(UUID id) {
        return "mn" + id.toString().replace("-", "").substring(0, 13);
    }

    private void refresh() {
        java.util.Map<UUID, Group> groups = new java.util.HashMap<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            Group group = plugin.getGroupManager().getGroup(plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getGroupName());
            if (group != null) groups.put(player.getUniqueId(), group);
        }
        for (Scoreboard board : boards()) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                Group group = groups.get(player.getUniqueId());
                if (group == null) continue;
                String name = teamName(player.getUniqueId());
                Team team = board.getTeam(name);
                if (team == null) team = board.registerNewTeam(name);
                Team previous = board.getEntryTeam(player.getName());
                if (previous != null && previous != team) previous.removeEntry(player.getName());
                if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
                String role = group.getFormattedRole();
                Component prefix = role.isBlank() ? Component.empty() : MINI.deserialize(role + "<reset> ");
                if (!team.prefix().equals(prefix)) team.prefix(prefix);
                NamedTextColor color = nameColor(group.getNameGradient(), group.getRoleGradient());
                team.color(color);
                if (team.getOption(Team.Option.NAME_TAG_VISIBILITY) != Team.OptionStatus.ALWAYS) {
                    team.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                }
                if (!team.suffix().equals(Component.empty())) team.suffix(Component.empty());
            }
            for (Team team : new HashSet<>(board.getTeams())) {
                if (!OWN_TEAM.matcher(team.getName()).matches()) continue;
                for (String entry : new HashSet<>(team.getEntries())) {
                    if (Bukkit.getPlayerExact(entry) == null) team.removeEntry(entry);
                }
            }
        }
    }

    private NamedTextColor nameColor(String configured, String roleColor) {
        NamedTextColor chosen = parseNameColor(configured);
        if (chosen == null || chosen == NamedTextColor.WHITE) {
            NamedTextColor role = parseNameColor(roleColor);
            if (role != null && role != NamedTextColor.WHITE) return role;
        }
        return chosen == null ? NamedTextColor.WHITE : chosen;
    }

    private NamedTextColor parseNameColor(String configured) {
        if (configured == null || configured.isBlank()) return null;
        Matcher matcher = HEX.matcher(configured);
        if (matcher.find()) {
            TextColor hex = TextColor.fromHexString(matcher.group());
            if (hex != null) return NamedTextColor.nearestTo(hex);
        }
        String normalized = configured.toLowerCase(java.util.Locale.ROOT).trim();
        if (normalized.startsWith("<gradient:") || normalized.startsWith("gradient:")) {
            normalized = normalized.replace("<", "").replace(">", "");
            String[] stops = normalized.substring("gradient:".length()).split(":");
            for (String stop : stops) {
                NamedTextColor color = NamedTextColor.NAMES.value(stop.replace('-', '_'));
                if (color != null && color != NamedTextColor.WHITE) return color;
            }
            return NamedTextColor.WHITE;
        }
        normalized = normalized.replace("<", "").replace(">", "").replace('-', '_');
        return NamedTextColor.NAMES.value(normalized);
    }

    private void removeOldDisplays() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            for (var passenger : player.getPassengers()) {
                if (passenger instanceof TextDisplay display && !display.isPersistent()
                        && display.isSilent() && display.isInvulnerable()) display.remove();
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTask(plugin.core(), () -> {
            removeOldDisplays();
            refresh();
        });
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Bukkit.getScheduler().runTask(plugin.core(), this::refresh);
    }
}
