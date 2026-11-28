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
        getCommand("media").setExecutor(this);
        getCommand("vote").setExecutor(this);
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
        if (cmd.equals("media")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
            player.openInventory(new MediaMenu().getInventory());
            return true;
        }
        if (cmd.equals("vote")) {
            sender.sendMessage(mm.deserialize("<gradient:#4682B4:#91C8FF><bold>MineNight Votes</bold></gradient> <gray>» <white>Support our server:</white>"));
            String[][] sites = {
                    {"Minecraft.buzz", "https://minecraft.buzz/vote/minenight"},
                    {"MinecraftServers", "https://minecraftservers.org/vote/696400"},
                    {"Minecraft-MP", "https://minecraft-mp.com/server/367126/vote/"},
                    {"MinecraftIPList", "https://www.minecraftiplist.com/server/minenight-45795"}
            };
            for (int i = 0; i < sites.length; i++) {
                String name = sites[i][0];
                String url = sites[i][1];
                sender.sendMessage(net.kyori.adventure.text.Component.text("  " + (i + 1) + ". " + name + "  ")
                        .color(net.kyori.adventure.text.format.TextColor.color(0xDAEFFF))
                        .append(net.kyori.adventure.text.Component.text("[VOTE]")
                                .color(net.kyori.adventure.text.format.TextColor.color(0x91C8FF))
                                .decorate(net.kyori.adventure.text.format.TextDecoration.BOLD)
                                .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))
                                .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(
                                        net.kyori.adventure.text.Component.text("Open " + name + " in your browser")))));
            }
            sender.sendMessage(mm.deserialize("<gray>Use <#91C8FF>/votetotal</#91C8FF> to view confirmed votes. Thanks for supporting MineNight!</gray>"));
            return true;
        }
        if (cmd.equals("discord") || cmd.equals("website")) {
            String url = getConfig().getString("links." + cmd,
                    cmd.equals("discord") ? "https://discord.com/invite/mXjqNYF4b6" : "https://minenight.de");
            if (url == null || !(url.startsWith("https://") || url.startsWith("http://"))) {
                sender.sendMessage(mm.deserialize("<#6685E8>This link is not configured correctly."));
                return true;
            }
            sender.sendMessage(mm.deserialize("<gradient:#4B63C4:#91B2FF><bold>MineNight</bold></gradient> <#8298C4>» <#B7C7E8>" +
                    (cmd.equals("discord") ? "Discord: " : "Website: "))
                    .append(net.kyori.adventure.text.Component.text(url)
                            .color(net.kyori.adventure.text.format.NamedTextColor.AQUA)
                            .clickEvent(net.kyori.adventure.text.event.ClickEvent.openUrl(url))));
            return true;
        }
        if (cmd.equals("back")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
            if (!sender.hasPermission("minenight.back")) { sender.sendMessage("You do not have permission."); return true; }
            if (de.minenight.tiertest.TierTestModule.locked(player) || player.getScoreboardTags().contains("minenight_rtpq_duel")) { player.sendMessage(mm.deserialize("<#6685E8>You cannot use /back during a duel.")); return true; }
            UUID playerId = player.getUniqueId();
            Location location = resolveSavedLocation(playerId);
            if (location == null || location.getWorld() == null) {
                player.sendMessage(mm.deserialize("<#6685E8>No return location saved."));
                return true;
            }
            if (!backInProgress.add(playerId)) {
                player.sendMessage(mm.deserialize("<#91B2FF>Your /back teleport is already in progress."));
                return true;
            }
            player.teleportAsync(location.clone(), PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((success, error) -> {
                backInProgress.remove(playerId);
                Bukkit.getScheduler().runTask(core(), () -> {
                    if (!player.isOnline()) return;
                    if (error == null && Boolean.TRUE.equals(success)) {
                        player.sendMessage(mm.deserialize("<gradient:#4B63C4:#91B2FF>Returned to your previous location.</gradient>"));
                    } else {
                        player.sendMessage(mm.deserialize("<#6685E8>Could not teleport to your saved return location."));
                    }
                });
            });
            return true;
        }
        if (!sender.hasPermission("minenight.announcement")) { sender.sendMessage("You do not have permission."); return true; }
        if (args.length == 0) { sender.sendMessage("Usage: /announcement <message>"); return true; }
        String message = String.join(" ", args).replace("\\", "\\\\").replace("<", "\\<").replace(">", "\\>");
        String main = getConfig().getString("announcement.title", "<gradient:#4B63C4:#91B2FF><bold>Announcement</bold></gradient>");
        String subtitle = getConfig().getString("announcement.subtitle-format", "<white>{message}").replace("{message}", message);
        Title title = Title.title(mm.deserialize(main), mm.deserialize(subtitle), Title.Times.times(Duration.ofMillis(350), Duration.ofSeconds(getConfig().getLong("announcement.duration-seconds", 5)), Duration.ofMillis(600)));
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.showTitle(title);
            if (getConfig().getBoolean("announcement.sound", true)) player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7f, 1.2f);
        }
        sender.sendMessage("Announcement sent to " + Bukkit.getOnlinePlayers().size() + " player(s).");
        return true;
    }

    private final class MediaMenu implements org.bukkit.inventory.InventoryHolder {
        private final org.bukkit.inventory.Inventory inventory;

        private MediaMenu() {
            inventory = Bukkit.createInventory(this, 27, mm.deserialize("<gray>» <#FF0099>Media Rank</#FF0099> <gray>«"));
            inventory.setItem(11, mediaItem(org.bukkit.Material.BOOK, "<#FF0099><bold>Media Requirements", java.util.List.of(
                    "<gray>Requirements to become a <#FF0099>Media Rank:",
                    "",
                    "<white>YouTube: <yellow>2,500+ views",
                    "<white>YouTube Shorts: <yellow>2,500+ views",
                    "<white>TikTok: <yellow>2,500+ views",
                    "<white>Instagram: <yellow>2,500+ views",
                    "<white>Twitch: <yellow>2,500+ views",
                    "",
                    "<white>Show <#91B2FF>minenight.de <white>in your content.",
                    "<gray>Apply on minenight.de.")));
            inventory.setItem(13, mediaItem(org.bukkit.Material.PAPER, "<#91B2FF>MineNight Website", java.util.List.of(
                    "<white>minenight.de", "<yellow>Click to receive the website link.")));
            inventory.setItem(15, mediaItem(org.bukkit.Material.WRITABLE_BOOK, "<#FF0099>Apply for Media", java.util.List.of(
                    "<gray>Apply on minenight.de.", "<yellow>Click to receive the website link.")));
        }

        @Override public org.bukkit.inventory.Inventory getInventory() { return inventory; }
    }

    private org.bukkit.inventory.ItemStack mediaItem(org.bukkit.Material material, String name, java.util.List<String> lore) {
        var item = new org.bukkit.inventory.ItemStack(material);
        var meta = item.getItemMeta();
        meta.displayName(mm.deserialize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(line -> mm.deserialize(line).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler public void onMediaClick(org.bukkit.event.inventory.InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MediaMenu)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot();
        if (slot != 13 && slot != 15) return;
        Bukkit.getScheduler().runTask(core(), () -> {
            if (!player.isOnline()) return;
            player.closeInventory();
            player.performCommand("website");
        });
    }

    @EventHandler public void onMediaDrag(org.bukkit.event.inventory.InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MediaMenu) event.setCancelled(true);
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
