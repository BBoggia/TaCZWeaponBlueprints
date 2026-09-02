package com.gamergaming.taczweaponblueprints.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;

class ResearchDataLootModifierTest {
    @BeforeAll
    static void bootstrapMinecraft() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void snapshotsItsItemAndAcceptsOnlyRealProbabilities() {
        ItemStack source = new ItemStack(Items.PAPER, 2);
        ResearchDataLootModifier modifier = new ResearchDataLootModifier(
                new LootItemCondition[0], source, 0.12f);
        source.setCount(1);

        assertEquals(2, modifier.item().getCount());
        assertEquals(0.12f, modifier.chance());
        assertThrows(IllegalArgumentException.class, () -> new ResearchDataLootModifier(
                new LootItemCondition[0], ItemStack.EMPTY, 0.12f));
        assertThrows(IllegalArgumentException.class, () -> new ResearchDataLootModifier(
                new LootItemCondition[0], new ItemStack(Items.PAPER), 0.0f));
        assertThrows(IllegalArgumentException.class, () -> new ResearchDataLootModifier(
                new LootItemCondition[0], new ItemStack(Items.PAPER), Float.NaN));
    }
}
