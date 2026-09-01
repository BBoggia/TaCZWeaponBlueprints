package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.client.ClientResearchState;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.WeaponRating;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupDefinition;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/**
 * Chunked synchronization for one disclosure-safe graph, its matching branch
 * groups, and its optional Tech Tree presentation metadata.
 */
public final class SyncResearchTreePacket {
    private static final int HEADER_RESERVE = 96;
    private static final int NODE_FIXED_RESERVE = 40;
    private static final int EDGE_RESERVE = 10;
    private static final int REQUIREMENT_GROUP_FIXED_RESERVE = 7;
    private static final int REQUIREMENT_ALTERNATIVE_RESERVE = 3;
    private static final int GROUP_FIXED_RESERVE = 25;
    private static final int MEMBER_RESERVE = 15;
    private static final int TECH_TREE_FIXED_RESERVE = 64;
    private static final int TECH_DOMAIN_FIXED_RESERVE = 32;
    private static final int TECH_LANE_FIXED_RESERVE = 32;
    private static final int TECH_MEMBER_RESERVE = 64;
    private static final ClientAccumulator CLIENT_ACCUMULATOR = new ClientAccumulator();

    private final long syncId;
    private final int chunkIndex;
    private final int chunkCount;
    private final int totalNodes;
    private final int totalEdges;
    private final int totalRequirementGroups;
    private final int totalGroups;
    private final int totalMembers;
    private final int totalTechTrees;
    private final List<ResearchTreeGraph.Node> nodes;
    private final List<WireEdge> edges;
    private final List<WireRequirementGroup> requirementGroups;
    private final List<WireGroup> groups;
    private final List<WireTechTree> techTrees;

    private SyncResearchTreePacket(
            long syncId,
            int chunkIndex,
            int chunkCount,
            int totalNodes,
            int totalEdges,
            int totalRequirementGroups,
            int totalGroups,
            int totalMembers,
            int totalTechTrees,
            List<ResearchTreeGraph.Node> nodes,
            List<WireEdge> edges,
            List<WireRequirementGroup> requirementGroups,
            List<WireGroup> groups,
            List<WireTechTree> techTrees) {
        this.syncId = syncId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.totalNodes = totalNodes;
        this.totalEdges = totalEdges;
        this.totalRequirementGroups = totalRequirementGroups;
        this.totalGroups = totalGroups;
        this.totalMembers = totalMembers;
        this.totalTechTrees = totalTechTrees;
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        this.edges = edges == null ? List.of() : List.copyOf(edges);
        this.requirementGroups = requirementGroups == null
                ? List.of()
                : List.copyOf(requirementGroups);
        this.groups = groups == null ? List.of() : List.copyOf(groups);
        this.techTrees = techTrees == null ? List.of() : List.copyOf(techTrees);
        validateCommonState();
        if (estimatedPayloadBytes() > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Research tree synchronization chunk exceeds the byte budget");
        }
    }

    public SyncResearchTreePacket(FriendlyByteBuf buf) {
        int start = buf.readerIndex();
        syncId = buf.readLong();
        chunkIndex = buf.readVarInt();
        chunkCount = buf.readVarInt();
        totalNodes = buf.readVarInt();
        totalEdges = buf.readVarInt();
        totalRequirementGroups = buf.readVarInt();
        totalGroups = buf.readVarInt();
        totalMembers = buf.readVarInt();
        totalTechTrees = buf.readVarInt();
        validateChunkMetadata(chunkIndex, chunkCount);
        validateTotals(
                totalNodes,
                totalEdges,
                totalRequirementGroups,
                totalGroups,
                totalMembers,
                totalTechTrees);

        int nodeCount = readBoundedCount(buf, totalNodes, "Research tree node");
        List<ResearchTreeGraph.Node> decodedNodes = new ArrayList<>(nodeCount);
        for (int index = 0; index < nodeCount; index++) {
            decodedNodes.add(readNode(buf));
        }
        int edgeCount = readBoundedCount(buf, totalEdges, "Research tree edge");
        List<WireEdge> decodedEdges = new ArrayList<>(edgeCount);
        for (int index = 0; index < edgeCount; index++) {
            WireEdge edge = new WireEdge(buf.readVarInt(), buf.readVarInt());
            if (edge.prerequisiteOrdinal() >= totalNodes
                    || edge.dependentOrdinal() >= totalNodes) {
                throw new IllegalArgumentException(
                        "Research tree edge ordinal is outside the node table");
            }
            decodedEdges.add(edge);
        }
        int requirementGroupCount = readBoundedCount(
                buf, totalRequirementGroups, "Research requirement group");
        List<WireRequirementGroup> decodedRequirementGroups =
                new ArrayList<>(requirementGroupCount);
        for (int index = 0; index < requirementGroupCount; index++) {
            decodedRequirementGroups.add(readRequirementGroup(buf, totalNodes));
        }
        int groupCount = readBoundedCount(buf, totalGroups, "Research tree group");
        List<WireGroup> decodedGroups = new ArrayList<>(groupCount);
        int remainingMembers = totalMembers;
        for (int index = 0; index < groupCount; index++) {
            WireGroup group = readGroup(buf, remainingMembers);
            decodedGroups.add(group);
            remainingMembers -= group.members().size();
        }
        int techTreeCount = readBoundedCount(buf, totalTechTrees, "Research Tech Tree");
        List<WireTechTree> decodedTechTrees = new ArrayList<>(techTreeCount);
        for (int index = 0; index < techTreeCount; index++) {
            decodedTechTrees.add(readTechTree(buf, totalNodes));
        }
        nodes = List.copyOf(decodedNodes);
        edges = List.copyOf(decodedEdges);
        requirementGroups = List.copyOf(decodedRequirementGroups);
        groups = List.copyOf(decodedGroups);
        techTrees = List.copyOf(decodedTechTrees);
        validateCommonState();
        if (buf.readerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Research tree synchronization chunk exceeds the byte budget");
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        int start = buf.writerIndex();
        buf.writeLong(syncId);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(totalNodes);
        buf.writeVarInt(totalEdges);
        buf.writeVarInt(totalRequirementGroups);
        buf.writeVarInt(totalGroups);
        buf.writeVarInt(totalMembers);
        buf.writeVarInt(totalTechTrees);
        buf.writeVarInt(nodes.size());
        nodes.forEach(node -> writeNode(buf, node));
        buf.writeVarInt(edges.size());
        edges.forEach(edge -> {
            buf.writeVarInt(edge.prerequisiteOrdinal());
            buf.writeVarInt(edge.dependentOrdinal());
        });
        buf.writeVarInt(requirementGroups.size());
        requirementGroups.forEach(group -> writeRequirementGroup(buf, group));
        buf.writeVarInt(groups.size());
        groups.forEach(group -> writeGroup(buf, group));
        buf.writeVarInt(techTrees.size());
        techTrees.forEach(tree -> writeTechTree(buf, tree));
        if (buf.writerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Research tree synchronization chunk exceeds the byte budget");
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> CLIENT_ACCUMULATOR.accept(this).ifPresent(publication ->
                ClientResearchState.acceptTree(syncId, publication)));
        context.setPacketHandled(true);
    }

    public static void clearClientState() {
        CLIENT_ACCUMULATOR.clear();
    }

    static List<SyncResearchTreePacket> split(ResearchTreePublication publication, long syncId) {
        if (publication == null) {
            throw new IllegalArgumentException("Research tree publication cannot be null");
        }
        ResearchTreeGraph graph = publication.graph();
        ResearchTreePresentation presentation = publication.presentation();
        Map<ResourceLocation, Integer> ordinals = new HashMap<>();
        graph.nodes().forEach(node -> {
            if (node.sourceOrdinal() != node.ordinal()) {
                throw new IllegalArgumentException(
                        "Research tree synchronization requires full-publication ordinals");
            }
            ordinals.put(node.blueprintId(), node.ordinal());
        });
        // Protocol 39 retains canonical requirement groups and derives the
        // compatibility edge table on receipt. Keeping edges out of the wire
        // avoids paying for every visible alternative twice.
        List<WireEdge> wireEdges = List.of();
        List<WireRequirementGroup> wireRequirementGroups = graph.requirementGroups().stream()
                .map(group -> WireRequirementGroup.from(group, ordinals))
                .toList();
        List<WireGroup> wireGroups = presentation.groups().stream()
                .map(group -> WireGroup.from(group, ordinals))
                .toList();
        List<WireTechTree> wireTechTrees = publication.techTree().available()
                ? List.of(WireTechTree.from(publication.techTree(), ordinals))
                : List.of();
        int memberCount = wireGroups.stream().mapToInt(group -> group.members().size()).sum();

        List<Chunk> chunks = new ArrayList<>();
        Chunk current = new Chunk();
        for (ResearchTreeGraph.Node node : graph.nodes()) {
            int bytes = estimatedNodeBytes(node);
            if (HEADER_RESERVE + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException("One Research tree node exceeds the chunk byte budget");
            }
            if (!current.empty() && current.estimatedBytes + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new Chunk();
            }
            current.nodes.add(node);
            current.estimatedBytes += bytes;
        }
        for (WireEdge edge : wireEdges) {
            if (!current.empty()
                    && current.estimatedBytes + EDGE_RESERVE > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new Chunk();
            }
            current.edges.add(edge);
            current.estimatedBytes += EDGE_RESERVE;
        }
        for (WireRequirementGroup group : wireRequirementGroups) {
            int bytes = estimatedRequirementGroupBytes(group);
            if (HEADER_RESERVE + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException(
                        "One Research requirement group exceeds the chunk byte budget");
            }
            if (!current.empty()
                    && current.estimatedBytes + bytes
                            > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new Chunk();
            }
            current.requirementGroups.add(group);
            current.estimatedBytes += bytes;
        }
        for (WireGroup group : wireGroups) {
            int bytes = estimatedGroupBytes(group);
            if (HEADER_RESERVE + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException("One Research tree group exceeds the chunk byte budget");
            }
            if (!current.empty() && current.estimatedBytes + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new Chunk();
            }
            current.groups.add(group);
            current.estimatedBytes += bytes;
        }
        for (WireTechTree techTree : wireTechTrees) {
            int bytes = estimatedTechTreeBytes(techTree);
            if (HEADER_RESERVE + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException("One Research Tech Tree exceeds the chunk byte budget");
            }
            if (!current.empty() && current.estimatedBytes + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new Chunk();
            }
            current.techTrees.add(techTree);
            current.estimatedBytes += bytes;
        }
        if (!current.empty() || chunks.isEmpty()) {
            chunks.add(current);
        }
        if (chunks.size() > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT) {
            throw new IllegalArgumentException("Research tree requires too many synchronization chunks");
        }

        List<SyncResearchTreePacket> packets = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            packets.add(new SyncResearchTreePacket(
                    syncId,
                    index,
                    chunks.size(),
                    graph.nodes().size(),
                    graph.edges().size(),
                    wireRequirementGroups.size(),
                    presentation.groups().size(),
                    memberCount,
                    wireTechTrees.size(),
                    chunk.nodes,
                    chunk.edges,
                    chunk.requirementGroups,
                    chunk.groups,
                    chunk.techTrees));
        }
        return List.copyOf(packets);
    }

