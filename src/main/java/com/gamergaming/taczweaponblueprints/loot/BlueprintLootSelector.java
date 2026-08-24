package com.gamergaming.taczweaponblueprints.loot;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;

final class BlueprintLootSelector {
    static final int MAX_BLUEPRINT_ROLLS = 64;

    private BlueprintLootSelector() {
    }

    static <T> Optional<WeightedEntry<T>> createEntry(String blueprintId, T value, Float weight) {
        ResourceLocation parsedId = blueprintId == null ? null : ResourceLocation.tryParse(blueprintId);
        if (parsedId == null || value == null || !isPositiveFinite(weight)) {
            return Optional.empty();
        }
        return Optional.of(new WeightedEntry<>(parsedId, value, weight));
    }

    static <T> List<WeightedEntry<T>> filterEligible(
            Collection<WeightedEntry<T>> candidates,
            Predicate<ResourceLocation> eligibility) {
        if (candidates == null || eligibility == null) {
            return List.of();
        }

        List<WeightedEntry<T>> eligible = new ArrayList<>();
        for (WeightedEntry<T> candidate : candidates) {
            if (candidate != null && eligibility.test(candidate.blueprintId())) {
                eligible.add(candidate);
            }
        }
        return List.copyOf(eligible);
    }

    static <T> Optional<WeightedEntry<T>> selectWeighted(
            List<WeightedEntry<T>> candidates,
            double randomUnit) {
        if (candidates == null || candidates.isEmpty()) {
            return Optional.empty();
        }

        double totalWeight = candidates.stream().mapToDouble(WeightedEntry::weight).sum();
        if (!(totalWeight > 0.0) || !Double.isFinite(totalWeight)) {
            return Optional.empty();
        }

        double unit = Double.isFinite(randomUnit) ? randomUnit : 0.0;
        unit = Math.max(0.0, Math.min(Math.nextDown(1.0), unit));
        double target = unit * totalWeight;
        double cumulativeWeight = 0.0;
        WeightedEntry<T> lastEntry = null;

        for (WeightedEntry<T> candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            cumulativeWeight += candidate.weight();
            lastEntry = candidate;
            if (target < cumulativeWeight) {
                return Optional.of(candidate);
            }
        }
        return Optional.ofNullable(lastEntry);
    }

    static RollRange sanitizeRollRange(int min, int max) {
        int safeMin = Math.max(0, Math.min(MAX_BLUEPRINT_ROLLS, min));
        int safeMax = Math.max(0, Math.min(MAX_BLUEPRINT_ROLLS, max));
        return new RollRange(safeMin, Math.max(safeMin, safeMax));
    }

    static int remainingBlueprintBudget(int existingBlueprints) {
        int safeExisting = Math.max(0, existingBlueprints);
        return Math.max(0, MAX_BLUEPRINT_ROLLS - safeExisting);
    }

    static int constrainRollsToBudget(int requestedRolls, int remainingBudget) {
        return Math.max(0, Math.min(requestedRolls, Math.max(0, remainingBudget)));
    }

    static float sanitizeProbability(double probability) {
        if (!Double.isFinite(probability)) {
            return 0.0f;
        }
        return (float) Math.max(0.0, Math.min(1.0, probability));
    }

    private static boolean isPositiveFinite(Float value) {
        return value != null && Float.isFinite(value) && value > 0.0f;
    }

    record WeightedEntry<T>(ResourceLocation blueprintId, T value, float weight) {
        WeightedEntry {
            Objects.requireNonNull(blueprintId, "blueprintId");
            Objects.requireNonNull(value, "value");
            if (!Float.isFinite(weight) || weight <= 0.0f) {
                throw new IllegalArgumentException("weight must be finite and greater than zero");
            }
        }
    }

    record RollRange(int min, int max) {
    }
}
