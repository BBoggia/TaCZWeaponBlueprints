package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import net.minecraft.resources.ResourceLocation;

class ResearchTreeSubgraphTest {
    @Test void reorderedSubsetsRetainCanonicalChoiceGroupsAndDisclosureCounts() {
        var a = id(0); var b = id(1); var c = id(2); var d = id(3);
        var graph = ResearchTreeGraph.withRequirementGroups(
                List.of(node(0, 0, 0), node(1, 0, 0), node(2, 0, 0), node(3, 3, 1)),
                List.of(new ResearchTreeGraph.RequirementGroup(d, 0, List.of(b, a), 1, 1, true, true),
                        new ResearchTreeGraph.RequirementGroup(d, 1, List.of(c, b), 0, 0, true, false)));
        for (int mask = 0; mask < 16; mask++) {
            var order = new ArrayList<ResourceLocation>();
            for (int i = 3; i >= 0; i--) if ((mask & (1 << i)) != 0) order.add(id(i));
            var selected = Set.copyOf(order);
            var projection = graph.orderedInducedSubgraph(order);
            assertEquals(order, projection.nodes().stream().map(ResearchTreeGraph.Node::blueprintId).toList());
            assertEquals(graph.edges().stream().filter(e -> selected.contains(e.dependentId()) && selected.contains(e.prerequisiteId())).toList(), projection.edges());
            var expectedGroups = graph.requirementGroups().stream().filter(g -> selected.contains(g.dependentId())).map(g -> {
                var visible = g.visibleAlternativeIds().stream().filter(selected::contains).toList();
                return new ResearchTreeGraph.RequirementGroup(g.dependentId(), g.ordinal(), visible,
                        g.hiddenAlternativeCount(), g.externalAlternativeCount() + g.visibleAlternativeIds().size() - visible.size(),
                        g.satisfactionDisclosed(), g.satisfied());
            }).toList();
            assertEquals(expectedGroups, projection.requirementGroups());
            for (int i = 0; i < order.size(); i++) {
                var n = projection.nodes().get(i);
                assertEquals(i, n.ordinal());
                assertEquals(graph.node(n.blueprintId()).orElseThrow().sourceOrdinal(), n.sourceOrdinal());
                assertEquals(projection.edges().stream().filter(e -> e.dependentId().equals(n.blueprintId())).count(), n.prerequisiteCount());
                assertEquals(n.blueprintId().equals(d) ? 1 : 0, n.hiddenPrerequisiteCount());
            }
        }
    }

    @Test void projectingEverySmallGroupVisitsEachIncomingEdgeAndGroupOnce() {
        var nodes = new ArrayList<ResearchTreeGraph.Node>();
        var edges = new ArrayList<ResearchTreeGraph.Edge>();
        for (int i = 0; i < 256; i++) {
            nodes.add(node(i, i < 32 ? 0 : 32, 0));
            if (i >= 32) for (int root = 0; root < 32; root++) edges.add(new ResearchTreeGraph.Edge(id(root), id(i)));
        }
        var graph = new ResearchTreeGraph(nodes, edges);
        var work = new ResearchTreeGraph.SubgraphWork();
        for (var node : nodes) {
            var one = graph.orderedInducedSubgraph(List.of(node.blueprintId()), work);
            assertTrue(one.edges().isEmpty());
            assertEquals(node.prerequisiteCount(), one.requirementGroups().size());
        }
        assertEquals(edges.size(), work.edgesVisited);
        assertEquals(graph.requirementGroups().size(), work.requirementGroupsVisited);
        var rootsOnly = new ResearchTreeGraph.SubgraphWork();
        graph.orderedInducedSubgraph(List.of(id(0)), rootsOnly);
        assertEquals(0, rootsOnly.edgesVisited);
        assertEquals(0, rootsOnly.requirementGroupsVisited);
    }

    private static ResourceLocation id(int i) { return new ResourceLocation("test:n" + i); }
    private static ResearchTreeGraph.Node node(int i, int prerequisites, int hidden) {
        return new ResearchTreeGraph.Node(i, id(i), "name." + i, "rifle", new ResourceLocation("minecraft:paper"),
                JournalVisibility.FULL, false, false, false, 1, 0, prerequisites, hidden,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }
}