    int estimatedPayloadBytes() {
        int bytes = HEADER_RESERVE;
        for (ResearchTreeGraph.Node node : nodes) {
            bytes += estimatedNodeBytes(node);
        }
        bytes += edges.size() * EDGE_RESERVE;
        for (WireRequirementGroup group : requirementGroups) {
            bytes += estimatedRequirementGroupBytes(group);
        }
        for (WireGroup group : groups) {
            bytes += estimatedGroupBytes(group);
        }
        for (WireTechTree techTree : techTrees) {
            bytes += estimatedTechTreeBytes(techTree);
        }
        return bytes;
    }

    private void validateCommonState() {
        validateChunkMetadata(chunkIndex, chunkCount);
        validateTotals(
                totalNodes,
                totalEdges,
                totalRequirementGroups,
                totalGroups,
                totalMembers,
                totalTechTrees);
        int chunkMembers = groups.stream().mapToInt(group -> group.members().size()).sum();
        if (!edges.isEmpty()) {
            throw new IllegalArgumentException(
                    "Protocol 39 Research tree chunks must derive edges from requirement groups");
        }
        if (nodes.size() > totalNodes || edges.size() > totalEdges
                || requirementGroups.size() > totalRequirementGroups
                || groups.size() > totalGroups || chunkMembers > totalMembers
                || techTrees.size() > totalTechTrees) {
            throw new IllegalArgumentException("Research tree chunk contains more entries than declared");
        }
        for (WireEdge edge : edges) {
            if (edge.prerequisiteOrdinal() >= totalNodes || edge.dependentOrdinal() >= totalNodes) {
                throw new IllegalArgumentException("Research tree edge ordinal is outside the node table");
            }
        }
        for (WireRequirementGroup group : requirementGroups) {
            group.validateOrdinals(totalNodes);
        }
        for (WireGroup group : groups) {
            if (group.iconOrdinal() >= totalNodes
                    || group.members().stream().anyMatch(member -> member.nodeOrdinal() >= totalNodes)) {
                throw new IllegalArgumentException("Research tree group ordinal is outside the node table");
            }
        }
        for (WireTechTree techTree : techTrees) {
            techTree.validateOrdinals(totalNodes);
        }
    }

