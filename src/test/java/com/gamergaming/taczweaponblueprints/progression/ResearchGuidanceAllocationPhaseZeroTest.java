package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;

class ResearchGuidanceAllocationPhaseZeroTest {
    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void flexibleRequirementCannotConsumeTheOnlyUnitForARestrictiveRequirement() {
        ResearchIngredientPlanner.Allocation allocation = ResearchIngredientPlanner.allocation(
                        List.of(
                                new ItemStack(Items.PAPER),
                                new ItemStack(Items.IRON_INGOT, 2)),
                        List.of(
                                alternativeRequirement(2, Items.PAPER, Items.IRON_INGOT),
                                itemRequirement(1, Items.PAPER)))
                .orElseThrow();

        assertTrue(allocation.complete());
        assertEquals(3, allocation.totalRequired());
        assertEquals(3, allocation.totalAllocated());
        assertEquals(2, allocation.allocatedForIngredient(0));
        assertEquals(1, allocation.allocatedForIngredient(1));
        assertEquals(1, allocation.decrement(0));
        assertEquals(2, allocation.decrement(1));
    }

    @Test
    void partialAllocationReportsTruthfulCombinedProgress() {
        ResearchIngredientPlanner.Allocation allocation = ResearchIngredientPlanner.allocation(
                        List.of(
                                new ItemStack(Items.PAPER),
                                new ItemStack(Items.IRON_INGOT)),
                        List.of(
                                alternativeRequirement(2, Items.PAPER, Items.IRON_INGOT),
                                itemRequirement(1, Items.PAPER)))
                .orElseThrow();

        assertFalse(allocation.complete());
        assertEquals(3, allocation.totalRequired());
        assertEquals(2, allocation.totalAllocated());
        assertEquals(2, allocation.allocatedForIngredient(0)
                + allocation.allocatedForIngredient(1));
    }

    @Test
    void tagOnlyRequirementRemainsAValidUnallocatedProgressShape() {
        ResearchIngredientPlanner.Requirement tagRequirement =
                new ResearchIngredientPlanner.Requirement(
                        List.of(),
                        Optional.of(new ResourceLocation("forge:ingots/iron")),
                        2);

        ResearchIngredientPlanner.Allocation allocation =
                ResearchIngredientPlanner.allocation(List.of(), List.of(tagRequirement))
                        .orElseThrow();

        assertFalse(allocation.complete());
        assertEquals(2, allocation.totalRequired());
        assertEquals(0, allocation.totalAllocated());
        assertEquals(0, allocation.allocatedForIngredient(0));
    }

    private static ResearchIngredientPlanner.Requirement itemRequirement(
            int count,
            net.minecraft.world.item.Item item) {
        return alternativeRequirement(count, item);
    }

    private static ResearchIngredientPlanner.Requirement alternativeRequirement(
            int count,
            net.minecraft.world.item.Item... items) {
        return new ResearchIngredientPlanner.Requirement(
                java.util.Arrays.stream(items)
                        .map(ForgeRegistries.ITEMS::getKey)
                        .toList(),
                Optional.empty(),
                count);
    }
}
