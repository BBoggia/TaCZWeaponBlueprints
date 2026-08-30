package com.gamergaming.taczweaponblueprints.menu;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintReverseEngineeringPolicy;

import net.minecraft.resources.ResourceLocation;

/** Server-authored decision for the Analyzer input and extract-only output. */
public record BlueprintRecyclerPreview(
        InputKind inputKind,
        Optional<ResourceLocation> inputId,
        int inputCount,
        int pointValue,
        int pointBalance,
        int pointCap,
        Optional<BlueprintRecyclingService.Status> recyclingStatus,
        Optional<ResearchDataRedemptionService.Status> researchDataStatus,
        long stateToken,
        Optional<ResourceLocation> outputBlueprintId,
        int requiredInputCount,
        int pointCost,
        boolean ingredientsSatisfied,
        boolean outputAvailable,
        boolean customizationWillBeLost,
        boolean alreadyKnown,
        Optional<BlueprintReverseEngineeringService.Status> reverseEngineeringStatus,
        List<IngredientPreview> ingredients) {
    public static final BlueprintRecyclerPreview EMPTY = empty(0, 0);

    public BlueprintRecyclerPreview {
        inputId = inputId == null ? Optional.empty() : inputId;
        recyclingStatus = recyclingStatus == null ? Optional.empty() : recyclingStatus;
        researchDataStatus = researchDataStatus == null ? Optional.empty() : researchDataStatus;
        outputBlueprintId = outputBlueprintId == null ? Optional.empty() : outputBlueprintId;
        reverseEngineeringStatus = reverseEngineeringStatus == null
                ? Optional.empty()
                : reverseEngineeringStatus;
        ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
        if (inputKind == null || inputCount < 0
                || inputCount > PlayerProgressionLimits.MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION
                || pointValue < 0 || pointValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointBalance < 0 || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointCap < 0 || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || stateToken < 0L
                || requiredInputCount < 0
                || requiredInputCount > BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT
                || pointCost < 0 || pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || ingredients.size() > com.gamergaming.taczweaponblueprints.resource.research
                        .BlueprintResearchCost.MAX_INGREDIENT_TYPES
                || ingredients.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid Blueprint Recycler preview");
        }
        inputId.ifPresent(BlueprintRecyclerPreview::validateId);
        outputBlueprintId.ifPresent(BlueprintRecyclerPreview::validateId);
        switch (inputKind) {
            case EMPTY -> {
                if (inputId.isPresent() || inputCount != 0 || pointValue != 0
                        || recyclingStatus.isPresent() || researchDataStatus.isPresent()
                        || reverseEngineeringStatus.isPresent() || outputBlueprintId.isPresent()
                        || requiredInputCount != 0 || pointCost != 0 || !ingredients.isEmpty()
                        || customizationWillBeLost || alreadyKnown) {
                    throw new IllegalArgumentException("empty Recycler preview contains input details");
                }
            }
            case INVALID -> {
                if (inputCount < 1 || pointValue != 0
                        || recyclingStatus.isPresent() || researchDataStatus.isPresent()
                        || reverseEngineeringStatus.isPresent() || outputBlueprintId.isPresent()
                        || requiredInputCount != 0 || pointCost != 0 || !ingredients.isEmpty()
                        || customizationWillBeLost || alreadyKnown) {
                    throw new IllegalArgumentException("invalid Recycler input has an action decision");
                }
            }
            case BLUEPRINT -> {
                if (inputId.isEmpty() || inputCount < 1
                        || recyclingStatus.isEmpty() || researchDataStatus.isPresent()
                        || reverseEngineeringStatus.isPresent() || outputBlueprintId.isPresent()
                        || requiredInputCount != 0 || pointCost != 0 || !ingredients.isEmpty()
                        || customizationWillBeLost || alreadyKnown) {
                    throw new IllegalArgumentException("blueprint Recycler preview is incomplete");
                }
                if (recyclingStatus.orElseThrow() == BlueprintRecyclingService.Status.SUCCESS
                        && (pointValue <= 0
                                || pointValue > pointCap - Math.min(pointBalance, pointCap))) {
                    throw new IllegalArgumentException("ready blueprint preview is not affordable");
                }
            }
            case RESEARCH_DATA -> {
                if (inputId.isEmpty() || inputCount < 1
                        || recyclingStatus.isPresent() || researchDataStatus.isEmpty()
                        || reverseEngineeringStatus.isPresent() || outputBlueprintId.isPresent()
                        || requiredInputCount != 0 || pointCost != 0 || !ingredients.isEmpty()
                        || customizationWillBeLost || alreadyKnown) {
                    throw new IllegalArgumentException("Research Data Recycler preview is incomplete");
                }
                if (researchDataStatus.orElseThrow() == ResearchDataRedemptionService.Status.SUCCESS
                        && pointValue <= 0) {
                    throw new IllegalArgumentException("ready Research Data preview has no value");
                }
            }
            case PHYSICAL_ITEM -> {
                if (inputId.isEmpty() || inputCount < 1
                        || recyclingStatus.isPresent() || researchDataStatus.isPresent()
                        || reverseEngineeringStatus.isEmpty()
                        || outputBlueprintId.filter(inputId.orElseThrow()::equals).isEmpty()
                        || pointValue != 0) {
                    throw new IllegalArgumentException(
                            "physical-item Analyzer preview is incomplete");
                }
                if (reverseEngineeringStatus.orElseThrow()
                                == BlueprintReverseEngineeringService.Status.READY
                        && (!ingredientsSatisfied || !outputAvailable
                                || inputCount < requiredInputCount
                                || requiredInputCount < 1
                                || pointBalance < pointCost)) {
                    throw new IllegalArgumentException(
                            "ready physical-item preview cannot satisfy its transaction");
                }
                if (reverseEngineeringStatus.orElseThrow()
                                == BlueprintReverseEngineeringService.Status.ALREADY_KNOWN
                        && !alreadyKnown) {
                    throw new IllegalArgumentException(
                            "already-known Analyzer preview is missing its knowledge marker");
                }
            }
        }
    }

    /** Compatibility constructor for the pre-Analyzer Recycler decisions. */
    public BlueprintRecyclerPreview(
            InputKind inputKind,
            Optional<ResourceLocation> inputId,
            int inputCount,
            int pointValue,
            int pointBalance,
            int pointCap,
            Optional<BlueprintRecyclingService.Status> recyclingStatus,
            Optional<ResearchDataRedemptionService.Status> researchDataStatus) {
        this(
                inputKind,
                inputId,
                inputCount,
                pointValue,
                pointBalance,
                pointCap,
                recyclingStatus,
                researchDataStatus,
                0L,
                Optional.empty(),
                0,
                0,
                true,
                true,
                false,
                false,
                Optional.empty(),
                List.of());
    }

    public static BlueprintRecyclerPreview empty(int pointBalance, int pointCap) {
        return new BlueprintRecyclerPreview(
                InputKind.EMPTY, Optional.empty(), 0, 0, pointBalance, pointCap,
                Optional.empty(), Optional.empty());
    }

    public static BlueprintRecyclerPreview invalid(
            Optional<ResourceLocation> inputId,
            int inputCount,
            int pointBalance,
            int pointCap) {
        return new BlueprintRecyclerPreview(
                InputKind.INVALID, inputId, inputCount, 0, pointBalance, pointCap,
                Optional.empty(), Optional.empty());
    }

    public boolean actionable() {
        return recyclingStatus.filter(status -> status == BlueprintRecyclingService.Status.SUCCESS)
                .isPresent()
                || researchDataStatus.filter(status ->
                        status == ResearchDataRedemptionService.Status.SUCCESS).isPresent()
                || reverseEngineeringStatus.filter(status ->
                        status == BlueprintReverseEngineeringService.Status.READY).isPresent();
    }

    public BlueprintRecyclerPreview withStateToken(long token) {
        return new BlueprintRecyclerPreview(
                inputKind,
                inputId,
                inputCount,
                pointValue,
                pointBalance,
                pointCap,
                recyclingStatus,
                researchDataStatus,
                token,
                outputBlueprintId,
                requiredInputCount,
                pointCost,
                ingredientsSatisfied,
                outputAvailable,
                customizationWillBeLost,
                alreadyKnown,
                reverseEngineeringStatus,
                ingredients);
    }

    private static void validateId(ResourceLocation id) {
        if (id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("invalid input ID in Blueprint Recycler preview");
        }
    }

    public enum InputKind {
        EMPTY,
        INVALID,
        BLUEPRINT,
        RESEARCH_DATA,
        PHYSICAL_ITEM
    }

    public record IngredientPreview(
            List<ResourceLocation> items,
            Optional<ResourceLocation> tag,
            int required,
            int inventoryAvailable) {
        public IngredientPreview {
            items = items == null ? List.of() : List.copyOf(items);
            tag = tag == null ? Optional.empty() : tag;
            if (items.size() > BlueprintResearchIngredient.MAX_ITEMS
                    || items.stream().anyMatch(java.util.Objects::isNull)
                    || required < 1
                    || required > BlueprintResearchIngredient.MAX_COUNT
                    || inventoryAvailable < 0
                    || inventoryAvailable > PlayerProgressionLimits.MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION
                    || items.isEmpty() && tag.isEmpty()) {
                throw new IllegalArgumentException(
                        "invalid reverse-engineering ingredient preview");
            }
            items.forEach(BlueprintRecyclerPreview::validateId);
            tag.ifPresent(BlueprintRecyclerPreview::validateId);
        }
    }
}
