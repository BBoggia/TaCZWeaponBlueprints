package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryService;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;

import net.minecraft.resources.ResourceLocation;

class BlueprintRecyclerPreviewTest {
    private static final ResourceLocation BLUEPRINT = new ResourceLocation("test:rifle");
    private static final ResourceLocation RESEARCH_NOTE =
            new ResourceLocation("taczweaponblueprints:research_note");

    @Test
    void blueprintDecisionUsesTheDedicatedSmartSlotContract() {
        BlueprintRecyclerPreview preview = new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.BLUEPRINT,
                Optional.of(BLUEPRINT),
                1,
                3,
                4,
                20,
                Optional.of(BlueprintRecyclingService.Status.SUCCESS),
                Optional.empty());

        assertEquals(BlueprintRecyclerPreview.InputKind.BLUEPRINT, preview.inputKind());
        assertEquals(Optional.of(BLUEPRINT), preview.inputId());
        assertTrue(preview.actionable());
    }

    @Test
    void researchDataDecisionUsesTheSameDedicatedSmartSlot() {
        BlueprintRecyclerPreview preview = new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,
                Optional.of(RESEARCH_NOTE),
                6,
                2,
                4,
                20,
                Optional.empty(),
                Optional.of(ResearchDataRedemptionService.Status.SUCCESS));

        assertEquals(BlueprintRecyclerPreview.InputKind.RESEARCH_DATA, preview.inputKind());
        assertEquals(6, preview.inputCount());
        assertEquals(Optional.of(RESEARCH_NOTE), preview.inputId());
        assertTrue(preview.actionable());
    }

    @Test
    void emptyAndInvalidInputsRemainNonActionable() {
        assertFalse(BlueprintRecyclerPreview.empty(4, 20).actionable());
        assertFalse(BlueprintRecyclerPreview.invalid(
                Optional.of(new ResourceLocation("minecraft:dirt")), 1, 4, 20).actionable());
    }

    @Test
    void physicalItemDecisionCarriesTheExactServerCostAndOutput() {
        BlueprintRecyclerPreview.IngredientPreview iron =
                new BlueprintRecyclerPreview.IngredientPreview(
                        List.of(new ResourceLocation("minecraft:iron_ingot")),
                        Optional.empty(),
                        2,
                        3);
        BlueprintRecyclerPreview preview = physical(
                BlueprintReverseEngineeringService.Status.READY,
                List.of(iron));

        assertEquals(BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM, preview.inputKind());
        assertEquals(Optional.of(BLUEPRINT), preview.outputBlueprintId());
        assertEquals(1, preview.requiredInputCount());
        assertEquals(3, preview.pointCost());
        assertTrue(preview.ingredientsSatisfied());
        assertTrue(preview.outputAvailable());
        assertTrue(preview.customizationWillBeLost());
        assertFalse(preview.alreadyKnown());
        assertEquals(List.of(iron), preview.ingredients());
        assertTrue(preview.actionable());
    }

    @Test
    void learnedMarkerCanAccompanyAnActionableCopyDecision() {
        BlueprintRecyclerPreview preview = new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM,
                Optional.of(BLUEPRINT),
                1,
                0,
                5,
                20,
                Optional.empty(),
                Optional.empty(),
                7L,
                Optional.of(BLUEPRINT),
                1,
                0,
                true,
                true,
                false,
                true,
                Optional.of(BlueprintReverseEngineeringService.Status.READY),
                List.of());

        assertTrue(preview.alreadyKnown());
        assertTrue(preview.actionable());
    }

    @Test
    void inputKindsCannotCarryTheWrongServerDecision() {
        assertThrows(IllegalArgumentException.class, () -> new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.BLUEPRINT,
                Optional.of(BLUEPRINT),
                1,
                2,
                4,
                20,
                Optional.empty(),
                Optional.of(ResearchDataRedemptionService.Status.SUCCESS)));

        assertThrows(IllegalArgumentException.class, () -> new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.EMPTY,
                Optional.of(BLUEPRINT),
                1,
                0,
                4,
                20,
                Optional.empty(),
                Optional.empty()));
    }

    @Test
    void alreadyKnownStatusCannotLoseItsAuthoritativeMarker() {
        assertThrows(IllegalArgumentException.class, () -> new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM,
                Optional.of(BLUEPRINT),
                1,
                0,
                5,
                20,
                Optional.empty(),
                Optional.empty(),
                7L,
                Optional.of(BLUEPRINT),
                1,
                0,
                true,
                true,
                false,
                false,
                Optional.of(BlueprintReverseEngineeringService.Status.ALREADY_KNOWN),
                List.of()));
    }

    @Test
    void everyExistingServiceStatusHasAnExplicitResultMapping() {
        for (BlueprintRecyclingService.Status status : BlueprintRecyclingService.Status.values()) {
            assertEquals(
                    status.name(),
                    BlueprintRecyclerActionContract.ResultCode.from(status).name());
        }
        for (ResearchDataRedemptionService.Status status
                : ResearchDataRedemptionService.Status.values()) {
            BlueprintRecyclerActionContract.ResultCode mapped =
                    BlueprintRecyclerActionContract.ResultCode.from(status);
            if (status == ResearchDataRedemptionService.Status.STALE_INVENTORY) {
                assertEquals(BlueprintRecyclerActionContract.ResultCode.STALE_INPUT, mapped);
            } else {
                assertEquals(status.name(), mapped.name());
            }
        }
        for (BlueprintReverseEngineeringService.Status status
                : BlueprintReverseEngineeringService.Status.values()) {
            BlueprintRecyclerActionContract.ResultCode mapped =
                    BlueprintRecyclerActionContract.ResultCode.from(status);
            switch (status) {
                case EMPTY_INPUT, UNSUPPORTED_ITEM, INVALID_PLAYER -> assertEquals(
                        BlueprintRecyclerActionContract.ResultCode.INVALID_INPUT, mapped);
                case READY -> assertEquals(
                        BlueprintRecyclerActionContract.ResultCode.TRANSACTION_FAILED, mapped);
                case RECOVERY_MODE_DISABLED -> assertEquals(
                        BlueprintRecyclerActionContract.ResultCode.RECOVERY_DISABLED, mapped);
                default -> assertEquals(status.name(), mapped.name());
            }
        }
        for (FoundWeaponRecoveryService.Status status
                : FoundWeaponRecoveryService.Status.values()) {
            BlueprintRecyclerActionContract.ResultCode mapped =
                    BlueprintRecyclerActionContract.ResultCode.from(status);
            switch (status) {
                case REVERSE_ENGINEERING_INELIGIBLE -> assertEquals(
                        BlueprintRecyclerActionContract.ResultCode.POLICY_INELIGIBLE, mapped);
                case READY -> assertEquals(
                        BlueprintRecyclerActionContract.ResultCode.TRANSACTION_FAILED, mapped);
                default -> assertEquals(status.name(), mapped.name());
            }
        }
    }

    private static BlueprintRecyclerPreview physical(
            BlueprintReverseEngineeringService.Status status,
            List<BlueprintRecyclerPreview.IngredientPreview> ingredients) {
        return new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM,
                Optional.of(BLUEPRINT),
                2,
                0,
                5,
                20,
                Optional.empty(),
                Optional.empty(),
                7L,
                Optional.of(BLUEPRINT),
                1,
                3,
                true,
                true,
                true,
                status == BlueprintReverseEngineeringService.Status.ALREADY_KNOWN,
                Optional.of(status),
                ingredients);
    }
}
