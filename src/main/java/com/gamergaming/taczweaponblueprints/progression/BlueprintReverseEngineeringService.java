package com.gamergaming.taczweaponblueprints.progression;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.BiPredicate;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintReverseEngineeringPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Atomic server authority for physical TaCZ item to blueprint conversion. */
public final class BlueprintReverseEngineeringService {
    private BlueprintReverseEngineeringService() {
    }

    /** Builds a mutation-free preview from current server state. */
    public static Evaluation evaluate(
            ServerPlayer player,
            WorkstationTransaction transaction) {
        if (player == null || !player.isAlive() || transaction == null) {
            return Evaluation.failure(Status.INVALID_PLAYER, 0, 0, true);
        }
        Optional<IPlayerRecipeData> resolvedData =
                player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (resolvedData.isEmpty()) {
            return Evaluation.failure(
                    Status.PLAYER_DATA_UNAVAILABLE,
                    0,
                    ModConfigs.BLUEPRINT.progressionSnapshot().pointCap(),
                    transaction.outputStack().isEmpty());
        }
        var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        return evaluate(
                transaction.physicalInput(),
                transaction.inventoryStacks(),
                transaction.outputStack().isEmpty(),
                BlueprintResearchDataManager.INSTANCE.snapshot(),
                BlueprintDataManager.SERVER.getBlueprintDataMap(),
                config.activeProfileId(),
                resolvedData.orElseThrow(),
                ModConfigs.BLUEPRINT::isItemBlacklisted,
                BlueprintProgressionAccess::isProgressionExempt,
                config.blueprintsEnabled(),
                config.pointCap(),
                null);
    }

