package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.network.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import net.minecraft.resources.ResourceLocation;

class ResearchTreeRouteReviewStateTest {
    private static final ResourceLocation A = ResourceLocation.parse("test:a"), B = ResourceLocation.parse("test:b"), C = ResourceLocation.parse("test:c");
    private static final ResearchRouteFingerprint FP = new ResearchRouteFingerprint(1,2);
    @AfterEach void clear() { ClientResearchPlannerState.clear(); }
    private ResearchTreeGraph graph() {
        return ResearchTreeGraph.withRequirementGroups(List.of(node(0,A,true),node(1,B,false),node(2,C,false)),List.of());
    }
    private ResearchTreeGraph.Node node(int ordinal, ResourceLocation id, boolean learned) {
        return new ResearchTreeGraph.Node(ordinal,id,"name."+id.getPath(),"gun",id,JournalVisibility.FULL,
                learned,true,!learned,1,0,0,0,learned ? ResearchTreeGraph.Availability.LEARNED : ResearchTreeGraph.Availability.AVAILABLE);
    }
    private ResearchSelectionPreview quote(ResourceLocation id, ResearchRouteFingerprint fp, int balance) {
        return new ResearchSelectionPreview(Optional.of(id),3,balance,true,true,true,false,false,List.of(),1,0,
                ResearchSelectionPreview.PathPlanningState.NONE,ResearchCostMode.POINTS_AND_ITEMS,Optional.of(fp),ResearchAccessSummary.NONE,
                ResearchSelectionProgressionPreview.EMPTY);
    }
    private ResearchTreeRouteReviewState.Binding binding(ResourceLocation id, long generation, ResearchRouteFingerprint fp, int balance, int inventory) {
        return new ResearchTreeRouteReviewState.Binding(7,generation,id,quote(id,fp,balance),inventory);
    }
    private ResearchGuidanceSnapshot snapshot(ResourceLocation target) {
        return new ResearchGuidanceSnapshot(target,ResearchGuidanceSnapshot.State.MISSING_POINTS,3,0,ResearchCostMode.POINTS_AND_ITEMS,
                false,true,0,List.of(),List.of(target),List.of(target),List.of(),Optional.of(target));
    }
    private ResearchRouteReviewResultPacket result(ResearchRouteReviewRequestPacket request) {
        return new ResearchRouteReviewResultPacket(request,new ResearchBenchMenu.GuidanceResult(ResearchBenchMenu.GuidanceResultCode.SUCCESS,Optional.of(snapshot(request.targetId()))));
    }
    @Test void unaffordableReviewDoesNotOverwriteTrackedGoalAndDelayedResponsesCannotReplaceCurrentTarget() {
        var graph=graph(); assertTrue(ClientResearchPlannerState.track(graph,A));
        var state=new ResearchTreeRouteReviewState();state.bind(binding(B,10,FP,0,0));var b=state.request(0).orElseThrow();
        state.bind(binding(C,10,FP,0,0));assertTrue(state.request(100).isEmpty());var c=state.request(500).orElseThrow();
        assertFalse(state.accept(result(b),graph,600));assertTrue(state.accept(result(c),graph,600));
        assertEquals(C,state.snapshot().orElseThrow().targetId());assertEquals(A,ClientResearchPlannerState.targetId().orElseThrow());
        assertEquals(ResearchTreeRouteReviewState.Status.CURRENT,state.status());
    }
    @Test void publicationQuoteBalanceAndInventoryChangesInvalidateAcceptedAndInflightReviews() {
        var state=new ResearchTreeRouteReviewState();var original=binding(B,10,FP,0,0); long now=10000;
        for(var changed:List.of(binding(B,11,FP,0,0),binding(B,10,new ResearchRouteFingerprint(3,4),0,0),binding(B,10,FP,1,0),binding(B,10,FP,0,1))) {
            now+=1000;state.bind(original);var request=state.request(now).orElseThrow();assertTrue(state.accept(result(request),graph(),10000));
            state.bind(changed);assertTrue(state.snapshot().isEmpty());assertFalse(state.accept(result(request),graph(),10000));
            state.clear();
        }
    }
    @Test void timeoutsAndThrottleHaveOnlyOneAutomaticRetry() {
        var state=new ResearchTreeRouteReviewState();state.bind(binding(B,10,FP,0,0));var first=state.request(0).orElseThrow();
        assertTrue(state.request(2999).isEmpty());var retry=state.request(3000).orElseThrow();assertNotEquals(first,retry);
        assertFalse(state.accept(result(first),graph(),3001));assertTrue(state.request(6000).isEmpty());
        assertEquals(ResearchTreeRouteReviewState.Status.UNAVAILABLE,state.status());
    }
    @Test void undisclosedOrIncompleteRouteProofIsRejected() {
        var state=new ResearchTreeRouteReviewState();state.bind(binding(B,10,FP,0,0));var request=state.request(0).orElseThrow();
        var graph=ResearchTreeGraph.withRequirementGroups(List.of(node(0,A,true),new ResearchTreeGraph.Node(1,B,"name.b","gun",B,JournalVisibility.FULL,false,true,true,1,0,1,0,ResearchTreeGraph.Availability.AVAILABLE)),List.of(new ResearchTreeGraph.RequirementGroup(B,0,List.of(A),0,false)));
        assertTrue(state.accept(result(request),graph,10));assertEquals(ResearchTreeRouteReviewState.Status.UNAVAILABLE,state.status());
        state.clear();state.bind(binding(B,10,FP,0,0));request=state.request(500).orElseThrow();
        assertTrue(state.accept(result(request),ResearchTreeGraph.EMPTY,501));assertTrue(state.snapshot().isEmpty());
    }
    @Test void sameFingerprintCannotKeepOldMaterialAllocationCurrent() {
        var paper = ResourceLocation.parse("minecraft:paper");
        var preview = new ResearchSelectionPreview(Optional.of(B),3,0,true,false,true,false,false,
                List.of(new ResearchSelectionPreview.IngredientPreview(List.of(paper),Optional.empty(),4,1)),1,1,
                ResearchSelectionPreview.PathPlanningState.NONE,ResearchCostMode.POINTS_AND_ITEMS,Optional.of(FP),ResearchAccessSummary.NONE,
                ResearchSelectionProgressionPreview.EMPTY);
        var state = new ResearchTreeRouteReviewState();
        state.bind(new ResearchTreeRouteReviewState.Binding(7,10,B,preview,0));var old=state.request(0).orElseThrow();
        state.bind(new ResearchTreeRouteReviewState.Binding(7,10,B,preview,1));assertFalse(state.accept(result(old),graph(),1));
        var request = state.request(500).orElseThrow();
        var changed = new ResearchGuidanceSnapshot(B,ResearchGuidanceSnapshot.State.MISSING_POINTS_AND_MATERIALS,3,0,
                ResearchCostMode.POINTS_AND_ITEMS,false,true,1,
                List.of(new ResearchGuidanceSnapshot.MaterialProgress(List.of(paper),Optional.empty(),4,2)),
                List.of(B),List.of(B),List.of(),Optional.of(B));
        assertTrue(state.accept(new ResearchRouteReviewResultPacket(request,new ResearchBenchMenu.GuidanceResult(
                ResearchBenchMenu.GuidanceResultCode.SUCCESS,Optional.of(changed))),graph(),501));
        assertEquals(ResearchTreeRouteReviewState.Status.UNAVAILABLE,state.status());assertTrue(state.snapshot().isEmpty());
    }