    private static ResearchTreeGraph.Node readNode(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        ResourceLocation blueprintId = readId(buf);
        String nameKey = buf.readUtf(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
        String itemType = buf.readUtf(BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH);
        ResourceLocation displaySlotId = readId(buf);
        int visibilityOrdinal = buf.readUnsignedByte();
        JournalVisibility[] visibilities = JournalVisibility.values();
        if (visibilityOrdinal >= visibilities.length
                || !visibilities[visibilityOrdinal].appearsInTree()) {
            throw new IllegalArgumentException("Invalid synchronized Research tree visibility");
        }
        int flags = buf.readUnsignedByte();
        if ((flags & ~7) != 0) {
            throw new IllegalArgumentException("Invalid synchronized Research tree flags");
        }
        int availabilityOrdinal = buf.readUnsignedByte();
        ResearchTreeGraph.Availability[] availabilities = ResearchTreeGraph.Availability.values();
        if (availabilityOrdinal >= availabilities.length) {
            throw new IllegalArgumentException("Invalid synchronized Research tree availability");
        }
        return new ResearchTreeGraph.Node(
                ordinal,
                blueprintId,
                nameKey,
                itemType,
                displaySlotId,
                visibilities[visibilityOrdinal],
                (flags & 1) != 0,
                (flags & 2) != 0,
                (flags & 4) != 0,
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                availabilities[availabilityOrdinal]);
    }

    private static void writeNode(FriendlyByteBuf buf, ResearchTreeGraph.Node node) {
        buf.writeVarInt(node.ordinal());
        writeId(buf, node.blueprintId());
        buf.writeUtf(node.nameKey(), BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
        buf.writeUtf(node.itemType(), BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH);
        writeId(buf, node.displaySlotId());
        buf.writeByte(node.visibility().ordinal());
        int flags = (node.learned() ? 1 : 0)
                | (node.discovered() ? 2 : 0)
                | (node.policyEligible() ? 4 : 0);
        buf.writeByte(flags);
        buf.writeByte(node.availability().ordinal());
        buf.writeVarInt(node.pointCost());
        buf.writeVarInt(node.ingredientTypeCount());
        buf.writeVarInt(node.prerequisiteCount());
        buf.writeVarInt(node.hiddenPrerequisiteCount());
    }

    private static WireRequirementGroup readRequirementGroup(
            FriendlyByteBuf buf,
            int totalNodes) {
        int dependentOrdinal = buf.readVarInt();
        int groupOrdinal = buf.readVarInt();
        int alternativeCount = readBoundedCount(
                buf,
                com.gamergaming.taczweaponblueprints.resource.research
                        .ResearchPrerequisiteGroup.MAX_ALTERNATIVES,
                "Research requirement alternative");
        List<Integer> alternativeOrdinals = new ArrayList<>(alternativeCount);
        for (int index = 0; index < alternativeCount; index++) {
            alternativeOrdinals.add(buf.readVarInt());
        }
        WireRequirementGroup group = new WireRequirementGroup(
                dependentOrdinal,
                groupOrdinal,
                alternativeOrdinals,
                buf.readVarInt(),
                buf.readBoolean(),
                buf.readBoolean());
        group.validateOrdinals(totalNodes);
        return group;
    }

    private static void writeRequirementGroup(
            FriendlyByteBuf buf,
            WireRequirementGroup group) {
        buf.writeVarInt(group.dependentOrdinal());
        buf.writeVarInt(group.groupOrdinal());
        buf.writeVarInt(group.visibleAlternativeOrdinals().size());
        group.visibleAlternativeOrdinals().forEach(buf::writeVarInt);
        buf.writeVarInt(group.hiddenAlternativeCount());
        buf.writeBoolean(group.satisfactionDisclosed());
        buf.writeBoolean(group.satisfied());
    }

    private static WireGroup readGroup(FriendlyByteBuf buf, int maximumMembers) {
        ResourceLocation groupId = readId(buf);
        String title = buf.readUtf(ResearchTreeGroupDefinition.MAX_TITLE_LENGTH);
        Optional<String> translationKey = buf.readBoolean()
                ? Optional.of(buf.readUtf(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH))
                : Optional.empty();
        int iconOrdinal = buf.readVarInt() - 1;
        int order = buf.readVarInt();
        int kindOrdinal = buf.readUnsignedByte();
        ResearchTreePresentation.Kind[] kinds = ResearchTreePresentation.Kind.values();
        if (kindOrdinal >= kinds.length) {
            throw new IllegalArgumentException("Invalid synchronized Research tree group kind");
        }
        boolean includedInOverview = buf.readBoolean();
        int memberCount = readBoundedCount(buf, maximumMembers, "Research tree group member");
        List<WireMember> members = new ArrayList<>(memberCount);
        for (int index = 0; index < memberCount; index++) {
            members.add(new WireMember(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));
        }
        return new WireGroup(
                groupId,
                title,
                translationKey,
                iconOrdinal,
                order,
                kinds[kindOrdinal],
                includedInOverview,
                members);
    }

    private static void writeGroup(FriendlyByteBuf buf, WireGroup group) {
        writeId(buf, group.id());
        buf.writeUtf(group.title(), ResearchTreeGroupDefinition.MAX_TITLE_LENGTH);
        buf.writeBoolean(group.translationKey().isPresent());
        group.translationKey().ifPresent(value ->
                buf.writeUtf(value, BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH));
        buf.writeVarInt(group.iconOrdinal() + 1);
        buf.writeVarInt(group.order());
        buf.writeByte(group.kind().ordinal());
        buf.writeBoolean(group.includedInOverview());
        buf.writeVarInt(group.members().size());
        group.members().forEach(member -> {
            buf.writeVarInt(member.nodeOrdinal());
            buf.writeVarInt(member.rank());
            buf.writeVarInt(member.orderInRank());
        });
    }

    private static WireTechTree readTechTree(FriendlyByteBuf buf, int maximumMembers) {
        ResourceLocation treeId = readId(buf);
        String title = buf.readUtf(ResearchTechTreeDefinition.MAX_TITLE_LENGTH);
        Optional<String> translationKey = readOptionalTranslationKey(buf);
        int iconOrdinal = buf.readVarInt() - 1;
        int maxNodesPerLayer = buf.readVarInt();
        if (maxNodesPerLayer
                        < ResearchTechTreeDefinition.LayoutDefinition.MIN_NODES_PER_LAYER
                || maxNodesPerLayer
                        > ResearchTechTreeDefinition.LayoutDefinition.MAX_NODES_PER_LAYER) {
            throw new IllegalArgumentException(
                    "Invalid synchronized Research Tech Tree layer capacity");
        }
        int tierCount = readBoundedCount(buf, Tier.values().length, "Research Tech Tree tier");
        List<WireTechTier> tiers = new ArrayList<>(tierCount);
        for (int index = 0; index < tierCount; index++) {
            tiers.add(new WireTechTier(
                    readTier(buf),
                    buf.readUtf(ResearchTechTreeDefinition.MAX_TITLE_LENGTH),
                    readOptionalTranslationKey(buf)));
        }
        int bandCount = readBoundedCount(
                buf,
                ResearchTechTreeDefinition.MAX_PRESENTATION_BANDS,
                "Research Tech Tree progression band");
        List<WireTechBand> bands = new ArrayList<>(bandCount);
        for (int index = 0; index < bandCount; index++) {
            bands.add(new WireTechBand(
                    readId(buf),
                    buf.readUtf(ResearchTechTreeDefinition.MAX_TITLE_LENGTH),
                    readOptionalTranslationKey(buf),
                    buf.readBoolean() ? Optional.of(buf.readInt()) : Optional.empty(),
                    buf.readBoolean() ? Optional.of(readId(buf)) : Optional.empty()));
        }
        int domainCount = readBoundedCount(buf, Domain.values().length, "Research Tech Tree domain");
        List<WireTechDomain> domains = new ArrayList<>(domainCount);
        int remainingMembers = maximumMembers;
        for (int domainIndex = 0; domainIndex < domainCount; domainIndex++) {
            Domain domain = readDomain(buf);
            String domainTitle = buf.readUtf(ResearchTechTreeDefinition.MAX_TITLE_LENGTH);
            Optional<String> domainTranslationKey = readOptionalTranslationKey(buf);
            int domainIconOrdinal = buf.readVarInt() - 1;
            int laneCount = readBoundedCount(
                    buf,
                    ResearchTechTreeDefinition.MAX_LANES_PER_DOMAIN,
                    "Research Tech Tree lane");
            List<WireTechLane> lanes = new ArrayList<>(laneCount);
            for (int laneIndex = 0; laneIndex < laneCount; laneIndex++) {
                ResourceLocation laneId = readId(buf);
                String laneTitle = buf.readUtf(ResearchTechTreeDefinition.MAX_TITLE_LENGTH);
                Optional<String> laneTranslationKey = readOptionalTranslationKey(buf);
                int laneIconOrdinal = buf.readVarInt() - 1;
                int order = buf.readVarInt();
                int memberCount = readBoundedCount(
                        buf, remainingMembers, "Research Tech Tree lane member");
                List<WireTechMember> members = new ArrayList<>(memberCount);
                for (int memberIndex = 0; memberIndex < memberCount; memberIndex++) {
                    int nodeOrdinal = buf.readVarInt();
                    int rank = buf.readVarInt();
                    long siblingOrder = buf.readVarLong();
                    Optional<ResourceLocation> bandId = buf.readBoolean()
                            ? Optional.of(readId(buf))
                            : Optional.empty();
                    PlacementOrigin origin = readPlacementOrigin(buf);
                    Optional<WeaponRating> rating = buf.readBoolean()
                            ? Optional.of(new WeaponRating(
                                    buf.readUnsignedByte(),
                                    buf.readUnsignedByte(),
                                    buf.readUnsignedByte()))
                            : Optional.empty();
                    Optional<ResearchTechTreePresentation.AutomaticBranchPlacement>
                            automaticBranch = buf.readBoolean()
                                    ? Optional.of(new ResearchTechTreePresentation
                                            .AutomaticBranchPlacement(
                                                    buf.readVarInt(),
                                                    buf.readVarInt(),
                                                    buf.readVarInt(),
                                                    buf.readVarInt()))
                                    : Optional.empty();
                    members.add(new WireTechMember(
                            nodeOrdinal,
                            rank,
                            siblingOrder,
                            bandId,
                            origin,
                            rating,
                            automaticBranch));
                }
                remainingMembers -= memberCount;
                lanes.add(new WireTechLane(
                        laneId,
                        laneTitle,
                        laneTranslationKey,
                        laneIconOrdinal,
                        order,
                        members));
            }
            domains.add(new WireTechDomain(
                    domain,
                    domainTitle,
                    domainTranslationKey,
                    domainIconOrdinal,
                    lanes));
        }
        return new WireTechTree(
                treeId,
                title,
                translationKey,
                iconOrdinal,
                maxNodesPerLayer,
                tiers,
                bands,
                domains);
    }

    private static void writeTechTree(FriendlyByteBuf buf, WireTechTree tree) {
        writeId(buf, tree.treeId());
        buf.writeUtf(tree.title(), ResearchTechTreeDefinition.MAX_TITLE_LENGTH);
        writeOptionalTranslationKey(buf, tree.translationKey());
        buf.writeVarInt(tree.iconOrdinal() + 1);
        buf.writeVarInt(tree.maxNodesPerLayer());
        buf.writeVarInt(tree.tiers().size());
        tree.tiers().forEach(tier -> {
            buf.writeByte(tier.tier().ordinal());
            buf.writeUtf(tier.title(), ResearchTechTreeDefinition.MAX_TITLE_LENGTH);
            writeOptionalTranslationKey(buf, tier.translationKey());
        });
        buf.writeVarInt(tree.bands().size());
        tree.bands().forEach(band -> {
            writeId(buf, band.id());
            buf.writeUtf(band.title(), ResearchTechTreeDefinition.MAX_TITLE_LENGTH);
            writeOptionalTranslationKey(buf, band.translationKey());
            buf.writeBoolean(band.color().isPresent());
            band.color().ifPresent(buf::writeInt);
            buf.writeBoolean(band.icon().isPresent());
            band.icon().ifPresent(value -> writeId(buf, value));
        });
        buf.writeVarInt(tree.domains().size());
        tree.domains().forEach(domain -> {
            buf.writeByte(domain.domain().ordinal());
            buf.writeUtf(domain.title(), ResearchTechTreeDefinition.MAX_TITLE_LENGTH);
            writeOptionalTranslationKey(buf, domain.translationKey());
            buf.writeVarInt(domain.iconOrdinal() + 1);
            buf.writeVarInt(domain.lanes().size());
            domain.lanes().forEach(lane -> {
                writeId(buf, lane.id());
                buf.writeUtf(lane.title(), ResearchTechTreeDefinition.MAX_TITLE_LENGTH);
                writeOptionalTranslationKey(buf, lane.translationKey());
                buf.writeVarInt(lane.iconOrdinal() + 1);
                buf.writeVarInt(lane.order());
                buf.writeVarInt(lane.members().size());
                lane.members().forEach(member -> {
                    buf.writeVarInt(member.nodeOrdinal());
                    buf.writeVarInt(member.rank());
                    buf.writeVarLong(member.siblingOrder());
                    buf.writeBoolean(member.bandId().isPresent());
                    member.bandId().ifPresent(value -> writeId(buf, value));
                    buf.writeByte(member.origin().ordinal());
                    buf.writeBoolean(member.rating().isPresent());
                    member.rating().ifPresent(rating -> {
                        buf.writeByte(rating.combat());
                        buf.writeByte(rating.utility());
                        buf.writeByte(rating.appeal());
                    });
                    buf.writeBoolean(member.automaticBranch().isPresent());
                    member.automaticBranch().ifPresent(branch -> {
                        buf.writeVarInt(branch.branchIndex());
                        buf.writeVarInt(branch.rankIndex());
                        buf.writeVarInt(branch.familyStartIndex());
                        buf.writeVarInt(branch.transitionEndIndex());
                    });
                });
            });
        });
    }

    private static Optional<String> readOptionalTranslationKey(FriendlyByteBuf buf) {
        return buf.readBoolean()
                ? Optional.of(buf.readUtf(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH))
                : Optional.empty();
    }

    private static void writeOptionalTranslationKey(
            FriendlyByteBuf buf,
            Optional<String> translationKey) {
        buf.writeBoolean(translationKey.isPresent());
        translationKey.ifPresent(value ->
                buf.writeUtf(value, BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH));
    }

    private static Tier readTier(FriendlyByteBuf buf) {
        int ordinal = buf.readUnsignedByte();
        if (ordinal >= Tier.values().length) {
            throw new IllegalArgumentException("Invalid synchronized Research Tech Tree tier");
        }
        return Tier.values()[ordinal];
    }

    private static Domain readDomain(FriendlyByteBuf buf) {
        int ordinal = buf.readUnsignedByte();
        if (ordinal >= Domain.values().length) {
            throw new IllegalArgumentException("Invalid synchronized Research Tech Tree domain");
        }
        return Domain.values()[ordinal];
    }

    private static PlacementOrigin readPlacementOrigin(FriendlyByteBuf buf) {
        int ordinal = buf.readUnsignedByte();
        if (ordinal >= PlacementOrigin.values().length) {
            throw new IllegalArgumentException(
                    "Invalid synchronized Research Tech Tree placement origin");
        }
        return PlacementOrigin.values()[ordinal];
    }

    private static int estimatedNodeBytes(ResearchTreeGraph.Node node) {
        return NODE_FIXED_RESERVE
                + BlueprintSyncLimits.encodedUtfBytes(node.blueprintId().toString())
                + BlueprintSyncLimits.encodedUtfBytes(node.nameKey())
                + BlueprintSyncLimits.encodedUtfBytes(node.itemType())
                + BlueprintSyncLimits.encodedUtfBytes(node.displaySlotId().toString());
    }

    private static int estimatedGroupBytes(WireGroup group) {
        int bytes = GROUP_FIXED_RESERVE
                + BlueprintSyncLimits.encodedUtfBytes(group.id().toString())
                + BlueprintSyncLimits.encodedUtfBytes(group.title())
                + group.members().size() * MEMBER_RESERVE;
        return bytes + group.translationKey()
                .map(BlueprintSyncLimits::encodedUtfBytes)
                .orElse(0);
    }

    private static int estimatedRequirementGroupBytes(WireRequirementGroup group) {
        return REQUIREMENT_GROUP_FIXED_RESERVE
                + group.visibleAlternativeOrdinals().size()
                        * REQUIREMENT_ALTERNATIVE_RESERVE;
    }

    private static int estimatedTechTreeBytes(WireTechTree tree) {
        int bytes = TECH_TREE_FIXED_RESERVE
                + BlueprintSyncLimits.encodedUtfBytes(tree.treeId().toString())
                + BlueprintSyncLimits.encodedUtfBytes(tree.title())
                + tree.translationKey().map(BlueprintSyncLimits::encodedUtfBytes).orElse(0);
        for (WireTechTier tier : tree.tiers()) {
            bytes += 8 + BlueprintSyncLimits.encodedUtfBytes(tier.title())
                    + tier.translationKey().map(BlueprintSyncLimits::encodedUtfBytes).orElse(0);
        }
        for (WireTechBand band : tree.bands()) {
            bytes += 14
                    + BlueprintSyncLimits.encodedUtfBytes(band.id().toString())
                    + BlueprintSyncLimits.encodedUtfBytes(band.title())
                    + band.translationKey()
                            .map(BlueprintSyncLimits::encodedUtfBytes).orElse(0)
                    + band.icon()
                            .map(value -> BlueprintSyncLimits.encodedUtfBytes(value.toString()))
                            .orElse(0);
        }
        for (WireTechDomain domain : tree.domains()) {
            bytes += TECH_DOMAIN_FIXED_RESERVE
                    + BlueprintSyncLimits.encodedUtfBytes(domain.title())
                    + domain.translationKey().map(BlueprintSyncLimits::encodedUtfBytes).orElse(0);
            for (WireTechLane lane : domain.lanes()) {
                bytes += TECH_LANE_FIXED_RESERVE
                        + BlueprintSyncLimits.encodedUtfBytes(lane.id().toString())
                        + BlueprintSyncLimits.encodedUtfBytes(lane.title())
                        + lane.translationKey().map(BlueprintSyncLimits::encodedUtfBytes).orElse(0)
                        + lane.members().stream().mapToInt(member -> TECH_MEMBER_RESERVE
                                + member.bandId()
                                        .map(value -> BlueprintSyncLimits.encodedUtfBytes(
                                                value.toString()))
                                        .orElse(0)).sum();
            }
        }
        return bytes;
    }

    private static ResourceLocation readId(FriendlyByteBuf buf) {
        ResourceLocation id = ResourceLocation.tryParse(
                buf.readUtf(BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH));
        if (id == null) {
            throw new IllegalArgumentException("Invalid synchronized Research tree ID");
        }
        return id;
    }

    private static void writeId(FriendlyByteBuf buf, ResourceLocation id) {
        buf.writeUtf(id.toString(), BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH);
    }

    private static int ordinalFor(Map<ResourceLocation, Integer> ordinals, ResourceLocation nodeId) {
        Integer ordinal = ordinals.get(nodeId);
        if (ordinal == null) {
            throw new IllegalArgumentException("Research tree publication references an unknown node");
        }
        return ordinal;
    }

    private static int readBoundedCount(FriendlyByteBuf buf, int maximum, String description) {
        int count = buf.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("Invalid " + description + " count: " + count);
        }
        return count;
    }

    private static void validateChunkMetadata(int chunkIndex, int chunkCount) {
        if (chunkCount < 1 || chunkCount > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT
                || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "Invalid Research tree synchronization chunk " + chunkIndex + " of " + chunkCount);
        }
    }

