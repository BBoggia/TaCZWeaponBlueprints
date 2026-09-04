package com.gamergaming.taczweaponblueprints.loot;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;

/** Builds one immutable, revision-matched fragment selection plan per loot rule. */
public final class BlueprintFragmentLootResolver {
    public static final int BASIS_POINTS = 10_000;
    static final double UNLEARNED_WEIGHT_MULTIPLIER = 2.0;
    static final double LEARNED_WEIGHT_MULTIPLIER = 0.25;

    private BlueprintFragmentLootResolver() {
    }

    public static Plan resolveRuntime(
            List<WeightedTarget> candidates,
            LootContext lootContext) {
        return resolveRuntime(candidates, playerFor(lootContext));
    }

    public static Plan resolveRuntime(
            List<WeightedTarget> candidates,
            ServerPlayer player) {
        return resolveRuntime(candidates, Optional.ofNullable(player));
    }

    private static Plan resolveRuntime(
            List<WeightedTarget> candidates,
            Optional<ServerPlayer> player) {
        ResearchFeatureConfigSnapshot currentConfig = ModConfigs.BLUEPRINT
                .researchFeatureSnapshot();
        int replacementBasisPoints = currentConfig.fragmentLootReplacementBasisPoints();
        if (replacementBasisPoints <= 0) {
            return Plan.disabled(replacementBasisPoints);
        }

        var policyAccess = ProgressionPolicyAccessService.acquire(
                ProgressionPolicyAccessService.Mode.CURRENT_ONLY).orElse(null);
        if (policyAccess == null) {
            return Plan.unavailable(replacementBasisPoints);
        }

        Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> profilePolicies =
                policyAccess.profilePolicies();
        Optional<Set<String>> learned = player
                .flatMap(value -> value.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve())
                .map(IPlayerRecipeData::getLearnedBlueprints)
                .map(Set::copyOf);
        return resolve(candidates, profilePolicies, replacementBasisPoints, learned);
    }

