package com.gamergaming.taczweaponblueprints.menu;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Pure validation for input correlation and action-to-input compatibility. */
public final class BlueprintRecyclerActionValidator {
    private BlueprintRecyclerActionValidator() {
    }

    public static boolean matchesInput(
            ResourceLocation expectedId,
            int expectedCount,
            long expectedStateToken,
            long currentStateToken,
            Optional<ResourceLocation> physicalId,
            int physicalCount) {
        Optional<ResourceLocation> actual = physicalId == null
                ? Optional.empty()
                : physicalId;
        return expectedId != null
                && expectedCount > 0
                && expectedStateToken > 0L
                && expectedStateToken == currentStateToken
                && expectedCount == physicalCount
                && actual.filter(expectedId::equals).isPresent();
    }

    /** Compatibility helper retained for pure pre-token validation tests. */
    public static boolean matchesInput(
            ResourceLocation expectedId,
            int expectedCount,
            Optional<ResourceLocation> physicalId,
            int physicalCount) {
        return matchesInput(
                expectedId, expectedCount, 1L, 1L, physicalId, physicalCount);
    }

    public static boolean supports(
            BlueprintRecyclerPreview.InputKind inputKind,
            BlueprintRecyclerActionContract.Action action) {
        if (inputKind == null || action == null) {
            return false;
        }
        return switch (inputKind) {
            case BLUEPRINT -> action == BlueprintRecyclerActionContract.Action.RECYCLE;
            case RESEARCH_DATA -> action == BlueprintRecyclerActionContract.Action.REDEEM
                    || action == BlueprintRecyclerActionContract.Action.REDEEM_STACK;
            case PHYSICAL_ITEM ->
                    action == BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER
                            || action == BlueprintRecyclerActionContract.Action.RECOVER_POINTS;
            case BLUEPRINT_FRAGMENT ->
                    action == BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS;
            case EMPTY, INVALID -> false;
        };
    }
}