    @Test void materialIdentityCompletenessAndCapacityMustMatchTheQuote() {
        var paper = ResourceLocation.parse("minecraft:paper");
        var iron = ResourceLocation.parse("minecraft:iron_ingot");
        var preview = new ResearchSelectionPreview(Optional.of(B),3,0,true,false,true,false,false,
                List.of(new ResearchSelectionPreview.IngredientPreview(List.of(paper),Optional.empty(),4,1)),1,1,
                ResearchSelectionPreview.PathPlanningState.NONE,ResearchCostMode.POINTS_AND_ITEMS,Optional.of(FP),
                ResearchAccessSummary.NONE,ResearchSelectionProgressionPreview.EMPTY);
        for (int variant = 0; variant < 3; variant++) {
            var materials = variant == 1 ? List.<ResearchGuidanceSnapshot.MaterialProgress>of()
                    : List.of(new ResearchGuidanceSnapshot.MaterialProgress(List.of(variant == 0 ? iron : paper),Optional.empty(),4,1));
            var value = new ResearchGuidanceSnapshot(B,ResearchGuidanceSnapshot.State.MISSING_POINTS_AND_MATERIALS,
                    3,0,ResearchCostMode.POINTS_AND_ITEMS,false,variant != 2,1,4,1,1,materials,
                    List.of(B),List.of(B),List.of(),Optional.of(B));
            assertReviewStatus(preview, value, ResearchTreeRouteReviewState.Status.UNAVAILABLE);
        }
    }
    @Test void tagExamplesAndBoundedMaterialTruncationRemainValid() {
        var tag = ResourceLocation.parse("forge:ingots/iron");
        var preview = new ResearchSelectionPreview(Optional.of(B),3,0,true,false,true,false,false,
                List.of(new ResearchSelectionPreview.IngredientPreview(List.of(ResourceLocation.parse("minecraft:iron_ingot")),
                        Optional.of(tag),4,1)),1,33,ResearchSelectionPreview.PathPlanningState.NONE,
                ResearchCostMode.POINTS_AND_ITEMS,Optional.of(FP),ResearchAccessSummary.NONE,ResearchSelectionProgressionPreview.EMPTY);
        var material = new ResearchGuidanceSnapshot.MaterialProgress(List.of(),Optional.of(tag),4,1);
        var value = new ResearchGuidanceSnapshot(B,ResearchGuidanceSnapshot.State.MISSING_POINTS_AND_MATERIALS,
                3,0,ResearchCostMode.POINTS_AND_ITEMS,false,true,33,132,33,33,
                java.util.Collections.nCopies(32,material),List.of(B),List.of(B),List.of(),Optional.of(B));
        assertReviewStatus(preview, value, ResearchTreeRouteReviewState.Status.CURRENT);
    }
    @Test void materialPagesCompareAgainstTheirActualGuidanceOffset() {
        var materials = java.util.stream.IntStream.range(0, 40)
                .mapToObj(i -> new ResearchGuidanceSnapshot.MaterialProgress(
                        List.of(ResourceLocation.parse("test:material_" + i)), Optional.empty(), 1, 0)).toList();
        var value = new ResearchGuidanceSnapshot(B, ResearchGuidanceSnapshot.State.MISSING_POINTS_AND_MATERIALS,
                3, 0, ResearchCostMode.POINTS_AND_ITEMS, false, true, 40, 40, 0, 40,
                materials.subList(0, 32), List.of(B), List.of(B), List.of(), Optional.of(B));
        for (int offset : new int[]{0, 6, 30, 36}) {
            var rows = materials.subList(offset, Math.min(40, offset + 6)).stream()
                    .map(m -> new ResearchSelectionPreview.IngredientPreview(m.items(), m.tag(), m.required(), m.allocated())).toList();
            var preview = new ResearchSelectionPreview(Optional.of(B), 3, 0, true, false, true, false, false,
                    rows, 1, 40, ResearchSelectionPreview.PathPlanningState.NONE, ResearchCostMode.POINTS_AND_ITEMS,
                    Optional.of(FP), ResearchAccessSummary.NONE, ResearchSelectionProgressionPreview.EMPTY, offset);
            assertReviewStatus(preview, value, ResearchTreeRouteReviewState.Status.CURRENT);
            if (offset < 32) {
                var stale = new java.util.ArrayList<>(rows);
                stale.set(0, new ResearchSelectionPreview.IngredientPreview(rows.get(0).items(), Optional.empty(), 1, 1));
                var changed = new ResearchSelectionPreview(Optional.of(B), 3, 0, true, false, true, false, false,
                        stale, 1, 40, ResearchSelectionPreview.PathPlanningState.NONE, ResearchCostMode.POINTS_AND_ITEMS,
                        Optional.of(FP), ResearchAccessSummary.NONE, ResearchSelectionProgressionPreview.EMPTY, offset);
                assertReviewStatus(changed, value, ResearchTreeRouteReviewState.Status.UNAVAILABLE);
            }
        }
    }
    private void assertReviewStatus(ResearchSelectionPreview preview, ResearchGuidanceSnapshot value,
            ResearchTreeRouteReviewState.Status expected) {
        var state = new ResearchTreeRouteReviewState();
        state.bind(new ResearchTreeRouteReviewState.Binding(7,10,B,preview,0));
        var request = state.request(0).orElseThrow();
        assertTrue(state.accept(new ResearchRouteReviewResultPacket(request,new ResearchBenchMenu.GuidanceResult(
                ResearchBenchMenu.GuidanceResultCode.SUCCESS,Optional.of(value))),graph(),1));
        assertEquals(expected,state.status());
    }

