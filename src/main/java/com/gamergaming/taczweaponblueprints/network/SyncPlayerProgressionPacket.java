package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.client.ClientBlueprintCatalog;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/**
 * Atomically synchronizes the durable, client-presentable player progression
 * that is independent of the active TaCZ recipe-filter snapshot.
 */
public final class SyncPlayerProgressionPacket {
    private static final ClientAccumulator CLIENT_ACCUMULATOR = new ClientAccumulator();

    private final long syncId;
    private final int chunkIndex;
    private final int chunkCount;
    private final int researchPoints;
    private final Set<String> learnedBlueprints;
    private final Set<String> discoveredBlueprints;

    private SyncPlayerProgressionPacket(
            long syncId,
            int chunkIndex,
            int chunkCount,
            int researchPoints,
            Collection<String> learnedBlueprints,
            Collection<String> discoveredBlueprints) {
        validateChunkMetadata(chunkIndex, chunkCount);
        validateResearchPoints(researchPoints);
        this.syncId = syncId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.researchPoints = researchPoints;
        this.learnedBlueprints = normalizedSnapshot(learnedBlueprints, "learned blueprint");
        this.discoveredBlueprints = normalizedSnapshot(discoveredBlueprints, "discovered blueprint");
        if (estimatedPayloadBytes() > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Player progression synchronization chunk exceeds the byte budget");
        }
    }

    public SyncPlayerProgressionPacket(FriendlyByteBuf buf) {
        int start = buf.readerIndex();
        syncId = buf.readLong();
        chunkIndex = buf.readVarInt();
        chunkCount = buf.readVarInt();
        validateChunkMetadata(chunkIndex, chunkCount);
        researchPoints = buf.readVarInt();
        validateResearchPoints(researchPoints);
        learnedBlueprints = readIds(buf, "learned blueprint");
        discoveredBlueprints = readIds(buf, "discovered blueprint");
        if (buf.readerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Player progression synchronization chunk exceeds the byte budget");
        }
    }