    private static void validateTotals(
            int totalNodes,
            int totalEdges,
            int totalRequirementGroups,
            int totalGroups,
            int totalMembers,
            int totalTechTrees) {
        if (totalNodes < 0 || totalNodes > ResearchTreeGraph.MAX_NODES
                || totalEdges < 0 || totalEdges > ResearchTreeGraph.MAX_EDGES
                || totalRequirementGroups < 0
                || totalRequirementGroups > ResearchTreeGraph.MAX_REQUIREMENT_GROUPS
                || totalGroups < 0 || totalGroups > ResearchTreePresentation.MAX_GROUPS
                || totalMembers < 0 || totalMembers > totalNodes
                || totalTechTrees < 0 || totalTechTrees > 1
                || (totalNodes == 0 && totalTechTrees != 0)
                || totalGroups > totalMembers
                || (totalGroups == 0) != (totalMembers == 0)) {
            throw new IllegalArgumentException("Invalid Research tree synchronization totals");
        }
    }

    private static final class Chunk {
        private final List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        private final List<WireEdge> edges = new ArrayList<>();
        private final List<WireRequirementGroup> requirementGroups = new ArrayList<>();
        private final List<WireGroup> groups = new ArrayList<>();
        private final List<WireTechTree> techTrees = new ArrayList<>();
        private int estimatedBytes = HEADER_RESERVE;

