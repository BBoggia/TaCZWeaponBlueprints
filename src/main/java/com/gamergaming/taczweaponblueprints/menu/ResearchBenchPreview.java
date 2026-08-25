package com.gamergaming.taczweaponblueprints.menu;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
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
        List<IngredientPreview> ingredients) {
    public static final ResearchBenchPreview EMPTY =
            new ResearchBenchPreview(
                    Optional.empty(), 0, 0, false, false, false, false, false, List.of());

    public ResearchBenchPreview {
        blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
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
        if (researchable && (!policyEligible || !ingredientsSatisfied || !outputSpace)) {
            throw new IllegalArgumentException("ready Research Bench preview has an unmet requirement");
        }
        if (researchable && !creativeBypass && pointBalance < pointCost) {
            throw new IllegalArgumentException("ready Research Bench preview cannot afford its point cost");
        }
    }

    public record IngredientPreview(
            List<ResourceLocation> items,
            Optional<ResourceLocation> tag,
            int required,
            int available) {
        public IngredientPreview {
            items = items == null ? List.of() : List.copyOf(new LinkedHashSet<>(items));
            tag = tag == null ? Optional.empty() : tag;
            if (items.size() > BlueprintResearchIngredient.MAX_ITEMS
                    || (items.isEmpty() && tag.isEmpty())
                    || required < 1
                    || required > BlueprintResearchIngredient.MAX_COUNT
                    || available < 0
                    || available > BlueprintResearchIngredient.MAX_COUNT * BlueprintResearchCost.MAX_INGREDIENT_TYPES) {
                throw new IllegalArgumentException("invalid Research Bench ingredient preview");
            }
            items.forEach(id -> validateId(id, "ingredient item"));
            tag.ifPresent(id -> validateId(id, "ingredient tag"));
        }
    }

    private static void validateId(ResourceLocation id, String description) {
        if (id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("invalid " + description + " ID in Research Bench preview");
        }
    }
}
