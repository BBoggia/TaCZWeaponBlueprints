package com.gamergaming.taczweaponblueprints.progression.fragment;

import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintDiscoveryService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointTransactionService;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Server-authoritative preview and atomic deposit for Blueprint Fragments. */
public final class BlueprintFragmentAnalysisService {
    private BlueprintFragmentAnalysisService() {
    }

    public static Evaluation evaluate(ServerPlayer player, WorkstationTransaction transaction) {
        if (player == null || transaction == null) {
            return Evaluation.failure(Status.INVALID_INPUT);
        }
        ItemStack input;
        ItemStack output;
        try {
            input = transaction.physicalInput();
            output = transaction.outputStack();
        } catch (RuntimeException exception) {
            return Evaluation.failure(Status.TRANSACTION_FAILED);
        }
        Optional<ResourceLocation> target = BlueprintFragmentItem.getTarget(input);
        if (target.isEmpty()) {
            return Evaluation.failure(Status.INVALID_INPUT);
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return Evaluation.unavailable(
                    Status.PLAYER_DATA_UNAVAILABLE,
                    target.orElseThrow(),
                    input.getCount(),
                    0,
                    0);
        }
        PolicyContext context = policy(target.orElseThrow());
        if (context == null) {
            return Evaluation.unavailable(
                    Status.POLICY_UNAVAILABLE,
                    target.orElseThrow(),
                    input.getCount(),
                    data.getResearchPoints(),
                    ModConfigs.BLUEPRINT.progressionSnapshot().pointCap());
        }
        return evaluate(
                target.orElseThrow(),
                input.getCount(),
                output,
                data,
                context.policy(),
                ModConfigs.BLUEPRINT.progressionSnapshot().pointCap(),
                context.publicationRevision());
    }

    public static Evaluation evaluate(
            ResourceLocation target,
            int offered,
            ItemStack output,
            IPlayerRecipeData data,
            ResolvedBlueprintProgressionPolicy resolved,
            int pointCap,
            long publicationRevision) {
        if (target == null || offered < 1 || output == null || data == null
                || resolved == null || !target.equals(resolved.blueprintId())
                || pointCap < 0 || publicationRevision < 1L) {
            return Evaluation.failure(Status.INVALID_INPUT, Optional.ofNullable(target));
        }
        BlueprintFragmentPolicy policy = resolved.fragments();
        if (!policy.enabled()) {
            return Evaluation.unavailable(
                    Status.FRAGMENTS_DISABLED,
                    target,
                    offered,
                    data.getResearchPoints(),
                    pointCap);
        }
        int before = data.getArchivedBlueprintFragments()
                .getOrDefault(target.toString(), 0);
        BlueprintFragmentPolicy.ArchiveResult archive;
        try {
            archive = policy.archive(before, offered);
        } catch (RuntimeException exception) {
            return Evaluation.failure(Status.INVALID_INPUT, Optional.of(target));
        }
        boolean learned = data.hasBlueprint(target.toString());
        int setsBefore = before / policy.threshold();
        int setsAfterDeposit = archive.resulting() / policy.threshold();
        boolean completedSet = setsAfterDeposit > 0;
        boolean consumeSet = false;
        int awardedPoints = 0;
        boolean createsBlueprint = false;
        Status status = archive.accepted() < 1 ? Status.ARCHIVE_FULL : Status.READY;
        if (learned) {
            if (policy.learnedTargetResearchPoints() < 1) {
                status = Status.LEARNED_TARGET_RETURN_DISABLED;
            } else if (completedSet) {
                var pointEvaluation = ResearchPointTransactionService.evaluate(
                        data,
                        policy.learnedTargetResearchPoints(),
                        pointCap,
                        ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL);
                if (!pointEvaluation.successful()) {
                    status = Status.POINT_CAP_REACHED;
                } else {
                    status = Status.READY;
                    awardedPoints = pointEvaluation.awardedPoints();
                    consumeSet = true;
                }
            }
        } else if (completedSet && policy.completionMode()
                == BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT) {
            if (!output.isEmpty()) {
                status = Status.OUTPUT_OCCUPIED;
            } else {
                status = Status.READY;
                createsBlueprint = true;
                consumeSet = true;
            }
        }

        int afterAction = archive.resulting() - (consumeSet ? policy.threshold() : 0);
        return new Evaluation(
                status,
                Optional.of(target),
                policy.completionMode(),
                offered,
                archive.accepted(),
                archive.rejected(),
                before,
                archive.resulting(),
                afterAction,
                policy.threshold(),
                setsBefore,
                setsAfterDeposit,
                consumeSet,
                learned,
                awardedPoints,
                data.getResearchPoints(),
                pointCap,
                createsBlueprint,
                publicationRevision);
    }

