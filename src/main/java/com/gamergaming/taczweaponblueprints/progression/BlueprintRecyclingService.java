package com.gamergaming.taczweaponblueprints.progression;

import java.util.Optional;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
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
        BlueprintLearningService.MigrationResult migration =
                BlueprintLearningService.migrateLegacyUnlocksDetailed(
                        BlueprintDataManager.SERVER, playerData);
        Result result = recycle(
                input,
                playerData,
                id -> BlueprintResearchDataManager.INSTANCE.policyFor(id, playerData));
        publishPostCommitBestEffort(player, requiredSync(result, migration));
        if (result.successful()) {
            NetworkHandler.sendResearchPointFeedback(
                    player,
                    new ResearchPointPresentationService.Feedback(
                            result.awardedPoints(), 1, false, java.util.List.of()));
        }
        return result;
    }

    private static void publishPostCommitBestEffort(
            ServerPlayer player,
            SyncKind syncKind) {
        try {
            switch (syncKind) {
                case KNOWLEDGE -> NetworkHandler.syncPlayerRecipeData(player);
                case POINTS -> NetworkHandler.syncPlayerPointBalance(player);
                case NONE -> {
                }
            }
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Blueprint recycling committed for {}, but immediate {} sync failed; scheduling a retry",
                    player == null ? "unknown player" : player.getGameProfile().getName(),
                    syncKind,
                    exception);
            if (syncKind == SyncKind.KNOWLEDGE) {
                BlueprintProgressionSyncScheduler.markKnowledgeDirty(player);
            } else if (syncKind == SyncKind.POINTS) {
                BlueprintProgressionSyncScheduler.markDirty(player);
            }
        }
    }

    static SyncKind requiredSync(
            Result result,
            BlueprintLearningService.MigrationResult migration) {
        if (migration != null && migration.changed()) {
            // Migration may repair the legacy recipe alias as well as learned
            // nodes and prerequisites, so publish recipe knowledge and the
            // complete tree together.
            return SyncKind.KNOWLEDGE;
        }
        return result != null && result.successful() ? SyncKind.POINTS : SyncKind.NONE;
    }

    /**
     * Resolves the same live policy used by {@link #recycle(ServerPlayer, ItemStack)}
     * without changing the item stack or player progression. This is the only
     * source of truth for the Research Bench's recycling preview.
     */
    public static Evaluation evaluate(ServerPlayer player, ItemStack input) {
        Optional<ResourceLocation> inputId = BlueprintItem.getBlueprintId(input);
        if (player == null || !player.isAlive() || inputId.isEmpty()) {
            return Evaluation.failure(Status.INVALID_INPUT, inputId, 0, 0, 0);
        }
        Optional<IPlayerRecipeData> resolvedData =
                player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (resolvedData.isEmpty()) {
            return Evaluation.failure(Status.PLAYER_DATA_UNAVAILABLE, inputId, 0, 0, 0);
        }
        IPlayerRecipeData playerData = resolvedData.orElseThrow();
        return evaluate(
                stackInput(input),
                playerData,
                id -> BlueprintResearchDataManager.INSTANCE.policyFor(id, playerData));
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
        Evaluation evaluation = evaluate(input, playerData, policyResolver);
        if (!evaluation.successful()) {
            return Result.failure(
                    evaluation.status(), evaluation.blueprintId(), evaluation.currentBalance());
        }
        ResearchPointTransactionService.Result pointResult = ResearchPointTransactionService.credit(
                playerData,
                evaluation.pointValue(),
                evaluation.pointCap(),
                ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL);
        if (!pointResult.successful()) {
            return Result.failure(
                    Status.POINT_CAP_REACHED,
                    evaluation.blueprintId(),
                    playerData.getResearchPoints());
        }
        input.consumeOne();
        return new Result(
                Status.SUCCESS,
                evaluation.blueprintId(),
                pointResult.awardedPoints(),
                pointResult.newBalance());
    }

    static Evaluation evaluate(
            RecyclingInput input,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver) {
        Optional<ResourceLocation> inputId = input == null ? Optional.empty() : input.blueprintId();
        if (inputId == null || inputId.isEmpty() || input.count() <= 0) {
            return Evaluation.failure(
                    Status.INVALID_INPUT,
                    Optional.empty(),
                    0,
                    playerData == null ? 0 : playerData.getResearchPoints(),
                    0);
        }
        if (playerData == null) {
            return Evaluation.failure(Status.PLAYER_DATA_UNAVAILABLE, inputId, 0, 0, 0);
        }
        if (policyResolver == null) {
            return Evaluation.failure(
                    Status.POLICY_UNAVAILABLE, inputId, 0, playerData.getResearchPoints(), 0);
        }

        BlueprintResearchPolicy policy;
        try {
            policy = policyResolver.apply(inputId.orElseThrow());
        } catch (RuntimeException exception) {
            return Evaluation.failure(
                    Status.POLICY_UNAVAILABLE, inputId, 0, playerData.getResearchPoints(), 0);
        }
        return evaluatePolicy(
                inputId.orElseThrow(),
                playerData,
                policy,
                input.provenanceAllowsRecycling());
    }

    private static Evaluation evaluatePolicy(
            ResourceLocation inputId,
            IPlayerRecipeData playerData,
            BlueprintResearchPolicy policy,
            boolean provenanceAllowsRecycling) {
        int currentPoints = playerData.getResearchPoints();
        if (policy == null) {
            return Evaluation.failure(
                    Status.POLICY_UNAVAILABLE, Optional.of(inputId), 0, currentPoints, 0);
        }
        int value = Math.max(0, policy.recyclingValue());
        int pointCap = policy.pointCap();
        if (!inputId.equals(policy.blueprintId())) {
            return Evaluation.failure(
                    Status.POLICY_MISMATCH, Optional.of(inputId), value, currentPoints, pointCap);
        }
        if (!policy.playerDataAvailable() || policy.researchPoints() != currentPoints) {
            return Evaluation.failure(
                    Status.STALE_POLICY, Optional.of(inputId), value, currentPoints, pointCap);
        }
        if (!policy.available()) {
            return Evaluation.failure(
                    Status.CONTENT_UNAVAILABLE, Optional.of(inputId), value, currentPoints, pointCap);
        }
        if (policy.blocked()) {
            return Evaluation.failure(Status.BLOCKED, Optional.of(inputId), value, currentPoints, pointCap);
        }
        if (!policy.recyclingEnabled()) {
            return Evaluation.failure(
                    Status.RECYCLING_DISABLED, Optional.of(inputId), value, currentPoints, pointCap);
        }
        if (policy.recyclingValue() <= 0) {
            return Evaluation.failure(Status.NO_VALUE, Optional.of(inputId), 0, currentPoints, pointCap);
        }
        if (!policy.learned() && !policy.allowUnlearnedRecycling()) {
            return Evaluation.failure(
                    Status.DUPLICATE_REQUIRED, Optional.of(inputId), value, currentPoints, pointCap);
        }
        if (!provenanceAllowsRecycling) {
            return Evaluation.failure(
                    Status.POLICY_INELIGIBLE, Optional.of(inputId), value, currentPoints, pointCap);
        }
        if (value > policy.pointCap() || currentPoints > policy.pointCap() - value) {
            return Evaluation.failure(
                    Status.POINT_CAP_REACHED, Optional.of(inputId), value, currentPoints, pointCap);
        }
        if (!policy.recyclable()) {
            return Evaluation.failure(
                    Status.POLICY_INELIGIBLE, Optional.of(inputId), value, currentPoints, pointCap);
        }
        return new Evaluation(
                Status.SUCCESS, Optional.of(inputId), value, currentPoints, pointCap);
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

            @Override
            public boolean provenanceAllowsRecycling() {
                return BlueprintItem.provenanceAllowsRecycling(stack);
            }
        };
    }

    interface RecyclingInput {
        Optional<ResourceLocation> blueprintId();

        int count();

        void consumeOne();

        default boolean provenanceAllowsRecycling() {
            return true;
        }
    }

    enum SyncKind {
        NONE,
        POINTS,
        KNOWLEDGE
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

    /** Immutable, non-mutating recycling decision suitable for server-authored UI. */
    public record Evaluation(
            Status status,
            Optional<ResourceLocation> blueprintId,
            int pointValue,
            int currentBalance,
            int pointCap) {
        public Evaluation {
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            if (status == null
                    || pointValue < 0
                    || pointValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || currentBalance < 0
                    || currentBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointCap < 0
                    || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid recycling evaluation");
            }
            if (blueprintId.filter(id -> id.toString().length()
                    > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()) {
                throw new IllegalArgumentException("recycling evaluation blueprint ID is oversized");
            }
            if (status == Status.SUCCESS
                    && (blueprintId.isEmpty() || pointValue <= 0
                    || pointValue > pointCap - Math.min(currentBalance, pointCap))) {
                throw new IllegalArgumentException("successful recycling evaluation is not affordable");
            }
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        private static Evaluation failure(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int pointValue,
                int currentBalance,
                int pointCap) {
            return new Evaluation(
                    status,
                    blueprintId,
                    Math.max(0, Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, pointValue)),
                    Math.max(0, Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, currentBalance)),
                    Math.max(0, Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, pointCap)));
        }
    }
}
