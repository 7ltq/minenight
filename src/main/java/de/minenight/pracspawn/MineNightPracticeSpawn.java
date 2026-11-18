package de.minenight.pracspawn;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.Locale;
import java.util.concurrent.CompletionException;
import java.util.logging.Level;

public final class MineNightPracticeSpawn extends de.minenight.allinone.MineNightModule implements Listener, CommandExecutor {
    private final MiniMessage mini = MiniMessage.miniMessage();
    @Override public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, core());
        getCommand("prac").setExecutor(this);
        getCommand("spawn").setExecutor(this);
    }
    public Location spawn() {
        World world = Bukkit.getWorld(getConfig().getString("spawn.world", "world"));
        if (world == null) return null;
        return new Location(world, getConfig().getDouble("spawn.x"), getConfig().getDouble("spawn.y"), getConfig().getDouble("spawn.z"), (float)getConfig().getDouble("spawn.yaw"), (float)getConfig().getDouble("spawn.pitch"));
    }
    public void sendSpawn(Player player) {
        sendSpawn(player, true);
    }
    private void sendSpawn(Player player, boolean notify) {
        Location location = spawn();
        if (location == null) {
            tell(player, "no-spawn");
            getLogger().warning("Cannot teleport " + player.getName() + " to spawn: configured world '" +
                    getConfig().getString("spawn.world", "world") + "' is not loaded. Set the spawn again with /prac spawn set here.");
            return;
        }

        Location origin = player.getLocation().clone();
        World targetWorld = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;
        boolean targetChunkLoaded = targetWorld.isChunkLoaded(chunkX, chunkZ);
        boolean insideWorldBorder = targetWorld.getWorldBorder().isInside(location);
        boolean withinWorldHeight = location.getY() >= targetWorld.getMinHeight()
                && location.getY() < targetWorld.getMaxHeight();

        player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((success, error) ->
                Bukkit.getScheduler().runTask(core(), () -> {
                    if (!player.isOnline()) {
                        getLogger().warning("Spawn teleport finished after player " + player.getName() + " went offline.");
                        return;
                    }
                    if (error != null) {
                        Throwable cause = error;
                        while (cause instanceof CompletionException && cause.getCause() != null) {
                            cause = cause.getCause();
                        }
                        getLogger().log(Level.SEVERE, "Spawn teleport threw an exception for " + player.getName()
                                + ". from=" + format(origin)
                                + " to=" + format(location)
                                + " targetChunkLoadedBeforeRequest=" + targetChunkLoaded
                                + " insideWorldBorder=" + insideWorldBorder
                                + " withinWorldHeight=" + withinWorldHeight, cause);
                        tell(player, "no-spawn");
                        return;
                    }
                    if (!Boolean.TRUE.equals(success)) {
                        Location current = player.getLocation();
                        getLogger().warning("Spawn teleport failed for " + player.getName()
                                + ": teleportAsync returned false without an exception."
                                + " from=" + format(origin)
                                + " current=" + format(current)
                                + " to=" + format(location)
                                + " targetChunkLoadedBeforeRequest=" + targetChunkLoaded
                                + " insideWorldBorder=" + insideWorldBorder
                                + " withinWorldHeight=" + withinWorldHeight
                                + " playerWorldUnchanged=" + current.getWorld().equals(origin.getWorld())
                                + ". This result does not by itself prove that another plugin cancelled the teleport.");
                        tell(player, "no-spawn");
                        return;
                    }
                    if (notify) tell(player, "arrived");
                }));
    }
    private String format(Location location) {
        return location.getWorld().getName() + " "
                + String.format(Locale.ROOT, "%.2f %.2f %.2f",
                location.getX(), location.getY(), location.getZ());
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("prac")) {
            if (!sender.hasPermission("minenightspawn.admin")) { tell(sender,"no-permission"); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
            if (args.length != 3 || !args[0].equalsIgnoreCase("spawn") || !args[1].equalsIgnoreCase("set") || !args[2].equalsIgnoreCase("here")) { tell(sender,"usage"); return true; }
            Location loc = player.getLocation();
            getConfig().set("spawn.world",loc.getWorld().getName());
            getConfig().set("spawn.x",loc.getX()); getConfig().set("spawn.y",loc.getY()); getConfig().set("spawn.z",loc.getZ());
            getConfig().set("spawn.yaw",loc.getYaw()); getConfig().set("spawn.pitch",loc.getPitch());
            saveConfig(); tell(player,"set"); return true;
        }
        if (!(sender instanceof Player player)) { sender.sendMessage("Player only."); return true; }
        if (!player.hasPermission("minenightspawn.use")) { tell(player,"no-permission"); return true; }
        if (!getConfig().getBoolean("settings.spawn-command-allowed-during-combat",false) && player.getScoreboardTags().contains("minenight_rtpq_duel")) { tell(player,"combat"); return true; }
        sendSpawn(player); return true;
    }
    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // MineNight always starts players at its configured practice spawn.
        // Do not let an old false value in an existing config disable this behavior.
        Bukkit.getScheduler().runTaskLater(core(), () -> {
            if (event.getPlayer().isOnline()) sendSpawn(event.getPlayer(), false);
        }, 5L);
    }
    @EventHandler(priority=EventPriority.HIGH) public void onRespawn(PlayerRespawnEvent event) {
        if (!getConfig().getBoolean("settings.override-respawn-location",true)) return;
        Location location = spawn();
        if (location != null) event.setRespawnLocation(location);
    }
    private void tell(CommandSender sender,String key) {
        sender.sendMessage(mini.deserialize(getConfig().getString("messages.prefix", "") + getConfig().getString("messages."+key,"<red>"+key)));
    }
}
