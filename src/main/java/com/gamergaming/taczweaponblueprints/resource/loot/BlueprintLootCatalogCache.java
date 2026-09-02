package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;

import net.minecraft.resources.ResourceLocation;

public final class BlueprintLootCatalogCache {
    private static volatile CacheState cache = CacheState.EMPTY;

    private BlueprintLootCatalogCache() {
    }

    public static List<BlueprintLootEntry> entriesFor(
            BlueprintLootSnapshot snapshot,
            ResourceLocation poolId,
            Map<ResourceLocation, BlueprintData> catalog) {
        if (snapshot == null || poolId == null || catalog == null) {
            return List.of();
        }

        CacheState current = cache;
        if (current.snapshot() != snapshot || current.catalog() != catalog) {
            synchronized (BlueprintLootCatalogCache.class) {
                current = cache;
                if (current.snapshot() != snapshot || current.catalog() != catalog) {
                    current = rebuild(snapshot, catalog);
                    cache = current;
                }
            }
        }
        CacheState stable = current;
        return stable.entriesByPool().computeIfAbsent(
                poolId,
                id -> resolve(stable.snapshot().pools().get(id), stable.catalog()));
    }

    static List<BlueprintLootEntry> resolve(
            BlueprintLootPool pool,
            Map<ResourceLocation, BlueprintData> catalog) {
        if (pool == null || catalog == null || catalog.isEmpty()) {
            return List.of();
        }

        Map<ResourceLocation, Double> weights = new LinkedHashMap<>();
        pool.entries().forEach(entry -> {
            if (catalog.containsKey(entry.blueprint())) {
                addWeight(weights, entry.blueprint(), entry.weight());
            }
        });

        List<Map.Entry<ResourceLocation, BlueprintData>> catalogEntries = new ArrayList<>(catalog.entrySet());
        catalogEntries.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        for (BlueprintCatalogSelector selector : pool.selectors()) {
            for (Map.Entry<ResourceLocation, BlueprintData> entry : catalogEntries) {
                if (selector.matches(entry.getKey(), entry.getValue())) {
                    addWeight(weights, entry.getKey(), selector.weight());
                }
            }
        }

        if (weights.size() > BlueprintLootPool.MAX_ENTRIES) {
            throw new IllegalStateException("resolved catalog pool exceeds "
                    + BlueprintLootPool.MAX_ENTRIES + " entries");
        }
        return weights.entrySet().stream()
                .map(entry -> new BlueprintLootEntry(entry.getKey(), entry.getValue().floatValue()))
                .toList();
    }

    private static CacheState rebuild(
            BlueprintLootSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog) {
        if (snapshot == null || catalog == null) {
            return CacheState.EMPTY;
        }
        return new CacheState(snapshot, catalog, new ConcurrentHashMap<>());
    }

    private static void addWeight(Map<ResourceLocation, Double> weights, ResourceLocation id, float weight) {
        double combined = weights.getOrDefault(id, 0.0) + weight;
        if (!Double.isFinite(combined) || combined <= 0.0 || combined > Float.MAX_VALUE) {
            throw new IllegalStateException("combined catalog selector weight overflow for " + id);
        }
        weights.put(id, combined);
    }

    private record CacheState(
            BlueprintLootSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ConcurrentMap<ResourceLocation, List<BlueprintLootEntry>> entriesByPool) {
        private static final CacheState EMPTY = new CacheState(null, null, new ConcurrentHashMap<>());
    }
}
