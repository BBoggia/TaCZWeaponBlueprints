package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreePublicationScopeTest {
    @Test
    void inducedLegacyGraphKeepsOnlyMembershipNodesAndTruthfulInternalEdges() {
        ResourceLocation weaponRoot = id("test:weapon_root");
        ResourceLocation attachment = id("test:attachment");
        ResourceLocation weaponUpgrade = id("test:weapon_upgrade");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, weaponRoot, 0, ResearchTreeGraph.Availability.AVAILABLE),
                        node(1, attachment, 1,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED),
                        node(2, weaponUpgrade, 1,
                                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED)),
                List.of(
                        new ResearchTreeGraph.Edge(weaponRoot, attachment),
                        new ResearchTreeGraph.Edge(attachment, weaponUpgrade)));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:weapons"),
                        "Weapons",
                        Optional.empty(),
                        Optional.of(weaponRoot),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(
                                new ResearchTreePresentation.Member(weaponRoot, 0, 0),
                                new ResearchTreePresentation.Member(weaponUpgrade, 1, 0)))));

        ResearchTreePublication publication = new ResearchTreePublication(graph, presentation);
        ResearchTreeGraph legacy = publication.legacyGraph();

        assertEquals(Set.of(weaponRoot, weaponUpgrade), publication.legacyNodeIds());
        assertEquals(List.of(weaponRoot, weaponUpgrade), legacy.nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList());
        assertTrue(legacy.edges().isEmpty());
        assertEquals(0, legacy.node(weaponUpgrade).orElseThrow().prerequisiteCount());
        assertEquals(2, legacy.node(weaponUpgrade).orElseThrow().sourceOrdinal());
        assertEquals(3, publication.graph().nodes().size());

        ResearchTreePublication legacyView = publication.legacyView();
        assertEquals(legacy, legacyView.graph());
        assertEquals(presentation, legacyView.presentation());
        assertTrue(legacyView.techTree().treeId().isEmpty());
    }

    @Test
    void completeLegacyMembershipReusesTheOriginalPublication() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, id("test:weapon"), 0,
                        ResearchTreeGraph.Availability.AVAILABLE)),
                List.of());
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:weapons"),
                        "Weapons",
                        Optional.empty(),
                        Optional.of(id("test:weapon")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(new ResearchTreePresentation.Member(
                                id("test:weapon"), 0, 0)))));
        ResearchTreePublication publication = new ResearchTreePublication(graph, presentation);

        assertSame(publication, publication.legacyView());
        assertSame(graph, publication.legacyGraph());
    }

    @Test
    void authoritativePublicationMayContainOnlyNonLegacyNodes() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, id("test:attachment"), 0,
                        ResearchTreeGraph.Availability.AVAILABLE)),
                List.of());
        ResearchTreePublication publication = new ResearchTreePublication(
                graph, ResearchTreePresentation.EMPTY);

        assertEquals(1, publication.graph().nodes().size());
        assertEquals(ResearchTreePublication.EMPTY, publication.legacyView());
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResourceLocation id,
            int prerequisiteCount,
            ResearchTreeGraph.Availability availability) {
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                "name." + id.getPath(),
                "test",
                new ResourceLocation("test", "slot/" + id.getPath()),
                JournalVisibility.FULL,
                availability == ResearchTreeGraph.Availability.LEARNED,
                true,
                availability == ResearchTreeGraph.Availability.AVAILABLE,
                8,
                0,
                prerequisiteCount,
                0,
                availability);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
