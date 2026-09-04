package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.ClientBlueprintCatalog;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class SyncPlayerRecipeDataPacket {
    private static final int MAX_RECIPES = PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
    private static final ClientAccumulator CLIENT_ACCUMULATOR = new ClientAccumulator();

    private final long syncId;
    private final int chunkIndex;
    private final int chunkCount;
    private final Set<String> learnedRecipes;

    public SyncPlayerRecipeDataPacket(Set<String> learnedRecipes) {
        this(0L, 0, 1, learnedRecipes);
    }

    private SyncPlayerRecipeDataPacket(long syncId, int chunkIndex, int chunkCount, Set<String> learnedRecipes) {
        validateChunkMetadata(chunkIndex, chunkCount);
        this.syncId = syncId;
        this.chunkIndex = chunkIndex;
        this.chunkCount = chunkCount;
        this.learnedRecipes = normalizedSnapshot(learnedRecipes);
        if (estimatedPayloadBytes() > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Learned-recipe synchronization chunk exceeds the byte budget");
        }
    }

    public SyncPlayerRecipeDataPacket(FriendlyByteBuf buf) {
        int start = buf.readerIndex();
        syncId = buf.readLong();
        chunkIndex = buf.readVarInt();
        chunkCount = buf.readVarInt();
        validateChunkMetadata(chunkIndex, chunkCount);
        int size = buf.readVarInt();
        if (size < 0 || size > MAX_RECIPES) {
            throw new IllegalArgumentException("Invalid learned recipe count: " + size);
        }

        TreeSet<String> decodedRecipes = new TreeSet<>();
        for (int i = 0; i < size; i++) {
            String rawId = buf.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
            String normalizedId = PlayerRecipeData.normalizeRecipeId(rawId);
            if (normalizedId == null) {
                throw new IllegalArgumentException("Invalid learned recipe ID in synchronization payload");
            }
            decodedRecipes.add(normalizedId);
        }
        if (buf.readerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Learned-recipe synchronization chunk exceeds the byte budget");
        }
        learnedRecipes = Collections.unmodifiableSet(decodedRecipes);
    }

    static List<SyncPlayerRecipeDataPacket> split(Set<String> learnedRecipes, long syncId) {
        Set<String> normalized = normalizedSnapshot(learnedRecipes);
        List<Set<String>> chunks = new ArrayList<>();
        Set<String> current = new TreeSet<>();
        int currentBytes = BlueprintSyncLimits.CHUNK_HEADER_RESERVE;
        for (String recipeId : normalized) {
            int entryBytes = BlueprintSyncLimits.encodedUtfBytes(recipeId);
            if (!current.isEmpty() && currentBytes + entryBytes > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
                chunks.add(current);
                current = new TreeSet<>();
                currentBytes = BlueprintSyncLimits.CHUNK_HEADER_RESERVE;
            }
            current.add(recipeId);
            currentBytes += entryBytes;
        }
        if (!current.isEmpty() || chunks.isEmpty()) {
            chunks.add(current);
        }

        List<SyncPlayerRecipeDataPacket> packets = new ArrayList<>(chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            packets.add(new SyncPlayerRecipeDataPacket(syncId, i, chunks.size(), chunks.get(i)));
        }
        return List.copyOf(packets);
    }

    public void toBytes(FriendlyByteBuf buf) {
        int start = buf.writerIndex();
        buf.writeLong(syncId);
        buf.writeVarInt(chunkIndex);
        buf.writeVarInt(chunkCount);
        buf.writeVarInt(learnedRecipes.size());
        for (String recipeId : learnedRecipes) {
            buf.writeUtf(recipeId, PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        }
        if (buf.writerIndex() - start > BlueprintSyncLimits.MAX_CHUNK_BYTES) {
            throw new IllegalArgumentException("Learned-recipe synchronization chunk exceeds the byte budget");
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> CLIENT_ACCUMULATOR.accept(this).ifPresent(completed -> {
            Minecraft mc = Minecraft.getInstance();
            if (mc.player != null) {
                mc.player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(cap -> {
                    cap.replaceRecipes(completed);
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
                + BlueprintSyncLimits.varIntBytes(learnedRecipes.size());
        for (String recipeId : learnedRecipes) {
            size += BlueprintSyncLimits.encodedUtfBytes(recipeId);
        }
        return size;
    }

    int chunkIndex() {
        return chunkIndex;
    }

    int chunkCount() {
        return chunkCount;
    }

    Set<String> entries() {
        return learnedRecipes;
    }

    private static Set<String> normalizedSnapshot(Set<String> recipes) {
        TreeSet<String> snapshot = new TreeSet<>();
        if (recipes != null) {
            for (String recipeId : recipes) {
                String normalizedId = PlayerRecipeData.normalizeRecipeId(recipeId);
                if (normalizedId != null) {
                    snapshot.add(normalizedId);
                }
            }
        }
        if (snapshot.size() > MAX_RECIPES) {
            throw new IllegalArgumentException("Too many learned recipes to synchronize: " + snapshot.size());
        }
        return Collections.unmodifiableSet(snapshot);
    }

    private static void validateChunkMetadata(int chunkIndex, int chunkCount) {
        if (chunkCount < 1
                || chunkCount > BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT
                || chunkIndex < 0
                || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "Invalid learned-recipe synchronization chunk " + chunkIndex + " of " + chunkCount);
        }
    }

    static final class ClientAccumulator {
        private boolean initialized;
        private boolean completed;
        private long syncId;
        private int expectedChunks;
        private final Map<Integer, SyncPlayerRecipeDataPacket> chunks = new TreeMap<>();

        synchronized Optional<Set<String>> accept(SyncPlayerRecipeDataPacket packet) {
            if (packet == null) {
                throw new IllegalArgumentException("learned-recipe packet cannot be null");
            }
            if (initialized && Long.compare(packet.syncId, syncId) < 0) {
                return Optional.empty();
            }
            if (!initialized || syncId != packet.syncId) {
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
                clear();
                throw new IllegalArgumentException("Inconsistent learned-recipe synchronization chunk count");
            }
            SyncPlayerRecipeDataPacket existing = chunks.putIfAbsent(packet.chunkIndex, packet);
            if (existing != null && !existing.learnedRecipes.equals(packet.learnedRecipes)) {
                clear();
                throw new IllegalArgumentException(
                        "Conflicting duplicate learned-recipe synchronization chunk");
            }
            long entryCount = chunks.values().stream()
                    .mapToLong(chunk -> chunk.learnedRecipes.size())
                    .sum();
            if (entryCount > MAX_RECIPES) {
                clear();
                throw new IllegalArgumentException(
                        "Learned-recipe synchronization exceeds the entry limit");
            }
            if (chunks.size() != expectedChunks) {
                return Optional.empty();
            }

            Set<String> completed = new TreeSet<>();
            for (SyncPlayerRecipeDataPacket chunk : chunks.values()) {
                for (String recipeId : chunk.learnedRecipes) {
                    if (!completed.add(recipeId)) {
                        clear();
                        throw new IllegalArgumentException(
                                "Duplicate learned recipe across synchronization chunks");
                    }
                }
            }
            chunks.clear();
            this.completed = true;
            return Optional.of(Collections.unmodifiableSet(completed));
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