        private boolean empty() {
            return nodes.isEmpty() && edges.isEmpty() && requirementGroups.isEmpty()
                    && groups.isEmpty() && techTrees.isEmpty();
        }
    }

    private record WireEdge(int prerequisiteOrdinal, int dependentOrdinal) {
        private WireEdge {
            if (prerequisiteOrdinal < 0 || dependentOrdinal < 0
                    || prerequisiteOrdinal >= ResearchTreeGraph.MAX_NODES
                    || dependentOrdinal >= ResearchTreeGraph.MAX_NODES
                    || prerequisiteOrdinal == dependentOrdinal) {
                throw new IllegalArgumentException("Invalid synchronized Research tree edge");
            }
        }
    }

    private record WireRequirementGroup(
            int dependentOrdinal,
            int groupOrdinal,
            List<Integer> visibleAlternativeOrdinals,
            int hiddenAlternativeCount,
            boolean satisfactionDisclosed,
            boolean satisfied) {
        private WireRequirementGroup {
            if (dependentOrdinal < 0
                    || dependentOrdinal >= ResearchTreeGraph.MAX_NODES
                    || groupOrdinal < 0
                    || groupOrdinal >= com.gamergaming.taczweaponblueprints.resource.research
                            .ResearchRequirements.MAX_GROUPS
                    || visibleAlternativeOrdinals == null
                    || visibleAlternativeOrdinals.stream()
                            .anyMatch(value -> value == null || value < 0
                                    || value >= ResearchTreeGraph.MAX_NODES)
                    || visibleAlternativeOrdinals.stream().distinct().count()
                            != visibleAlternativeOrdinals.size()
                    || hiddenAlternativeCount < 0
                    || hiddenAlternativeCount
                            > com.gamergaming.taczweaponblueprints.resource.research
                                    .ResearchPrerequisiteGroup.MAX_ALTERNATIVES
                    || visibleAlternativeOrdinals.isEmpty()
                            && hiddenAlternativeCount == 0
                    || visibleAlternativeOrdinals.size() + hiddenAlternativeCount
                            > com.gamergaming.taczweaponblueprints.resource.research
                                    .ResearchPrerequisiteGroup.MAX_ALTERNATIVES
                    || !satisfactionDisclosed && satisfied) {
                throw new IllegalArgumentException(
                        "Invalid synchronized Research requirement group");
            }
            visibleAlternativeOrdinals = List.copyOf(visibleAlternativeOrdinals);
            if (visibleAlternativeOrdinals.contains(dependentOrdinal)) {
                throw new IllegalArgumentException(
                        "Synchronized Research requirement group contains a self alternative");
            }
        }

        private static WireRequirementGroup from(
                ResearchTreeGraph.RequirementGroup group,
                Map<ResourceLocation, Integer> ordinals) {
            if (group.externalAlternativeCount() != 0) {
                throw new IllegalArgumentException(
                        "Research tree synchronization requires a full, unprojected requirement graph");
            }
            return new WireRequirementGroup(
                    ordinalFor(ordinals, group.dependentId()),
                    group.ordinal(),
                    group.visibleAlternativeIds().stream()
                            .map(id -> ordinalFor(ordinals, id))
                            .toList(),
                    group.hiddenAlternativeCount(),
                    group.satisfactionDisclosed(),
                    group.satisfied());
        }

        private void validateOrdinals(int totalNodes) {
            if (dependentOrdinal >= totalNodes
                    || visibleAlternativeOrdinals.stream()
                            .anyMatch(ordinal -> ordinal >= totalNodes)) {
                throw new IllegalArgumentException(
                        "Research requirement group ordinal is outside the node table");
            }
        }

        private ResearchTreeGraph.RequirementGroup resolve(
                List<ResearchTreeGraph.Node> nodes) {
            return new ResearchTreeGraph.RequirementGroup(
                    nodes.get(dependentOrdinal).blueprintId(),
                    groupOrdinal,
                    visibleAlternativeOrdinals.stream()
                            .map(ordinal -> nodes.get(ordinal).blueprintId())
                            .toList(),
                    hiddenAlternativeCount,
                    satisfactionDisclosed,
                    satisfied);
        }
    }

    private record WireMember(int nodeOrdinal, int rank, int orderInRank) {
        private WireMember {
            if (nodeOrdinal < 0 || nodeOrdinal >= ResearchTreeGraph.MAX_NODES
                    || rank < 0 || rank >= ResearchTreeGraph.MAX_NODES
                    || orderInRank < 0 || orderInRank >= ResearchTreeGraph.MAX_NODES) {
                throw new IllegalArgumentException("Invalid synchronized Research tree group member");
            }
        }
    }

