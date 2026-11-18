package de.minenight.performance;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.Chunk;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class MineNightPerformance extends de.minenight.allinone.MineNightModule implements Listener {
    private final Map<UUID, Item> trackedItems = new HashMap<>();
    private final MiniMessage miniMessage = MiniMessage.miniMessage();
    private BukkitTask sweepTask;
    private int despawnTicks;
    private boolean despawnEnabled;
    private boolean blockPluginCommands;
    private boolean hidePluginCommands;
    private Set<String> excludedWorlds;
    private Set<String> excludedMaterials;
    private long removedItems;
    private boolean noMobsEnabled;
    private Set<String> mobExcludedWorlds;
    private boolean allowCustomMobSpawns;
    private long blockedMobs;
    private boolean blockDropsEnabled;
    private Set<String> dropExcludedWorlds;
    private long suppressedBlockDrops;
    private final Map<String, Long> recentlyBrokenBlocks = new HashMap<>();
    private long lastBrokenCleanup;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        // Upgrade the old 15-second default without overriding customized values.
        if (getConfig().getInt("items.despawn-seconds", 10) == 15) {
            getConfig().set("items.despawn-seconds", 10);
            saveConfig();
        }
        loadOptions();
        Bukkit.getPluginManager().registerEvents(this, core());
        getLogger().info("MineNightPerformance enabled. Item despawn: " + (despawnEnabled ? despawnTicks / 20 + " seconds" : "disabled"));
    }

    @Override
    public void onDisable() {
        if (sweepTask != null) sweepTask.cancel();
        trackedItems.clear();
        recentlyBrokenBlocks.clear();
    }

    private void loadOptions() {
        blockDropsEnabled = getConfig().getBoolean("block-drops.disabled", true);
        dropExcludedWorlds = normalize(getConfig().getStringList("block-drops.excluded-worlds"));
        noMobsEnabled = getConfig().getBoolean("mobs.block-spawning", true);
        mobExcludedWorlds = normalize(getConfig().getStringList("mobs.excluded-worlds"));
        allowCustomMobSpawns = getConfig().getBoolean("mobs.allow-custom-spawns", false);
        despawnEnabled = getConfig().getBoolean("items.enabled", true);
        int seconds = Math.max(1, Math.min(3600, getConfig().getInt("items.despawn-seconds", 10)));
        despawnTicks = seconds * 20;
        excludedWorlds = normalize(getConfig().getStringList("items.excluded-worlds"));
        excludedMaterials = normalize(getConfig().getStringList("items.excluded-materials"));
        blockPluginCommands = getConfig().getBoolean("commands.block-plugin-list", true);
        hidePluginCommands = getConfig().getBoolean("commands.hide-plugin-list-suggestions", true);
        if (sweepTask != null) sweepTask.cancel();
        trackedItems.clear();
        if (despawnEnabled) {
            for (World world : Bukkit.getWorlds()) {
                if (excludedWorlds.contains(world.getName().toLowerCase(Locale.ROOT))) continue;
                for (Item item : world.getEntitiesByClass(Item.class)) track(item);
            }
            sweepTask = Bukkit.getScheduler().runTaskTimer(core(), this::sweepItems, 20L, 20L);
        }
    }

    private Set<String> normalize(Iterable<String> values) {
        Set<String> result = new HashSet<>();
        for (String value : values) result.add(value.toLowerCase(Locale.ROOT));
        return result;
    }

    private void track(Item item) {
        if (!despawnEnabled || !item.isValid()) return;
        if (excludedWorlds.contains(item.getWorld().getName().toLowerCase(Locale.ROOT))) return;
        if (excludedMaterials.contains(item.getItemStack().getType().name().toLowerCase(Locale.ROOT))) return;
        trackedItems.put(item.getUniqueId(), item);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onItemSpawn(ItemSpawnEvent event) {
        track(event.getEntity());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!despawnEnabled) return;
        if (excludedWorlds.contains(event.getWorld().getName().toLowerCase(Locale.ROOT))) return;
        Chunk chunk = event.getChunk();
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof Item item) track(item);
        }
    }

    private void sweepItems() {
        Iterator<Map.Entry<UUID, Item>> iterator = trackedItems.entrySet().iterator();
        while (iterator.hasNext()) {
            Item item = iterator.next().getValue();
            if (!item.isValid() || item.isDead()) {
                iterator.remove();
                continue;
            }
            if (item.getTicksLived() >= despawnTicks) {
                item.remove();
                iterator.remove();
                removedItems++;
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!noMobsEnabled) return;
        LivingEntity entity = event.getEntity();
        if (entity instanceof Player || entity instanceof ArmorStand) return;
        if (mobExcludedWorlds.contains(entity.getWorld().getName().toLowerCase(Locale.ROOT))) return;
        if (allowCustomMobSpawns && event.getSpawnReason() == CreatureSpawnEvent.SpawnReason.CUSTOM) return;
        event.setCancelled(true);
        blockedMobs++;
    }

    private boolean blockDropsIn(World world) {
        return blockDropsEnabled && !dropExcludedWorlds.contains(world.getName().toLowerCase(Locale.ROOT));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!blockDropsIn(event.getBlock().getWorld())) return;
        event.setDropItems(false);
        suppressedBlockDrops++;
        markBroken(event.getBlock().getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockDropItem(BlockDropItemEvent event) {
        if (!blockDropsIn(event.getBlock().getWorld())) return;
        event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!blockDropsIn(event.getLocation().getWorld())) return;
        event.setYield(0.0f);
        suppressedBlockDrops += event.blockList().size();
        for (org.bukkit.block.Block block : event.blockList()) markBroken(block.getLocation());
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!blockDropsIn(event.getBlock().getWorld())) return;
        event.setYield(0.0f);
        suppressedBlockDrops += event.blockList().size();
        for (org.bukkit.block.Block block : event.blockList()) markBroken(block.getLocation());
    }

    private String brokenKey(org.bukkit.Location location) {
        return location.getWorld().getUID() + ":" + location.getBlockX() + ":" + location.getBlockY() + ":" + location.getBlockZ();
    }

    private void markBroken(org.bukkit.Location location) {
        recentlyBrokenBlocks.put(brokenKey(location), System.currentTimeMillis() + 250L);
        if (recentlyBrokenBlocks.size() > 4096 || System.currentTimeMillis() - lastBrokenCleanup > 1000L) {
            lastBrokenCleanup = System.currentTimeMillis();
            recentlyBrokenBlocks.entrySet().removeIf(entry -> entry.getValue() < lastBrokenCleanup);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockItemSpawn(ItemSpawnEvent event) {
        Item item = event.getEntity();
        if (!blockDropsIn(item.getWorld())) return;
        org.bukkit.Location location = item.getLocation();
        long now = System.currentTimeMillis();
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            String key = brokenKey(location.clone().add(x, y, z));
            Long expires = recentlyBrokenBlocks.get(key);
            if (expires != null && expires >= now) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void sendDropStatus(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize("<gradient:#8175AE:#D5DCE8>MineNight</gradient> <dark_gray>» <gray>Block drops: "
            + (blockDropsEnabled ? "<red>DISABLED" : "<green>ENABLED") + "<gray>. Suppressed blocks: <white>" + suppressedBlockDrops));
    }

    private boolean runNoBlockDrops(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(miniMessage.deserialize(getConfig().getString("messages.no-permission", "<red>No permission.")));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendDropStatus(sender);
            sender.sendMessage("Usage: /noblockdrops <on|off|status>");
            return true;
        }
        if (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off")) {
            sender.sendMessage("Usage: /noblockdrops <on|off|status>");
            return true;
        }
        blockDropsEnabled = args[0].equalsIgnoreCase("on");
        getConfig().set("block-drops.disabled", blockDropsEnabled);
        saveConfig();
        sendDropStatus(sender);
        return true;
    }

    private boolean runNo(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[0].equalsIgnoreCase("block") && args[1].equalsIgnoreCase("drop")) {
            return runNoBlockDrops(sender, java.util.Arrays.copyOfRange(args, 2, args.length));
        }
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(miniMessage.deserialize(getConfig().getString("messages.no-permission", "<red>No permission.")));
            return true;
        }
        sender.sendMessage("Usage: /no block drop <on|off|status>");
        return true;
    }

    private void sendMobStatus(CommandSender sender) {
        sender.sendMessage(miniMessage.deserialize("<gradient:#8175AE:#D5DCE8>MineNight</gradient> <dark_gray>» <gray>Mob spawning: "
            + (noMobsEnabled ? "<red>BLOCKED" : "<green>ALLOWED") + "<gray>. Blocked so far: <white>" + blockedMobs));
    }

    private boolean runNoMobs(CommandSender sender, String[] args) {
        if (sender instanceof Player player && !player.isOp()) {
            sender.sendMessage(miniMessage.deserialize(getConfig().getString("messages.no-permission", "<red>You do not have permission to use this command.")));
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sendMobStatus(sender);
            sender.sendMessage("Usage: /nomobs <on|off|status>");
            return true;
        }
        if (!args[0].equalsIgnoreCase("on") && !args[0].equalsIgnoreCase("off")) {
            sender.sendMessage("Usage: /nomobs <on|off|status>");
            return true;
        }
        noMobsEnabled = args[0].equalsIgnoreCase("on");
        getConfig().set("mobs.block-spawning", noMobsEnabled);
        saveConfig();
        sendMobStatus(sender);
        return true;
    }

    private boolean isPluginListCommand(String input) {
        String command = input.stripLeading().split("\\s+", 2)[0].toLowerCase(Locale.ROOT);
        if (command.startsWith("/")) command = command.substring(1);
        int colon = command.lastIndexOf(':');
        if (colon >= 0) command = command.substring(colon + 1);
        return command.equals("plugins") || command.equals("pl");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!blockPluginCommands || event.getPlayer().isOp()) return;
        if (!isPluginListCommand(event.getMessage())) return;
        event.setCancelled(true);
        event.getPlayer().sendMessage(miniMessage.deserialize(getConfig().getString("messages.no-permission", "<red>You do not have permission to use this command.")));
    }

    @EventHandler
    public void onCommandSuggestions(PlayerCommandSendEvent event) {
        if (!hidePluginCommands || event.getPlayer().isOp()) return;
        event.getCommands().removeIf(this::isPluginListCommand);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("nomobs")) return runNoMobs(sender, args);
        if (command.getName().equalsIgnoreCase("noblockdrops")) return runNoBlockDrops(sender, args);
        if (command.getName().equalsIgnoreCase("no")) return runNo(sender, args);
        if (!(sender instanceof Player player) || player.isOp()) {
            if (args.length > 0 && args[0].equalsIgnoreCase("drops")) {
                return runNoBlockDrops(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("mobs")) {
                return runNoMobs(sender, java.util.Arrays.copyOfRange(args, 1, args.length));
            }
            if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
                reloadConfig();
                loadOptions();
                sender.sendMessage(miniMessage.deserialize("<gradient:#8175AE:#D5DCE8>MineNight</gradient> <gray>» <green>Configuration reloaded."));
                return true;
            }
            Runtime runtime = Runtime.getRuntime();
            long used = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long allocated = runtime.totalMemory() / (1024 * 1024);
            long max = runtime.maxMemory() / (1024 * 1024);
            sender.sendMessage(miniMessage.deserialize("<gradient:#8175AE:#D5DCE8>MineNight Performance</gradient>"));
            sender.sendMessage("Memory: " + used + " / " + allocated + " MiB allocated (max " + max + " MiB)");
            sender.sendMessage("Tracked dropped items: " + trackedItems.size() + " | Removed: " + removedItems);
            sender.sendMessage("Item lifetime: " + (despawnEnabled ? despawnTicks / 20 + " seconds" : "disabled"));
            sender.sendMessage("Mob spawning: " + (noMobsEnabled ? "blocked" : "allowed") + " | Spawns blocked: " + blockedMobs);
            sender.sendMessage("Block drops: " + (blockDropsEnabled ? "disabled" : "enabled") + " | Suppressed blocks: " + suppressedBlockDrops);
            sender.sendMessage("Usage: /mnperf [reload|mobs on|mobs off|mobs status|drops on|drops off|drops status]");
        } else {
            sender.sendMessage(miniMessage.deserialize(getConfig().getString("messages.no-permission", "<red>You do not have permission to use this command.")));
        }
        return true;
    }
}
