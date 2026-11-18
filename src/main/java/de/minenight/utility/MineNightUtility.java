package de.minenight.utility;

import de.minenight.allinone.MineNightModule;
import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class MineNightUtility extends MineNightModule implements Listener {
    private final Map<UUID, Location> deaths = new ConcurrentHashMap<>();
    private final Set<UUID> backInProgress = ConcurrentHashMap.newKeySet();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private File positions;

    @Override public void onEnable() {
        saveDefaultConfig();
        positions = new File(getDataFolder(), "deaths.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(positions);
        if (data.getConfigurationSection("players") != null) {
            for (String key : data.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(key);
                    String prefix = "players." + key + ".";
                    World world = Bukkit.getWorld(data.getString(prefix + "world", ""));
                    if (world != null) deaths.put(uuid, new Location(world, data.getDouble(prefix + "x"), data.getDouble(prefix + "y"), data.getDouble(prefix + "z"), (float) data.getDouble(prefix + "yaw"), (float) data.getDouble(prefix + "pitch")));
                } catch (IllegalArgumentException ignored) { }
            }
        }
        getCommand("back").setExecutor(this);
        getCommand("announcement").setExecutor(this);
        getCommand("discord").setExecutor(this);
        getCommand("website").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, core());
    }

    @EventHandler public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        deaths.put(player.getUniqueId(), player.getLocation().clone());
        savePositions();
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        if (backInProgress.contains(playerId)) return;
        PlayerTeleportEvent.TeleportCause cause = event.getCause();
        if (cause == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || cause == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT) return;
        Location from = event.getFrom();
        if (from.getWorld() == null) return;
        deaths.put(playerId, from.clone());
        savePositions();
    }

    private Location resolveSavedLocation(UUID playerId) {
        Location cached = deaths.get(playerId);
        if (cached != null && cached.getWorld() != null
                && Bukkit.getWorld(cached.getWorld().getName()) != null) return cached.clone();

        if (positions == null || !positions.isFile()) return null;
        YamlConfiguration data = YamlConfiguration.loadConfiguration(positions);
        String prefix = "players." + playerId + ".";
        String worldName = data.getString(prefix + "world", "");
        if (worldName == null || worldName.isBlank()) return null;
        World world = Bukkit.getWorld(worldName);
        if (world == null) return null;

        Location saved = new Location(world,
                data.getDouble(prefix + "x"),
                data.getDouble(prefix + "y"),
                data.getDouble(prefix + "z"),
                (float) data.getDouble(prefix + "yaw"),
                (float) data.getDouble(prefix + "pitch"));
        deaths.put(playerId, saved);
        return saved.clone();
    }

    private synchronized void savePositions() {
        YamlConfiguration data = YamlConfiguration.loadConfiguration(positions);
        deaths.forEach((id, location) -> {
            String prefix = "players." + id + ".";
            data.set(prefix + "world", location.getWorld().getName());
            data.set(prefix + "x", location.getX());
            data.set(prefix + "y", location.getY());
            data.set(prefix + "z", location.getZ());
            data.set(prefix + "yaw", location.getYaw());
            data.set(prefix + "pitch", location.getPitch());
        });
        try { data.save(positions); }
        catch (IOException error) { getLogger().warning("Could not save death positions: " + error.getMessage()); }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String cmd = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (cmd.equals("discord") || cmd.equals("website")) {
            String url = getConfig().getString("links." + cmd,
                    cmd.equals("discord") ? "https://discord.com/invite/mXjqNYF4b6" : "https://minenight.de");
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                sender.sendMessage(mm.deserialize("<red>This link is not configured correctly."));
                return true;
            }
            sender.sendMessage(mm.deserialize("<gradient:#8175AE:#D5DCE8><bold>MineNight</bold></gradient> <dark_gray>» <gray>" +
                    (cmd.equals("discord") ? "Discord: " : "Website: "))
                    .append(net.kyori.adventure.text.Component.text(url)
                            .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))));
            return true;
        }
        if (cmd.equals("back")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
            if (!sender.hasPermission("minenight.back")) { sender.sendMessage("You do not have permission."); return true; }
            if (player.getScoreboardTags().contains("minenight_rtpq_duel")) { player.sendMessage(mm.deserialize("<red>You cannot use /back during a duel.")); return true; }
            UUID playerId = player.getUniqueId();
            Location location = resolveSavedLocation(playerId);
            if (location == null || location.getWorld() == null) {
                player.sendMessage(mm.deserialize("<red>No return location saved."));
                return true;
            }
            if (!backInProgress.add(playerId)) {
                player.sendMessage(mm.deserialize("<yellow>Your /back teleport is already in progress."));
                return true;
            }
            player.teleportAsync(location.clone(), PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((success, error) -> {
                backInProgress.remove(playerId);
                Bukkit.getScheduler().runTask(core(), () -> {
                    if (!player.isOnline()) return;
                    if (error == null && Boolean.TRUE.equals(success)) {
                        player.sendMessage(mm.deserialize("<gradient:#8175AE:#D5DCE8>Returned to your previous location.</gradient>"));
                    } else {
                        player.sendMessage(mm.deserialize("<red>Could not teleport to your saved return location."));
                    }
                });
            });
            return true;
        }
        if (!sender.hasPermission("minenight.announcement")) { sender.sendMessage("You do not have permission."); return true; }
        if (args.length == 0) { sender.sendMessage("Usage: /announcement <message>"); return true; }
        String message = String.join(" ", args).replace("\\", "\\\\").replace("<", "\\<").replace(">", "\\>");
        String main = getConfig().getString("announcement.title", "<gradient:#8175AE:#D5DCE8><bold>Announcement</bold></gradient>");
        String subtitle = getConfig().getString("announcement.subtitle-format", "<white>{message}").replace("{message}", message);
        Title title = Title.title(mm.deserialize(main), mm.deserialize(subtitle), Title.Times.times(Duration.ofMillis(350), Duration.ofSeconds(getConfig().getLong("announcement.duration-seconds", 5)), Duration.ofMillis(600)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            if (getConfig().getBoolean("announcement.sound", true)) player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
        sender.sendMessage("Announcement sent to " + Bukkit.getOnlinePlayers().size() + " player(s).");
        return true;
    }

    @EventHandler public void onCommandSuggestions(PlayerCommandSendEvent event) {
        if (!getConfig().getBoolean("command-suggestions.enabled", true)) return;
        var commands = event.getCommands();
        if (getConfig().getBoolean("command-suggestions.hide-namespaced", true)) {
            java.util.Set<String> exceptions = new java.util.HashSet<>();
            for (String allowed : getConfig().getStringList("command-suggestions.allowed-namespaced")) {
                exceptions.add(allowed.toLowerCase(java.util.Locale.ROOT));
            }
            commands.removeIf(name -> name.contains(":") && !exceptions.contains(name.toLowerCase(java.util.Locale.ROOT)));
        }
        if (getConfig().getBoolean("command-suggestions.hide-vanilla", true)) {
            commands.removeIf(name -> getConfig().getStringList("command-suggestions.hidden-vanilla-commands").contains(name.toLowerCase(java.util.Locale.ROOT)));
        }
    }
}
