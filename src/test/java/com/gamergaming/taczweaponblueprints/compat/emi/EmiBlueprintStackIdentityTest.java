package com.gamergaming.taczweaponblueprints.compat.emi;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

class EmiBlueprintStackIdentityTest {
    @Test
    void physicalBlueprintIdentityUsesOnlyCanonicalBlueprintId() {
        CompoundTag first = new CompoundTag();
        first.putString("bpId", "addon:weapons/carbine");
        first.putString("unrelated_provenance", "research");
        CompoundTag second = new CompoundTag();
        second.putString("bpId", "addon:weapons/carbine");

        assertEquals(
                EmiBlueprintStackIdentity.blueprintId(first),
                EmiBlueprintStackIdentity.blueprintId(second));
        assertEquals(
                "addon:weapons/carbine",
                EmiBlueprintStackIdentity.blueprintId(first));
    }

    @Test
    void malformedOrMissingIdentityDoesNotMatchConcreteBlueprints() {
        CompoundTag malformed = new CompoundTag();
        malformed.putString("bpId", "not a resource id");

        assertEquals("", EmiBlueprintStackIdentity.blueprintId(null));
        assertEquals("", EmiBlueprintStackIdentity.blueprintId(new CompoundTag()));
        assertEquals("", EmiBlueprintStackIdentity.blueprintId(malformed));
    }

    @Test
    void blankBlueprintTargetIsExplicitAndCanonical() {
        CompoundTag tag = new CompoundTag();
        ResourceLocation blueprintId = new ResourceLocation("addon", "pistol");

        EmiBlueprintStackIdentity.targetBlankBlueprint(tag, blueprintId);

        assertEquals("addon:pistol", EmiBlueprintStackIdentity.blankTarget(tag));
        assertThrows(
                IllegalArgumentException.class,
                () -> EmiBlueprintStackIdentity.targetBlankBlueprint(null, blueprintId));
        assertThrows(
                IllegalArgumentException.class,
                () -> EmiBlueprintStackIdentity.targetBlankBlueprint(tag, null));
    }
}
