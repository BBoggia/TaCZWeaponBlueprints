package com.gamergaming.taczweaponblueprints.menu;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

import net.minecraft.resources.ResourceLocation;

/** Bounded, server-authored research cost shown only in an open bench menu. */
public record ResearchBenchPreview(
        Optional<ResourceLocation> blueprintId,
        int pointCost,
        int pointBalance,
        boolean policyEligible,
        boolean ingredientsSatisfied,
        boolean outputSpace,
        boolean researchable,
        boolean creativeBypass,
        List<IngredientPreview> ingredients,
        RecyclingPreview recycling) {
    public static final ResearchBenchPreview EMPTY =
            new ResearchBenchPreview(
                    Optional.empty(), 0, 0, false, false, false, false, false,
                    List.of(), RecyclingPreview.EMPTY);

    public ResearchBenchPreview {
        blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        recycling = recycling == null ? RecyclingPreview.EMPTY : recycling;
        if (pointCost < 0 || pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointBalance < 0 || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || ingredients.size() > BlueprintResearchCost.MAX_INGREDIENT_TYPES) {
            throw new IllegalArgumentException("invalid Research Bench preview");
        }
        blueprintId.ifPresent(id -> validateId(id, "blueprint"));
        if (blueprintId.isEmpty() && (pointCost != 0 || policyEligible || ingredientsSatisfied
                || outputSpace || researchable || creativeBypass || !ingredients.isEmpty())) {
            throw new IllegalArgumentException("empty Research Bench preview contains policy details");
        }
        boolean materialsComplete = ingredients.stream()
                .allMatch(ingredient -> ingredient.totalAvailable() >= ingredient.required());
        if (blueprintId.isPresent() && !creativeBypass && ingredientsSatisfied != materialsComplete) {
            throw new IllegalArgumentException("Research Bench material summary is inconsistent");
        }
        if (researchable && (!policyEligible || !ingredientsSatisfied || !outputSpace)) {
            throw new IllegalArgumentException("ready Research Bench preview has an unmet requirement");
        }
        if (researchable && !creativeBypass && pointBalance < pointCost) {
            throw new IllegalArgumentException("ready Research Bench preview cannot afford its point cost");
        }
    }

    public ResearchBenchPreview withRecycling(RecyclingPreview nextRecycling) {
        return new ResearchBenchPreview(
                blueprintId,
                pointCost,
                pointBalance,
                policyEligible,
                ingredientsSatisfied,
                outputSpace,
                researchable,
                creativeBypass,
                ingredients,
                nextRecycling);
    }

    public record IngredientPreview(
            List<ResourceLocation> items,
            Optional<ResourceLocation> tag,
            int required,
            int inventoryAvailable) {
        public IngredientPreview {
            items = items == null ? List.of() : List.copyOf(new LinkedHashSet<>(items));
            tag = tag == null ? Optional.empty() : tag;
            if (items.size() > BlueprintResearchIngredient.MAX_ITEMS
                    || (items.isEmpty() && tag.isEmpty())
                    || required < 1
                    || required > BlueprintResearchIngredient.MAX_COUNT
                    || inventoryAvailable < 0
                    || inventoryAvailable > required) {
                throw new IllegalArgumentException("invalid Research Bench ingredient preview");
            }
            items.forEach(id -> validateId(id, "ingredient item"));
            tag.ifPresent(id -> validateId(id, "ingredient tag"));
        }

        public int totalAvailable() {
            return inventoryAvailable;
        }
    }

    /** Exact server-side decision for the blueprint currently in the recycle slot. */
    public record RecyclingPreview(
            Optional<ResourceLocation> blueprintId,
            BlueprintRecyclingService.Status status,
            int pointValue,
            int pointBalance,
            int pointCap) {
        public static final RecyclingPreview EMPTY = new RecyclingPreview(
                Optional.empty(), BlueprintRecyclingService.Status.INVALID_INPUT, 0, 0, 0);

        public RecyclingPreview {
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            if (status == null
                    || pointValue < 0
                    || pointValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointBalance < 0
                    || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointCap < 0
                    || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid Research Bench recycling preview");
            }
            blueprintId.ifPresent(id -> validateId(id, "recycling blueprint"));
            if (status == BlueprintRecyclingService.Status.SUCCESS
                    && (blueprintId.isEmpty() || pointValue <= 0
                    || pointValue > pointCap - Math.min(pointBalance, pointCap))) {
                throw new IllegalArgumentException("ready recycling preview is not affordable");
            }
        }

        public boolean recyclable() {
            return status == BlueprintRecyclingService.Status.SUCCESS;
        }
    }

    private static void validateId(ResourceLocation id, String description) {
        if (id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("invalid " + description + " ID in Research Bench preview");
        }
    }
}
