package com.gamergaming.taczweaponblueprints.menu;

import java.util.List;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Allocation-relevant inventory state used to keep an open research preview fresh.
 * Research ingredients match item identity or tags, so components do not affect this snapshot.
 */
final class ResearchInventorySnapshot {
    static final ResearchInventorySnapshot EMPTY = new ResearchInventorySnapshot(new Item[0], new int[0]);

    private final Item[] items;
    private final int[] counts;

    private ResearchInventorySnapshot(Item[] items, int[] counts) {
        this.items = items;
        this.counts = counts;
    }

    static ResearchInventorySnapshot capture(List<ItemStack> stacks) {
        if (stacks == null || stacks.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("research inventory stacks cannot be null");
        }
        Item[] items = new Item[stacks.size()];
        int[] counts = new int[stacks.size()];
        for (int slot = 0; slot < stacks.size(); slot++) {
            ItemStack stack = stacks.get(slot);
            items[slot] = stack.getItem();
            counts[slot] = stack.getCount();
        }
        return new ResearchInventorySnapshot(items, counts);
    }

    boolean matches(List<ItemStack> stacks) {
        if (stacks == null || stacks.size() != items.length) {
            return false;
        }
        for (int slot = 0; slot < items.length; slot++) {
            ItemStack stack = stacks.get(slot);
            if (stack == null || stack.getItem() != items[slot] || stack.getCount() != counts[slot]) {
                return false;
            }
        }
        return true;
    }
}
