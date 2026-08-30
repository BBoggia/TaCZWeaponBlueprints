package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
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
                    input.directRequirementCount(),
                    input.immediateUnlockCount(),
                    preview.creativeBypass(),
                    preview.creativeBypass() || preview.pointBalance() >= preview.pointCost(),
                    preview.ingredientsSatisfied(),
                    true,
                    message == Message.READY && preview.researchable());
        }

        ResearchTreeGraph.Node node = input.node();
        return new Presentation(
                publishedMessage(node, input.canAffordPoints()),
                false,
                node.pointCost(),
                0,
                List.of(),
                input.directRequirementCount(),
                input.immediateUnlockCount(),
                false,
                input.canAffordPoints(),
                false,
                false,
                false);
    }

    private static Message authoritativeMessage(
            ResearchTreeGraph.Node node,
            ResearchSelectionPreview preview) {
        Message published = switch (node.availability()) {
            case REDACTED -> Message.FOLLOW_PATH;
            case LEARNED -> Message.LEARNED;
            case DISCOVERY_REQUIRED -> Message.DISCOVERY_REQUIRED;
            case PREREQUISITES_REQUIRED -> Message.PREREQUISITES_REQUIRED;
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
            boolean canAffordPoints) {
        return switch (node.availability()) {
            case REDACTED -> Message.FOLLOW_PATH;
            case PREVIEW -> Message.CHECKING_REQUIREMENTS;
            case LEARNED -> Message.LEARNED;
            case AVAILABLE -> canAffordPoints
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
            int immediateUnlockCount) {
        Input {
            if (node == null || authoritativeSelection == null || preview == null
                    || directRequirementCount < 0 || immediateUnlockCount < 0) {
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
            int directRequirementCount,
            int immediateUnlockCount,
            boolean costBypassed,
            boolean pointsSatisfied,
            boolean materialsSatisfied,
            boolean actionVisible,
            boolean actionEnabled) {
        Presentation {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
            if (message == null || pointCost < 0 || pointBalance < 0
                    || ingredients.stream().anyMatch(java.util.Objects::isNull)
                    || directRequirementCount < 0 || immediateUnlockCount < 0
                    || !exactPreview && (pointBalance != 0 || !ingredients.isEmpty()
                            || costBypassed || materialsSatisfied)
                    || costBypassed && (!exactPreview || !pointsSatisfied || !materialsSatisfied)
                    || actionVisible != exactPreview
                    || actionEnabled && !actionVisible
                    || actionEnabled != (message == Message.READY)) {
                throw new IllegalArgumentException(
                        "invalid Research Tree selected-node presentation");
            }
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
        READY,
        LOCKED,
        DISCOVERY_REQUIRED,
        PREREQUISITES_REQUIRED,
        RESEARCH_DISABLED,
        COST_UNAVAILABLE,
        CONTENT_UNAVAILABLE
    }
}
