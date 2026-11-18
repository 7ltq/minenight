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
        migrateDefaultSpawnToVoid();
        Bukkit.getPluginManager().registerEvents(this, core());
        getCommand("prac").setExecutor(this);
        getCommand("spawn").setExecutor(this);
    }
    private void migrateDefaultSpawnToVoid() {
        String configuredWorld = getConfig().getString("spawn.world", "world");
        if (configuredWorld == null || !configuredWorld.equalsIgnoreCase("world")) return;

        de.minenight.allinone.MineNightModule voidModule = core().module("void");
        if (voidModule == null) return;

        String voidWorldName = voidModule.getConfig().getString("destination.world", "minenight_void");
        World voidWorld = Bukkit.getWorld(voidWorldName);
        if (voidWorld == null) {
            getLogger().warning("Cannot migrate the default spawn: Void world '" + voidWorldName + "' is not loaded.");
            return;
        }

        getConfig().set("spawn.world", voidWorldName);
        getConfig().set("spawn.x", voidModule.getConfig().getDouble("destination.x", voidWorld.getSpawnLocation().getX()));
        getConfig().set("spawn.y", voidModule.getConfig().getDouble("destination.y", voidWorld.getSpawnLocation().getY()));
        getConfig().set("spawn.z", voidModule.getConfig().getDouble("destination.z", voidWorld.getSpawnLocation().getZ()));
        getConfig().set("spawn.yaw", voidModule.getConfig().getDouble("destination.yaw", 0.0));
        getConfig().set("spawn.pitch", voidModule.getConfig().getDouble("destination.pitch", 0.0));
        saveConfig();
        getLogger().info("Migrated the default practice spawn to Void destination: " + voidWorldName
                + " " + getConfig().getDouble("spawn.x") + " "
                + getConfig().getDouble("spawn.y") + " " + getConfig().getDouble("spawn.z"));
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
            getLogger().warning("Cannot teleport " + player.getName() + ": configured spawn world '" +
                    getConfig().getString("spawn.world", "world") + "' is not loaded.");
            return;
        }

        Location origin = player.getLocation().clone();
        World targetWorld = location.getWorld();
        int chunkX = location.getBlockX() >> 4;
        int chunkZ = location.getBlockZ() >> 4;

        getLogger().info("Preparing spawn teleport for " + player.getName()
                + ": from=" + format(origin)
                + " to=" + format(location)
                + " targetChunk=" + chunkX + "," + chunkZ
                + " loadedBeforeRequest=" + targetWorld.isChunkLoaded(chunkX, chunkZ));

        try {
            targetWorld.getChunkAtAsync(location).whenComplete((chunk, chunkError) ->
                    Bukkit.getScheduler().runTask(core(), () -> {
                        if (!player.isOnline()) return;

                        if (chunkError != null || chunk == null) {
                            getLogger().log(Level.SEVERE, "Could not load spawn chunk for " + player.getName()
                                    + ": " + format(location), chunkError);
                            tell(player, "no-spawn");
                            return;
                        }

                        if (!targetWorld.isChunkLoaded(chunkX, chunkZ)) {
                            getLogger().warning("Spawn chunk future completed but chunk is not loaded for "
                                    + player.getName() + ": " + chunkX + "," + chunkZ);
                            tell(player, "no-spawn");
                            return;
                        }

                        boolean success;
                        try {
                            success = player.teleport(location, PlayerTeleportEvent.TeleportCause.COMMAND);
                        } catch (Throwable error) {
                            getLogger().log(Level.SEVERE, "Synchronous spawn teleport threw for "
                                    + player.getName() + " from=" + format(origin)
                                    + " to=" + format(location), error);
                            tell(player, "no-spawn");
                            return;
                        }

                        if (!success) {
                            getLogger().warning("Spawn teleport returned false after the destination chunk was loaded."
                                    + " player=" + player.getName()
                                    + " from=" + format(origin)
                                    + " current=" + format(player.getLocation())
                                    + " to=" + format(location)
                                    + " chunk=" + chunkX + "," + chunkZ
                                    + " chunkLoaded=" + targetWorld.isChunkLoaded(chunkX, chunkZ));
                            tell(player, "no-spawn");
                            return;
                        }

                        getLogger().info("Spawn teleport succeeded for " + player.getName()
                                + " at " + format(player.getLocation()));
                        if (notify) tell(player, "arrived");
                    }));
        } catch (Throwable error) {
            getLogger().log(Level.SEVERE, "Failed to request spawn chunk for " + player.getName()
                    + " at " + format(location), error);
            tell(player, "no-spawn");
        }
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
