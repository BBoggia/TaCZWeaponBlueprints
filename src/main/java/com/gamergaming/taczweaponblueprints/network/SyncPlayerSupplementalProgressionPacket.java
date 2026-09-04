package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.client.ClientBlueprintCatalog;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.progression.PlayerSupplementalProgressionView;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Atomically synchronizes disclosure-filtered fragment and criterion progress. */
public final class SyncPlayerSupplementalProgressionPacket {
    private static final ClientAccumulator CLIENT_ACCUMULATOR = new ClientAccumulator();

    private final long syncId;
    private final int chunkIndex;
    private final int chunkCount;
    private final Map<String, Integer> archivedFragments;
    private final Map<String, Integer> publicCriteria;

    private SyncPlayerSupplementalProgressionPacket(
            long syncId,
            int chunkIndex,
            int chunkCount,
            Map<String, Integer> archivedFragments,
            Map<String, Integer> publicCriteria) {
        validateChunkMetadata(chunkIndex, chunkCount);
        this.syncId = syncId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.archivedFragments = normalizedProgressMap(
                archivedFragments,
                PlayerProgressionLimits.MAX_FRAGMENT_TARGETS,
                "archived fragment");
        this.publicCriteria = normalizedProgressMap(
                publicCriteria,
                PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA,
                "public criterion");
        if (estimatedPayloadBytes() > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException(
                    "Supplemental progression synchronization chunk exceeds the byte budget");
        }
    }

    public SyncPlayerSupplementalProgressionPacket(FriendlyByteBuf buffer) {
        int start = buffer.readerIndex();
        syncId = buffer.readLong();
        chunkIndex = buffer.readVarInt();
        chunkCount = buffer.readVarInt();
        validateChunkMetadata(chunkIndex, chunkCount);
        archivedFragments = readProgressMap(
                buffer,
                PlayerProgressionLimits.MAX_FRAGMENT_TARGETS,
                "archived fragment");
        publicCriteria = readProgressMap(
                buffer,
                PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA,
                "public criterion");
        if (buffer.readerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException(
                    "Supplemental progression synchronization chunk exceeds the byte budget");
        }
    }

