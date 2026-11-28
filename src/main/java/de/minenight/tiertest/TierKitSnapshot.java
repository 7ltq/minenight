package de.minenight.tiertest;

import java.util.Arrays;
import org.bukkit.inventory.ItemStack;

public record TierKitSnapshot(ItemStack[] storage, ItemStack[] armor, ItemStack offhand, ItemStack[] ender) {
    public TierKitSnapshot {
        storage = copy(storage, 36);
        armor = copy(armor, 4);
        offhand = cloneItem(offhand);
        ender = copy(ender, 27);
    }

    private static ItemStack cloneItem(ItemStack item) {
        return item == null ? null : item.clone();
    }

    private static ItemStack[] copy(ItemStack[] source, int size) {
        ItemStack[] result = Arrays.copyOf(source, size);
        for (int i = 0; i < result.length; i++) result[i] = cloneItem(result[i]);
        return result;
    }

    @Override public ItemStack[] storage() { return copy(storage, 36); }
    @Override public ItemStack[] armor() { return copy(armor, 4); }
    @Override public ItemStack offhand() { return cloneItem(offhand); }
    @Override public ItemStack[] ender() { return copy(ender, 27); }
}
