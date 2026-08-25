package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeRelationsTest {
    @Test
    void indexesDiamondAncestorsDescendantsAndDirectRelationships() {
        ResearchTreeRelations relations = ResearchTreeRelations.create(diamondGraph());
        ResearchTreeRelations.FocusPath focus = relations.focus(id("test:d"));

        assertEquals(List.of(id("test:b"), id("test:c")), relations.directRequirements(id("test:d")));
        assertEquals(List.of(id("test:e")), relations.directUnlocks(id("test:d")));
        assertEquals(Set.of(id("test:b"), id("test:c")), focus.directRequirements());
        assertEquals(Set.of(id("test:a")), focus.requirementPath());
        assertEquals(Set.of(id("test:e")), focus.directUnlocks());
        assertEquals(Set.of(id("test:f")), focus.unlockPath());
        assertEquals(ResearchTreePresentationContract.RelationshipRole.SELECTED,
                focus.role(id("test:d")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT,
                focus.role(id("test:b")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.REQUIREMENT_PATH,
                focus.role(id("test:a")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.DIRECT_UNLOCK,
                focus.role(id("test:e")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.UNLOCK_PATH,
                focus.role(id("test:f")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.UNRELATED,
                focus.role(id("test:x")));
    }

    @Test
    void edgeRolesCoverTheCompleteFocusedPathWhileHoverFocusStaysDirect() {
        ResearchTreeRelations relations = ResearchTreeRelations.create(diamondGraph());
        ResearchTreeRelations.FocusPath focus = relations.focus(id("test:d"));
        ResearchTreeRelations.FocusPath hover = relations.directFocus(id("test:d"));

        assertEquals(ResearchTreePresentationContract.RelationshipRole.REQUIREMENT_PATH,
                focus.role(edge("test:a", "test:b")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT,
                focus.role(edge("test:b", "test:d")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.DIRECT_UNLOCK,
                focus.role(edge("test:d", "test:e")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.UNLOCK_PATH,
                focus.role(edge("test:e", "test:f")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.UNRELATED,
                focus.role(edge("test:x", "test:f")));
        assertTrue(hover.requirementPath().isEmpty());
        assertTrue(hover.unlockPath().isEmpty());
        assertEquals(ResearchTreePresentationContract.RelationshipRole.UNRELATED,
                hover.role(id("test:a")));
        assertEquals(ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT,
                hover.role(id("test:b")));
    }

    @Test
    void maximumPublicGraphFocusRemainsLinearAndBounded() {
        assertTimeout(Duration.ofSeconds(2), () -> {
            List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
            List<ResearchTreeGraph.Edge> edges = new ArrayList<>();
            nodes.add(node(0, "test:root", 0));
            for (int ordinal = 1; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
                ResourceLocation dependent = id("test:node_" + ordinal);
                nodes.add(node(ordinal, dependent.toString(), 1));
                edges.add(new ResearchTreeGraph.Edge(id("test:root"), dependent));
            }
            ResearchTreeRelations.FocusPath focus = ResearchTreeRelations
                    .create(new ResearchTreeGraph(nodes, edges))
                    .focus(id("test:root"));
            assertEquals(ResearchTreeGraph.MAX_NODES - 1, focus.directUnlocks().size());
            assertTrue(focus.unlockPath().isEmpty());
        });
    }

    private static ResearchTreeGraph diamondGraph() {
        return new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", 0),
                        node(1, "test:b", 1),
                        node(2, "test:c", 1),
                        node(3, "test:d", 2),
                        node(4, "test:e", 1),
                        node(5, "test:f", 1),
                        node(6, "test:x", 0)),
                List.of(
                        edge("test:a", "test:b"),
                        edge("test:a", "test:c"),
                        edge("test:b", "test:d"),
                        edge("test:c", "test:d"),
                        edge("test:d", "test:e"),
                        edge("test:e", "test:f")));
    }

    private static ResearchTreeGraph.Node node(int ordinal, String value, int prerequisites) {
        ResourceLocation blueprintId = id(value);
        return new ResearchTreeGraph.Node(
                ordinal,
                blueprintId,
                "name." + blueprintId.getPath(),
                "rifle",
                id("test:slot/" + ordinal),
                JournalVisibility.FULL,
                false, false, prerequisites == 0, 8, 0, prerequisites, 0,
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
