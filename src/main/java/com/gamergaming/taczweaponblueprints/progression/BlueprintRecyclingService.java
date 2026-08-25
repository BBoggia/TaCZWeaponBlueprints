package com.gamergaming.taczweaponblueprints.progression;

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

/** Atomic server authority for one manual blueprint-to-points transaction. */
public final class BlueprintRecyclingService {
    private BlueprintRecyclingService() {
    }

    /**
     * Re-resolves policy from the current catalog, datapack snapshot, coarse
     * configuration, blacklist, and player progression immediately before commit.
     * The caller must supply the actual Research Bench input-slot stack.
     */
    public static Result recycle(ServerPlayer player, ItemStack input) {
        Optional<ResourceLocation> inputId = BlueprintItem.getBlueprintId(input);
        if (player == null || !player.isAlive() || inputId.isEmpty()) {
            return Result.failure(Status.INVALID_INPUT, inputId, 0);
        }

        Optional<IPlayerRecipeData> resolvedData =
                player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (resolvedData.isEmpty()) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, inputId, 0);
        }

        IPlayerRecipeData playerData = resolvedData.orElseThrow();
        int migrated = BlueprintDataManager.SERVER.migrateLegacyUnlocks(playerData);
        Result result = recycle(
                input,
                playerData,
                id -> BlueprintResearchDataManager.INSTANCE.policyFor(id, playerData));
        if (result.successful() || migrated > 0) {
            // One progression publication also refreshes the disclosure-filtered Journal.
            NetworkHandler.syncPlayerProgressionData(player);
        }
        return result;
    }

    static Result recycle(
            ItemStack input,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver) {
        return recycle(stackInput(input), playerData, policyResolver);
    }

    static Result recycle(
            RecyclingInput input,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver) {
        Optional<ResourceLocation> inputId = input == null ? Optional.empty() : input.blueprintId();
        if (inputId == null || inputId.isEmpty() || input.count() <= 0) {
            return Result.failure(
                    Status.INVALID_INPUT,
                    Optional.empty(),
                    playerData == null ? 0 : playerData.getResearchPoints());
        }
        if (playerData == null) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, inputId, 0);
        }
        if (policyResolver == null) {
            return Result.failure(Status.POLICY_UNAVAILABLE, inputId, playerData.getResearchPoints());
        }

        BlueprintResearchPolicy policy;
        try {
            policy = policyResolver.apply(inputId.orElseThrow());
        } catch (RuntimeException exception) {
            return Result.failure(Status.POLICY_UNAVAILABLE, inputId, playerData.getResearchPoints());
        }
        return commit(input, inputId.orElseThrow(), playerData, policy);
    }

    private static Result commit(
            RecyclingInput input,
            ResourceLocation inputId,
            IPlayerRecipeData playerData,
            BlueprintResearchPolicy policy) {
        int currentPoints = playerData.getResearchPoints();
        if (policy == null) {
            return Result.failure(Status.POLICY_UNAVAILABLE, Optional.of(inputId), currentPoints);
        }
        if (!inputId.equals(policy.blueprintId())) {
            return Result.failure(Status.POLICY_MISMATCH, Optional.of(inputId), currentPoints);
        }
        if (!policy.playerDataAvailable() || policy.researchPoints() != currentPoints) {
            return Result.failure(Status.STALE_POLICY, Optional.of(inputId), currentPoints);
        }
        if (!policy.available()) {
            return Result.failure(Status.CONTENT_UNAVAILABLE, Optional.of(inputId), currentPoints);
        }
        if (policy.blocked()) {
            return Result.failure(Status.BLOCKED, Optional.of(inputId), currentPoints);
        }
        if (!policy.recyclingEnabled()) {
            return Result.failure(Status.RECYCLING_DISABLED, Optional.of(inputId), currentPoints);
        }
        if (policy.recyclingValue() <= 0) {
            return Result.failure(Status.NO_VALUE, Optional.of(inputId), currentPoints);
        }
        if (!policy.learned() && !policy.allowUnlearnedRecycling()) {
            return Result.failure(Status.DUPLICATE_REQUIRED, Optional.of(inputId), currentPoints);
        }
        int value = policy.recyclingValue();
        if (value > policy.pointCap() || currentPoints > policy.pointCap() - value) {
            return Result.failure(Status.POINT_CAP_REACHED, Optional.of(inputId), currentPoints);
        }
        if (!policy.recyclable()) {
            return Result.failure(Status.POLICY_INELIGIBLE, Optional.of(inputId), currentPoints);
        }

        // No fallible item operation remains after this validated point credit.
        // The real input stack is always consumed, including for Creative players.
        if (!playerData.addResearchPoints(value, policy.pointCap())) {
            return Result.failure(Status.POINT_CAP_REACHED, Optional.of(inputId), currentPoints);
        }
        input.consumeOne();
        return new Result(Status.SUCCESS, Optional.of(inputId), value, playerData.getResearchPoints());
    }

    private static RecyclingInput stackInput(ItemStack stack) {
        Optional<ResourceLocation> id = BlueprintItem.getBlueprintId(stack);
        int count = stack == null ? 0 : stack.getCount();
        return new RecyclingInput() {
            @Override
            public Optional<ResourceLocation> blueprintId() {
                return id;
            }

            @Override
            public int count() {
                return count;
            }

            @Override
            public void consumeOne() {
                stack.shrink(1);
            }
        };
    }

    interface RecyclingInput {
        Optional<ResourceLocation> blueprintId();

        int count();

        void consumeOne();
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
        RECYCLING_DISABLED,
        NO_VALUE,
        DUPLICATE_REQUIRED,
        POINT_CAP_REACHED,
        POLICY_INELIGIBLE
    }

    public record Result(
            Status status,
            Optional<ResourceLocation> blueprintId,
            int awardedPoints,
            int newBalance) {
        public Result {
            if (status == null
                    || awardedPoints < 0
                    || awardedPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || newBalance < 0
                    || newBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid blueprint recycling result");
            }
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            if (blueprintId.filter(id -> id.toString().length()
                    > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()) {
                throw new IllegalArgumentException("recycling result blueprint ID is oversized");
            }
            if (status == Status.SUCCESS) {
                if (blueprintId.isEmpty() || awardedPoints <= 0 || awardedPoints > newBalance) {
                    throw new IllegalArgumentException("successful recycling must identify a positive award");
                }
            } else if (awardedPoints != 0) {
                throw new IllegalArgumentException("failed recycling cannot award points");
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
            return new Result(status, blueprintId, 0, boundedBalance);
        }
    }
}