    public static Result analyze(ServerPlayer player, WorkstationTransaction transaction) {
        Evaluation evaluation = evaluate(player, transaction);
        if (!evaluation.ready() || player == null || transaction == null) {
            return Result.from(evaluation);
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE, evaluation.target());
        }
        Evaluation current = evaluate(player, transaction);
        if (!current.equals(evaluation)) {
            return Result.failure(Status.STALE_POLICY, evaluation.target());
        }

        Result result = commit(evaluation, data, transaction);
        if (!result.successful()) {
            return result;
        }
        // Discovery is normally recorded by the fragment item's inventory tick.
        // This fallback also covers immediate shift-click and menu insertion.
        ItemStack inputBefore = BlueprintFragmentItem.create(evaluation.target().orElseThrow());
        BlueprintDiscoveryService.discoverInventoryFragment(player, inputBefore);
        try {
            NetworkHandler.syncPlayerRecipeData(player);
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Blueprint Fragment progress committed for {}, but immediate sync failed",
                    player.getGameProfile().getName(),
                    exception);
            BlueprintProgressionSyncScheduler.markKnowledgeDirty(player);
        }
        return result;
    }

    /** Commits an already server-authored evaluation without consulting mutable globals. */
    public static Result commit(
            Evaluation evaluation,
            IPlayerRecipeData data,
            WorkstationTransaction transaction) {
        if (evaluation == null || !evaluation.ready() || data == null || transaction == null) {
            return Result.from(evaluation);
        }
        ItemStack inputBefore;
        ItemStack outputBefore;
        String target;
        int pointsBefore;
        Map<String, Integer> fragmentsBefore;
        Map<String, Integer> criteriaBefore;
        boolean changesArchive;
        try {
            inputBefore = transaction.physicalInput().copy();
            outputBefore = transaction.outputStack().copy();
            target = evaluation.target().orElseThrow().toString();
            if (transaction.fragmentTarget()
                            .filter(evaluation.target().orElseThrow()::equals).isEmpty()
                    || inputBefore.getCount() != evaluation.offered()
                    || data.getResearchPoints() != evaluation.pointBalance()
                    || data.getArchivedBlueprintFragments().getOrDefault(target, 0)
                            != evaluation.archivedBefore()
                    || evaluation.createsBlueprint() && !outputBefore.isEmpty()) {
                return Result.failure(Status.STALE_POLICY, evaluation.target());
            }
            pointsBefore = data.getResearchPoints();
            fragmentsBefore = Map.copyOf(data.getArchivedBlueprintFragments());
            criteriaBefore = Map.copyOf(data.getProgressionCriteria());
            changesArchive = evaluation.archivedBefore() != evaluation.archivedAfterAction();
            if (changesArchive) {
                PlayerProgressValueMutation.Result preflight = data.applyArchivedFragmentMutation(
                        PlayerProgressValueMutation.Request.preflight(
                                target,
                                evaluation.archivedBefore(),
                                evaluation.archivedAfterAction()));
                if (!preflight.successful()) {
                    return Result.failure(
                            preflight.status()
                                            == PlayerProgressValueMutation.Status.CAPACITY_REACHED
                                    ? Status.PROGRESSION_CAPACITY_EXHAUSTED
                                    : Status.STALE_POLICY,
                            evaluation.target());
                }
            }
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Blueprint Fragment Analyzer preflight failed",
                    exception);
            return Result.failure(Status.TRANSACTION_FAILED, evaluation.target());
        }

        boolean committed = false;
        try {
            if (evaluation.awardedPoints() > 0) {
                var credited = ResearchPointTransactionService.credit(
                        data,
                        evaluation.awardedPoints(),
                        evaluation.pointCap(),
                        ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL);
                if (!credited.successful()) {
                    throw new IllegalStateException("RP balance changed after fragment preflight");
                }
            }
            if (changesArchive && !data.applyArchivedFragmentMutation(
                    PlayerProgressValueMutation.Request.commit(
                            target,
                            evaluation.archivedBefore(),
                            evaluation.archivedAfterAction())).successful()) {
                throw new IllegalStateException("fragment archive changed after preflight");
            }
            if (!transaction.consumePhysical(inputBefore, evaluation.accepted())) {
                throw new IllegalStateException("fragment input changed during commit");
            }
            if (evaluation.createsBlueprint()) {
                ItemStack reconstructed = transaction.createOutput(
                        evaluation.target().orElseThrow(),
                        BlueprintProvenance.fragmentReconstructed());
                if (!transaction.placeOutput(reconstructed, outputBefore)) {
                    throw new IllegalStateException("fragment reconstruction output changed");
                }
            }
            committed = true;
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Blueprint Fragment Analyzer transaction failed for {}",
                    target,
                    exception);
        }
        if (!committed) {
            boolean restored = restore(
                    transaction,
                    data,
                    inputBefore,
                    outputBefore,
                    pointsBefore,
                    fragmentsBefore,
                    criteriaBefore);
            return Result.failure(
                    restored ? Status.TRANSACTION_FAILED : Status.ROLLBACK_FAILED,
                    evaluation.target());
        }

        return new Result(
                Status.SUCCESS,
                evaluation.target(),
                evaluation.accepted(),
                evaluation.awardedPoints(),
                data.getResearchPoints(),
                evaluation.archivedAfterAction(),
                evaluation.createsBlueprint());
    }

    private static boolean restore(
            WorkstationTransaction transaction,
            IPlayerRecipeData data,
            ItemStack input,
            ItemStack output,
            int points,
            Map<String, Integer> fragments,
            Map<String, Integer> criteria) {
        boolean workstation = false;
        boolean balance = false;
        boolean supplemental = false;
        try {
            workstation = transaction.restore(input, output);
        } catch (RuntimeException ignored) {
        }
        try {
            balance = data.setResearchPoints(points);
        } catch (RuntimeException ignored) {
        }
        try {
            supplemental = data.replaceSupplementalProgression(fragments, criteria);
        } catch (RuntimeException ignored) {
        }
        return workstation && balance && supplemental;
    }

    private static PolicyContext policy(ResourceLocation target) {
        if (target == null || BlueprintDataManager.SERVER.getBlueprintData(target.toString()) == null) {
            return null;
        }
        var policyAccess = ProgressionPolicyAccessService.acquire(
                ProgressionPolicyAccessService.Mode.ENSURE_CURRENT).orElse(null);
        if (policyAccess == null) {
            return null;
        }
        return policyAccess.policyFor(target)
                .map(value -> new PolicyContext(
                        value, policyAccess.policy().revision()))
                .orElse(null);
    }

    public interface WorkstationTransaction {
        ItemStack physicalInput();

        default Optional<ResourceLocation> fragmentTarget() {
            return BlueprintFragmentItem.getTarget(physicalInput());
        }

        ItemStack outputStack();

        ItemStack createOutput(ResourceLocation blueprintId, BlueprintProvenance provenance);

        boolean consumePhysical(ItemStack expectedInput, int count);

        boolean placeOutput(ItemStack output, ItemStack expectedOutput);

        boolean restore(ItemStack physicalInput, ItemStack output);
    }

    public enum Status {
        READY,
        SUCCESS,
        INVALID_INPUT,
        PLAYER_DATA_UNAVAILABLE,
        POLICY_UNAVAILABLE,
        STALE_POLICY,
        FRAGMENTS_DISABLED,
        ARCHIVE_FULL,
        LEARNED_TARGET_RETURN_DISABLED,
        POINT_CAP_REACHED,
        OUTPUT_OCCUPIED,
        PROGRESSION_CAPACITY_EXHAUSTED,
        TRANSACTION_FAILED,
        ROLLBACK_FAILED
    }

    public record Evaluation(
            Status status,
            Optional<ResourceLocation> target,
            BlueprintFragmentPolicy.CompletionMode completionMode,
            int offered,
            int accepted,
            int rejected,
            int archivedBefore,
            int archivedAfterDeposit,
            int archivedAfterAction,
            int threshold,
            int completedSetsBefore,
            int completedSetsAfterDeposit,
            boolean consumesSet,
            boolean learnedTarget,
            int awardedPoints,
            int pointBalance,
            int pointCap,
            boolean createsBlueprint,
            long publicationRevision) {
        public Evaluation {
            target = target == null ? Optional.empty() : target;
            if (status == null || completionMode == null || offered < 0 || accepted < 0
                    || rejected < 0 || archivedBefore < 0 || archivedAfterDeposit < 0
                    || archivedAfterAction < 0 || threshold < 0 || completedSetsBefore < 0
                    || completedSetsAfterDeposit < 0 || awardedPoints < 0 || pointBalance < 0
                    || pointCap < 0 || publicationRevision < 0L
                    || offered > PlayerProgressionLimits.MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION
                    || accepted > offered || rejected > offered
                    || archivedBefore > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || archivedAfterDeposit > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || archivedAfterAction > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || threshold > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || completedSetsBefore > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || completedSetsAfterDeposit > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || awardedPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
                throw new IllegalArgumentException("invalid Blueprint Fragment evaluation");
            }
            if (offered > 0 && accepted + rejected != offered) {
                throw new IllegalArgumentException("Blueprint Fragment offer accounting is inconsistent");
            }
            if (status == Status.READY && (target.isEmpty() || offered < 1
                    || accepted < 1 && !consumesSet
                    || threshold < 1 || publicationRevision < 1L
                    || accepted + rejected != offered
                    || archivedAfterDeposit != archivedBefore + accepted
                    || archivedAfterAction != archivedAfterDeposit
                            - (consumesSet ? threshold : 0))) {
                throw new IllegalArgumentException("ready Blueprint Fragment evaluation is inconsistent");
            }
        }

        static Evaluation failure(Status status) {
            return failure(status, Optional.empty());
        }

        static Evaluation failure(Status status, Optional<ResourceLocation> target) {
            return new Evaluation(
                    status,
                    target,
                    BlueprintFragmentPolicy.CompletionMode.DISABLED,
                    0, 0, 0, 0, 0, 0, 0, 0, 0,
                    false, false, 0, 0, 0, false, 0L);
        }

        static Evaluation unavailable(
                Status status,
                ResourceLocation target,
                int offered,
                int pointBalance,
                int pointCap) {
            return new Evaluation(
                    status,
                    Optional.of(target),
                    BlueprintFragmentPolicy.CompletionMode.DISABLED,
                    offered,
                    0,
                    offered,
                    0, 0, 0, 0, 0, 0,
                    false,
                    false,
                    0,
                    pointBalance,
                    pointCap,
                    false,
                    0L);
        }

        public boolean ready() {
            return status == Status.READY;
        }
    }

    public record Result(
            Status status,
            Optional<ResourceLocation> target,
            int consumed,
            int awardedPoints,
            int pointBalance,
            int archivedAfter,
            boolean createdBlueprint) {
        public Result {
            target = target == null ? Optional.empty() : target;
            if (status == null || consumed < 0 || awardedPoints < 0
                    || pointBalance < 0 || archivedAfter < 0
                    || (status == Status.SUCCESS)
                            != (consumed > 0 || awardedPoints > 0 || createdBlueprint)) {
                throw new IllegalArgumentException("invalid Blueprint Fragment result");
            }
        }

        static Result from(Evaluation evaluation) {
            return failure(evaluation == null ? Status.INVALID_INPUT : evaluation.status(),
                    evaluation == null ? Optional.empty() : evaluation.target());
        }

        static Result failure(Status status, Optional<ResourceLocation> target) {
            return new Result(status, target, 0, 0, 0, 0, false);
        }

        public boolean successful() {
            return status == Status.SUCCESS;
        }
    }

    private record PolicyContext(
            ResolvedBlueprintProgressionPolicy policy,
            long publicationRevision) {
    }
}
