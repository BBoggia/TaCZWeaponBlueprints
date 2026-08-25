package com.gamergaming.taczweaponblueprints.progression;

import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
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
        int migrated = BlueprintDataManager.SERVER.migrateLegacyUnlocks(playerData);
        Result result = research(
                blueprintId,
                playerData,
                id -> BlueprintResearchDataManager.INSTANCE.policyFor(id, playerData),
                input,
                player.isCreative());
        if (result.successful()) {
            BlueprintDiscoveryService.discoverInventoryBlueprint(
                    player, BlueprintItem.createBlueprint(blueprintId.toString()));
        }
        if (result.successful() || migrated > 0) {
            NetworkHandler.syncPlayerProgressionData(player);
        }
        return result;
    }

    static Result research(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            ResearchInput input,
            boolean creativePlayer) {
        Optional<ResourceLocation> id = Optional.ofNullable(blueprintId).filter(BlueprintResearchService::validId);
        if (id.isEmpty() || input == null) {
            return Result.failure(Status.INVALID_INPUT, id, playerData == null ? 0 : playerData.getResearchPoints());
        }
        if (playerData == null) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, id, 0);
        }
        if (policyResolver == null) {
            return Result.failure(Status.POLICY_UNAVAILABLE, id, playerData.getResearchPoints());
        }

        BlueprintResearchPolicy policy;
        try {
            policy = policyResolver.apply(blueprintId);
        } catch (RuntimeException exception) {
            return Result.failure(Status.POLICY_UNAVAILABLE, id, playerData.getResearchPoints());
        }
        return commit(blueprintId, playerData, policy, input, creativePlayer);
    }

    private static Result commit(
            ResourceLocation blueprintId,
            IPlayerRecipeData playerData,
            BlueprintResearchPolicy policy,
            ResearchInput input,
            boolean creativePlayer) {
        int currentPoints = playerData.getResearchPoints();
        Optional<ResourceLocation> id = Optional.of(blueprintId);
        if (policy == null) {
            return Result.failure(Status.POLICY_UNAVAILABLE, id, currentPoints);
        }
        if (!blueprintId.equals(policy.blueprintId())) {
            return Result.failure(Status.POLICY_MISMATCH, id, currentPoints);
        }
        if (!policy.playerDataAvailable() || policy.researchPoints() != currentPoints) {
            return Result.failure(Status.STALE_POLICY, id, currentPoints);
        }
        if (!policy.available()) {
            return Result.failure(Status.CONTENT_UNAVAILABLE, id, currentPoints);
        }
        if (policy.blocked()) {
            return Result.failure(Status.BLOCKED, id, currentPoints);
        }
        if (!policy.researchEnabled()) {
            return Result.failure(Status.RESEARCH_DISABLED, id, currentPoints);
        }
        if (policy.learned()) {
            return Result.failure(Status.ALREADY_LEARNED, id, currentPoints);
        }
        if (policy.requiresDiscovery() && !policy.discovered()) {
            return Result.failure(Status.DISCOVERY_REQUIRED, id, currentPoints);
        }
        if (!policy.prerequisitesSatisfied()) {
            return Result.failure(Status.PREREQUISITES_REQUIRED, id, currentPoints);
        }
        if (policy.researchCost().points() > policy.pointCap() || !policy.researchable()) {
            return Result.failure(Status.POLICY_INELIGIBLE, id, currentPoints);
        }

        boolean bypassCost = creativePlayer && policy.creativeBypassesCost();
        List<ItemStack> inputSnapshot = input.stacks();
        if (inputSnapshot == null || inputSnapshot.stream().anyMatch(java.util.Objects::isNull)) {
            return Result.failure(Status.INVALID_INPUT, id, currentPoints);
        }
        ResearchIngredientPlanner.Plan plan = new ResearchIngredientPlanner.Plan(new int[inputSnapshot.size()]);
        if (!bypassCost) {
            if (currentPoints < policy.researchCost().points()) {
                return Result.failure(Status.POINTS_REQUIRED, id, currentPoints);
            }
            Optional<ResearchIngredientPlanner.Plan> resolvedPlan =
                    ResearchIngredientPlanner.plan(inputSnapshot, policy.researchCost());
            if (resolvedPlan.isEmpty()) {
                return Result.failure(Status.INGREDIENTS_REQUIRED, id, currentPoints);
            }
            plan = resolvedPlan.orElseThrow();
        }
        if (!input.canAcceptOutput()) {
            return Result.failure(Status.OUTPUT_FULL, id, currentPoints);
        }
        ItemStack output;
        try {
            output = input.createOutput(blueprintId);
        } catch (RuntimeException exception) {
            return Result.failure(Status.TRANSACTION_FAILED, id, currentPoints);
        }
        if (output == null || output.isEmpty()) {
            return Result.failure(Status.TRANSACTION_FAILED, id, currentPoints);
        }

        int pointsSpent = bypassCost ? 0 : policy.researchCost().points();
        if (pointsSpent > 0 && !playerData.spendResearchPoints(pointsSpent)) {
            return Result.failure(Status.STALE_POLICY, id, playerData.getResearchPoints());
        }
        try {
            input.consume(plan);
            if (!input.deliver(output)) {
                rollback(playerData, input, inputSnapshot, currentPoints);
                return Result.failure(Status.TRANSACTION_FAILED, id, playerData.getResearchPoints());
            }
        } catch (RuntimeException exception) {
            rollback(playerData, input, inputSnapshot, currentPoints);
            return Result.failure(Status.TRANSACTION_FAILED, id, playerData.getResearchPoints());
        }
        return new Result(Status.SUCCESS, id, pointsSpent, playerData.getResearchPoints(), bypassCost);
    }

    private static void rollback(
            IPlayerRecipeData playerData,
            ResearchInput input,
            List<ItemStack> inputSnapshot,
            int originalPoints) {
        try {
            input.restore(inputSnapshot);
        } catch (RuntimeException ignored) {
            // Preserve the original failure result; the concrete menu input
            // restore path is deterministic and does not throw.
        } finally {
            playerData.setResearchPoints(originalPoints);
        }
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
                if (!player.getInventory().add(output)) {
                    player.drop(output, false);
                }
                return true;
            }
        };
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
        TRANSACTION_FAILED
    }

    public record Result(
            Status status,
            Optional<ResourceLocation> blueprintId,
            int spentPoints,
            int newBalance,
            boolean costBypassed) {
        public Result {
            if (status == null || spentPoints < 0 || newBalance < 0
                    || spentPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || newBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
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
                        || spentPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS - newBalance) {
                    throw new IllegalArgumentException("successful research result is inconsistent");
                }
            } else if (spentPoints != 0 || costBypassed) {
                throw new IllegalArgumentException("failed research cannot spend or bypass costs");
            }
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        private static Result failure(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int currentBalance) {
            int boundedBalance = Math.max(
                    0,
                    Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, currentBalance));
            return new Result(status, blueprintId, 0, boundedBalance, false);
        }
    }
}
