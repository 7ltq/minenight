package de.minenight.allinone;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

public final class GuiPlayerIcons {
    private static final NamespacedKey KEY = new NamespacedKey("minenight", "gui_name_tag_icons");

    private GuiPlayerIcons() {}

    public static boolean useHeads(Player viewer) {
        return viewer.getPersistentDataContainer().getOrDefault(KEY, PersistentDataType.BYTE, (byte) 0) == 0;
    }

    public static Material material(Player viewer) {
        return useHeads(viewer) ? Material.PLAYER_HEAD : Material.NAME_TAG;
    }

    public static boolean toggle(Player viewer) {
        boolean heads = !useHeads(viewer);
        if (heads) viewer.getPersistentDataContainer().remove(KEY);
        else viewer.getPersistentDataContainer().set(KEY, PersistentDataType.BYTE, (byte) 1);
        return heads;
    }

    public static void applyProfile(ItemStack item, Player player) {
        if (!(item.getItemMeta() instanceof SkullMeta meta)) return;
        meta.setPlayerProfile(player.getPlayerProfile());
        item.setItemMeta(meta);
    }
}