    private record WireGroup(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            int iconOrdinal,
            int order,
            ResearchTreePresentation.Kind kind,
            boolean includedInOverview,
            List<WireMember> members) {
        private static final Comparator<WireMember> MEMBER_ORDER = Comparator
                .comparingInt(WireMember::rank)
                .thenComparingInt(WireMember::orderInRank)
                .thenComparingInt(WireMember::nodeOrdinal);

        private WireGroup {
            if (id == null || title == null || translationKey == null || kind == null
                    || members == null || members.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Synchronized Research tree group fields cannot be null");
            }
            members = List.copyOf(members);
            if (id.toString().length() > BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH
                    || !validTitle(title)
                    || translationKey.filter(value -> !validTranslationKey(value)).isPresent()
                    || iconOrdinal < -1 || iconOrdinal >= ResearchTreeGraph.MAX_NODES
                    || order < 0 || order >= ResearchTreePresentation.MAX_GROUPS
                    || members.isEmpty() || members.size() > ResearchTreeGraph.MAX_NODES
                    || !members.equals(members.stream().sorted(MEMBER_ORDER).toList())) {
                throw new IllegalArgumentException("Invalid synchronized Research tree group");
            }
            Set<Integer> memberOrdinals = new LinkedHashSet<>();
            Map<Integer, Integer> nextOrderByRank = new LinkedHashMap<>();
            for (WireMember member : members) {
                if (!memberOrdinals.add(member.nodeOrdinal())) {
                    throw new IllegalArgumentException("Synchronized Research tree group has a duplicate member");
                }
                int expectedOrder = nextOrderByRank.getOrDefault(member.rank(), 0);
                if (member.orderInRank() != expectedOrder) {
                    throw new IllegalArgumentException("Synchronized Research sibling orders are not contiguous");
                }
                nextOrderByRank.put(member.rank(), expectedOrder + 1);
            }
            if (iconOrdinal >= 0 && !memberOrdinals.contains(iconOrdinal)) {
                throw new IllegalArgumentException("Synchronized Research tree icon is not a group member");
            }
            if (kind == ResearchTreePresentation.Kind.UNDISCLOSED
                    && (iconOrdinal >= 0
                    || !title.equals(ResearchTreePresentation.UNDISCLOSED_TITLE)
                    || !translationKey.equals(Optional.of(
                            ResearchTreePresentation.UNDISCLOSED_TRANSLATION_KEY)))) {
                throw new IllegalArgumentException("Synchronized Undisclosed group leaks identifying metadata");
            }
        }

        private static WireGroup from(
                ResearchTreePresentation.Group group,
                Map<ResourceLocation, Integer> ordinals) {
            List<WireMember> members = group.members().stream()
                    .map(member -> new WireMember(
                            ordinalFor(ordinals, member.nodeId()),
                            member.rank(),
                            member.orderInRank()))
                    .toList();
            int iconOrdinal = group.iconNodeId()
                    .map(id -> ordinalFor(ordinals, id))
                    .orElse(-1);
            return new WireGroup(
                    group.id(),
                    group.title(),
                    group.translationKey(),
                    iconOrdinal,
                    group.order(),
                    group.kind(),
                    group.includedInOverview(),
                    members);
        }

        private ResearchTreePresentation.Group resolve(List<ResearchTreeGraph.Node> nodes) {
            return new ResearchTreePresentation.Group(
                    id,
                    title,
                    translationKey,
                    iconOrdinal < 0
                            ? Optional.empty()
                            : Optional.of(nodes.get(iconOrdinal).blueprintId()),
                    order,
                    kind,
                    includedInOverview,
                    members.stream()
                            .map(member -> new ResearchTreePresentation.Member(
                                    nodes.get(member.nodeOrdinal()).blueprintId(),
                                    member.rank(),
                                    member.orderInRank()))
                            .toList());
        }

        private static boolean validTitle(String value) {
            return !value.isBlank()
                    && value.equals(value.trim())
                    && value.length() <= ResearchTreeGroupDefinition.MAX_TITLE_LENGTH
                    && value.chars().noneMatch(Character::isISOControl);
        }

        private static boolean validTranslationKey(String value) {
            return !value.isBlank()
                    && value.length() <= BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH
                    && value.chars().noneMatch(character -> Character.isWhitespace(character)
                            || Character.isISOControl(character));
        }
    }

    private record WireTechTier(
            Tier tier,
            String title,
            Optional<String> translationKey) {
        private WireTechTier {
            if (tier == null || title == null || translationKey == null
                    || !WireGroup.validTitle(title)
                    || translationKey.filter(value -> !WireGroup.validTranslationKey(value)).isPresent()) {
                throw new IllegalArgumentException("Invalid synchronized Research Tech Tree tier");
            }
        }
    }

    private record WireTechBand(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            Optional<Integer> color,
            Optional<ResourceLocation> icon) {
        private WireTechBand {
            color = color == null ? Optional.empty() : color;
            icon = icon == null ? Optional.empty() : icon;
            if (id == null || title == null || translationKey == null
                    || id.toString().length() > BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH
                    || !WireGroup.validTitle(title)
                    || translationKey.filter(value ->
                            !WireGroup.validTranslationKey(value)).isPresent()
                    || color.filter(value -> value < 0 || value > 0xFFFFFF).isPresent()
                    || icon.filter(value -> value.toString().length()
                            > BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH).isPresent()) {
                throw new IllegalArgumentException(
                        "Invalid synchronized Research Tech Tree progression band");
            }
        }
    }

    private record WireTechMember(
            int nodeOrdinal,
            int rank,
            long siblingOrder,
            Optional<ResourceLocation> bandId,
            PlacementOrigin origin,
            Optional<WeaponRating> rating,
            Optional<ResearchTechTreePresentation.AutomaticBranchPlacement>
                    automaticBranch) {
        private WireTechMember {
            bandId = bandId == null ? Optional.empty() : bandId;
            rating = rating == null ? Optional.empty() : rating;
            automaticBranch = automaticBranch == null
                    ? Optional.empty() : automaticBranch;
            if (nodeOrdinal < 0 || nodeOrdinal >= ResearchTreeGraph.MAX_NODES
                    || origin == null
                    || rank < 0 || rank > ResearchTechTreeContract.MAX_PROGRESSION_RANK
                    || siblingOrder < 0
                    || origin != PlacementOrigin.AUTOMATIC && automaticBranch.isPresent()
                    || bandId.stream().anyMatch(value ->
                            value.toString().length() > BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH)) {
                throw new IllegalArgumentException("Invalid synchronized Research Tech Tree member");
            }
        }
    }

    private record WireTechLane(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            int iconOrdinal,
            int order,
            List<WireTechMember> members) {
        private static final Comparator<WireTechMember> MEMBER_ORDER = Comparator
                .comparingInt(WireTechMember::rank)
                .thenComparingLong(WireTechMember::siblingOrder)
                .thenComparingInt(WireTechMember::nodeOrdinal);

        private WireTechLane {
            if (id == null || title == null || translationKey == null || members == null
                    || members.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Synchronized Research Tech Tree lane fields cannot be null");
            }
            members = List.copyOf(members);
            if (id.toString().length() > BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH
                    || !WireGroup.validTitle(title)
                    || translationKey.filter(value -> !WireGroup.validTranslationKey(value)).isPresent()
                    || iconOrdinal < -1 || iconOrdinal >= ResearchTreeGraph.MAX_NODES
                    || order < 0 || order > ResearchTechTreeDefinition.MAX_ORDER
                    || members.isEmpty() || members.size() > ResearchTreeGraph.MAX_NODES
                    || !members.equals(members.stream().sorted(MEMBER_ORDER).toList())) {
                throw new IllegalArgumentException("Invalid synchronized Research Tech Tree lane");
            }
            Set<Integer> memberOrdinals = new LinkedHashSet<>();
            for (WireTechMember member : members) {
                if (!memberOrdinals.add(member.nodeOrdinal())) {
                    throw new IllegalArgumentException(
                            "Synchronized Research Tech Tree lane has a duplicate member");
                }
            }
            if (iconOrdinal >= 0 && !memberOrdinals.contains(iconOrdinal)) {
                throw new IllegalArgumentException(
                        "Synchronized Research Tech Tree lane icon is not a lane member");
            }
        }

        private static WireTechLane from(
                ResearchTechTreePresentation.LaneView lane,
                Map<ResourceLocation, Integer> ordinals) {
            return new WireTechLane(
                    lane.id(),
                    lane.title(),
                    lane.translationKey(),
                    lane.iconNodeId().map(id -> ordinalFor(ordinals, id)).orElse(-1),
                    lane.order(),
                    lane.members().stream()
                            .map(member -> new WireTechMember(
                                    ordinalFor(ordinals, member.nodeId()),
                                    member.rank(),
                                    member.siblingOrder(),
                                    member.bandId(),
                                    member.origin(),
                                    member.rating(),
                                    member.automaticBranch()))
                            .toList());
        }

        private ResearchTechTreePresentation.LaneView resolve(
                List<ResearchTreeGraph.Node> nodes) {
            return new ResearchTechTreePresentation.LaneView(
                    id,
                    title,
                    translationKey,
                    iconOrdinal < 0
                            ? Optional.empty()
                            : Optional.of(nodes.get(iconOrdinal).blueprintId()),
                    order,
                    members.stream()
                            .map(member -> new ResearchTechTreePresentation.Member(
                                    nodes.get(member.nodeOrdinal()).blueprintId(),
                                    member.rank(),
                                    member.siblingOrder(),
                                    member.bandId(),
                                    member.origin(),
                                    member.rating(),
                                    member.automaticBranch()))
                            .toList());
        }
    }

