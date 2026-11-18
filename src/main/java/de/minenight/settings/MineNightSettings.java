package de.minenight.settings;

import de.minenight.allinone.MineNightModule;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.configuration.file.YamlConfiguration;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public final class MineNightSettings extends MineNightModule implements Listener {
    private final Map<UUID, Preferences> preferences = new ConcurrentHashMap<>();
    private final MiniMessage mini = MiniMessage.miniMessage();
    private File dataFile;
    private YamlConfiguration data;

    public record Preferences(boolean chat, boolean tpa, boolean tpahere) { }
    private static final Preferences DEFAULT = new Preferences(true, true, true);
    private static final class SettingsHolder implements InventoryHolder {
        private Inventory inventory;
        @Override public Inventory getInventory() { return inventory; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "players.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String key : data.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                preferences.put(uuid, new Preferences(data.getBoolean(key + ".chat", true), data.getBoolean(key + ".tpa", true), data.getBoolean(key + ".tpahere", true)));
            } catch (IllegalArgumentException ignored) { }
        }
        getCommand("settings").setExecutor(this);
        Bukkit.getPluginManager().registerEvents(this, core());
    }

    @Override public void onDisable() { saveData(); }

    public boolean allowsChat(UUID id) { return preferences.getOrDefault(id, DEFAULT).chat(); }
    public boolean allowsTpa(UUID id) { return preferences.getOrDefault(id, DEFAULT).tpa(); }
    public boolean allowsTpaHere(UUID id) { return preferences.getOrDefault(id, DEFAULT).tpahere(); }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is for players only.");
            return true;
        }
        if (args.length != 0) {
            player.sendMessage(mini.deserialize("<gray>Usage: <white>/settings"));
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        SettingsHolder holder = new SettingsHolder();
        Inventory inv = Bukkit.createInventory(holder, 9, mini.deserialize("<gradient:#8175AE:#D5DCE8><bold>MineNight Settings</bold></gradient>"));
        holder.inventory = inv;
        refresh(inv, player.getUniqueId());
        player.openInventory(inv);
    }

    private void refresh(Inventory inv, UUID id) {
        Preferences p = preferences.getOrDefault(id, DEFAULT);
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.empty());
        filler.setItemMeta(fillerMeta);
        for (int slot = 0; slot < inv.getSize(); slot++) inv.setItem(slot, filler);
        inv.setItem(2, setting(Material.PAPER, "Chat Messages", p.chat(), "Show or hide player chat messages"));
        inv.setItem(4, setting(Material.ENDER_PEARL, "TPA Requests", p.tpa(), "Allow /tpa requests from other players"));
        inv.setItem(6, setting(Material.CHORUS_FRUIT, "TPAHere Requests", p.tpahere(), "Allow /tpahere requests from other players"));
    }

    private ItemStack setting(Material material, String name, boolean enabled, String description) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(mini.deserialize("<gradient:#8175AE:#D5DCE8><bold>" + name + "</bold></gradient>"));
        meta.lore(List.of(mini.deserialize("<gray>" + description), Component.empty(), mini.deserialize(enabled ? "<green>Status: ON" : "<red>Status: OFF"), mini.deserialize("<yellow>Click to toggle")));
        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SettingsHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        Preferences old = preferences.getOrDefault(player.getUniqueId(), DEFAULT);
        Preferences next = switch (event.getSlot()) {
            case 2 -> new Preferences(!old.chat(), old.tpa(), old.tpahere());
            case 4 -> new Preferences(old.chat(), !old.tpa(), old.tpahere());
            case 6 -> new Preferences(old.chat(), old.tpa(), !old.tpahere());
            default -> null;
        };
        if (next == null) return;
        preferences.put(player.getUniqueId(), next);
        saveData();
        if (event.getSlot() == 4 && !next.tpa()) discardRequests(player.getUniqueId(), false);
        if (event.getSlot() == 6 && !next.tpahere()) discardRequests(player.getUniqueId(), true);
        refresh(event.getView().getTopInventory(), player.getUniqueId());
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
    }

    private void discardRequests(UUID id, boolean here) {
        if (core().module("tpa") instanceof de.minenight.tpa.MineNightTPA tpa) tpa.cancelIncoming(id, here);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SettingsHolder) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent event) {
        event.viewers().removeIf(viewer -> viewer instanceof Player player && !allowsChat(player.getUniqueId()));
    }

    private void saveData() {
        if (data == null) return;
        for (Map.Entry<UUID, Preferences> entry : preferences.entrySet()) {
            String key = entry.getKey().toString();
            Preferences p = entry.getValue();
            data.set(key + ".chat", p.chat());
            data.set(key + ".tpa", p.tpa());
            data.set(key + ".tpahere", p.tpahere());
        }
        try { data.save(dataFile); }
        catch (IOException exception) { getLogger().warning("Unable to save player settings: " + exception.getMessage()); }
    }
}
