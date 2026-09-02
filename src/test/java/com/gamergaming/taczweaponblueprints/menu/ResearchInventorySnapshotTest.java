package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class ResearchInventorySnapshotTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void detectsAllocationRelevantItemAndCountChanges() {
        List<ItemStack> inventory = new ArrayList<>(List.of(
                new ItemStack(Items.PAPER, 2),
                new ItemStack(Items.IRON_INGOT, 3),
                ItemStack.EMPTY));
        ResearchInventorySnapshot snapshot = ResearchInventorySnapshot.capture(inventory);

        assertTrue(snapshot.matches(inventory));
        inventory.get(0).grow(1);
        assertFalse(snapshot.matches(inventory));
        inventory.set(0, new ItemStack(Items.PAPER, 2));
        assertTrue(snapshot.matches(inventory));
        inventory.set(1, new ItemStack(Items.GOLD_INGOT, 3));
        assertFalse(snapshot.matches(inventory));
    }

    @Test
    void rejectsInvalidOrStructurallyDifferentInventories() {
        ResearchInventorySnapshot snapshot = ResearchInventorySnapshot.capture(
                List.of(new ItemStack(Items.PAPER)));

        assertFalse(snapshot.matches(List.of()));
        assertFalse(snapshot.matches(null));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchInventorySnapshot.capture(null));
    }
}
