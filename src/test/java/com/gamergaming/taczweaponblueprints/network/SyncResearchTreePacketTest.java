package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.WeaponRating;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupDefinition;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

class SyncResearchTreePacketTest {
    @Test
    void chunksRoundTripAndAccumulateAtomicallyOutOfOrder() {
        ResearchTreeGraph graph = largeGraph();
        ResearchTreePublication base = publication(graph);
        ResearchTreePublication publication = new ResearchTreePublication(
                graph,
                base.presentation(),
                largeTechTree(graph.nodes()));
        List<SyncResearchTreePacket> packets = SyncResearchTreePacket.split(publication, 55L);
        assertTrue(packets.size() > 1);

        List<SyncResearchTreePacket> decoded = packets.stream().map(packet -> {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.toBytes(buffer);
                assertTrue(buffer.readableBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES);
                return new SyncResearchTreePacket(buffer);
            } finally {
                buffer.release();
            }
        }).toList();
        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();
        Optional<ResearchTreePublication> completed = Optional.empty();
        for (int index = decoded.size() - 1; index >= 0; index--) {
            completed = accumulator.accept(decoded.get(index));
        }
        assertEquals(publication, completed.orElseThrow());
    }

    @Test
    void ordinalEdgesRoundTripWithoutRepeatingBlueprintIds() {
        ResearchTreeGraph graph = branchingGraph();
        ResearchTreePublication publication = publication(graph);
        SyncResearchTreePacket packet = SyncResearchTreePacket.split(publication, 9L).get(0);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            SyncResearchTreePacket decoded = new SyncResearchTreePacket(buffer);
            ResearchTreePublication completed = new SyncResearchTreePacket.ClientAccumulator()
                    .accept(decoded).orElseThrow();
            assertEquals(publication, completed);
        } finally {
            buffer.release();
        }
    }

    @Test
    void techTreeMetadataRoundTripsAtomicallyWithItsMatchingGraph() {
        ResearchTreeGraph graph = branchingGraph();
        ResearchTreePublication base = publication(graph);
        ResearchTreePublication expected = new ResearchTreePublication(
                graph,
                base.presentation(),
                techTree(graph.nodes()));
        List<SyncResearchTreePacket> packets = SyncResearchTreePacket.split(expected, 91L);
        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();
        Optional<ResearchTreePublication> completed = Optional.empty();

        for (int index = packets.size() - 1; index >= 0; index--) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packets.get(index).toBytes(buffer);
                assertTrue(buffer.readableBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES);
                completed = accumulator.accept(new SyncResearchTreePacket(buffer));
            } finally {
                buffer.release();
            }
        }

        ResearchTreePublication decoded = completed.orElseThrow();
        assertEquals(expected, decoded);
        assertEquals(id("test:tech_tree"), decoded.techTree().treeId().orElseThrow());
        assertEquals(28, decoded.techTree().maxNodesPerLayer());
        assertEquals(4, decoded.techTree().memberCount());
        assertEquals(Optional.of(new WeaponRating(25, 50, 75)),
                decoded.techTree().domains().get(0).lanes().get(0).members().get(0).rating());
        assertEquals(123,
                decoded.techTree().domains().get(0).lanes().get(0).members().get(0).rank());
        assertEquals(Optional.of(id("test:custom_band")),
                decoded.techTree().domains().get(0).lanes().get(0).members().get(0).bandId());
        assertEquals(Optional.of(0x336699), decoded.techTree().bands().get(0).color());
        assertEquals(Optional.of(id("test:a")),
                decoded.techTree().bands().get(0).icon());
        assertEquals(4_000_000_000L,
                decoded.techTree().domains().get(0).lanes().get(0).members().get(0).siblingOrder());
        assertEquals(PlacementOrigin.AUTOMATIC,
                decoded.techTree().domains().get(0).lanes().get(0).members().get(0).origin());
        assertEquals(Optional.of(new ResearchTechTreePresentation.AutomaticBranchPlacement(
                        2, 7, 3, 9)),
                decoded.techTree().domains().get(0).lanes().get(0).members().get(0)
                        .automaticBranch());
    }

    @Test
    void mixedKindGraphRoundTripsWithWeaponOnlyLegacyMembership() {
        ResearchTreeGraph graph = branchingGraph();
        ResearchTreePresentation weaponPresentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:weapons"),
                        "Weapons",
                        Optional.empty(),
                        Optional.of(id("test:a")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        List.of(
                                new ResearchTreePresentation.Member(id("test:a"), 0, 0),
                                new ResearchTreePresentation.Member(id("test:b"), 1, 0)))));
        ResearchTreePublication expected = new ResearchTreePublication(
                graph,
                weaponPresentation,
                techTree(graph.nodes()));
        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();
        Optional<ResearchTreePublication> completed = Optional.empty();

        for (SyncResearchTreePacket packet : SyncResearchTreePacket.split(expected, 92L)) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.toBytes(buffer);
                completed = accumulator.accept(new SyncResearchTreePacket(buffer));
            } finally {
                buffer.release();
            }
        }

        ResearchTreePublication decoded = completed.orElseThrow();
        assertEquals(expected, decoded);
        assertEquals(4, decoded.graph().nodes().size());
        assertEquals(List.of(id("test:a"), id("test:b")), decoded.legacyGraph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList());
        assertEquals(4, decoded.techTree().memberCount());
    }

    @Test
    void splitRejectsAProjectedNodeInsteadOfDroppingItsSourceOrdinal() {
        ResourceLocation publicId = id("test:projected");
        ResearchTreeGraph.Node projected = new ResearchTreeGraph.Node(
                0,
                2,
                publicId,
                "name.projected",
                "rifle",
                id("test:slot/projected"),
                JournalVisibility.FULL,
                false,
                false,
                true,
                8,
                0,
                0,
                0,
                ResearchTreeGraph.Availability.AVAILABLE);
        ResearchTreePublication projection = publication(
                new ResearchTreeGraph(List.of(projected), List.of()));

        assertThrows(IllegalArgumentException.class, () ->
                SyncResearchTreePacket.split(projection, 10L));
    }

    @Test
    void groupMetadataRanksIconsAndKindsRoundTripWithTheMatchingGraph() {
        ResearchTreeGraph graph = branchingGraph();
        ResearchTreePresentation presentation = new ResearchTreePresentation(List.of(
                new ResearchTreePresentation.Group(
                        id("test:sidearms"),
                        "Sidearms",
                        Optional.of("group.test.sidearms"),
                        Optional.of(id("test:a")),
                        0,
                        ResearchTreePresentation.Kind.AUTHORED,
                        false,
                        List.of(
                                new ResearchTreePresentation.Member(id("test:a"), 0, 0),
                                new ResearchTreePresentation.Member(id("test:b"), 1, 0))),
                new ResearchTreePresentation.Group(
                        id("test:rifles"),
                        "Rifles",
                        Optional.empty(),
                        Optional.of(id("test:c")),
                        1,
                        ResearchTreePresentation.Kind.ITEM_TYPE_FALLBACK,
                        List.of(
                                new ResearchTreePresentation.Member(id("test:c"), 1, 0),
                                new ResearchTreePresentation.Member(id("test:d"), 2, 0)))));
        ResearchTreePublication expected = new ResearchTreePublication(graph, presentation);

        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();
        Optional<ResearchTreePublication> completed = Optional.empty();
        for (SyncResearchTreePacket packet : SyncResearchTreePacket.split(expected, 21L)) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.toBytes(buffer);
                completed = accumulator.accept(new SyncResearchTreePacket(buffer));
            } finally {
                buffer.release();
            }
        }

        ResearchTreePublication decoded = completed.orElseThrow();
        assertEquals(expected, decoded);
        assertEquals(ResearchTreePresentation.Kind.ITEM_TYPE_FALLBACK,
                decoded.presentation().groups().get(1).kind());
        assertEquals(Optional.of(id("test:a")),
                decoded.presentation().groups().get(0).iconNodeId());
        assertEquals(1, decoded.presentation().groups().get(0).members().get(1).rank());
        assertFalse(decoded.presentation().groups().get(0).includedInOverview());
        assertTrue(decoded.presentation().groups().get(1).includedInOverview());
    }

    @Test
    void redactedVisibilityTiersRoundTripWithOnlyOpaqueNodeKeys() {
        ResourceLocation silhouetteId = ResearchTreeGraph.redactedNodeId(0);
        ResourceLocation nameId = ResearchTreeGraph.redactedNodeId(1);
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(
                        redactedNode(0, silhouetteId, ResearchTreeGraph.REDACTED_NAME_KEY,
                                JournalVisibility.SILHOUETTE, 0),
                        redactedNode(1, nameId, "name.safe", JournalVisibility.NAME, 1)),
                List.of(new ResearchTreeGraph.Edge(silhouetteId, nameId)));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ResearchTreePublication publication = publication(graph);
            SyncResearchTreePacket.split(publication, 10L).get(0).toBytes(buffer);
            ResearchTreePublication decoded = new SyncResearchTreePacket.ClientAccumulator()
                    .accept(new SyncResearchTreePacket(buffer)).orElseThrow();
            assertEquals(publication, decoded);
            assertEquals(ResearchTreeGraph.Availability.REDACTED,
                    decoded.graph().nodes().get(0).availability());
            assertEquals(ResearchTreeGraph.REDACTED_ITEM_TYPE,
                    decoded.graph().nodes().get(1).itemType());
        } finally {
            buffer.release();
        }
    }

    @Test
    void maximumDenseEdgeTableFitsTheBoundedOrdinalWireFormat() {
        ResearchTreeGraph graph = denseGraph();
        assertEquals(ResearchTreeGraph.MAX_EDGES, graph.edges().size());
        ResearchTreePublication publication = publication(graph);
        List<SyncResearchTreePacket> packets = SyncResearchTreePacket.split(publication, 12L);

        assertEquals(1, packets.size());
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packets.get(0).toBytes(buffer);
            assertTrue(buffer.readableBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES);
            ResearchTreePublication decoded = new SyncResearchTreePacket.ClientAccumulator()
                    .accept(new SyncResearchTreePacket(buffer)).orElseThrow();
            assertEquals(publication, decoded);
        } finally {
            buffer.release();
        }
    }

    @Test
    void groupedRequirementIdentityAndHiddenCountsRoundTrip() {
        List<ResearchTreeGraph.Node> nodes = List.of(
                node(0, "test:a", "name.a", 0),
                node(1, "test:b", "name.b", 0),
                new ResearchTreeGraph.Node(
                        2,
                        id("test:c"),
                        "name.c",
                        "rifle",
                        id("test:slot/c"),
                        JournalVisibility.FULL,
                        false,
                        false,
                        false,
                        8,
                        0,
                        2,
                        1,
                        ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED));
        List<ResearchTreeGraph.RequirementGroup> groups = List.of(
                new ResearchTreeGraph.RequirementGroup(
                        id("test:c"),
                        0,
                        List.of(id("test:a"), id("test:b")),
                        0,
                        true),
                new ResearchTreeGraph.RequirementGroup(
                        id("test:c"),
                        1,
                        List.of(),
                        1,
                        false));
        ResearchTreeGraph graph = ResearchTreeGraph.withRequirementGroups(nodes, groups);
        ResearchTreePublication publication = publication(graph);
        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();
        ResearchTreePublication decoded = null;
        for (SyncResearchTreePacket packet : SyncResearchTreePacket.split(publication, 91L)) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.toBytes(buffer);
                Optional<ResearchTreePublication> accepted = accumulator.accept(
                        new SyncResearchTreePacket(buffer));
                if (accepted.isPresent()) {
                    decoded = accepted.orElseThrow();
                }
            } finally {
                buffer.release();
            }
        }

        assertEquals(publication, decoded);
        assertEquals(groups, decoded.graph().requirementGroupsOf(id("test:c")));
    }

    @Test
    void maximumGroupTableFitsAndAssignsEveryPublicNodeExactlyOnce() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTreePresentation.Group> groups = new ArrayList<>();
        for (int ordinal = 0; ordinal < ResearchTreeGraph.MAX_NODES; ordinal++) {
            ResearchTreeGraph.Node node = node(
                    ordinal, "test:group_node_" + ordinal, "name.group", 0);
            nodes.add(node);
            groups.add(new ResearchTreePresentation.Group(
                    id("test:group_" + ordinal),
                    "Group " + ordinal,
                    Optional.empty(),
                    Optional.of(node.blueprintId()),
                    ordinal,
                    ResearchTreePresentation.Kind.ITEM_TYPE_FALLBACK,
                    List.of(new ResearchTreePresentation.Member(
                            node.blueprintId(), 0, 0))));
        }
        ResearchTreePublication expected = new ResearchTreePublication(
                new ResearchTreeGraph(nodes, List.of()),
                new ResearchTreePresentation(groups));
        List<SyncResearchTreePacket> packets = SyncResearchTreePacket.split(expected, 13L);
        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();
        Optional<ResearchTreePublication> completed = Optional.empty();

        for (SyncResearchTreePacket packet : packets) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.toBytes(buffer);
                assertTrue(buffer.readableBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES);
                completed = accumulator.accept(new SyncResearchTreePacket(buffer));
            } finally {
                buffer.release();
            }
        }

        assertEquals(expected, completed.orElseThrow());
        assertEquals(ResearchTreePresentation.MAX_GROUPS,
                completed.orElseThrow().presentation().groups().size());
    }

    @Test
    void newerCompletedTreeRejectsOlderRemaindersAndDuplicateChunks() {
        List<SyncResearchTreePacket> oldPackets = SyncResearchTreePacket.split(
                publication(largeGraph()), 10L);
        SyncResearchTreePacket replacement = SyncResearchTreePacket
                .split(ResearchTreePublication.EMPTY, 11L).get(0);
        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();

        assertFalse(accumulator.accept(oldPackets.get(0)).isPresent());
        assertFalse(accumulator.accept(oldPackets.get(0)).isPresent());
        assertEquals(ResearchTreePublication.EMPTY, accumulator.accept(replacement).orElseThrow());
        for (int index = 1; index < oldPackets.size(); index++) {
            assertFalse(accumulator.accept(oldPackets.get(index)).isPresent());
        }
        assertFalse(accumulator.accept(replacement).isPresent());
    }

    @Test
    void decoderRejectsCountsVisibilityAndEdgeOrdinalsBeforeAccumulation() {
        FriendlyByteBuf excessiveNodes = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writeHeader(excessiveNodes, 0, 0);
            excessiveNodes.writeVarInt(1);
            assertThrows(IllegalArgumentException.class, () -> new SyncResearchTreePacket(excessiveNodes));
        } finally {
            excessiveNodes.release();
        }

        FriendlyByteBuf hiddenNode = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writeHeader(hiddenNode, 1, 0);
            hiddenNode.writeVarInt(1);
            hiddenNode.writeVarInt(0);
            hiddenNode.writeUtf("test:a", BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
            hiddenNode.writeUtf("name.a", BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
            hiddenNode.writeUtf("rifle", BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH);
            hiddenNode.writeUtf("test:slot/a", BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
            hiddenNode.writeByte(JournalVisibility.HIDDEN.ordinal());
            assertThrows(IllegalArgumentException.class, () -> new SyncResearchTreePacket(hiddenNode));
        } finally {
            hiddenNode.release();
        }

        FriendlyByteBuf invalidEdge = new FriendlyByteBuf(Unpooled.buffer());
        try {
            writeHeader(invalidEdge, 1, 1);
            invalidEdge.writeVarInt(0);
            invalidEdge.writeVarInt(1);
            invalidEdge.writeVarInt(0);
            invalidEdge.writeVarInt(1);
            assertThrows(IllegalArgumentException.class, () -> new SyncResearchTreePacket(invalidEdge));
        } finally {
            invalidEdge.release();
        }
    }

    @Test
    void decoderAndAccumulatorRejectInvalidOrDisclosureBreakingGroups() {
        FriendlyByteBuf invalidKind = groupPacketPrefix();
        try {
            writeGroupPrefix(invalidKind, "Published", Optional.empty(), 0,
                    ResearchTreePresentation.Kind.values().length);
            assertThrows(IllegalArgumentException.class, () -> new SyncResearchTreePacket(invalidKind));
        } finally {
            invalidKind.release();
        }

        FriendlyByteBuf excessiveMembers = groupPacketPrefix();
        try {
            writeGroupPrefix(excessiveMembers, "Published", Optional.empty(), 0,
                    ResearchTreePresentation.Kind.AUTHORED.ordinal());
            excessiveMembers.writeVarInt(2);
            assertThrows(IllegalArgumentException.class,
                    () -> new SyncResearchTreePacket(excessiveMembers));
        } finally {
            excessiveMembers.release();
        }

        FriendlyByteBuf disclosureMismatch = groupPacketPrefix();
        try {
            writeGroupPrefix(
                    disclosureMismatch,
                    ResearchTreePresentation.UNDISCLOSED_TITLE,
                    Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                    -1,
                    ResearchTreePresentation.Kind.UNDISCLOSED.ordinal());
            disclosureMismatch.writeVarInt(1);
            disclosureMismatch.writeVarInt(0);
            disclosureMismatch.writeVarInt(0);
            disclosureMismatch.writeVarInt(0);
            disclosureMismatch.writeVarInt(0);
            SyncResearchTreePacket decoded = new SyncResearchTreePacket(disclosureMismatch);
            assertThrows(IllegalArgumentException.class,
                    () -> new SyncResearchTreePacket.ClientAccumulator().accept(decoded));
        } finally {
            disclosureMismatch.release();
        }
    }

    @Test
    void decoderRejectsAnOversizedNestedTechTreeBeforeAllocation() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeLong(71L);
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            buffer.writeVarInt(1);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            buffer.writeVarInt(1);
            buffer.writeVarInt(1);
            buffer.writeVarInt(1);
            writeNode(buffer, node(0, "test:a", "name.a", 0));
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            writeWireGroup(buffer, 1);
            buffer.writeVarInt(1);
            buffer.writeUtf("test:tech_tree", BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
            buffer.writeUtf("Tech Tree", 80);
            buffer.writeBoolean(false);
            buffer.writeVarInt(1);
            buffer.writeVarInt(Tier.values().length + 1);

            assertThrows(IllegalArgumentException.class,
                    () -> new SyncResearchTreePacket(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void accumulatorRejectsDuplicateNodeOrdinalsAndConflictingChunks() {
        SyncResearchTreePacket duplicateOrdinals = decodedPacket(
                30L, 0, 1, 2, 0,
                List.of(node(0, "test:a", "name.a", 0), node(0, "test:b", "name.b", 0)),
                List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncResearchTreePacket.ClientAccumulator().accept(duplicateOrdinals));

        SyncResearchTreePacket first = decodedPacket(
                31L, 0, 2, 2, 0,
                List.of(node(0, "test:a", "name.a", 0)),
                List.of());
        SyncResearchTreePacket conflicting = decodedPacket(
                31L, 0, 2, 2, 0,
                List.of(node(1, "test:b", "name.b", 0)),
                List.of());
        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();
        assertFalse(accumulator.accept(first).isPresent());
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(conflicting));
    }

    @Test
    void accumulatorRejectsCompletedTotalsThatDoNotMatchTheDeclaration() {
        SyncResearchTreePacket incomplete = decodedPacket(
                40L, 0, 1, 2, 0,
                List.of(node(0, "test:a", "name.a", 0)),
                List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncResearchTreePacket.ClientAccumulator().accept(incomplete));
    }

    @Test
    void accumulatorRejectsCumulativeTotalsBeforeTheFinalChunk() {
        SyncResearchTreePacket first = decodedPacket(
                41L, 0, 3, 2, 0,
                List.of(
                        node(0, "test:a", "name.a", 0),
                        node(1, "test:b", "name.b", 0)),
                List.of());
        SyncResearchTreePacket overflow = decodedPacket(
                41L, 1, 3, 2, 0,
                List.of(node(0, "test:a", "name.a", 0)),
                List.of());
        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();

        assertFalse(accumulator.accept(first).isPresent());
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(overflow));
    }

    private static void writeHeader(FriendlyByteBuf buffer, int totalNodes, int totalEdges) {
        buffer.writeLong(1L);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeVarInt(totalNodes);
        buffer.writeVarInt(totalEdges);
        buffer.writeVarInt(0);
        buffer.writeVarInt(totalNodes == 0 ? 0 : 1);
        buffer.writeVarInt(totalNodes);
        buffer.writeVarInt(0);
    }

    private static SyncResearchTreePacket decodedPacket(
            long syncId,
            int chunkIndex,
            int chunkCount,
            int totalNodes,
            int totalEdges,
            List<ResearchTreeGraph.Node> nodes,
            List<int[]> edges) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeLong(syncId);
            buffer.writeVarInt(chunkIndex);
            buffer.writeVarInt(chunkCount);
            buffer.writeVarInt(totalNodes);
            buffer.writeVarInt(totalEdges);
            buffer.writeVarInt(0);
            buffer.writeVarInt(totalNodes == 0 ? 0 : 1);
            buffer.writeVarInt(totalNodes);
            buffer.writeVarInt(0);
            buffer.writeVarInt(nodes.size());
            for (ResearchTreeGraph.Node node : nodes) {
                writeNode(buffer, node);
            }
            buffer.writeVarInt(edges.size());
            edges.forEach(edge -> {
                buffer.writeVarInt(edge[0]);
                buffer.writeVarInt(edge[1]);
            });
            buffer.writeVarInt(0);
            if (totalNodes == 0 || chunkCount > 1) {
                buffer.writeVarInt(0);
            } else {
                buffer.writeVarInt(1);
                writeWireGroup(buffer, totalNodes);
            }
            buffer.writeVarInt(0);
            return new SyncResearchTreePacket(buffer);
        } finally {
            buffer.release();
        }
    }

    private static FriendlyByteBuf groupPacketPrefix() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        writeHeader(buffer, 1, 0);
        buffer.writeVarInt(1);
        writeNode(buffer, node(0, "test:a", "name.a", 0));
        buffer.writeVarInt(0);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        return buffer;
    }

    private static void writeGroupPrefix(
            FriendlyByteBuf buffer,
            String title,
            Optional<String> translationKey,
            int iconOrdinal,
            int kindOrdinal) {
        buffer.writeUtf("test:published", BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeUtf(title, ResearchTreeGroupDefinition.MAX_TITLE_LENGTH);
        buffer.writeBoolean(translationKey.isPresent());
        translationKey.ifPresent(value ->
                buffer.writeUtf(value, BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH));
        buffer.writeVarInt(iconOrdinal + 1);
        buffer.writeVarInt(0);
        buffer.writeByte(kindOrdinal);
        buffer.writeBoolean(true);
    }

    private static void writeNode(FriendlyByteBuf buffer, ResearchTreeGraph.Node node) {
        buffer.writeVarInt(node.ordinal());
        buffer.writeUtf(node.blueprintId().toString(), BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeUtf(node.nameKey(), BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
        buffer.writeUtf(node.itemType(), BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH);
        buffer.writeUtf(node.displaySlotId().toString(), BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeByte(node.visibility().ordinal());
        int flags = (node.learned() ? 1 : 0)
                | (node.discovered() ? 2 : 0)
                | (node.policyEligible() ? 4 : 0);
        buffer.writeByte(flags);
        buffer.writeByte(node.availability().ordinal());
        buffer.writeVarInt(node.pointCost());
        buffer.writeVarInt(node.ingredientTypeCount());
        buffer.writeVarInt(node.prerequisiteCount());
        buffer.writeVarInt(node.hiddenPrerequisiteCount());
    }

    private static void writeWireGroup(FriendlyByteBuf buffer, int memberCount) {
        buffer.writeUtf("test:published", BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeUtf("Published", ResearchTreeGroupDefinition.MAX_TITLE_LENGTH);
        buffer.writeBoolean(false);
        buffer.writeVarInt(1);
        buffer.writeVarInt(0);
        buffer.writeByte(ResearchTreePresentation.Kind.AUTHORED.ordinal());
        buffer.writeBoolean(true);
        buffer.writeVarInt(memberCount);
        for (int ordinal = 0; ordinal < memberCount; ordinal++) {
            buffer.writeVarInt(ordinal);
            buffer.writeVarInt(0);
            buffer.writeVarInt(ordinal);
        }
    }

    private static ResearchTreePublication publication(ResearchTreeGraph graph) {
        if (graph.nodes().isEmpty()) {
            return ResearchTreePublication.EMPTY;
        }
        List<ResearchTreePresentation.Group> groups = new ArrayList<>();
        List<ResearchTreeGraph.Node> identified = graph.nodes().stream()
                .filter(node -> node.visibility().revealsIdentity())
                .toList();
        List<ResearchTreeGraph.Node> anonymous = graph.nodes().stream()
                .filter(node -> !node.visibility().revealsIdentity())
                .toList();
        if (!identified.isEmpty()) {
            Optional<ResourceLocation> icon = identified.stream()
                    .filter(node -> node.visibility().revealsIcon())
                    .map(ResearchTreeGraph.Node::blueprintId)
                    .findFirst();
            groups.add(new ResearchTreePresentation.Group(
                    id("test:published"),
                    "Published",
                    Optional.of("group.test.published"),
                    icon,
                    groups.size(),
                    ResearchTreePresentation.Kind.AUTHORED,
                    members(graph, identified)));
        }
        if (!anonymous.isEmpty()) {
            groups.add(new ResearchTreePresentation.Group(
                    ResearchTreePresentation.PREFERRED_UNDISCLOSED_GROUP_ID,
                    ResearchTreePresentation.UNDISCLOSED_TITLE,
                    Optional.of(ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY),
                    Optional.empty(),
                    groups.size(),
                    ResearchTreePresentation.Kind.UNDISCLOSED,
                    members(graph, anonymous)));
        }
        return new ResearchTreePublication(graph, new ResearchTreePresentation(groups));
    }

    private static ResearchTechTreePresentation techTree(List<ResearchTreeGraph.Node> nodes) {
        ResourceLocation a = nodes.get(0).blueprintId();
        ResourceLocation customBand = id("test:custom_band");
        return new ResearchTechTreePresentation(
                Optional.of(id("test:tech_tree")),
                "Tech Tree",
                Optional.of("tree.test.tech_tree"),
                Optional.of(a),
                List.of(),
                List.of(new ResearchTechTreePresentation.BandLabel(
                        customBand,
                        "Custom Band",
                        Optional.of("tree.band.test.custom"),
                        Optional.of(0x336699),
                        Optional.of(a))),
                28,
                List.of(new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.of("domain.test.weapons"),
                        Optional.of(a),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                id("test:general"),
                                "General",
                                Optional.empty(),
                                Optional.of(a),
                                0,
                                List.of(
                                        new ResearchTechTreePresentation.Member(
                                                a,
                                                123,
                                                4_000_000_000L,
                                                Optional.of(customBand),
                                                PlacementOrigin.AUTOMATIC,
                                                Optional.of(new WeaponRating(25, 50, 75)),
                                                Optional.of(new ResearchTechTreePresentation
                                                        .AutomaticBranchPlacement(
                                                                2, 7, 3, 9))),
                                        new ResearchTechTreePresentation.Member(
                                                nodes.get(1).blueprintId(), 124, 0,
                                                Optional.of(customBand), PlacementOrigin.EXACT,
                                                Optional.empty()),
                                        new ResearchTechTreePresentation.Member(
                                                nodes.get(2).blueprintId(), 124, 1,
                                                Optional.of(customBand), PlacementOrigin.EXACT,
                                                Optional.empty()),
                                        new ResearchTechTreePresentation.Member(
                                                nodes.get(3).blueprintId(), 125, 0,
                                                Optional.of(customBand), PlacementOrigin.EXACT,
                                                Optional.empty())))))));
    }

    private static ResearchTechTreePresentation largeTechTree(List<ResearchTreeGraph.Node> nodes) {
        ResourceLocation first = nodes.get(0).blueprintId();
        return new ResearchTechTreePresentation(
                Optional.of(id("test:large_tech_tree")),
                "Large Tech Tree",
                Optional.empty(),
                Optional.of(first),
                List.of(Tier.values()).stream()
                        .map(tier -> new ResearchTechTreePresentation.TierLabel(
                                tier, tier.name(), Optional.empty()))
                        .toList(),
                List.of(new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.of(first),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                id("test:all_weapons"),
                                "All Weapons",
                                Optional.empty(),
                                Optional.of(first),
                                0,
                                nodes.stream()
                                        .map(node -> new ResearchTechTreePresentation.Member(
                                                node.blueprintId(),
                                                Tier.STARTER,
                                                node.ordinal(),
                                                Optional.empty()))
                                        .toList())))));
    }

    private static List<ResearchTreePresentation.Member> members(
            ResearchTreeGraph graph,
            List<ResearchTreeGraph.Node> nodes) {
        List<ResearchTreePresentation.Member> members = new ArrayList<>();
        java.util.Map<ResourceLocation, Integer> ranks = new java.util.HashMap<>();
        for (ResearchTreeGraph.Node node : nodes) {
            int rank = publicationRank(
                    node.blueprintId(), graph, ranks, new java.util.LinkedHashSet<>());
            members.add(new ResearchTreePresentation.Member(
                    node.blueprintId(), rank, 0));
        }
        members.sort(java.util.Comparator
                .comparingInt(ResearchTreePresentation.Member::rank)
                .thenComparing(member -> member.nodeId().toString()));
        java.util.Map<Integer, Integer> nextOrderByRank = new java.util.LinkedHashMap<>();
        List<ResearchTreePresentation.Member> ordered = new ArrayList<>();
        for (ResearchTreePresentation.Member member : members) {
            int order = nextOrderByRank.getOrDefault(member.rank(), 0);
            ordered.add(new ResearchTreePresentation.Member(member.nodeId(), member.rank(), order));
            nextOrderByRank.put(member.rank(), order + 1);
        }
        return List.copyOf(ordered);
    }

    private static int publicationRank(
            ResourceLocation nodeId,
            ResearchTreeGraph graph,
            java.util.Map<ResourceLocation, Integer> ranks,
            java.util.Set<ResourceLocation> visiting) {
        Integer known = ranks.get(nodeId);
        if (known != null) {
            return known;
        }
        if (!visiting.add(nodeId)) {
            throw new IllegalArgumentException("test graph contains a cycle");
        }
        int rank = 0;
        for (ResourceLocation prerequisiteId : graph.prerequisitesOf(nodeId)) {
            rank = Math.max(rank, publicationRank(
                    prerequisiteId, graph, ranks, visiting) + 1);
        }
        visiting.remove(nodeId);
        ranks.put(nodeId, rank);
        return rank;
    }

    private static ResearchTreeGraph largeGraph() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        String name = "n".repeat(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
        for (int index = 0; index < ResearchTreeGraph.MAX_NODES; index++) {
            nodes.add(node(index, "test:node_" + index, name, 0));
        }
        return new ResearchTreeGraph(nodes, List.of());
    }

    private static ResearchTreeGraph branchingGraph() {
        return new ResearchTreeGraph(
                List.of(
                        node(0, "test:a", "name.a", 0),
                        node(1, "test:b", "name.b", 1),
                        node(2, "test:c", "name.c", 1),
                        node(3, "test:d", "name.d", 2)),
                List.of(
                        new ResearchTreeGraph.Edge(id("test:a"), id("test:b")),
                        new ResearchTreeGraph.Edge(id("test:a"), id("test:c")),
                        new ResearchTreeGraph.Edge(id("test:b"), id("test:d")),
                        new ResearchTreeGraph.Edge(id("test:c"), id("test:d"))));
    }

    private static ResearchTreeGraph denseGraph() {
        int rootCount = 64;
        int dependentCount = ResearchTreeGraph.MAX_EDGES / rootCount;
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        List<ResearchTreeGraph.Edge> edges = new ArrayList<>();
        for (int root = 0; root < rootCount; root++) {
            nodes.add(node(root, "test:root_" + root, "name.root", 0));
        }
        for (int dependent = 0; dependent < dependentCount; dependent++) {
            int ordinal = rootCount + dependent;
            ResourceLocation dependentId = id("test:dependent_" + dependent);
            nodes.add(node(ordinal, dependentId.toString(), "name.dependent", rootCount));
            for (int root = 0; root < rootCount; root++) {
                edges.add(new ResearchTreeGraph.Edge(id("test:root_" + root), dependentId));
            }
        }
        return new ResearchTreeGraph(nodes, edges);
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            String value,
            String name,
            int prerequisites) {
        ResourceLocation id = id(value);
        return new ResearchTreeGraph.Node(
                ordinal,
                id,
                name,
                "rifle",
                id("test:slot/" + ordinal),
                JournalVisibility.FULL,
                false,
                false,
                prerequisites == 0,
                8,
                0,
                prerequisites,
                0,
                prerequisites == 0
                        ? ResearchTreeGraph.Availability.AVAILABLE
                        : ResearchTreeGraph.Availability.PREREQUISITES_REQUIRED);
    }

    private static ResearchTreeGraph.Node redactedNode(
            int ordinal,
            ResourceLocation publicId,
            String name,
            JournalVisibility visibility,
            int prerequisites) {
        return new ResearchTreeGraph.Node(
                ordinal,
                publicId,
                name,
                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                visibility,
                false,
                false,
                false,
                0,
                0,
                prerequisites,
                0,
                ResearchTreeGraph.Availability.REDACTED);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
