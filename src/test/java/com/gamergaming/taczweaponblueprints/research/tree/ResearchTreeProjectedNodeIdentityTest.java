package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

class ResearchTreeProjectedNodeIdentityTest {
    @Test
    void redactedNodeMayBeLocallyReindexedWithoutChangingItsOpaqueSourceKey() {
        ResearchTreeGraph.Node projected = redactedNode(0, 7);
        ResearchTreeGraph graph = new ResearchTreeGraph(List.of(projected), List.of());

        assertEquals(0, graph.nodes().get(0).ordinal());
        assertEquals(7, graph.nodes().get(0).sourceOrdinal());
        assertEquals(ResearchTreeGraph.redactedNodeId(7),
                graph.nodes().get(0).blueprintId());
    }

    @Test
    void redactedOpaqueKeyMustMatchTheStableSourceOrdinal() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph.Node(
                0,
                7,
                ResearchTreeGraph.redactedNodeId(0),
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
                ResearchTreeGraph.Availability.REDACTED));
    }

    @Test
    void graphRejectsDuplicateSourceOrdinalsAfterProjection() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGraph(
                List.of(
                        redactedNode(0, 7),
                        new ResearchTreeGraph.Node(
                                1,
                                7,
                                ResearchTreeGraph.redactedNodeId(7, 1),
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
                                ResearchTreeGraph.Availability.REDACTED)),
                List.of()));
    }

    @Test
    void legacyConstructorKeepsFullPublicationIdentitySemantics() {
        ResearchTreeGraph.Node node = new ResearchTreeGraph.Node(
                4,
                ResearchTreeGraph.redactedNodeId(4),
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

        assertEquals(node.ordinal(), node.sourceOrdinal());
    }

    private static ResearchTreeGraph.Node redactedNode(int localOrdinal, int sourceOrdinal) {
        return new ResearchTreeGraph.Node(
                localOrdinal,
                sourceOrdinal,
                ResearchTreeGraph.redactedNodeId(sourceOrdinal),
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
}
