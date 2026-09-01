package com.gamergaming.taczweaponblueprints.menu;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.BlueprintResearchService;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
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
        List<IngredientPreview> ingredients,
        int unlockCount,
        int ingredientTypeCount,
        PathPlanningState pathPlanningState,
        ResearchCostMode costMode) {
    public static final ResearchSelectionPreview EMPTY = new ResearchSelectionPreview(
            Optional.empty(), 0, 0, false, false, false, false, false,
            List.of(), 0, 0, PathPlanningState.NONE, ResearchCostMode.POINTS_AND_ITEMS);

    /** Compatibility constructor for the original single-node preview shape. */
    public ResearchSelectionPreview(
            Optional<ResourceLocation> blueprintId,
            int pointCost,
            int pointBalance,
            boolean policyEligible,
            boolean ingredientsSatisfied,
            boolean outputSpace,
            boolean researchable,
            boolean creativeBypass,
            List<IngredientPreview> ingredients) {
        this(
                blueprintId,
                pointCost,
                pointBalance,
                policyEligible,
                ingredientsSatisfied,
                outputSpace,
                researchable,
                creativeBypass,
                ingredients,
                blueprintId == null || blueprintId.isEmpty() ? 0 : 1,
                ingredients == null ? 0 : ingredients.size(),
                PathPlanningState.NONE,
                ResearchCostMode.POINTS_AND_ITEMS);
    }

    /** Compatibility constructor for the protocol-38 path preview shape. */
    public ResearchSelectionPreview(
            Optional<ResourceLocation> blueprintId,
            int pointCost,
            int pointBalance,
            boolean policyEligible,
            boolean ingredientsSatisfied,
            boolean outputSpace,
            boolean researchable,
            boolean creativeBypass,
            List<IngredientPreview> ingredients,
            int unlockCount,
            int ingredientTypeCount) {
        this(
                blueprintId,
                pointCost,
                pointBalance,
                policyEligible,
                ingredientsSatisfied,
                outputSpace,
                researchable,
                creativeBypass,
                ingredients,
                unlockCount,
                ingredientTypeCount,
                PathPlanningState.NONE,
                ResearchCostMode.POINTS_AND_ITEMS);
    }

    /** Compatibility constructor for the protocol-39 path-planning preview shape. */
    public ResearchSelectionPreview(
            Optional<ResourceLocation> blueprintId,
            int pointCost,
            int pointBalance,
            boolean policyEligible,
            boolean ingredientsSatisfied,
            boolean outputSpace,
            boolean researchable,
            boolean creativeBypass,
            List<IngredientPreview> ingredients,
            int unlockCount,
            int ingredientTypeCount,
            PathPlanningState pathPlanningState) {
        this(
                blueprintId,
                pointCost,
                pointBalance,
                policyEligible,
                ingredientsSatisfied,
                outputSpace,
                researchable,
                creativeBypass,
                ingredients,
                unlockCount,
                ingredientTypeCount,
                pathPlanningState,
                ResearchCostMode.POINTS_AND_ITEMS);
    }

    public ResearchSelectionPreview {
        blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        pathPlanningState = pathPlanningState == null
                ? PathPlanningState.NONE
                : pathPlanningState;
        costMode = costMode == null ? ResearchCostMode.POINTS_AND_ITEMS : costMode;
        if (pointCost < 0 || pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointBalance < 0 || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || ingredients.size() > BlueprintResearchCost.MAX_INGREDIENT_TYPES
                || unlockCount < 0
                || unlockCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                || ingredientTypeCount < ingredients.size()
                || ingredientTypeCount > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                        * BlueprintResearchCost.MAX_INGREDIENT_TYPES) {
            throw new IllegalArgumentException("invalid research selection preview");
        }
        if (!costMode.pointsEnabled() && pointCost != 0) {
            throw new IllegalArgumentException("disabled Research Point cost channel is non-zero");
        }
        if (!costMode.itemsEnabled()
                && (!ingredients.isEmpty() || ingredientTypeCount != 0)) {
            throw new IllegalArgumentException("disabled material cost channel is non-empty");
        }
        blueprintId.ifPresent(id -> validateId(id, "blueprint"));
        if (blueprintId.isEmpty() && (pointCost != 0 || policyEligible || ingredientsSatisfied
                || outputSpace || researchable || creativeBypass || !ingredients.isEmpty()
                || unlockCount != 0 || ingredientTypeCount != 0
                || pathPlanningState != PathPlanningState.NONE)) {
            throw new IllegalArgumentException("empty research selection contains policy details");
        }
        if (blueprintId.isPresent() && unlockCount < 1) {
            throw new IllegalArgumentException("selected research preview has no unlock target");
        }
        boolean materialsComplete = ingredients.stream()
                .allMatch(ingredient -> ingredient.inventoryAvailable() >= ingredient.required());
        if (blueprintId.isPresent() && !creativeBypass
                && (ingredientsSatisfied && !materialsComplete
                        || ingredientTypeCount == ingredients.size()
                                && ingredientsSatisfied != materialsComplete)) {
            throw new IllegalArgumentException("research material summary is inconsistent");
        }
        if (researchable && (!policyEligible || !ingredientsSatisfied || !outputSpace)) {
            throw new IllegalArgumentException("ready research selection has an unmet requirement");
        }
        if (researchable && !creativeBypass && pointBalance < pointCost) {
            throw new IllegalArgumentException("ready research selection cannot afford its point cost");
        }
        if (pathPlanningState != PathPlanningState.NONE
                && (blueprintId.isEmpty() || policyEligible || researchable)) {
            throw new IllegalArgumentException("failed path planning preview is inconsistent");
        }
        if (pathPlanningState != PathPlanningState.NONE
                && (pointCost != 0 || creativeBypass || !ingredients.isEmpty()
                        || unlockCount != 1 || ingredientTypeCount != 0)) {
            throw new IllegalArgumentException(
                    "failed path planning preview contains a partial economy");
        }
    }

    /**
     * Whether the transaction's non-economic result can still be committed.
     * The legacy field is named {@code outputSpace} for compatibility;
     * direct learning uses it for progression-collection capacity instead.
     */
    public boolean transactionCapacityAvailable() {
        return outputSpace;
    }

    public boolean pathPurchase() {
        return unlockCount > 1;
    }

    public boolean pointsEnabled() {
        return costMode.pointsEnabled();
    }

    public boolean materialsEnabled() {
        return costMode.itemsEnabled();
    }

    public int additionalIngredientTypes() {
        return ingredientTypeCount - ingredients.size();
    }

    public enum PathPlanningState {
        NONE,
        PATH_TOO_LARGE,
        ROUTE_TOO_COMPLEX;

        public static PathPlanningState fromStatus(BlueprintResearchService.Status status) {
            return status == BlueprintResearchService.Status.PATH_TOO_LARGE
                    ? PATH_TOO_LARGE
                    : status == BlueprintResearchService.Status.ROUTE_TOO_COMPLEX
                            ? ROUTE_TOO_COMPLEX
                            : NONE;
        }
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
                    || required > com.gamergaming.taczweaponblueprints.progression
                            .ResearchIngredientPlanner.MAX_TOTAL_REQUIREMENT_COUNT
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
