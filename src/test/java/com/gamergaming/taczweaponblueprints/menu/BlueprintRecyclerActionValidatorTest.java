package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BlueprintRecyclerActionValidatorTest {
    private static final ResourceLocation INPUT = new ResourceLocation("test:input");
    private static final ResourceLocation OTHER = new ResourceLocation("test:other");

    @Test
    void physicalIdentityAndCountMustBothMatchThePreview() {
        assertTrue(BlueprintRecyclerActionValidator.matchesInput(
                INPUT, 4, Optional.of(INPUT), 4));
        assertFalse(BlueprintRecyclerActionValidator.matchesInput(
                INPUT, 4, Optional.of(INPUT), 3));
        assertFalse(BlueprintRecyclerActionValidator.matchesInput(
                INPUT, 4, Optional.of(OTHER), 4));
        assertFalse(BlueprintRecyclerActionValidator.matchesInput(
                INPUT, 4, Optional.empty(), 4));
        assertFalse(BlueprintRecyclerActionValidator.matchesInput(
                null, 4, Optional.of(INPUT), 4));
        assertFalse(BlueprintRecyclerActionValidator.matchesInput(
                INPUT, 0, Optional.of(INPUT), 0));
        assertTrue(BlueprintRecyclerActionValidator.matchesInput(
                INPUT, 4, 9L, 9L, Optional.of(INPUT), 4));
        assertFalse(BlueprintRecyclerActionValidator.matchesInput(
                INPUT, 4, 8L, 9L, Optional.of(INPUT), 4));
    }

    @Test
    void actionsCannotCrossTheSmartSlotInputBoundary() {
        assertTrue(BlueprintRecyclerActionValidator.supports(
                BlueprintRecyclerPreview.InputKind.BLUEPRINT,
                BlueprintRecyclerActionContract.Action.RECYCLE));
        assertFalse(BlueprintRecyclerActionValidator.supports(
                BlueprintRecyclerPreview.InputKind.BLUEPRINT,
                BlueprintRecyclerActionContract.Action.REDEEM));
        assertFalse(BlueprintRecyclerActionValidator.supports(
                BlueprintRecyclerPreview.InputKind.BLUEPRINT,
                BlueprintRecyclerActionContract.Action.REDEEM_STACK));

        assertFalse(BlueprintRecyclerActionValidator.supports(
                BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,
                BlueprintRecyclerActionContract.Action.RECYCLE));
        assertTrue(BlueprintRecyclerActionValidator.supports(
                BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,
                BlueprintRecyclerActionContract.Action.REDEEM));
        assertTrue(BlueprintRecyclerActionValidator.supports(
                BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,
                BlueprintRecyclerActionContract.Action.REDEEM_STACK));

        assertTrue(BlueprintRecyclerActionValidator.supports(
                BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM,
                BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER));
        assertFalse(BlueprintRecyclerActionValidator.supports(
                BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM,
                BlueprintRecyclerActionContract.Action.RECYCLE));

        for (BlueprintRecyclerActionContract.Action action
                : BlueprintRecyclerActionContract.Action.values()) {
            assertFalse(BlueprintRecyclerActionValidator.supports(
                    BlueprintRecyclerPreview.InputKind.EMPTY, action));
            assertFalse(BlueprintRecyclerActionValidator.supports(
                    BlueprintRecyclerPreview.InputKind.INVALID, action));
        }
    }
}
