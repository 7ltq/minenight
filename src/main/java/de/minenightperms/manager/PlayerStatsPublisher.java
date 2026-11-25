package de.minenightperms.manager;

import com.google.gson.*;
import de.minenightperms.MineNightPerms;
import org.bukkit.Bukkit;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Publishes existing vanilla counters; never changes or resets gameplay statistics. */
public final class PlayerStatsPublisher {
 @FunctionalInterface public interface Sink { void send(JsonObject body) throws Exception; }
 private final MineNightPerms plugin;
 private final Sink sink;
 private final Path statsDirectory, userCache;
 private final AtomicBoolean busy = new AtomicBoolean();
 private BukkitTask task;
 private long lastWarning;
 public PlayerStatsPublisher(MineNightPerms plugin, Sink sink) {
  this.plugin=plugin;this.sink=sink;
  statsDirectory=Bukkit.getWorlds().get(0).getWorldFolder().toPath().resolve("stats");
  userCache=plugin.core().getDataFolder().toPath().toAbsolutePath().getParent().getParent().resolve("usercache.json");
 }
 public void start() { task=Bukkit.getScheduler().runTaskTimer(plugin.core(),this::sample,40,1200); }
 public void stop() { if(task!=null)task.cancel(); }
 private void sample() {
  if(!busy.compareAndSet(false,true))return;
  Map<String,JsonObject> online=new HashMap<>();
  for(Player player:Bukkit.getOnlinePlayers()) {
   JsonObject row=new JsonObject();row.addProperty("uuid",player.getUniqueId().toString());row.addProperty("name",player.getName());
   row.addProperty("kills",Math.max(0,player.getStatistic(Statistic.PLAYER_KILLS)));
   row.addProperty("deaths",Math.max(0,player.getStatistic(Statistic.DEATHS)));
   row.addProperty("playtime_seconds",Math.max(0L,(long)player.getStatistic(Statistic.PLAY_ONE_MINUTE))/20);
   online.put(player.getUniqueId().toString(),row);
  }
  long captured=System.currentTimeMillis()/1000;
  Bukkit.getScheduler().runTaskAsynchronously(plugin.core(),()->{
   try {
    Map<String,String> names=new HashMap<>();
    if(Files.isRegularFile(userCache))for(JsonElement item:JsonParser.parseString(Files.readString(userCache)).getAsJsonArray()) {
     JsonObject user=item.getAsJsonObject();String name=user.get("name").getAsString();
     if(name.matches("[A-Za-z0-9_]{3,16}"))names.put(user.get("uuid").getAsString().toLowerCase(Locale.ROOT),name);
    }
    Map<String,JsonObject> rows=new TreeMap<>();
    if(Files.isDirectory(statsDirectory))try(var files=Files.list(statsDirectory)) {
     files.filter(p->p.getFileName().toString().matches("[a-fA-F0-9-]{36}\\.json")).forEach(file->{
      String id=file.getFileName().toString().substring(0,36).toLowerCase(Locale.ROOT);
      if(!names.containsKey(id))return;
      try {
       if(Files.size(file)>2*1024*1024)return;
       JsonObject document=JsonParser.parseString(Files.readString(file)).getAsJsonObject();
       JsonObject stats=document.getAsJsonObject("stats");JsonObject custom=stats==null?null:stats.getAsJsonObject("minecraft:custom");
       JsonObject row=new JsonObject();row.addProperty("uuid",id);row.addProperty("name",names.get(id));
       row.addProperty("kills",counter(custom,"minecraft:player_kills"));row.addProperty("deaths",counter(custom,"minecraft:deaths"));
       long ticks=counter(custom,"minecraft:play_time");if(custom!=null&&!custom.has("minecraft:play_time"))ticks=counter(custom,"minecraft:play_one_minute");
       row.addProperty("playtime_seconds",ticks/20);rows.put(id,row);
      }catch(Exception ignored){ /* A concurrently written stats file is retried next minute. */ }
     });
    }
    rows.putAll(online);
    JsonArray batch=new JsonArray();
    for(JsonObject row:rows.values()){batch.add(row);if(batch.size()==200){send(batch,captured);batch=new JsonArray();}}
    if(batch.size()>0||rows.isEmpty())send(batch,captured);
   }catch(Exception error){
    if(System.currentTimeMillis()-lastWarning>300000){lastWarning=System.currentTimeMillis();plugin.getLogger().warning("Website statistics sync deferred; check bridge configuration and connectivity.");}
   }finally{busy.set(false);}
  });
 }
 private static long counter(JsonObject custom,String name) { return custom!=null&&custom.has(name)?Math.max(0,custom.get(name).getAsLong()):0; }
 private void send(JsonArray players,long captured)throws Exception {
  JsonObject body=new JsonObject();body.addProperty("captured_at",captured);body.add("players",players);sink.send(body);
 }
}
