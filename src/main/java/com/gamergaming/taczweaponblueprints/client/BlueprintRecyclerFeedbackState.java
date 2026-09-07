package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;

import net.minecraft.resources.ResourceLocation;

/** Keeps short-lived Recycler results attached to the input that produced them. */
public final class BlueprintRecyclerFeedbackState {
    public static final int DISPLAY_TICKS = 80;

    private Feedback feedback;
    private int remainingTicks;
    private long stateToken;
    private Optional<com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable.Result> outcome = Optional.empty();
    private Optional<BlueprintRecyclerActionContract.FragmentResult> fragments = Optional.empty();
    public Optional<BlueprintRecyclerActionContract.FragmentResult> visibleFragments() { return visibleCode().isPresent() ? fragments : Optional.empty(); }
    public Optional<ResourceLocation> visibleTarget() { return visibleCode().isPresent() ? Optional.of(feedback.inputId()) : Optional.empty(); }
    public Optional<com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable.Result> visibleOutcome() {
        return visibleCode().isPresent() ? outcome : Optional.empty();
    }

    public void accept(
            BlueprintRecyclerActionContract.ActionResult result,
            BlueprintRecyclerPreview preview) {
        if (result == null || result.inputId().isEmpty() || preview == null) {
            clear();
            return;
        }
        // The same target/count can describe a replacement weapon or fragment stack.
        if (result.stateToken() > 0 && result.stateToken() != preview.stateToken()) {
            clear();
            return;
        }
        stateToken = result.stateToken();
        ResourceLocation resultInputId = result.inputId().orElseThrow();
        outcome = result.outcome();
        fragments = result.fragments();
        if (preview.inputKind() == BlueprintRecyclerPreview.InputKind.EMPTY) {
            if (result.code() == BlueprintRecyclerActionContract.ResultCode.SUCCESS) {
                feedback = Feedback.afterConsumption(result.code(), resultInputId);
                remainingTicks = DISPLAY_TICKS;
            } else {
                clear();
            }
            return;
        }
        if (preview.inputId().filter(resultInputId::equals).isEmpty()) {
            clear();
            return;
        }
        show(result.code(), resultInputId, preview);
    }

    public void show(
            BlueprintRecyclerActionContract.ResultCode code,
            BlueprintRecyclerPreview preview) {
        if (code == null || preview == null || preview.inputId().isEmpty()
                || preview.inputKind() == BlueprintRecyclerPreview.InputKind.EMPTY) {
            clear();
            return;
        }
        stateToken = 0L;
        outcome = Optional.empty();
        fragments = Optional.empty();
        show(code, preview.inputId().orElseThrow(), preview);
    }

    public void reconcile(BlueprintRecyclerPreview preview) {
        if (feedback == null) {
            return;
        }
        if (preview == null) {
            clear();
            return;
        }
        if (preview.inputKind() == BlueprintRecyclerPreview.InputKind.EMPTY) {
            if (feedback.code() == BlueprintRecyclerActionContract.ResultCode.SUCCESS) {
                feedback = feedback.afterConsumption();
            } else {
                clear();
            }
            return;
        }
        if (stateToken > 0 && preview.stateToken() != stateToken
                || feedback.observedEmpty()
                || preview.inputId().filter(feedback.inputId()::equals).isEmpty()
                || preview.inputKind() != feedback.inputKind()
                || preview.inputCount() != feedback.inputCount()) {
            clear();
        }
    }

    public Optional<BlueprintRecyclerActionContract.ResultCode> visibleCode() {
        return feedback == null || remainingTicks <= 0
                ? Optional.empty()
                : Optional.of(feedback.code());
    }

    /** Returns true only when visible feedback expires on this tick. */
    public boolean tick() {
        if (remainingTicks <= 0) {
            return false;
        }
        remainingTicks--;
        if (remainingTicks == 0) {
            feedback = null;
            return true;
        }
        return false;
    }

    public void clear() {
        feedback = null;
        remainingTicks = 0;
        stateToken = 0L;
        outcome = Optional.empty();
        fragments = Optional.empty();
    }

    private void show(
            BlueprintRecyclerActionContract.ResultCode code,
            ResourceLocation inputId,
            BlueprintRecyclerPreview preview) {
        feedback = new Feedback(
                code,
                inputId,
                preview.inputKind(),
                preview.inputCount(),
                false);
        remainingTicks = DISPLAY_TICKS;
    }

    private record Feedback(
            BlueprintRecyclerActionContract.ResultCode code,
            ResourceLocation inputId,
            BlueprintRecyclerPreview.InputKind inputKind,
            int inputCount,
            boolean observedEmpty) {
        private Feedback {
            if (code == null || inputId == null || inputCount < 0
                    || (!observedEmpty && (inputKind == null || inputCount < 1))) {
                throw new IllegalArgumentException("invalid Blueprint Recycler feedback");
            }
        }

        private static Feedback afterConsumption(
                BlueprintRecyclerActionContract.ResultCode code,
                ResourceLocation inputId) {
            return new Feedback(code, inputId, null, 0, true);
        }

        private Feedback afterConsumption() {
            return observedEmpty ? this : afterConsumption(code, inputId);
        }
    }
}
