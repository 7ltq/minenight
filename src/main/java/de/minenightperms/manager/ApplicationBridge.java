package de.minenightperms.manager;

import com.google.gson.*;
import de.minenightperms.MineNightPerms;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class ApplicationBridge {
 private final MineNightPerms plugin;
 private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final AtomicBoolean busy=new AtomicBoolean();
 private BukkitTask task;
 private PlayerStatsPublisher statsPublisher;
 private String endpoint,secret;
 public ApplicationBridge(MineNightPerms plugin){this.plugin=plugin;}
 public void start(){
  try{
   Path file=plugin.getDataFolder().toPath().resolve("application-bridge.yml");
   if(!Files.exists(file)){Files.writeString(file,"enabled: false\nendpoint: https://minenight.de\nsecret: ''\n");return;}
   YamlConfiguration cfg=YamlConfiguration.loadConfiguration(file.toFile());if(!cfg.getBoolean("enabled",false))return;
   endpoint=cfg.getString("endpoint","").replaceAll("/+$","");secret=cfg.getString("secret","");
   URI uri=URI.create(endpoint);if(!"https".equals(uri.getScheme())||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||secret.length()<40)throw new IllegalArgumentException("Invalid bridge configuration");
   task=Bukkit.getScheduler().runTaskTimerAsynchronously(plugin.core(),this::poll,20,40);
   statsPublisher=new PlayerStatsPublisher(plugin,body->request("stats",body));statsPublisher.start();
  }catch(Exception e){plugin.getLogger().warning("Application bridge disabled: check private configuration.");}
 }
 public void stop(){if(task!=null)task.cancel();if(statsPublisher!=null)statsPublisher.stop();}
 private JsonObject request(String path,JsonObject body)throws Exception{
  HttpRequest.Builder builder=HttpRequest.newBuilder(URI.create(endpoint+"/api/application-bridge/"+path)).timeout(Duration.ofSeconds(8)).header("X-MineNight-Bridge","Bearer "+secret);
  if(body==null)builder.GET();else builder.header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofString(body.toString()));
  HttpResponse<String> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofString());
  if(response.statusCode()!=200)throw new IllegalStateException("Bridge HTTP "+response.statusCode());
  return JsonParser.parseString(response.body()).getAsJsonObject();
 }
 public boolean link(CommandSender sender,String[] args){
  if(!(sender instanceof Player player)){sender.sendMessage("Run this command in-game.");return true;}
  if(endpoint==null||secret==null||secret.length()<40){player.sendMessage("Account linking is not available yet.");return true;}
  if(!Bukkit.getOnlineMode()){player.sendMessage("Secure linking requires an authenticated online-mode server.");return true;}
  if(args.length!=2||!args[1].matches("[a-f0-9]{48}")){player.sendMessage("Get your one-time linking command on minenight.de/support.html.");return true;}
  UUID id=player.getUniqueId();String ign=player.getName();String code=args[1];
  Bukkit.getScheduler().runTaskAsynchronously(plugin.core(),()->{
   String message;try{JsonObject b=new JsonObject();b.addProperty("uuid",id.toString());b.addProperty("ign",ign);b.addProperty("code",code);request("link",b);message="Minecraft account linked. You can return to your application.";}catch(Exception e){message="Account link failed. Get a fresh code on the website; an existing verified link cannot be overwritten.";}
   String result=message;Bukkit.getScheduler().runTask(plugin.core(),()->{Player current=Bukkit.getPlayer(id);if(current!=null)current.sendMessage(result);});
  });return true;
 }
 private void poll(){
  if(!busy.compareAndSet(false,true))return;
  try{
   for(JsonElement element:request("jobs",null).getAsJsonArray("jobs")){
    JsonObject job=element.getAsJsonObject();String ticket=job.get("ticket").getAsString();String role=job.get("role").getAsString();UUID id=UUID.fromString(job.get("uuid").getAsString());
    boolean ok=Bukkit.getScheduler().callSyncMethod(plugin.core(),()->{
     if(!Set.of("helper","mod","admin","media").contains(role)||!plugin.getGroupManager().groupExists(role))return false;
     String current=plugin.getPlayerDataManager().getPlayerData(id).getAdministrativeGroupName().toLowerCase(Locale.ROOT);
     Map<String,Integer> priority=Map.of("media",1,"helper",2,"mod",3,"admin",4,"dev",5,"manager",6,"owner",7);
     if(priority.getOrDefault(current,0)>priority.get(role))return false;
     var existing=plugin.getGroupManager().getGroup(current);var selected=plugin.getGroupManager().getGroup(role);
     if(existing!=null&&existing.getPriority()>selected.getPriority())return false;
     plugin.getPlayerDataManager().setPlayerGroup(id,role);Player player=Bukkit.getPlayer(id);if(player!=null){player.recalculatePermissions();player.updateCommands();player.sendMessage("Your MineNight application was accepted. Rank: "+role);}return true;
    }).get(10,java.util.concurrent.TimeUnit.SECONDS);
    JsonObject ack=new JsonObject();ack.addProperty("ticket",ticket);ack.addProperty("ok",ok);request("ack",ack);
   }
  }catch(Exception e){plugin.getLogger().warning("Application bridge delivery deferred; check connectivity and configuration.");}finally{busy.set(false);}
 }
}
