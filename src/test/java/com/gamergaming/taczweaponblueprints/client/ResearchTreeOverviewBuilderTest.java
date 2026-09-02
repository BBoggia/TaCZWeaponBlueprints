package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeOverviewBuilderTest {
    @Test
    void excludesOptedOutGroupsAndTurnsRealBoundaryEdgesIntoPortals() {
        ResearchTreePublication full = publication(false);

        ResearchTreeOverviewBuilder.Result result = ResearchTreeOverviewBuilder.build(full);

        assertFalse(result.includesCompleteGraph());
        assertEquals(3, full.graph().nodes().size());
        assertEquals(List.of(id("test:a"), id("test:b")),
                result.publication().graph().nodes().stream()
                        .map(ResearchTreeGraph.Node::blueprintId)
                        .toList());
        assertEquals(List.of(new ResearchTreeGraph.Edge(id("test:a"), id("test:b"))),
                result.publication().graph().edges());
        assertEquals(1, result.publication().graph().node(id("test:b"))
                .orElseThrow().prerequisiteCount());
        assertEquals(List.of(new ResearchTreeProjection.CrossGroupLink(
                        id("test:b"),
                        id("test:c"),
                        id("test:excluded"),
                        ResearchTreeProjection.Direction.UNLOCK)),
                result.boundaryLinks());
        assertEquals(List.of(id("test:included")),
                result.publication().presentation().groups().stream()
                        .map(ResearchTreePresentation.Group::id)
                        .toList());
        assertTrue(full.presentation().membership(id("test:c")).isPresent());
    }

    @Test
    void explicitOptInCanIncludeANonAuthoredGroup() {
        ResearchTreePublication full = publication(true);

        ResearchTreeOverviewBuilder.Result result = ResearchTreeOverviewBuilder.build(full);

        assertTrue(result.includesCompleteGraph());
        assertSame(full, result.publication());
        assertTrue(result.boundaryLinks().isEmpty());
    }

    @Test
    void hiddenOverviewBoundaryPublishesOnlyOpaqueRequirementMetadata() {
        ResourceLocation anonymousId = ResearchTreeGraph.redactedNodeId(0);
        ResourceLocation dependentId = id("test:dependent");
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        anonymousNode(0, anonymousId),
                        node(1, dependentId.toString(), 1)),
                List.of(new ResearchTreeGraph.Edge(anonymousId, dependentId)));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID,
                        ResearchTreePresentation.UNDISCLOSED_TITLE,
                        Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                        Optional.empty(),
                        0,
                        ResearchTreePresentation.Kind.UNDISCLOSED,
                        false,
                        List.of(new ResearchTreePresentation.Member(anonymousId, 0, 0))),
                new ResearchTreePresentation.Group(
                        id("test:included"),
                        "Included",
                        Optional.empty(),
                        Optional.of(dependentId),
                        1,
                        ResearchTreePresentation.Kind.AUTHORED,
                        true,
                        List.of(new ResearchTreePresentation.Member(dependentId, 1, 0)))));

        ResearchTreeOverviewBuilder.Result result = ResearchTreeOverviewBuilder.build(
                new ResearchTreePublication(graph, presentation));

        assertEquals(List.of(dependentId), result.publication().graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList());
        assertEquals(List.of(new ResearchTreeProjection.CrossGroupLink(
                        dependentId,
                        anonymousId,
                        ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID,
                        ResearchTreeProjection.Direction.REQUIREMENT)),
                result.boundaryLinks());
        assertFalse(result.boundaryLinks().toString().contains("secret"));
    }

    @Test
    void includedRedactedSubsetKeepsItsOpaqueSourceOrdinalWhenLocallyReindexed() {
        ResourceLocation excludedId = id("test:excluded");
        ResourceLocation anonymousId = ResearchTreeGraph.redactedNodeId(1);
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        node(0, excludedId.toString(), 0),
                        anonymousNode(1, anonymousId)),
                List.of());
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:excluded_group"),
                        "Excluded",
                        Optional.empty(),
                        Optional.of(excludedId),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        false,
                        List.of(new ResearchTreePresentation.Member(excludedId, 0, 0))),
                new ResearchTreePresentation.Group(
                        ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID,
                        ResearchTreePresentation.UNDISCLOSED_TITLE,
                        Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                        Optional.empty(),
                        1,
                        ResearchTreePresentation.Kind.UNDISCLOSED,
                        true,
                        List.of(new ResearchTreePresentation.Member(anonymousId, 0, 0)))));

        ResearchTreeOverviewBuilder.Result result = ResearchTreeOverviewBuilder.build(
                new ResearchTreePublication(graph, presentation));
        ResearchTreeGraph.Node projected = result.publication().graph().nodes().get(0);

        assertEquals(0, projected.ordinal());
        assertEquals(1, projected.sourceOrdinal());
        assertEquals(anonymousId, projected.blueprintId());
        assertFalse(projected.visibility().revealsIdentity());
    }

    @Test
    void explicitlyExcludedMaximumFallbackPopulationStaysOutOfTheOverview() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTreePresentation.Group> groups = new ArrayList<>();
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            ResourceLocation nodeId = id("test:fallback_" + ordinal);
            nodes.add(node(ordinal, nodeId.toString(), 0));
            groups.add(new ResearchTreePresentation.Group(
                    id("test:fallback_group_" + ordinal),
                    "Fallback " + ordinal,
                    Optional.empty(),
                    Optional.of(nodeId),
                    ordinal,
                    ResearchTreePresentation.Kind.ITEM_TYPE_FALLBACK,
                    false,
                    List.of(new ResearchTreePresentation.Member(nodeId, 0, 0))));
        }
        ResearchTreePublication full = new ResearchTreePublication(
                new ResearchTreeGraph(nodes, List.of()),
                new ResearchTreePresentation(groups));

        ResearchTreeOverviewBuilder.Result result = org.junit.jupiter.api.Assertions.assertTimeout(
                Duration.ofSeconds(3),
                () -> ResearchTreeOverviewBuilder.build(full));

        assertFalse(result.includesCompleteGraph());
        assertEquals(ResearchTreePublication.EMPTY, result.publication());
        assertTrue(result.boundaryLinks().isEmpty());
        assertEquals(ResearchTreeGraph.MAX_NODES, full.graph().nodes().size());
    }

    private static ResearchTreePublication publication(boolean includeFallback) {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(node(0, "test:a", 0), node(1, "test:b", 1), node(2, "test:c", 1)),
                List.of(
                        new ResearchTreeGraph.Edge(id("test:a"), id("test:b")),
                        new ResearchTreeGraph.Edge(id("test:b"), id("test:c"))));
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:included"),
                        "Included",
                        Optional.empty(),
                        Optional.of(id("test:a")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        true,
                        List.of(
                                new ResearchTreePresentation.Member(id("test:a"), 0, 0),
                                new ResearchTreePresentation.Member(id("test:b"), 1, 0))),
                new ResearchTreePresentation.Group(
                        id("test:excluded"),
                        "Excluded",
                        Optional.empty(),
                        Optional.of(id("test:c")),
                        1,
                        ResearchTreePresentation.Kind.ITEM_TYPE_FALLBACK,
                        includeFallback,
                        List.of(new ResearchTreePresentation.Member(id("test:c"), 2, 0)))));
        return new ResearchTreePublication(graph, presentation);
    }

    private static ResearchTreeGraph.Node node(int ordinal, String value, int prerequisites) {
        ResourceLocation nodeId = id(value);
        return new ResearchTreeGraph.Node(
                ordinal,
                nodeId,
                "name." + nodeId.getPath(),
                "rifle",
                id("test:slot/" + nodeId.getPath()),
                JournalVisibility.FULL,
                false,
                false,
                false,
                8,
                0,
                prerequisites,
                0,
                ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResearchTreeGraph.Node anonymousNode(
            int ordinal,
            ResourceLocation nodeId) {
        return new ResearchTreeGraph.Node(
                ordinal,
                nodeId,
                ResearchTreeGraph.REDACTED_NAME_KEY,
                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                JournalVisibility.SILHOUETTE,
                false,
                false,
                false,
                0,
                0,
                0,
                0,
                ResearchTreeGraph.Availability.REDACTED);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
