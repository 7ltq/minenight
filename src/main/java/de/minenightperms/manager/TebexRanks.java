package de.minenightperms.manager;

import de.minenightperms.MineNightPerms;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TebexRanks {
    private final MineNightPerms plugin;
    private final Path file;
    private volatile Map<UUID, Map<String, Grant>> grants = Map.of();

    private enum Product {
        VIP_MONTHLY("vip", false), MVP_MONTHLY("mvp", false),
        VIP_LIFETIME("vip", true), MVP_LIFETIME("mvp", true);
        final String rank;
        final boolean lifetime;
        Product(String rank, boolean lifetime) { this.rank = rank; this.lifetime = lifetime; }
    }

    private record Grant(Product product, long expires, boolean revoked) {
        boolean active(long now) { return !revoked && expires > now; }
    }

    public TebexRanks(MineNightPerms plugin) {
        this.plugin = plugin;
        this.file = plugin.getDataFolder().toPath().resolve("tebex-ranks.yml");
        if (!Files.exists(file)) return;
        try {
            YamlConfiguration data = new YamlConfiguration();
            data.load(file.toFile());
            Map<UUID, Map<String, Grant>> loaded = new HashMap<>();
            var players = data.getConfigurationSection("purchases");
            if (players != null) for (String key : players.getKeys(false)) {
                UUID uuid = UUID.fromString(key);
                var section = players.getConfigurationSection(key);
                if (section == null) throw new IllegalStateException("Invalid purchase data");
                Map<String, Grant> entries = new HashMap<>();
                for (String ref : section.getKeys(false)) {
                    var item = section.getConfigurationSection(ref);
                    if (item == null || !item.contains("expires")) throw new IllegalStateException("Invalid purchase record");
                    Product product = Product.valueOf(item.getString("product", ""));
                    entries.put(ref, new Grant(product, item.getLong("expires"), item.getBoolean("revoked")));
                }
                loaded.put(uuid, Map.copyOf(entries));
            }
            grants = Map.copyOf(loaded);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read Tebex rank ledger; refusing to overwrite it", ex);
        }
    }

    public String effectiveGroup(UUID uuid, String administrative) {
        if (!administrative.equalsIgnoreCase("default") && !administrative.equalsIgnoreCase("vip")
                && !administrative.equalsIgnoreCase("mvp")) return administrative;
        if (administrative.equalsIgnoreCase("mvp")) return administrative;
        long now = System.currentTimeMillis();
        boolean vip = administrative.equalsIgnoreCase("vip");
        for (Grant grant : grants.getOrDefault(uuid, Map.of()).values()) {
            if (!grant.active(now)) continue;
            if (grant.product.rank.equals("mvp") && plugin.getGroupManager().groupExists("mvp")) return "mvp";
            if (grant.product.rank.equals("vip")) vip = true;
        }
        return vip && plugin.getGroupManager().groupExists("vip") ? "vip" : administrative;
    }

    public boolean command(CommandSender sender, String[] args) {
        if (!(sender instanceof ConsoleCommandSender)) {
            sender.sendMessage("This command is console-only.");
            return true;
        }
        if (args.length != 5) {
            sender.sendMessage("Usage: mnperms tebex <purchase|renew|expire|refund|chargeback> <uuid> <product> <transaction>");
            return true;
        }
        try {
            String action = args[1].toLowerCase(Locale.ROOT);
            String raw = args[2];
            if (raw.matches("[0-9a-fA-F]{32}")) raw = raw.replaceFirst("(.{8})(.{4})(.{4})(.{4})(.{12})", "$1-$2-$3-$4-$5");
            if (!raw.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) throw new IllegalArgumentException("Invalid UUID");
            UUID uuid = UUID.fromString(raw);
            Product product = Product.valueOf(args[3].toUpperCase(Locale.ROOT));
            if (!args[4].matches("[A-Za-z0-9_-]{1,128}")) throw new IllegalArgumentException("Invalid transaction reference");
            String ref = product.name() + "_" + args[4];
            Map<String, Grant> entries = new HashMap<>(grants.getOrDefault(uuid, Map.of()));
            Grant previous = entries.get(ref);
            long now = System.currentTimeMillis();
            switch (action) {
                case "purchase", "renew" -> {
                    if (previous != null) { sender.sendMessage("Tebex transaction already recorded."); return true; }
                    if (action.equals("renew") && product.lifetime) throw new IllegalArgumentException("Lifetime products cannot renew");
                    if (!plugin.getGroupManager().groupExists(product.rank)) throw new IllegalArgumentException("Missing configured rank: " + product.rank);
                    long start = now;
                    if (!product.lifetime) for (Grant grant : entries.values()) {
                        if (grant.product == product && grant.active(now)) start = Math.max(start, grant.expires);
                    }
                    long end = product.lifetime ? Long.MAX_VALUE : Instant.ofEpochMilli(start).atZone(ZoneOffset.UTC).plusMonths(1).toInstant().toEpochMilli();
                    entries.put(ref, new Grant(product, end, false));
                }
                case "expire" -> {
                    if (previous == null || product.lifetime || previous.expires > now) {
                        sender.sendMessage("No elapsed paid period to expire; paid access is preserved.");
                        return true;
                    }
                    sender.sendMessage("Paid period has expired.");
                    return true;
                }
                case "refund", "chargeback" -> {
                    entries.put(ref, new Grant(product, previous == null ? 0 : previous.expires, true));
                }
                default -> throw new IllegalArgumentException("Unknown Tebex action");
            }
            Map<UUID, Map<String, Grant>> updated = new HashMap<>(grants);
            updated.put(uuid, Map.copyOf(entries));
            persist(updated);
            grants = Map.copyOf(updated);
            sender.sendMessage("Tebex " + action + " recorded for " + uuid + ".");
        } catch (IllegalArgumentException ex) {
            sender.sendMessage("Tebex command rejected: " + ex.getMessage());
        } catch (Exception ex) {
            plugin.getLogger().severe("Tebex ledger write failed: " + ex.getMessage());
            sender.sendMessage("Tebex command failed; review logs and resend after repair.");
        }
        return true;
    }

    private void persist(Map<UUID, Map<String, Grant>> updated) throws Exception {
        YamlConfiguration data = new YamlConfiguration();
        for (var player : updated.entrySet()) for (var entry : player.getValue().entrySet()) {
            String path = "purchases." + player.getKey() + "." + entry.getKey();
            Grant grant = entry.getValue();
            data.set(path + ".product", grant.product.name());
            data.set(path + ".expires", grant.expires);
            data.set(path + ".revoked", grant.revoked);
        }
        Files.createDirectories(file.getParent());
        Path temporary = Files.createTempFile(file.getParent(), "tebex-", ".tmp");
        try {
            data.save(temporary.toFile());
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
