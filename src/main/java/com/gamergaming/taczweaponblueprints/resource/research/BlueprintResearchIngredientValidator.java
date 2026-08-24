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
        for (var entry : snapshot.profiles().entrySet()) {
            validateCost("research profile " + entry.getKey(), entry.getValue().researchCost(), itemExists);
        }
        for (var entry : snapshot.rules().entrySet()) {
            entry.getValue().researchCost().ifPresent(cost ->
                    validateCost("research rule " + entry.getKey(), cost, itemExists));
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
        snapshot.rules().values().forEach(rule -> rule.researchCost().ifPresent(cost ->
                collectUnresolvedTags(cost, tagExists, unresolved)));
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
