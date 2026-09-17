package com.gamergaming.taczweaponblueprints.menu;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteEvaluationService;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFailureReporter;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

import net.minecraft.resources.ResourceLocation;

/** Builds the bounded selection packet without letting presentation failures escape the server tick. */
final class ResearchSelectionPreviewFactory {
    private ResearchSelectionPreviewFactory() { }

    static ResearchSelectionPreview create(
            ResearchRouteEvaluationService.Evaluation evaluation,
            ResearchCostMode costMode,
            Function<ResourceLocation, List<ResourceLocation>> tagItems,
            Supplier<ResearchSelectionProgressionPreview> progression) {
        try {
            var requirements = evaluation.requirements();
            List<ResearchSelectionPreview.IngredientPreview> ingredients = new ArrayList<>();
            for (int index = 0; index < Math.min(
                    requirements.size(), BlueprintResearchCost.MAX_INGREDIENT_TYPES); index++) {
                var requirement = requirements.get(index);
                List<ResourceLocation> items = requirement.items();
                if (items.isEmpty() && requirement.tag().isPresent()) {
                    items = tagItems.apply(requirement.tag().orElseThrow()).stream()
                            .filter(java.util.Objects::nonNull)
                            .limit(BlueprintResearchIngredient.MAX_ITEMS)
                            .toList();
                }
                ingredients.add(new ResearchSelectionPreview.IngredientPreview(
                        items, requirement.tag(), requirement.count(),
                        evaluation.allocation().allocatedForIngredient(index)));
            }
            return new ResearchSelectionPreview(
                    Optional.of(evaluation.targetId()), evaluation.pointCost(), evaluation.pointBalance(),
                    evaluation.policyEligible(), evaluation.ingredientsSatisfied(),
                    evaluation.transactionCapacityAvailable(), evaluation.ready(), evaluation.costBypassed(),
                    ingredients, evaluation.unlockCount(), requirements.size(),
                    ResearchSelectionPreview.PathPlanningState.fromStatus(evaluation.planningStatus()),
                    costMode, evaluation.routeFingerprint(), evaluation.accessSummary(), progression.get());
        } catch (RuntimeException exception) {
            ResearchRouteFailureReporter.report("Bench selection preview construction", exception);
            return unavailable(evaluation == null ? null : evaluation.targetId(), costMode);
        }
    }

    static ResearchSelectionPreview unavailable(ResourceLocation target, ResearchCostMode costMode) {
        if (target == null || target.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            return ResearchSelectionPreview.EMPTY;
        }
        return new ResearchSelectionPreview(
                Optional.of(target), 0, 0, false, true, false, false, false,
                List.of(), 1, 0, ResearchSelectionPreview.PathPlanningState.TECH_TREE_UNAVAILABLE,
                costMode, Optional.empty());
    }
}