    /**
     * Re-evaluates current input, policy, costs, output, and progression before
     * applying one rollback-safe transaction.
     */
    public static Result reverseEngineer(
            ServerPlayer player,
            WorkstationTransaction transaction) {
        if (player == null || !player.isAlive() || transaction == null) {
            return Result.failure(Status.INVALID_PLAYER, Optional.empty(), 0);
        }
        Optional<IPlayerRecipeData> resolvedData =
                player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (resolvedData.isEmpty()) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, Optional.empty(), 0);
        }
        IPlayerRecipeData playerData = resolvedData.orElseThrow();
        Evaluation evaluation = evaluate(player, transaction);
        Result result = commit(evaluation, playerData, transaction);
        if (!result.successful()) {
            return result;
        }

        ResourceLocation blueprintId = result.blueprintId().orElseThrow();
        ResearchPointAwardDispatcher.blueprintTransitions(
                player, playerData, blueprintId, result.discoveredChanged(), false);
        Result finalResult = result.withBalance(playerData.getResearchPoints());
        try {
            if (result.discoveredChanged()) {
                NetworkHandler.syncPlayerRecipeData(player);
            } else {
                NetworkHandler.syncPlayerPointBalance(player);
            }
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Reverse engineering committed for {}, but immediate progression sync failed; scheduling retry",
                    blueprintId,
                    exception);
            if (result.discoveredChanged()) {
                BlueprintProgressionSyncScheduler.markKnowledgeDirty(player);
            } else {
                BlueprintProgressionSyncScheduler.markDirty(player);
            }
        }
        return finalResult;
    }

    static Evaluation evaluate(
            ItemStack physicalInput,
            List<ItemStack> inventory,
            boolean outputAvailable,
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId,
            IPlayerRecipeData playerData,
            Predicate<String> blockedPredicate,
            Predicate<ResourceLocation> progressionExemptPredicate,
            boolean blueprintsEnabled,
            int pointCap,
            PhysicalItemBlueprintResolver.IdentityAdapter identityAdapter) {
        int balance = playerData == null ? 0 : playerData.getResearchPoints();
        BlueprintReverseEngineeringEvaluator.Evaluation base = identityAdapter == null
                ? BlueprintReverseEngineeringEvaluator.evaluate(
                        physicalInput,
                        snapshot,
                        catalog,
                        profileId,
                        playerData,
                        blockedPredicate,
                        progressionExemptPredicate)
                : BlueprintReverseEngineeringEvaluator.evaluate(
                        physicalInput,
                        snapshot,
                        catalog,
                        profileId,
                        playerData,
                        blockedPredicate,
                        progressionExemptPredicate,
                        identityAdapter);
        Status baseStatus = map(base.status());
        BlueprintResearchCost cost = base.reversePolicy()
                .map(BlueprintReverseEngineeringPolicy::cost)
                .orElseGet(() -> new BlueprintResearchCost(0, List.of()));
        Optional<ResearchIngredientPlanner.Allocation> allocation =
                ResearchIngredientPlanner.allocation(
                        copyStacks(inventory), cost);
        boolean ingredientsSatisfied = allocation.filter(
                ResearchIngredientPlanner.Allocation::complete).isPresent();

        Status status = baseStatus;
        if (baseStatus == Status.READY) {
            ResourceLocation blueprintId = base.physical().blueprintId().orElseThrow();
            if (!blueprintsEnabled) {
                status = Status.BLUEPRINTS_DISABLED;
            } else if (!outputAvailable) {
                status = Status.OUTPUT_OCCUPIED;
            } else if (!playerData.hasDiscoveredBlueprint(blueprintId.toString())
                    && playerData.getDiscoveredBlueprints().size()
                            >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
                status = Status.PROGRESSION_CAPACITY_EXHAUSTED;
            } else if (cost.points() > pointCap) {
                status = Status.POLICY_INELIGIBLE;
            } else if (balance < cost.points()) {
                status = Status.POINTS_REQUIRED;
            } else if (!ingredientsSatisfied) {
                status = Status.INGREDIENTS_REQUIRED;
            }
        }
        return new Evaluation(
                status,
                base,
                cost,
                Math.max(0, balance),
                Math.max(0, pointCap),
                ingredientsSatisfied,
                outputAvailable,
                allocation);
    }

    static Result commit(
            Evaluation evaluation,
            IPlayerRecipeData playerData,
            WorkstationTransaction transaction) {
        return commit(
                evaluation,
                playerData,
                transaction,
                (output, blueprintId) -> !output.isEmpty()
                        && output.getCount() == 1
                        && BlueprintItem.getBlueprintId(output)
                                .filter(blueprintId::equals).isPresent()
                        && BlueprintItem.getProvenance(output).isPresent());
    }

    static Result commit(
            Evaluation evaluation,
            IPlayerRecipeData playerData,
            WorkstationTransaction transaction,
            BiPredicate<ItemStack, ResourceLocation> outputValidator) {
        int balance = playerData == null ? 0 : playerData.getResearchPoints();
        Optional<ResourceLocation> target = evaluation == null
                ? Optional.empty()
                : evaluation.blueprintId();
        if (evaluation == null || playerData == null || transaction == null
                || outputValidator == null) {
            return Result.failure(Status.INVALID_PLAYER, target, balance);
        }
        if (!evaluation.ready()) {
            return Result.failure(evaluation.status(), target, balance);
        }

        ResourceLocation blueprintId = target.orElseThrow();
        BlueprintReverseEngineeringPolicy policy = evaluation.base()
                .reversePolicy().orElseThrow();
        ItemStack originalInput;
        List<ItemStack> originalInventory;
        ItemStack originalOutput;
        int originalPoints;
        boolean discoveredChanged;
        try {
            originalInput = transaction.physicalInput().copy();
            originalInventory = copyStacks(transaction.inventoryStacks());
            originalOutput = transaction.outputStack().copy();
            originalPoints = playerData.getResearchPoints();
            discoveredChanged = !playerData.hasDiscoveredBlueprint(blueprintId.toString());
        } catch (RuntimeException exception) {
            return Result.failure(Status.TRANSACTION_FAILED, target, balance);
        }
        Optional<ResearchIngredientPlanner.Plan> resolvedMaterialPlan;
        try {
            resolvedMaterialPlan = ResearchIngredientPlanner.plan(
                    originalInventory, evaluation.cost());
        } catch (RuntimeException exception) {
            return Result.failure(Status.TRANSACTION_FAILED, target, balance);
        }
        if (resolvedMaterialPlan.isEmpty()) {
            return Result.failure(Status.STALE_INPUT, target, balance);
        }
        ResearchIngredientPlanner.Plan materialPlan = resolvedMaterialPlan.orElseThrow();

        ItemStack output;
        try {
            output = transaction.createOutput(
                    blueprintId,
                    BlueprintProvenance.reverseEngineered(
                            policy.outputRecyclable(),
                            policy.physicalBlueprintLearningMode()));
            if (!outputValidator.test(output, blueprintId)) {
                return Result.failure(Status.TRANSACTION_FAILED, target, originalPoints);
            }
        } catch (RuntimeException exception) {
            return Result.failure(Status.TRANSACTION_FAILED, target, originalPoints);
        }

        int pointsSpent = evaluation.cost().points();
        if (pointsSpent > 0 && !playerData.spendResearchPoints(pointsSpent)) {
            return Result.failure(Status.STALE_POLICY, target, playerData.getResearchPoints());
        }

        try {
            if (!transaction.consumeMaterials(materialPlan, originalInventory)
                    || !transaction.consumePhysical(
                            originalInput, evaluation.requiredInputCount())
                    || !transaction.placeOutput(output, originalOutput)) {
                return rollbackFailure(
                        transaction,
                        playerData,
                        originalInput,
                        originalInventory,
                        originalOutput,
                        originalPoints,
                        target,
                        Status.STALE_INPUT);
            }
        } catch (RuntimeException exception) {
            return rollbackFailure(
                    transaction,
                    playerData,
                    originalInput,
                    originalInventory,
                    originalOutput,
                    originalPoints,
                    target,
                    Status.TRANSACTION_FAILED);
        }

        if (discoveredChanged && !playerData.discoverBlueprint(blueprintId.toString())) {
            return rollbackFailure(
                    transaction,
                    playerData,
                    originalInput,
                    originalInventory,
                    originalOutput,
                    originalPoints,
                    target,
                    Status.TRANSACTION_FAILED);
        }
        return new Result(
                Status.SUCCESS,
                target,
                evaluation.requiredInputCount(),
                pointsSpent,
                playerData.getResearchPoints(),
                discoveredChanged);
    }

    private static Result rollbackFailure(
            WorkstationTransaction transaction,
            IPlayerRecipeData playerData,
            ItemStack originalInput,
            List<ItemStack> originalInventory,
            ItemStack originalOutput,
            int originalPoints,
            Optional<ResourceLocation> target,
            Status failure) {
        boolean restored = true;
        try {
            restored = transaction.restore(originalInput, originalInventory, originalOutput);
        } catch (RuntimeException exception) {
            restored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Reverse-engineering transaction inventory rollback threw",
                    exception);
        }
        try {
            restored &= playerData.setResearchPoints(originalPoints);
        } catch (RuntimeException exception) {
            restored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Reverse-engineering transaction RP rollback threw",
                    exception);
        }
        if (!restored) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Reverse-engineering transaction could not restore all server-owned state for {}",
                    target.orElse(null));
        }
        return Result.failure(
                restored ? failure : Status.ROLLBACK_FAILED,
                target,
                playerData.getResearchPoints());
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        if (stacks == null || stacks.stream().anyMatch(java.util.Objects::isNull)) {
            return List.of();
        }
        return stacks.stream().map(ItemStack::copy).toList();
    }

    private static Status map(BlueprintReverseEngineeringEvaluator.Status status) {
        return switch (status) {
            case EMPTY_INPUT -> Status.EMPTY_INPUT;
            case UNSUPPORTED_ITEM -> Status.UNSUPPORTED_ITEM;
            case INVALID_ITEM_DATA -> Status.INVALID_ITEM_DATA;
            case MISSING_LOGICAL_ID -> Status.MISSING_LOGICAL_ID;
            case CONTENT_UNAVAILABLE -> Status.CONTENT_UNAVAILABLE;
            case CATALOG_KIND_MISMATCH -> Status.CATALOG_KIND_MISMATCH;
            case BLOCKED -> Status.BLOCKED;
            case PROGRESSION_EXEMPT -> Status.PROGRESSION_EXEMPT;
            case REVERSE_ENGINEERING_DISABLED -> Status.REVERSE_ENGINEERING_DISABLED;
            case PLAYER_DATA_UNAVAILABLE -> Status.PLAYER_DATA_UNAVAILABLE;
            case ALREADY_KNOWN -> Status.ALREADY_KNOWN;
            case LOADED_GUN -> Status.LOADED_GUN;
            case GUN_HAS_ATTACHMENTS -> Status.GUN_HAS_ATTACHMENTS;
            case MODIFIED_ITEM_NOT_ALLOWED -> Status.MODIFIED_ITEM_NOT_ALLOWED;
            case INSUFFICIENT_INPUT_COUNT -> Status.INSUFFICIENT_INPUT_COUNT;
            case READY -> Status.READY;
        };
    }

    public enum Status {
        EMPTY_INPUT,
        UNSUPPORTED_ITEM,
        INVALID_ITEM_DATA,
        MISSING_LOGICAL_ID,
        CONTENT_UNAVAILABLE,
        CATALOG_KIND_MISMATCH,
        INVALID_PLAYER,
        PLAYER_DATA_UNAVAILABLE,
        BLUEPRINTS_DISABLED,
        BLOCKED,
        PROGRESSION_EXEMPT,
        REVERSE_ENGINEERING_DISABLED,
        ALREADY_KNOWN,
        LOADED_GUN,
        GUN_HAS_ATTACHMENTS,
        MODIFIED_ITEM_NOT_ALLOWED,
        INSUFFICIENT_INPUT_COUNT,
        PROGRESSION_CAPACITY_EXHAUSTED,
        POLICY_INELIGIBLE,
        POINTS_REQUIRED,
        INGREDIENTS_REQUIRED,
        OUTPUT_OCCUPIED,
        READY,
        SUCCESS,
        STALE_INPUT,
        STALE_POLICY,
        TRANSACTION_FAILED,
        ROLLBACK_FAILED
    }

    public record Evaluation(
            Status status,
            BlueprintReverseEngineeringEvaluator.Evaluation base,
            BlueprintResearchCost cost,
            int pointBalance,
            int pointCap,
            boolean ingredientsSatisfied,
            boolean outputAvailable,
            Optional<ResearchIngredientPlanner.Allocation> allocation) {
        public Evaluation {
            if (status == null || base == null || cost == null
                    || pointBalance < 0 || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointCap < 0 || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid reverse-engineering evaluation");
            }
            allocation = allocation == null ? Optional.empty() : allocation;
        }

        public static Evaluation failure(
                Status status,
                int pointBalance,
                int pointCap,
                boolean outputAvailable) {
            PhysicalItemBlueprintResolver.Resolution physical =
                    PhysicalItemBlueprintResolver.Resolution.failure(
                            PhysicalItemBlueprintResolver.Status.EMPTY_INPUT);
            return new Evaluation(
                    status,
                    BlueprintReverseEngineeringEvaluator.Evaluation.physicalFailure(
                            BlueprintReverseEngineeringEvaluator.Status.EMPTY_INPUT,
                            physical),
                    new BlueprintResearchCost(0, List.of()),
                    Math.max(0, pointBalance),
                    Math.max(0, pointCap),
                    true,
                    outputAvailable,
                    Optional.empty());
        }

        public Optional<ResourceLocation> blueprintId() {
            return base.physical().blueprintId();
        }

        public int requiredInputCount() {
            return base.requiredInputCount();
        }

        public int physicalInputCount() {
            return base.physical().stackCount();
        }

        public boolean customizationWillBeLost() {
            return base.physical().modified();
        }

        /** Whether the target was learned before this transaction was evaluated. */
        public boolean alreadyKnown() {
            return base.researchPolicy()
                    .map(com.gamergaming.taczweaponblueprints.resource.research
                            .BlueprintResearchPolicy::learned)
                    .orElse(false);
        }

        public boolean ready() {
            return status == Status.READY;
        }
    }

    public record Result(
            Status status,
            Optional<ResourceLocation> blueprintId,
            int consumedPhysicalItems,
            int spentPoints,
            int newBalance,
            boolean discoveredChanged) {
        public Result {
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            if (status == null
                    || consumedPhysicalItems < 0
                    || consumedPhysicalItems > BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT
                    || spentPoints < 0 || spentPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || newBalance < 0 || newBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid reverse-engineering result");
            }
            if (status == Status.SUCCESS) {
                if (blueprintId.isEmpty() || consumedPhysicalItems < 1) {
                    throw new IllegalArgumentException(
                            "successful reverse engineering is missing its output identity");
                }
            } else if (consumedPhysicalItems != 0 || spentPoints != 0 || discoveredChanged) {
                throw new IllegalArgumentException(
                        "failed reverse engineering cannot retain committed mutations");
            }
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        Result withBalance(int balance) {
            return new Result(
                    status,
                    blueprintId,
                    consumedPhysicalItems,
                    spentPoints,
                    Math.max(0, Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, balance)),
                    discoveredChanged);
        }

        static Result failure(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int currentBalance) {
            return new Result(
                    status,
                    blueprintId,
                    0,
                    0,
                    Math.max(0, Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, currentBalance)),
                    false);
        }
    }

    /** Menu-owned physical input, player materials, and extract-only output. */
    public interface WorkstationTransaction {
        ItemStack physicalInput();

        ItemStack outputStack();

        List<ItemStack> inventoryStacks();

        ItemStack createOutput(
                ResourceLocation blueprintId,
                BlueprintProvenance provenance);

        boolean consumeMaterials(
                ResearchIngredientPlanner.Plan plan,
                List<ItemStack> expectedInventory);

        boolean consumePhysical(ItemStack expectedInput, int count);

        boolean placeOutput(ItemStack output, ItemStack expectedOutput);

        boolean restore(
                ItemStack physicalInput,
                List<ItemStack> inventory,
                ItemStack output);
    }
}
