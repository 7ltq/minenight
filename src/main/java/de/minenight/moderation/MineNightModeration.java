package de.minenight.moderation;

import de.minenight.allinone.MineNightModule;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.server.ServerCommandEvent;

public final class MineNightModeration extends MineNightModule implements Listener {
    public record Sanction(String reason, long until, String actor) {
        boolean active() { return until == 0 || until > System.currentTimeMillis(); }
    }
    private record DurationResult(long millis, String label) { }
    private static final Pattern TIME = Pattern.compile("(?i)^(\\d+)(mo|[smhdy])$");
    private final Map<UUID, Sanction> bans = new ConcurrentHashMap<>();
    private final Map<UUID, Sanction> mutes = new ConcurrentHashMap<>();
    private final Map<UUID, String> hashedIps = new ConcurrentHashMap<>();
    private final Set<UUID> unbanNotices = ConcurrentHashMap.newKeySet();
    private final Set<UUID> expiredNotices = ConcurrentHashMap.newKeySet();
    private final Map<UUID, String> names = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> derivedBans = new ConcurrentHashMap<>();
    private final MiniMessage mm = MiniMessage.miniMessage();
    private File file;
    private byte[] salt;
    private volatile boolean enabled = true;

    @Override public void onEnable() {
        saveDefaultConfig();
        file = new File(getDataFolder(), "punishments.yml");
        YamlConfiguration data = YamlConfiguration.loadConfiguration(file);
        String encoded = data.getString("security.salt", "");
        if (encoded.isBlank()) {
            salt = new byte[32];
            new SecureRandom().nextBytes(salt);
            data.set("security.salt", Base64.getEncoder().encodeToString(salt));
            saveYaml(data);
        } else salt = Base64.getDecoder().decode(encoded);
        for (String key : data.getConfigurationSection("players") == null ? Set.<String>of() : data.getConfigurationSection("players").getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String prefix = "players." + key + ".";
                names.put(id, data.getString(prefix + "name", key));
                if (data.contains(prefix + "ban.reason")) bans.put(id, new Sanction(data.getString(prefix + "ban.reason", "Unknown"), data.getLong(prefix + "ban.until"), data.getString(prefix + "ban.actor", "Console")));
                String inherited = data.getString(prefix + "ban.origin", "");
                if (!inherited.isBlank()) {
                    try { derivedBans.put(id, UUID.fromString(inherited)); } catch (IllegalArgumentException ignored) { }
                }
                if (data.contains(prefix + "mute.reason")) mutes.put(id, new Sanction(data.getString(prefix + "mute.reason", "Unknown"), data.getLong(prefix + "mute.until"), data.getString(prefix + "mute.actor", "Console")));
                String ip = data.getString(prefix + "ipHash", "");
                if (!ip.isEmpty()) hashedIps.put(id, ip);
                if (data.getBoolean(prefix + "unbanNotice")) unbanNotices.add(id);
                if (data.getBoolean(prefix + "expiredNotice")) expiredNotices.add(id);
            } catch (IllegalArgumentException ignored) { }
        }
        for (String name : List.of("ban", "unban", "mute", "unmute", "pardon", "banlist", "kick", "altlookup")) {
            if (getCommand(name) != null) getCommand(name).setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, core());
        if (Bukkit.getPluginManager().isPluginEnabled("voicechat")) {
            try {
                de.maxhenkel.voicechat.api.BukkitVoicechatService service = Bukkit.getServicesManager().load(de.maxhenkel.voicechat.api.BukkitVoicechatService.class);
                if (service != null) {
                    service.registerPlugin(new MineNightVoiceBridge(this));
                    getLogger().info("Simple Voice Chat mute integration registered.");
                }
            } catch (NoClassDefFoundError | Exception error) { getLogger().warning("Voice chat mute integration unavailable: " + error.getMessage()); }
        } else getLogger().info("Simple Voice Chat not detected; text mutes still work.");
    }

    @Override public void onDisable() { enabled = false; save(); }
    public boolean banScreenshareAvoid(UUID id, String name) {
        if (!enabled || currentBan(id) != null) return false;
        names.put(id, name);
        String reason = getConfig().getString("reasons.screenshare_avoid.label", "Screenshare Avoiding");
        Sanction sanction = new Sanction(reason, System.currentTimeMillis() + Duration.ofDays(7).toMillis(), "Automatic Screenshare");
        derivedBans.remove(id);
        bans.put(id, sanction);
        unbanNotices.remove(id);
        expiredNotices.remove(id);
        refreshDerivedFor(id, sanction);
        save();
        return true;
    }

    public boolean isMuted(UUID id) {
        Sanction sanction = mutes.get(id);
        return enabled && sanction != null && sanction.active();
    }
    private void saveYaml(YamlConfiguration yml) {
        try { yml.save(file); }
        catch (IOException e) { getLogger().severe("Unable to save punishment data: " + e.getMessage()); }
    }
    private synchronized void save() {
        YamlConfiguration yml = new YamlConfiguration();
        yml.set("security.salt", Base64.getEncoder().encodeToString(salt));
        Set<UUID> ids = new java.util.HashSet<>();
        ids.addAll(names.keySet()); ids.addAll(bans.keySet()); ids.addAll(mutes.keySet()); ids.addAll(hashedIps.keySet()); ids.addAll(unbanNotices); ids.addAll(expiredNotices); ids.addAll(derivedBans.keySet());
        for (UUID id : ids) {
            String path = "players." + id + ".";
            yml.set(path + "name", names.getOrDefault(id, id.toString()));
            Sanction ban = bans.get(id), mute = mutes.get(id);
            if (ban != null) { yml.set(path + "ban.reason", ban.reason); yml.set(path + "ban.until", ban.until); yml.set(path + "ban.actor", ban.actor); }
            if (mute != null) { yml.set(path + "mute.reason", mute.reason); yml.set(path + "mute.until", mute.until); yml.set(path + "mute.actor", mute.actor); }
            yml.set(path + "ipHash", hashedIps.get(id));
            UUID origin = derivedBans.get(id);
            if (origin != null) yml.set(path + "ban.origin", origin.toString());
            if (unbanNotices.contains(id)) yml.set(path + "unbanNotice", true);
            if (expiredNotices.contains(id)) yml.set(path + "expiredNotice", true);
        }
        saveYaml(yml);
    }
    private String hash(InetAddress address) {
        if (address == null) return "";
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(salt);
            byte[] bytes = digest.digest(address.getAddress());
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception error) { return ""; }
    }
    private Component banMessage(Sanction sanction) {
        String discord = getConfig().getString("appeal.discord-url", "https://discord.com/");
        Component message = mm.deserialize("<#6685E8><bold>You have been banned!</bold></#6685E8><newline><#B7C7E8>Reason: <white>" + escape(sanction.reason) + "<newline><#B7C7E8>Remaining: <white>" + remaining(sanction) + "<newline><newline><#B7C7E8>If you want to appeal this ban, create a ticket in our Discord server.<newline>");
        return message.append(Component.text(discord, NamedTextColor.AQUA).clickEvent(ClickEvent.openUrl(discord)));
    }
    private String escape(String input) { return input.replace("<", "\\<").replace(">", "\\>"); }
    private String remaining(Sanction sanction) {
        if (sanction.until == 0) return "Permanent";
        long seconds = Math.max(0, (sanction.until - System.currentTimeMillis() + 999) / 1000);
        long days = seconds / 86400, hours = seconds / 3600 % 24, minutes = seconds / 60 % 60;
        return days > 0 ? days + "d " + hours + "h" : hours > 0 ? hours + "h " + minutes + "m" : minutes > 0 ? minutes + "m " + seconds % 60 + "s" : seconds + "s";
    }
    private UUID rootOf(UUID id) {
        UUID root = id;
        Set<UUID> visited = new java.util.HashSet<>();
        while (visited.add(root) && derivedBans.containsKey(root)) root = derivedBans.get(root);
        return root;
    }

    private Sanction currentBan(UUID id) {
        UUID root = rootOf(id);
        Sanction sanction = bans.get(root);
        if (sanction == null || !sanction.active()) return null;
        if (root.equals(id)) return sanction;
        return new Sanction("Ban Evading", sanction.until(), "Automatic IP Detection");
    }

    private UUID matchingBannedAccount(UUID id, String hashedIp) {
        if (hashedIp == null || hashedIp.isBlank()) return null;
        UUID best = null;
        long bestUntil = -1L;
        for (Map.Entry<UUID, String> entry : hashedIps.entrySet()) {
            if (entry.getKey().equals(id) || !hashedIp.equals(entry.getValue())) continue;
            UUID root = rootOf(entry.getKey());
            if (id.equals(root)) continue;
            Sanction ban = bans.get(root);
            if (ban == null || !ban.active()) continue;
            long until = ban.until() == 0 ? Long.MAX_VALUE : ban.until();
            if (best == null || until > bestUntil) {
                best = root;
                bestUntil = until;
            }
        }
        return best;
    }

    private void clearDerivedFor(UUID main) {
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(derivedBans.entrySet())) {
            if (!rootOf(entry.getKey()).equals(main)) continue;
            derivedBans.remove(entry.getKey());
            bans.remove(entry.getKey());
        }
    }

    private void refreshDerivedFor(UUID main, Sanction source) {
        for (Map.Entry<UUID, UUID> entry : new ArrayList<>(derivedBans.entrySet())) {
            if (!rootOf(entry.getKey()).equals(main)) continue;
            bans.put(entry.getKey(), new Sanction("Ban Evading", source.until(), "Automatic IP Detection"));
        }
    }

    private void altLookup(CommandSender sender, String targetName) {
        UUID id = find(targetName);
        if (id == null) {
            sender.sendMessage(PREFIX_LOOKUP + "Player not found in server history.");
            return;
        }
        sender.sendMessage(PREFIX_LOOKUP + "Checking " + names.getOrDefault(id, targetName));
        String fingerprint = hashedIps.get(id);
        if (fingerprint == null) {
            sender.sendMessage("§7No saved IP fingerprint for this player.");
            return;
        }
        List<UUID> matches = hashedIps.entrySet().stream()
                .filter(e -> !e.getKey().equals(id) && fingerprint.equals(e.getValue()))
                .map(Map.Entry::getKey)
                .sorted(Comparator.comparing(e -> names.getOrDefault(e, e.toString()), String.CASE_INSENSITIVE_ORDER))
                .toList();
        if (matches.isEmpty()) {
            sender.sendMessage("§7No linked accounts found using the most recently recorded IP.");
        } else {
            for (UUID match : matches) {
                Sanction sanction = currentBan(match);
                sender.sendMessage("§8- §f" + names.getOrDefault(match, match.toString()) + " §8| " +
                        (sanction == null ? "§aNot banned" : "§cBanned for " + remaining(sanction)));
            }
        }
        sender.sendMessage("§eA shared IP alone does not confirm the same person.");
    }

    private static final String PREFIX_LOOKUP = "§8[§9MineNight§8] §7";

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        UUID id = event.getUniqueId();
        String ip = hash(event.getAddress());
        names.put(id, event.getName());
        if (!ip.isEmpty()) hashedIps.put(id, ip);

        UUID inheritedFrom = derivedBans.get(id);
        if (inheritedFrom != null) {
            Sanction source = currentBan(rootOf(inheritedFrom));
            if (source == null) {
                derivedBans.remove(id);
                bans.remove(id);
                Bukkit.getScheduler().runTask(core(), this::save);
            } else {
                Sanction derived = new Sanction("Ban Evading", source.until(), "Automatic IP Detection");
                bans.put(id, derived);
                Bukkit.getScheduler().runTask(core(), this::save);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage(derived));
                return;
            }
        }

        Sanction ban = bans.get(id);
        if (ban != null && ban.active()) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage(ban));
            return;
        }
        if (ban != null) {
            bans.remove(id, ban);
            expiredNotices.add(id);
            unbanNotices.remove(id);
            Bukkit.getScheduler().runTask(core(), this::save);
        }

        if (getConfig().getBoolean("ip-detection.enabled", true) &&
                getConfig().getBoolean("ip-detection.auto-ban-matching-ips", true) &&
                !ip.isBlank() &&
                !getConfig().getStringList("ip-detection.exempt-uuids").contains(id.toString())) {
            UUID main = matchingBannedAccount(id, ip);
            if (main != null) {
                Sanction source = currentBan(main);
                if (source != null) {
                    Sanction inherited = new Sanction("Ban Evading", source.until(), "Automatic IP Detection");
                    bans.put(id, inherited);
                    derivedBans.put(id, main);
                    unbanNotices.remove(id);
                    expiredNotices.remove(id);
                    Bukkit.getScheduler().runTask(core(), this::save);
                    event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, banMessage(inherited));
                    return;
                }
            }
        }
        Bukkit.getScheduler().runTask(core(), this::save);
    }
    @EventHandler public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID id = player.getUniqueId();
        boolean manual = unbanNotices.remove(id);
        boolean expired = expiredNotices.remove(id);
        if (!manual && !expired) return;
        Bukkit.getScheduler().runTaskLater(core(), () -> {
            if (!player.isOnline()) return;
            String title = manual ? "<gradient:#4B63C4:#91B2FF><bold>You have been unbanned!</bold></gradient>" : "<gradient:#4B63C4:#91B2FF><bold>Your ban has expired!</bold></gradient>";
            String subtitle = manual ? "<#B7C7E8>Welcome back to MineNight" : "<#B7C7E8>You can play again";
            player.showTitle(net.kyori.adventure.title.Title.title(mm.deserialize(title), mm.deserialize(subtitle), net.kyori.adventure.title.Title.Times.times(Duration.ofMillis(400), Duration.ofSeconds(4), Duration.ofMillis(700))));
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        }, 20L);
        save();
    }
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        Sanction mute = mutes.get(event.getPlayer().getUniqueId());
        if (mute == null || !mute.active()) return;
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(core(), () -> event.getPlayer().sendMessage(mm.deserialize("<#6685E8>You are muted. <#B7C7E8>Reason: <white>" + escape(mute.reason) + " <#B7C7E8>| Remaining: <white>" + remaining(mute))));
    }
    @EventHandler public void onCommands(PlayerCommandSendEvent event) {
        if (event.getPlayer().hasPermission("minenight.moderation.staff")) return;
        event.getCommands().removeAll(Set.of("minecraft:ban", "minecraft:pardon", "minecraft:ban-ip", "minecraft:pardon-ip", "minecraft:banlist", "ban", "pardon", "ban-ip", "pardon-ip", "banlist"));
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void intercept(PlayerCommandPreprocessEvent event) {
        String text = event.getMessage().substring(1).split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (Set.of("minecraft:ban", "minecraft:pardon", "minecraft:ban-ip", "minecraft:pardon-ip", "minecraft:banlist", "ban-ip", "pardon-ip").contains(text)) {
            event.setCancelled(true);
            event.getPlayer().sendMessage("Use /ban, /unban or /banlist from MineNight.");
        }
    }
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void interceptConsole(ServerCommandEvent event) {
        String cmd = event.getCommand().split(" ", 2)[0].toLowerCase(Locale.ROOT);
        if (Set.of("minecraft:ban", "minecraft:pardon", "minecraft:ban-ip", "minecraft:pardon-ip", "minecraft:banlist", "ban-ip", "pardon-ip").contains(cmd)) {
            event.setCancelled(true);
            event.getSender().sendMessage("Use /ban, /unban or /banlist from MineNight.");
        }
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("minenight.moderation.staff")) { sender.sendMessage("You do not have permission."); return true; }
        String cmd = command.getName().toLowerCase(Locale.ROOT);
        if (cmd.equals("altlookup")) {
            if (args.length != 1) sender.sendMessage("Usage: /altlookup <player>");
            else altLookup(sender, args[0]);
            return true;
        }
        if (cmd.equals("pardon")) { sender.sendMessage("Use /unban <player> <reason> instead."); return true; }
        if (cmd.equals("kick")) { sender.sendMessage("Use /ban <player> <reason> <duration> for timed punishments."); return true; }
        if (cmd.equals("banlist")) {
            sender.sendMessage("Active MineNight bans:");
            bans.forEach((id, ban) -> { if (ban.active()) sender.sendMessage(names.getOrDefault(id, id.toString()) + " - " + ban.reason + " - " + remaining(ban)); });
            return true;
        }
        if (args.length < 1) { usage(sender, cmd); return true; }
        String targetName = args[0];
        UUID id = find(targetName);
        if (id == null) { sender.sendMessage("Unknown player. Player must have joined at least once."); return true; }
        names.put(id, targetName);
        if (cmd.equals("ban") || cmd.equals("mute")) {
            if (args.length < 2) { usage(sender, cmd); return true; }
            String reasonKey = args[1].toLowerCase(Locale.ROOT).replace('-', '_');
            String configReason = getConfig().getString("reasons." + reasonKey + ".label");
            if (configReason == null) { sender.sendMessage("Unknown reason. Available: " + String.join(", ", getConfig().getConfigurationSection("reasons").getKeys(false))); return true; }
            String time = args.length >= 3 ? args[2] : getConfig().getString("reasons." + reasonKey + "." + cmd + "-duration", cmd.equals("mute") ? "1d" : "7d");
            if (time.equalsIgnoreCase("permanent") && cmd.equals("ban")) {
                bans.put(id, new Sanction(configReason, 0L, sender.getName()));
            } else {
                DurationResult duration = parseTime(time);
                if (duration == null) { sender.sendMessage("Invalid duration. Examples: 30s, 10m, 2h, 7d, 1mo, 1y."); return true; }
                long until = System.currentTimeMillis() + duration.millis;
                if (cmd.equals("ban")) bans.put(id, new Sanction(configReason, until, sender.getName()));
                else mutes.put(id, new Sanction(configReason, until, sender.getName()));
            }
            if (cmd.equals("ban")) {
                derivedBans.remove(id);
                refreshDerivedFor(id, bans.get(id));
            }
            save();
            Player target = Bukkit.getPlayer(id);
            if (cmd.equals("ban") && target != null) target.kick(banMessage(bans.get(id)));
            if (cmd.equals("mute") && target != null) target.sendMessage(mm.deserialize("<#6685E8>You have been muted: <white>" + escape(configReason)));
            sender.sendMessage((cmd.equals("ban") ? "Banned " : "Muted ") + targetName + " for " + configReason + " (" + time + ").");
            return true;
        }
        if (cmd.equals("unban")) {
            if (args.length < 2) { usage(sender, cmd); return true; }
            if (bans.remove(id) == null) { sender.sendMessage("That player is not banned by MineNight."); return true; }
            derivedBans.remove(id);
            clearDerivedFor(id);
            unbanNotices.add(id);
            expiredNotices.remove(id);
            save();
            sender.sendMessage("Unbanned " + targetName + ". Reason: " + String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            return true;
        }
        if (cmd.equals("unmute")) {
            if (mutes.remove(id) == null) { sender.sendMessage("That player is not muted."); return true; }
            save();
            sender.sendMessage("Unmuted " + targetName + ".");
            return true;
        }
        return true;
    }
    private void usage(CommandSender sender, String cmd) {
        sender.sendMessage(switch (cmd) {
            case "ban" -> "Usage: /ban <player> <reason_key> [duration]";
            case "altlookup" -> "Usage: /altlookup <player>";
            case "mute" -> "Usage: /mute <player> <reason_key> [duration]";
            case "unban" -> "Usage: /unban <player> <reason>";
            default -> "Usage: /unmute <player>";
        });
    }
    private UUID find(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        for (Map.Entry<UUID, String> entry : names.entrySet()) if (entry.getValue().equalsIgnoreCase(name)) return entry.getKey();
        for (org.bukkit.OfflinePlayer player : Bukkit.getOfflinePlayers()) if (name.equalsIgnoreCase(player.getName())) return player.getUniqueId();
        return null;
    }
    private DurationResult parseTime(String input) {
        Matcher m = TIME.matcher(input);
        if (!m.matches()) return null;
        try {
            long amount = Long.parseLong(m.group(1));
            if (amount <= 0 || amount > 100000) return null;
            long days = switch (m.group(2).toLowerCase(Locale.ROOT)) {
                case "mo" -> 30L;
                case "y" -> 365L;
                default -> 0L;
            };
            long base = switch (m.group(2).toLowerCase(Locale.ROOT)) {
                case "s" -> 1000L;
                case "m" -> 60000L;
                case "h" -> 3600000L;
                case "d" -> 86400000L;
                default -> Math.multiplyExact(days, 86400000L);
            };
            return new DurationResult(Math.multiplyExact(amount, base), input);
        } catch (ArithmeticException error) { return null; }
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("minenight.moderation.staff")) return List.of();
        if (args.length == 1) return names.values().stream().filter(s -> s.toLowerCase(Locale.ROOT).startsWith(args[0].toLowerCase(Locale.ROOT))).limit(25).toList();
        if (args.length == 2 && Set.of("ban", "mute").contains(command.getName())) return getConfig().getConfigurationSection("reasons").getKeys(false).stream().filter(s -> s.startsWith(args[1].toLowerCase(Locale.ROOT))).toList();
        if (args.length == 3 && Set.of("ban", "mute").contains(command.getName())) return List.of("1s", "1m", "1h", "1d", "7d", "14d", "1mo", "1y");
        if (args.length == 2 && command.getName().equals("unban")) return List.of("second_chance", "false_positive", "appeal_accepted");
        return List.of();
    }
}
