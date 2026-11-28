package de.minenight.allinone;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.util.Vector;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class KitModule extends MineNightModule implements Listener {
    private static final String PREFIX = "§8[§9MineNight§8] §7";
    private static final int[] KIT_CHEST_SLOTS = {9,10,11,12,13,14,15,16,17};
    private static final int[] KIT_ROOM_SLOTS = {18,19,20,21,22,23,24,25,26};
    private static final int[] DECOR_TOP = {0,1,2,3,4,5,6,7,8};
    private static final int[] DECOR_BOTTOM = {36,37,38,39,40,41,42,43,44};

    private final Map<UUID, Long> lastHit = new HashMap<>();
    private final Map<UUID, Integer> activeKits = new HashMap<>();
    private NamespacedKey shulkerKey;
    private File playerFile;
    private File roomsFile;
    private YamlConfiguration playerData;
    private YamlConfiguration rooms;
    private Set<Material> regearMaterials;

    private static final class Screen implements InventoryHolder {
        final String type;
        final int kit;
        final int page;
        final UUID owner;
        Inventory inventory;
        Screen(String type, int kit, int page, UUID owner) {
            this.type = type;
            this.kit = kit;
            this.page = page;
            this.owner = owner;
        }
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        shulkerKey = new NamespacedKey(core(), "regear_box");
        playerFile = new File(getDataFolder(), "players.yml");
        roomsFile = new File(getDataFolder(), "kitrooms.yml");
        playerData = YamlConfiguration.loadConfiguration(playerFile);
        rooms = YamlConfiguration.loadConfiguration(roomsFile);
        // Migrate the first legacy kitroom into the shared room without deleting older templates.
        if (!rooms.contains(roomPath(1))) {
            boolean migrated = false;
            for (int page = 1; page <= 3; page++) {
                String old = "rooms.1.pages." + page + ".inventory";
                if (rooms.contains(old)) {
                    rooms.set(roomPath(page), rooms.getList(old));
                    migrated = true;
                }
            }
            if (migrated) save(rooms, roomsFile);
        }
        regearMaterials = new HashSet<>();
        for (String raw : getConfig().getStringList("regear.whitelist")) {
            Material material = Material.matchMaterial(raw);
            if (material == null) {
                getLogger().warning("Unknown regear item: " + raw);
            } else {
                regearMaterials.add(material);
            }
        }
        for (String name : List.of("kit", "k1", "k2", "k3", "k4", "k5", "k6", "k7", "k8", "k9", "copykit", "copy", "regear", "kitroom", "staffkitroom")) {
            if (getCommand(name) != null) getCommand(name).setExecutor(this);
        }
        Bukkit.getPluginManager().registerEvents(this, core());
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (de.minenight.tiertest.TierTestModule.protectedInventory(player)) return;
        if (getConfig().getBoolean("death.compact-drops", true) && !event.getKeepInventory() && !event.getDrops().isEmpty()) {
            Location location = player.getLocation().clone();
            World world = location.getWorld();
            List<ItemStack> compact = new ArrayList<>();
            for (ItemStack original : event.getDrops()) {
                if (original == null || original.getType().isAir() || original.getAmount() <= 0) continue;
                ItemStack remaining = original.clone();
                for (ItemStack existing : compact) {
                    if (!existing.isSimilar(remaining)) continue;
                    int space = existing.getMaxStackSize() - existing.getAmount();
                    if (space <= 0) continue;
                    int moved = Math.min(space, remaining.getAmount());
                    existing.setAmount(existing.getAmount() + moved);
                    remaining.setAmount(remaining.getAmount() - moved);
                    if (remaining.getAmount() == 0) break;
                }
                while (remaining.getAmount() > 0) {
                    int amount = Math.min(remaining.getMaxStackSize(), remaining.getAmount());
                    ItemStack split = remaining.clone();
                    split.setAmount(amount);
                    compact.add(split);
                    remaining.setAmount(remaining.getAmount() - amount);
                }
            }
            event.getDrops().clear();
            Bukkit.getScheduler().runTask(core(), () -> {
                if (world == null) return;
                double spread = Math.max(0.0, Math.min(3.0, getConfig().getDouble("death.drop-spread-radius", 1.5)));
                java.util.concurrent.ThreadLocalRandom random = java.util.concurrent.ThreadLocalRandom.current();
                for (ItemStack stack : compact) {
                    double offsetX = random.nextDouble(-spread, spread);
                    double offsetZ = random.nextDouble(-spread, spread);
                    Location dropLocation = location.clone().add(offsetX, 0.15, offsetZ);
                    org.bukkit.entity.Item dropped = world.dropItem(dropLocation, stack);
                    dropped.setVelocity(new Vector(0.0, 0.0, 0.0));
                }
            });
        }
        if (getConfig().getBoolean("death.auto-respawn", true)) {
            UUID playerId = player.getUniqueId();
            Bukkit.getScheduler().runTaskLater(core(), () -> {
                Player current = Bukkit.getPlayer(playerId);
                if (current != null && current.isOnline() && current.isDead()) current.spigot().respawn();
            }, 2L);
        }
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.MONITOR)
    public void onPlayerRespawn(PlayerRespawnEvent event) {
        if (de.minenight.tiertest.TierTestModule.protectedInventory(event.getPlayer())) return;
        if (!getConfig().getBoolean("death.auto-equip-kit-1", true)) return;
        UUID playerId = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(core(), () -> {
            Player player = Bukkit.getPlayer(playerId);
            if (player == null || !player.isOnline() || player.isDead() || de.minenight.tiertest.TierTestModule.locked(player)) return;
            if (kitExists(playerId, 1)) loadKit(player, playerId, 1);
        }, 1L);
    }

    private String kitPath(UUID id, int number) { return "players." + id + ".kits." + number; }
    private String roomPath(int page) { return "shared.pages." + page + ".inventory"; }
    private String enderPath(UUID owner, int kit) { return "players." + owner + ".kitender." + kit; }


    private static List<ItemStack> copyItems(ItemStack[] items) {
        List<ItemStack> result = new ArrayList<>();
        for (ItemStack item : items) result.add(item == null ? null : item.clone());
        return result;
    }

    private static ItemStack[] readItems(List<?> stored, int size) {
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size && i < stored.size(); i++) {
            Object item = stored.get(i);
            if (item instanceof ItemStack stack) items[i] = stack.clone();
        }
        return items;
    }

    private void save(YamlConfiguration file, File path) {
        try {
            file.save(path);
        } catch (IOException e) {
            getLogger().severe("Could not save " + path + ": " + e.getMessage());
        }
    }

    private void recordKit(Player player, int number) {
        if (de.minenight.tiertest.TierTestModule.locked(player)&&!de.minenight.tiertest.TierTestModule.editingKit(player)) return;
        String path = kitPath(player.getUniqueId(), number);
        PlayerInventory inv = player.getInventory();
        playerData.set(path + ".storage", copyItems(inv.getStorageContents()));
        playerData.set(path + ".armor", copyItems(inv.getArmorContents()));
        ItemStack offhand = inv.getItemInOffHand();
        playerData.set(path + ".offhand", offhand == null ? null : offhand.clone());
        save(playerData, playerFile);
    }

    private boolean kitExists(UUID id, int number) {
        return playerData.contains(kitPath(id, number) + ".storage");
    }

    public java.util.Optional<de.minenight.tiertest.TierKitSnapshot> snapshotForTierTest(UUID owner, int number) {
        if (number < 1 || number > 9 || !kitExists(owner, number)) return java.util.Optional.empty();
        String path = kitPath(owner, number);
        return java.util.Optional.of(new de.minenight.tiertest.TierKitSnapshot(
                readItems(playerData.getList(path + ".storage", List.of()), 36),
                readItems(playerData.getList(path + ".armor", List.of()), 4),
                playerData.getItemStack(path + ".offhand"),
                readItems(playerData.getList(enderPath(owner, number), List.of()), 27)));
    }

    public ItemStack[] tierKitroomPage(int page) {
        return readItems(rooms.getList(roomPath(Math.max(1, Math.min(3,page))), List.of()),45);
    }

    public void saveTierKit(Player player, int number, de.minenight.tiertest.TierKitSnapshot kit) throws IOException {
        if (number < 1 || number > 9 || !de.minenight.tiertest.TierTestModule.canEditKit(player))
            throw new IllegalStateException("Kit editing is unavailable");
        String path=kitPath(player.getUniqueId(),number);
        String ender=enderPath(player.getUniqueId(),number);
        String previous=playerData.saveToString();
        playerData.set(path+".storage",copyItems(kit.storage()));
        playerData.set(path+".armor",copyItems(kit.armor()));
        playerData.set(path+".offhand",kit.offhand());
        playerData.set(ender,copyItems(kit.ender()));
        File tmp=new File(playerFile.getParentFile(),playerFile.getName()+".tier.tmp");
        try {
            playerData.save(tmp);
            try { java.nio.file.Files.move(tmp.toPath(),playerFile.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,java.nio.file.StandardCopyOption.ATOMIC_MOVE); }
            catch (java.nio.file.AtomicMoveNotSupportedException error) { java.nio.file.Files.move(tmp.toPath(),playerFile.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING); }
        } catch(IOException error) {
            try { playerData.loadFromString(previous); } catch(org.bukkit.configuration.InvalidConfigurationException ignored) {}
            throw error;
        }
    }

    private void loadKit(Player player, UUID owner, int number) {
        if (de.minenight.tiertest.TierTestModule.locked(player)&&!de.minenight.tiertest.TierTestModule.editingKit(player)) return;
        String path = kitPath(owner, number);
        if (!kitExists(owner, number)) {
            player.sendMessage(PREFIX + "Kit " + number + " is empty. Right-click the chest in /kit to save one.");
            return;
        }
        PlayerInventory inv = player.getInventory();
        inv.setStorageContents(readItems(playerData.getList(path + ".storage", List.of()), inv.getStorageContents().length));
        inv.setArmorContents(readItems(playerData.getList(path + ".armor", List.of()), inv.getArmorContents().length));
        ItemStack off = playerData.getItemStack(path + ".offhand");
        inv.setItemInOffHand(off == null ? new ItemStack(Material.AIR) : off.clone());
        activeKits.put(player.getUniqueId(), number);
        // Refill survival stats when a saved kit is equipped. Potion effects remain untouched.
        org.bukkit.attribute.AttributeInstance healthAttribute = player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
        if (healthAttribute != null) player.setHealth(healthAttribute.getValue());
        player.setFoodLevel(20);
        player.setSaturation(20.0f);
        player.updateInventory();
        if (getConfig().getBoolean("sounds.kit-loaded.enabled", true)) {
            String soundName = getConfig().getString("sounds.kit-loaded.sound", "ENTITY_PLAYER_LEVELUP");
            try {
                Sound sound = Sound.valueOf(soundName);
                player.playSound(player.getLocation(), sound,
                        (float) getConfig().getDouble("sounds.kit-loaded.volume", 0.7),
                        (float) getConfig().getDouble("sounds.kit-loaded.pitch", 1.7));
            } catch (IllegalArgumentException exception) {
                getLogger().warning("Invalid kit load sound configured: " + soundName);
            }
        }
        player.sendMessage(PREFIX + "Loaded kit " + number + ".");
    }

    private ItemStack icon(Material type, String title, String... description) {
        ItemStack stack = new ItemStack(type);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName("§9" + title);
        if (description.length > 0) meta.setLore(Arrays.asList(description));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack named(ItemStack stack, String title, String... description) {
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName("§9" + title);
        if (description.length > 0) meta.setLore(Arrays.asList(description));
        stack.setItemMeta(meta);
        return stack;
    }

    private ItemStack pane() {
        ItemStack stack = new ItemStack(Material.BLUE_STAINED_GLASS_PANE);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(" ");
        stack.setItemMeta(meta);
        return stack;
    }

    private Inventory screen(String type, Player owner, int number, int page, int size, String title) {
        Screen holder = new Screen(type, number, page, owner.getUniqueId());
        holder.inventory = Bukkit.createInventory(holder, size, title);
        return holder.inventory;
    }

    public void openTierKitMenu(Player player){
        if(de.minenight.tiertest.TierTestModule.editingKit(player))openMain(player);
    }
    public boolean isTierEditorInventory(Player player,Inventory inventory){
        if(!de.minenight.tiertest.TierTestModule.editingKit(player))return false;
        if(inventory.getType()==org.bukkit.event.inventory.InventoryType.CRAFTING)return true;
        return inventory.getHolder() instanceof Screen screen&&screen.owner.equals(player.getUniqueId())&&Set.of("main","preview","room","kitender").contains(screen.type);
    }

    private void openMain(Player player) {
        Inventory inv = screen("main", player, 0, 1, 45, "§9MineNight §8» §bKits");
        for (int slot : DECOR_TOP) inv.setItem(slot, pane());
        for (int slot : DECOR_BOTTOM) inv.setItem(slot, pane());
        inv.setItem(27, pane());
        inv.setItem(31, pane());
        inv.setItem(35, pane());

        for (int n = 1; n <= 9; n++) {
            Material chestMaterial = kitExists(player.getUniqueId(), n) ? Material.CHEST : Material.TRAPPED_CHEST;
            inv.setItem(KIT_CHEST_SLOTS[n - 1], icon(chestMaterial, "Kit " + n,
                    "§7Left-click: load kit",
                    "§7Right-click: view and edit kit",
                    "§8/kit" + n + " also works"));
            inv.setItem(KIT_ROOM_SLOTS[n - 1], icon(Material.ENDER_CHEST, "Kit " + n + " Ender Chest",
                    "§7Open your private kit storage",
                    "§7Independent for each kit"));
        }

        inv.setItem(28, icon(Material.WRITABLE_BOOK, "Save Guide",
                "§7Right-click a kit chest to preview it",
                "§7Then select Save Inventory to Kit"));
        inv.setItem(29, icon(Material.BOOK, "Copy Guide",
                "§7Use §f/copy kit <player> <1-9>",
                "§7to copy another player's kit"));
        inv.setItem(30, icon(Material.ANVIL, "Repair Inventory",
                "§7Repairs all carried and worn items"));
        inv.setItem(32, icon(Material.BLUE_SHULKER_BOX, "Regear Shulker",
                "§7Use §f/regear §7to get one",
                "§7Single-use and disappears after opening"));
        inv.setItem(33, icon(Material.ENDER_CHEST, "Shared Kitroom",
                "§7Open the shared item selection",
                "§7Same supplies for every player",
                "§7Use /kitroom"));
        inv.setItem(34, de.minenight.tiertest.TierTestModule.editingKit(player)?icon(Material.ARROW,"Back to Tier Test","§7Restore your normal inventory"):icon(Material.BARRIER, "Close", "§7Close this menu"));
        if(de.minenight.tiertest.TierTestModule.editingKit(player))inv.setItem(32,icon(Material.BUCKET,"Clear editing inventory","§7Clear temporary items"));
        player.openInventory(inv);
    }

    private void openPreview(Player player, int kit) {
        Inventory inv = screen("preview", player, kit, 1, 54, "§9Kit " + kit + " §8» §bPreview");
        for (int slot = 36; slot < 54; slot++) inv.setItem(slot, pane());
        if (kitExists(player.getUniqueId(), kit)) {
            String path = kitPath(player.getUniqueId(), kit);
            ItemStack[] storage = readItems(playerData.getList(path + ".storage", List.of()), 36);
            // Display hotbar above the preview inventory (0..8), then the other 27 slots.
            for (int i = 0; i < 36; i++) inv.setItem(i, storage[i]);
            ItemStack[] armor = readItems(playerData.getList(path + ".armor", List.of()), 4);
            for (int i = 0; i < 4; i++) inv.setItem(37 + i, armor[i]);
            inv.setItem(42, playerData.getItemStack(path + ".offhand"));
        }
        inv.setItem(36, icon(Material.ARMOR_STAND, "Armor", "§7Boots, leggings, chestplate, helmet: 38-41"));
        inv.setItem(43, icon(Material.SHIELD, "Offhand", "§7Preview at slot 43"));
        inv.setItem(49, icon(Material.LIME_DYE, "Save Inventory to Kit",
                "§7Save your current inventory, hotbar, armor and offhand",
                "§7Replaces saved Kit " + kit));
        inv.setItem(45, icon(Material.ARROW, "Back to Kits"));
        inv.setItem(53, icon(Material.BARRIER, "Close"));
        player.openInventory(inv);
    }

    private void openKitEnder(Player player, int kit) {
        Inventory inv = screen("kitender", player, kit, 1, 27, "§9Kit " + kit + " §8» §bEnder Chest");
        ItemStack[] contents = readItems(playerData.getList(enderPath(player.getUniqueId(), kit), List.of()), 27);
        inv.setContents(contents);
        player.openInventory(inv);
    }

    private void saveKitEnder(Screen screen, Inventory inventory) {
        playerData.set(enderPath(screen.owner, screen.kit), copyItems(inventory.getContents()));
        save(playerData, playerFile);
    }

    private void openRoom(Player player, int page, boolean staff) {
        int safePage = Math.max(1, Math.min(3, page));
        Inventory inv = screen(staff ? "staffroom" : "room", player, 0, safePage, 54,
                (staff ? "§9Staff Kitroom" : "§9Shared Kitroom") + " §8» §bPage " + safePage + "/3");
        ItemStack[] saved = readItems(rooms.getList(roomPath(safePage), List.of()), 45);
        for (int i = 0; i < 45; i++) inv.setItem(i, saved[i]);
        for (int i = 45; i < 54; i++) inv.setItem(i, pane());
        inv.setItem(45, icon(Material.ARROW, "Previous Page"));
        inv.setItem(49, icon(staff ? Material.LIME_DYE : Material.EMERALD,
                staff ? "Save Page" : "Refill", staff ? "§7Save the template for everybody" : "§7Restore this page"));
        inv.setItem(53, icon(Material.ARROW, "Next Page"));
        inv.setItem(48, icon(Material.ENDER_CHEST, "Shared Kitroom", "§7Page " + safePage + "/3"));
        inv.setItem(50, icon(Material.BARRIER, "Close"));
        player.openInventory(inv);
    }

    private void saveStaffRoomPage(int page, Inventory inventory) {
        List<ItemStack> saved = new ArrayList<>();
        for (int i = 0; i < 45; i++) {
            ItemStack item = inventory.getItem(i);
            saved.add(item == null ? null : item.clone());
        }
        rooms.set(roomPath(page), saved);
        save(rooms, roomsFile);
    }

    private void refillRoom(Inventory inv, int page) {
        ItemStack[] contents = readItems(rooms.getList(roomPath(page), List.of()), 45);
        for (int i = 0; i < 45; i++) inv.setItem(i, contents[i]);
    }

    private void repair(Player player) {
        PlayerInventory inv = player.getInventory();
        int total = 0;
        for (ItemStack stack : inv.getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            if (stack.getItemMeta() instanceof Damageable damage && damage.hasDamage()) {
                damage.setDamage(0);
                stack.setItemMeta(damage);
                total++;
            }
        }
        player.sendMessage(PREFIX + "Repaired " + total + " item(s).");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (de.minenight.tiertest.TierTestModule.locked(player)) {
            if (command.getName().equalsIgnoreCase("regear") && core().module("tiertest") instanceof de.minenight.tiertest.TierTestModule tier) return tier.regear(player);
            String requested=command.getName().toLowerCase(java.util.Locale.ROOT);
            if(!de.minenight.tiertest.TierTestModule.editingKit(player)||!(Set.of("kit","kitroom","copy","copykit").contains(requested)||requested.matches("k[1-9]"))){player.sendMessage("§cKits are locked during a tier test."); return true;}
        }
        String name = command.getName().toLowerCase();
        if (name.matches("k[1-9]")) {
            loadKit(player, player.getUniqueId(), Integer.parseInt(name.substring(1)));
            return true;
        }
        switch (name) {
            case "kit" -> {
                if (args.length == 1 && args[0].matches("[1-9]")) loadKit(player, player.getUniqueId(), Integer.parseInt(args[0]));
                else openMain(player);
            }
            case "copykit", "copy" -> {
                String target;
                String number;
                if (name.equals("copy")) {
                    if (args.length != 3 || !args[0].equalsIgnoreCase("kit")) {
                        player.sendMessage(PREFIX + "Usage: /copy kit <player> <1-9>");
                        break;
                    }
                    target = args[1];
                    number = args[2];
                } else {
                    if (args.length != 2) {
                        player.sendMessage(PREFIX + "Usage: /copykit <player> <1-9>");
                        break;
                    }
                    target = args[0];
                    number = args[1];
                }
                if (!number.matches("[1-9]")) {
                    player.sendMessage(PREFIX + "Kit number must be 1-9.");
                    break;
                }
                @SuppressWarnings("deprecation") org.bukkit.OfflinePlayer other = Bukkit.getOfflinePlayer(target);
                if (!other.hasPlayedBefore() && !other.isOnline()) {
                    player.sendMessage(PREFIX + "Unknown player.");
                    break;
                }
                loadKit(player, other.getUniqueId(), Integer.parseInt(number));
            }
            case "regear" -> {
                player.getInventory().addItem(regearBox());
                player.sendMessage(PREFIX + "Regear shulker added. You can place it after 10 seconds without taking damage.");
            }
            case "kitroom" -> openRoom(player, 1, false);
            case "staffkitroom" -> {
                if (!player.hasPermission("minenight.kits.staff")) {
                    player.sendMessage(PREFIX + "No permission.");
                    break;
                }
                openRoom(player, 1, true);
            }
        }
        return true;
    }

    private ItemStack regearBox() {
        ItemStack box = icon(Material.PURPLE_SHULKER_BOX, "Regear Shulker",
                "§7Place it after combat delay",
                "§7Single-use: disappears after opening");
        ItemMeta meta = box.getItemMeta();
        meta.getPersistentDataContainer().set(shulkerKey, PersistentDataType.BYTE, (byte) 1);
        box.setItemMeta(meta);
        return box;
    }

    @EventHandler public void onDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            lastHit.put(player.getUniqueId(), System.currentTimeMillis());
        }
    }

    @EventHandler public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (item.getType() != Material.PURPLE_SHULKER_BOX || !item.hasItemMeta() ||
                !item.getItemMeta().getPersistentDataContainer().has(shulkerKey, PersistentDataType.BYTE)) return;
        long elapsed = System.currentTimeMillis() - lastHit.getOrDefault(event.getPlayer().getUniqueId(), 0L);
        long delayMillis = getConfig().getLong("regear.combat-delay-seconds", 10) * 1000L;
        if (elapsed < delayMillis) {
            event.setCancelled(true);
            event.getPlayer().sendMessage(PREFIX + "Wait " + ((delayMillis - elapsed + 999) / 1000) + " seconds after taking damage.");
            return;
        }
        Bukkit.getScheduler().runTask(core(), () -> {
            if (event.getBlockPlaced().getState() instanceof org.bukkit.block.ShulkerBox box) {
                box.getPersistentDataContainer().set(shulkerKey, PersistentDataType.BYTE, (byte) 1);
                box.update(true, false);
            }
        });
    }

    @EventHandler public void onInteract(PlayerInteractEvent event) {
        if (de.minenight.tiertest.TierTestModule.locked(event.getPlayer())) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK || event.getClickedBlock() == null) return;
        if (!(event.getClickedBlock().getState() instanceof org.bukkit.block.ShulkerBox box)) return;
        if (!box.getPersistentDataContainer().has(shulkerKey, PersistentDataType.BYTE)) return;
        event.setCancelled(true);
        Player player = event.getPlayer();
        int kit = activeKits.getOrDefault(player.getUniqueId(), 1);
        event.getClickedBlock().setType(Material.AIR, false);
        Inventory inv = screen("regear", player, kit, 1, 27, "§9Regear");
        for (int i = 0; i < 27; i++) if (i != 13) inv.setItem(i, pane());
        inv.setItem(13, icon(Material.LIME_DYE, "Regear Now",
                "§7Restock whitelisted kit items",
                "§7This shulker is consumed"));
        player.openInventory(inv);
    }

    @EventHandler public void onProtectBox(BlockBreakEvent event) {
        if (event.getBlock().getState() instanceof org.bukkit.block.ShulkerBox box &&
                box.getPersistentDataContainer().has(shulkerKey, PersistentDataType.BYTE)) {
            event.setDropItems(false);
            event.getBlock().setType(Material.AIR, false);
        }
    }

    private static boolean matchingRegearItem(ItemStack current, ItemStack template) {
        if (current == null || current.getType().isAir() || template == null || template.getType().isAir()) return false;
        if (current.getType() != template.getType()) return false;
        if (template.getMaxStackSize() == 1) return true;
        return current.isSimilar(template);
    }

    private static void place(ItemStack[] storage, ItemStack[] offhand, int slot, ItemStack value) {
        if (slot == 36) offhand[0] = value;
        else storage[slot] = value;
    }

    private static ItemStack at(ItemStack[] storage, ItemStack[] offhand, int slot) {
        return slot == 36 ? offhand[0] : storage[slot];
    }

    private static int ownedAmount(ItemStack[] storage, ItemStack[] offhand, ItemStack[] armor, ItemStack target) {
        int owned = 0;
        for (ItemStack item : storage) if (matchingRegearItem(item, target)) owned += item.getAmount();
        if (matchingRegearItem(offhand[0], target)) owned += offhand[0].getAmount();
        for (ItemStack item : armor) if (matchingRegearItem(item, target)) owned += item.getAmount();
        return owned;
    }

    private static int targetAmount(ItemStack[] desired, ItemStack target) {
        int count = 0;
        for (ItemStack item : desired) if (matchingRegearItem(item, target)) count += item.getAmount();
        return count;
    }

    private void refillPlayer(Player player, int kit) {
        if (de.minenight.tiertest.TierTestModule.locked(player)) return;
        String path = kitPath(player.getUniqueId(), kit);
        if (!kitExists(player.getUniqueId(), kit)) {
            player.sendMessage(PREFIX + "Save kit " + kit + " first.");
            return;
        }
        refillFromSnapshot(player, readItems(playerData.getList(path + ".storage", List.of()), 36), playerData.getItemStack(path + ".offhand"), List.of());
    }

    public long regearDelayMillis() { return Math.max(0, getConfig().getLong("regear.combat-delay-seconds",10)) * 1000L; }
    public long regearWaitMillis(Player player) {
        return Math.max(0,regearDelayMillis()-(System.currentTimeMillis()-lastHit.getOrDefault(player.getUniqueId(),0L)));
    }
    private static boolean tierRefillable(ItemStack item){
        if(item==null||item.getType().isAir())return false;
        Material material=item.getType();
        String name=material.name();
        return !Set.of(Material.TOTEM_OF_UNDYING,Material.ELYTRA,Material.FIREWORK_ROCKET,Material.TURTLE_HELMET).contains(material)
                &&!name.endsWith("_HELMET")&&!name.endsWith("_CHESTPLATE")&&!name.endsWith("_LEGGINGS")&&!name.endsWith("_BOOTS")
                &&!name.endsWith("_HORSE_ARMOR")&&!name.endsWith("_NAUTILUS_ARMOR")&&material!=Material.WOLF_ARMOR;
    }
    private static boolean isShulker(ItemStack item){return item!=null&&item.getType().name().endsWith("SHULKER_BOX");}
    private static void flatten(ItemStack item,List<ItemStack> list){
        if(item==null||item.getType().isAir())return;
        if(isShulker(item)&&item.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta meta&&meta.getBlockState() instanceof org.bukkit.block.ShulkerBox box){
            list.add(new ItemStack(item.getType()));
            for(ItemStack nested:box.getSnapshotInventory().getContents())flatten(nested,list);
        }else list.add(item);
    }
    public void refillTierPlayer(Player player,de.minenight.tiertest.TierKitSnapshot kit,List<ItemStack> external){
        if(!(core().module("tiertest") instanceof de.minenight.tiertest.TierTestModule tier)||!tier.isActive(player))return;
        PlayerInventory inv=player.getInventory();
        ItemStack[] storage=inv.getStorageContents(),armor=inv.getArmorContents(),offhand={inv.getItemInOffHand()},ender=player.getEnderChest().getContents();
        ItemStack[][] actual={storage,armor,offhand,ender},desired={kit.storage(),kit.armor(),new ItemStack[]{kit.offhand()},kit.ender()};
        List<ItemStack> targets=new ArrayList<>(),owned=new ArrayList<>();
        for(ItemStack[] array:desired)for(ItemStack item:array)flatten(item,targets);
        for(ItemStack[] array:actual)for(ItemStack item:array)flatten(item,owned);
        for(ItemStack item:external)flatten(item,owned);
        int added=0;
        for(int section=0;section<actual.length;section++)for(int slot=0;slot<actual[section].length;slot++){
            ItemStack wanted=desired[section][slot],current=actual[section][slot];
            if(!tierRefillable(wanted))continue;
            if(isShulker(wanted)){
                if(current==null||current.getType().isAir()){
                    int targetBoxes=0,ownedBoxes=0;
                    for(ItemStack item:targets)if(item.getType()==wanted.getType())targetBoxes+=item.getAmount();
                    for(ItemStack item:owned)if(item.getType()==wanted.getType())ownedBoxes+=item.getAmount();
                    if(ownedBoxes>=targetBoxes)continue;
                    current=new ItemStack(wanted.getType());
                    actual[section][slot]=current;owned.add(current.clone());added++;
                }
                if(current.getType()!=wanted.getType()||!(current.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta currentMeta)||!(wanted.getItemMeta() instanceof org.bukkit.inventory.meta.BlockStateMeta wantedMeta)||!(currentMeta.getBlockState() instanceof org.bukkit.block.ShulkerBox box)||!(wantedMeta.getBlockState() instanceof org.bukkit.block.ShulkerBox template))continue;
                ItemStack[] contents=box.getSnapshotInventory().getContents(),goal=template.getSnapshotInventory().getContents();
                for(int i=0;i<contents.length;i++){ItemStack previous=contents[i];contents[i]=refillTierSlot(previous,goal[i],targets,owned);added+=amount(contents[i])-amount(previous);}
                box.getSnapshotInventory().setContents(contents);currentMeta.setBlockState(box);current.setItemMeta(currentMeta);
            }else{
                ItemStack updated=refillTierSlot(current,wanted,targets,owned);
                added+=amount(updated)-amount(current);actual[section][slot]=updated;
            }
        }
        // Top up matching stacks or free inventory slots when the original slot is occupied.
        for(ItemStack wanted:targets){
            if(!tierRefillable(wanted)||isShulker(wanted))continue;
            for(int slot=0;slot<storage.length;slot++){
                ItemStack current=storage[slot];
                if(amount(current)==0||!tierMatches(current,wanted))continue;
                ItemStack goal=wanted.clone();goal.setAmount(wanted.getMaxStackSize());
                ItemStack result=refillTierSlot(current,goal,targets,owned);
                added+=amount(result)-amount(current);storage[slot]=result;
            }
            for(int slot=0;slot<storage.length;slot++){
                if(amount(storage[slot])!=0)continue;
                ItemStack goal=wanted.clone();goal.setAmount(wanted.getMaxStackSize());
                ItemStack result=refillTierSlot(null,goal,targets,owned);
                if(result==null)break;
                added+=amount(result);storage[slot]=result;
            }
        }
        inv.setStorageContents(storage);inv.setArmorContents(armor);inv.setItemInOffHand(offhand[0]==null?new ItemStack(Material.AIR):offhand[0]);player.getEnderChest().setContents(ender);
        player.updateInventory();player.sendMessage(PREFIX+"Regeared "+added+" item(s). Totems, Elytra, Rockets and armor are excluded.");
    }
    private static int amount(ItemStack item){return item==null||item.getType().isAir()?0:item.getAmount();}
    private static boolean tierMatches(ItemStack a,ItemStack b){
        if(a==null||b==null||a.getType()!=b.getType()||a.getType().isAir())return false;
        if(a.isSimilar(b))return true;
        if(a.getType().getMaxDurability()==0)return false;
        ItemStack first=a.clone(),second=b.clone();
        if(first.getItemMeta() instanceof Damageable x&&second.getItemMeta() instanceof Damageable y){
            x.setDamage(0);y.setDamage(0);first.setItemMeta(x);second.setItemMeta(y);
        }
        return first.isSimilar(second);
    }
    private static ItemStack refillTierSlot(ItemStack current,ItemStack wanted,List<ItemStack> targets,List<ItemStack> owned){
        if(!tierRefillable(wanted)||isShulker(wanted)||amount(current)>0&&!tierMatches(current,wanted))return current;
        int target=0,have=0;
        for(ItemStack item:targets)if(tierMatches(item,wanted))target+=item.getAmount();
        for(ItemStack item:owned)if(tierMatches(item,wanted))have+=item.getAmount();
        int add=Math.min(target-have,Math.min(wanted.getAmount()-amount(current),wanted.getMaxStackSize()-amount(current)));
        if(add<=0)return current;
        ItemStack fresh=wanted.clone();fresh.setAmount(add);owned.add(fresh.clone());
        ItemStack result=amount(current)>0?current.clone():wanted.clone();result.setAmount(amount(current)+add);return result;
    }
    private void refillFromSnapshot(Player player, ItemStack[] desiredStorage, ItemStack desiredOffhand, List<ItemStack> external) {
        PlayerInventory inv = player.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        ItemStack[] armor = inv.getArmorContents();
        ItemStack[] offhand = {inv.getItemInOffHand()};
        ItemStack[] desired = Arrays.copyOf(desiredStorage, storage.length + 1);
        desired[storage.length] = desiredOffhand;
        int moved = 0;
        int created = 0;
        int blocked = 0;

        for (int slot = 0; slot < desired.length; slot++) {
            ItemStack wanted = desired[slot];
            if (wanted == null || !regearMaterials.contains(wanted.getType())) continue;
            ItemStack current = at(storage, offhand, slot);
            if (current != null && !current.getType().isAir()) continue;
            for (int donor = 0; donor < desired.length; donor++) {
                if (donor == slot) continue;
                ItemStack candidate = at(storage, offhand, donor);
                if (!matchingRegearItem(candidate, wanted)) continue;
                ItemStack donorTarget = desired[donor];
                int reserved = matchingRegearItem(donorTarget, candidate) ? donorTarget.getAmount() : 0;
                int available = candidate.getAmount() - reserved;
                if (available <= 0) continue;
                int taken = Math.min(available, wanted.getAmount());
                ItemStack relocated = candidate.clone();
                relocated.setAmount(taken);
                place(storage, offhand, slot, relocated);
                if (candidate.getAmount() == taken) place(storage, offhand, donor, null);
                else {
                    candidate.setAmount(candidate.getAmount() - taken);
                    place(storage, offhand, donor, candidate);
                }
                moved += taken;
                break;
            }
        }

        for (int slot = 0; slot < desired.length; slot++) {
            ItemStack wanted = desired[slot];
            if (wanted == null || !regearMaterials.contains(wanted.getType())) continue;
            ItemStack current = at(storage, offhand, slot);
            if (current != null && !current.getType().isAir() && !matchingRegearItem(current, wanted)) {
                blocked++;
                continue;
            }
            int outside=0;
            for(ItemStack item:external)if(matchingRegearItem(item,wanted))outside+=item.getAmount();
            int shortage = targetAmount(desired, wanted) - ownedAmount(storage, offhand, armor, wanted) - outside;
            if (shortage <= 0) continue;
            int slotMissing = current == null || current.getType().isAir()
                    ? wanted.getAmount() : Math.max(0, wanted.getAmount() - current.getAmount());
            if (slotMissing <= 0) continue;
            int room = current == null || current.getType().isAir()
                    ? wanted.getMaxStackSize() : wanted.getMaxStackSize() - current.getAmount();
            int toCreate = Math.min(Math.min(shortage, slotMissing), room);
            if (toCreate <= 0) continue;
            if (current == null || current.getType().isAir()) {
                ItemStack fresh = wanted.clone();
                fresh.setAmount(toCreate);
                place(storage, offhand, slot, fresh);
            } else {
                current.setAmount(current.getAmount() + toCreate);
                place(storage, offhand, slot, current);
            }
            created += toCreate;
        }

        inv.setStorageContents(storage);
        inv.setItemInOffHand(offhand[0] == null ? new ItemStack(Material.AIR) : offhand[0]);
        player.updateInventory();
        player.sendMessage(PREFIX + "Regeared " + created + " item(s) into saved slots" +
                (moved > 0 ? ", moved " + moved + " item(s)" : "") +
                (blocked > 0 ? ". " + blocked + " saved slots are occupied." : "."));
    }

    @EventHandler public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof Screen screen)) return;
        int slot = event.getRawSlot();
        if (screen.type.equals("main") || screen.type.equals("preview")) {
            if (slot < 0 || slot >= top.getSize()) {
                if (slot >= top.getSize() && (event.isShiftClick()
                        || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY
                        || event.getAction() == InventoryAction.COLLECT_TO_CURSOR)) {
                    event.setCancelled(true);
                }
                return;
            }
        }
        switch (screen.type) {
            case "main" -> {
                event.setCancelled(true);
                if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
                if (slot == 30) {
                    repair(player);
                    openMain(player);
                    return;
                }
                if (slot == 33) {
                    openRoom(player, 1, false);
                    return;
                }
                if (slot == 32 && de.minenight.tiertest.TierTestModule.editingKit(player)) {player.getInventory().clear();player.updateInventory();return;}
                if (slot == 34) {
                    if(de.minenight.tiertest.TierTestModule.editingKit(player)&&core().module("tiertest") instanceof de.minenight.tiertest.TierTestModule tier){Bukkit.getScheduler().runTask(core(),()->tier.endKitEdit(player));return;}
                    player.closeInventory();
                    return;
                }
                for (int i = 0; i < 9; i++) {
                    if (slot == KIT_CHEST_SLOTS[i]) {
                        int kit = i + 1;
                        if (event.getClick() == ClickType.RIGHT) {
                            openPreview(player, kit);
                        } else if (event.getClick() == ClickType.LEFT) {
                            loadKit(player, player.getUniqueId(), kit);
                            player.closeInventory();
                        }
                        return;
                    }
                    if (slot == KIT_ROOM_SLOTS[i]) {
                        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
                            openKitEnder(player, i + 1);
                        }
                        return;
                    }
                }
            }
            case "preview" -> {
                event.setCancelled(true);
                if (event.getClick() != ClickType.LEFT && event.getClick() != ClickType.RIGHT) return;
                if (slot == 49) {
                    recordKit(player, screen.kit);
                    activeKits.put(player.getUniqueId(), screen.kit);
                    player.playSound(player.getLocation(), Sound.BLOCK_CHEST_CLOSE, 1f, 1.2f);
                    player.sendMessage(PREFIX + "Saved inventory to Kit " + screen.kit + ".");
                    openPreview(player, screen.kit);
                } else if (slot == 45) {
                    openMain(player);
                } else if (slot == 53) {
                    player.closeInventory();
                }
            }
            case "kitender" -> {
                // Vanilla chest interactions are supported; persist all contents on close.
            }
            case "room" -> {
                // The shared kitroom is a read-only item source in the upper inventory.
                // Players may freely rearrange their own inventory in the lower section.
                if (slot >= 0 && slot < top.getSize()) {
                    event.setCancelled(true);

                    if (slot < 45) {
                        ItemStack current = top.getItem(slot);
                        if (current == null || current.getType().isAir()) return;
                        if (event.getClick() == ClickType.NUMBER_KEY) {
                            int targetSlot = event.getHotbarButton();
                            if (targetSlot < 0 || targetSlot > 8) return;
                            PlayerInventory inventory = player.getInventory();
                            ItemStack replaced = inventory.getItem(targetSlot);
                            if (replaced != null && !replaced.getType().isAir()) {
                                int freeSlot = inventory.firstEmpty();
                                if (freeSlot == -1) {
                                    player.sendMessage(PREFIX + "Not enough inventory space to move the existing hotbar item.");
                                    return;
                                }
                                inventory.setItem(freeSlot, replaced.clone());
                            }
                            inventory.setItem(targetSlot, current.clone());
                            player.updateInventory();
                            return;
                        }
                        if (event.getClick() == ClickType.LEFT || event.getClick() == ClickType.RIGHT) {
                            Map<Integer, ItemStack> extra = player.getInventory().addItem(current.clone());
                            if (!extra.isEmpty()) player.sendMessage(PREFIX + "Not enough inventory space.");
                        }
                        return;
                    }

                    if (slot == 45 && screen.page > 1) {
                        openRoom(player, screen.page - 1, false);
                    } else if (slot == 49) {
                        refillRoom(top, screen.page);
                        player.sendMessage(PREFIX + "Kitroom refilled.");
                    } else if (slot == 50) {
                        player.closeInventory();
                    } else if (slot == 53 && screen.page < 3) {
                        openRoom(player, screen.page + 1, false);
                    }
                    return;
                }

                // Shift-click would insert player items into the shared template inventory.
                // Collect-to-cursor could also pull template items into the cursor, so block it.
                if (event.isShiftClick() || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                        || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
                    event.setCancelled(true);
                }
            }
            case "staffroom" -> {
                // Do not let shift-click from the player's inventory overwrite navigation buttons.
                if (slot >= top.getSize() && event.isShiftClick()) event.setCancelled(true);
                if (slot >= 45 && slot < 54) event.setCancelled(true);
                if (slot == 45 && screen.page > 1) {
                    saveStaffRoomPage(screen.page, top);
                    openRoom(player, screen.page - 1, true);
                } else if (slot == 49) {
                    saveStaffRoomPage(screen.page, top);
                    refillRoom(top, screen.page);
                    player.sendMessage(PREFIX + "Saved and refilled kitroom page " + screen.page + ".");
                } else if (slot == 50) {
                    saveStaffRoomPage(screen.page, top);
                    player.closeInventory();
                } else if (slot == 53 && screen.page < 3) {
                    saveStaffRoomPage(screen.page, top);
                    openRoom(player, screen.page + 1, true);
                }
            }
            case "regear" -> {
                event.setCancelled(true);
                if (slot == 13) {
                    refillPlayer(player, screen.kit);
                    player.closeInventory();
                }
            }
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Screen screen)) return;
        if ((screen.type.equals("room") || screen.type.equals("regear") || screen.type.equals("main") || screen.type.equals("preview")) &&
                event.getRawSlots().stream().anyMatch(i -> i < event.getView().getTopInventory().getSize())) {
            event.setCancelled(true);
            return;
        }
        if (screen.type.equals("staffroom") && event.getRawSlots().stream().anyMatch(i -> i >= 45 && i < 54)) {
            event.setCancelled(true);
        }
    }

    @EventHandler public void onClose(InventoryCloseEvent event) {
        if (!(event.getInventory().getHolder() instanceof Screen screen)) return;
        if (screen.type.equals("staffroom")) {
            saveStaffRoomPage(screen.page, event.getInventory());
        } else if (screen.type.equals("kitender")) {
            saveKitEnder(screen, event.getInventory());
        }
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("copykit")) {
            if (args.length == 1) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            if (args.length == 2) return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
        }
        if (command.getName().equalsIgnoreCase("copy")) {
            if (args.length == 1) return List.of("kit");
            if (args.length == 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            if (args.length == 3) return List.of("1", "2", "3", "4", "5", "6", "7", "8", "9");
        }
        return List.of();
    }
}
