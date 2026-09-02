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
    private final List<BlueprintJournalSnapshot.RecentUnlockBatch> recentUnlocks;

    private SyncBlueprintJournalPacket(
            long syncId,
            boolean reuseExistingTree,
            int chunkIndex,
            int chunkCount,
            BlueprintJournalSnapshot snapshot,
            List<BlueprintJournalEntry> entries,
            List<BlueprintJournalSnapshot.HistoryEntry> history,
            List<BlueprintJournalSnapshot.RecentUnlockBatch> recentUnlocks) {
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
        this.recentUnlocks = List.copyOf(recentUnlocks);
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
        int recentCount = readBoundedCount(
                buf,
                "Journal recent unlock",
                PlayerProgressionLimits.MAX_RECENT_UNLOCK_BATCHES);
        List<BlueprintJournalSnapshot.RecentUnlockBatch> decodedRecent =
                new ArrayList<>(recentCount);
        for (int index = 0; index < recentCount; index++) {
            long sequence = buf.readLong();
            int sourceOrdinal = buf.readUnsignedByte();
            var sources = com.gamergaming.taczweaponblueprints.capabilities
                    .RecentBlueprintUnlockBatch.Source.values();
            if (sourceOrdinal >= sources.length) {
                throw new IllegalArgumentException("Invalid recent unlock source");
            }
            ResourceLocation target = readId(buf);
            int totalMembers = buf.readVarInt();
            int memberCount = readBoundedCount(
                    buf,
                    "Journal recent unlock member",
                    PlayerProgressionLimits.MAX_RECENT_UNLOCK_MEMBERS_PER_BATCH);
            List<ResourceLocation> members = new ArrayList<>(memberCount);
            for (int memberIndex = 0; memberIndex < memberCount; memberIndex++) {
                members.add(readId(buf));
            }
            decodedRecent.add(new BlueprintJournalSnapshot.RecentUnlockBatch(
                    sequence, sources[sourceOrdinal], target, members, totalMembers));
        }
        recentUnlocks = List.copyOf(decodedRecent);
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
        buf.writeVarInt(recentUnlocks.size());
        recentUnlocks.forEach(batch -> {
            buf.writeLong(batch.sequence());
            buf.writeByte(batch.source().ordinal());
            buf.writeResourceLocation(batch.targetBlueprintId());
            buf.writeVarInt(batch.totalMemberCount());
            buf.writeVarInt(batch.memberBlueprintIds().size());
            batch.memberBlueprintIds().forEach(buf::writeResourceLocation);
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
        for (BlueprintJournalSnapshot.RecentUnlockBatch batch : snapshot.recentUnlocks()) {
            int bytes = estimatedRecentUnlockBytes(batch);
            if (!current.empty()
                    && current.estimatedBytes + bytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new Chunk();
            }
            current.recentUnlocks.add(batch);
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
                    syncId, reuseExistingTree, index, chunks.size(), snapshot,
                    chunk.entries, chunk.history, chunk.recentUnlocks));
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
        for (BlueprintJournalSnapshot.RecentUnlockBatch batch : recentUnlocks) {
            bytes += estimatedRecentUnlockBytes(batch);
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
                || history.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || recentUnlocks.size() > PlayerProgressionLimits.MAX_RECENT_UNLOCK_BATCHES
                || recentUnlocks.stream().mapToInt(batch -> batch.memberBlueprintIds().size()).sum()
                        > PlayerProgressionLimits.MAX_RECENT_UNLOCK_MEMBER_IDS) {
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

    private static int estimatedRecentUnlockBytes(
            BlueprintJournalSnapshot.RecentUnlockBatch batch) {
        int bytes = 24 + BlueprintSyncLimits.encodedUtfBytes(
                batch.targetBlueprintId().toString());
        for (ResourceLocation member : batch.memberBlueprintIds()) {
            bytes += BlueprintSyncLimits.encodedUtfBytes(member.toString());
        }
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
        return readBoundedCount(
                buf, description, PlayerProgressionLimits.MAX_IDS_PER_COLLECTION);
    }

    private static int readBoundedCount(
            FriendlyByteBuf buf,
            String description,
            int maximum) {
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
                    "Invalid Journal synchronization chunk " + chunkIndex + " of " + chunkCount);
        }
    }

    private static final class Chunk {
        private final List<BlueprintJournalEntry> entries = new ArrayList<>();
        private final List<BlueprintJournalSnapshot.HistoryEntry> history = new ArrayList<>();
        private final List<BlueprintJournalSnapshot.RecentUnlockBatch> recentUnlocks =
                new ArrayList<>();
        private int estimatedBytes = JOURNAL_HEADER_RESERVE;

        private boolean empty() {
            return entries.isEmpty() && history.isEmpty() && recentUnlocks.isEmpty();
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
                    && (!existing.entries.equals(packet.entries)
                            || !existing.history.equals(packet.history)
                            || !existing.recentUnlocks.equals(packet.recentUnlocks))) {
                chunks.clear();
                throw new IllegalArgumentException("Conflicting duplicate Journal synchronization chunk");
            }
            if (chunks.size() != expectedChunks) {
                return Optional.empty();
            }
            long totalEntries = chunks.values().stream().mapToLong(chunk -> chunk.entries.size()).sum();
            long totalHistory = chunks.values().stream().mapToLong(chunk -> chunk.history.size()).sum();
            long totalRecent = chunks.values().stream()
                    .mapToLong(chunk -> chunk.recentUnlocks.size()).sum();
            long totalRecentMembers = chunks.values().stream()
                    .flatMap(chunk -> chunk.recentUnlocks.stream())
                    .mapToLong(batch -> batch.memberBlueprintIds().size()).sum();
            if (totalEntries > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    || totalHistory > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    || totalRecent > PlayerProgressionLimits.MAX_RECENT_UNLOCK_BATCHES
                    || totalRecentMembers > PlayerProgressionLimits.MAX_RECENT_UNLOCK_MEMBER_IDS) {
                chunks.clear();
                throw new IllegalArgumentException("Completed Journal synchronization exceeds the entry limit");
            }
            List<BlueprintJournalEntry> entries = new ArrayList<>();
            List<BlueprintJournalSnapshot.HistoryEntry> history = new ArrayList<>();
            List<BlueprintJournalSnapshot.RecentUnlockBatch> recentUnlocks = new ArrayList<>();
            chunks.values().forEach(chunk -> {
                entries.addAll(chunk.entries);
                history.addAll(chunk.history);
                recentUnlocks.addAll(chunk.recentUnlocks);
            });
            chunks.clear();
            completed = true;
            return Optional.of(new BlueprintJournalSnapshot(
                    entries, history, recentUnlocks, researchPoints, pointCap,
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
