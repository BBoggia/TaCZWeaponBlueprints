package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class SyncPacketTest {

    @Test
    void learnedRecipePacketWritesAStableSortedSnapshot() {
        SyncPlayerRecipeDataPacket packet = new SyncPlayerRecipeDataPacket(
                Set.of("tacz:gun/m4a1", "tacz:gun/ak47"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);

            assertEquals(0L, buffer.readLong());
            assertEquals(0, buffer.readVarInt());
            assertEquals(1, buffer.readVarInt());
            assertEquals(2, buffer.readVarInt());
            assertEquals("tacz:gun/ak47", buffer.readUtf(256));
            assertEquals("tacz:gun/m4a1", buffer.readUtf(256));
        } finally {
            buffer.release();
        }
    }

    @Test
    void learnedRecipePacketRejectsOversizedCounts() {
        Set<String> recipes = IntStream.range(0, 4097)
                .mapToObj(index -> "test:recipe_" + index)
                .collect(Collectors.toSet());
        assertThrows(IllegalArgumentException.class, () -> new SyncPlayerRecipeDataPacket(recipes));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeLong(1L);
            buffer.writeVarInt(0);
            buffer.writeVarInt(1);
            buffer.writeVarInt(4097);
            assertThrows(IllegalArgumentException.class, () -> new SyncPlayerRecipeDataPacket(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void playerProgressionPacketRoundTripsAStableSnapshot() {
        var packets = SyncPlayerProgressionPacket.split(
                Set.of("test:bravo", "test:alpha"),
                Set.of("test:history", "test:bravo", "test:alpha"),
                125,
                9L);
        assertEquals(1, packets.size());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packets.get(0).toBytes(buffer);
            SyncPlayerProgressionPacket decoded = new SyncPlayerProgressionPacket(buffer);

            assertEquals(Set.of("test:alpha", "test:bravo"), decoded.learnedEntries());
            assertEquals(Set.of("test:alpha", "test:bravo", "test:history"), decoded.discoveredEntries());
            assertEquals(125, decoded.researchPoints());
        } finally {
            buffer.release();
        }
    }

    @Test
    void playerProgressionPacketRejectsInvalidState() {
        assertThrows(
                IllegalArgumentException.class,
                () -> SyncPlayerProgressionPacket.split(Set.of("test:learned"), Set.of(), 0, 1L));
        assertThrows(
                IllegalArgumentException.class,
                () -> SyncPlayerProgressionPacket.split(
                        Set.of(),
                        Set.of(),
                        PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1,
                        1L));

        FriendlyByteBuf excessiveChunks = new FriendlyByteBuf(Unpooled.buffer());
        try {
            excessiveChunks.writeLong(1L);
            excessiveChunks.writeVarInt(0);
            excessiveChunks.writeVarInt(BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT + 1);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SyncPlayerProgressionPacket(excessiveChunks));
        } finally {
            excessiveChunks.release();
        }
    }

    @Test
    void blueprintPacketUsesMapKeysAndStableOrdering() {
        ResourceLocation firstId = new ResourceLocation("test", "alpha");
        ResourceLocation secondId = new ResourceLocation("test", "bravo");
        Map<ResourceLocation, BlueprintData> blueprints = new LinkedHashMap<>();
        blueprints.put(secondId, blueprint(secondId));
        blueprints.put(firstId, blueprint(firstId));

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SyncBlueprintDataPacket(blueprints).toBytes(buffer);

            assertEquals(0L, buffer.readLong());
            assertEquals(0, buffer.readVarInt());
            assertEquals(1, buffer.readVarInt());
            assertEquals(2, buffer.readVarInt());
            assertEquals(firstId, buffer.readResourceLocation());
        } finally {
            buffer.release();
        }
    }

    @Test
    void blueprintPacketRoundTripsAndRejectsOutboundValuesTheClientCannotDecode() {
        ResourceLocation blueprintId = new ResourceLocation("test", "alpha");
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            SyncBlueprintDataPacket outbound = new SyncBlueprintDataPacket(
                    Map.of(blueprintId, blueprint(blueprintId)));
            outbound.toBytes(buffer);
            SyncBlueprintDataPacket decoded = new SyncBlueprintDataPacket(buffer);

            FriendlyByteBuf reencoded = new FriendlyByteBuf(Unpooled.buffer());
            try {
                decoded.toBytes(reencoded);
                assertEquals(0L, reencoded.readLong());
                assertEquals(0, reencoded.readVarInt());
                assertEquals(1, reencoded.readVarInt());
                assertEquals(1, reencoded.readVarInt());
                assertEquals(blueprintId, reencoded.readResourceLocation());
                assertEquals("item.test.name", reencoded.readUtf(256));
            } finally {
                reencoded.release();
            }
        } finally {
            buffer.release();
        }

        BlueprintData oversizedName = blueprint(
                blueprintId,
                "x".repeat(257),
                "item.test.tooltip",
                "rifle");
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncBlueprintDataPacket(Map.of(blueprintId, oversizedName)));

        BlueprintData oversizedType = blueprint(
                blueprintId,
                "item.test.name",
                "item.test.tooltip",
                "x".repeat(65));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SyncBlueprintDataPacket(Map.of(blueprintId, oversizedType)));
    }

    @Test
    void maximumCatalogAndRecipeSetsAreSplitBelowThePayloadBudget() {
        Map<ResourceLocation, BlueprintData> blueprints = new LinkedHashMap<>();
        Set<String> recipes = new java.util.LinkedHashSet<>();
        Set<String> progressionIds = new java.util.LinkedHashSet<>();
        for (int index = 0; index < BlueprintDataManager.MAX_CATALOG_ENTRIES; index++) {
            ResourceLocation blueprintId = new ResourceLocation("test", "blueprint_" + index);
            blueprints.put(
                    blueprintId,
                    blueprint(
                            blueprintId,
                            "n".repeat(256),
                            "t".repeat(256),
                            "x".repeat(64)));
            String suffix = Integer.toString(index);
            recipes.add("test:" + "r".repeat(251 - suffix.length()) + suffix);
            progressionIds.add("test:" + "p".repeat(251 - suffix.length()) + suffix);
        }

        var blueprintChunks = SyncBlueprintDataPacket.split(blueprints, 41L);
        var recipeChunks = SyncPlayerRecipeDataPacket.split(recipes, 42L);
        var progressionChunks = SyncPlayerProgressionPacket.split(
                progressionIds,
                progressionIds,
                PlayerProgressionLimits.MAX_RESEARCH_POINTS,
                43L);

        assertTrue(blueprintChunks.size() > 1);
        assertTrue(recipeChunks.size() > 1);
        assertTrue(progressionChunks.size() > 1);
        assertEquals(
                BlueprintDataManager.MAX_CATALOG_ENTRIES,
                blueprintChunks.stream().mapToInt(packet -> packet.entries().size()).sum());
        assertEquals(4096, recipeChunks.stream().mapToInt(packet -> packet.entries().size()).sum());
        assertEquals(
                PlayerProgressionLimits.MAX_IDS_PER_COLLECTION,
                progressionChunks.stream().mapToInt(packet -> packet.learnedEntries().size()).sum());
        assertEquals(
                PlayerProgressionLimits.MAX_IDS_PER_COLLECTION,
                progressionChunks.stream().mapToInt(packet -> packet.discoveredEntries().size()).sum());
        blueprintChunks.forEach(packet ->
                assertTrue(packet.estimatedPayloadBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES));
        recipeChunks.forEach(packet ->
                assertTrue(packet.estimatedPayloadBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES));
        progressionChunks.forEach(packet ->
                assertTrue(packet.estimatedPayloadBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES));
        blueprintChunks.forEach(packet -> assertEncodedWithinBudget(packet::toBytes));
        recipeChunks.forEach(packet -> assertEncodedWithinBudget(packet::toBytes));
        progressionChunks.forEach(packet -> assertEncodedWithinBudget(packet::toBytes));

        SyncPlayerProgressionPacket.ClientAccumulator outOfOrderAccumulator =
                new SyncPlayerProgressionPacket.ClientAccumulator();
        java.util.Optional<SyncPlayerProgressionPacket.ProgressionSnapshot> completed = java.util.Optional.empty();
        for (int index = progressionChunks.size() - 1; index >= 0; index--) {
            completed = outOfOrderAccumulator.accept(progressionChunks.get(index));
        }
        assertTrue(completed.isPresent());
        assertEquals(progressionIds, completed.orElseThrow().learnedBlueprints());
        assertEquals(progressionIds, completed.orElseThrow().discoveredBlueprints());

        SyncPlayerProgressionPacket.ClientAccumulator replacedAccumulator =
                new SyncPlayerProgressionPacket.ClientAccumulator();
        assertFalse(replacedAccumulator.accept(progressionChunks.get(0)).isPresent());
        var replacementChunks = SyncPlayerProgressionPacket.split(
                progressionIds,
                progressionIds,
                PlayerProgressionLimits.MAX_RESEARCH_POINTS,
                44L);
        assertFalse(replacedAccumulator.accept(replacementChunks.get(0)).isPresent());
        assertFalse(replacedAccumulator.accept(replacementChunks.get(0)).isPresent());
        for (int index = 1; index < replacementChunks.size() - 1; index++) {
            assertFalse(replacedAccumulator.accept(replacementChunks.get(index)).isPresent());
        }
        assertTrue(replacedAccumulator.accept(replacementChunks.get(replacementChunks.size() - 1)).isPresent());
    }

    @Test
    void clientCatalogCannotOverwriteServerCatalogAndStaleRecipesAreNotSynchronized() {
        ResourceLocation serverId = new ResourceLocation("test", "server");
        ResourceLocation clientId = new ResourceLocation("test", "client");
        try {
            BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of(serverId, blueprint(serverId)));
            BlueprintDataManager.CLIENT.setBlueprintDataMap(Map.of(clientId, blueprint(clientId)));

            assertEquals(Set.of(serverId), BlueprintDataManager.SERVER.getBlueprintDataMap().keySet());
            assertEquals(Set.of(clientId), BlueprintDataManager.CLIENT.getBlueprintDataMap().keySet());
            assertEquals(
                    Set.of("test:recipe/server"),
                    RecipeSyncFilter.activeLearnedRecipes(
                            Set.of("test:recipe/server", "removed:stale_recipe"),
                            Set.of(),
                            BlueprintDataManager.SERVER.getBlueprintDataMap(),
                            BlueprintDataManager.SERVER.getRecipeToBlueprintMap()));
        } finally {
            BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of());
            BlueprintDataManager.CLIENT.setBlueprintDataMap(Map.of());
        }
    }

    @Test
    void duplicateRecipeAliasesMigrateToTheCanonicalRecipeForTheirBlueprint() {
        ResourceLocation blueprintId = new ResourceLocation("test", "shared_output");
        BlueprintData canonical = blueprint(blueprintId);
        ResourceLocation canonicalRecipe = canonical.getRecipeId();
        ResourceLocation legacyAlias = new ResourceLocation("oldpack", "gun/shared_output");

        assertEquals(
                Set.of(canonicalRecipe.toString()),
                RecipeSyncFilter.activeLearnedRecipes(
                        Set.of(legacyAlias.toString()),
                        Set.of(),
                        Map.of(blueprintId, canonical),
                        Map.of(legacyAlias, blueprintId, canonicalRecipe, blueprintId)));
        assertEquals(
                Set.of(canonicalRecipe.toString()),
                RecipeSyncFilter.activeLearnedRecipes(
                        Set.of(),
                        Set.of(blueprintId.toString()),
                        Map.of(blueprintId, canonical),
                        Map.of(canonicalRecipe, blueprintId)));
    }

    private static BlueprintData blueprint(ResourceLocation blueprintId) {
        return blueprint(blueprintId, "item.test.name", "item.test.tooltip", "rifle");
    }

    private static void assertEncodedWithinBudget(java.util.function.Consumer<FriendlyByteBuf> encoder) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            encoder.accept(buffer);
            assertTrue(buffer.readableBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES);
        } finally {
            buffer.release();
        }
    }

    private static BlueprintData blueprint(
            ResourceLocation blueprintId,
            String nameKey,
            String tooltipKey,
            String itemType) {
        return new BlueprintData(
                "deliberately:different_from_map_key",
                nameKey,
                tooltipKey,
                new ResourceLocation("test", "recipe/" + blueprintId.getPath()),
                null,
                itemType,
                new ResourceLocation("test", "display/rifle"));
    }
}
