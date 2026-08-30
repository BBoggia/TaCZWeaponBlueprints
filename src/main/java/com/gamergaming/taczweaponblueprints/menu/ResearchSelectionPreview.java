package com.gamergaming.taczweaponblueprints.menu;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

import net.minecraft.resources.ResourceLocation;

/** Bounded research-only selection state, independent of workstation turn-ins. */
public record ResearchSelectionPreview(
        Optional<ResourceLocation> blueprintId,
        int pointCost,
        int pointBalance,
        boolean policyEligible,
        boolean ingredientsSatisfied,
        boolean outputSpace,
        boolean researchable,
        boolean creativeBypass,
        List<IngredientPreview> ingredients) {
    public static final ResearchSelectionPreview EMPTY = new ResearchSelectionPreview(
            Optional.empty(), 0, 0, false, false, false, false, false, List.of());

    public ResearchSelectionPreview {
        blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        if (pointCost < 0 || pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointBalance < 0 || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || ingredients.size() > BlueprintResearchCost.MAX_INGREDIENT_TYPES) {
            throw new IllegalArgumentException("invalid research selection preview");
        }
        blueprintId.ifPresent(id -> validateId(id, "blueprint"));
        if (blueprintId.isEmpty() && (pointCost != 0 || policyEligible || ingredientsSatisfied
                || outputSpace || researchable || creativeBypass || !ingredients.isEmpty())) {
            throw new IllegalArgumentException("empty research selection contains policy details");
        }
        boolean materialsComplete = ingredients.stream()
                .allMatch(ingredient -> ingredient.inventoryAvailable() >= ingredient.required());
        if (blueprintId.isPresent() && !creativeBypass
                && ingredientsSatisfied != materialsComplete) {
            throw new IllegalArgumentException("research material summary is inconsistent");
        }
        if (researchable && (!policyEligible || !ingredientsSatisfied || !outputSpace)) {
            throw new IllegalArgumentException("ready research selection has an unmet requirement");
        }
        if (researchable && !creativeBypass && pointBalance < pointCost) {
            throw new IllegalArgumentException("ready research selection cannot afford its point cost");
        }
    }

    /**
     * Whether the transaction's non-economic result can still be committed.
     * The protocol-25 field is named {@code outputSpace} for compatibility;
     * direct learning uses it for progression-collection capacity instead.
     */
    public boolean transactionCapacityAvailable() {
        return outputSpace;
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
                throw new IllegalArgumentException("invalid research ingredient preview");
            }
            items.forEach(id -> validateId(id, "ingredient item"));
            tag.ifPresent(id -> validateId(id, "ingredient tag"));
        }
    }

    private static void validateId(ResourceLocation id, String description) {
        if (id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("invalid " + description + " ID in research preview");
        }
    }
}
