package de.minenight.allinone;

import com.sk89q.worldedit.EditSession;
import com.sk89q.worldedit.WorldEdit;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.function.operation.Operations;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.session.ClipboardHolder;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockBurnEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockIgniteEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.entity.Entity;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;

public final class SchematicModule extends MineNightModule implements Listener {
    private static final String PREFIX = "§8[§dMineNight§8] §7";
    private final Map<UUID, Clipboard> loaded = new HashMap<>();
    private final Map<UUID, String> loadedNames = new HashMap<>();
    private final Map<UUID, RecentPaste> lastPastes = new HashMap<>();
    private final Set<UUID> staffMode = new HashSet<>();
    private final Set<UUID> returningToSpawn = new HashSet<>();
    private final Map<UUID, Long> warnings = new HashMap<>();
    private final List<Region> protectedAreas = new ArrayList<>();
    private File directory;
    private File stateFile;
    private YamlConfiguration state;
    private boolean worldEditInstalled;

    private record Region(String name, UUID world, int minX, int minY, int minZ,
                          int maxX, int maxY, int maxZ) {
        boolean contains(Location location) {
            if (location == null || location.getWorld() == null ||
                    !location.getWorld().getUID().equals(world)) return false;
            return contains(location.getBlockX(), location.getBlockY(), location.getBlockZ());
        }
        boolean containsHorizontal(Location location) {
            return location != null && location.getWorld() != null &&
                    location.getWorld().getUID().equals(world) &&
                    location.getBlockX() >= minX && location.getBlockX() <= maxX &&
                    location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
        }
        boolean touchesHorizontalBorder(Location location) {
            return containsHorizontal(location) &&
                    (location.getBlockX() == minX || location.getBlockX() == maxX ||
                     location.getBlockZ() == minZ || location.getBlockZ() == maxZ);
        }
        boolean contains(Block block) {
            return block.getWorld().getUID().equals(world) &&
                    contains(block.getX(), block.getY(), block.getZ());
        }
        boolean contains(int x, int y, int z) {
            return x >= minX && x <= maxX && y >= minY && y <= maxY &&
                   z >= minZ && z <= maxZ;
        }
    }
    private record RecentPaste(String name, Region region) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        directory = new File(core().getDataFolder(), "schematics");
        if (!directory.exists() && !directory.mkdirs()) {
            getLogger().warning("Could not create schematic directory: " + directory);
        }
        stateFile = new File(getDataFolder(), "regions.yml");
        state = YamlConfiguration.loadConfiguration(stateFile);
        loadRegions();
        worldEditInstalled = Bukkit.getPluginManager().isPluginEnabled("WorldEdit") ||
                             Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit");
        if (!worldEditInstalled) getLogger().warning("Schematic load/paste requires WorldEdit or FAWE installed.");
        if (getCommand("mnschematic") != null) {
            getCommand("mnschematic").setExecutor(this);
            getCommand("mnschematic").setTabCompleter(this);
        }
        Bukkit.getPluginManager().registerEvents(this, core());
        getLogger().info("Loaded " + protectedAreas.size() + " protected schematic regions.");
    }

    @Override public void onDisable() {
        loaded.clear();
        loadedNames.clear();
        lastPastes.clear();
        staffMode.clear();
        returningToSpawn.clear();
        warnings.clear();
    }

    private void loadRegions() {
        protectedAreas.clear();
        ConfigurationSection section = state.getConfigurationSection("protected");
        if (section == null) return;
        for (String id : section.getKeys(false)) {
            String path = "protected." + id;
            try {
                UUID world = UUID.fromString(state.getString(path + ".world", ""));
                int[] min = Arrays.stream(state.getIntegerList(path + ".min").toArray(new Integer[0])).mapToInt(Integer::intValue).toArray();
                int[] max = Arrays.stream(state.getIntegerList(path + ".max").toArray(new Integer[0])).mapToInt(Integer::intValue).toArray();
                if (min.length != 3 || max.length != 3) throw new IllegalArgumentException("Invalid dimensions");
                protectedAreas.add(new Region(state.getString(path + ".name", id), world,
                        min[0], min[1], min[2], max[0], max[1], max[2]));
            } catch (Exception e) {
                getLogger().warning("Skipping invalid schematic protection " + id + ": " + e.getMessage());
            }
        }
    }

    private void saveRegion(Region region) {
        String path = "protected." + UUID.randomUUID();
        state.set(path + ".name", region.name());
        state.set(path + ".world", region.world().toString());
        state.set(path + ".min", List.of(region.minX(), region.minY(), region.minZ()));
        state.set(path + ".max", List.of(region.maxX(), region.maxY(), region.maxZ()));
        try { state.save(stateFile); }
        catch (IOException error) { getLogger().severe("Failed to save schematic protection: " + error.getMessage()); }
    }

    private boolean protectedAt(Location location) {
        for (Region region : protectedAreas) if (region.contains(location)) return true;
        return false;
    }
    private Region protectedFootprintAt(Location location) {
        for (Region region : protectedAreas) if (region.containsHorizontal(location)) return region;
        return null;
    }
    private boolean protectedAt(Block block) {
        for (Region region : protectedAreas) if (region.contains(block)) return true;
        return false;
    }
    private boolean override(Player player) {
        return player != null && staffMode.contains(player.getUniqueId()) &&
               player.hasPermission("minenight.schematic.admin");
    }
    private boolean deny(Player player, Block block) {
        return protectedAt(block) && !override(player);
    }
    private void warn(Player player) {
        long now = System.currentTimeMillis();
        long last = warnings.getOrDefault(player.getUniqueId(), 0L);
        if (now - last > 1500) {
            player.sendMessage(PREFIX + "This spawn schematic is protected.");
            warnings.put(player.getUniqueId(), now);
        }
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(PREFIX + "Run schematic commands in game.");
            return true;
        }
        if (!sender.hasPermission("minenight.schematic.admin")) {
            sender.sendMessage(PREFIX + "No permission.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage(PREFIX + "/mnschematic load <name> | paste | <name> protect | staff mode | list");
            return true;
        }
        String operation = args[0].toLowerCase(java.util.Locale.ROOT);
        if (operation.equals("staff") && args.length >= 2 && args[1].equalsIgnoreCase("mode")) {
            if (!staffMode.add(player.getUniqueId())) {
                staffMode.remove(player.getUniqueId());
                player.sendMessage(PREFIX + "Schematic Staff Mode: §cOFF§7. Spawn protection applies to you.");
            } else {
                player.sendMessage(PREFIX + "Schematic Staff Mode: §aON§7. Remember to turn it off.");
            }
            return true;
        }
        if (operation.equals("list")) {
            String[] filenames = directory.list((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
            player.sendMessage(PREFIX + "Schematics: " + (filenames == null || filenames.length == 0 ? "none" : String.join(", ", filenames)));
            return true;
        }
        if (operation.equals("load")) {
            if (args.length != 2 || !validName(args[1])) {
                player.sendMessage(PREFIX + "Usage: /mnschematic load <name>");
                return true;
            }
            if (!worldEditInstalled) {
                player.sendMessage(PREFIX + "Install a compatible WorldEdit or FAWE build to load schematics.");
                return true;
            }
            File file = findFile(args[1]);
            if (file == null) {
                player.sendMessage(PREFIX + "Schematic not found. Put .schem files in plugins/MineNightAllInOne/schematics/");
                return true;
            }
            try {
                ClipboardFormat format = ClipboardFormats.findByFile(file);
                if (format == null) {
                    player.sendMessage(PREFIX + "Unsupported schematic format.");
                    return true;
                }
                try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                    Clipboard clipboard = reader.read();
                    loaded.put(player.getUniqueId(), clipboard);
                    loadedNames.put(player.getUniqueId(), stripExtension(file.getName()));
                    player.sendMessage(PREFIX + "Loaded " + file.getName() + " (" + clipboard.getDimensions() + "). Use /mnschematic paste.");
                }
            } catch (Exception e) {
                getLogger().warning("Failed to load " + file.getName() + ": " + e.getMessage());
                player.sendMessage(PREFIX + "Could not read schematic: " + e.getMessage());
            }
            return true;
        }
        if (operation.equals("paste")) {
            if (!worldEditInstalled) {
                player.sendMessage(PREFIX + "Install WorldEdit or FAWE to paste schematics.");
                return true;
            }
            Clipboard clipboard = loaded.get(player.getUniqueId());
            String name = loadedNames.get(player.getUniqueId());
            if (clipboard == null || name == null) {
                player.sendMessage(PREFIX + "First use /mnschematic load <name>.");
                return true;
            }
            Location location = player.getLocation();
            BlockVector3 target = BlockVector3.at(location.getBlockX(), location.getBlockY(), location.getBlockZ());
            BlockVector3 offset = target.subtract(clipboard.getOrigin());
            BlockVector3 minimum = clipboard.getRegion().getMinimumPoint().add(offset);
            BlockVector3 maximum = clipboard.getRegion().getMaximumPoint().add(offset);
            long volume = (long)(maximum.x()-minimum.x()+1)*(maximum.y()-minimum.y()+1)*(maximum.z()-minimum.z()+1);
            long maxVolume = getConfig().getLong("paste.max-volume", 1500000L);
            if (volume > maxVolume) {
                player.sendMessage(PREFIX + "Schematic exceeds the configured maximum volume (" + maxVolume + " blocks).");
                return true;
            }
            try (EditSession session = WorldEdit.getInstance().newEditSession(BukkitAdapter.adapt(location.getWorld()))) {
                Operations.complete(new ClipboardHolder(clipboard).createPaste(session).to(target).ignoreAirBlocks(false).build());
                Region region = new Region(name, location.getWorld().getUID(), minimum.x(), minimum.y(), minimum.z(), maximum.x(), maximum.y(), maximum.z());
                lastPastes.put(player.getUniqueId(), new RecentPaste(name, region));
                player.sendMessage(PREFIX + "Pasted " + name + ". Protection bounds: (" + minimum + ") to (" + maximum + ").");
                player.sendMessage(PREFIX + "Activate with /mnschematic " + name + " protect.");
            } catch (Exception error) {
                getLogger().warning("Schematic paste failed: " + error.getMessage());
                player.sendMessage(PREFIX + "Paste failed: " + error.getMessage());
            }
            return true;
        }
        String protectedName = null;
        if (operation.equals("protect") && args.length == 2) protectedName = args[1];
        else if (args.length == 2 && args[1].equalsIgnoreCase("protect")) protectedName = args[0];
        if (protectedName != null) {
            RecentPaste paste = lastPastes.get(player.getUniqueId());
            if (paste == null || !paste.name().equalsIgnoreCase(protectedName)) {
                player.sendMessage(PREFIX + "Paste that schematic first, then use /mnschematic " + protectedName + " protect.");
                return true;
            }
            protectedAreas.add(paste.region());
            saveRegion(paste.region());
            lastPastes.remove(player.getUniqueId());
            player.sendMessage(PREFIX + "Protected " + protectedName + " using its exact schematic bounds. All players, including OPs, are restricted unless Staff Mode is enabled.");
            return true;
        }
        player.sendMessage(PREFIX + "/mnschematic load <name> | paste | <name> protect | staff mode | list");
        return true;
    }

    private boolean validName(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,100}(\\.schem(atic)?)?");
    }
    private File findFile(String value) {
        for (String name : List.of(value, value + ".schem", value + ".schematic")) {
            File candidate = new File(directory, name);
            if (candidate.isFile() && (name.endsWith(".schem") || name.endsWith(".schematic"))) return candidate;
        }
        return null;
    }
    private String stripExtension(String filename) {
        if (filename.endsWith(".schematic")) return filename.substring(0, filename.length()-10);
        if (filename.endsWith(".schem")) return filename.substring(0, filename.length()-6);
        return filename;
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("minenight.schematic.admin")) return List.of();
        if (args.length == 1) {
            String[] files = directory.list((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
            List<String> candidates = new ArrayList<>(List.of("load", "paste", "protect", "staff", "list"));
            if (files != null) candidates.addAll(Arrays.stream(files).map(this::stripExtension).toList());
            return matching(candidates, args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("staff")) return matching(List.of("mode"), args[1]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("load") || args[0].equalsIgnoreCase("protect"))) {
            String[] files = directory.list((dir, name) -> name.endsWith(".schem") || name.endsWith(".schematic"));
            return matching(files == null ? List.of() : Arrays.stream(files).map(this::stripExtension).toList(), args[1]);
        }
        if (args.length == 2) return matching(List.of("protect"), args[1]);
        return List.of();
    }
    private List<String> matching(List<String> values, String prefix) {
        return values.stream().filter(v -> v.toLowerCase().startsWith(prefix.toLowerCase())).toList();
    }

    // Spawn protection includes combat and commands from external plugins, including Bot.
    // Staff Mode is deliberately not an exception to the no-combat/no-bot rules.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!protectedAt(player.getLocation())) return;
        // Do not prevent harmless environmental mechanics such as hunger or fall damage.
        if (event instanceof EntityDamageByEntityEvent) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnAttack(EntityDamageByEntityEvent event) {
        Entity attacker = event.getDamager();
        if (attacker instanceof Projectile projectile && projectile.getShooter() instanceof Entity shooter) {
            attacker = shooter;
        }
        // Players cannot attack out of the protected zone either.
        if (attacker instanceof Player player && protectedAt(player.getLocation())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBotCommand(PlayerCommandPreprocessEvent event) {
        if (!protectedAt(event.getPlayer().getLocation())) return;
        String message = event.getMessage().trim().toLowerCase(java.util.Locale.ROOT);
        String label = message.split("\\s+", 2)[0];
        // Block both the normal command and namespaced variants of /bot.
        if (label.equals("/bot") || label.endsWith(":bot")) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(PREFIX + "Practice bots are disabled in the protected spawn.");
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (deny(event.getPlayer(), event.getBlock())) { event.setCancelled(true); warn(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (deny(event.getPlayer(), event.getBlockPlaced())) { event.setCancelled(true); warn(event.getPlayer()); }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (override(event.getPlayer())) return;
        ItemStack item = event.getItem();
        if (protectedAt(event.getPlayer().getLocation()) && item != null &&
                (item.getType() == Material.ENDER_PEARL || item.getType() == Material.EXPERIENCE_BOTTLE) &&
                (event.getAction() == Action.RIGHT_CLICK_AIR || event.getAction() == Action.RIGHT_CLICK_BLOCK)) {
            event.setCancelled(true);
            warn(event.getPlayer());
            return;
        }
        if (event.getClickedBlock() != null && protectedAt(event.getClickedBlock())) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onProjectile(ProjectileLaunchEvent event) {
        Projectile projectile = event.getEntity();
        if (!(projectile.getShooter() instanceof Player player) || override(player)) return;
        if (protectedAt(player.getLocation()) &&
                (projectile.getType() == org.bukkit.entity.EntityType.ENDER_PEARL ||
                 projectile.getType() == org.bukkit.entity.EntityType.EXPERIENCE_BOTTLE)) {
            event.setCancelled(true);
            warn(player);
        }
    }
    // Players may move freely inside the protected spawn. When they cross its
    // horizontal schematic boundary, return them to the configured practice spawn.
    // TPA arrivals are allowed; the boundary applies only when they later walk out.
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSpawnBorderMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();
        // Teleports (including /spawn, join-spawn and TPA) are intentional moves,
        // not attempts to walk through the spawn border. Never cancel them here.
        if (event instanceof PlayerTeleportEvent) return;
        if (override(player) || returningToSpawn.contains(player.getUniqueId())) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null || (from.getWorld() == to.getWorld() &&
                from.getBlockX() == to.getBlockX() && from.getBlockZ() == to.getBlockZ())) return;

        Region currentRegion = protectedFootprintAt(from);
        Region destinationRegion = protectedFootprintAt(to);
        boolean crossedOuterEdge = currentRegion != null && destinationRegion == null;
        boolean touchedOuterEdge = destinationRegion != null && destinationRegion.touchesHorizontalBorder(to);
        if (!crossedOuterEdge && !touchedOuterEdge) return;

        event.setCancelled(true);
        UUID playerId = player.getUniqueId();
        if (!returningToSpawn.add(playerId)) return;

        MineNightModule spawnModule = core().module("spawn");
        Location spawn = spawnModule instanceof de.minenight.pracspawn.MineNightPracticeSpawn practiceSpawn
                ? practiceSpawn.spawn() : null;
        if (spawn == null || spawn.getWorld() == null) {
            returningToSpawn.remove(playerId);
            warn(player);
            return;
        }

        player.teleportAsync(spawn, PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((success, error) ->
                Bukkit.getScheduler().runTask(core(), () -> {
                    returningToSpawn.remove(playerId);
                    if ((error != null || !Boolean.TRUE.equals(success)) && player.isOnline()) warn(player);
                }));
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        // Let /tp, /rtp, /rtpq, /spawn, /void and plugin teleports through.
        // Ender pearls stay disabled inside protected schematic areas.
        if (override(event.getPlayer()) || event.getTo() == null ||
                event.getCause() != PlayerTeleportEvent.TeleportCause.ENDER_PEARL) return;
        if (protectedAt(event.getFrom())) {
            event.setCancelled(true);
            warn(event.getPlayer());
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (deny(event.getPlayer(), event.getBlockClicked()) ||
                deny(event.getPlayer(), event.getBlockClicked().getRelative(event.getBlockFace()))) {
            event.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (deny(event.getPlayer(), event.getBlockClicked())) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBurn(BlockBurnEvent event) { if (protectedAt(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onIgnite(BlockIgniteEvent event) { if (protectedAt(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFlow(BlockFromToEvent event) { if (protectedAt(event.getToBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityBlock(EntityChangeBlockEvent event) { if (protectedAt(event.getBlock())) event.setCancelled(true); }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplosion(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectedAt);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplosion(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectedAt);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> protectedAt(b) || protectedAt(b.getRelative(event.getDirection())))) event.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (event.getBlocks().stream().anyMatch(b -> protectedAt(b) || protectedAt(b.getRelative(event.getDirection())))) event.setCancelled(true);
    }
}
