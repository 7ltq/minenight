package de.minenight.tab;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.server.ServerListPingEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.HashMap;
import java.util.UUID;

public final class MineNightTab extends de.minenight.allinone.MineNightModule implements Listener {
    private final java.util.Set<UUID> hiddenIp = new java.util.HashSet<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private final Map<UUID, TabState> sentTab = new HashMap<>();
    private final Map<UUID, Component> sentNames = new HashMap<>();
    private record TabState(Component header, Component footer) {}
    private BukkitTask task;
    private BukkitTask actionbarTask;
    private volatile Component motd = Component.empty();
    private volatile boolean motdEnabled;
    private volatile int maxPlayersOverride = -1;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadIpPreferences();
        var ip = getCommand("ip");
        if (ip != null) {
            ip.setExecutor(this);
            ip.setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, core());
        startUpdates();
        startActionbar();
        getLogger().info("MineNightTab enabled.");
    }

    @Override
    public void onDisable() {
        if (task != null) task.cancel();
        if (actionbarTask != null) actionbarTask.cancel();
        sentTab.clear();
        sentNames.clear();
    }

    private void startUpdates() {
        if (task != null) task.cancel();
        sentTab.clear();
        sentNames.clear();
        FileConfiguration config = getConfig();
        motdEnabled = config.getBoolean("settings.motd-enabled", true);
        maxPlayersOverride = config.getInt("settings.motd-max-players", -1);
        refreshMotd();
        long interval = Math.max(20, config.getLong("settings.update-ticks", 40));
        task = Bukkit.getScheduler().runTaskTimer(core(), () -> {
            refreshMotd();
            boolean enabled = getConfig().getBoolean("settings.tab-enabled", true);
            if (!enabled) return;
            updateOrder();
            for (Player player : Bukkit.getOnlinePlayers()) updatePlayer(player);
        }, 1L, interval);
    }

    private void startActionbar() {
        if (actionbarTask != null) actionbarTask.cancel();
        if (!getConfig().getBoolean("actionbar.enabled", true)) return;
        long interval = Math.max(10L, getConfig().getLong("actionbar.interval-ticks", 40L));
        actionbarTask = Bukkit.getScheduler().runTaskTimer(core(), () -> {
            Component message = parse(getConfig().getString("actionbar.text", "<gradient:#4B63C4:#91B2FF>minenight.de</gradient>"));
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (!hiddenIp.contains(player.getUniqueId())) player.sendActionBar(message);
            }
        }, 20L, interval);
    }

    private void loadIpPreferences() {
        hiddenIp.clear();
        for (String value : getConfig().getStringList("actionbar.hidden-players")) {
            try {
                hiddenIp.add(UUID.fromString(value));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private int priority(Player player) {
        if (core().module("perms") instanceof de.minenightperms.MineNightPerms perms) {
            var group = perms.getGroupManager().getGroup(perms.getPlayerDataManager().getPlayerData(player.getUniqueId()).getGroupName());
            if (group != null) return group.getPriority();
        }
        return 0;
    }

    private boolean hasRank(Player player) {
        if (core().module("perms") instanceof de.minenightperms.MineNightPerms perms) {
            String key = perms.getPlayerDataManager().getPlayerData(player.getUniqueId()).getGroupName();
            return key != null && !key.equalsIgnoreCase("default") && perms.getGroupManager().groupExists(key);
        }
        return false;
    }

    private void updateOrder() {
        var players = new java.util.ArrayList<Player>(Bukkit.getOnlinePlayers());
        players.sort(java.util.Comparator.comparing(this::hasRank).reversed()
                .thenComparing(java.util.Comparator.comparingInt(this::priority).reversed())
                .thenComparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        for (int i = 0; i < players.size(); i++) {
            Player player = players.get(i);
            int order = players.size() - i;
            if (player.getPlayerListOrder() != order) player.setPlayerListOrder(order);
        }
    }

    private void updatePlayer(Player player) {
        List<String> header = getConfig().getStringList("tab.header");
        List<String> footer = getConfig().getStringList("tab.footer");
        UUID id = player.getUniqueId();
        TabState next = new TabState(formatLines(header, player), formatLines(footer, player));
        if (!next.equals(sentTab.get(id))) {
            player.sendPlayerListHeaderAndFooter(next.header(), next.footer());
            sentTab.put(id, next);
        }
        if (getConfig().getBoolean("settings.tab-player-names", true)) {
            Component name;
            if (isMediaPlayer(player)) {
                name = mediaPlayerListName(player);
            } else {
                String template = getConfig().getString("tab.player-name", "{prefix}{player}{suffix}");
                name = parse(apply(template, player));
            }
            if (!name.equals(sentNames.get(id))) {
                player.playerListName(name);
                sentNames.put(id, name);
            }
        }
    }


    private boolean isMediaPlayer(Player player) {
        Object perms = de.minenight.allinone.MineNightAllInOne.instance().module("perms");
        if (perms instanceof de.minenightperms.MineNightPerms mineNightPerms) {
            return mineNightPerms.isMediaPlayer(player.getUniqueId());
        }
        return false;
    }

    private Component mediaPlayerListName(Player player) {
        TextColor pink = TextColor.color(0xE52DBB);
        TextColor cyan = TextColor.color(0x00C8F0);
        Component formattedName = Component.text(player.getName()).color(pink);
        Object perms = de.minenight.allinone.MineNightAllInOne.instance().module("perms");
        if (perms instanceof de.minenightperms.MineNightPerms mineNightPerms) {
            formattedName = parse(mineNightPerms.getPlayerNameFormat(player.getUniqueId(), escape(player.getName())));
        }
        // Match the in-world Media nametag exactly: 📹 + player name, no spaces.
        // The plus is a normal text glyph rather than a stretched/custom icon.
        return Component.text("📹").color(pink)
            .append(Component.text("+").color(cyan))
            .append(formattedName);
    }

    private Component formatLines(List<String> lines, Player player) {
        return parse(apply(String.join("<newline>", lines), player));
    }

    private void refreshMotd() {
        List<String> lines = getConfig().getStringList("motd.lines");
        motd = formatLines(lines, null);
    }

    private Component parse(String input) {
        try {
            return miniMessage.deserialize(input);
        } catch (Exception ex) {
            return Component.text("MineNight");
        }
    }

    private String apply(String template, Player player) {
        String prefix = "";
        String suffix = "";
        String formattedPlayer = player == null ? "" : escape(player.getName());
        if (player != null && getConfig().getBoolean("settings.minenightperms-prefixes", true)
            && de.minenight.allinone.MineNightAllInOne.instance().module("perms") != null) {
            try {
                Object perms = de.minenight.allinone.MineNightAllInOne.instance().module("perms");
                Object result = perms.getClass().getMethod("getPlayerRole", java.util.UUID.class)
                    .invoke(perms, player.getUniqueId());
                if (result instanceof String value && !value.isBlank()) prefix = value + "<reset> ";
                Object nameResult = perms.getClass().getMethod("getPlayerNameFormat", java.util.UUID.class, String.class)
                    .invoke(perms, player.getUniqueId(), player.getName());
                if (nameResult instanceof String value && !value.isBlank()) formattedPlayer = value;
            } catch (ReflectiveOperationException ex) {
                getLogger().warning("Could not read MineNightPerms rank: " + ex.getMessage());
            }
        }
        if (prefix.isEmpty() && player != null && Bukkit.getPluginManager().isPluginEnabled("LuckPerms")) {
            try {
                LuckPerms luckPerms = LuckPermsProvider.get();
                User user = luckPerms.getPlayerAdapter(Player.class).getUser(player);
                if (getConfig().getBoolean("settings.luckperms-prefixes", true)) {
                    prefix = fromLegacy(user.getCachedData().getMetaData().getPrefix());
                }
                if (getConfig().getBoolean("settings.luckperms-suffixes", false)) {
                    suffix = fromLegacy(user.getCachedData().getMetaData().getSuffix());
                }
            } catch (Exception | LinkageError ignored) {
                prefix = "";
                suffix = "";
            }
        }
        int online = Bukkit.getOnlinePlayers().size();
        int max = Bukkit.getMaxPlayers();
        String ping = player == null ? "0" : Integer.toString(player.getPing());
        double tps = Bukkit.getTPS()[0];

        return template.replace("{online}", Integer.toString(online))
            .replace("{max}", Integer.toString(max))
            .replace("{ping}", ping)
            .replace("{tps}", String.format(Locale.ROOT, "%.1f", Math.min(tps, 20.0)))
            .replace("{player}", formattedPlayer)
            .replace("{prefix}", prefix)
            .replace("{suffix}", suffix);
    }

    private String escape(String text) {
        return miniMessage.escapeTags(text);
    }

    private String fromLegacy(String legacy) {
        if (legacy == null || legacy.isEmpty()) return "";
        if (legacy.contains("<") && legacy.contains(">")) return legacy;
        String normalized = legacy.replace('§', '&');
        return miniMessage.serialize(
            net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacyAmpersand().deserialize(normalized)
        );
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPing(ServerListPingEvent event) {
        if (!motdEnabled) return;
        event.motd(motd);
        if (maxPlayersOverride >= 0) event.setMaxPlayers(maxPlayersOverride);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(core(), () -> {
            if (event.getPlayer().isOnline() && getConfig().getBoolean("settings.tab-enabled", true)) {
                updateOrder();
                updatePlayer(event.getPlayer());
            }
        }, 5L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        sentTab.remove(id);
        sentNames.remove(id);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("ip") && args.length == 1) {
            return List.of("on", "off").stream().filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT))).toList();
        }
        return List.of();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("ip")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Player only.");
                return true;
            }
            UUID id = player.getUniqueId();
            if (args.length == 0) {
                player.sendMessage("IP display: " + (hiddenIp.contains(id) ? "OFF" : "ON"));
                player.sendMessage(Component.text("Website: ").append(Component.text("https://minenight.de", TextColor.color(0x91B2FF)).clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl("https://minenight.de"))));
                return true;
            }
            if (args.length != 1 || !(args[0].equalsIgnoreCase("on") || args[0].equalsIgnoreCase("off"))) {
                player.sendMessage("Usage: /ip <on|off>");
                return true;
            }
            boolean enabled = args[0].equalsIgnoreCase("on");
            if (enabled) hiddenIp.remove(id);
            else hiddenIp.add(id);
            getConfig().set("actionbar.hidden-players", hiddenIp.stream().map(UUID::toString).sorted().toList());
            saveConfig();
            player.sendActionBar(enabled && getConfig().getBoolean("actionbar.enabled", true)
                    ? parse(getConfig().getString("actionbar.text", "minenight.de")) : Component.empty());
            player.sendMessage("IP display: " + (enabled ? "ON" : "OFF"));
            return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("minenighttab.admin")) {
                sender.sendMessage(Component.text("You do not have permission."));
                return true;
            }
            reloadConfig();
            loadIpPreferences();
            startUpdates();
            startActionbar();
            sender.sendMessage(Component.text("MineNightTab configuration reloaded."));
            return true;
        }
        sender.sendMessage(Component.text("Usage: /minenighttab reload"));
        return true;
    }
}
