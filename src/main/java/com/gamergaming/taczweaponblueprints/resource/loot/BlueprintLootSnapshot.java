package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

public record BlueprintLootSnapshot(
        Map<ResourceLocation, BlueprintLootTag> tags,
        Map<ResourceLocation, BlueprintLootPool> pools,
        Map<ResourceLocation, BlueprintLootRule> rules,
        Map<ResourceLocation, List<RuleBinding>> rulesByLootTable,
        List<RuleBinding> selectorBindings,
        int bindingCount) {
    public static final int MAX_BINDINGS = 65_536;
    public static final int MAX_INHERITANCE_DEPTH = 64;
    public static final BlueprintLootSnapshot EMPTY = new BlueprintLootSnapshot(
            Map.of(), Map.of(), Map.of(), Map.of(), List.of(), 0);

    public BlueprintLootSnapshot {
        tags = immutableMap(tags);
        pools = immutableMap(pools);
        rules = immutableMap(rules);
        rulesByLootTable = immutableBindingMap(rulesByLootTable);
        selectorBindings = selectorBindings == null ? List.of() : List.copyOf(selectorBindings);
        if (bindingCount < 0 || bindingCount > MAX_BINDINGS) {
            throw new IllegalArgumentException("bindingCount must be between zero and " + MAX_BINDINGS);
        }
    }

    public static BlueprintLootSnapshot create(
            Map<ResourceLocation, BlueprintLootPool> pools,
            Map<ResourceLocation, BlueprintLootRule> rules) {
        return create(Map.of(), pools, rules);
    }

    public static BlueprintLootSnapshot create(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintLootPool> pools,
            Map<ResourceLocation, BlueprintLootRule> rules) {
        Map<ResourceLocation, BlueprintLootTag> sortedTags = sortedCopy(tags);
        Map<ResourceLocation, BlueprintLootPool> sortedPools = resolvePools(sortedCopy(pools), sortedTags);
        Map<ResourceLocation, BlueprintLootRule> sortedRules = sortedCopy(rules);
        Map<ResourceLocation, List<RuleBinding>> bindings = new LinkedHashMap<>();
        List<RuleBinding> selectorBindings = new ArrayList<>();
        int bindingCount = 0;

        for (Map.Entry<ResourceLocation, BlueprintLootRule> ruleEntry : sortedRules.entrySet()) {
            ResourceLocation ruleId = ruleEntry.getKey();
            BlueprintLootRule rule = ruleEntry.getValue();
            if (!rule.enabled()) {
                continue;
            }

            BlueprintLootPool pool = sortedPools.get(rule.pool());
            if (pool == null) {
                throw new IllegalArgumentException(
                        "enabled loot rule " + ruleId + " references missing pool " + rule.pool());
            }

            RuleBinding binding = new RuleBinding(ruleId, rule, pool);
            if (rule.lootTableSelector().isPresent()) {
                selectorBindings.add(binding);
            }
            for (ResourceLocation lootTableId : rule.lootTables()) {
                if (bindingCount >= MAX_BINDINGS) {
                    throw new IllegalArgumentException(
                            "blueprint loot data cannot contain more than " + MAX_BINDINGS + " exact bindings");
                }
                bindings.computeIfAbsent(lootTableId, ignored -> new ArrayList<>()).add(binding);
                bindingCount++;
            }
        }

        return new BlueprintLootSnapshot(
                sortedTags,
                sortedPools,
                sortedRules,
                bindings,
                selectorBindings,
                bindingCount);
    }

    public boolean active() {
        return !rulesByLootTable.isEmpty() || !selectorBindings.isEmpty();
    }

    public boolean ownsDistribution() {
        return !rules.isEmpty();
    }

    public boolean globallyDisablesDistribution() {
        return rules.values().stream().anyMatch(rule ->
                !rule.enabled() && rule.lootTables().isEmpty() && rule.lootTableSelector().isEmpty());
    }

    public boolean ownsLootTable(ResourceLocation lootTableId) {
        if (lootTableId == null || rules.isEmpty()) {
            return false;
        }
        for (BlueprintLootRule rule : rules.values()) {
            if (!rule.enabled() && rule.lootTables().isEmpty() && rule.lootTableSelector().isEmpty()) {
                return true;
            }
            if (rule.lootTables().contains(lootTableId)
                    || rule.lootTableSelector().map(selector -> selector.matches(lootTableId)).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    public List<RuleBinding> rulesFor(ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return List.of();
        }

        Map<ResourceLocation, RuleBinding> matches = new LinkedHashMap<>();
        rulesByLootTable.getOrDefault(lootTableId, List.of())
                .forEach(binding -> matches.put(binding.ruleId(), binding));
        selectorBindings.stream()
                .filter(binding -> binding.rule().lootTableSelector()
                        .map(selector -> selector.matches(lootTableId))
                        .orElse(false))
                .forEach(binding -> matches.putIfAbsent(binding.ruleId(), binding));

        return matches.values().stream()
                .sorted(Comparator.comparing(binding -> binding.ruleId().toString()))
                .toList();
    }

    private static Map<ResourceLocation, BlueprintLootPool> resolvePools(
            Map<ResourceLocation, BlueprintLootPool> rawPools,
            Map<ResourceLocation, BlueprintLootTag> tags) {
        Map<ResourceLocation, BlueprintLootPool> resolved = new LinkedHashMap<>();
        for (ResourceLocation poolId : rawPools.keySet()) {
            resolvePool(poolId, rawPools, tags, resolved, new LinkedHashSet<>());
        }
        return sortedCopy(resolved);
    }

    private static BlueprintLootPool resolvePool(
            ResourceLocation poolId,
            Map<ResourceLocation, BlueprintLootPool> rawPools,
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintLootPool> resolved,
            Set<ResourceLocation> resolving) {
        BlueprintLootPool existing = resolved.get(poolId);
        if (existing != null) {
            return existing;
        }
        BlueprintLootPool raw = rawPools.get(poolId);
        if (raw == null) {
            throw new IllegalArgumentException("blueprint loot pool references missing pool " + poolId);
        }
        if (!resolving.add(poolId)) {
            throw new IllegalArgumentException("blueprint loot pool inheritance cycle: "
                    + String.join(" -> ", resolving.stream().map(ResourceLocation::toString).toList())
                    + " -> " + poolId);
        }
        if (resolving.size() > MAX_INHERITANCE_DEPTH) {
            throw new IllegalArgumentException("blueprint loot pool inheritance exceeds depth "
                    + MAX_INHERITANCE_DEPTH + " at " + poolId);
        }

        try {
            ResolutionAccumulator accumulator = new ResolutionAccumulator(poolId);
            for (BlueprintLootPoolReference reference : raw.includes()) {
                BlueprintLootPool inherited = resolvePool(
                        reference.pool(), rawPools, tags, resolved, resolving);
                inherited.entries().forEach(entry -> accumulator.addEntry(
                        entry.blueprint(), multiply(entry.weight(), reference.weight(), "inherited entry")));
                inherited.selectors().forEach(selector ->
                        accumulator.addSelector(selector.multiplyWeight(reference.weight())));
            }
            for (BlueprintLootTagReference reference : raw.tags()) {
                BlueprintLootTag tag = tags.get(reference.tag());
                if (tag == null) {
                    throw new IllegalArgumentException(
                            "blueprint loot pool " + poolId + " references missing tag " + reference.tag());
                }
                tag.values().forEach(blueprintId -> accumulator.addEntry(blueprintId, reference.weight()));
            }
            raw.entries().forEach(entry -> accumulator.addEntry(entry.blueprint(), entry.weight()));
            raw.selectors().forEach(accumulator::addSelector);
            accumulator.validatePotentialWeights();

            BlueprintLootPool flattened = new BlueprintLootPool(
                    raw.format(),
                    accumulator.entries(),
                    List.of(),
                    List.of(),
                    accumulator.selectors());
            resolved.put(poolId, flattened);
            return flattened;
        } finally {
            resolving.remove(poolId);
        }
    }

    private static float multiply(float left, float right, String description) {
        double result = (double) left * right;
        float narrowed = (float) result;
        if (!Double.isFinite(result) || result <= 0.0 || result > Float.MAX_VALUE
                || !Float.isFinite(narrowed) || narrowed <= 0.0f) {
            throw new IllegalArgumentException(description + " weight overflow or underflow");
        }
        return narrowed;
    }

    private static <T> Map<ResourceLocation, T> sortedCopy(Map<ResourceLocation, T> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        List<Map.Entry<ResourceLocation, T>> entries = new ArrayList<>(values.entrySet());
        entries.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        Map<ResourceLocation, T> sorted = new LinkedHashMap<>();
        entries.forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        return Collections.unmodifiableMap(sorted);
    }

    private static <T> Map<ResourceLocation, T> immutableMap(Map<ResourceLocation, T> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        return Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    private static Map<ResourceLocation, List<RuleBinding>> immutableBindingMap(
            Map<ResourceLocation, List<RuleBinding>> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<ResourceLocation, List<RuleBinding>> immutable = new LinkedHashMap<>();
        values.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> immutable.put(entry.getKey(), List.copyOf(entry.getValue())));
        return Collections.unmodifiableMap(immutable);
    }

    public record RuleBinding(
            ResourceLocation ruleId,
            BlueprintLootRule rule,
            BlueprintLootPool pool) {
    }

    private static final class ResolutionAccumulator {
        private final ResourceLocation poolId;
        private final Map<ResourceLocation, Double> weights = new LinkedHashMap<>();
        private final List<BlueprintCatalogSelector> selectors = new ArrayList<>();
        private double selectorWeight;

        private ResolutionAccumulator(ResourceLocation poolId) {
            this.poolId = poolId;
        }

        private void addEntry(ResourceLocation blueprintId, float weight) {
            double combined = weights.getOrDefault(blueprintId, 0.0) + weight;
            if (!Double.isFinite(combined) || combined > Float.MAX_VALUE) {
                throw new IllegalArgumentException("combined blueprint weight overflow in pool " + poolId);
            }
            weights.put(blueprintId, combined);
            if (weights.size() > BlueprintLootPool.MAX_ENTRIES) {
                throw new IllegalArgumentException("resolved blueprint loot pool " + poolId
                        + " cannot contain more than " + BlueprintLootPool.MAX_ENTRIES + " entries");
            }
        }

        private void addSelector(BlueprintCatalogSelector selector) {
            selectors.add(selector);
            selectorWeight += selector.weight();
            if (!Double.isFinite(selectorWeight) || selectorWeight > Float.MAX_VALUE) {
                throw new IllegalArgumentException("combined selector weight overflow in pool " + poolId);
            }
            if (selectors.size() > BlueprintLootPool.MAX_SOURCES) {
                throw new IllegalArgumentException("resolved blueprint loot pool " + poolId
                        + " cannot contain more than " + BlueprintLootPool.MAX_SOURCES + " selectors");
            }
        }

        private void validatePotentialWeights() {
            weights.values().forEach(weight -> {
                if (!Double.isFinite(weight + selectorWeight) || weight + selectorWeight > Float.MAX_VALUE) {
                    throw new IllegalArgumentException("combined entry and selector weight overflow in pool " + poolId);
                }
            });
        }

        private List<BlueprintLootEntry> entries() {
            return weights.entrySet().stream()
                    .map(entry -> new BlueprintLootEntry(entry.getKey(), entry.getValue().floatValue()))
                    .toList();
        }

        private List<BlueprintCatalogSelector> selectors() {
            return List.copyOf(selectors);
        }
    }
}
