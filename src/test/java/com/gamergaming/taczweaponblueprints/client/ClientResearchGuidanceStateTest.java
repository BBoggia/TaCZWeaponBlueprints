package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchPathUnlockPlanner;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ClientResearchGuidanceStateTest {
    private static final ResourceLocation ROOT = id("test:root");
    private static final ResourceLocation OTHER = id("test:other");
    private static final ResourceLocation TARGET = id("test:target");

    @AfterEach
    void clearSharedState() {
        ClientResearchGuidanceState.clear();
        ClientResearchPlannerState.clear();
    }

    @Test
    void onlyOneRequestIsCreatedForTheSameTargetAndPublication() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));

        ClientResearchGuidanceState.Request request =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();

        assertEquals(TARGET, request.targetId());
        assertTrue(ClientResearchGuidanceState.pending());
        assertTrue(ClientResearchGuidanceState.begin(graph, TARGET, 12L).isEmpty());
    }

    @Test
    void closingTheBenchAbandonsPendingWorkSoAReopenCanRetry() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        ClientResearchGuidanceState.Request first =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();

        ClientResearchGuidanceState.abandonPending();

        ClientResearchGuidanceState.Request reopened =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();
        assertTrue(reopened.requestId() > first.requestId());
    }

    @Test
    void lostGuidanceResponseRetriesOnceThenBecomesUnavailable() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        ClientResearchGuidanceState.Request first =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L, 100L)
                        .orElseThrow();

        assertEquals(ClientResearchGuidanceState.TimeoutOutcome.NONE,
                ClientResearchGuidanceState.expirePending(1_099L, 1_000L));
        assertEquals(ClientResearchGuidanceState.TimeoutOutcome.RETRY,
                ClientResearchGuidanceState.expirePending(1_100L, 1_000L));
        assertFalse(ClientResearchGuidanceState.pending());

        ClientResearchGuidanceState.Request retry =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L, 1_100L)
                        .orElseThrow();
        assertTrue(retry.requestId() > first.requestId());
        assertFalse(ClientResearchGuidanceState.accept(
                first.requestId(), 12L, snapshot(ROOT), publication(12L, graph)));
        assertEquals(ClientResearchGuidanceState.TimeoutOutcome.UNAVAILABLE,
                ClientResearchGuidanceState.expirePending(2_100L, 1_000L));
        assertFalse(ClientResearchGuidanceState.pending());
        assertTrue(ClientResearchGuidanceState.unavailable());
        assertTrue(ClientResearchGuidanceState.begin(
                graph, TARGET, 12L, 2_100L).isEmpty());

        ClientResearchGuidanceState.invalidateResources();
        assertTrue(ClientResearchGuidanceState.begin(
                graph, TARGET, 12L, 2_101L).isPresent());
    }

    @Test
    void permanentRejectionIsTerminalUntilRelevantStateChanges() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        ClientResearchGuidanceState.Request request =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();

        assertTrue(ClientResearchGuidanceState.reject(
                request.requestId(), 12L, true));
        assertTrue(ClientResearchGuidanceState.unavailable());
        assertTrue(ClientResearchGuidanceState.begin(graph, TARGET, 12L).isEmpty());

        ClientResearchGuidanceState.invalidateResources();
        assertFalse(ClientResearchGuidanceState.unavailable());
        assertTrue(ClientResearchGuidanceState.begin(graph, TARGET, 12L).isPresent());
    }

    @Test
    void staleAndMismatchedResponsesCannotReplaceCurrentGuidance() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        ClientResearchGuidanceState.Request request =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();

        assertFalse(ClientResearchGuidanceState.accept(
                request.requestId(),
                11L,
                snapshot(ROOT),
                publication(12L, graph)));
        assertFalse(ClientResearchGuidanceState.accept(
                request.requestId(),
                12L,
                snapshot(ROOT),
                publication(13L, graph)));
        request = ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();
        assertTrue(ClientResearchGuidanceState.accept(
                request.requestId(),
                12L,
                snapshot(ROOT),
                publication(12L, graph)));
        assertEquals(snapshot(ROOT),
                ClientResearchGuidanceState.snapshot().orElseThrow());
    }

    @Test
    void selectedEdgesMustExistInTheMatchingPublicRequirementGroup() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        ClientResearchGuidanceState.Request request =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();

        assertFalse(ClientResearchGuidanceState.accept(
                request.requestId(),
                12L,
                snapshot(OTHER),
                publication(12L, graph)));
        assertTrue(ClientResearchGuidanceState.snapshot().isEmpty());
    }

    @Test
    void publicationChangesInvalidatePendingAndAcceptedGuidance() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        ClientResearchGuidanceState.Request request =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();
        assertTrue(ClientResearchGuidanceState.accept(
                request.requestId(),
                12L,
                snapshot(ROOT),
                publication(12L, graph)));

        ClientResearchGuidanceState.retain(graph, 13L);

        assertTrue(ClientResearchGuidanceState.snapshot().isEmpty());
        assertFalse(ClientResearchGuidanceState.pending());
    }

    @Test
    void matchingOldResponseImmediatelyDropsAcceptedGuidanceAfterPublicationAdvance() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        ClientResearchGuidanceState.Request acceptedRequest =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();
        assertTrue(ClientResearchGuidanceState.accept(
                acceptedRequest.requestId(),
                12L,
                snapshot(ROOT),
                publication(12L, graph)));

        ClientResearchGuidanceState.invalidateResources();
        ClientResearchGuidanceState.Request staleRefresh =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();
        assertFalse(ClientResearchGuidanceState.accept(
                staleRefresh.requestId(),
                12L,
                snapshot(ROOT),
                publication(13L, graph)));

        assertTrue(ClientResearchGuidanceState.snapshot().isEmpty());
        assertTrue(ClientResearchGuidanceState.currentSnapshot().isEmpty());
        assertFalse(ClientResearchGuidanceState.pending());
    }

    @Test
    void boundedUnavailableResultReplacesTheClientEstimate() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        ClientResearchGuidanceState.Request request =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();
        ResearchGuidanceSnapshot unavailable = new ResearchGuidanceSnapshot(
                TARGET,
                ResearchGuidanceSnapshot.State.ROUTE_UNAVAILABLE,
                0,
                5,
                ResearchCostMode.POINTS_AND_ITEMS,
                false,
                true,
                0,
                List.of(),
                List.of(TARGET),
                List.of(),
                List.of(),
                Optional.empty());

        assertTrue(ClientResearchGuidanceState.accept(
                request.requestId(),
                12L,
                unavailable,
                publication(12L, graph)));
        assertEquals(unavailable, ClientResearchGuidanceState.snapshot().orElseThrow());
    }

    @Test
    void resourceInvalidationRetainsTheRouteAndCorrelatesAReplacementRequest() {
        ResearchTreeGraph graph = graph();
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        ClientResearchGuidanceState.Request first =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();
        assertTrue(ClientResearchGuidanceState.accept(
                first.requestId(),
                12L,
                snapshot(ROOT),
                publication(12L, graph)));

        ClientResearchGuidanceState.invalidateResources();

        assertEquals(snapshot(ROOT), ClientResearchGuidanceState.snapshot().orElseThrow());
        assertTrue(ClientResearchGuidanceState.currentSnapshot().isEmpty());
        ClientResearchGuidanceState.Request replacement =
                ClientResearchGuidanceState.begin(graph, TARGET, 12L).orElseThrow();
        assertTrue(replacement.requestId() > first.requestId());
        assertFalse(ClientResearchGuidanceState.accept(
                first.requestId(),
                12L,
                snapshot(ROOT),
                publication(12L, graph)));
        assertTrue(ClientResearchGuidanceState.accept(
                replacement.requestId(),
                12L,
                snapshot(ROOT),
                publication(12L, graph)));
        assertEquals(snapshot(ROOT),
                ClientResearchGuidanceState.currentSnapshot().orElseThrow());
    }

    private static ResearchGuidanceSnapshot snapshot(ResourceLocation prerequisite) {
        return new ResearchGuidanceSnapshot(
                TARGET,
                ResearchGuidanceSnapshot.State.AFFORDABLE,
                3,
                5,
                ResearchCostMode.POINTS_AND_ITEMS,
                false,
                true,
                0,
                List.of(),
                List.of(ROOT, OTHER, TARGET),
                List.of(TARGET),
                List.of(new ResearchPathUnlockPlanner.SelectedRequirement(
                        TARGET, 0, prerequisite)),
                Optional.of(TARGET));
    }

    private static ResearchTreeGraph graph() {
        return ResearchTreeGraph.withRequirementGroups(
                List.of(node(0, ROOT), node(1, OTHER), node(2, TARGET)),
                List.of(new ResearchTreeGraph.RequirementGroup(
                        TARGET, 0, List.of(ROOT), 0, false)));
    }

    private static ResearchTreeGraph.Node node(int ordinal, ResourceLocation id) {
        boolean learned = !id.equals(TARGET);
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "name." + id.getPath(),
                "gun",
                id("test:slot/" + id.getPath()),
                JournalVisibility.FULL,
                learned,
                true,
                !learned,
                1,
                0,
                id.equals(TARGET) ? 1 : 0,
                0,
                learned
                        ? ResearchTreeGraph.Availability.LEARNED
                        : ResearchTreeGraph.Availability.AVAILABLE);
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
