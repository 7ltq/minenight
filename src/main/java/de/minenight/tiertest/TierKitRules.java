package de.minenight.tiertest;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.block.ShulkerBox;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.BlockStateMeta;
import org.bukkit.inventory.meta.BundleMeta;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;

public final class TierKitRules {
    public enum Type {
        BELOW("below"), ABOVE("above");
        private final String key;
        Type(String key) { this.key = key; }
        public String key() { return key; }
    }

    public record Result(TierKitSnapshot kit, List<String> changes) {
        public Result { changes = List.copyOf(changes); }
    }

    private static final Set<Material> DEFAULT_BANNED = Set.of(
            Material.BEACON, Material.MILK_BUCKET, Material.ENCHANTED_GOLDEN_APPLE);
    private static final Set<Material> DEFAULT_SHULKER_CONTENTS = Set.of(
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION,
            Material.END_CRYSTAL, Material.OBSIDIAN, Material.ENDER_PEARL, Material.EXPERIENCE_BOTTLE);

    private final Type type;
    private final Map<Material, Integer> limits;
    private final Set<Material> banned;
    private final Set<Material> shulkerContents;
    private final Set<String> bannedEffects;
    private final int armorLimit;
    private final boolean weaknessArrows;
    private final boolean weaknessPotions;

    public TierKitRules(ConfigurationSection config, Type type) {
        this.type = type;
        String path = "rules." + type.key() + ".";
        Map<Material, Integer> configured = new HashMap<>();
        configured.put(Material.RESPAWN_ANCHOR, 64);
        configured.put(Material.FIREWORK_ROCKET, 24);
        configured.put(Material.TOTEM_OF_UNDYING, type == Type.BELOW ? 14 : 8);
        readLimits(config.getConfigurationSection("rules.limits"), configured);
        readLimits(config.getConfigurationSection(path + "limits"), configured);
        this.limits = Map.copyOf(configured);
        this.armorLimit = Math.max(0, config.getInt("rules.max-armor-pieces", 4));
        this.banned = new HashSet<>(DEFAULT_BANNED);
        this.banned.addAll(materials(config.getStringList("rules.banned-items")));
        this.banned.addAll(materials(config.getStringList(path + "banned-items")));
        this.shulkerContents = config.contains("rules.above.shulker-contents")
                ? materials(config.getStringList("rules.above.shulker-contents"))
                : DEFAULT_SHULKER_CONTENTS;
        this.bannedEffects = new HashSet<>();
        for (String value : config.getStringList("rules.banned-effects")) bannedEffects.add(normalizeEffect(value));
        for (String value : config.getStringList(path + "banned-effects")) bannedEffects.add(normalizeEffect(value));
        this.weaknessArrows = config.getBoolean(path + "allow-weakness-arrows", type == Type.ABOVE);
        this.weaknessPotions = config.getBoolean(path + "allow-weakness-potions", false);
    }

    private static Set<Material> materials(List<String> values) {
        Set<Material> result = new HashSet<>();
        for (String value : values) {
            Material material = Material.matchMaterial(value);
            if (material == null || !material.isItem()) throw new IllegalArgumentException("Invalid tier-test material: " + value);
            result.add(material);
        }
        return result;
    }

    private static void readLimits(ConfigurationSection section, Map<Material, Integer> target) {
        if (section == null) return;
        for (String key : section.getKeys(false)) {
            Material material = Material.matchMaterial(key);
            if (material == null || !material.isItem() || !section.isInt(key) || section.getInt(key) < 0)
                throw new IllegalArgumentException("Invalid tier-test limit: " + key);
            target.put(material, section.getInt(key));
        }
    }

    private static String normalizeEffect(String value) {
        String normalized = value.toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("minecraft:") ? normalized.substring(10) : normalized;
    }

