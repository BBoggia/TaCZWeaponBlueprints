package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/**
 * Pure disclosure-safe information hierarchy for a selected Research Tree node.
 *
 * <p>The publication can say that a node is worth inspecting, but only a
 * matching server preview may say that it is ready or expose exact inventory
 * requirements.</p>
 */
final class ResearchTreeSelectedNodePresenter {
    private ResearchTreeSelectedNodePresenter() {
    }

    static Presentation present(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("Research Tree selected-node input cannot be null");
        }
        boolean exactPreview = ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                input.node().blueprintId(),
                input.authoritativeSelection(),
                input.preview());
        if (exactPreview) {
            ResearchSelectionPreview preview = input.preview();
            Message message = authoritativeMessage(input.node(), preview);
            return new Presentation(
                    message,
                    true,
                    preview.pointCost(),
                    preview.pointBalance(),
                    preview.ingredients(),
                    preview.unlockCount(),
                    preview.ingredientTypeCount(),
                    input.directRequirementCount(),
                    input.immediateUnlockCount(),
                    preview.creativeBypass(),
                    preview.pathPlanningState() == ResearchSelectionPreview.PathPlanningState.NONE
                            && (preview.creativeBypass()
                                    || preview.pointBalance() >= preview.pointCost()),
                    preview.pathPlanningState() == ResearchSelectionPreview.PathPlanningState.NONE
                            && preview.ingredientsSatisfied(),
                    true,
                    message == Message.READY && preview.researchable(),
                    preview.costMode());
        }

        ResearchTreeGraph.Node node = input.node();
        return new Presentation(
                publishedMessage(
                        node,
                        input.canAffordPoints(),
                        input.publishedCostMode()),
                false,
                node.pointCost(),
                0,
                List.of(),
                0,
                node.visibility().revealsResearchSummary()
                        ? node.ingredientTypeCount()
                        : 0,
                input.directRequirementCount(),
                input.immediateUnlockCount(),
                false,
                !input.publishedCostMode().pointsEnabled()
                        || input.canAffordPoints(),
                false,
                false,
                false,
                input.publishedCostMode());
    }

    private static Message authoritativeMessage(
            ResearchTreeGraph.Node node,
            ResearchSelectionPreview preview) {
        Message pathPlanningFailure = switch (preview.pathPlanningState()) {
            case PATH_TOO_LARGE -> Message.PATH_TOO_LARGE;
            case ROUTE_TOO_COMPLEX -> Message.ROUTE_TOO_COMPLEX;
            case TECH_TREE_UNAVAILABLE -> Message.TECH_TREE_UNAVAILABLE;
            case UNSATISFIABLE -> Message.UNSATISFIABLE;
            case NONE -> null;
        };
        if (pathPlanningFailure != null) {
            return pathPlanningFailure;
        }
        Message accessFailure = switch (preview.accessSummary().kind()) {
            case NONE -> null;
            case POLICY_UNAVAILABLE -> Message.REQUIREMENTS_UNAVAILABLE;
            case WORKBENCH_TIER -> Message.WORKBENCH_TIER_REQUIRED;
            case PROGRESSION_GATE -> Message.PROGRESSION_GATE_REQUIRED;
        };
        if (accessFailure != null) {
            return accessFailure;
        }
        Message published = switch (node.availability()) {
            case REDACTED -> Message.FOLLOW_PATH;
            case LEARNED -> Message.LEARNED;
            case DISCOVERY_REQUIRED -> Message.DISCOVERY_REQUIRED;
            case PREREQUISITES_REQUIRED -> preview.pathPurchase()
                            && preview.policyEligible()
                    ? null
                    : Message.PREREQUISITES_REQUIRED;
            case RESEARCH_DISABLED -> Message.RESEARCH_DISABLED;
            case COST_ABOVE_CAP -> Message.COST_UNAVAILABLE;
            case CONTENT_UNAVAILABLE -> Message.CONTENT_UNAVAILABLE;
            case PREVIEW, AVAILABLE -> null;
        };
        if (published != null) {
            return published;
        }
        if (!preview.policyEligible()) {
            return Message.LOCKED;
        }
        if (!preview.creativeBypass() && preview.pointBalance() < preview.pointCost()) {
            return Message.POINTS_REQUIRED;
        }
        if (!preview.ingredientsSatisfied()) {
            return Message.MATERIALS_REQUIRED;
        }
        if (!preview.transactionCapacityAvailable()) {
            return Message.PROGRESSION_CAPACITY_EXHAUSTED;
        }
        return preview.researchable() ? Message.READY : Message.LOCKED;
    }

    private static Message publishedMessage(
            ResearchTreeGraph.Node node,
            boolean canAffordPoints,
            ResearchCostMode costMode) {
        return switch (node.availability()) {
            case REDACTED -> Message.FOLLOW_PATH;
            case PREVIEW -> Message.CHECKING_REQUIREMENTS;
            case LEARNED -> Message.LEARNED;
            case AVAILABLE -> !costMode.pointsEnabled() || canAffordPoints
                    ? Message.CHECKING_REQUIREMENTS
                    : Message.POINTS_REQUIRED;
            case DISCOVERY_REQUIRED -> Message.DISCOVERY_REQUIRED;
            case PREREQUISITES_REQUIRED -> Message.PREREQUISITES_REQUIRED;
            case RESEARCH_DISABLED -> Message.RESEARCH_DISABLED;
            case COST_ABOVE_CAP -> Message.COST_UNAVAILABLE;
            case CONTENT_UNAVAILABLE -> Message.CONTENT_UNAVAILABLE;
        };
    }

    record Input(
            ResearchTreeGraph.Node node,
            boolean canAffordPoints,
            Optional<ResourceLocation> authoritativeSelection,
            ResearchSelectionPreview preview,
            int directRequirementCount,
            int immediateUnlockCount,
            ResearchCostMode publishedCostMode) {
        Input {
            if (node == null || authoritativeSelection == null || preview == null
                    || directRequirementCount < 0 || immediateUnlockCount < 0
                    || publishedCostMode == null) {
                throw new IllegalArgumentException("invalid Research Tree selected-node input");
            }
        }
    }

    record Presentation(
            Message message,
            boolean exactPreview,
            int pointCost,
            int pointBalance,
            List<ResearchSelectionPreview.IngredientPreview> ingredients,
            int unlockCount,
            int ingredientTypeCount,
            int directRequirementCount,
            int immediateUnlockCount,
            boolean costBypassed,
            boolean pointsSatisfied,
            boolean materialsSatisfied,
            boolean actionVisible,
            boolean actionEnabled,
            ResearchCostMode costMode) {
        Presentation {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
            if (message == null || costMode == null || pointCost < 0 || pointBalance < 0
                    || ingredients.stream().anyMatch(java.util.Objects::isNull)
                    || unlockCount < 0
                    || ingredientTypeCount < ingredients.size()
                    || directRequirementCount < 0 || immediateUnlockCount < 0
                    || !exactPreview && (pointBalance != 0 || !ingredients.isEmpty()
                            || unlockCount != 0
                            || costBypassed || materialsSatisfied)
                    || costBypassed && (!exactPreview || !pointsSatisfied || !materialsSatisfied)
                    || actionVisible != exactPreview
                    || actionEnabled && !actionVisible
                    || actionEnabled != (message == Message.READY)
                    || !costMode.pointsEnabled() && pointCost != 0
                    || !costMode.itemsEnabled()
                            && (!ingredients.isEmpty() || ingredientTypeCount != 0)) {
                throw new IllegalArgumentException(
                        "invalid Research Tree selected-node presentation");
            }
        }

        int additionalIngredientTypes() {
            return ingredientTypeCount - ingredients.size();
        }

        boolean pathPurchase() {
            return unlockCount > 1;
        }

        boolean pathPlanningFailed() {
            return message == Message.PATH_TOO_LARGE
                    || message == Message.ROUTE_TOO_COMPLEX
                    || message == Message.TECH_TREE_UNAVAILABLE
                    || message == Message.UNSATISFIABLE;
        }

        boolean pointsEnabled() {
            return costMode.pointsEnabled();
        }

        boolean materialsEnabled() {
            return costMode.itemsEnabled();
        }
    }

    enum Message {
        FOLLOW_PATH,
        CHECKING_REQUIREMENTS,
        LEARNED,
        POINTS_REQUIRED,
        MATERIALS_REQUIRED,
        INVENTORY_SPACE_REQUIRED,
        PROGRESSION_CAPACITY_EXHAUSTED,
        WORKBENCH_TIER_REQUIRED,
        PROGRESSION_GATE_REQUIRED,
        REQUIREMENTS_UNAVAILABLE,
        READY,
        LOCKED,
        DISCOVERY_REQUIRED,
        PREREQUISITES_REQUIRED,
        RESEARCH_DISABLED,
        COST_UNAVAILABLE,
        CONTENT_UNAVAILABLE,
        PATH_TOO_LARGE,
        ROUTE_TOO_COMPLEX,
        TECH_TREE_UNAVAILABLE,
        UNSATISFIABLE
    }
}
