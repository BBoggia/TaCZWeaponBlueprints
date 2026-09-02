package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchAffordabilitySnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

/** Cross-feature lifecycle acceptance for the completed research-guidance layer. */
class ResearchGuidancePhaseSevenIntegrationTest {
    private static final ResourceLocation TARGET = id("test:target");
    private static final ResourceLocation OTHER = id("test:other");

    @AfterEach
    void clearConnectionLocalState() {
        ClientResearchPlannerState.clear();
        ClientResearchGuidanceState.clear();
        ClientResearchAffordabilityState.clear();
    }

    @Test
    void publicationAdvanceRetainsPublicGoalButInvalidatesLiveAuthorityAndEconomy() {
        ResearchTreeGraph graph = graph(TARGET, OTHER);
        assertTrue(ClientResearchPlannerState.track(graph, TARGET));
        acceptUnavailableGuidance(graph, 12L);
        completeAffordabilitySweep(graph, 12L);

        ClientResearchPlannerState.retain(graph);
        ClientResearchGuidanceState.retain(graph, 13L);
        ClientResearchAffordabilityState.retain(graph, 13L);

        assertEquals(Optional.of(TARGET), ClientResearchPlannerState.targetId());
        assertTrue(ClientResearchGuidanceState.snapshot().isEmpty());
        assertFalse(ClientResearchGuidanceState.pending());
        ClientResearchAffordabilityState.Snapshot affordability =
                ClientResearchAffordabilityState.snapshot();
        assertTrue(affordability.enabled());
        assertEquals(13L, affordability.publicationGeneration());
        assertEquals(0, affordability.checkedTargets());
        assertEquals(2, affordability.totalTargets());
    }

    @Test
    void disclosureRemovalClearsGoalAndCannotRetainItsGuidanceOrClassification() {
        ResearchTreeGraph original = graph(TARGET, OTHER);
        assertTrue(ClientResearchPlannerState.track(original, TARGET));
        acceptUnavailableGuidance(original, 12L);
        completeAffordabilitySweep(original, 12L);

        ResearchTreeGraph replacement = graph(OTHER);
        ClientResearchPlannerState.retain(replacement);
        ClientResearchGuidanceState.retain(replacement, 13L);
        ClientResearchAffordabilityState.retain(replacement, 13L);

        assertTrue(ClientResearchPlannerState.targetId().isEmpty());
        assertTrue(ClientResearchGuidanceState.snapshot().isEmpty());
        assertFalse(ClientResearchGuidanceState.pending());
        ClientResearchAffordabilityState.Snapshot affordability =
                ClientResearchAffordabilityState.snapshot();
        assertEquals(1, affordability.totalTargets());
        assertEquals(0, affordability.checkedTargets());
        assertFalse(affordability.results().containsKey(TARGET));
    }

    private static void acceptUnavailableGuidance(ResearchTreeGraph graph, long generation) {
        ClientResearchGuidanceState.Request request =
                ClientResearchGuidanceState.begin(graph, TARGET, generation).orElseThrow();
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
                generation,
                unavailable,
                publication(generation, graph)));
    }

    private static void completeAffordabilitySweep(ResearchTreeGraph graph, long generation) {
        ClientResearchAffordabilityState.setEnabled(true, graph, generation);
        ClientResearchAffordabilityState.Request request =
                ClientResearchAffordabilityState.beginNext(graph, generation).orElseThrow();
        assertEquals(ClientResearchAffordabilityState.ResponseOutcome.ACCEPTED,
                ClientResearchAffordabilityState.accept(
                        request.requestId(),
                        generation,
                        new ResearchAffordabilitySnapshot(request.targetIds().stream()
                                .map(targetId -> new ResearchAffordabilitySnapshot.Entry(
                                        targetId,
                                        ResearchGuidanceSnapshot.State.AFFORDABLE,
                                        true))
                                .toList()),
                        publication(generation, graph)));
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

    private static ResearchTreeGraph graph(ResourceLocation... ids) {
        java.util.ArrayList<ResearchTreeGraph.Node> nodes = new java.util.ArrayList<>();
        for (int ordinal = 0; ordinal < ids.length; ordinal++) {
            ResourceLocation blueprintId = ids[ordinal];
            nodes.add(new ResearchTreeGraph.Node(
                    ordinal,
                    blueprintId,
                    "name." + blueprintId.getPath(),
                    "gun",
                    id("test:slot/" + blueprintId.getPath()),
                    JournalVisibility.FULL,
                    false,
                    true,
                    true,
                    1,
                    0,
                    0,
                    0,
                    ResearchTreeGraph.Availability.AVAILABLE));
        }
        return new ResearchTreeGraph(nodes, List.of());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