    private record WireTechDomain(
            Domain domain,
            String title,
            Optional<String> translationKey,
            int iconOrdinal,
            List<WireTechLane> lanes) {
        private WireTechDomain {
            if (domain == null || title == null || translationKey == null || lanes == null
                    || lanes.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Synchronized Research Tech Tree domain fields cannot be null");
            }
            lanes = List.copyOf(lanes);
            if (!WireGroup.validTitle(title)
                    || translationKey.filter(value -> !WireGroup.validTranslationKey(value)).isPresent()
                    || iconOrdinal < -1 || iconOrdinal >= ResearchTreeGraph.MAX_NODES
                    || lanes.isEmpty()
                    || lanes.size() > ResearchTechTreeDefinition.MAX_LANES_PER_DOMAIN
                    || !lanes.equals(lanes.stream().sorted(Comparator
                            .comparingInt(WireTechLane::order)
                            .thenComparing(value -> value.id().toString())).toList())) {
                throw new IllegalArgumentException("Invalid synchronized Research Tech Tree domain");
            }
            Set<ResourceLocation> laneIds = new LinkedHashSet<>();
            Set<Integer> memberOrdinals = new LinkedHashSet<>();
            for (WireTechLane lane : lanes) {
                if (!laneIds.add(lane.id())) {
                    throw new IllegalArgumentException(
                            "Synchronized Research Tech Tree domain has a duplicate lane");
                }
                for (WireTechMember member : lane.members()) {
                    if (!memberOrdinals.add(member.nodeOrdinal())) {
                        throw new IllegalArgumentException(
                                "Synchronized Research Tech Tree domain has a duplicate member");
                    }
                    if (domain != Domain.WEAPONS
                            && (member.rating().isPresent()
                                    || member.origin() == PlacementOrigin.AUTOMATIC)) {
                        throw new IllegalArgumentException(
                                "Synchronized automatic placement or rating is outside Weapons");
                    }
                }
            }
            if (iconOrdinal >= 0 && !memberOrdinals.contains(iconOrdinal)) {
                throw new IllegalArgumentException(
                        "Synchronized Research Tech Tree domain icon is not a domain member");
            }
        }

        private static WireTechDomain from(
                ResearchTechTreePresentation.DomainView domain,
                Map<ResourceLocation, Integer> ordinals) {
            return new WireTechDomain(
                    domain.domain(),
                    domain.title(),
                    domain.translationKey(),
                    domain.iconNodeId().map(id -> ordinalFor(ordinals, id)).orElse(-1),
                    domain.lanes().stream().map(lane -> WireTechLane.from(lane, ordinals)).toList());
        }

        private ResearchTechTreePresentation.DomainView resolve(
                List<ResearchTreeGraph.Node> nodes) {
            return new ResearchTechTreePresentation.DomainView(
                    domain,
                    title,
                    translationKey,
                    iconOrdinal < 0
                            ? Optional.empty()
                            : Optional.of(nodes.get(iconOrdinal).blueprintId()),
                    lanes.stream().map(lane -> lane.resolve(nodes)).toList());
        }
    }

    private record WireTechTree(
            ResourceLocation treeId,
            String title,
            Optional<String> translationKey,
            int iconOrdinal,
            int maxNodesPerLayer,
            List<WireTechTier> tiers,
            List<WireTechBand> bands,
            List<WireTechDomain> domains) {
        private WireTechTree {
            if (treeId == null || title == null || translationKey == null
                    || tiers == null || bands == null || domains == null
                    || tiers.stream().anyMatch(java.util.Objects::isNull)
                    || bands.stream().anyMatch(java.util.Objects::isNull)
                    || domains.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("Synchronized Research Tech Tree fields cannot be null");
            }
            tiers = List.copyOf(tiers);
            bands = List.copyOf(bands);
            domains = List.copyOf(domains);
            if (treeId.toString().length() > BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH
                    || !WireGroup.validTitle(title)
                    || translationKey.filter(value -> !WireGroup.validTranslationKey(value)).isPresent()
                    || iconOrdinal < -1 || iconOrdinal >= ResearchTreeGraph.MAX_NODES
                    || maxNodesPerLayer
                            < ResearchTechTreeDefinition.LayoutDefinition.MIN_NODES_PER_LAYER
                    || maxNodesPerLayer
                            > ResearchTechTreeDefinition.LayoutDefinition.MAX_NODES_PER_LAYER
                    || !tiers.isEmpty() && !tiers.stream().map(WireTechTier::tier)
                            .toList().equals(List.of(Tier.values()))
                    || bands.size() > ResearchTechTreeDefinition.MAX_PRESENTATION_BANDS
                    || bands.stream().map(WireTechBand::id).distinct().count()
                            != bands.size()
                    || domains.isEmpty() || domains.size() > Domain.values().length
                    || !domains.equals(domains.stream()
                            .sorted(Comparator.comparingInt(value -> value.domain().ordinal()))
                            .toList())) {
                throw new IllegalArgumentException("Invalid synchronized Research Tech Tree");
            }
            Set<Domain> domainIds = new LinkedHashSet<>();
            Set<Integer> memberOrdinals = new LinkedHashSet<>();
            for (WireTechDomain domain : domains) {
                if (!domainIds.add(domain.domain())) {
                    throw new IllegalArgumentException(
                            "Synchronized Research Tech Tree has a duplicate domain");
                }
                for (WireTechLane lane : domain.lanes()) {
                    for (WireTechMember member : lane.members()) {
                        if (!memberOrdinals.add(member.nodeOrdinal())) {
                            throw new IllegalArgumentException(
                                    "Synchronized Research Tech Tree has a duplicate member");
                        }
                    }
                }
            }
            if (iconOrdinal >= 0 && !memberOrdinals.contains(iconOrdinal)) {
                throw new IllegalArgumentException(
                        "Synchronized Research Tech Tree icon is not a tree member");
            }
        }

        private static WireTechTree from(
                ResearchTechTreePresentation presentation,
                Map<ResourceLocation, Integer> ordinals) {
            if (!presentation.available()) {
                throw new IllegalArgumentException("Cannot synchronize an empty Research Tech Tree");
            }
            return new WireTechTree(
                    presentation.treeId().orElseThrow(),
                    presentation.title(),
                    presentation.translationKey(),
                    presentation.iconNodeId().map(id -> ordinalFor(ordinals, id)).orElse(-1),
                    presentation.maxNodesPerLayer(),
                    presentation.tiers().stream()
                            .map(tier -> new WireTechTier(
                                    tier.tier(), tier.title(), tier.translationKey()))
                            .toList(),
                    presentation.bands().stream()
                            .map(band -> new WireTechBand(
                                    band.id(),
                                    band.title(),
                                    band.translationKey(),
                                    band.color(),
                                    band.icon()))
                            .toList(),
                    presentation.domains().stream()
                            .map(domain -> WireTechDomain.from(domain, ordinals))
                            .toList());
        }

        private void validateOrdinals(int totalNodes) {
            if (iconOrdinal >= totalNodes) {
                throw new IllegalArgumentException(
                        "Research Tech Tree icon ordinal is outside the node table");
            }
            for (WireTechDomain domain : domains) {
                if (domain.iconOrdinal() >= totalNodes) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree domain icon ordinal is outside the node table");
                }
                for (WireTechLane lane : domain.lanes()) {
                    if (lane.iconOrdinal() >= totalNodes
                            || lane.members().stream()
                                    .anyMatch(member -> member.nodeOrdinal() >= totalNodes)) {
                        throw new IllegalArgumentException(
                                "Research Tech Tree lane ordinal is outside the node table");
                    }
                }
            }
        }

