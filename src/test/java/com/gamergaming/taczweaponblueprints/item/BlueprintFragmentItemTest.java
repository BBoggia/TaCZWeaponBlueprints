package com.gamergaming.taczweaponblueprints.item;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.SharedConstants;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class BlueprintFragmentItemTest {
    private static final ResourceLocation FIRST = id("test:first");
    private static final ResourceLocation SECOND = id("test:second");

    @BeforeAll
    static void bootstrapMinecraftRegistries() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void factoryWritesOneCanonicalBoundedTargetAndEqualTargetsStack() {
        CompoundTag firstTag = BlueprintFragmentItem.createTargetTag(FIRST);
        CompoundTag sameTag = BlueprintFragmentItem.createTargetTag(FIRST);
        CompoundTag differentTag = BlueprintFragmentItem.createTargetTag(SECOND);
        ItemStack first = new ItemStack(Items.PAPER);
        ItemStack same = new ItemStack(Items.PAPER);
        ItemStack different = new ItemStack(Items.PAPER);
        first.setTag(firstTag);
        same.setTag(sameTag);
        different.setTag(differentTag);

        assertEquals(FIRST, BlueprintFragmentItem.getTarget(firstTag).orElseThrow());
        assertEquals(1, firstTag.size());
        assertTrue(ItemStack.isSameItemSameTags(first, same));
        assertFalse(ItemStack.isSameItemSameTags(first, different));
    }

    @Test
    void malformedExtraAndNonCanonicalDataFailClosed() {
        CompoundTag extra = BlueprintFragmentItem.createTargetTag(FIRST);
        extra.putInt("Injected", 1);
        CompoundTag wrongType = new CompoundTag();
        wrongType.putInt(BlueprintFragmentItem.TARGET_TAG, 1);

        assertTrue(BlueprintFragmentItem.getTarget(extra).isEmpty());
        assertTrue(BlueprintFragmentItem.getTarget(wrongType).isEmpty());
        assertTrue(BlueprintFragmentItem.getTarget((CompoundTag) null).isEmpty());
        assertTrue(BlueprintFragmentItem.parseTarget("Test:first").isEmpty());
        assertTrue(BlueprintFragmentItem.parseTarget("not an id").isEmpty());
        assertTrue(BlueprintFragmentItem.getTarget(ItemStack.EMPTY).isEmpty());
    }

    @Test
    void itemCreationRejectsOversizedAndMissingTargets() {
        String oversizedPath = "a".repeat(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        ResourceLocation oversized = id("test:" + oversizedPath);

        assertThrows(IllegalArgumentException.class,
                () -> BlueprintFragmentItem.createTargetTag(oversized));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintFragmentItem.createTargetTag(null));
    }

    @Test
    void copiedStacksRetainExactTargetIdentity() {
        ItemStack original = new ItemStack(Items.PAPER);
        original.setTag(BlueprintFragmentItem.createTargetTag(FIRST));
        original.setCount(32);
        ItemStack copy = original.copy();

        assertEquals(32, copy.getCount());
        assertEquals(FIRST, BlueprintFragmentItem.getTarget(copy.getTag()).orElseThrow());
        assertTrue(ItemStack.isSameItemSameTags(original, copy));
    }

    private static ResourceLocation id(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new IllegalArgumentException(value);
        }
        return parsed;
    }
}
