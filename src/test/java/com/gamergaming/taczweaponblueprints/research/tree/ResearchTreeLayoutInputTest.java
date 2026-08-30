package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeLayoutInputTest {
    @Test
    void publicationAdapterPreservesThePhaseOneLayoutExactly() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.connectedProgression();
        ResearchTreeLayoutInput input = ResearchTreeLayoutInput.from(publication);

        assertEquals(
                ResearchTreeLayeredLayoutEngine.layout(
                        publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW),
                ResearchTreeLayeredLayoutEngine.layoutInput(
                        input, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));
        assertEquals(publication.graph().nodes().stream()
                        .map(ResearchTreeGraph.Node::blueprintId)
                        .toList(),
                input.nodes().stream().map(ResearchTreeLayoutInput.Node::nodeId).toList());
    }

    @Test
    void componentHintsGroupNodesWithoutInventingPrerequisiteEdges() {
        ResearchTreeLayoutInput input = new ResearchTreeLayoutInput(
                List.of(
                        node(0, "lower", 0, 0, 0),
                        node(1, "upper", 1, 0, 0)),
                List.of());

        ResearchTreeLayout layout = ResearchTreeLayeredLayoutEngine.layoutInput(
                input, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);

        assertEquals(0, layout.position(id("lower")).orElseThrow().component());
        assertEquals(0, layout.position(id("upper")).orElseThrow().component());
        assertTrue(layout.position(id("lower")).orElseThrow().y()
                > layout.position(id("upper")).orElseThrow().y());
        assertTrue(input.edges().isEmpty(), "visual affinity must not become a progression edge");
    }

    @Test
    void malformedInputsFailBeforeEnteringTheLayoutKernel() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeLayoutInput(
                        List.of(node(1, "wrong_ordinal", 0, 0, 0)), List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeLayoutInput(
                        List.of(
                                node(0, "duplicate", 0, 0, 0),
                                node(1, "duplicate", 1, 0, 0)),
                        List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeLayoutInput(
                        List.of(node(0, "known", 0, 0, 0)),
                        List.of(new ResearchTreeLayoutInput.Edge(
                                id("missing"), id("known")))));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeLayoutInput(
                        List.of(
                                node(0, "first", 0, 0, 0),
                                node(1, "second", 1, 0, 0)),
                        List.of(
                                new ResearchTreeLayoutInput.Edge(id("first"), id("second")),
                                new ResearchTreeLayoutInput.Edge(id("second"), id("first")))));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTreeLayoutInput(
                        List.of(
                                node(0, "lower_rank", 0, 0, 0),
                                node(1, "higher_rank", 1, 0, 0)),
                        List.of(new ResearchTreeLayoutInput.Edge(
                                id("higher_rank"), id("lower_rank")))));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTreeLayeredLayoutEngine.layoutInput(
                        null, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW));
    }

    private static ResearchTreeLayoutInput.Node node(
            int ordinal,
            String path,
            int rank,
            int orderInRank,
            int componentHint) {
        return new ResearchTreeLayoutInput.Node(
                ordinal, id(path), rank, 0, orderInRank, componentHint);
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation("phase_two", path);
    }
}