    static Plan resolve(
            List<WeightedTarget> candidates,
            Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> profilePolicies,
            int replacementBasisPoints,
            Optional<Set<String>> learnedBlueprints) {
        if (replacementBasisPoints < 0 || replacementBasisPoints > BASIS_POINTS) {
            throw new IllegalArgumentException("fragment replacement share is out of bounds");
        }
        if (candidates == null || profilePolicies == null || learnedBlueprints == null) {
            throw new IllegalArgumentException("fragment loot planning inputs cannot be null");
        }
        if (replacementBasisPoints == 0) {
            return Plan.disabled(0);
        }

        Map<ResourceLocation, CandidateAccumulator> accumulated = new LinkedHashMap<>();
        for (WeightedTarget candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            ResolvedBlueprintProgressionPolicy policy = profilePolicies.get(candidate.blueprintId());
            if (policy == null || !policy.fragments().enabled()) {
                continue;
            }
            double multiplier = learnedBlueprints
                    .map(learned -> learned.contains(candidate.blueprintId().toString())
                            ? LEARNED_WEIGHT_MULTIPLIER
                            : UNLEARNED_WEIGHT_MULTIPLIER)
                    .orElse(1.0);
            double effectiveWeight = candidate.weight() * multiplier;
            if (!Double.isFinite(effectiveWeight) || effectiveWeight <= 0.0) {
                continue;
            }
            accumulated.compute(candidate.blueprintId(), (ignored, existing) -> {
                if (existing == null) {
                    return new CandidateAccumulator(
                            candidate.weight(),
                            effectiveWeight,
                            policy.fragments().threshold(),
                            policy.researchWorkbenchTier(),
                            policy.exactFragmentThreshold());
                }
                return existing.add(candidate.weight(), effectiveWeight);
            });
        }

        List<Candidate> resolved = accumulated.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        java.util.Comparator.comparing(ResourceLocation::toString)))
                .map(entry -> entry.getValue().candidate(entry.getKey()))
                .toList();
        return new Plan(true, replacementBasisPoints, learnedBlueprints.isPresent(), resolved);
    }

    private static Optional<ServerPlayer> playerFor(LootContext context) {
        if (context == null) {
            return Optional.empty();
        }
        if (context.getParamOrNull(LootContextParams.LAST_DAMAGE_PLAYER)
                instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        Entity killer = context.getParamOrNull(LootContextParams.KILLER_ENTITY);
        if (killer instanceof ServerPlayer player) {
            return Optional.of(player);
        }
        Entity subject = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        return subject instanceof ServerPlayer player ? Optional.of(player) : Optional.empty();
    }

    public record WeightedTarget(ResourceLocation blueprintId, double weight) {
        public WeightedTarget {
            if (blueprintId == null || !Double.isFinite(weight) || weight <= 0.0) {
                throw new IllegalArgumentException("fragment loot target is invalid");
            }
        }
    }

    public record Candidate(
            ResourceLocation blueprintId,
            double baseWeight,
            double effectiveWeight,
            int threshold,
            ResearchWorkbenchTier tier,
            boolean exactThreshold) {
        public Candidate {
            if (blueprintId == null || tier == null
                    || !Double.isFinite(baseWeight) || baseWeight <= 0.0
                    || !Double.isFinite(effectiveWeight) || effectiveWeight <= 0.0
                    || threshold < 1) {
                throw new IllegalArgumentException("resolved fragment loot candidate is invalid");
            }
        }
    }

    public record Plan(
            boolean policyAvailable,
            int replacementBasisPoints,
            boolean playerAware,
            List<Candidate> candidates) {
        public Plan {
            if (replacementBasisPoints < 0 || replacementBasisPoints > BASIS_POINTS
                    || candidates == null
                    || !policyAvailable && !candidates.isEmpty()) {
                throw new IllegalArgumentException("fragment loot plan is invalid");
            }
            candidates = List.copyOf(candidates);
        }

        static Plan disabled(int replacementBasisPoints) {
            return new Plan(true, replacementBasisPoints, false, List.of());
        }

        static Plan unavailable(int replacementBasisPoints) {
            return new Plan(false, replacementBasisPoints, false, List.of());
        }

        public boolean canReplace() {
            return policyAvailable && replacementBasisPoints > 0 && !candidates.isEmpty();
        }

        public boolean shouldReplace(int basisPointDraw) {
            if (basisPointDraw < 0 || basisPointDraw >= BASIS_POINTS) {
                throw new IllegalArgumentException("fragment replacement draw is out of bounds");
            }
            return canReplace() && basisPointDraw < replacementBasisPoints;
        }

        public Optional<ResourceLocation> select(double randomUnit) {
            if (!canReplace()) {
                return Optional.empty();
            }
            double totalWeight = candidates.stream().mapToDouble(Candidate::effectiveWeight).sum();
            if (!Double.isFinite(totalWeight) || totalWeight <= 0.0) {
                return Optional.empty();
            }
            double unit = Double.isFinite(randomUnit) ? randomUnit : 0.0;
            unit = Math.max(0.0, Math.min(Math.nextDown(1.0), unit));
            double target = unit * totalWeight;
            double cumulative = 0.0;
            Candidate last = null;
            for (Candidate candidate : candidates) {
                cumulative += candidate.effectiveWeight();
                last = candidate;
                if (target < cumulative) {
                    return Optional.of(candidate.blueprintId());
                }
            }
            return Optional.ofNullable(last).map(Candidate::blueprintId);
        }

        public double expectedFragments(double expectedBlueprintOpportunities) {
            if (!canReplace() || !Double.isFinite(expectedBlueprintOpportunities)
                    || expectedBlueprintOpportunities <= 0.0) {
                return 0.0;
            }
            return expectedBlueprintOpportunities * replacementBasisPoints / BASIS_POINTS;
        }

        public Map<Integer, Integer> thresholdCounts() {
            Map<Integer, Integer> counts = new java.util.TreeMap<>();
            candidates.forEach(candidate -> counts.merge(candidate.threshold(), 1, Integer::sum));
            return Collections.unmodifiableMap(counts);
        }
    }

    private record CandidateAccumulator(
            double baseWeight,
            double effectiveWeight,
            int threshold,
            ResearchWorkbenchTier tier,
            boolean exactThreshold) {
        private CandidateAccumulator add(double base, double effective) {
            double nextBase = baseWeight + base;
            double nextEffective = effectiveWeight + effective;
            if (!Double.isFinite(nextBase) || !Double.isFinite(nextEffective)) {
                throw new IllegalArgumentException("fragment loot candidate weight overflow");
            }
            return new CandidateAccumulator(
                    nextBase,
                    nextEffective,
                    threshold,
                    tier,
                    exactThreshold);
        }

        private Candidate candidate(ResourceLocation blueprintId) {
            return new Candidate(
                    blueprintId,
                    baseWeight,
                    effectiveWeight,
                    threshold,
                    tier,
                    exactThreshold);
        }
    }
}
