package com.gamergaming.taczweaponblueprints.loot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootCatalogCache;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootEntry;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootRolls;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootSnapshot;

import net.minecraft.resources.ResourceLocation;

/** Resolves the exact non-random policy consumed by a dynamic loot rule. */
public final class BlueprintLootPolicyResolver {
    private BlueprintLootPolicyResolver() {
    }

    public static EffectiveRule resolve(
            BlueprintLootSnapshot snapshot,
            BlueprintLootSnapshot.RuleBinding binding,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation dimension,
            float luck,
            RuntimeDefaults defaults) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(binding, "binding");
        RuleSettings settings = resolveSettings(binding, dimension, luck, defaults);
        return resolveCandidates(snapshot, binding, catalog, settings);
    }

    public static RuleSettings resolveSettings(
            BlueprintLootSnapshot.RuleBinding binding,
            ResourceLocation dimension,
            float luck,
            RuntimeDefaults defaults) {
        Objects.requireNonNull(binding, "binding");
        RuntimeDefaults stableDefaults = defaults == null ? RuntimeDefaults.DISABLED : defaults;

        boolean predicateMatches = binding.rule().predicate()
                .map(predicate -> predicate.matches(dimension, luck))
                .orElse(true);
        float chance = BlueprintLootSelector.sanitizeProbability(
                binding.rule().chance().map(Float::doubleValue).orElse(stableDefaults.chance()));
        BlueprintLootSelector.RollRange resolvedRolls = binding.rule().rolls()
                .map(BlueprintLootPolicyResolver::sanitizeRolls)
                .orElseGet(() -> BlueprintLootSelector.sanitizeRollRange(
                        stableDefaults.minRolls(), stableDefaults.maxRolls()));
        return new RuleSettings(
                binding.ruleId(),
                binding.rule().pool(),
                stableDefaults.blueprintsEnabled() && binding.rule().enabled(),
                predicateMatches,
                chance,
                new RollRange(resolvedRolls.min(), resolvedRolls.max()),
                stableDefaults.excluded());
    }

    public static EffectiveRule resolveCandidates(
            BlueprintLootSnapshot snapshot,
            BlueprintLootSnapshot.RuleBinding binding,
            Map<ResourceLocation, BlueprintData> catalog,
            RuleSettings settings) {
        Objects.requireNonNull(snapshot, "snapshot");
        Objects.requireNonNull(binding, "binding");
        Objects.requireNonNull(settings, "settings");
        if (!settings.ruleId().equals(binding.ruleId())
                || !settings.poolId().equals(binding.rule().pool())) {
            throw new IllegalArgumentException("rule settings do not belong to binding " + binding.ruleId());
        }
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;

        List<BlueprintLootSelector.WeightedEntry<ResourceLocation>> eligible = new ArrayList<>();
        int catalogCandidateCount = 0;
        for (BlueprintLootEntry entry : BlueprintLootCatalogCache.entriesFor(
                snapshot, binding.rule().pool(), stableCatalog)) {
            catalogCandidateCount++;
            if (!stableCatalog.containsKey(entry.blueprint()) || settings.excluded().test(entry.blueprint())) {
                continue;
            }
            BlueprintLootSelector.createEntry(
                    entry.blueprint().toString(), entry.blueprint(), entry.weight()).ifPresent(eligible::add);
        }

        double totalWeight = eligible.stream()
                .mapToDouble(BlueprintLootSelector.WeightedEntry::weight)
                .sum();
        List<Candidate> candidates = eligible.stream()
                .map(entry -> new Candidate(
                        entry.blueprintId(),
                        entry.weight(),
                        totalWeight > 0.0 ? entry.weight() / totalWeight : 0.0))
                .toList();
        boolean active = settings.canAttempt()
                && !eligible.isEmpty();
        double expectedAdditions = active
                ? settings.chance() * ((settings.rolls().min() + settings.rolls().max()) / 2.0)
                : 0.0;

        return new EffectiveRule(
                settings,
                catalogCandidateCount,
                candidates,
                totalWeight,
                expectedAdditions,
                active,
                eligible);
    }

    private static BlueprintLootSelector.RollRange sanitizeRolls(BlueprintLootRolls rolls) {
        return BlueprintLootSelector.sanitizeRollRange(rolls.min(), rolls.max());
    }

    public record RuntimeDefaults(
            boolean blueprintsEnabled,
            double chance,
            int minRolls,
            int maxRolls,
            Predicate<ResourceLocation> excluded) {
        private static final RuntimeDefaults DISABLED = new RuntimeDefaults(
                false, 0.0, 0, 0, ignored -> false);

        public RuntimeDefaults {
            excluded = excluded == null ? ignored -> false : excluded;
        }
    }

    public record RollRange(int min, int max) {
    }

    public record RuleSettings(
            ResourceLocation ruleId,
            ResourceLocation poolId,
            boolean blueprintsEnabled,
            boolean predicateMatches,
            float chance,
            RollRange rolls,
            Predicate<ResourceLocation> excluded) {
        public RuleSettings(
                ResourceLocation ruleId,
                ResourceLocation poolId,
                boolean blueprintsEnabled,
                boolean predicateMatches,
                float chance,
                RollRange rolls) {
            this(ruleId, poolId, blueprintsEnabled, predicateMatches, chance, rolls, ignored -> false);
        }

        public RuleSettings {
            Objects.requireNonNull(ruleId, "ruleId");
            Objects.requireNonNull(poolId, "poolId");
            Objects.requireNonNull(rolls, "rolls");
            excluded = excluded == null ? ignored -> false : excluded;
        }

        public boolean canAttempt() {
            return blueprintsEnabled && predicateMatches && chance > 0.0f && rolls.max() > 0;
        }

        public boolean shouldEvaluateChance() {
            return blueprintsEnabled && predicateMatches;
        }
    }

    public record Candidate(ResourceLocation blueprintId, float weight, double probability) {
        public Candidate {
            Objects.requireNonNull(blueprintId, "blueprintId");
            if (!Float.isFinite(weight) || weight <= 0.0f) {
                throw new IllegalArgumentException("weight must be finite and greater than zero");
            }
            if (!Double.isFinite(probability) || probability < 0.0 || probability > 1.0) {
                throw new IllegalArgumentException("probability must be finite and between zero and one");
            }
        }
    }

    public static final class EffectiveRule {
        private final RuleSettings settings;
        private final int catalogCandidateCount;
        private final List<Candidate> candidates;
        private final double totalWeight;
        private final double expectedAdditions;
        private final boolean active;
        private final List<BlueprintLootSelector.WeightedEntry<ResourceLocation>> selectionEntries;

        private EffectiveRule(
                RuleSettings settings,
                int catalogCandidateCount,
                List<Candidate> candidates,
                double totalWeight,
                double expectedAdditions,
                boolean active,
                List<BlueprintLootSelector.WeightedEntry<ResourceLocation>> selectionEntries) {
            this.settings = settings;
            this.catalogCandidateCount = catalogCandidateCount;
            this.candidates = List.copyOf(candidates);
            this.totalWeight = totalWeight;
            this.expectedAdditions = expectedAdditions;
            this.active = active;
            this.selectionEntries = List.copyOf(selectionEntries);
        }

        public ResourceLocation ruleId() {
            return settings.ruleId();
        }

        public ResourceLocation poolId() {
            return settings.poolId();
        }

        public boolean blueprintsEnabled() {
            return settings.blueprintsEnabled();
        }

        public boolean predicateMatches() {
            return settings.predicateMatches();
        }

        public float chance() {
            return settings.chance();
        }

        public RollRange rolls() {
            return settings.rolls();
        }

        public int catalogCandidateCount() {
            return catalogCandidateCount;
        }

        public List<Candidate> candidates() {
            return candidates;
        }

        public double totalWeight() {
            return totalWeight;
        }

        public double expectedAdditions() {
            return expectedAdditions;
        }

        public boolean active() {
            return active;
        }

        public Optional<ResourceLocation> select(double randomUnit) {
            return BlueprintLootSelector.selectWeighted(selectionEntries, randomUnit)
                    .map(BlueprintLootSelector.WeightedEntry::value);
        }
    }
}
