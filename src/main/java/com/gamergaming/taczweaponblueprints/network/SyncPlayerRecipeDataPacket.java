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
import com.gamergaming.taczweaponblueprints.client.ClientBlueprintCatalog;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public class SyncPlayerRecipeDataPacket {
    private static final int MAX_RECIPES = 4096;
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
            String rawId = buf.readUtf(PlayerRecipeData.MAX_RESOURCE_ID_LENGTH);
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
            buf.writeUtf(recipeId, PlayerRecipeData.MAX_RESOURCE_ID_LENGTH);
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
        if (chunkCount < 1 || chunkCount > MAX_RECIPES || chunkIndex < 0 || chunkIndex >= chunkCount) {
            throw new IllegalArgumentException(
                    "Invalid learned-recipe synchronization chunk " + chunkIndex + " of " + chunkCount);
        }
    }

    private static final class ClientAccumulator {
        private long syncId = Long.MIN_VALUE;
        private int expectedChunks;
        private final Map<Integer, Set<String>> chunks = new TreeMap<>();

        private synchronized Optional<Set<String>> accept(SyncPlayerRecipeDataPacket packet) {
            if (syncId != packet.syncId) {
                syncId = packet.syncId;
                expectedChunks = packet.chunkCount;
                chunks.clear();
            }
            if (expectedChunks != packet.chunkCount) {
                throw new IllegalArgumentException("Inconsistent learned-recipe synchronization chunk count");
            }
            chunks.put(packet.chunkIndex, packet.learnedRecipes);
            if (chunks.size() != expectedChunks) {
                return Optional.empty();
            }

            Set<String> completed = new TreeSet<>();
            chunks.values().forEach(completed::addAll);
            if (completed.size() > MAX_RECIPES) {
                throw new IllegalArgumentException("Completed learned-recipe synchronization exceeds the entry limit");
            }
            chunks.clear();
            return Optional.of(Collections.unmodifiableSet(completed));
        }
    }
}
