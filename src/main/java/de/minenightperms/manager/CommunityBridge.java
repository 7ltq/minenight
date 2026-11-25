package de.minenightperms.manager;

import com.google.gson.*;
import de.minenight.allinone.MineNightAllInOne;
import de.minenight.moderation.MineNightModeration;
import de.minenightperms.MineNightPerms;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.concurrent.TimeUnit;

public final class CommunityBridge {
    @FunctionalInterface public interface Delivery { JsonObject send(JsonObject body) throws Exception; }
    private final MineNightPerms plugin;
    private final Delivery delivery;
    private final Delivery acknowledgement;
    private final File file;
    private final YamlConfiguration pending;

    public CommunityBridge(MineNightPerms plugin, Delivery delivery, Delivery acknowledgement) {
        this.plugin = plugin;
        this.delivery = delivery;
        this.acknowledgement = acknowledgement;
        file = new File(plugin.getDataFolder(), "confirmed-votes.yml");
        pending = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void capture(String name, String service, String timestamp) {
        var player = Bukkit.getOfflinePlayerIfCached(name);
        String id = hash(name.toLowerCase(Locale.ROOT) + "\n" + service + "\n" + timestamp);
        if (pending.contains(id)) return;
        JsonObject vote = new JsonObject();
        vote.addProperty("id", id);
        vote.addProperty("uuid", player == null ? "" : player.getUniqueId().toString());
        vote.addProperty("name", name);
        vote.addProperty("platform", service);
        vote.addProperty("confirmed", System.currentTimeMillis() / 1000);
        pending.set(id, vote.toString());
        save();
    }

    private void save() {
        try { pending.save(file); }
        catch (java.io.IOException e) { throw new IllegalStateException("Cannot persist confirmed votes", e); }
    }


    public void poll() throws Exception {
        Object module = MineNightAllInOne.instance().module("moderation");
        if (!(module instanceof MineNightModeration moderation)) return;
        JsonObject body = new JsonObject();
        body.add("bans", moderation.websiteBans());
        JsonArray votes = new JsonArray();
        synchronized (this) {
            pending.getKeys(false).stream().limit(100).forEach(id -> votes.add(JsonParser.parseString(pending.getString(id))));
        }
        body.add("votes", votes);
        JsonObject result = delivery.send(body);
        synchronized (this) {
            for (JsonElement vote : votes) pending.set(vote.getAsJsonObject().get("id").getAsString(), null);
            if (!votes.isEmpty()) save();
        }
        for (JsonElement element : result.getAsJsonArray("jobs")) {
            JsonObject job = element.getAsJsonObject();
            boolean ok = Bukkit.getScheduler().callSyncMethod(plugin.core(), () -> moderation.websiteUnban(job)).get(10, TimeUnit.SECONDS);
            JsonObject ack = new JsonObject();
            ack.addProperty("ticket", job.get("ticket").getAsString());
            ack.addProperty("ok", ok);
            acknowledgement.send(ack);
        }
    }


    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
}
