package com.gamergaming.taczweaponblueprints.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class PhysicalWeaponProvenanceTest {
    private static final ResourceLocation LOOT =
            ResourceLocation.parse("minecraft:chests/simple_dungeon");
    private static final ResourceLocation RECIPE =
            ResourceLocation.parse("tacz:test_rifle");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void trustedMarkersRoundTripAndLootCannotLaunderCraftedOrigin() {
        CompoundTag root = new CompoundTag();
        PhysicalWeaponProvenance expectedFound = new PhysicalWeaponProvenance(
                PhysicalWeaponProvenance.CURRENT_FORMAT,
                PhysicalWeaponProvenance.Origin.LOOT_GENERATED,
                LOOT);
        root.put(PhysicalWeaponProvenance.TAG_KEY, expectedFound.toTag());
        PhysicalWeaponProvenance found = PhysicalWeaponProvenance.fromTag(root).orElseThrow();
        assertTrue(found.verifiedLoot());
        assertEquals(LOOT, found.sourceId());

        PhysicalWeaponProvenance expectedCrafted = new PhysicalWeaponProvenance(
                PhysicalWeaponProvenance.CURRENT_FORMAT,
                PhysicalWeaponProvenance.Origin.CRAFTED_SURVIVAL,
                RECIPE);
        root.put(PhysicalWeaponProvenance.TAG_KEY, expectedCrafted.toTag());
        PhysicalWeaponProvenance crafted = PhysicalWeaponProvenance.fromTag(root).orElseThrow();
        assertEquals(PhysicalWeaponProvenance.Origin.CRAFTED_SURVIVAL, crafted.origin());
        assertFalse(crafted.verifiedLoot());
    }

    @Test void tradedSaveMarkersAreAdditiveAndPurchasedBlueprintPermissionsAreExplicit() {
        var root=new CompoundTag();var traded=new PhysicalWeaponProvenance(1,PhysicalWeaponProvenance.Origin.TRADED,RECIPE);
        root.put(PhysicalWeaponProvenance.TAG_KEY,traded.toTag());assertEquals(traded,PhysicalWeaponProvenance.fromTag(root).orElseThrow());assertFalse(traded.verifiedLoot());
        var blueprint=BlueprintProvenance.traded();root.put(BlueprintProvenance.TAG_KEY,blueprint.toTag());
        assertEquals(blueprint,BlueprintProvenance.fromTag(root).orElseThrow());assertFalse(BlueprintProvenance.allowsRecycling(root));
        assertEquals(com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                BlueprintProvenance.learningMode(root,com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES));
    }

    @Test
    void malformedUnknownAndNonWeaponStacksFailClosed() {
        CompoundTag root = new CompoundTag();
        CompoundTag malformed = new CompoundTag();
        malformed.putInt("format", PhysicalWeaponProvenance.CURRENT_FORMAT);
        malformed.putString("origin", "loot_generated");
        malformed.putString("source_id", "not a resource id");
        root.put(PhysicalWeaponProvenance.TAG_KEY, malformed);

        assertTrue(PhysicalWeaponProvenance.fromTag(root).isEmpty());
        assertFalse(PhysicalWeaponProvenance.stampLootGenerated(
                new ItemStack(Items.PAPER), LOOT));
        assertFalse(PhysicalWeaponProvenance.stampCrafted(
                new ItemStack(Items.PAPER), RECIPE));
    }
}
