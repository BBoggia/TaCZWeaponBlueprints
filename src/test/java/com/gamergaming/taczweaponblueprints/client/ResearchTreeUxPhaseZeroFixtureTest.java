package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

class ResearchTreeUxPhaseZeroFixtureTest {
    @Test
    void fixtureRepresentsEveryDisclosureSafeAvailabilityExactlyOnce() {
        ResearchTreeGraph graph = ResearchTreeUxPhaseZeroFixture.everyAvailability();
        Map<ResearchTreeGraph.Availability, ResearchTreeGraph.Node> nodes = graph.nodes().stream()
                .collect(Collectors.toMap(
                        ResearchTreeGraph.Node::availability,
                        Function.identity()));

        assertEquals(ResearchTreeGraph.Availability.values().length, graph.nodes().size());
        assertEquals(ResearchTreeGraph.Availability.values().length, nodes.size());
        for (ResearchTreeGraph.Availability availability
                : ResearchTreeGraph.Availability.values()) {
            assertTrue(nodes.containsKey(availability));
        }
    }

    @Test
    void exactPreviewFixturesKeepSelectionSeparateFromTransactionReadiness() {
        ResearchSelectionPreview ready = ResearchTreeUxPhaseZeroFixture.readyPreview();
        ResearchSelectionPreview points =
                ResearchTreeUxPhaseZeroFixture.insufficientPointsPreview();
        ResearchSelectionPreview materials =
                ResearchTreeUxPhaseZeroFixture.missingMaterialsPreview();
        ResearchSelectionPreview output = ResearchTreeUxPhaseZeroFixture.outputFullPreview();
        ResearchSelectionPreview policy = ResearchTreeUxPhaseZeroFixture.lockedPolicyPreview();

        assertTrue(ready.researchable());
        assertTrue(ready.ingredientsSatisfied());
        assertTrue(ready.outputSpace());
        assertTrue(ready.pointBalance() >= ready.pointCost());

        assertFalse(points.researchable());
        assertTrue(points.ingredientsSatisfied());
        assertTrue(points.pointBalance() < points.pointCost());

        assertFalse(materials.researchable());
        assertFalse(materials.ingredientsSatisfied());
        assertTrue(materials.pointBalance() >= materials.pointCost());

        assertFalse(output.researchable());
        assertFalse(output.outputSpace());

        assertFalse(policy.researchable());
        assertFalse(policy.policyEligible());
    }
}
