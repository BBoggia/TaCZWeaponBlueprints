package com.gamergaming.taczweaponblueprints.progression;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.LearningTarget;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.Preparation;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService.PreparedLearning;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Atomic server authority for one Research Bench blueprint transaction. */
public final class BlueprintResearchService {
    private BlueprintResearchService() {
    }

    /** Researches directly from the player's main inventory and hotbar. */
    public static Result researchFromInventory(ServerPlayer player, ResourceLocation blueprintId) {
        if (player == null || !player.isAlive() || !validId(blueprintId)) {
            return Result.failure(Status.INVALID_INPUT, Optional.ofNullable(blueprintId), 0);
        }
        return research(player, blueprintId, playerInventoryInput(player));
    }

    private static Result research(
            ServerPlayer player,
            ResourceLocation blueprintId,
            ResearchInput input) {
        Optional<IPlayerRecipeData> resolvedData =
                player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (resolvedData.isEmpty()) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, Optional.of(blueprintId), 0);
        }

        IPlayerRecipeData playerData = resolvedData.orElseThrow();
        BlueprintLearningService.MigrationResult migration =
                BlueprintLearningService.migrateLegacyUnlocksDetailed(
                        BlueprintDataManager.SERVER, playerData);
        BlueprintProgressionConfigSnapshot config =
                ModConfigs.BLUEPRINT.progressionSnapshot();
        TreeResearchResultMode resultMode = config.treeResearchResultMode();
        if (BlueprintProgressionAccess.isProgressionExempt(blueprintId)) {
            return Result.failure(
                    Status.POLICY_INELIGIBLE,
                    Optional.of(blueprintId),
                    playerData.getResearchPoints(),
                    resultMode);
        }
        Result result = research(
                blueprintId,
                playerData,
                id -> BlueprintResearchDataManager.INSTANCE.policyFor(id, playerData),
                id -> BlueprintLearningService.targetFromCatalog(
                        BlueprintDataManager.SERVER, id),
                input,
                player.isCreative(),
                config.blueprintsEnabled(),
                resultMode);
        if (result.successful() && resultMode.learnsDirectly()) {
            ResearchPointAwardDispatcher.blueprintTransitions(
                    player,
                    playerData,
                    blueprintId,
                    result.discoveredChanged(),
                    result.learnedChanged());
        } else if (result.successful()) {
            BlueprintDiscoveryService.discoverInventoryBlueprint(
                    player, BlueprintItem.createBlueprint(blueprintId.toString()));
        }
        if (result.successful() || migration.changed()) {
            syncPostCommitBestEffort(
                    player,
                    resultMode.learnsDirectly() || migration.changed());
        }
        return result;
    }

    private static void syncPostCommitBestEffort(
            ServerPlayer player,
            boolean recipeKnowledgeChanged) {
        try {
            if (recipeKnowledgeChanged) {
                NetworkHandler.syncPlayerRecipeData(player);
            } else {
                NetworkHandler.syncPlayerProgressionData(player);
            }
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research committed for {}, but immediate progression sync failed; scheduling a retry",
                    player == null ? "unknown player" : player.getGameProfile().getName(),
                    exception);
            if (recipeKnowledgeChanged) {
                BlueprintProgressionSyncScheduler.markKnowledgeDirty(player);
            } else {
                BlueprintProgressionSyncScheduler.markDirty(player);
            }
        }
    }

    static Result research(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            ResearchInput input,
            boolean creativePlayer) {
        return research(
                blueprintId,
                playerData,
                policyResolver,
                ignored -> null,
                input,
                creativePlayer,
                true,
                TreeResearchResultMode.CREATE_BLUEPRINT);
    }

    static Result research(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            boolean creativePlayer,
            boolean blueprintsEnabled,
            TreeResearchResultMode resultMode) {
        Optional<ResourceLocation> id = Optional.ofNullable(blueprintId).filter(BlueprintResearchService::validId);
        if (id.isEmpty() || input == null) {
            return Result.failure(
                    Status.INVALID_INPUT,
                    id,
                    playerData == null ? 0 : playerData.getResearchPoints(),
                    resultMode);
        }
        if (playerData == null) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, id, 0, resultMode);
        }
        if (policyResolver == null || resultMode == null
                || (resultMode.learnsDirectly() && targetResolver == null)) {
            return Result.failure(
                    Status.POLICY_UNAVAILABLE,
                    id,
                    playerData.getResearchPoints(),
                    resultMode);
        }

        BlueprintResearchPolicy policy;
        try {
            policy = policyResolver.apply(blueprintId);
        } catch (RuntimeException exception) {
            return Result.failure(
                    Status.POLICY_UNAVAILABLE,
                    id,
                    playerData.getResearchPoints(),
                    resultMode);
        }
        return commit(
                blueprintId,
                playerData,
                policy,
                targetResolver,
                input,
                creativePlayer,
                blueprintsEnabled,
                resultMode);
    }

    private static Result commit(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            BlueprintResearchPolicy policy,
            Function<ResourceLocation, LearningTarget> targetResolver,
            ResearchInput input,
            boolean creativePlayer,
            boolean blueprintsEnabled,
            TreeResearchResultMode resultMode) {
        int currentPoints = playerData.getResearchPoints();
        Optional<ResourceLocation> id = Optional.of(blueprintId);
        if (policy == null) {
            return Result.failure(Status.POLICY_UNAVAILABLE, id, currentPoints, resultMode);
        }
        if (!blueprintId.equals(policy.blueprintId())) {
            return Result.failure(Status.POLICY_MISMATCH, id, currentPoints, resultMode);
        }
        if (!policy.playerDataAvailable() || policy.researchPoints() != currentPoints) {
            return Result.failure(Status.STALE_POLICY, id, currentPoints, resultMode);
        }
        if (!policy.available()) {
            return Result.failure(Status.CONTENT_UNAVAILABLE, id, currentPoints, resultMode);
        }
        if (policy.blocked()) {
            return Result.failure(Status.BLOCKED, id, currentPoints, resultMode);
        }
        if (!policy.researchEnabled()) {
            return Result.failure(Status.RESEARCH_DISABLED, id, currentPoints, resultMode);
        }
        if (policy.learned()) {
            return Result.failure(Status.ALREADY_LEARNED, id, currentPoints, resultMode);
        }
        if (policy.requiresDiscovery() && !policy.discovered()) {
            return Result.failure(Status.DISCOVERY_REQUIRED, id, currentPoints, resultMode);
        }
        if (!policy.prerequisitesSatisfied()) {
            return Result.failure(Status.PREREQUISITES_REQUIRED, id, currentPoints, resultMode);
        }
        if (policy.researchCost().points() > policy.pointCap() || !policy.researchable()) {
            return Result.failure(Status.POLICY_INELIGIBLE, id, currentPoints, resultMode);
        }

        boolean bypassCost = creativePlayer && policy.creativeBypassesCost();
        List<ItemStack> inputSnapshot = input.stacks();
        if (inputSnapshot == null || inputSnapshot.stream().anyMatch(java.util.Objects::isNull)) {
            return Result.failure(Status.INVALID_INPUT, id, currentPoints, resultMode);
        }
        ResearchIngredientPlanner.Plan plan = new ResearchIngredientPlanner.Plan(new int[inputSnapshot.size()]);
        if (!bypassCost) {
            if (currentPoints < policy.researchCost().points()) {
                return Result.failure(Status.POINTS_REQUIRED, id, currentPoints, resultMode);
            }
            Optional<ResearchIngredientPlanner.Plan> resolvedPlan =
                    ResearchIngredientPlanner.plan(inputSnapshot, policy.researchCost());
            if (resolvedPlan.isEmpty()) {
                return Result.failure(Status.INGREDIENTS_REQUIRED, id, currentPoints, resultMode);
            }
            plan = resolvedPlan.orElseThrow();
        }

        PreparedLearning preparedLearning = null;
        ItemStack output = ItemStack.EMPTY;
        if (resultMode.learnsDirectly()) {
            Preparation preparation = BlueprintLearningService.prepare(
                    new BlueprintLearningService.Request(
                            BlueprintUnlockOrigin.TREE_RESEARCH,
                            blueprintId,
                            blueprintsEnabled,
                            PhysicalBlueprintLearningMode.DISABLED,
                            // The live ServerPlayer entry point rejects current
                            // exemptions before entering this pure transaction.
                            // Do not re-enter Forge config from the injectable
                            // commit boundary used by deterministic tests/tools.
                            false),
                    playerData,
                    targetResolver,
                    ignored -> policy);
            if (!preparation.ready()) {
                return Result.failure(
                        mapLearningFailure(
                                preparation.failure().orElseThrow().status()),
                        id,
                        currentPoints,
                        resultMode);
            }
            preparedLearning = preparation.prepared().orElseThrow();
        } else {
            if (!input.canAcceptOutput()) {
                return Result.failure(Status.OUTPUT_FULL, id, currentPoints, resultMode);
            }
            try {
                output = input.createOutput(blueprintId);
            } catch (RuntimeException exception) {
                return Result.failure(Status.TRANSACTION_FAILED, id, currentPoints, resultMode);
            }
            if (output == null || output.isEmpty()) {
                return Result.failure(Status.TRANSACTION_FAILED, id, currentPoints, resultMode);
            }
        }

        int pointsSpent = bypassCost ? 0 : policy.researchCost().points();
        if (pointsSpent > 0 && !playerData.spendResearchPoints(pointsSpent)) {
            return Result.failure(
                    Status.STALE_POLICY,
                    id,
                    playerData.getResearchPoints(),
                    resultMode);
        }
        try {
            input.consume(plan);
            if (resultMode.createsPhysicalBlueprint() && !input.deliver(output)) {
                boolean restored = rollback(
                        playerData, input, inputSnapshot, currentPoints);
                return Result.failure(
                        restored ? Status.TRANSACTION_FAILED : Status.ROLLBACK_FAILED,
                        id,
                        playerData.getResearchPoints(),
                        resultMode);
            }
        } catch (RuntimeException exception) {
            boolean restored = rollback(
                    playerData, input, inputSnapshot, currentPoints);
            return Result.failure(
                    restored ? Status.TRANSACTION_FAILED : Status.ROLLBACK_FAILED,
                    id,
                    playerData.getResearchPoints(),
                    resultMode);
        }

        BlueprintLearningService.Result learning = null;
        if (resultMode.learnsDirectly()) {
            learning = BlueprintLearningService.commitPrepared(
                    preparedLearning, playerData);
            if (!learning.successful()) {
                boolean restored = rollback(
                        playerData, input, inputSnapshot, currentPoints);
                return Result.failure(
                        restored
                                ? mapLearningFailure(learning.status())
                                : Status.ROLLBACK_FAILED,
                        id,
                        playerData.getResearchPoints(),
                        resultMode);
            }
        }
        return new Result(
                Status.SUCCESS,
                id,
                pointsSpent,
                playerData.getResearchPoints(),
                bypassCost,
                resultMode,
                learning != null && learning.learnedChanged(),
                learning != null && learning.discoveredChanged(),
                learning != null && learning.legacyRecipeChanged());
    }

    private static Status mapLearningFailure(BlueprintLearningService.Status status) {
        return switch (status) {
            case SUCCESS -> throw new IllegalArgumentException(
                    "successful learning cannot map to research failure");
            case INVALID_INPUT, INVALID_IDENTITY -> Status.INVALID_INPUT;
            case PLAYER_DATA_UNAVAILABLE -> Status.PLAYER_DATA_UNAVAILABLE;
            case CONTENT_UNAVAILABLE, BLUEPRINTS_DISABLED, PROGRESSION_EXEMPT ->
                    Status.CONTENT_UNAVAILABLE;
            case POLICY_UNAVAILABLE -> Status.POLICY_UNAVAILABLE;
            case POLICY_MISMATCH -> Status.POLICY_MISMATCH;
            case STALE_POLICY -> Status.STALE_POLICY;
            case BLOCKED -> Status.BLOCKED;
            case ALREADY_LEARNED -> Status.ALREADY_LEARNED;
            case PREREQUISITES_REQUIRED -> Status.PREREQUISITES_REQUIRED;
            case PROGRESSION_CAPACITY_EXHAUSTED ->
                    Status.PROGRESSION_CAPACITY_EXHAUSTED;
            case PHYSICAL_BLUEPRINT_LEARNING_DISABLED, TRANSACTION_FAILED ->
                    Status.TRANSACTION_FAILED;
        };
    }

    private static boolean rollback(
            IPlayerRecipeData playerData,
            ResearchInput input,
            List<ItemStack> inputSnapshot,
            int originalPoints) {
        boolean inventoryRestored = true;
        try {
            input.restore(inputSnapshot);
        } catch (RuntimeException exception) {
            inventoryRestored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research transaction failed and its inventory snapshot could not be restored",
                    exception);
        }
        boolean pointsRestored;
        try {
            pointsRestored = playerData.setResearchPoints(originalPoints);
        } catch (RuntimeException exception) {
            pointsRestored = false;
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research transaction failed and restoring its RP balance threw",
                    exception);
        }
        if (!pointsRestored) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Research transaction failed and its RP balance could not be restored to {}",
                    originalPoints);
        }
        return inventoryRestored && pointsRestored;
    }

    private static ResearchInput playerInventoryInput(ServerPlayer player) {
        return new ResearchInput() {
            @Override
            public List<ItemStack> stacks() {
                return player.getInventory().items.stream()
                        .map(ItemStack::copy)
                        .toList();
            }

            @Override
            public boolean canAcceptOutput() {
                // Ingredient consumption normally opens a slot. If it does not,
                // deliver() safely drops the researched blueprint at the player.
                return true;
            }

            @Override
            public void consume(ResearchIngredientPlanner.Plan plan) {
                List<ItemStack> items = player.getInventory().items;
                if (plan.slotCount() != items.size()) {
                    throw new IllegalStateException("player inventory changed before research commit");
                }
                for (int slot = 0; slot < items.size(); slot++) {
                    int amount = plan.decrement(slot);
                    if (amount < 0 || items.get(slot).getCount() < amount) {
                        throw new IllegalStateException("player inventory changed before research commit");
                    }
                    items.get(slot).shrink(amount);
                }
                player.getInventory().setChanged();
            }

            @Override
            public void restore(List<ItemStack> snapshot) {
                List<ItemStack> items = player.getInventory().items;
                if (snapshot.size() != items.size()) {
                    throw new IllegalStateException("cannot restore a resized player inventory");
                }
                for (int slot = 0; slot < items.size(); slot++) {
                    items.set(slot, snapshot.get(slot).copy());
                }
                player.getInventory().setChanged();
            }

            @Override
            public ItemStack createOutput(ResourceLocation id) {
                return BlueprintItem.createBlueprint(id.toString());
            }

            @Override
            public boolean deliver(ItemStack output) {
                return deliverOutput(
                        output,
                        player.getInventory()::add,
                        remainder -> player.drop(remainder, false) != null);
            }
        };
    }

    /**
     * Delivers the single transaction output and verifies the fallback entity was actually
     * created. Keeping this boundary explicit prevents a cancelled Forge toss event from being
     * reported as a successful research transaction.
     */
    static boolean deliverOutput(
            ItemStack output,
            Consumer<ItemStack> inventoryInsertion,
            Predicate<ItemStack> overflowDrop) {
        if (output == null || output.isEmpty() || output.getCount() != 1
                || inventoryInsertion == null || overflowDrop == null) {
            return false;
        }
        inventoryInsertion.accept(output);
        return output.isEmpty() || overflowDrop.test(output);
    }

    private static boolean validId(ResourceLocation id) {
        return id != null && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    interface ResearchInput {
        List<ItemStack> stacks();

        boolean canAcceptOutput();

        void consume(ResearchIngredientPlanner.Plan plan);

        void restore(List<ItemStack> snapshot);

        ItemStack createOutput(ResourceLocation blueprintId);

        /** Returns false only when no part of the output was delivered. */
        boolean deliver(ItemStack output);
    }

    public enum Status {
        SUCCESS,
        INVALID_INPUT,
        PLAYER_DATA_UNAVAILABLE,
        POLICY_UNAVAILABLE,
        POLICY_MISMATCH,
        STALE_POLICY,
        CONTENT_UNAVAILABLE,
        BLOCKED,
        RESEARCH_DISABLED,
        ALREADY_LEARNED,
        DISCOVERY_REQUIRED,
        PREREQUISITES_REQUIRED,
        POINTS_REQUIRED,
        INGREDIENTS_REQUIRED,
        OUTPUT_FULL,
        POLICY_INELIGIBLE,
        TRANSACTION_FAILED,
        PROGRESSION_CAPACITY_EXHAUSTED,
        ROLLBACK_FAILED
    }

    /**
     * Completed transaction result.
     *
     * @param balanceAfterCost RP balance immediately after paying the research
     *     cost, before live discovery or learning awards are dispatched
     */
    public record Result(
            Status status,
            Optional<ResourceLocation> blueprintId,
            int spentPoints,
            int balanceAfterCost,
            boolean costBypassed,
            TreeResearchResultMode resultMode,
            boolean learnedChanged,
            boolean discoveredChanged,
            boolean legacyRecipeChanged) {
        public Result {
            if (status == null || resultMode == null
                    || spentPoints < 0 || balanceAfterCost < 0
                    || spentPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || balanceAfterCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid blueprint research result");
            }
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            if (blueprintId.filter(id -> id.toString().length()
                    > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()) {
                throw new IllegalArgumentException("research result blueprint ID is oversized");
            }
            if (status == Status.SUCCESS) {
                if (blueprintId.isEmpty()
                        || (costBypassed && spentPoints != 0)
                        || spentPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                                - balanceAfterCost
                        || (resultMode.learnsDirectly() && !learnedChanged)
                        || (resultMode.createsPhysicalBlueprint()
                                && (learnedChanged
                                        || discoveredChanged
                                        || legacyRecipeChanged))) {
                    throw new IllegalArgumentException("successful research result is inconsistent");
                }
            } else if (spentPoints != 0 || costBypassed
                    || learnedChanged || discoveredChanged || legacyRecipeChanged) {
                throw new IllegalArgumentException("failed research cannot spend or bypass costs");
            }
        }

        /** Compatibility constructor for menu-generated failures. */
        public Result(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int spentPoints,
                int balanceAfterCost,
                boolean costBypassed) {
            this(
                    status,
                    blueprintId,
                    spentPoints,
                    balanceAfterCost,
                    costBypassed,
                    TreeResearchResultMode.CREATE_BLUEPRINT,
                    false,
                    false,
                    false);
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        private static Result failure(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int currentBalance) {
            return failure(
                    status,
                    blueprintId,
                    currentBalance,
                    TreeResearchResultMode.CREATE_BLUEPRINT);
        }

        private static Result failure(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int currentBalance,
                TreeResearchResultMode resultMode) {
            int boundedBalance = Math.max(
                    0,
                    Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, currentBalance));
            return new Result(
                    status,
                    blueprintId,
                    0,
                    boundedBalance,
                    false,
                    resultMode == null
                            ? TreeResearchResultMode.DIRECT_LEARN
                            : resultMode,
                    false,
                    false,
                    false);
        }
    }
}
