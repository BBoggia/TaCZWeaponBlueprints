package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.ClientResearchState;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Chunked atomic synchronization for the disclosure-filtered Journal model. */
public final class SyncBlueprintJournalPacket {
    private static final int JOURNAL_HEADER_RESERVE = 64;
    private static final int JOURNAL_ENTRY_FIXED_RESERVE = 40;
    private static final ClientAccumulator CLIENT_ACCUMULATOR = new ClientAccumulator();

    private final long syncId;
    private final boolean reuseExistingTree;
    private final int chunkIndex;
    private final int chunkCount;
    private final int researchPoints;
    private final int pointCap;
    private final int learnedCount;
    private final int discoveredCount;
    private final int researchableCount;
    private final List<BlueprintJournalEntry> entries;
    private final List<BlueprintJournalSnapshot.HistoryEntry> history;

    private SyncBlueprintJournalPacket(
            long syncId,
            boolean reuseExistingTree,
            int chunkIndex,
            int chunkCount,
            BlueprintJournalSnapshot snapshot,
            List<BlueprintJournalEntry> entries,
            List<BlueprintJournalSnapshot.HistoryEntry> history) {
        validateChunkMetadata(chunkIndex, chunkCount);
        this.syncId = syncId;
        this.reuseExistingTree = reuseExistingTree;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        researchPoints = snapshot.researchPoints();
        pointCap = snapshot.pointCap();
        learnedCount = snapshot.learnedCount();
        discoveredCount = snapshot.discoveredCount();
        researchableCount = snapshot.researchableCount();
        this.entries = List.copyOf(entries);
        this.history = List.copyOf(history);
        validateCommonState();
        if (estimatedPayloadBytes() > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Journal synchronization chunk exceeds the byte budget");
        }
    }

    public SyncBlueprintJournalPacket(FriendlyByteBuf buf) {
        int start = buf.readerIndex();
        syncId = buf.readLong();
        reuseExistingTree = buf.readBoolean();
        chunkIndex = buf.readVarInt();
        chunkCount = buf.readVarInt();
        validateChunkMetadata(chunkIndex, chunkCount);
        researchPoints = buf.readVarInt();
        pointCap = buf.readVarInt();
        learnedCount = buf.readVarInt();
        discoveredCount = buf.readVarInt();
        researchableCount = buf.readVarInt();
        int entryCount = readBoundedCount(buf, "Journal entry");
        List<BlueprintJournalEntry> decodedEntries = new ArrayList<>(entryCount);
        for (int index = 0; index < entryCount; index++) {
            decodedEntries.add(readEntry(buf));
        }
        int historyCount = readBoundedCount(buf, "Journal history");
        List<BlueprintJournalSnapshot.HistoryEntry> decodedHistory = new ArrayList<>(historyCount);
        for (int index = 0; index < historyCount; index++) {
            decodedHistory.add(new BlueprintJournalSnapshot.HistoryEntry(
                    readId(buf), buf.readBoolean()));
        }
        entries = List.copyOf(decodedEntries);
        history = List.copyOf(decodedHistory);
        validateCommonState();
        if (buf.readerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Journal synchronization chunk exceeds the byte budget");
        }
    }

    public void toBytes(FriendlyByteBuf buf) {
        int start = buf.writerIndex();
        buf.writeLong(syncId);
        buf.writeBoolean(reuseExistingTree);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(researchPoints);
        buf.writeVarInt(pointCap);
        buf.writeVarInt(learnedCount);
        buf.writeVarInt(discoveredCount);
        buf.writeVarInt(researchableCount);
        buf.writeVarInt(entries.size());
        entries.forEach(entry -> writeEntry(buf, entry));
        buf.writeVarInt(history.size());
        history.forEach(entry -> {
            buf.writeResourceLocation(entry.blueprintId());
            buf.writeBoolean(entry.learned());
        });
        if (buf.writerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Journal synchronization chunk exceeds the byte budget");
        }
    }

    public void handle(Supplier<NetworkEvent.Context> context) {
        context.get().enqueueWork(() -> CLIENT_ACCUMULATOR.accept(this).ifPresent(snapshot ->
                ClientResearchState.acceptJournal(syncId, snapshot, reuseExistingTree)));
        context.get().setPacketHandled(true);
    }

    public static void clearClientState() {
        CLIENT_ACCUMULATOR.clear();
    }

    static List<SyncBlueprintJournalPacket> split(BlueprintJournalSnapshot snapshot, long syncId) {
        return split(snapshot, syncId, false);
    }

