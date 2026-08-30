package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.client.ClientBlueprintCatalog;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

public class SyncBlueprintDataPacket {
    private static final int MAX_BLUEPRINTS = BlueprintDataManager.MAX_CATALOG_ENTRIES;
    private static final ClientAccumulator CLIENT_ACCUMULATOR = new ClientAccumulator();

    private final long syncId;
    private final int chunkIndex;
    private final int chunkCount;
    private final Map<ResourceLocation, BlueprintData> blueprintDataMap;

    public SyncBlueprintDataPacket(Map<ResourceLocation, BlueprintData> blueprintDataMap) {
        this(0L, 0, 1, blueprintDataMap);
    }

    private SyncBlueprintDataPacket(
            long syncId,
            int chunkIndex,
            int chunkCount,
            Map<ResourceLocation, BlueprintData> blueprintDataMap) {
        validateChunkMetadata(chunkIndex, chunkCount);
        BlueprintSyncLimits.validateCatalog(blueprintDataMap);
        this.syncId = syncId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.blueprintDataMap = sortedSnapshot(blueprintDataMap);
        if (estimatedPayloadBytes() > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Blueprint synchronization chunk exceeds the byte budget");
        }
    }

    public SyncBlueprintDataPacket(FriendlyByteBuf buf) {
        int start = buf.readerIndex();
        syncId = buf.readLong();
        chunkIndex = buf.readVarInt();
        chunkCount = buf.readVarInt();
        validateChunkMetadata(chunkIndex, chunkCount);

        int size = buf.readVarInt();
        if (size < 0 || size > MAX_BLUEPRINTS) {
            throw new IllegalArgumentException("Invalid blueprint count: " + size);
        }

        Map<ResourceLocation, BlueprintData> decodedBlueprints = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            ResourceLocation bpId = buf.readResourceLocation();
            String nameKey = buf.readUtf(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
            String tooltipKey = buf.readUtf(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
            ResourceLocation recipeId = buf.readResourceLocation();
            String itemType = buf.readUtf(BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH);
            ResourceLocation displaySlotKey = buf.readResourceLocation();
            BlueprintKind kind = buf.readEnum(BlueprintKind.class);

            BlueprintData data = new BlueprintData(
                    bpId.toString(), nameKey, tooltipKey, recipeId, null, itemType, displaySlotKey, kind);
            BlueprintSyncLimits.validateEntry(bpId, data);
            if (decodedBlueprints.put(bpId, data) != null) {
                throw new IllegalArgumentException("Duplicate blueprint ID in synchronized catalog: " + bpId);
            }
        }
        if (buf.readerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Blueprint synchronization chunk exceeds the byte budget");
        }
        blueprintDataMap = Collections.unmodifiableMap(decodedBlueprints);
    }

    static List<SyncBlueprintDataPacket> split(
            Map<ResourceLocation, BlueprintData> blueprintDataMap,
            long syncId) {
        BlueprintSyncLimits.validateCatalog(blueprintDataMap);
        List<Map.Entry<ResourceLocation, BlueprintData>> entries = new ArrayList<>(blueprintDataMap.entrySet());
        entries.sort(Map.Entry.comparingByKey());

        List<Map<ResourceLocation, BlueprintData>> chunks = new ArrayList<>();
        Map<ResourceLocation, BlueprintData> current = new LinkedHashMap<>();
        int currentBytes = BlueprintSyncLimits.CHUNK_HEADER_RESERVE;
        for (Map.Entry<ResourceLocation, BlueprintData> entry : entries) {
            int entryBytes = BlueprintSyncLimits.encodedBlueprintEntryBytes(entry.getKey(), entry.getValue());
            if (!current.isEmpty() && currentBytes + entryBytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new LinkedHashMap<>();
                currentBytes = BlueprintSyncLimits.CHUNK_HEADER_RESERVE;
            }
            current.put(entry.getKey(), entry.getValue());
            currentBytes += entryBytes;
        }
        if (!current.isEmpty() || chunks.isEmpty()) {
            chunks.add(current);
        }

        List<SyncBlueprintDataPacket> packets = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            packets.add(new SyncBlueprintDataPacket(syncId, i, chunks.size(), chunks.get(i)));
        }
        return List.copyOf(packets);
    }

