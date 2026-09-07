package com.gamergaming.taczweaponblueprints.client;
import static org.junit.jupiter.api.Assertions.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import net.minecraft.resources.ResourceLocation;
class AnalyzerResultFeedbackTest {
    private static final ResourceLocation ID=new ResourceLocation("test:gun");
    @Test void delayedReceiptCannotAttachToReplacementWithSameTargetAndCount() {
        var f = new BlueprintRecyclerFeedbackState();
        var result = blueprintResult().withStateToken(10);
        var replacement = BlueprintRecyclerPreview.invalid(Optional.of(ID), 1, 0, 0).withStateToken(11);
        f.accept(result, replacement);
        assertTrue(f.visibleCode().isEmpty());
        assertTrue(f.visibleOutcome().isEmpty());
    }
    @Test void acceptedReceiptClearsWhenOnlyAuthoritativeStateChanges() {
        var f = new BlueprintRecyclerFeedbackState();
        var preview = BlueprintRecyclerPreview.invalid(Optional.of(ID), 1, 0, 0).withStateToken(10);
        f.accept(blueprintResult().withStateToken(10), preview);
        assertTrue(f.visibleOutcome().isPresent());
        f.reconcile(preview.withStateToken(11));
        assertTrue(f.visibleCode().isEmpty());
    }
    @Test void matchingPostConsumptionReceiptSurvivesUnrelatedEmptyStateChanges() {
        var f = new BlueprintRecyclerFeedbackState();
        f.accept(blueprintResult().withStateToken(10), BlueprintRecyclerPreview.EMPTY.withStateToken(10));
        f.reconcile(BlueprintRecyclerPreview.EMPTY.withStateToken(11));
        assertTrue(f.visibleOutcome().isPresent());
        f.reconcile(BlueprintRecyclerPreview.invalid(Optional.of(ID), 1, 0, 0).withStateToken(12));
        assertTrue(f.visibleCode().isEmpty());
    }
    private static BlueprintRecyclerActionContract.ActionResult blueprintResult() {
        return new BlueprintRecyclerActionContract.ActionResult(BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,
                Optional.of(ID), BlueprintRecyclerActionContract.ResultCode.SUCCESS,
                Optional.of(new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.BLUEPRINT, 1)));
    }
    @Test void committedNoResultSurvivesEmptyInputThenExpires() {
        var f=new BlueprintRecyclerFeedbackState();
        var outcome=new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.NO_RESULT,0);
        f.accept(new BlueprintRecyclerActionContract.ActionResult(BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,Optional.of(ID),
                BlueprintRecyclerActionContract.ResultCode.SUCCESS,Optional.of(outcome)),BlueprintRecyclerPreview.EMPTY);
        f.reconcile(BlueprintRecyclerPreview.EMPTY);
        assertEquals(Optional.of(outcome),f.visibleOutcome());assertEquals(Optional.of(ID),f.visibleTarget());
        for(int i=0;i<BlueprintRecyclerFeedbackState.DISPLAY_TICKS;i++)f.tick();
        assertTrue(f.visibleOutcome().isEmpty());assertTrue(f.visibleTarget().isEmpty());
    }
    @Test void reconstructionKeepsItsExactRetentionAndClearsForANewInput() {
        var f=new BlueprintRecyclerFeedbackState();var result=new BlueprintRecyclerActionContract.FragmentResult(3,0,2,true);
        f.accept(new BlueprintRecyclerActionContract.ActionResult(BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS,Optional.of(ID),
                BlueprintRecyclerActionContract.ResultCode.SUCCESS,Optional.empty(),Optional.of(result)),BlueprintRecyclerPreview.EMPTY);
        assertEquals(Optional.of(result),f.visibleFragments());
        f.reconcile(BlueprintRecyclerPreview.invalid(Optional.of(new ResourceLocation("test:new_item")),1,0,0));
        assertTrue(f.visibleFragments().isEmpty());assertTrue(f.visibleOutcome().isEmpty());
    }
    @Test void timedOutOrMismatchedRequestsCannotInstallResults() {
        var requests=new BlueprintRecyclerRequestTracker();
        var r=requests.begin(BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,ID,1,9).orElseThrow();
        var success=new BlueprintRecyclerActionContract.ActionResult(r.action(),Optional.of(ID),BlueprintRecyclerActionContract.ResultCode.SUCCESS,
                Optional.of(new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.BLUEPRINT,1)));
        assertFalse(requests.accept(r.requestId()+1,success));
        for(int i=0;i<BlueprintRecyclerRequestTracker.TIMEOUT_TICKS;i++)requests.tick();
        assertFalse(requests.accept(r.requestId(),success));
        var next=requests.begin(r.action(),ID,1,10).orElseThrow();
        assertFalse(requests.accept(r.requestId(),success));assertTrue(requests.accept(next.requestId(),success));
    }
}