    static List<SyncPlayerProgressionPacket> split(
            Set<String> learnedBlueprints,
            Set<String> discoveredBlueprints,
            int researchPoints,
            long syncId) {
        Set<String> learned = normalizedSnapshot(learnedBlueprints, "learned blueprint");
        Set<String> discovered = normalizedSnapshot(discoveredBlueprints, "discovered blueprint");
        if (!discovered.containsAll(learned)) {
            throw new IllegalArgumentException("Learned blueprints must also be discovered before synchronization");
        }
        validateResearchPoints(researchPoints);

        List<ProgressionChunk> chunks = new ArrayList<>();
        ProgressionChunk current = new ProgressionChunk();
        for (String learnedId : learned) {
            if (!current.canAdd(learnedId)) {
                chunks.add(current);
                current = new ProgressionChunk();
            }
            current.learned().add(learnedId);
            current.addBytes(learnedId);
        }
        for (String discoveredId : discovered) {
            if (!current.canAdd(discoveredId)) {
                chunks.add(current);
                current = new ProgressionChunk();
            }
            current.discovered().add(discoveredId);
            current.addBytes(discoveredId);
        }
        if (!current.empty() || chunks.isEmpty()) {
            chunks.add(current);
        }

        List<SyncPlayerProgressionPacket> packets = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            ProgressionChunk chunk = chunks.get(index);
            packets.add(new SyncPlayerProgressionPacket(
                    syncId,
                    index,
                    chunks.size(),
                    researchPoints,
                    chunk.learned(),
                    chunk.discovered()));
        }
        return List.copyOf(packets);
    }

    public void toBytes(FriendlyByteBuf buf) {
        int start = buf.writerIndex();
        buf.writeLong(syncId);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(researchPoints);
        writeIds(buf, learnedBlueprints);
        writeIds(buf, discoveredBlueprints);
        if (buf.writerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Player progression synchronization chunk exceeds the byte budget");
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> CLIENT_ACCUMULATOR.accept(this).ifPresent(completed -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(capability -> {
                    if (!capability.replaceProgression(
                            completed.learnedBlueprints(),
                            completed.discoveredBlueprints(),
                            completed.researchPoints())) {
                        throw new IllegalArgumentException("Completed player progression snapshot is invalid");
                    }
                    ClientBlueprintCatalog.refreshOpenGunSmithScreen();
                });
            }
        }));
        ctx.get().setPacketHandled(true);
    }

    /** Drops any partial or completed snapshot retained from the previous connection. */
    public static void clearClientState() {
        CLIENT_ACCUMULATOR.clear();
    }

    int estimatedPayloadBytes() {
        int size = 8
                + BlueprintSyncLimits.varIntBytes(chunkIndex)
                + BlueprintSyncLimits.varIntBytes(chunkCount)
                + BlueprintSyncLimits.varIntBytes(researchPoints)
                + BlueprintSyncLimits.varIntBytes(learnedBlueprints.size())
                + BlueprintSyncLimits.varIntBytes(discoveredBlueprints.size());
        for (String id : learnedBlueprints) {
            size += BlueprintSyncLimits.encodedUtfBytes(id);
        }
        for (String id : discoveredBlueprints) {
            size += BlueprintSyncLimits.encodedUtfBytes(id);
        }
        return size;
    }

    Set<String> learnedEntries() {
        return learnedBlueprints;
    }

    Set<String> discoveredEntries() {
        return discoveredBlueprints;
    }

    int researchPoints() {
        return researchPoints;
    }

    private static Set<String> readIds(FriendlyByteBuf buf, String description) {
        int size = buf.readVarInt();
        if (size < 0 || size > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("Invalid " + description + " count: " + size);
        }
        TreeSet<String> decoded = new TreeSet<>();
        for (int index = 0; index < size; index++) {
            String rawId = buf.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
            String normalizedId = PlayerRecipeData.normalizeResourceId(rawId);
            if (normalizedId == null) {
                throw new IllegalArgumentException("Invalid " + description + " ID in synchronization payload");
            }
            decoded.add(normalizedId);
        }
        return Collections.unmodifiableSet(decoded);
    }

    private static void writeIds(FriendlyByteBuf buf, Set<String> ids) {
        buf.writeVarInt(ids.size());
        for (String id : ids) {
            buf.writeUtf(id, PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        }
    }

    private static Set<String> normalizedSnapshot(Collection<String> values, String description) {
        TreeSet<String> snapshot = new TreeSet<>();
        if (values != null) {
            if (values.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                throw new IllegalArgumentException(
                        "Too many " + description + " IDs to synchronize: " + values.size());
            }
            for (String value : values) {
                String normalizedId = PlayerRecipeData.normalizeResourceId(value);
                if (normalizedId == null) {
                    throw new IllegalArgumentException("Invalid " + description + " ID to synchronize");
                }
                snapshot.add(normalizedId);
            }
        }
        return Collections.unmodifiableSet(snapshot);
    }

    private static void validateResearchPoints(int points) {
        if (points < 0 || points > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("Invalid Research Point balance: " + points);
        }
    }

    private static void validateChunkMetadata(int chunkIndex, int chunkCount) {
        if (chunkCount < 1
                || chunkCount > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT
                || chunkIndex < 0
                || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "Invalid player progression synchronization chunk " + chunkIndex + " of " + chunkCount);
        }
    }

    record ProgressionSnapshot(
            Set<String> learnedBlueprints,
            Set<String> discoveredBlueprints,
            int researchPoints) {
    }

    private static final class ProgressionChunk {
        private final Set<String> learned = new TreeSet<>();
        private final Set<String> discovered = new TreeSet<>();
        private int estimatedBytes = BlueprintSyncLimits.CHUNK_HEADER_RESERVE;

        private Set<String> learned() {
            return learned;
        }

        private Set<String> discovered() {
            return discovered;
        }

        private boolean canAdd(String id) {
            return empty()
                    || estimatedBytes + BlueprintSyncLimits.encodedUtfBytes(id)
                    <= BlueprintSyncLimits.MAX_CHUNK_BYTES;
        }

        private void addBytes(String id) {
            estimatedBytes += BlueprintSyncLimits.encodedUtfBytes(id);
        }

        private boolean empty() {
            return learned.isEmpty() && discovered.isEmpty();
        }
    }

    static final class ClientAccumulator {
        private boolean initialized;
        private boolean completed;
        private long syncId;
        private int expectedChunks;
        private int researchPoints;
        private final Map<Integer, SyncPlayerProgressionPacket> chunks = new TreeMap<>();

        synchronized Optional<ProgressionSnapshot> accept(SyncPlayerProgressionPacket packet) {
            if (initialized && Long.compare(packet.syncId, syncId) < 0) {
                return Optional.empty();
            }
            if (!initialized || syncId != packet.syncId) {
                initialized = true;
                completed = false;
                syncId = packet.syncId;
                expectedChunks = packet.chunkCount;
                researchPoints = packet.researchPoints;
                chunks.clear();
            }
            if (completed) {
                return Optional.empty();
            }
            if (expectedChunks != packet.chunkCount || researchPoints != packet.researchPoints) {
                throw new IllegalArgumentException("Inconsistent player progression synchronization chunks");
            }

            SyncPlayerProgressionPacket existing = chunks.putIfAbsent(packet.chunkIndex, packet);
            if (existing != null
                    && (!existing.learnedBlueprints.equals(packet.learnedBlueprints)
                    || !existing.discoveredBlueprints.equals(packet.discoveredBlueprints))) {
                chunks.clear();
                throw new IllegalArgumentException(
                        "Conflicting duplicate player progression synchronization chunk");
            }
            long learnedEntryCount = chunks.values().stream()
                    .mapToLong(chunk -> chunk.learnedBlueprints.size())
                    .sum();
            long discoveredEntryCount = chunks.values().stream()
                    .mapToLong(chunk -> chunk.discoveredBlueprints.size())
                    .sum();
            if (learnedEntryCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    || discoveredEntryCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                chunks.clear();
                throw new IllegalArgumentException("Player progression synchronization exceeds the entry limit");
            }
            if (chunks.size() != expectedChunks) {
                return Optional.empty();
            }

            Set<String> learned = new TreeSet<>();
            Set<String> discovered = new TreeSet<>();
            chunks.values().forEach(part -> {
                learned.addAll(part.learnedBlueprints);
                discovered.addAll(part.discoveredBlueprints);
            });
            chunks.clear();
            if (!discovered.containsAll(learned)) {
                throw new IllegalArgumentException("Completed player progression violates discovery invariants");
            }
            completed = true;
            return Optional.of(new ProgressionSnapshot(
                    Collections.unmodifiableSet(learned),
                    Collections.unmodifiableSet(discovered),
                    researchPoints));
        }

        synchronized void clear() {
            initialized = false;
            completed = false;
            syncId = 0L;
            expectedChunks = 0;
            researchPoints = 0;
            chunks.clear();
        }
    }
}