    static List<SyncPlayerSupplementalProgressionPacket> split(
            PlayerSupplementalProgressionView view,
            long syncId) {
        if (view == null) {
            throw new IllegalArgumentException("supplemental progression view cannot be null");
        }
        Map<String, Integer> fragments = normalizedProgressMap(
                view.archivedFragments(),
                PlayerProgressionLimits.MAX_FRAGMENT_TARGETS,
                "archived fragment");
        Map<String, Integer> criteria = normalizedProgressMap(
                view.publicCriteria(),
                PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA,
                "public criterion");

        List<ProgressionChunk> chunks = new ArrayList<>();
        ProgressionChunk current = new ProgressionChunk();
        for (Map.Entry<String, Integer> entry : fragments.entrySet()) {
            if (!current.canAdd(entry)) {
                chunks.add(current);
                current = new ProgressionChunk();
            }
            current.fragments.put(entry.getKey(), entry.getValue());
            current.addBytes(entry);
        }
        for (Map.Entry<String, Integer> entry : criteria.entrySet()) {
            if (!current.canAdd(entry)) {
                chunks.add(current);
                current = new ProgressionChunk();
            }
            current.criteria.put(entry.getKey(), entry.getValue());
            current.addBytes(entry);
        }
        if (!current.empty() || chunks.isEmpty()) {
            chunks.add(current);
        }
        if (chunks.size() > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT) {
            throw new IllegalArgumentException("Supplemental progression requires too many chunks");
        }

        List<SyncPlayerSupplementalProgressionPacket> packets = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            ProgressionChunk chunk = chunks.get(index);
            packets.add(new SyncPlayerSupplementalProgressionPacket(
                    syncId,
                    index,
                    chunks.size(),
                    chunk.fragments,
                    chunk.criteria));
        }
        return List.copyOf(packets);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        int start = buffer.writerIndex();
        buffer.writeLong(syncId);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        writeProgressMap(buffer, archivedFragments);
        writeProgressMap(buffer, publicCriteria);
        if (buffer.writerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException(
                    "Supplemental progression synchronization chunk exceeds the byte budget");
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        contextSupplier.get().enqueueWork(() -> CLIENT_ACCUMULATOR.accept(this).ifPresent(snapshot -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) {
                minecraft.player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(data -> {
                    if (!data.replaceSupplementalProgression(
                            snapshot.archivedFragments(),
                            snapshot.publicCriteria())) {
                        throw new IllegalArgumentException(
                                "Completed supplemental progression snapshot is invalid");
                    }
                    ClientBlueprintCatalog.refreshOpenGunSmithScreen();
                });
            }
        }));
        contextSupplier.get().setPacketHandled(true);
    }

    public static void clearClientState() {
        CLIENT_ACCUMULATOR.clear();
    }

    Map<String, Integer> archivedFragments() {
        return archivedFragments;
    }

    Map<String, Integer> publicCriteria() {
        return publicCriteria;
    }

    int estimatedPayloadBytes() {
        int size = 8
                + BlueprintSyncLimits.varIntBytes(chunkIndex)
                + BlueprintSyncLimits.varIntBytes(chunkCount)
                + BlueprintSyncLimits.varIntBytes(archivedFragments.size())
                + BlueprintSyncLimits.varIntBytes(publicCriteria.size());
        for (Map.Entry<String, Integer> entry : archivedFragments.entrySet()) {
            size += encodedEntryBytes(entry);
        }
        for (Map.Entry<String, Integer> entry : publicCriteria.entrySet()) {
            size += encodedEntryBytes(entry);
        }
        return size;
    }

    private static Map<String, Integer> readProgressMap(
            FriendlyByteBuf buffer,
            int maximumEntries,
            String description) {
        int size = buffer.readVarInt();
        if (size < 0 || size > maximumEntries) {
            throw new IllegalArgumentException("Invalid " + description + " count: " + size);
        }
        TreeMap<String, Integer> values = new TreeMap<>();
        for (int index = 0; index < size; index++) {
            String rawId = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
            String id = PlayerRecipeData.normalizeResourceId(rawId);
            int value = buffer.readVarInt();
            if (id == null || !id.equals(rawId) || value <= 0
                    || value > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || values.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException(
                        "Invalid or duplicate " + description + " synchronization entry");
            }
        }
        return Collections.unmodifiableMap(values);
    }

    private static void writeProgressMap(FriendlyByteBuf buffer, Map<String, Integer> values) {
        buffer.writeVarInt(values.size());
        values.forEach((id, value) -> {
            buffer.writeUtf(id, PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
            buffer.writeVarInt(value);
        });
    }

    private static Map<String, Integer> normalizedProgressMap(
            Map<String, Integer> values,
            int maximumEntries,
            String description) {
        if (values == null || values.size() > maximumEntries) {
            throw new IllegalArgumentException("Invalid " + description + " progression map");
        }
        TreeMap<String, Integer> normalized = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            String id = PlayerRecipeData.normalizeResourceId(entry.getKey());
            Integer value = entry.getValue();
            if (id == null || !id.equals(entry.getKey()) || value == null || value <= 0
                    || value > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || normalized.putIfAbsent(id, value) != null) {
                throw new IllegalArgumentException("Invalid " + description + " progression entry");
            }
            if (encodedEntryBytes(Map.entry(id, value))
                    + BlueprintSyncLimits.CHUNK_HEADER_RESERVE
                    > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                throw new IllegalArgumentException(description + " entry cannot fit in one chunk");
            }
        }
        return Collections.unmodifiableMap(normalized);
    }

    private static int encodedEntryBytes(Map.Entry<String, Integer> entry) {
        return BlueprintSyncLimits.encodedUtfBytes(entry.getKey())
                + BlueprintSyncLimits.varIntBytes(entry.getValue());
    }

    private static void validateChunkMetadata(int chunkIndex, int chunkCount) {
        if (chunkCount < 1
                || chunkCount > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT
                || chunkIndex < 0
                || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "Invalid supplemental progression synchronization chunk "
                            + chunkIndex + " of " + chunkCount);
        }
    }

    record Snapshot(
            Map<String, Integer> archivedFragments,
            Map<String, Integer> publicCriteria) {
    }

    private static final class ProgressionChunk {
        private final Map<String, Integer> fragments = new TreeMap<>();
        private final Map<String, Integer> criteria = new TreeMap<>();
        private int estimatedBytes = BlueprintSyncLimits.CHUNK_HEADER_RESERVE;

        private boolean canAdd(Map.Entry<String, Integer> entry) {
            return empty() || estimatedBytes + encodedEntryBytes(entry)
                    <= BlueprintSyncLimits.MAX_CHUNK_BYTES;
        }

        private void addBytes(Map.Entry<String, Integer> entry) {
            estimatedBytes += encodedEntryBytes(entry);
        }

        private boolean empty() {
            return fragments.isEmpty() && criteria.isEmpty();
        }
    }

    static final class ClientAccumulator {
        private boolean initialized;
        private boolean completed;
        private long syncId;
        private int expectedChunks;
        private final Map<Integer, SyncPlayerSupplementalProgressionPacket> chunks =
                new TreeMap<>();

        synchronized Optional<Snapshot> accept(SyncPlayerSupplementalProgressionPacket packet) {
            if (packet == null) {
                throw new IllegalArgumentException("supplemental progression packet cannot be null");
            }
            if (initialized && Long.compare(packet.syncId, syncId) < 0) {
                return Optional.empty();
            }
            if (!initialized || packet.syncId != syncId) {
                initialized = true;
                completed = false;
                syncId = packet.syncId;
                expectedChunks = packet.chunkCount;
                chunks.clear();
            }
            if (completed) {
                return Optional.empty();
            }
            if (expectedChunks != packet.chunkCount) {
                chunks.clear();
                throw new IllegalArgumentException(
                        "Inconsistent supplemental progression synchronization chunks");
            }
            SyncPlayerSupplementalProgressionPacket existing =
                    chunks.putIfAbsent(packet.chunkIndex, packet);
            if (existing != null
                    && (!existing.archivedFragments.equals(packet.archivedFragments)
                            || !existing.publicCriteria.equals(packet.publicCriteria))) {
                chunks.clear();
                throw new IllegalArgumentException(
                        "Conflicting duplicate supplemental progression chunk");
            }
            long fragmentCount = chunks.values().stream()
                    .mapToLong(chunk -> chunk.archivedFragments.size()).sum();
            long criterionCount = chunks.values().stream()
                    .mapToLong(chunk -> chunk.publicCriteria.size()).sum();
            if (fragmentCount > PlayerProgressionLimits.MAX_FRAGMENT_TARGETS
                    || criterionCount > PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA) {
                chunks.clear();
                throw new IllegalArgumentException(
                        "Supplemental progression synchronization exceeds the entry limit");
            }
            if (chunks.size() != expectedChunks) {
                return Optional.empty();
            }

            TreeMap<String, Integer> fragments = new TreeMap<>();
            TreeMap<String, Integer> criteria = new TreeMap<>();
            try {
                for (SyncPlayerSupplementalProgressionPacket chunk : chunks.values()) {
                    mergeUnique(fragments, chunk.archivedFragments, "fragment");
                    mergeUnique(criteria, chunk.publicCriteria, "criterion");
                }
            } catch (RuntimeException exception) {
                clear();
                throw exception;
            }
            chunks.clear();
            completed = true;
            return Optional.of(new Snapshot(
                    Collections.unmodifiableMap(fragments),
                    Collections.unmodifiableMap(criteria)));
        }

        private static void mergeUnique(
                Map<String, Integer> target,
                Map<String, Integer> source,
                String description) {
            for (Map.Entry<String, Integer> entry : source.entrySet()) {
                if (target.putIfAbsent(entry.getKey(), entry.getValue()) != null) {
                    throw new IllegalArgumentException(
                            "Duplicate " + description + " across progression chunks");
                }
            }
        }

        synchronized void clear() {
            initialized = false;
            completed = false;
            syncId = 0L;
            expectedChunks = 0;
            chunks.clear();
        }
    }
}
