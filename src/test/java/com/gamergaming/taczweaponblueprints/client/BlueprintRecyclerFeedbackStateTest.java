package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;

import net.minecraft.resources.ResourceLocation;

class BlueprintRecyclerFeedbackStateTest {
    private static final ResourceLocation NOTE = new ResourceLocation("test:note");
    private static final ResourceLocation REPORT = new ResourceLocation("test:report");

    @Test
    void resultRemainsVisibleForItsInputAndForTheEmptyPostConsumptionSlot() {
        BlueprintRecyclerFeedbackState state = new BlueprintRecyclerFeedbackState();
        state.accept(result(NOTE), researchData(NOTE, 2));

        state.reconcile(researchData(NOTE, 2));
        assertEquals(Optional.of(BlueprintRecyclerActionContract.ResultCode.SUCCESS),
                state.visibleCode());
        state.reconcile(BlueprintRecyclerPreview.empty(5, 100));
        assertTrue(state.visibleCode().isPresent());
    }

    @Test
    void changingToAnotherInputImmediatelyClearsStaleFeedback() {
        BlueprintRecyclerFeedbackState state = new BlueprintRecyclerFeedbackState();
        state.accept(result(NOTE), researchData(NOTE, 2));

        state.reconcile(researchData(REPORT, 2));

        assertTrue(state.visibleCode().isEmpty());
    }

    @Test
    void changingTheCountOfTheSameInputClearsStaleFeedback() {
        BlueprintRecyclerFeedbackState state = new BlueprintRecyclerFeedbackState();
        state.accept(result(NOTE), researchData(NOTE, 2));

        state.reconcile(researchData(NOTE, 1));

        assertTrue(state.visibleCode().isEmpty());
    }

    @Test
    void insertingTheSameInputAfterAnEmptySuccessClearsOldFeedback() {
        BlueprintRecyclerFeedbackState state = new BlueprintRecyclerFeedbackState();
        state.accept(result(NOTE), BlueprintRecyclerPreview.empty(5, 100));
        assertTrue(state.visibleCode().isPresent());

        state.reconcile(researchData(NOTE, 1));

        assertTrue(state.visibleCode().isEmpty());
    }

    @Test
    void removingInputClearsFailureFeedback() {
        BlueprintRecyclerFeedbackState state = new BlueprintRecyclerFeedbackState();
        state.accept(failure(NOTE), researchData(NOTE, 2));

        state.reconcile(BlueprintRecyclerPreview.empty(5, 100));

        assertTrue(state.visibleCode().isEmpty());
    }

    @Test
    void feedbackExpiresExactlyOnce() {
        BlueprintRecyclerFeedbackState state = new BlueprintRecyclerFeedbackState();
        state.accept(result(NOTE), researchData(NOTE, 2));

        for (int tick = 1; tick < BlueprintRecyclerFeedbackState.DISPLAY_TICKS; tick++) {
            assertFalse(state.tick());
        }
        assertTrue(state.tick());
        assertTrue(state.visibleCode().isEmpty());
        assertFalse(state.tick());
    }

    private static BlueprintRecyclerActionContract.ActionResult result(ResourceLocation id) {
        return new BlueprintRecyclerActionContract.ActionResult(
                BlueprintRecyclerActionContract.Action.REDEEM,
                Optional.of(id),
                BlueprintRecyclerActionContract.ResultCode.SUCCESS);
    }

    private static BlueprintRecyclerActionContract.ActionResult failure(ResourceLocation id) {
        return new BlueprintRecyclerActionContract.ActionResult(
                BlueprintRecyclerActionContract.Action.REDEEM,
                Optional.of(id),
                BlueprintRecyclerActionContract.ResultCode.TRANSACTION_FAILED);
    }

    private static BlueprintRecyclerPreview researchData(ResourceLocation id, int count) {
        return new BlueprintRecyclerPreview(
                BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,
                Optional.of(id),
                count,
                5,
                4,
                100,
                Optional.empty(),
                Optional.of(ResearchDataRedemptionService.Status.SUCCESS));
    }
}
