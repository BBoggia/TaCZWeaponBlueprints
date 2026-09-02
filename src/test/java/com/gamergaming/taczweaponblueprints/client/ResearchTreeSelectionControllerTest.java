package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeSelectionControllerTest {
    @Test
    void localFocusAndAuthoritativeSelectionRemainSeparate() {
        ResearchTreeGraph graph = ResearchTreeUxPhaseZeroFixture.everyAvailability();
        ResearchTreeSelectionController controller = new ResearchTreeSelectionController();

        assertFalse(controller.resolve(
                graph,
                graph.nodes().stream()
                        .filter(node -> node.availability() == ResearchTreeGraph.Availability.REDACTED)
                        .findFirst().orElseThrow().blueprintId())
                .orElseThrow().sendAuthoritativeSelection());
        assertTrue(controller.resolve(
                graph,
                new ResourceLocation("phase_zero:preview"))
                .orElseThrow().sendAuthoritativeSelection());
        assertTrue(controller.resolve(
                graph,
                new ResourceLocation("phase_zero:available"))
                .orElseThrow().sendAuthoritativeSelection());
        assertTrue(controller.resolve(graph, new ResourceLocation("test:missing")).isEmpty());
    }

    @Test
    void malformedSelectionStateIsRejected() {
        ResearchTreeSelectionController controller = new ResearchTreeSelectionController();
        assertThrows(IllegalArgumentException.class,
                () -> controller.resolve(null, new ResourceLocation("test:node")));
    }
}
