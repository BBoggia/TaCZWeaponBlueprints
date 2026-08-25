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
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeGroupDefinition;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Chunked synchronization for one disclosure-safe graph and its matching groups. */
public final class SyncResearchTreePacket {
    private static final int HEADER_RESERVE = 96;
    private static final int NODE_FIXED_RESERVE = 40;
    private static final int EDGE_RESERVE = 10;
    private static final int GROUP_FIXED_RESERVE = 24;
    private static final int MEMBER_RESERVE = 15;
    private static final ClientAccumulator CLIENT_ACCUMULATOR = new ClientAccumulator();

    private final long syncId;
    private final int chunkIndex;
    private final int chunkCount;
    private final int totalNodes;
    private final int totalEdges;
    private final int totalGroups;
    private final int totalMembers;
    private final List<ResearchTreeGraph.Node> nodes;
    private final List<WireEdge> edges;
    private final List<WireGroup> groups;

    private SyncResearchTreePacket(
            long syncId,
            int chunkIndex,
            int chunkCount,
            int totalNodes,
            int totalEdges,
            int totalGroups,
            int totalMembers,
            List<ResearchTreeGraph.Node> nodes,
            List<WireEdge> edges,
            List<WireGroup> groups) {
        this.syncId = syncId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.totalNodes = totalNodes;
        this.totalEdges = totalEdges;
        this.totalGroups = totalGroups;
        this.totalMembers = totalMembers;
        this.nodes = nodes == null ? List.of() : List.copyOf(nodes);
        this.edges = edges == null ? List.of() : List.copyOf(edges);
        this.groups = groups == null ? List.of() : List.copyOf(groups);
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
        totalGroups = buf.readVarInt();
        totalMembers = buf.readVarInt();
        validateChunkMetadata(chunkIndex, chunkCount);
        validateTotals(totalNodes, totalEdges, totalGroups, totalMembers);

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
        int groupCount = readBoundedCount(buf, totalGroups, "Research tree group");
        List<WireGroup> decodedGroups = new ArrayList<>(groupCount);
        int remainingMembers = totalMembers;
        for (int index = 0; index < groupCount; index++) {
            WireGroup group = readGroup(buf, remainingMembers);
            decodedGroups.add(group);
            remainingMembers -= group.members().size();
        }
        nodes = List.copyOf(decodedNodes);
        edges = List.copyOf(decodedEdges);
        groups = List.copyOf(decodedGroups);
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
        buf.writeVarInt(totalGroups);
        buf.writeVarInt(totalMembers);
        buf.writeVarInt(nodes.size());
        nodes.forEach(node -> writeNode(buf, node));
        buf.writeVarInt(edges.size());
        edges.forEach(edge -> {
            buf.writeVarInt(edge.prerequisiteOrdinal());
            buf.writeVarInt(edge.dependentOrdinal());
        });
        buf.writeVarInt(groups.size());
        groups.forEach(group -> writeGroup(buf, group));
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
        graph.nodes().forEach(node -> ordinals.put(node.blueprintId(), node.ordinal()));
        List<WireEdge> wireEdges = graph.edges().stream()
                .map(edge -> new WireEdge(
                        ordinalFor(ordinals, edge.prerequisiteId()),
                        ordinalFor(ordinals, edge.dependentId())))
                .toList();
        List<WireGroup> wireGroups = presentation.groups().stream()
                .map(group -> WireGroup.from(group, ordinals))
                .toList();
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
                    presentation.groups().size(),
                    memberCount,
                    chunk.nodes,
                    chunk.edges,
                    chunk.groups));
        }
        return List.copyOf(packets);
    }

    int estimatedPayloadBytes() {
        int bytes = HEADER_RESERVE;
        for (ResearchTreeGraph.Node node : nodes) {
            bytes += estimatedNodeBytes(node);
        }
        bytes += edges.size() * EDGE_RESERVE;
        for (WireGroup group : groups) {
            bytes += estimatedGroupBytes(group);
        }
        return bytes;
    }

    private void validateCommonState() {
        validateChunkMetadata(chunkIndex, chunkCount);
        validateTotals(totalNodes, totalEdges, totalGroups, totalMembers);
        int chunkMembers = groups.stream().mapToInt(group -> group.members().size()).sum();
        if (nodes.size() > totalNodes || edges.size() > totalEdges
                || groups.size() > totalGroups || chunkMembers > totalMembers) {
            throw new IllegalArgumentException("Research tree chunk contains more entries than declared");
        }
        for (WireEdge edge : edges) {
            if (edge.prerequisiteOrdinal() >= totalNodes || edge.dependentOrdinal() >= totalNodes) {
                throw new IllegalArgumentException("Research tree edge ordinal is outside the node table");
            }
        }
        for (WireGroup group : groups) {
            if (group.iconOrdinal() >= totalNodes
                    || group.members().stream().anyMatch(member -> member.nodeOrdinal() >= totalNodes)) {
                throw new IllegalArgumentException("Research tree group ordinal is outside the node table");
            }
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
        buf.writeVarInt(group.members().size());
        group.members().forEach(member -> {
            buf.writeVarInt(member.nodeOrdinal());
            buf.writeVarInt(member.rank());
            buf.writeVarInt(member.orderInRank());
        });
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
            int totalGroups,
            int totalMembers) {
        if (totalNodes < 0 || totalNodes > ResearchTreeGraph.MAX_NODES
                || totalEdges < 0 || totalEdges > ResearchTreeGraph.MAX_EDGES
                || totalGroups < 0 || totalGroups > ResearchTreePresentation.MAX_GROUPS
                || totalMembers != totalNodes
                || totalGroups > totalMembers
                || (totalNodes == 0) != (totalGroups == 0)) {
            throw new IllegalArgumentException("Invalid Research tree synchronization totals");
        }
    }

    private static final class Chunk {
        private final List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        private final List<WireEdge> edges = new ArrayList<>();
        private final List<WireGroup> groups = new ArrayList<>();
        private int estimatedBytes = HEADER_RESERVE;

        private boolean empty() {
            return nodes.isEmpty() && edges.isEmpty() && groups.isEmpty();
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

    static final class ClientAccumulator {
        private boolean initialized;
        private boolean completed;
        private long syncId;
        private int expectedChunks;
        private int totalNodes;
        private int totalEdges;
        private int totalGroups;
        private int totalMembers;
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
                totalGroups = packet.totalGroups;
                totalMembers = packet.totalMembers;
                chunks.clear();
            }
            if (completed) {
                return Optional.empty();
            }
            if (expectedChunks != packet.chunkCount
                    || totalNodes != packet.totalNodes
                    || totalEdges != packet.totalEdges
                    || totalGroups != packet.totalGroups
                    || totalMembers != packet.totalMembers) {
                throw new IllegalArgumentException("Inconsistent Research tree synchronization chunks");
            }
            SyncResearchTreePacket existing = chunks.putIfAbsent(packet.chunkIndex, packet);
            if (existing != null
                    && (!existing.nodes.equals(packet.nodes)
                    || !existing.edges.equals(packet.edges)
                    || !existing.groups.equals(packet.groups))) {
                chunks.clear();
                throw new IllegalArgumentException("Conflicting duplicate Research tree synchronization chunk");
            }

            long nodeCount = chunks.values().stream().mapToLong(chunk -> chunk.nodes.size()).sum();
            long edgeCount = chunks.values().stream().mapToLong(chunk -> chunk.edges.size()).sum();
            long groupCount = chunks.values().stream().mapToLong(chunk -> chunk.groups.size()).sum();
            long memberCount = chunks.values().stream()
                    .flatMap(chunk -> chunk.groups.stream())
                    .mapToLong(group -> group.members().size())
                    .sum();
            if (nodeCount > totalNodes || edgeCount > totalEdges
                    || groupCount > totalGroups || memberCount > totalMembers) {
                chunks.clear();
                throw new IllegalArgumentException(
                        "Research tree synchronization exceeds its declared totals");
            }
            if (chunks.size() != expectedChunks) {
                return Optional.empty();
            }
            if (nodeCount != totalNodes || edgeCount != totalEdges
                    || groupCount != totalGroups || memberCount != totalMembers) {
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
            List<WireEdge> wireEdges = chunks.values().stream()
                    .flatMap(chunk -> chunk.edges.stream())
                    .toList();
            List<ResearchTreeGraph.Edge> edges = wireEdges.stream()
                    .map(edge -> new ResearchTreeGraph.Edge(
                            nodes.get(edge.prerequisiteOrdinal()).blueprintId(),
                            nodes.get(edge.dependentOrdinal()).blueprintId()))
                    .toList();
            List<WireGroup> wireGroups = chunks.values().stream()
                    .flatMap(chunk -> chunk.groups.stream())
                    .sorted(Comparator.comparingInt(WireGroup::order))
                    .toList();
            chunks.clear();
            ResearchTreeGraph graph = new ResearchTreeGraph(nodes, edges);
            ResearchTreePresentation presentation = new ResearchTreePresentation(
                    wireGroups.stream().map(group -> group.resolve(nodes)).toList());
            ResearchTreePublication publication = new ResearchTreePublication(graph, presentation);
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
            totalGroups = 0;
            totalMembers = 0;
            chunks.clear();
        }
    }
}