    public Result sanitize(TierKitSnapshot source) {
        Budget budget = new Budget();
        ItemStack[] armor = cleanArray(source.armor(), budget, false, "armor");
        ItemStack offhand = clean(source.offhand(), budget, false, "offhand");
        ItemStack[] storage = cleanArray(source.storage(), budget, false, "inventory");
        ItemStack[] ender = source.ender();
        if (type == Type.BELOW) {
            for (int i = 0; i < ender.length; i++) {
                if (present(ender[i])) budget.changes.add("ender[" + i + "]: removed " + ender[i].getType() + " (storage not allowed)");
                ender[i] = null;
            }
        } else {
            ender = cleanArray(ender, budget, false, "ender");
        }
        return new Result(new TierKitSnapshot(storage, armor, offhand, ender), budget.changes);
    }

    private final class Budget {
        private final Map<Material, Integer> remaining = new HashMap<>(limits);
        private int armorRemaining = armorLimit;
        private final List<String> changes = new ArrayList<>();

        private int available(Material material) {
            int amount = remaining.getOrDefault(material, Integer.MAX_VALUE);
            return isArmor(material) ? Math.min(amount, armorRemaining) : amount;
        }

        private void consume(Material material, int amount) {
            if (remaining.containsKey(material)) remaining.computeIfPresent(material, (key, left) -> left - amount);
            if (isArmor(material)) armorRemaining -= amount;
        }
    }

    private ItemStack[] cleanArray(ItemStack[] items, Budget budget, boolean insideShulker, String location) {
        for (int i = 0; i < items.length; i++) items[i] = clean(items[i], budget, insideShulker, location + "[" + i + "]");
        return items;
    }

    private static boolean present(ItemStack item) {
        return item != null && !item.getType().isAir() && item.getAmount() > 0;
    }

    private static boolean isArmor(Material material) {
        String name = material.name();
        return name.endsWith("_HELMET") || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS") || name.endsWith("_BOOTS");
    }

    private static boolean storageItem(Material material) {
        String name = material.name();
        return name.endsWith("SHULKER_BOX") || name.endsWith("BUNDLE") || name.endsWith("_CHEST")
                || name.equals("CHEST") || name.contains("CHEST_MINECART") || name.contains("HOPPER")
                || name.equals("BARREL") || name.equals("DISPENSER") || name.equals("DROPPER")
                || name.equals("FURNACE") || name.equals("BLAST_FURNACE") || name.equals("SMOKER")
                || name.equals("BREWING_STAND") || name.equals("DECORATED_POT") || name.equals("CHISELED_BOOKSHELF")
                || name.equals("CRAFTER") || name.endsWith("_CHEST_BOAT") || name.endsWith("_CHEST_RAFT");
    }

    private static boolean prohibitedMaterial(Material material) {
        String name = material.name();
        return !material.isItem() || name.endsWith("_SPAWN_EGG") || name.endsWith("COMMAND_BLOCK")
                || name.equals("COMMAND_BLOCK_MINECART") || name.equals("BARRIER")
                || name.equals("BEDROCK") || name.equals("STRUCTURE_BLOCK") || name.equals("STRUCTURE_VOID")
                || name.equals("JIGSAW") || name.equals("DEBUG_STICK") || name.equals("LIGHT")
                || name.equals("KNOWLEDGE_BOOK") || name.equals("END_PORTAL_FRAME")
                || name.equals("REINFORCED_DEEPSLATE") || name.equals("BUDDING_AMETHYST")
                || name.equals("SPAWNER") || name.equals("TRIAL_SPAWNER") || name.equals("VAULT")
                || name.equals("INFESTED_STONE") || name.startsWith("INFESTED_");
    }

