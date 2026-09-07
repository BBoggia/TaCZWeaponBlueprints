package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;

final class BlueprintResearchIngredientValidator {
    private BlueprintResearchIngredientValidator() {
    }

    static void validateExactItems(
            BlueprintResearchSnapshot snapshot,
            Predicate<ResourceLocation> itemExists) {
        if (snapshot == null || itemExists == null) {
            throw new IllegalArgumentException("snapshot and item registry predicate cannot be null");
        }
        snapshot.researchData().policies().forEach((id, policy) -> {
            policy.input().item().ifPresent(item -> {
                if (!itemExists.test(item)) throw new IllegalArgumentException("Research Data " + id + " references missing input " + item);
            });
            validateCost("Research Data " + id, policy.cost(), itemExists);
        });
        for (var entry : snapshot.profiles().entrySet()) {
            entry.getValue().analyzer().stream().flatMap(BlueprintAnalyzerPolicy::costs).forEach(cost ->
                    validateCost("Analyzer profile " + entry.getKey(), cost, itemExists));
            validateCost("research profile " + entry.getKey(), entry.getValue().researchCost(), itemExists);
            validateCost(
                    "reverse-engineering profile " + entry.getKey(),
                    entry.getValue().reverseEngineering().cost(),
                    itemExists);
        }
        for (var entry : snapshot.rules().entrySet()) {
            entry.getValue().analyzer().stream().flatMap(BlueprintAnalyzerOverride::costs).forEach(cost ->
                    validateCost("Analyzer rule " + entry.getKey(), cost, itemExists));
            entry.getValue().researchCost().ifPresent(cost ->
                    validateCost("research rule " + entry.getKey(), cost, itemExists));
            entry.getValue().reverseEngineering()
                    .flatMap(BlueprintReverseEngineeringOverride::cost)
                    .ifPresent(cost -> validateCost(
                            "reverse-engineering rule " + entry.getKey(),
                            cost,
                            itemExists));
        }
    }

    static Set<ResourceLocation> unresolvedTags(
            BlueprintResearchSnapshot snapshot,
            Predicate<ResourceLocation> tagExists) {
        if (snapshot == null || tagExists == null) {
            throw new IllegalArgumentException("snapshot and item tag predicate cannot be null");
        }
        LinkedHashSet<ResourceLocation> unresolved = new LinkedHashSet<>();
        snapshot.profiles().values().forEach(profile ->
                collectUnresolvedTags(profile.researchCost(), tagExists, unresolved));
        snapshot.profiles().values().forEach(profile ->
                collectUnresolvedTags(
                        profile.reverseEngineering().cost(),
                        tagExists,
                        unresolved));
        snapshot.rules().values().forEach(rule -> rule.researchCost().ifPresent(cost ->
                collectUnresolvedTags(cost, tagExists, unresolved)));
        snapshot.rules().values().forEach(rule -> rule.reverseEngineering()
                .flatMap(BlueprintReverseEngineeringOverride::cost)
                .ifPresent(cost -> collectUnresolvedTags(cost, tagExists, unresolved)));
        snapshot.profiles().values().stream().flatMap(p -> p.analyzer().stream()).flatMap(BlueprintAnalyzerPolicy::costs)
                .forEach(cost -> collectUnresolvedTags(cost, tagExists, unresolved));
        snapshot.rules().values().stream().flatMap(r -> r.analyzer().stream()).flatMap(BlueprintAnalyzerOverride::costs)
                .forEach(cost -> collectUnresolvedTags(cost, tagExists, unresolved));
        snapshot.researchData().policies().values().forEach(policy -> {
            collectUnresolvedTags(policy.cost(), tagExists, unresolved);
            policy.input().tag().filter(id -> !tagExists.test(id)).ifPresent(unresolved::add);
        });
        return Set.copyOf(unresolved);
    }

    private static void validateCost(
            String owner,
            BlueprintResearchCost cost,
            Predicate<ResourceLocation> itemExists) {
        for (BlueprintResearchIngredient ingredient : cost.ingredients()) {
            for (ResourceLocation itemId : ingredient.items()) {
                if (!itemExists.test(itemId)) {
                    throw new IllegalArgumentException(owner + " references missing item " + itemId);
                }
            }
        }
    }

    private static void collectUnresolvedTags(
            BlueprintResearchCost cost,
            Predicate<ResourceLocation> tagExists,
            Set<ResourceLocation> unresolved) {
        cost.ingredients().forEach(ingredient -> ingredient.tag()
                .filter(tagId -> !tagExists.test(tagId))
                .ifPresent(unresolved::add));
    }
}
