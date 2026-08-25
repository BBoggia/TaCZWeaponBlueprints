package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutEngine;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeNavigatorTest {
    @Test
    void upAndDownPreferConnectedNodesWhileSidesFollowTheTier() {
        ResearchTreeGraph graph = graph();
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);

        assertEquals(id("test:b"), move(graph, layout, "test:a", ResearchTreeNavigator.Direction.UP));
        assertEquals(id("test:a"), move(graph, layout, "test:b", ResearchTreeNavigator.Direction.DOWN));
        assertEquals(id("test:c"), move(graph, layout, "test:b", ResearchTreeNavigator.Direction.RIGHT));
        assertEquals(id("test:b"), move(graph, layout, "test:c", ResearchTreeNavigator.Direction.LEFT));
    }

    @Test
    void mergingNodesChooseTheClosestParentDeterministically() {
        ResearchTreeGraph graph = graph();
        ResearchTreeLayout layout = ResearchTreeLayoutEngine.layout(graph);

        ResourceLocation first = move(graph, layout, "test:d", ResearchTreeNavigator.Direction.DOWN);
        for (int repeat = 0; repeat < 20; repeat++) {
            assertEquals(first, move(graph, layout, "test:d", ResearchTreeNavigator.Direction.DOWN));
        }
        assertTrue(first.equals(id("test:b")) || first.equals(id("test:c")));
    }

    @Test
    void invalidOrEmptyInputsFailClosed() {
        assertTrue(ResearchTreeNavigator.move(
                ResearchTreeGraph.EMPTY,
                ResearchTreeLayout.EMPTY,
                id("test:missing"),
                ResearchTreeNavigator.Direction.DOWN).isEmpty());
        assertTrue(ResearchTreeNavigator.move(null, null, null, null).isEmpty());
    }

    private static ResourceLocation move(
            ResearchTreeGraph graph,
            ResearchTreeLayout layout,
            String current,
            ResearchTreeNavigator.Direction direction) {
        return ResearchTreeNavigator.move(graph, layout, id(current), direction).orElseThrow();
    }

    private static ResearchTreeGraph graph() {
        return new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0),
                        node(1, "test:b", 1),
                        node(2, "test:c", 1),
                        node(3, "test:d", 2)),
                List.of(
                        edge("test:a", "test:b"),
                        edge("test:a", "test:c"),
                        edge("test:b", "test:d"),
                        edge("test:c", "test:d")));
    }

    private static ResearchTreeGraph.Node node(int ordinal, String value, int prerequisites) {
        ResourceLocation id = id(value);
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "name." + id.getPath(),
                "rifle",
                id("test:slot/" + ordinal),
                JournalVisibility.FULL,
                false,
                false,
                prerequisites == 0,
                4 + prerequisites * 2,
                0,
                prerequisites,
                0,
                prerequisites == 0
                        ? ResearchTreeGraph.Availability.AVAILABLE
                        : ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResearchTreeGraph.Edge edge(String prerequisite, String dependent) {
        return new ResearchTreeGraph.Edge(id(prerequisite), id(dependent));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
