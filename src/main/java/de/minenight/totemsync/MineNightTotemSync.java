package de.minenight.totemsync;

import de.minenight.allinone.MineNightModule;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityResurrectEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;

public final class MineNightTotemSync extends MineNightModule implements Listener {
    private final Set<UUID> pending = new HashSet<>();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        Bukkit.getPluginManager().registerEvents(this, core());
    }

    @Override
    public void onDisable() {
        pending.clear();
    }

    private static boolean isTotem(ItemStack item) {
        return item != null && item.getType() == Material.TOTEM_OF_UNDYING;
    }

    private void resync(Player player) {
        if (!getConfig().getBoolean("enabled", true)) return;
        UUID id = player.getUniqueId();
        if (!pending.add(id)) return;
        Bukkit.getScheduler().runTask(core(), () -> {
            if (!pending.remove(id)) return;
            Player current = Bukkit.getPlayer(id);
            if (current != null && current.isOnline() && getConfig().getBoolean("enabled", true)) {
                current.updateInventory();
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        boolean relevant = isTotem(event.getCurrentItem()) || isTotem(event.getCursor());
        if (event.getClick() == ClickType.NUMBER_KEY && event.getHotbarButton() >= 0) {
            relevant |= isTotem(player.getInventory().getItem(event.getHotbarButton()));
        }
        if (event.getClick() == ClickType.SWAP_OFFHAND) {
            relevant |= isTotem(player.getInventory().getItemInOffHand());
        }
        if (relevant) resync(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (isTotem(event.getOldCursor()) || event.getNewItems().values().stream().anyMatch(MineNightTotemSync::isTotem)) {
            resync(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent event) {
        if (isTotem(event.getMainHandItem()) || isTotem(event.getOffHandItem())) resync(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onHotbarChange(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();
        if (isTotem(player.getInventory().getItem(event.getPreviousSlot()))
                || isTotem(player.getInventory().getItem(event.getNewSlot()))) resync(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onResurrect(EntityResurrectEvent event) {
        if (event.getEntity() instanceof Player player) resync(player);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
    }
}