    private ItemStack clean(ItemStack source, Budget budget, boolean insideShulker, String location) {
        if (!present(source)) return null;
        ItemStack item = source.clone();
        Material material = item.getType();
        boolean shulker = material.name().endsWith("SHULKER_BOX");
        if (banned.contains(material) || prohibitedMaterial(material))
            return remove(item, budget, location, "prohibited material");
        if (insideShulker && (!shulkerContents.contains(material) || storageItem(material)))
            return remove(item, budget, location, "not allowed inside shulker");
        if (storageItem(material) && (type == Type.BELOW || (!shulker && material != Material.ENDER_CHEST)))
            return remove(item, budget, location, "storage not allowed");
        normalizeComponents(item, shulker, budget, location);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (meta instanceof org.bukkit.inventory.meta.FireworkMeta firework && (firework.getPower() < 1 || firework.getPower() > 3)) {
                firework.setPower(Math.max(1, Math.min(3, firework.getPower())));
                item.setItemMeta(firework);
                meta = item.getItemMeta();
                budget.changes.add(location + ": corrected firework duration");
            }
            if (meta instanceof org.bukkit.inventory.meta.CrossbowMeta crossbow && crossbow.hasChargedProjectiles()) {
                crossbow.setChargedProjectiles(List.of());
                item.setItemMeta(crossbow);
                meta = item.getItemMeta();
                budget.changes.add(location + ": cleared loaded crossbow projectiles");
            }
            if (meta instanceof Damageable damage && (damage.getDamage() < 0 ||
                    (material.getMaxDurability() > 0 && damage.getDamage() >= material.getMaxDurability()))) {
                damage.setDamage(Math.max(0, material.getMaxDurability() - 1));
                item.setItemMeta(damage);
                budget.changes.add(location + ": corrected durability");
            }
            normalizeEnchantments(item, budget, location);
            meta = item.getItemMeta();
            if (meta instanceof PotionMeta potion && !validPotion(material, potion))
                return remove(item, budget, location, "prohibited or custom potion effects");
            if (meta instanceof BundleMeta || (meta instanceof BlockStateMeta && !shulker && material != Material.SHIELD))
                return remove(item, budget, location, "unsupported storage or block metadata");
        }
        int max = Math.min(material.getMaxStackSize(), budget.available(material));
        int original = item.getAmount();
        int amount = Math.min(original, max);
        if (amount <= 0) return remove(item, budget, location, "global limit");
        if (shulker) {
            if (!(meta instanceof BlockStateMeta blockMeta) || !(blockMeta.getBlockState() instanceof ShulkerBox box))
                return remove(item, budget, location, "invalid shulker metadata");
            ItemStack[] contents = cleanArray(box.getSnapshotInventory().getContents(), budget, true, location + "/shulker");
            ItemStack fresh = new ItemStack(material);
            BlockStateMeta freshMeta = (BlockStateMeta) fresh.getItemMeta();
            ShulkerBox freshBox = (ShulkerBox) freshMeta.getBlockState();
            freshBox.getSnapshotInventory().setContents(contents);
            freshMeta.setBlockState(freshBox);
            if (blockMeta.hasDisplayName()) freshMeta.displayName(blockMeta.displayName());
            fresh.setItemMeta(freshMeta);
            item = fresh;
        }
        item.setAmount(amount);
        budget.consume(material, amount);
        if (amount != original) budget.changes.add(location + ": " + material + " reduced from " + original + " to " + amount);
        return item;
    }

    private static void normalizeComponents(ItemStack item, boolean shulker, Budget budget, String location) {
        ItemStack baseline = new ItemStack(item.getType());
        Set<io.papermc.paper.datacomponent.DataComponentType> components = new HashSet<>(baseline.getDataTypes());
        components.addAll(item.getDataTypes());
        Set<String> allowed = Set.of("custom_name", "item_name", "lore", "repair_cost", "damage", "enchantments",
                "stored_enchantments", "dyed_color", "trim", "banner_patterns", "base_color",
                "tooltip_display", "enchantment_glint_override", "custom_model_data", "item_model");
        for (var component : components) {
            String key = component.getKey().getKey();
            boolean vanilla = component.getKey().getNamespace().equals("minecraft");
            boolean preserve = vanilla && (allowed.contains(key)
                    || (key.equals("potion_contents") && item.getItemMeta() instanceof PotionMeta)
                    || (key.equals("fireworks") && item.getType() == Material.FIREWORK_ROCKET)
                    || (key.equals("firework_explosion") && item.getType() == Material.FIREWORK_STAR)
                    || (key.equals("charged_projectiles") && item.getType() == Material.CROSSBOW)
                    || (shulker && (key.equals("container") || key.equals("block_entity_data"))));
            if (preserve) continue;
            ItemStack before = item.clone();
            item.resetData(component);
            if (!item.isSimilar(before)) budget.changes.add(location + ": reset " + key + " to vanilla");
        }
    }

    private static void normalizeEnchantments(ItemStack item, Budget budget, String location) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;
        List<Enchantment> kept = new ArrayList<>();
        List<Map.Entry<Enchantment, Integer>> enchants = new ArrayList<>(meta.getEnchants().entrySet());
        enchants.sort(java.util.Comparator.comparingInt((Map.Entry<Enchantment, Integer> entry)->entry.getValue()).reversed()
                .thenComparing(entry->entry.getKey().getKey().toString()));
        for (var entry : enchants) {
            Enchantment enchantment = entry.getKey();
            int level = entry.getValue();
            boolean legal = enchantment.getKey().getNamespace().equals("minecraft") && enchantment.canEnchantItem(item)
                    && level >= enchantment.getStartLevel() && kept.stream().noneMatch(enchantment::conflictsWith);
            if (!legal) {
                meta.removeEnchant(enchantment);
                budget.changes.add(location + ": removed invalid enchantment " + enchantment.getKey().getKey());
            } else {
                kept.add(enchantment);
                if (level > enchantment.getMaxLevel()) {
                    meta.removeEnchant(enchantment);
                    meta.addEnchant(enchantment, enchantment.getMaxLevel(), true);
                    budget.changes.add(location + ": capped " + enchantment.getKey().getKey());
                }
            }
        }
        if (meta instanceof EnchantmentStorageMeta book) {
            for (var entry : new ArrayList<>(book.getStoredEnchants().entrySet())) {
                Enchantment enchantment = entry.getKey();
                if (!enchantment.getKey().getNamespace().equals("minecraft") || entry.getValue() < enchantment.getStartLevel()) {
                    book.removeStoredEnchant(enchantment);
                    budget.changes.add(location + ": removed invalid stored enchantment");
                } else if (entry.getValue() > enchantment.getMaxLevel()) {
                    book.removeStoredEnchant(enchantment);
                    book.addStoredEnchant(enchantment,enchantment.getMaxLevel(),true);
                    budget.changes.add(location + ": capped stored enchantment");
                }
            }
        }
        item.setItemMeta(meta);
    }

    public boolean allowsShulkerItem(ItemStack item) {
        if (!present(item)) return true;
        if (type != Type.ABOVE || !shulkerContents.contains(item.getType())) return false;
        Budget budget = new Budget();
        ItemStack result = clean(item, budget, true, "shulker");
        return result != null && result.getAmount() == item.getAmount() && budget.changes.isEmpty();
    }

    private boolean validPotion(Material material, PotionMeta potion) {
        if (potion.hasCustomEffects()) return false;
        if (potion.getBasePotionType() == null) return false;
        if (potion.getBasePotionType().name().contains("TURTLE_MASTER")) return false;
        for (PotionEffect effect : potion.getBasePotionType().getPotionEffects()) {
            String key = effect.getType().getKey().getKey();
            if (bannedEffects.contains(key)) return false;
            if (key.equals("weakness") && !(material == Material.TIPPED_ARROW ? weaknessArrows : weaknessPotions)) return false;
        }
        return true;
    }

    private static ItemStack remove(ItemStack item, Budget budget, String location, String reason) {
        budget.changes.add(location + ": removed " + item.getAmount() + " " + item.getType() + " (" + reason + ")");
        return null;
    }
}
