package com.gamergaming.taczweaponblueprints.progression;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Atomic direct conversion of one positively verified found gun into RP. */
public final class FoundWeaponRecoveryService {
    private FoundWeaponRecoveryService() {
    }

    public static Evaluation evaluate(
            ServerPlayer player,
            BlueprintReverseEngineeringService.WorkstationTransaction transaction) {
        if (player == null || !player.isAlive() || transaction == null) {
            return Evaluation.failure(Status.INVALID_PLAYER, null, 0, 0, 0);
        }
        Optional<IPlayerRecipeData> data = player.getCapability(
                ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (data.isEmpty()) {
            return Evaluation.failure(
                    Status.PLAYER_DATA_UNAVAILABLE, null, 0, 0,
                    ModConfigs.BLUEPRINT.progressionSnapshot().pointCap());
        }
        BlueprintReverseEngineeringService.Evaluation reverse =
                BlueprintReverseEngineeringService.evaluateForDirectRecovery(
                        player, transaction);
        return evaluate(
                transaction.physicalInput(),
                data.orElseThrow(),
                reverse,
                ModConfigs.BLUEPRINT.progressionSnapshot().foundWeaponRecoveryMode(),
                id -> com.gamergaming.taczweaponblueprints.resource.research
                        .BlueprintResearchDataManager.INSTANCE.policyFor(
                                id, data.orElseThrow()));
    }

    static Evaluation evaluate(
            ItemStack input,
            IPlayerRecipeData playerData,
            BlueprintReverseEngineeringService.Evaluation reverse,
            FoundWeaponRecoveryMode recoveryMode,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver) {
        int balance = playerData == null ? 0 : playerData.getResearchPoints();
        int pointCap = reverse == null ? 0 : reverse.pointCap();
        if (playerData == null) {
            return Evaluation.failure(
                    Status.PLAYER_DATA_UNAVAILABLE, reverse, 0, balance, pointCap);
        }
        if (reverse == null || reverse.blueprintId().isEmpty()) {
            return Evaluation.failure(Status.INVALID_INPUT, reverse, 0, balance, pointCap);
        }
        if (!reverse.ready()) {
            return Evaluation.failure(
                    Status.REVERSE_ENGINEERING_INELIGIBLE,
                    reverse,
                    0,
                    balance,
                    pointCap);
        }
        if (reverse.base().physical().kind() != BlueprintKind.GUN) {
            return Evaluation.failure(Status.NOT_A_WEAPON, reverse, 0, balance, pointCap);
        }
        if (PhysicalWeaponProvenance.from(input)
                .filter(PhysicalWeaponProvenance::verifiedLoot).isEmpty()) {
            return Evaluation.failure(
                    Status.VERIFIED_LOOT_REQUIRED, reverse, 0, balance, pointCap);
        }
        FoundWeaponRecoveryMode stableMode = recoveryMode == null
                ? FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY
                : recoveryMode;
        if (!stableMode.directPointsEnabled()) {
            return Evaluation.failure(Status.RECOVERY_DISABLED, reverse, 0, balance, pointCap);
        }

        ResourceLocation blueprintId = reverse.blueprintId().orElseThrow();
        BlueprintResearchPolicy policy;
        try {
            policy = policyResolver == null ? null : policyResolver.apply(blueprintId);
        } catch (RuntimeException exception) {
            return Evaluation.failure(
                    Status.POLICY_UNAVAILABLE, reverse, 0, balance, pointCap);
        }
        int value = policy == null ? 0 : Math.max(0, policy.recyclingValue());
        if (policy == null || !blueprintId.equals(policy.blueprintId())) {
            return Evaluation.failure(Status.POLICY_UNAVAILABLE, reverse, value, balance, pointCap);
        }
        pointCap = policy.pointCap();
        if (!policy.playerDataAvailable() || policy.researchPoints() != balance) {
            return Evaluation.failure(Status.STALE_POLICY, reverse, value, balance, pointCap);
        }
        if (!policy.available() || policy.blocked()) {
            return Evaluation.failure(Status.POLICY_INELIGIBLE, reverse, value, balance, pointCap);
        }
        if (!policy.recyclingEnabled()) {
            return Evaluation.failure(Status.RECYCLING_DISABLED, reverse, value, balance, pointCap);
        }
        if (value <= 0) {
            return Evaluation.failure(Status.NO_VALUE, reverse, 0, balance, pointCap);
        }

        int postCostBalance = balance - reverse.cost().points();
        if (postCostBalance < 0 || value > pointCap - Math.min(postCostBalance, pointCap)) {
            return Evaluation.failure(Status.POINT_CAP_REACHED, reverse, value, balance, pointCap);
        }
        return new Evaluation(
                Status.READY,
                reverse,
                value,
                balance,
                pointCap,
                postCostBalance + value);
    }

    public static Result recover(
            ServerPlayer player,
            BlueprintReverseEngineeringService.WorkstationTransaction transaction) {
        if (player == null || !player.isAlive() || transaction == null) {
            return Result.failure(Status.INVALID_PLAYER, Optional.empty(), 0);
        }
        Optional<IPlayerRecipeData> resolved = player.getCapability(
                ModCapabilities.PLAYER_RECIPE_DATA).resolve();
        if (resolved.isEmpty()) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, Optional.empty(), 0);
        }
        IPlayerRecipeData playerData = resolved.orElseThrow();
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
                    "Found-weapon recovery committed for {}, but progression sync failed; scheduling retry",
                    blueprintId,
                    exception);
            if (result.discoveredChanged()) {
                BlueprintProgressionSyncScheduler.markKnowledgeDirty(player);
            } else {
                BlueprintProgressionSyncScheduler.markDirty(player);
            }
        }
        NetworkHandler.sendResearchPointFeedback(
                player,
                new ResearchPointPresentationService.Feedback(
                        result.awardedPoints(), 1, false, List.of()));
        return finalResult;
    }

    static Result commit(
            Evaluation evaluation,
            IPlayerRecipeData playerData,
            BlueprintReverseEngineeringService.WorkstationTransaction transaction) {
        int currentBalance = playerData == null ? 0 : playerData.getResearchPoints();
        Optional<ResourceLocation> target = evaluation == null
                || evaluation.reverse() == null
                ? Optional.empty()
                : evaluation.reverse().blueprintId();
        if (evaluation == null || playerData == null || transaction == null) {
            return Result.failure(Status.INVALID_PLAYER, target, currentBalance);
        }
        if (!evaluation.ready()) {
            return Result.failure(evaluation.status(), target, currentBalance);
        }

        ResourceLocation blueprintId = target.orElseThrow();
        ItemStack originalInput;
        List<ItemStack> originalInventory;
        ItemStack originalOutput;
        Set<String> originalLearned;
        Set<String> originalDiscovered;
        int originalPoints;
        try {
            originalInput = transaction.physicalInput().copy();
            originalInventory = transaction.inventoryStacks().stream()
                    .map(ItemStack::copy).toList();
            originalOutput = transaction.outputStack().copy();
            originalLearned = new LinkedHashSet<>(playerData.getLearnedBlueprints());
            originalDiscovered = new LinkedHashSet<>(playerData.getDiscoveredBlueprints());
            originalPoints = playerData.getResearchPoints();
        } catch (RuntimeException exception) {
            return Result.failure(Status.TRANSACTION_FAILED, target, currentBalance);
        }
        Optional<ResearchIngredientPlanner.Plan> materialPlan;
        try {
            materialPlan = ResearchIngredientPlanner.plan(
                    originalInventory, evaluation.reverse().cost());
        } catch (RuntimeException exception) {
            return Result.failure(Status.TRANSACTION_FAILED, target, currentBalance);
        }
        if (materialPlan.isEmpty()) {
            return Result.failure(Status.STALE_INPUT, target, currentBalance);
        }

        int pointsSpent = evaluation.reverse().cost().points();
        if (pointsSpent > 0 && !playerData.spendResearchPoints(pointsSpent)) {
            return Result.failure(Status.STALE_POLICY, target, playerData.getResearchPoints());
        }
        boolean discoveredChanged = !playerData.hasDiscoveredBlueprint(blueprintId.toString());
        try {
            if (!transaction.consumeMaterials(
                        materialPlan.orElseThrow(), originalInventory)
                    || !transaction.consumePhysical(
                        originalInput, evaluation.reverse().requiredInputCount())) {
                return rollback(
                        transaction, playerData, originalInput, originalInventory,
                        originalOutput, originalLearned, originalDiscovered, originalPoints,
                        target, Status.STALE_INPUT);
            }
            if (discoveredChanged
                    && !playerData.discoverBlueprint(blueprintId.toString())) {
                return rollback(
                        transaction, playerData, originalInput, originalInventory,
                        originalOutput, originalLearned, originalDiscovered, originalPoints,
                        target, Status.TRANSACTION_FAILED);
            }
            ResearchPointTransactionService.Result credit =
                    ResearchPointTransactionService.credit(
                            playerData,
                            evaluation.pointValue(),
                            evaluation.pointCap(),
                            ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL);
            if (!credit.successful()) {
                return rollback(
                        transaction, playerData, originalInput, originalInventory,
                        originalOutput, originalLearned, originalDiscovered, originalPoints,
                        target, Status.POINT_CAP_REACHED);
            }
        } catch (RuntimeException exception) {
            return rollback(
                    transaction, playerData, originalInput, originalInventory,
                    originalOutput, originalLearned, originalDiscovered, originalPoints,
                    target, Status.TRANSACTION_FAILED);
        }
        return new Result(
                Status.SUCCESS,
                target,
                evaluation.reverse().requiredInputCount(),
                pointsSpent,
                evaluation.pointValue(),
                playerData.getResearchPoints(),
                discoveredChanged);
    }

    private static Result rollback(
            BlueprintReverseEngineeringService.WorkstationTransaction transaction,
            IPlayerRecipeData playerData,
            ItemStack input,
            List<ItemStack> inventory,
            ItemStack output,
            Set<String> learned,
            Set<String> discovered,
            int points,
            Optional<ResourceLocation> target,
            Status failure) {
        boolean restored = true;
        try {
            restored = transaction.restore(input, inventory, output);
        } catch (RuntimeException exception) {
            restored = false;
        }
        try {
            restored &= playerData.replaceProgression(learned, discovered, points);
        } catch (RuntimeException exception) {
            restored = false;
        }
        if (!restored) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Found-weapon recovery could not restore all state for {}",
                    target.orElse(null));
        }
        return Result.failure(
                restored ? failure : Status.ROLLBACK_FAILED,
                target,
                playerData.getResearchPoints());
    }

    public enum Status {
        INVALID_INPUT,
        INVALID_PLAYER,
        PLAYER_DATA_UNAVAILABLE,
        REVERSE_ENGINEERING_INELIGIBLE,
        NOT_A_WEAPON,
        VERIFIED_LOOT_REQUIRED,
        RECOVERY_DISABLED,
        POLICY_UNAVAILABLE,
        STALE_POLICY,
        POLICY_INELIGIBLE,
        RECYCLING_DISABLED,
        NO_VALUE,
        POINT_CAP_REACHED,
        READY,
        SUCCESS,
        STALE_INPUT,
        TRANSACTION_FAILED,
        ROLLBACK_FAILED
    }

    public record Evaluation(
            Status status,
            BlueprintReverseEngineeringService.Evaluation reverse,
            int pointValue,
            int pointBalance,
            int pointCap,
            int projectedBalance) {
        public Evaluation {
            if (status == null || pointValue < 0
                    || pointValue > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointBalance < 0
                    || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointCap < 0 || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || projectedBalance < 0
                    || projectedBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid found-weapon recovery evaluation");
            }
        }

        static Evaluation failure(
                Status status,
                BlueprintReverseEngineeringService.Evaluation reverse,
                int value,
                int balance,
                int cap) {
            int boundedBalance = Math.max(0, Math.min(
                    PlayerProgressionLimits.MAX_RESEARCH_POINTS, balance));
            return new Evaluation(
                    status,
                    reverse,
                    Math.max(0, Math.min(
                            PlayerProgressionLimits.MAX_RESEARCH_POINTS, value)),
                    boundedBalance,
                    Math.max(0, Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, cap)),
                    boundedBalance);
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
            int awardedPoints,
            int newBalance,
            boolean discoveredChanged) {
        public Result {
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            if (status == null || consumedPhysicalItems < 0
                    || spentPoints < 0 || awardedPoints < 0 || newBalance < 0
                    || consumedPhysicalItems
                            > com.gamergaming.taczweaponblueprints.resource.research
                                    .BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT
                    || spentPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || awardedPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || newBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid found-weapon recovery result");
            }
            if (status == Status.SUCCESS
                    && (blueprintId.isEmpty() || consumedPhysicalItems < 1
                            || awardedPoints < 1)) {
                throw new IllegalArgumentException(
                        "successful found-weapon recovery is incomplete");
            }
            if (status != Status.SUCCESS
                    && (consumedPhysicalItems != 0 || spentPoints != 0
                            || awardedPoints != 0 || discoveredChanged)) {
                throw new IllegalArgumentException(
                        "failed found-weapon recovery retained committed mutations");
            }
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }

        Result withBalance(int balance) {
            return new Result(
                    status, blueprintId, consumedPhysicalItems, spentPoints, awardedPoints,
                    Math.max(0, Math.min(
                            PlayerProgressionLimits.MAX_RESEARCH_POINTS, balance)),
                    discoveredChanged);
        }

        static Result failure(
                Status status,
                Optional<ResourceLocation> blueprintId,
                int balance) {
            return new Result(
                    status,
                    blueprintId,
                    0,
                    0,
                    0,
                    Math.max(0, Math.min(
                            PlayerProgressionLimits.MAX_RESEARCH_POINTS, balance)),
                    false);
        }
    }
}
