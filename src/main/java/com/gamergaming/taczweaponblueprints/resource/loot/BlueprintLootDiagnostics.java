package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;

import net.minecraft.resources.ResourceLocation;

/**
 * Read-only diagnostics for the prepared loot snapshot. Keeping this separate
 * from Brigadier makes operator output deterministic and unit-testable.
 */
public final class BlueprintLootDiagnostics {
    private BlueprintLootDiagnostics() {
    }

    public static Summary summarize(BlueprintLootSnapshot snapshot, int catalogSize) {
        BlueprintLootSnapshot stable = snapshot == null ? BlueprintLootSnapshot.EMPTY : snapshot;
        int enabledRules = (int) stable.rules().values().stream()
                .filter(BlueprintLootRule::enabled)
                .count();
        return new Summary(
                stable.tags().size(),
                stable.pools().size(),
                stable.rules().size(),
                enabledRules,
                stable.bindingCount(),
                stable.selectorBindings().size(),
                Math.max(0, catalogSize),
                stable.active(),
                stable.ownsDistribution(),
                stable.globallyDisablesDistribution());
    }

    public static TableReport inspect(
            BlueprintLootSnapshot snapshot,
            ResourceLocation lootTableId,
            ResourceLocation dimension,
            float luck,
            Map<ResourceLocation, BlueprintData> catalog) {
        BlueprintLootSnapshot stable = snapshot == null ? BlueprintLootSnapshot.EMPTY : snapshot;
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        if (lootTableId == null) {
            return new TableReport(null, false, List.of());
        }

        List<RuleReport> reports = new ArrayList<>();
        stable.rules().forEach((ruleId, rule) -> {
            TargetMatch targetMatch = targetMatch(rule, lootTableId);
            if (targetMatch == TargetMatch.NONE) {
                return;
            }

            boolean predicateMatches = rule.predicate()
                    .map(predicate -> predicate.matches(dimension, luck))
                    .orElse(true);
            int catalogCandidates = rule.enabled()
                    ? BlueprintLootCatalogCache.entriesFor(stable, rule.pool(), stableCatalog).size()
                    : 0;
            reports.add(new RuleReport(
                    ruleId,
                    rule.enabled(),
                    rule.pool(),
                    targetMatch,
                    predicateMatches,
                    catalogCandidates));
        });
        reports.sort(Comparator.comparing(report -> report.ruleId().toString()));
        return new TableReport(lootTableId, stable.ownsLootTable(lootTableId), reports);
    }

    public static PoolReport inspectPool(
            BlueprintLootSnapshot snapshot,
            ResourceLocation poolId,
            Map<ResourceLocation, BlueprintData> catalog) {
        BlueprintLootSnapshot stable = snapshot == null ? BlueprintLootSnapshot.EMPTY : snapshot;
        if (poolId == null) {
            return new PoolReport(null, false, 0, 0, 0);
        }
        BlueprintLootPool pool = stable.pools().get(poolId);
        if (pool == null) {
            return new PoolReport(poolId, false, 0, 0, 0);
        }
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        return new PoolReport(
                poolId,
                true,
                pool.entries().size(),
                pool.selectors().size(),
                BlueprintLootCatalogCache.entriesFor(stable, poolId, stableCatalog).size());
    }

    private static TargetMatch targetMatch(BlueprintLootRule rule, ResourceLocation lootTableId) {
        boolean exact = rule.lootTables().contains(lootTableId);
        boolean selector = rule.lootTableSelector()
                .map(value -> value.matches(lootTableId))
                .orElse(false);
        if (exact && selector) {
            return TargetMatch.EXACT_AND_SELECTOR;
        }
        if (exact) {
            return TargetMatch.EXACT;
        }
        if (selector) {
            return TargetMatch.SELECTOR;
        }
        if (!rule.enabled() && rule.lootTables().isEmpty() && rule.lootTableSelector().isEmpty()) {
            return TargetMatch.GLOBAL_DISABLE;
        }
        return TargetMatch.NONE;
    }

    public record Summary(
            int tagCount,
            int poolCount,
            int ruleCount,
            int enabledRuleCount,
            int exactBindingCount,
            int selectorRuleCount,
            int catalogSize,
            boolean active,
            boolean ownsDistribution,
            boolean globallyDisabled) {
    }

    public record TableReport(
            ResourceLocation lootTableId,
            boolean dynamicallyOwned,
            List<RuleReport> rules) {
        public TableReport {
            rules = rules == null ? List.of() : List.copyOf(rules);
        }

        public long contextEligibleRuleCount() {
            return rules.stream().filter(RuleReport::contextEligible).count();
        }
    }

    public record RuleReport(
            ResourceLocation ruleId,
            boolean enabled,
            ResourceLocation poolId,
            TargetMatch targetMatch,
            boolean predicateMatches,
            int catalogCandidates) {
        public boolean contextEligible() {
            return enabled && predicateMatches && catalogCandidates > 0;
        }
    }

    public record PoolReport(
            ResourceLocation poolId,
            boolean exists,
            int composedEntryCount,
            int selectorCount,
            int catalogCandidateCount) {
    }

    public enum TargetMatch {
        NONE("none"),
        EXACT("exact"),
        SELECTOR("selector"),
        EXACT_AND_SELECTOR("exact+selector"),
        GLOBAL_DISABLE("global-disable");

        private final String description;

        TargetMatch(String description) {
            this.description = description;
        }

        public String description() {
            return description;
        }
    }
}
