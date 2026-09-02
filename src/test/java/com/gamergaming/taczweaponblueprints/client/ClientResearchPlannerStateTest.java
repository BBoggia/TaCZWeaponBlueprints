package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ClientResearchPlannerStateTest {
    @AfterEach
    void clearSharedState() {
        ClientResearchPlannerState.clear();
    }

    @Test
    void trackedTargetSurvivesCompatibleReloadsAndClearsWhenRemoved() {
        ResourceLocation target = id("test:target");
        ResearchTreeGraph graph = graph(target, JournalVisibility.FULL);

        assertTrue(ClientResearchPlannerState.track(graph, target));
        assertEquals(target, ClientResearchPlannerState.targetId().orElseThrow());

        ClientResearchPlannerState.retain(graph(target, JournalVisibility.PREVIEW));
        assertEquals(target, ClientResearchPlannerState.targetId().orElseThrow());

        ClientResearchPlannerState.retain(ResearchTreeGraph.EMPTY);
        assertTrue(ClientResearchPlannerState.targetId().isEmpty());
    }

    @Test
    void anonymousTargetsAreRejectedWithoutReplacingTheCurrentGoal() {
        ResourceLocation target = id("test:target");
        assertTrue(ClientResearchPlannerState.track(
                graph(target, JournalVisibility.FULL), target));

        ResourceLocation anonymous = ResearchTreeGraph.redactedNodeId(0);
        assertFalse(ClientResearchPlannerState.track(
                graph(anonymous, JournalVisibility.SILHOUETTE), anonymous));
        assertEquals(target, ClientResearchPlannerState.targetId().orElseThrow());
    }

    private static ResearchTreeGraph graph(
            ResourceLocation blueprintId,
            JournalVisibility visibility) {
        return new ResearchTreeGraph(
                List.of(new ResearchTreeGraph.Node(
                        0,
                        blueprintId,
                        visibility.revealsName()
                                ? "name.target"
                                : ResearchTreeGraph.REDACTED_NAME_KEY,
                        visibility.revealsIdentity()
                                ? "gun"
                                : ResearchTreeGraph.REDACTED_ITEM_TYPE,
                        visibility.revealsIdentity()
                                ? id("test:slot/target")
                                : ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                        visibility,
                        false,
                        visibility.revealsExactPolicy(),
                        visibility.revealsExactPolicy(),
                        visibility.revealsResearchSummary() ? 4 : 0,
                        visibility.revealsResearchSummary() ? 1 : 0,
                        0,
                        0,
                        visibility.revealsExactPolicy()
                                ? ResearchTreeGraph.Availability.AVAILABLE
                                : visibility.revealsResearchSummary()
                                        ? ResearchTreeGraph.Availability.PREVIEW
                                        : ResearchTreeGraph.Availability.REDACTED)),
                List.of());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
