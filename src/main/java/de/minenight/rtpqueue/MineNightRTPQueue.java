package de.minenight.rtpqueue;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;

public final class MineNightRTPQueue extends de.minenight.allinone.MineNightModule implements Listener, CommandExecutor {
    private static final String DUEL_TAG = "minenight_rtpq_duel";
    private static final List<String> BIOMES = List.of("plain", "snow", "desert", "mushroom", "badlands", "end");
    private static final Map<String, String> GENERATED_WORLD_NAMES = Map.of(
            "plain", "plains_world",
            "snow", "snow_world",
            "desert", "desert_world",
            "mushroom", "mushroom_world",
            "badlands", "badlands_world",
            "end", "end_world"
    );
    private final MiniMessage mini = MiniMessage.miniMessage();
    private final ArrayDeque<UUID> waiting = new ArrayDeque<>();
    private final Set<UUID> reserved = new HashSet<>();
    private final Map<UUID, UUID> opponent = new HashMap<>();
    private final Set<UUID> respawning = new HashSet<>();
    private final Set<UUID> preparing = new HashSet<>();
    private final Map<UUID, UUID> preparationPairs = new HashMap<>();
    private boolean stopping;

    @Override public void onEnable() {
        saveDefaultConfig();
        upgradeLegacyWorldNames();
        getCommand("rtpq").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, core());
    }
    private void upgradeLegacyWorldNames() {
        Map<String, String> legacy = Map.of(
                "plain", "plains_world",
                "snow", "snow_world",
                "desert", "desert_world",
                "mushroom", "mushroom_world",
                "badlands", "badlands_world",
                "end", "end_world"
        );
        boolean changed = false;
        for (var entry : legacy.entrySet()) {
            String path = "worlds." + entry.getKey();
            String current = getConfig().getString(path, "");
            if (current == null) current = "";
            String generated = GENERATED_WORLD_NAMES.get(entry.getKey());
            if (generated != null && (current.isBlank() || current.startsWith("mine_") || current.equalsIgnoreCase("world_" + entry.getKey()) || current.equalsIgnoreCase(entry.getKey() + "_world"))) {
                getConfig().set(path, generated);
                changed = true;
            }
        }
        if (changed) saveConfig();
    }

    @Override public void onDisable() {
        stopping = true;
        waiting.clear(); reserved.clear(); preparing.clear(); preparationPairs.clear(); respawning.clear(); opponent.clear();
        for (Player player : Bukkit.getOnlinePlayers()) player.removeScoreboardTag(DUEL_TAG);
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player)) { say(sender,"player-only"); return true; }
        if (!player.hasPermission("minenightrtpq.use")) { say(player,"no-permission"); return true; }
        UUID id = player.getUniqueId();
        if (opponent.containsKey(id)) { say(player,"matched","{opponent}",name(opponent.get(id))); return true; }
        if (preparing.contains(id)) { say(player,"preparing"); return true; }
        if (waiting.remove(id)) { say(player,"left"); return true; }
        if (getConfig().getBoolean("settings.require-practice-spawn", true) && practiceSpawn() == null) { say(player,"no-spawn"); return true; }
        waiting.add(id); say(player,"queued"); matchNext(); return true;
    }
    private String name(UUID id) {
        Player player = Bukkit.getPlayer(id);
        return player == null ? "Offline" : player.getName();
    }
    private Location practiceSpawn() {
        de.minenight.allinone.MineNightModule plugin = de.minenight.allinone.MineNightAllInOne.instance().module("spawn");
        if (plugin == null || !plugin.isEnabled()) return null;
        World world = Bukkit.getWorld(plugin.getConfig().getString("spawn.world", ""));
        if (world == null) return null;
        return new Location(world, plugin.getConfig().getDouble("spawn.x"), plugin.getConfig().getDouble("spawn.y"), plugin.getConfig().getDouble("spawn.z"), (float)plugin.getConfig().getDouble("spawn.yaw"), (float)plugin.getConfig().getDouble("spawn.pitch"));
    }
    private void matchNext() {
        if (stopping) return;
        while (waiting.size() >= 2) {
            UUID a = waiting.poll();
            UUID b = waiting.poll();
            Player first = Bukkit.getPlayer(a);
            Player second = Bukkit.getPlayer(b);
            if (first == null || !first.isOnline()) { if (second != null && second.isOnline()) waiting.addFirst(b); continue; }
            if (second == null || !second.isOnline()) { waiting.addFirst(a); continue; }
            preparing.add(a); preparing.add(b);
            preparationPairs.put(a,b); preparationPairs.put(b,a);
            say(first,"preparing"); say(second,"preparing");
            chooseWorld(first,second);
        }
    }
    private void chooseWorld(Player a, Player b) {
        List<World> choices = new ArrayList<>();
        de.minenight.allinone.MineNightModule rtp = de.minenight.allinone.MineNightAllInOne.instance().module("rtp");
        for (String biome: BIOMES) {
            String key = "worlds."+biome;
            String worldName = getConfig().getString(key, "");
            if (getConfig().getBoolean("settings.use-rtp-plugin-worlds",true) && rtp != null && rtp.isEnabled()) {
                worldName = rtp.getConfig().getString("destinations."+biome+".world",worldName);
            }
            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                String generatedName = GENERATED_WORLD_NAMES.get(biome);
                world = generatedName == null ? null : Bukkit.getWorld(generatedName);
            }
            if (world != null && world.getEnvironment() == World.Environment.NORMAL && !choices.contains(world)) choices.add(world);
        }
        if (choices.isEmpty()) { fail(a,b,"no-worlds"); return; }
        Collections.shuffle(choices);
        search(a,b,choices.getFirst(),Math.max(1,Math.min(100,getConfig().getInt("settings.max-search-attempts",40))));
    }
    private boolean validPair(Player a, Player b) {
        return !stopping && a.isOnline() && b.isOnline() && preparing.contains(a.getUniqueId()) && preparing.contains(b.getUniqueId());
    }
    private void search(Player a, Player b, World world, int attemptsLeft) {
        if (!validPair(a,b)) { cancelPreparation(a,b); return; }
        if (attemptsLeft <= 0) { fail(a,b,"no-location"); return; }
        int min = Math.max(0,getConfig().getInt("settings.min-radius",500));
        int max = Math.max(min+1,getConfig().getInt("settings.max-radius",5000));
        double angle = ThreadLocalRandom.current().nextDouble(Math.PI*2);
        double radius = Math.sqrt(ThreadLocalRandom.current().nextDouble((double)min*min,(double)max*max));
        int startX = world.getSpawnLocation().getBlockX() + (int)Math.round(radius*Math.cos(angle));
        int startZ = world.getSpawnLocation().getBlockZ() + (int)Math.round(radius*Math.sin(angle));
        boolean eastWest = ThreadLocalRandom.current().nextBoolean();
        int endX = startX + (eastWest ? 15 : 0);
        int endZ = startZ + (eastWest ? 0 : 15);
        if (!world.getWorldBorder().isInside(new Location(world,startX+.5,world.getSeaLevel(),startZ+.5)) ||
            !world.getWorldBorder().isInside(new Location(world,endX+.5,world.getSeaLevel(),endZ+.5))) {
            search(a,b,world,attemptsLeft-1); return;
        }
        boolean generate = getConfig().getBoolean("settings.generate-chunks",true);
        try {
            CompletableFuture<?> first = world.getChunkAtAsync(startX >> 4,startZ >> 4,generate);
            CompletableFuture<?> second = world.getChunkAtAsync(endX >> 4,endZ >> 4,generate);
            CompletableFuture.allOf(first,second).whenComplete((result,error) -> {
                if (stopping) return;
                Bukkit.getScheduler().runTask(core(),() -> {
                    if (!validPair(a,b)) { cancelPreparation(a,b); return; }
                    if (error != null) { search(a,b,world,attemptsLeft-1); return; }
                    int y1 = world.getHighestBlockYAt(startX,startZ,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1;
                    int y2 = world.getHighestBlockYAt(endX,endZ,HeightMap.MOTION_BLOCKING_NO_LEAVES)+1;
                    if (Math.abs(y1-y2) > getConfig().getInt("settings.maximum-ground-height-difference",3)) { search(a,b,world,attemptsLeft-1); return; }
                    Location locA = new Location(world,startX+.5,y1,startZ+.5);
                    Location locB = new Location(world,endX+.5,y2,endZ+.5);
                    if (!safe(locA) || !safe(locB)) { search(a,b,world,attemptsLeft-1); return; }
                    locA.setDirection(locB.toVector().subtract(locA.toVector()));
                    locB.setDirection(locA.toVector().subtract(locB.toVector()));
                    start(a,b,locA,locB);
                });
            });
        } catch (Exception error) { fail(a,b,"teleport-failed"); }
    }
    private boolean safe(Location location) {
        World world = location.getWorld();
        if (world == null || location.getBlockY()<=world.getMinHeight()+1 || location.getBlockY()+2>=world.getMaxHeight()) return false;
        Block ground = location.getBlock().getRelative(0,-1,0);
        Material material = ground.getType();
        if (!material.isSolid() || !material.isOccluding() || material==Material.MAGMA_BLOCK || material==Material.CACTUS || material==Material.CAMPFIRE || material==Material.SOUL_CAMPFIRE || material==Material.POWDER_SNOW) return false;
        return location.getBlock().getType().isAir() && location.getBlock().getRelative(0,1,0).getType().isAir() && world.getWorldBorder().isInside(location);
    }
    private void start(Player a,Player b,Location first,Location second) {
        if (!validPair(a,b)) { cancelPreparation(a,b); return; }
        CompletableFuture<Boolean> fa = a.teleportAsync(first, PlayerTeleportEvent.TeleportCause.COMMAND);
        CompletableFuture<Boolean> fb = b.teleportAsync(second, PlayerTeleportEvent.TeleportCause.COMMAND);
        CompletableFuture.allOf(fa,fb).whenComplete((ignored,error) -> {
            if (stopping) return;
            Bukkit.getScheduler().runTask(core(),() -> {
                if (!validPair(a,b)) { cancelPreparation(a,b); return; }
                if (error!=null || !Boolean.TRUE.equals(fa.getNow(false)) || !Boolean.TRUE.equals(fb.getNow(false))) {
                    fail(a,b,"teleport-failed"); return;
                }
                preparing.remove(a.getUniqueId()); preparing.remove(b.getUniqueId());
                preparationPairs.remove(a.getUniqueId()); preparationPairs.remove(b.getUniqueId());
                opponent.put(a.getUniqueId(),b.getUniqueId()); opponent.put(b.getUniqueId(),a.getUniqueId());
                a.addScoreboardTag(DUEL_TAG); b.addScoreboardTag(DUEL_TAG);
                say(a,"matched","{opponent}",b.getName());
                say(b,"matched","{opponent}",a.getName());
            });
        });
    }
    private void cancelPreparation(Player a,Player b) {
        preparing.remove(a.getUniqueId()); preparing.remove(b.getUniqueId());
        preparationPairs.remove(a.getUniqueId()); preparationPairs.remove(b.getUniqueId());
        if (a.isOnline() && !waiting.contains(a.getUniqueId())) waiting.add(a.getUniqueId());
        if (b.isOnline() && !waiting.contains(b.getUniqueId())) waiting.add(b.getUniqueId());
        matchNext();
    }
    private void fail(Player a, Player b, String reason) {
        if (a.isOnline()) say(a,reason);
        if (b.isOnline()) say(b,reason);
        preparing.remove(a.getUniqueId()); preparing.remove(b.getUniqueId());
        preparationPairs.remove(a.getUniqueId()); preparationPairs.remove(b.getUniqueId());
    }
    @EventHandler(priority=EventPriority.MONITOR) public void onDeath(PlayerDeathEvent event) {
        Player loser = event.getEntity();
        UUID loserId = loser.getUniqueId();
        UUID winnerId = opponent.remove(loserId);
        if (winnerId == null) return;
        opponent.remove(winnerId);
        loser.removeScoreboardTag(DUEL_TAG);
        Player winner = Bukkit.getPlayer(winnerId);
        if (winner != null) winner.removeScoreboardTag(DUEL_TAG);
    }
    @EventHandler public void onQuit(PlayerQuitEvent event) {
        Player leaver = event.getPlayer(); UUID id=leaver.getUniqueId();
        waiting.remove(id); respawning.remove(id);
        if (preparing.remove(id)) {
            UUID otherId = preparationPairs.remove(id);
            if (otherId != null) {
                preparationPairs.remove(otherId);
                preparing.remove(otherId);
                Player other = Bukkit.getPlayer(otherId);
                if (other != null && other.isOnline()) { waiting.addFirst(otherId); say(other,"opponent-left"); }
            }
            matchNext();
        }
        UUID enemyId = opponent.remove(id);
        leaver.removeScoreboardTag(DUEL_TAG);
        if (enemyId != null) {
            opponent.remove(enemyId);
            Player enemy=Bukkit.getPlayer(enemyId);
            if (enemy != null) { enemy.removeScoreboardTag(DUEL_TAG); say(enemy,"opponent-left"); }
        }
    }
    private void say(CommandSender sender,String key,String... substitutions) {
        String value=getConfig().getString("messages.prefix","")+getConfig().getString("messages."+key,"<#6685E8>"+key);
        for (int i=0;i+1<substitutions.length;i+=2) value=value.replace(substitutions[i],substitutions[i+1]);
        sender.sendMessage(mini.deserialize(value));
    }
}
