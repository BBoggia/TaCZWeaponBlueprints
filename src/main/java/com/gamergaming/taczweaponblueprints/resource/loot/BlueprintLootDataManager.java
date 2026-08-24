package com.gamergaming.taczweaponblueprints.resource.loot;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

public final class BlueprintLootDataManager extends SimplePreparableReloadListener<BlueprintLootSnapshot> {
    public static final BlueprintLootDataManager INSTANCE = new BlueprintLootDataManager();
    public static final int MAX_DEFINITIONS_PER_TYPE = 4096;

    static final String POOL_DIRECTORY = "taczweaponblueprints/loot_pools";
    static final String RULE_DIRECTORY = "taczweaponblueprints/loot_rules";
    static final String TAG_DIRECTORY = "taczweaponblueprints/blueprint_tags";

    private volatile Publication publication = new Publication(BlueprintLootSnapshot.EMPTY, 0L);

    private BlueprintLootDataManager() {
    }

    @Override
    protected BlueprintLootSnapshot prepare(ResourceManager resourceManager, ProfilerFiller profiler) {
        Map<ResourceLocation, BlueprintLootTag> tags = loadDefinitions(
                resourceManager,
                TAG_DIRECTORY,
                BlueprintLootTag.CODEC,
                "loot tag");
        Map<ResourceLocation, BlueprintLootPool> pools = loadDefinitions(
                resourceManager,
                POOL_DIRECTORY,
                BlueprintLootPool.CODEC,
                "loot pool");
        Map<ResourceLocation, BlueprintLootRule> rules = loadDefinitions(
                resourceManager,
                RULE_DIRECTORY,
                BlueprintLootRule.CODEC,
                "loot rule");

        try {
            return BlueprintLootSnapshot.create(tags, pools, rules);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid blueprint loot data: " + exception.getMessage(), exception);
        }
    }

    @Override
    protected void apply(BlueprintLootSnapshot prepared, ResourceManager resourceManager, ProfilerFiller profiler) {
        Publication previous = publication;
        Publication applied = new Publication(prepared, previous.revision() + 1L);
        publication = applied;
        TaCZWeaponBlueprints.LOGGER.info(
                "Applied blueprint loot snapshot revision {}: {} tags, {} pools, {} rules, "
                        + "{} exact bindings, and {} selector rules",
                applied.revision(),
                prepared.tags().size(),
                prepared.pools().size(),
                prepared.rules().size(),
                prepared.bindingCount(),
                prepared.selectorBindings().size());
    }

    public BlueprintLootSnapshot snapshot() {
        return publication.snapshot();
    }

    public long revision() {
        return publication.revision();
    }

    public Publication publication() {
        return publication;
    }

    public boolean ownsLootDistribution(ResourceLocation lootTableId) {
        return publication.snapshot().ownsLootTable(lootTableId);
    }

    static ResourceLocation definitionId(ResourceLocation resourceId, String directory) {
        String prefix = directory + "/";
        String path = resourceId.getPath();
        if (!path.startsWith(prefix) || !path.endsWith(".json")) {
            throw new IllegalArgumentException("Resource is outside " + directory + ": " + resourceId);
        }
        String definitionPath = path.substring(prefix.length(), path.length() - ".json".length());
        ResourceLocation definitionId = ResourceLocation.tryBuild(resourceId.getNamespace(), definitionPath);
        if (definitionId == null) {
            throw new IllegalArgumentException("Invalid definition ID derived from " + resourceId);
        }
        return definitionId;
    }

    private static <T> Map<ResourceLocation, T> loadDefinitions(
            ResourceManager resourceManager,
            String directory,
            Codec<T> codec,
            String typeName) {
        Map<ResourceLocation, T> definitions = new LinkedHashMap<>();
        resourceManager.listResources(directory, id -> id.getPath().endsWith(".json")).entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    ResourceLocation resourceId = entry.getKey();
                    ResourceLocation definitionId = definitionId(resourceId, directory);
                    T definition = readDefinition(resourceId, entry.getValue(), codec, typeName);
                    T previous = definitions.put(definitionId, definition);
                    if (previous != null) {
                        throw new IllegalStateException("Duplicate blueprint " + typeName + " ID " + definitionId);
                    }
                    if (definitions.size() > MAX_DEFINITIONS_PER_TYPE) {
                        throw new IllegalStateException(
                                "Too many blueprint " + typeName + " definitions; maximum is "
                                        + MAX_DEFINITIONS_PER_TYPE);
                    }
                });
        return definitions;
    }

    private static <T> T readDefinition(
            ResourceLocation resourceId,
            Resource resource,
            Codec<T> codec,
            String typeName) {
        try (BufferedReader reader = resource.openAsReader()) {
            JsonElement json = JsonParser.parseReader(reader);
            DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
            return result.result().orElseThrow(() -> new IllegalStateException(
                    "Invalid blueprint " + typeName + " " + resourceId + " from pack "
                            + resource.sourcePackId() + ": "
                            + result.error().map(DataResult.PartialResult::message).orElse("unknown codec error")));
        } catch (IOException | RuntimeException exception) {
            if (exception instanceof IllegalStateException illegalStateException) {
                throw illegalStateException;
            }
            throw new IllegalStateException(
                    "Failed to read blueprint " + typeName + " " + resourceId
                            + " from pack " + resource.sourcePackId(),
                    exception);
        }
    }

    public record Publication(BlueprintLootSnapshot snapshot, long revision) {
        public Publication {
            if (snapshot == null) {
                throw new IllegalArgumentException("snapshot cannot be null");
            }
            if (revision < 0L) {
                throw new IllegalArgumentException("revision cannot be negative");
            }
        }
    }
}