    static List<SyncBlueprintJournalPacket> split(
            BlueprintJournalSnapshot snapshot,
            long syncId,
            boolean reuseExistingTree) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Journal snapshot cannot be null");
        }
        List<Chunk> chunks = new ArrayList<>();
        Chunk current = new Chunk();
        for (BlueprintJournalEntry entry : snapshot.entries()) {
            int bytes = estimatedEntryBytes(entry);
            if (!current.empty() && current.estimatedBytes + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new Chunk();
            }
            current.entries.add(entry);
            current.estimatedBytes += bytes;
        }
        for (BlueprintJournalSnapshot.HistoryEntry entry : snapshot.unavailableHistory()) {
            int bytes = BlueprintSyncLimits.encodedUtfBytes(entry.blueprintId().toString()) + 1;
            if (!current.empty() && current.estimatedBytes + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new Chunk();
            }
            current.history.add(entry);
            current.estimatedBytes += bytes;
        }
        if (!current.empty() || chunks.isEmpty()) {
            chunks.add(current);
        }
        if (chunks.size() > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT) {
            throw new IllegalArgumentException("Journal snapshot requires too many synchronization chunks");
        }
        List<SyncBlueprintJournalPacket> packets = new ArrayList<>(chunks.size());
        for (int index = 0; index < chunks.size(); index++) {
            Chunk chunk = chunks.get(index);
            packets.add(new SyncBlueprintJournalPacket(
                    syncId, reuseExistingTree, index, chunks.size(), snapshot, chunk.entries, chunk.history));
        }
        return List.copyOf(packets);
    }

    int estimatedPayloadBytes() {
        int bytes = JOURNAL_HEADER_RESERVE;
        for (BlueprintJournalEntry entry : entries) {
            bytes += estimatedEntryBytes(entry);
        }
        for (BlueprintJournalSnapshot.HistoryEntry entry : history) {
            bytes += BlueprintSyncLimits.encodedUtfBytes(entry.blueprintId().toString()) + 1;
        }
        return bytes;
    }

    private void validateCommonState() {
        if (researchPoints < 0 || researchPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointCap < 0 || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || learnedCount < 0 || discoveredCount < learnedCount || researchableCount < 0
                || learnedCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || discoveredCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || researchableCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || entries.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || history.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("Journal synchronization metadata is invalid");
        }
    }

    private static BlueprintJournalEntry readEntry(FriendlyByteBuf buf) {
        int ordinal = buf.readVarInt();
        int visibilityOrdinal = buf.readUnsignedByte();
        JournalVisibility[] values = JournalVisibility.values();
        if (visibilityOrdinal <= JournalVisibility.HIDDEN.ordinal() || visibilityOrdinal >= values.length) {
            throw new IllegalArgumentException("Invalid synchronized Journal visibility");
        }
        JournalVisibility visibility = values[visibilityOrdinal];
        Optional<ResourceLocation> blueprintId = readOptionalId(buf);
        Optional<String> nameKey = readOptionalString(buf, BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
        Optional<String> itemType = readOptionalString(buf, BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH);
        Optional<ResourceLocation> displaySlot = readOptionalId(buf);
        int flags = buf.readUnsignedByte();
        if ((flags & ~31) != 0) {
            throw new IllegalArgumentException("Invalid synchronized Journal flags");
        }
        return new BlueprintJournalEntry(
                ordinal,
                visibility,
                blueprintId,
                nameKey,
                itemType,
                displaySlot,
                (flags & 1) != 0,
                (flags & 2) != 0,
                (flags & 4) != 0,
                (flags & 8) != 0,
                (flags & 16) != 0,
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt(),
                buf.readVarInt());
    }

    private static void writeEntry(FriendlyByteBuf buf, BlueprintJournalEntry entry) {
        buf.writeVarInt(entry.ordinal());
        buf.writeByte(entry.visibility().ordinal());
        writeOptionalId(buf, entry.blueprintId());
        writeOptionalString(buf, entry.nameKey(), BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
        writeOptionalString(buf, entry.itemType(), BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH);
        writeOptionalId(buf, entry.displaySlotId());
        int flags = (entry.learned() ? 1 : 0)
                | (entry.discovered() ? 2 : 0)
                | (entry.researchable() ? 4 : 0)
                | (entry.recyclable() ? 8 : 0)
                | (entry.canAffordPoints() ? 16 : 0);
        buf.writeByte(flags);
        buf.writeVarInt(entry.researchPointCost());
        buf.writeVarInt(entry.ingredientTypeCount());
        buf.writeVarInt(entry.prerequisiteCount());
        buf.writeVarInt(entry.recyclingValue());
    }

    private static int estimatedEntryBytes(BlueprintJournalEntry entry) {
        int bytes = JOURNAL_ENTRY_FIXED_RESERVE;
        bytes += entry.blueprintId().map(id -> BlueprintSyncLimits.encodedUtfBytes(id.toString())).orElse(0);
        bytes += entry.nameKey().map(BlueprintSyncLimits::encodedUtfBytes).orElse(0);
        bytes += entry.itemType().map(BlueprintSyncLimits::encodedUtfBytes).orElse(0);
        bytes += entry.displaySlotId().map(id -> BlueprintSyncLimits.encodedUtfBytes(id.toString())).orElse(0);
        return bytes;
    }

    private static Optional<ResourceLocation> readOptionalId(FriendlyByteBuf buf) {
        return buf.readBoolean() ? Optional.of(readId(buf)) : Optional.empty();
    }

    private static ResourceLocation readId(FriendlyByteBuf buf) {
        ResourceLocation id = ResourceLocation.tryParse(
                buf.readUtf(BlueprintSyncLimits.MAX_RESOURCE_ID_LENGTH));
        if (id == null) {
            throw new IllegalArgumentException("Invalid synchronized Journal ID");
        }
        return id;
    }

    private static void writeOptionalId(FriendlyByteBuf buf, Optional<ResourceLocation> value) {
        buf.writeBoolean(value.isPresent());
        value.ifPresent(buf::writeResourceLocation);
    }

    private static Optional<String> readOptionalString(FriendlyByteBuf buf, int maximumLength) {
        return buf.readBoolean() ? Optional.of(buf.readUtf(maximumLength)) : Optional.empty();
    }

    private static void writeOptionalString(FriendlyByteBuf buf, Optional<String> value, int maximumLength) {
        buf.writeBoolean(value.isPresent());
        value.ifPresent(text -> buf.writeUtf(text, maximumLength));
    }

    private static int readBoundedCount(FriendlyByteBuf buf, String description) {
        int count = buf.readVarInt();
        if (count < 0 || count > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("Invalid " + description + " count: " + count);
        }
        return count;
    }

    private static void validateChunkMetadata(int chunkIndex, int chunkCount) {
        if (chunkCount < 1 || chunkCount > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT
                || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "Invalid Journal synchronization chunk " + chunkIndex + " of " + chunkCount);
        }
    }

    private static final class Chunk {
        private final List<BlueprintJournalEntry> entries = new ArrayList<>();
        private final List<BlueprintJournalSnapshot.HistoryEntry> history = new ArrayList<>();
        private int estimatedBytes = JOURNAL_HEADER_RESERVE;

        private boolean empty() {
            return entries.isEmpty() && history.isEmpty();
        }
    }

    static final class ClientAccumulator {
        private boolean initialized;
        private boolean completed;
        private long syncId;
        private int expectedChunks;
        private boolean reuseExistingTree;
        private int researchPoints;
        private int pointCap;
        private int learnedCount;
        private int discoveredCount;
        private int researchableCount;
        private final Map<Integer, SyncBlueprintJournalPacket> chunks = new TreeMap<>();

        synchronized Optional<BlueprintJournalSnapshot> accept(SyncBlueprintJournalPacket packet) {
            if (initialized && Long.compare(packet.syncId, syncId) < 0) {
                return Optional.empty();
            }
            if (!initialized || syncId != packet.syncId) {
                initialized = true;
                completed = false;
                syncId = packet.syncId;
                expectedChunks = packet.chunkCount;
                reuseExistingTree = packet.reuseExistingTree;
                researchPoints = packet.researchPoints;
                pointCap = packet.pointCap;
                learnedCount = packet.learnedCount;
                discoveredCount = packet.discoveredCount;
                researchableCount = packet.researchableCount;
                chunks.clear();
            }
            if (completed) {
                return Optional.empty();
            }
            if (expectedChunks != packet.chunkCount
                    || reuseExistingTree != packet.reuseExistingTree
                    || researchPoints != packet.researchPoints
                    || pointCap != packet.pointCap
                    || learnedCount != packet.learnedCount
                    || discoveredCount != packet.discoveredCount
                    || researchableCount != packet.researchableCount) {
                throw new IllegalArgumentException("Inconsistent Journal synchronization chunks");
            }
            SyncBlueprintJournalPacket existing = chunks.putIfAbsent(packet.chunkIndex, packet);
            if (existing != null
                    && (!existing.entries.equals(packet.entries) || !existing.history.equals(packet.history))) {
                chunks.clear();
                throw new IllegalArgumentException("Conflicting duplicate Journal synchronization chunk");
            }
            if (chunks.size() != expectedChunks) {
                return Optional.empty();
            }
            long totalEntries = chunks.values().stream().mapToLong(chunk -> chunk.entries.size()).sum();
            long totalHistory = chunks.values().stream().mapToLong(chunk -> chunk.history.size()).sum();
            if (totalEntries > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    || totalHistory > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                chunks.clear();
                throw new IllegalArgumentException("Completed Journal synchronization exceeds the entry limit");
            }
            List<BlueprintJournalEntry> entries = new ArrayList<>();
            List<BlueprintJournalSnapshot.HistoryEntry> history = new ArrayList<>();
            chunks.values().forEach(chunk -> {
                entries.addAll(chunk.entries);
                history.addAll(chunk.history);
            });
            chunks.clear();
            completed = true;
            return Optional.of(new BlueprintJournalSnapshot(
                    entries, history, researchPoints, pointCap,
                    learnedCount, discoveredCount, researchableCount));
        }

        synchronized void clear() {
            initialized = false;
            completed = false;
            syncId = 0L;
            expectedChunks = 0;
            reuseExistingTree = false;
            researchPoints = 0;
            pointCap = 0;
            learnedCount = 0;
            discoveredCount = 0;
            researchableCount = 0;
            chunks.clear();
        }
    }
}
