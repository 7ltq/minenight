package de.minenightperms.listeners;

import de.minenightperms.MineNightPerms;
import de.minenight.allinone.MineNightModule;
import de.minenight.allinone.StaffToolsModule;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.bukkit.util.Transformation;

public final class NameTagManager implements Listener {
    private static final Pattern HEX = Pattern.compile("#[0-9A-Fa-f]{6}");
    private static final float BASE_NAMETAG_HEIGHT = 0.0f;
    private final MineNightPerms plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<UUID, TextDisplay> labels = new HashMap<>();
    private final Set<Scoreboard> boards = new HashSet<>();
    private BukkitTask task;
    private boolean mountWarningLogged;
    private boolean mountUnsupported;

    public NameTagManager(MineNightPerms plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin.core(), this::updateAll, 10L, 10L);
    }

    public void stop() {
        if (task != null) task.cancel();
        for (TextDisplay display : labels.values()) {
            if (display != null && display.isValid()) display.remove();
        }
        labels.clear();
        for (Scoreboard board : boards) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                removeOldTeams(board, player, null);
            }
        }
        boards.clear();
    }

    private String teamName(Player player) {
        String groupName = plugin.getPlayerDataManager().getPlayerData(player.getUniqueId()).getGroupName();
        var group = plugin.getGroupManager().getGroup(groupName);
        int priority = group == null ? 0 : group.getPriority();
        int boundedPriority = Math.max(-499999, Math.min(499999, priority));
        String playerId = player.getUniqueId().toString().replace("-", "");
        return "mn" + String.format(Locale.ROOT, "%06d", 499999 - boundedPriority) + playerId.substring(0, 8);
    }

    private void removeOldTeams(Scoreboard board, Player player, String desiredName) {
        String playerId = player.getUniqueId().toString().replace("-", "");
        String legacyName = "mn" + playerId.substring(0, 13);
        String suffix = playerId.substring(0, 8);
        for (Team team : new ArrayList<>(board.getTeams())) {
            String name = team.getName();
            if (name.equals(desiredName)) continue;
            boolean rankedName = name.length() == 16 && name.startsWith("mn")
                    && name.endsWith(suffix)
                    && name.substring(2, 8).chars().allMatch(Character::isDigit);
            if (!name.equals(legacyName) && !rankedName) continue;
            team.removeEntry(player.getName());
            if (team.getEntries().isEmpty()) team.unregister();
        }
    }

    private boolean isVanished(Player player) {
        MineNightModule module = plugin.core().module("stafftools");
        return module instanceof StaffToolsModule staffTools && staffTools.isVanished(player.getUniqueId());
    }

    private boolean isMediaPlayer(Player player) {
        String groupName = plugin.getPlayerDataManager()
                .getPlayerData(player.getUniqueId())
                .getGroupName();
        return "media".equalsIgnoreCase(groupName);
    }

    private Component roleNameSeparator(Player player) {
        // The Media prefix is intentionally compact: 📹+7ltq, with no spaces.
        // Every other role keeps the usual single space before the player name.
        return isMediaPlayer(player) ? Component.empty() : Component.space();
    }

    private Component roleComponent(Player player) {
        if (isMediaPlayer(player)) {
            // Build the plus as a literal, default-font '+' character instead of
            // parsing it through MiniMessage. This avoids special formatting,
            // custom glyph substitution, or inherited prefix styling stretching it.
            TextColor pink = TextColor.color(0xE52DBB);
            TextColor cyan = TextColor.color(0x00C8F0);
            return Component.text("📹").color(pink)
                    .append(Component.text("+").color(cyan));
        }
        return mm.deserialize(plugin.getPlayerRole(player.getUniqueId()));
    }

    private Component labelText(Player player) {
        Component role = roleComponent(player);
        Component name = mm.deserialize(plugin.getPlayerNameFormat(player.getUniqueId(), mm.escapeTags(player.getName())));
        return role.equals(Component.empty()) ? name : Component.empty().append(role).append(roleNameSeparator(player)).append(name);
    }

    private Component teamPrefix(Player player) {
        return roleComponent(player).append(roleNameSeparator(player));
    }

    private NamedTextColor nearestColor(Player player) {
        String value = plugin.getPlayerNameColorFormat(player.getUniqueId());
        Matcher match = HEX.matcher(value == null ? "" : value);
        if (match.find()) {
            TextColor color = TextColor.fromHexString(match.group());
            if (color != null) return NamedTextColor.nearestTo(color);
        }
        return NamedTextColor.WHITE;
    }

    private List<Scoreboard> activeBoards() {
        Set<Scoreboard> active = new HashSet<>();
        active.add(Bukkit.getScoreboardManager().getMainScoreboard());
        for (Player viewer : Bukkit.getOnlinePlayers()) active.add(viewer.getScoreboard());
        boards.addAll(active);
        return new ArrayList<>(active);
    }

    private void updateAll() {
        List<Scoreboard> active = activeBoards();
        boolean fullGradients = plugin.getConfig().getBoolean("nametags.full-gradients", true);
        double height = plugin.getConfig().getDouble("nametags.height-offset", 0.15);
        float translationY = BASE_NAMETAG_HEIGHT + (float) height;
        for (Player player : Bukkit.getOnlinePlayers()) {
            TextDisplay display = labels.get(player.getUniqueId());
            boolean attached = display != null && display.isValid() && display.getVehicle() == player;
            boolean vanished = isVanished(player);
            boolean custom = fullGradients && !mountUnsupported && !player.isDead() && !player.isInvisible() && !vanished && (attached || display == null);
            for (Scoreboard board : active) {
                String name = teamName(player);
                removeOldTeams(board, player, name);
                Team team = board.getTeam(name);
                if (team == null) team = board.registerNewTeam(name);
                if (!team.hasEntry(player.getName())) team.addEntry(player.getName());
                team.setOption(Team.Option.NAME_TAG_VISIBILITY, custom ? Team.OptionStatus.NEVER : Team.OptionStatus.ALWAYS);
                team.color(nearestColor(player));
                team.prefix(custom ? Component.empty() : teamPrefix(player));
                team.suffix(Component.empty());
            }
            if (!fullGradients || mountUnsupported || player.isDead() || player.isInvisible() || vanished) {
                removeDisplay(player.getUniqueId());
                continue;
            }
            if (!attached) {
                removeDisplay(player.getUniqueId());
                TextDisplay created = player.getWorld().spawn(player.getLocation(), TextDisplay.class, entity -> {
                    entity.setBillboard(Display.Billboard.CENTER);
                    entity.setDefaultBackground(false);
                    entity.setSeeThrough(false);
                    entity.setShadowed(true);
                    entity.setLineWidth(256);
                    entity.setGravity(false);
                    entity.setPersistent(false);
                    entity.setSilent(true);
                    entity.setInvulnerable(true);
                    entity.setTeleportDuration(0);
                    entity.setTransformation(new Transformation(new Vector3f(0, translationY, 0), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
                });
                created.text(labelText(player));
                if (player.addPassenger(created)) {
                    labels.put(player.getUniqueId(), created);
                } else {
                    created.remove();
                    mountUnsupported = true;
                    for (Scoreboard board : active) {
                        for (Player target : Bukkit.getOnlinePlayers()) {
                            Team fallback = board.getTeam(teamName(target));
                            if (fallback != null) {
                                fallback.setOption(Team.Option.NAME_TAG_VISIBILITY, Team.OptionStatus.ALWAYS);
                                fallback.color(nearestColor(target));
                                fallback.prefix(teamPrefix(target));
                            }
                        }
                    }
                    if (!mountWarningLogged) {
                        mountWarningLogged = true;
                        plugin.getLogger().warning("Could not attach TextDisplay to a player. Using vanilla nametags to avoid trailing labels.");
                    }
                }
            } else {
                display.text(labelText(player));
                display.setTransformation(new Transformation(new Vector3f(0, translationY, 0), new Quaternionf(), new Vector3f(1, 1, 1), new Quaternionf()));
            }
        }
        for (UUID id : new ArrayList<>(labels.keySet())) {
            if (Bukkit.getPlayer(id) == null) removeDisplay(id);
        }
    }

    private void removeDisplay(UUID id) {
        TextDisplay display = labels.remove(id);
        if (display != null && display.isValid()) display.remove();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin.core(), this::updateAll, 2L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        removeDisplay(event.getPlayer().getUniqueId());
        for (Scoreboard board : boards) {
            removeOldTeams(board, event.getPlayer(), null);
        }
    }
}