        private ResearchTechTreePresentation resolve(List<ResearchTreeGraph.Node> nodes) {
            return new ResearchTechTreePresentation(
                    Optional.of(treeId),
                    title,
                    translationKey,
                    iconOrdinal < 0
                            ? Optional.empty()
                            : Optional.of(nodes.get(iconOrdinal).blueprintId()),
                    tiers.stream()
                            .map(tier -> new ResearchTechTreePresentation.TierLabel(
                                    tier.tier(), tier.title(), tier.translationKey()))
                            .toList(),
                    bands.stream()
                            .map(band -> new ResearchTechTreePresentation.BandLabel(
                                    band.id(),
                                    band.title(),
                                    band.translationKey(),
                                    band.color(),
                                    band.icon()))
                            .toList(),
                    maxNodesPerLayer,
                    domains.stream().map(domain -> domain.resolve(nodes)).toList());
        }
    }

    static final class ClientAccumulator {
        private boolean initialized;
        private boolean completed;
        private long syncId;
        private int expectedChunks;
        private int totalNodes;
        private int totalEdges;
        private int totalRequirementGroups;
        private int totalGroups;
        private int totalMembers;
        private int totalTechTrees;
        private final Map<Integer, SyncResearchTreePacket> chunks = new TreeMap<>();

        synchronized Optional<ResearchTreePublication> accept(SyncResearchTreePacket packet) {
            if (initialized && Long.compare(packet.syncId, syncId) < 0) {
                return Optional.empty();
            }
            if (!initialized || packet.syncId != syncId) {
                initialized = true;
                completed = false;
                syncId = packet.syncId;
                expectedChunks = packet.chunkCount;
                totalNodes = packet.totalNodes;
                totalEdges = packet.totalEdges;
                totalRequirementGroups = packet.totalRequirementGroups;
                totalGroups = packet.totalGroups;
                totalMembers = packet.totalMembers;
                totalTechTrees = packet.totalTechTrees;
                chunks.clear();
            }
            if (completed) {
                return Optional.empty();
            }
            if (expectedChunks != packet.chunkCount
                    || totalNodes != packet.totalNodes
                    || totalEdges != packet.totalEdges
                    || totalRequirementGroups != packet.totalRequirementGroups
                    || totalGroups != packet.totalGroups
                    || totalMembers != packet.totalMembers
                    || totalTechTrees != packet.totalTechTrees) {
                throw new IllegalArgumentException("Inconsistent Research tree synchronization chunks");
            }
            SyncResearchTreePacket existing = chunks.putIfAbsent(packet.chunkIndex, packet);
            if (existing != null
                    && (!existing.nodes.equals(packet.nodes)
                    || !existing.edges.equals(packet.edges)
                    || !existing.requirementGroups.equals(packet.requirementGroups)
                    || !existing.groups.equals(packet.groups)
                    || !existing.techTrees.equals(packet.techTrees))) {
                chunks.clear();
                throw new IllegalArgumentException("Conflicting duplicate Research tree synchronization chunk");
            }

            long nodeCount = chunks.values().stream().mapToLong(chunk -> chunk.nodes.size()).sum();
            long edgeCount = chunks.values().stream().mapToLong(chunk -> chunk.edges.size()).sum();
            long requirementGroupCount = chunks.values().stream()
                    .mapToLong(chunk -> chunk.requirementGroups.size()).sum();
            long groupCount = chunks.values().stream().mapToLong(chunk -> chunk.groups.size()).sum();
            long memberCount = chunks.values().stream()
                    .flatMap(chunk -> chunk.groups.stream())
                    .mapToLong(group -> group.members().size())
                    .sum();
            long techTreeCount = chunks.values().stream()
                    .mapToLong(chunk -> chunk.techTrees.size())
                    .sum();
            if (nodeCount > totalNodes || edgeCount > totalEdges
                    || requirementGroupCount > totalRequirementGroups
                    || groupCount > totalGroups || memberCount > totalMembers
                    || techTreeCount > totalTechTrees) {
                chunks.clear();
                throw new IllegalArgumentException(
                        "Research tree synchronization exceeds its declared totals");
            }
            if (chunks.size() != expectedChunks) {
                return Optional.empty();
            }
            if (nodeCount != totalNodes
                    || requirementGroupCount != totalRequirementGroups
                    || groupCount != totalGroups || memberCount != totalMembers
                    || techTreeCount != totalTechTrees) {
                chunks.clear();
                throw new IllegalArgumentException("Completed Research tree synchronization has incorrect totals");
            }
            List<ResearchTreeGraph.Node> nodes = chunks.values().stream()
                    .flatMap(chunk -> chunk.nodes.stream())
                    .sorted(Comparator.comparingInt(ResearchTreeGraph.Node::ordinal))
                    .toList();
            for (int ordinal = 0; ordinal < nodes.size(); ordinal++) {
                if (nodes.get(ordinal).ordinal() != ordinal) {
                    chunks.clear();
                    throw new IllegalArgumentException(
                            "Completed Research tree synchronization has an invalid node table");
                }
            }
            List<ResearchTreeGraph.RequirementGroup> requirementGroups = chunks.values().stream()
                    .flatMap(chunk -> chunk.requirementGroups.stream())
                    .map(group -> group.resolve(nodes))
                    .sorted(Comparator
                            .comparing((ResearchTreeGraph.RequirementGroup group) ->
                                    group.dependentId().toString())
                    .thenComparingInt(ResearchTreeGraph.RequirementGroup::ordinal))
                    .toList();
            List<ResearchTreeGraph.Edge> edges = requirementGroups.stream()
                    .flatMap(group -> group.visibleAlternativeIds().stream()
                            .map(alternative -> new ResearchTreeGraph.Edge(
                                    alternative, group.dependentId())))
                    .distinct()
                    .toList();
            if (edges.size() != totalEdges) {
                chunks.clear();
                throw new IllegalArgumentException(
                        "Completed Research tree requirements do not match the declared edge total");
            }
            List<WireGroup> wireGroups = chunks.values().stream()
                    .flatMap(chunk -> chunk.groups.stream())
                    .sorted(Comparator.comparingInt(WireGroup::order))
                    .toList();
            List<WireTechTree> wireTechTrees = chunks.values().stream()
                    .flatMap(chunk -> chunk.techTrees.stream())
                    .toList();
            chunks.clear();
            ResearchTreeGraph graph = new ResearchTreeGraph(
                    nodes, edges, requirementGroups);
            ResearchTreePresentation presentation = new ResearchTreePresentation(
                    wireGroups.stream().map(group -> group.resolve(nodes)).toList());
            ResearchTechTreePresentation techTree = wireTechTrees.isEmpty()
                    ? ResearchTechTreePresentation.EMPTY
                    : wireTechTrees.get(0).resolve(nodes);
            ResearchTreePublication publication = new ResearchTreePublication(
                    graph, presentation, techTree);
            completed = true;
            return Optional.of(publication);
        }

        synchronized void clear() {
            initialized = false;
            completed = false;
            syncId = 0L;
            expectedChunks = 0;
            totalNodes = 0;
            totalEdges = 0;
            totalRequirementGroups = 0;
            totalGroups = 0;
            totalMembers = 0;
            totalTechTrees = 0;
            chunks.clear();
        }
    }
}
