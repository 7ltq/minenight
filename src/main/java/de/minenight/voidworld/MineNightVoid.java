package de.minenight.voidworld;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerTeleportEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.generator.ChunkGenerator;
import java.util.List;
import java.util.Random;

public final class MineNightVoid extends de.minenight.allinone.MineNightModule implements Listener {
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final ChunkGenerator generator = new EmptyGenerator();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, core());
        String worldName = getConfig().getString("destination.world", "minenight_void");
        World world = Bukkit.getWorld(worldName);
        if (world == null) world = Bukkit.createWorld(new WorldCreator(worldName).generator(generator).generateStructures(false));
        if (world == null) getLogger().severe("Unable to load Void world: " + worldName);
        PluginCommand command = getCommand("void");
        if (command == null) throw new IllegalStateException("Missing /void in plugin.yml");
        command.setExecutor(this);
        command.setTabCompleter(this);
        getLogger().info("Void destination world: " + worldName + "; command registered.");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is only available to players.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("set")) {
            if (!player.isOp() && !player.hasPermission("minenightvoid.admin")) {
                player.sendMessage("You do not have permission.");
                return true;
            }
            Location location = player.getLocation();
            getConfig().set("destination.world", location.getWorld().getName());
            getConfig().set("destination.x", location.getX());
            getConfig().set("destination.y", location.getY());
            getConfig().set("destination.z", location.getZ());
            getConfig().set("destination.yaw", (double) location.getYaw());
            getConfig().set("destination.pitch", (double) location.getPitch());
            saveConfig();
            player.sendMessage("Void lobby destination saved in world " + location.getWorld().getName() + ".");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!player.isOp() && !player.hasPermission("minenightvoid.admin")) {
                player.sendMessage("You do not have permission.");
                return true;
            }
            reloadConfig();
            player.sendMessage("Void destination reloaded.");
            return true;
        }
        if (args.length > 0 && !args[0].equalsIgnoreCase("status")) {
            player.sendMessage("Usage: /void [set|status|reload]");
            return true;
        }
        String worldName = getConfig().getString("destination.world", "minenight_void");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            player.sendMessage("Void world '" + worldName + "' is not loaded. Check plugins/MineNightAllInOne/void/config.yml.");
            return true;
        }
        Location destination = new Location(world,
            getConfig().getDouble("destination.x", 0.5),
            getConfig().getDouble("destination.y", 65.0),
            getConfig().getDouble("destination.z", 0.5),
            (float) getConfig().getDouble("destination.yaw", 0.0),
            (float) getConfig().getDouble("destination.pitch", 0.0));
        if (args.length > 0) {
            player.sendMessage("Void destination: " + world.getName() + " x=" + destination.getBlockX() + " y=" + destination.getBlockY() + " z=" + destination.getBlockZ());
            return true;
        }
        pending.add(player.getUniqueId());
        player.teleportAsync(destination, PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((success, error) -> {
            Bukkit.getScheduler().runTask(core(), () -> {
                pending.remove(player.getUniqueId());
                if (!player.isOnline()) return;
                if (error != null) {
                    player.sendMessage("Void teleport failed: " + error.getClass().getSimpleName() + ". Check server console.");
                    getLogger().warning("Void teleport failed for " + player.getName() + ": " + error);
                } else if (!Boolean.TRUE.equals(success)) {
                    player.sendMessage("Void teleport cancelled by a teleport event. See server console for diagnostics.");
                    getLogger().warning("/void teleport returned false for " + player.getName() + " (destination " + world.getName() + "). Check other plugins handling PlayerTeleportEvent.");
                } else {
                    player.sendMessage("Teleported to " + world.getName() + ".");
                    Bukkit.getScheduler().runTaskLater(core(), () -> {
                        if (player.isOnline() && !player.getWorld().getUID().equals(world.getUID())) {
                            player.sendMessage("Another plugin moved you out of the Void world after teleportation.");
                        }
                    }, 10L);
                }
            });
        });
        return true;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onTeleportDiagnostic(PlayerTeleportEvent event) {
        if (!pending.contains(event.getPlayer().getUniqueId())) return;
        String from = event.getFrom().getWorld().getName();
        String to = event.getTo() == null || event.getTo().getWorld() == null ? "null" : event.getTo().getWorld().getName();
        getLogger().info("/void teleport event: player=" + event.getPlayer().getName() + ", from=" + from + ", to=" + to + ", cause=" + event.getCause() + ", cancelled=" + event.isCancelled());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = sender.isOp() || sender.hasPermission("minenightvoid.admin") ? List.of("set", "status", "reload") : List.of("status");
        return options.stream().filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return generator;
    }

    private static final class EmptyGenerator extends ChunkGenerator {
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public Location getFixedSpawnLocation(World world, Random random) { return new Location(world, 0.5, 65, 0.5); }
    }
}