    @Test void disabledCurrencyDoesNotInvalidateAnItemsOnlyReview() {
        for (var mode : ResearchCostMode.values()) {
            int cost=mode.pointsEnabled()?1:0;
            var preview=new ResearchSelectionPreview(Optional.of(B),cost,3,true,true,true,true,false,List.of(),1,0,
                    ResearchSelectionPreview.PathPlanningState.NONE,mode,Optional.of(FP),ResearchAccessSummary.NONE,ResearchSelectionProgressionPreview.EMPTY);
            var value=new ResearchGuidanceSnapshot(B,ResearchGuidanceSnapshot.State.AFFORDABLE,cost,mode.pointsEnabled()?3:0,
                    mode,false,true,0,List.of(),List.of(B),List.of(B),List.of(),Optional.of(B));
            var state=new ResearchTreeRouteReviewState();state.bind(new ResearchTreeRouteReviewState.Binding(7,10,B,preview,0));
            var request=state.request(0).orElseThrow();
            assertTrue(state.accept(new ResearchRouteReviewResultPacket(request,new ResearchBenchMenu.GuidanceResult(
                    ResearchBenchMenu.GuidanceResultCode.SUCCESS,Optional.of(value))),graph(),1));
            assertEquals(ResearchTreeRouteReviewState.Status.CURRENT,state.status(),mode.toString());
        }
    }

}
