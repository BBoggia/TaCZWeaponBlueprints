package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchAffordabilitySnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ClientResearchAffordabilityStateTest {
    @AfterEach
    void clearSharedState() {
        ClientResearchAffordabilityState.clear();
    }

    @Test
    void queuedAcknowledgementRenewsThePendingLeaseWithoutLosingRetryHistory() {
        ResearchTreeGraph graph = graph(3);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);
        ClientResearchAffordabilityState.Request request =
                ClientResearchAffordabilityState.beginNext(graph, 12L, 100L)
                        .orElseThrow();

        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.ACKNOWLEDGED,
                ClientResearchAffordabilityState.acknowledge(
                        request.requestId(), 12L, 900L));
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.IGNORED,
                ClientResearchAffordabilityState.expirePending(1_899L, 1_000L));
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.RETRY,
                ClientResearchAffordabilityState.expirePending(1_900L, 1_000L));

        ClientResearchAffordabilityState.Request retry =
                ClientResearchAffordabilityState.beginNext(graph, 12L, 1_900L)
                        .orElseThrow();
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.RETRY,
                ClientResearchAffordabilityState.throttle(retry.requestId(), 12L));
        ClientResearchAffordabilityState.Request afterThrottle =
                ClientResearchAffordabilityState.beginNext(graph, 12L, 2_000L)
                        .orElseThrow();
        assertTrue(afterThrottle.requestId() > retry.requestId());
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.ADVANCED_AFTER_FAILURE,
                ClientResearchAffordabilityState.expirePending(3_000L, 1_000L));
    }

    @Test
    void sweepUsesBoundedCorrelatedBatchesAndAccumulatesResults() {
        ResearchTreeGraph graph = graph(11);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);

        ClientResearchAffordabilityState.Request first =
                ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();
        assertEquals(ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH,
                first.targetIds().size());
        assertTrue(ClientResearchAffordabilityState.beginNext(graph, 12L).isEmpty());
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.ACCEPTED,
                ClientResearchAffordabilityState.accept(
                first.requestId(),
                12L,
                result(first.targetIds(), ResearchGuidanceSnapshot.State.AFFORDABLE),
                publication(12L, graph)));

        ClientResearchAffordabilityState.Request second =
                ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();
        assertEquals(3, second.targetIds().size());
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.ACCEPTED,
                ClientResearchAffordabilityState.accept(
                second.requestId(),
                12L,
                result(second.targetIds(), ResearchGuidanceSnapshot.State.MISSING_POINTS),
                publication(12L, graph)));

        ClientResearchAffordabilityState.Snapshot snapshot =
                ClientResearchAffordabilityState.snapshot();
        assertTrue(snapshot.complete());
        assertEquals(11, snapshot.checkedTargets());
        assertEquals(8, snapshot.affordableTargets());
        assertTrue(ClientResearchAffordabilityState.beginNext(graph, 12L).isEmpty());
    }

    @Test
    void inventoryAndPublicationChangesRestartWithoutTurningTheFilterOff() {
        ResearchTreeGraph graph = graph(3);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);
        ClientResearchAffordabilityState.Request request =
                ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.ACCEPTED,
                ClientResearchAffordabilityState.accept(
                request.requestId(),
                12L,
                result(request.targetIds(), ResearchGuidanceSnapshot.State.AFFORDABLE),
                publication(12L, graph)));

        ClientResearchAffordabilityState.invalidateResources(graph, 12L);
        assertTrue(ClientResearchAffordabilityState.snapshot().enabled());
        assertEquals(0, ClientResearchAffordabilityState.snapshot().checkedTargets());

        ClientResearchAffordabilityState.retain(graph, 13L);
        assertEquals(13L, ClientResearchAffordabilityState.snapshot().publicationGeneration());
        assertEquals(0, ClientResearchAffordabilityState.snapshot().checkedTargets());
        assertTrue(ClientResearchAffordabilityState.beginNext(graph, 13L).isPresent());
    }

    @Test
    void mismatchedResponsesNeverAdvanceTheProgressiveCursor() {
        ResearchTreeGraph graph = graph(2);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);
        ClientResearchAffordabilityState.Request request =
                ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();
        List<ResourceLocation> reversed = new ArrayList<>(request.targetIds());
        java.util.Collections.reverse(reversed);

        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.RETRY,
                ClientResearchAffordabilityState.accept(
                request.requestId(),
                12L,
                result(reversed, ResearchGuidanceSnapshot.State.AFFORDABLE),
                publication(12L, graph)));
        assertEquals(0, ClientResearchAffordabilityState.snapshot().checkedTargets());
        assertTrue(ClientResearchAffordabilityState.beginNext(graph, 12L).isPresent());
    }

    @Test
    void staleResponsesCannotReplaceThePendingBatchForANewerPublication() {
        ResearchTreeGraph graph = graph(2);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);
        ClientResearchAffordabilityState.Request stale =
                ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();

        ClientResearchAffordabilityState.retain(graph, 13L);
        ClientResearchAffordabilityState.Request current =
                ClientResearchAffordabilityState.beginNext(graph, 13L).orElseThrow();
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.IGNORED,
                ClientResearchAffordabilityState.accept(
                stale.requestId(),
                12L,
                result(stale.targetIds(), ResearchGuidanceSnapshot.State.AFFORDABLE),
                publication(12L, graph)));
        assertTrue(ClientResearchAffordabilityState.snapshot().pending());
        assertEquals(0, ClientResearchAffordabilityState.snapshot().checkedTargets());

        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.ACCEPTED,
                ClientResearchAffordabilityState.accept(
                current.requestId(),
                13L,
                result(current.targetIds(), ResearchGuidanceSnapshot.State.AFFORDABLE),
                publication(13L, graph)));
        assertTrue(ClientResearchAffordabilityState.snapshot().complete());
    }

    @Test
    void matchingOldRequestIsAbandonedWhenTheLivePublicationHasAdvanced() {
        ResearchTreeGraph graph = graph(2);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);
        ClientResearchAffordabilityState.Request stale =
                ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();

        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.IGNORED,
                ClientResearchAffordabilityState.accept(
                        stale.requestId(),
                        12L,
                        result(stale.targetIds(), ResearchGuidanceSnapshot.State.AFFORDABLE),
                        publication(13L, graph)));

        ClientResearchAffordabilityState.Snapshot reconciled =
                ClientResearchAffordabilityState.snapshot();
        assertTrue(reconciled.enabled());
        assertEquals(13L, reconciled.publicationGeneration());
        assertFalse(reconciled.pending());
        assertEquals(0, reconciled.checkedTargets());
        assertTrue(ClientResearchAffordabilityState.beginNext(graph, 13L).isPresent());
    }

    @Test
    void malformedMatchingResultCannotLeaveTheSweepPermanentlyPending() {
        ResearchTreeGraph graph = graph(2);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);
        ClientResearchAffordabilityState.Request request =
                ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();

        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.RETRY,
                ClientResearchAffordabilityState.accept(
                        request.requestId(), 12L, null, publication(12L, graph)));
        assertFalse(ClientResearchAffordabilityState.snapshot().pending());
        assertTrue(ClientResearchAffordabilityState.beginNext(graph, 12L).isPresent());
    }

    @Test
    void lostBatchResponseRetriesOnceThenAdvancesAsUnavailable() {
        ResearchTreeGraph graph = graph(2);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);
        ClientResearchAffordabilityState.Request first =
                ClientResearchAffordabilityState.beginNext(graph, 12L, 100L)
                        .orElseThrow();

        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.IGNORED,
                ClientResearchAffordabilityState.expirePending(1_099L, 1_000L));
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.RETRY,
                ClientResearchAffordabilityState.expirePending(1_100L, 1_000L));
        assertFalse(ClientResearchAffordabilityState.snapshot().pending());

        ClientResearchAffordabilityState.Request retry =
                ClientResearchAffordabilityState.beginNext(graph, 12L, 1_100L)
                        .orElseThrow();
        assertTrue(retry.requestId() > first.requestId());
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.IGNORED,
                ClientResearchAffordabilityState.accept(
                        first.requestId(),
                        12L,
                        result(first.targetIds(), ResearchGuidanceSnapshot.State.AFFORDABLE),
                        publication(12L, graph)));
        assertEquals(
                ClientResearchAffordabilityState.ResponseOutcome.ADVANCED_AFTER_FAILURE,
                ClientResearchAffordabilityState.expirePending(2_100L, 1_000L));

        ClientResearchAffordabilityState.Snapshot snapshot =
                ClientResearchAffordabilityState.snapshot();
        assertTrue(snapshot.complete());
        assertEquals(2, snapshot.checkedTargets());
        assertEquals(0, snapshot.affordableTargets());
        assertTrue(snapshot.results().values().stream().allMatch(entry ->
                entry.state() == ResearchGuidanceSnapshot.State.ROUTE_UNAVAILABLE));
    }

    @Test
    void disablingAndDisconnectClearingRemoveEveryResultAndPendingRequest() {
        ResearchTreeGraph graph = graph(1);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);
        ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();

        ClientResearchAffordabilityState.setEnabled(false, graph, 12L);

        ClientResearchAffordabilityState.Snapshot snapshot =
                ClientResearchAffordabilityState.snapshot();
        assertFalse(snapshot.enabled());
        assertEquals(0, snapshot.totalTargets());
        assertTrue(snapshot.results().isEmpty());
    }

    @Test
    void snapshotsAreReusedUntilTheUnderlyingStateChanges() {
        ResearchTreeGraph graph = graph(2);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);

        ClientResearchAffordabilityState.Snapshot first =
                ClientResearchAffordabilityState.snapshot();
        assertSame(first, ClientResearchAffordabilityState.snapshot());
        ClientResearchAffordabilityState.retain(graph, 12L);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);
        assertSame(first, ClientResearchAffordabilityState.snapshot());

        ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();
        ClientResearchAffordabilityState.Snapshot pending =
                ClientResearchAffordabilityState.snapshot();
        assertSame(pending, ClientResearchAffordabilityState.snapshot());
        assertFalse(first == pending);
    }

    @Test
    void equivalentReplacementGraphRetainsProgressWhileChangedTargetsRestartIt() {
        ResearchTreeGraph firstGraph = graph(2);
        ClientResearchAffordabilityState.setEnabled(true, firstGraph, 12L);
        ClientResearchAffordabilityState.Request request =
                ClientResearchAffordabilityState.beginNext(firstGraph, 12L).orElseThrow();
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.ACCEPTED,
                ClientResearchAffordabilityState.accept(
                        request.requestId(),
                        12L,
                        result(request.targetIds(), ResearchGuidanceSnapshot.State.AFFORDABLE),
                        publication(12L, firstGraph)));

        ResearchTreeGraph equivalentGraph = graph(2);
        ClientResearchAffordabilityState.retain(equivalentGraph, 12L);
        assertTrue(ClientResearchAffordabilityState.snapshot().complete());

        ResearchTreeGraph changedGraph = graph(3);
        ClientResearchAffordabilityState.retain(changedGraph, 12L);
        assertEquals(0, ClientResearchAffordabilityState.snapshot().checkedTargets());
        assertEquals(3, ClientResearchAffordabilityState.snapshot().totalTargets());
    }

    @Test
    void repeatedRejectedBatchesBecomeUnavailableAndTheSweepContinues() {
        ResearchTreeGraph graph = graph(2);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);

        for (int attempt = 1;
                attempt <= ClientResearchAffordabilityState.MAX_BATCH_FAILURES;
                attempt++) {
            ClientResearchAffordabilityState.Request request =
                    ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();
            ClientResearchAffordabilityState.ResponseOutcome outcome =
                    ClientResearchAffordabilityState.reject(request.requestId(), 12L);
            assertEquals(
                    attempt == ClientResearchAffordabilityState.MAX_BATCH_FAILURES
                            ? ClientResearchAffordabilityState.ResponseOutcome
                                    .ADVANCED_AFTER_FAILURE
                            : ClientResearchAffordabilityState.ResponseOutcome.RETRY,
                    outcome);
        }

        ClientResearchAffordabilityState.Snapshot snapshot =
                ClientResearchAffordabilityState.snapshot();
        assertTrue(snapshot.complete());
        assertEquals(2, snapshot.checkedTargets());
        assertEquals(0, snapshot.affordableTargets());
    }

    @Test
    void maximumPublicationStillProducesOnlyOneBoundedRequestBatch() {
        ResearchTreeGraph graph = graph(ResearchTreeGraph.MAX_NODES);
        ClientResearchAffordabilityState.setEnabled(true, graph, 12L);

        ClientResearchAffordabilityState.Snapshot initial =
                ClientResearchAffordabilityState.snapshot();
        assertEquals(ResearchTreeGraph.MAX_NODES, initial.totalTargets());
        ClientResearchAffordabilityState.Request first =
                ClientResearchAffordabilityState.beginNext(graph, 12L).orElseThrow();
        assertEquals(ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH,
                first.targetIds().size());

        ClientResearchAffordabilityState.retain(graph, 12L);
        assertTrue(ClientResearchAffordabilityState.snapshot().pending());
        assertEquals(0, ClientResearchAffordabilityState.snapshot().checkedTargets());
    }

    private static ResearchAffordabilitySnapshot result(
            List<ResourceLocation> targets,
            ResearchGuidanceSnapshot.State state) {
        return new ResearchAffordabilitySnapshot(targets.stream()
                .map(id -> new ResearchAffordabilitySnapshot.Entry(id, state, true))
                .toList());
    }

    private static ResearchTreeGraph graph(int nodeCount) {
        ArrayList<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        for (int index = 0; index < nodeCount; index++) {
            ResourceLocation id = id("test:node_" + index);
            nodes.add(new ResearchTreeGraph.Node(
                    index,
                    id,
                    "name.test.node_" + index,
                    "gun",
                    id("test:slot/node_" + index),
                    JournalVisibility.FULL,
                    false,
                    true,
                    index == 0,
                    1,
                    0,
                    0,
                    0,
                    index == 0
                            ? ResearchTreeGraph.Availability.AVAILABLE
                            : ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED));
        }
        return new ResearchTreeGraph(nodes, List.of());
    }

    private static ClientResearchState.Publication publication(
            long generation,
            ResearchTreeGraph graph) {
        return new ClientResearchState.Publication(
                generation,
                BlueprintJournalSnapshot.EMPTY,
                graph,
                ResearchTreePresentation.EMPTY);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