    public void toBytes(FriendlyByteBuf buf) {
        int start = buf.writerIndex();
        buf.writeLong(syncId);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(blueprintDataMap.size());
        for (Map.Entry<ResourceLocation, BlueprintData> entry : blueprintDataMap.entrySet()) {
            BlueprintData data = entry.getValue();
            buf.writeResourceLocation(entry.getKey());
            buf.writeUtf(data.getNameKey(), BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
            buf.writeUtf(data.getTooltipKey(), BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
            buf.writeResourceLocation(data.getRecipeId());
            buf.writeUtf(data.getItemType(), BlueprintSyncLimits.MAX_ITEM_TYPE_LENGTH);
            buf.writeResourceLocation(data.getDisplaySlotKey());
            buf.writeEnum(data.getKind());
        }
        if (buf.writerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Blueprint synchronization chunk exceeds the byte budget");
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> CLIENT_ACCUMULATOR.accept(this).ifPresent(completed -> {
            BlueprintDataManager.CLIENT.setBlueprintDataMap(completed);
            ClientBlueprintCatalog.invalidateCreativeTabs();
        }));
        ctx.get().setPacketHandled(true);
    }

    int estimatedPayloadBytes() {
        int size = 8
                + BlueprintSyncLimits.varIntBytes(chunkIndex)
                + BlueprintSyncLimits.varIntBytes(chunkCount)
                + BlueprintSyncLimits.varIntBytes(blueprintDataMap.size());
        for (Map.Entry<ResourceLocation, BlueprintData> entry : blueprintDataMap.entrySet()) {
            size += BlueprintSyncLimits.encodedBlueprintEntryBytes(entry.getKey(), entry.getValue());
        }
        return size;
    }

    int chunkIndex() {
        return chunkIndex;
    }

    int chunkCount() {
        return chunkCount;
    }

    Map<ResourceLocation, BlueprintData> entries() {
        return blueprintDataMap;
    }

    private static Map<ResourceLocation, BlueprintData> sortedSnapshot(
            Map<ResourceLocation, BlueprintData> values) {
        List<Map.Entry<ResourceLocation, BlueprintData>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey());
        Map<ResourceLocation, BlueprintData> snapshot = new LinkedHashMap<>();
        entries.forEach(entry -> snapshot.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(snapshot);
    }

    private static void validateChunkMetadata(int chunkIndex, int chunkCount) {
        if (chunkCount < 1
                || chunkCount > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT
                || chunkIndex < 0
                || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "Invalid blueprint synchronization chunk " + chunkIndex + " of " + chunkCount);
        }
    }

    private static final class ClientAccumulator {
        private long syncId = Long.MIN_VALUE;
        private int expectedChunks;
        private final Map<Integer, Map<ResourceLocation, BlueprintData>> chunks = new TreeMap<>();

        private synchronized Optional<Map<ResourceLocation, BlueprintData>> accept(SyncBlueprintDataPacket packet) {
            if (syncId != packet.syncId) {
                syncId = packet.syncId;
                expectedChunks = packet.chunkCount;
                chunks.clear();
            }
            if (expectedChunks != packet.chunkCount) {
                throw new IllegalArgumentException("Inconsistent blueprint synchronization chunk count");
            }
            chunks.put(packet.chunkIndex, packet.blueprintDataMap);
            if (chunks.size() != expectedChunks) {
                return Optional.empty();
            }

            Map<ResourceLocation, BlueprintData> completed = new LinkedHashMap<>();
            chunks.values().forEach(chunk -> chunk.forEach((id, data) -> {
                if (completed.put(id, data) != null) {
                    throw new IllegalArgumentException("Duplicate blueprint ID across synchronization chunks: " + id);
                }
            }));
            if (completed.size() > MAX_BLUEPRINTS) {
                throw new IllegalArgumentException("Completed blueprint synchronization exceeds the entry limit");
            }
            chunks.clear();
            return Optional.of(Collections.unmodifiableMap(completed));
        }
    }
}
