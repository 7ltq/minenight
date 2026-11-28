package de.minenight.rtp;

import net.kyori.adventure.text.Component;
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
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class MineNightRTP extends de.minenight.allinone.MineNightModule implements Listener, CommandExecutor, TabCompleter {
    private static final List<String> BIOMES = List.of("plain", "snow", "desert", "mushroom", "badlands", "end");
    private static final java.util.Map<String, String> GENERATED_WORLD_NAMES = java.util.Map.of(
            "plain", "plains_world",
            "snow", "snow_world",
            "desert", "desert_world",
            "mushroom", "mushroom_world",
            "badlands", "badlands_world",
            "end", "end_world"
    );
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Set<UUID> inProgress = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        upgradeLegacyIcons();
        upgradeLegacyWorldNames();
        ensureEndDestination();
        Bukkit.getPluginManager().registerEvents(this, core());
        for (String command : List.of("rtp", "rtpgui", "rtpreload")) {
            var registered = getCommand(command);
            if (registered != null) {
                registered.setExecutor(this);
                registered.setTabCompleter(this);
            }
        }
    }

    private void upgradeLegacyWorldNames() {
        java.util.Map<String, String> legacy = java.util.Map.of(
                "plain", "plains_world",
                "snow", "snow_world",
                "desert", "desert_world",
                "mushroom", "mushroom_world",
                "badlands", "badlands_world",
                "end", "end_world"
        );
        boolean changed = false;
        for (var entry : legacy.entrySet()) {
            String path = "destinations." + entry.getKey() + ".world";
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

    private void ensureEndDestination() {
        if (getConfig().getConfigurationSection("destinations.end") != null) return;
        getConfig().set("destinations.end.enabled", true);
        getConfig().set("destinations.end.world", "end_world");
        getConfig().set("destinations.end.display-name", "<#91B2FF>The End");
        getConfig().set("destinations.end.plain-name", "The End");
        getConfig().set("destinations.end.icon", "END_STONE");
        getConfig().set("destinations.end.slot", 22);
        saveConfig();
    }

    private void upgradeLegacyIcons() {
        java.util.Map<String, String> previous = java.util.Map.of(
            "plain", "FROG_SPAWN_EGG",
            "snow", "POLAR_BEAR_SPAWN_EGG",
            "desert", "CAMEL_SPAWN_EGG",
            "mushroom", "MOOSHROOM_SPAWN_EGG",
            "badlands", "ARMADILLO_SPAWN_EGG"
        );
        boolean changed = false;
        for (String biome : BIOMES) {
            String path = "destinations." + biome + ".icon";
            String previousIcon = previous.get(biome);
            String configuredIcon = getConfig().getString(path, "");
            if (previousIcon != null && configuredIcon != null && previousIcon.equalsIgnoreCase(configuredIcon)) {
                getConfig().set(path, defaultIcon(biome));
                changed = true;
            }
        }
        if (changed) saveConfig();
    }

    @Override
    public void onDisable() {
        inProgress.clear();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(Locale.ROOT);
        if (name.equals("rtpreload")) {
            if (!sender.hasPermission("minenightrtp.admin")) {
                reply(sender, "no-permission");
                return true;
            }
            reloadConfig();
            upgradeLegacyWorldNames();
            reply(sender, "reloaded");
            return true;
        }
        if (!(sender instanceof Player player)) {
            reply(sender, "player-only");
            return true;
        }
        if (!player.hasPermission("minenightrtp.use")) {
            reply(player, "no-permission");
            return true;
        }
        if (name.equals("rtpgui")) {
            openGui(player);
            return true;
        }
        if (args.length == 0) {
            startTeleport(player, "plain");
            return true;
        }
        if (args.length == 1) {
            String selected = args[0].toLowerCase(Locale.ROOT);
            if (selected.equals("plains")) selected = "plain";
            if (BIOMES.contains(selected)) {
                startTeleport(player, selected);
                return true;
            }
        }
        reply(player, "rtp-usage");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!command.getName().equalsIgnoreCase("rtp") || args.length != 1 || !sender.hasPermission("minenightrtp.use")) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("plains", "snow", "desert", "mushroom", "badlands", "end")
                .stream().filter(b -> b.startsWith(prefix)).toList();
    }

    private void openGui(Player player) {
        MenuHolder holder = new MenuHolder();
        Inventory inventory = Bukkit.createInventory(holder, 27, format(getConfig().getString("gui.title", "<gradient:#4B63C4:#91B2FF>MineNight RTP</gradient>")));
        holder.inventory = inventory;
        if (getConfig().getBoolean("gui.fill-empty-slots", true)) {
            ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
            ItemMeta meta = pane.getItemMeta();
            meta.displayName(Component.empty());
            pane.setItemMeta(meta);
            for (int slot = 0; slot < 27; slot++) inventory.setItem(slot, pane);
        }
        for (String biome : BIOMES) {
            ConfigurationSection section = getConfig().getConfigurationSection("destinations." + biome);
            if (section == null) continue;
            int slot = section.getInt("slot", defaultSlot(biome));
            if (slot < 0 || slot >= 27) continue;
            Material material = Material.matchMaterial(section.getString("icon", defaultIcon(biome)));
            if (material == null || !material.isItem()) material = Material.GRASS_BLOCK;
            ItemStack item = new ItemStack(material);
            ItemMeta meta = item.getItemMeta();
            meta.displayName(format(section.getString("display-name", biome)));
            String worldName = section.getString("world", "");
            if (Bukkit.getWorld(worldName) == null) continue;
            List<Component> lore = new ArrayList<>();
            for (String line : getConfig().getStringList("gui.lore")) {
                lore.add(format(line.replace("{world}", worldName)));
            }
            meta.lore(lore);
            item.setItemMeta(meta);
            inventory.setItem(slot, item);
            holder.slots.put(slot, biome);
        }
        player.openInventory(inventory);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        String biome = holder.slots.get(event.getSlot());
        if (biome == null) return;
        player.closeInventory();
        startTeleport(player, biome);
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof MenuHolder) event.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        inProgress.remove(event.getPlayer().getUniqueId());
    }

    private void startTeleport(Player player, String biome) {
        if (de.minenight.tiertest.TierTestModule.locked(player)) { player.sendMessage("§cYou are in a tier test."); return; }
        if (!inProgress.add(player.getUniqueId())) {
            reply(player, "already-searching");
            return;
        }
        ConfigurationSection section = getConfig().getConfigurationSection("destinations." + biome);
        if (section == null || !section.getBoolean("enabled", true)) {
            finish(player, "destination-disabled");
            return;
        }
        String worldName = section.getString("world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            String generatedName = GENERATED_WORLD_NAMES.get(biome);
            World generatedWorld = generatedName == null ? null : Bukkit.getWorld(generatedName);
            if (generatedWorld != null) {
                world = generatedWorld;
                worldName = generatedName;
            }
        }
        getLogger().info("RTP request: player=" + player.getName() + ", biome=" + biome + ", world=" + worldName + ", loaded=" + (world != null));
        if (world == null) {
            finish(player, "world-not-found", "{world}", worldName);
            return;
        }
        if (world.getEnvironment() != World.Environment.NORMAL) {
            finish(player, "overworld-only");
            return;
        }
        reply(player, "searching");
        int attempts = Math.max(1, Math.min(100, getConfig().getInt("teleport.max-attempts", 48)));
        search(player, world, biome, attempts);
    }

    private void search(Player player, World world, String biome, int remaining) {
        if (!player.isOnline() || !inProgress.contains(player.getUniqueId())) return;
        if (de.minenight.tiertest.TierTestModule.locked(player)) { inProgress.remove(player.getUniqueId()); return; }
        if (remaining <= 0) {
            getLogger().warning("RTP found no safe location: player=" + player.getName() + ", biome=" + biome + ", world=" + world.getName());
            finish(player, "no-safe-location");
            return;
        }
        ConfigurationSection destination = getConfig().getConfigurationSection("destinations." + biome);
        if (destination == null) {
            finish(player, "destination-disabled");
            return;
        }
        int min = Math.max(0, destination.getInt("min-radius", getConfig().getInt("teleport.min-radius", 500)));
        int max = Math.max(min + 1, destination.getInt("max-radius", getConfig().getInt("teleport.max-radius", 5000)));
        int centerX = destination.getInt("center-x", world.getSpawnLocation().getBlockX());
        int centerZ = destination.getInt("center-z", world.getSpawnLocation().getBlockZ());
        double theta = ThreadLocalRandom.current().nextDouble(0.0, Math.PI * 2.0);
        double radius = Math.sqrt(ThreadLocalRandom.current().nextDouble((double) min * min, (double) max * max));
        int x = centerX + (int) Math.round(radius * Math.cos(theta));
        int z = centerZ + (int) Math.round(radius * Math.sin(theta));
        if (!world.getWorldBorder().isInside(new Location(world, x + 0.5, world.getSeaLevel(), z + 0.5))) {
            search(player, world, biome, remaining - 1);
            return;
        }
        try {
            world.getChunkAtAsync(x >> 4, z >> 4, getConfig().getBoolean("teleport.generate-chunks", true))
                .whenComplete((chunk, throwable) -> Bukkit.getScheduler().runTask(core(), () -> {
                    if (!player.isOnline() || !inProgress.contains(player.getUniqueId())) return;
                    if (de.minenight.tiertest.TierTestModule.locked(player)) { inProgress.remove(player.getUniqueId()); return; }
                    if (throwable != null || chunk == null) {
                        if (remaining == 1) {
                            if (throwable != null) {
                                getLogger().log(java.util.logging.Level.WARNING,
                                        "RTP could not load its final candidate chunk: player=" + player.getName() + ", world=" + world.getName() + ", chunk=" + (x >> 4) + "," + (z >> 4), throwable);
                            } else {
                                getLogger().warning("RTP returned no chunk for its final candidate: player=" + player.getName() + ", world=" + world.getName() + ", chunk=" + (x >> 4) + "," + (z >> 4));
                            }
                        }
                        search(player, world, biome, remaining - 1);
                        return;
                    }
                    int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
                    Location location = new Location(world, x + 0.5, y + 1.0, z + 0.5, player.getLocation().getYaw(), player.getLocation().getPitch());
                    if (!isSafe(location)) {
                        if (remaining == 1) {
                            getLogger().warning("RTP final candidate was unsafe: player=" + player.getName() + ", biome=" + biome + ", location=" + location);
                        }
                        search(player, world, biome, remaining - 1);
                        return;
                    }
                    player.teleportAsync(location, PlayerTeleportEvent.TeleportCause.COMMAND).whenComplete((success, error) -> Bukkit.getScheduler().runTask(core(), () -> {
                        if (!player.isOnline() || !inProgress.remove(player.getUniqueId())) return;
                        if (error == null && Boolean.TRUE.equals(success)) {
                            getLogger().info("RTP succeeded: player=" + player.getName() + ", biome=" + biome + ", location=" + location);
                            reply(player, "teleported", "{destination}", destination.getString("plain-name", biomeLabel(biome)));
                        } else {
                            getLogger().warning("RTP teleport failed: player=" + player.getName()
                                    + ", biome=" + biome
                                    + ", location=" + location
                                    + ", cause=COMMAND, error=" + (error == null ? "teleport rejected or cancelled" : error));
                            reply(player, "teleport-failed");
                        }
                    }));
                }));
        } catch (Exception exception) {
            getLogger().log(java.util.logging.Level.WARNING,
                    "RTP search failed: player=" + player.getName() + ", biome=" + biome + ", world=" + world.getName(), exception);
            finish(player, "teleport-failed");
        }
    }

    public record TierPair(Location first, Location second) {}

    public boolean isSearching(UUID player) { return inProgress.contains(player); }

    public java.util.concurrent.CompletableFuture<TierPair> findTierPair(String biome, int distance, java.util.function.Predicate<TierPair> available, java.util.function.BooleanSupplier valid) {
        var result = new java.util.concurrent.CompletableFuture<TierPair>();
        ConfigurationSection destination = getConfig().getConfigurationSection("destinations." + biome);
        if (destination == null || !destination.getBoolean("enabled", true)) {
            result.completeExceptionally(new IllegalStateException("RTP destination disabled"));
            return result;
        }
        World world = Bukkit.getWorld(destination.getString("world", ""));
        if (world == null) world = Bukkit.getWorld(GENERATED_WORLD_NAMES.getOrDefault(biome, ""));
        if (world == null || world.getEnvironment() != World.Environment.NORMAL) {
            result.completeExceptionally(new IllegalStateException("RTP world not loaded"));
            return result;
        }
        findTierPair(world, destination, Math.max(1, distance), Math.max(1, Math.min(100, getConfig().getInt("teleport.max-attempts", 48))), available, valid, result);
        return result;
    }

    private void findTierPair(World world, ConfigurationSection destination, int distance, int remaining, java.util.function.Predicate<TierPair> available, java.util.function.BooleanSupplier valid, java.util.concurrent.CompletableFuture<TierPair> result) {
        if (result.isDone()) return;
        if (!valid.getAsBoolean() || remaining <= 0) {
            result.completeExceptionally(new IllegalStateException("No safe tier-test location"));
            return;
        }
        int min = Math.max(0, destination.getInt("min-radius", getConfig().getInt("teleport.min-radius", 500)));
        int max = Math.max(min + 1, destination.getInt("max-radius", getConfig().getInt("teleport.max-radius", 5000)));
        var random = ThreadLocalRandom.current();
        double theta = random.nextDouble(Math.PI * 2);
        double radius = Math.sqrt(random.nextDouble((double) min * min, (double) max * max));
        int x = destination.getInt("center-x", world.getSpawnLocation().getBlockX()) + (int) Math.round(radius * Math.cos(theta));
        int z = destination.getInt("center-z", world.getSpawnLocation().getBlockZ()) + (int) Math.round(radius * Math.sin(theta));
        boolean east = random.nextBoolean();
        int bx = x + (east ? distance : 0), bz = z + (east ? 0 : distance);
        boolean generate = getConfig().getBoolean("teleport.generate-chunks", true);
        try {
            var a = world.getChunkAtAsync(x >> 4, z >> 4, generate);
            var b = world.getChunkAtAsync(bx >> 4, bz >> 4, generate);
            java.util.concurrent.CompletableFuture.allOf(a, b).whenComplete((ignored, error) -> {
                if (!core().isEnabled()) return;
                Bukkit.getScheduler().runTask(core(), () -> {
                    if (result.isDone() || !valid.getAsBoolean()) { result.cancel(false); return; }
                    if (error == null && a.getNow(null) != null && b.getNow(null) != null && world.isChunkLoaded(x >> 4, z >> 4) && world.isChunkLoaded(bx >> 4, bz >> 4)) {
                        Location first = new Location(world, x + .5, world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, z + .5);
                        Location second = new Location(world, bx + .5, world.getHighestBlockYAt(bx, bz, HeightMap.MOTION_BLOCKING_NO_LEAVES) + 1, bz + .5);
                        if (Math.abs(first.getY() - second.getY()) <= 3 && isSafe(first) && isSafe(second)
                                && first.getBlock().getType().isAir() && second.getBlock().getType().isAir()
                                && first.getBlock().getRelative(0,1,0).getType().isAir() && second.getBlock().getRelative(0,1,0).getType().isAir()
                                && first.getBlock().getBiome().equals(second.getBlock().getBiome())) {
                            first.setDirection(second.toVector().subtract(first.toVector()));
                            second.setDirection(first.toVector().subtract(second.toVector()));
                            TierPair pair = new TierPair(first, second);
                            if (available.test(pair)) { result.complete(pair); return; }
                        }
                    }
                    findTierPair(world, destination, distance, remaining - 1, available, valid, result);
                });
            });
        } catch (RuntimeException error) { result.completeExceptionally(error); }
    }

    private boolean isSafe(Location loc) {
        World world = loc.getWorld();
        if (world == null || loc.getBlockY() <= world.getMinHeight() || loc.getBlockY() + 1 >= world.getMaxHeight()) return false;
        Block feet = loc.getBlock();
        Block head = feet.getRelative(0, 1, 0);
        Block ground = feet.getRelative(0, -1, 0);
        Material groundType = ground.getType();
        if ((!groundType.isSolid() || !groundType.isOccluding()) && !(groundType == Material.SNOW && ground.getRelative(0, -1, 0).getType().isSolid())) return false;
        if (!feet.isPassable() || !head.isPassable()) return false;
        for (Material material : List.of(Material.MAGMA_BLOCK, Material.CACTUS, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.POWDER_SNOW)) {
            if (groundType == material || feet.getType() == material || head.getType() == material) return false;
        }
        if (world.getWorldBorder().isInside(loc) == false) return false;
        return true;
    }

    private void finish(Player player, String message, String... replacements) {
        inProgress.remove(player.getUniqueId());
        if (player.isOnline()) reply(player, message, replacements);
    }

    private void reply(CommandSender sender, String key, String... replacements) {
        String message = getConfig().getString("messages." + key, "<#6685E8>" + key);
        for (int i = 0; i + 1 < replacements.length; i += 2) message = message.replace(replacements[i], replacements[i + 1]);
        sender.sendMessage(format(message));
    }

    private Component format(String message) {
        return mm.deserialize(message == null ? "" : message);
    }

    private static int defaultSlot(String biome) {
        return switch (biome) {
            case "plain" -> 10;
            case "snow" -> 11;
            case "desert" -> 13;
            case "mushroom" -> 15;
            case "badlands" -> 16;
            default -> 22;
        };
    }

    private static String defaultIcon(String biome) {
        return switch (biome) {
            case "plain" -> "GRASS_BLOCK";
            case "snow" -> "SNOW_BLOCK";
            case "desert" -> "SAND";
            case "mushroom" -> "MYCELIUM";
            case "badlands" -> "RED_SAND";
            default -> "END_STONE";
        };
    }

    private static String biomeLabel(String biome) {
        return switch (biome) {
            case "plain" -> "Plains";
            case "snow" -> "Snow";
            case "desert" -> "Desert";
            case "mushroom" -> "Mushroom";
            case "badlands" -> "Badlands";
            default -> "The End";
        };
    }

    private static final class MenuHolder implements InventoryHolder {
        private Inventory inventory;
        private final java.util.Map<Integer, String> slots = new java.util.HashMap<>();

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}
