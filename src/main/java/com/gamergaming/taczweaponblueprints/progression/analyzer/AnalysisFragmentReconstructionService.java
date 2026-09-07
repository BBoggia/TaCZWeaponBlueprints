package com.gamergaming.taczweaponblueprints.progression.analyzer;

import java.util.Objects;
import com.gamergaming.taczweaponblueprints.capabilities.AnalysisFragmentMutation;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.item.BlueprintProvenance;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentAnalysisService.WorkstationTransaction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Reconstruction-only transaction boundary. Menu/policy acquisition is integrated separately. */
public final class AnalysisFragmentReconstructionService {
    private AnalysisFragmentReconstructionService() { }
    public enum Status { READY, SUCCESS, INVALID_INPUT, DISABLED, ARCHIVE_FULL, OUTPUT_OCCUPIED,
        CAPACITY_REACHED, UNSUPPORTED, STALE, TRANSACTION_FAILED, ROLLBACK_FAILED }

    /** Current target policy; these permissions must not be inferred from the holder's inventory. */
    public record Policy(ResourceLocation target, boolean enabled, int threshold, int retainedCap,
            boolean recyclable, PhysicalBlueprintLearningMode learning, long revision) {
        public Policy {
            if (target == null || BlueprintFragmentItem.parseTarget(target.toString()).isEmpty()
                    || threshold < 1 || threshold > 1_000_000 || retainedCap < threshold
                    || retainedCap > PlayerProgressionLimits.MAX_PROGRESS_VALUE || learning == null || revision < 1) {
                throw new IllegalArgumentException("invalid reconstruction policy");
            }
        }
    }
    public record Evaluation(Status status, Policy policy, BlueprintFragmentItem.Identity identity,
            int offered, int accepted, AnalysisFragmentCounts before, AnalysisFragmentCounts after,
            boolean createsBlueprint, AnalysisRewardClass outputClass) {
        public boolean ready() { return status == Status.READY; }
    }
    public record Result(Status status, int consumed, boolean createsBlueprint) {
        public boolean successful() { return status == Status.SUCCESS; }
    }

    public static Evaluation evaluate(ItemStack input, ItemStack output, IPlayerRecipeData data, Policy policy) {
        Objects.requireNonNull(data); Objects.requireNonNull(policy); Objects.requireNonNull(output);
        var identity = BlueprintFragmentItem.identity(input).filter(id -> id.analysisClass().isPresent()).orElse(null);
        if (identity == null || !identity.target().equals(policy.target()) || input.getCount() > input.getMaxStackSize()) {
            return new Evaluation(Status.INVALID_INPUT, policy, identity, 0, 0, AnalysisFragmentCounts.EMPTY, AnalysisFragmentCounts.EMPTY, false, null);
        }
        var reward = identity.analysisClass().orElseThrow();
        var before = data.getArchivedAnalysisFragments().getOrDefault(identity.target().toString(), AnalysisFragmentCounts.EMPTY);
        var ordinary = data.getArchivedBlueprintFragments();
        long room = Math.max(0L, Math.min((long) policy.retainedCap() - before.total(),
                (long) PlayerProgressionLimits.MAX_PROGRESS_VALUE - before.total() - ordinary.getOrDefault(identity.target().toString(), 0)));
        int accepted = (int) Math.min(input.getCount(), room);
        boolean complete = (long) before.get(reward) + accepted >= policy.threshold();
        var after = before.with(reward, before.get(reward) + accepted - (complete ? policy.threshold() : 0));
        Status status = !policy.enabled() || !policy.learning().learningPermitted() ? Status.DISABLED
                : complete && !output.isEmpty() ? Status.OUTPUT_OCCUPIED
                : accepted == 0 && !complete ? Status.ARCHIVE_FULL : Status.READY;
        if (status == Status.READY) {
            var preflight = data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.PREFLIGHT,
                    identity.target().toString(), before, after));
            if (preflight == PlayerProgressValueMutation.Status.UNSUPPORTED) status = Status.UNSUPPORTED;
            else if (preflight == PlayerProgressValueMutation.Status.CAPACITY_REACHED) status = Status.CAPACITY_REACHED;
            else if (preflight != PlayerProgressValueMutation.Status.READY && preflight != PlayerProgressValueMutation.Status.UNCHANGED) status = Status.STALE;
        }
        var outputClass = policy.learning().learningPermitted() ? reward.restrict(policy.recyclable(), policy.learning()) : null;
        boolean ready = status == Status.READY;
        return new Evaluation(status, policy, identity, input.getCount(), ready ? accepted : 0,
                before, ready ? after : before, ready && complete, outputClass);
    }

    /** Re-evaluates current policy and state before consuming; never credits RP or learns a target. */
    public static Result commit(Evaluation quoted, Policy current, IPlayerRecipeData data, WorkstationTransaction workstation) {
        return commit(quoted, current, data, workstation, () -> true);
    }

    public static Result commit(Evaluation quoted, Policy current, IPlayerRecipeData data, WorkstationTransaction workstation,
                                java.util.function.BooleanSupplier stationAndPolicyCurrent) {
        if (quoted == null || !quoted.ready() || workstation == null || data == null || current == null || stationAndPolicyCurrent == null) return failure(Status.INVALID_INPUT);
        ItemStack input, output, created = ItemStack.EMPTY;
        try {
            if (!stationAndPolicyCurrent.getAsBoolean()) return failure(Status.STALE);
            input = workstation.physicalInput().copy(); output = workstation.outputStack().copy();
            if (!quoted.equals(evaluate(input, output, data, current))) return failure(Status.STALE);
            if (quoted.createsBlueprint()) {
                var provenance = quoted.outputClass().provenance(BlueprintProvenance.Source.FRAGMENT_RECONSTRUCTION);
                created = workstation.createOutput(quoted.identity().target(), provenance);
                if (created == null || created.isEmpty() || created.getCount() != 1
                        || BlueprintItem.getBlueprintId(created).filter(quoted.identity().target()::equals).isEmpty()
                        || BlueprintItem.getProvenance(created).filter(provenance::equals).isEmpty()) return failure(Status.TRANSACTION_FAILED);
                created = created.copy();
            }
        } catch (RuntimeException exception) { return failure(Status.TRANSACTION_FAILED); }
        try { if (!stationAndPolicyCurrent.getAsBoolean()) return failure(Status.STALE); }
        catch (RuntimeException error) { return failure(Status.TRANSACTION_FAILED); }
        String target = quoted.identity().target().toString();
        try {
            var mutation = data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.COMMIT,
                    target, quoted.before(), quoted.after()));
            if (mutation != PlayerProgressValueMutation.Status.APPLIED && mutation != PlayerProgressValueMutation.Status.UNCHANGED) throw new IllegalStateException("analysis archive changed");
            if (!stationAndPolicyCurrent.getAsBoolean() || !workstation.consumePhysical(input, quoted.accepted())) throw new IllegalStateException("analysis input changed");
            if (quoted.createsBlueprint() && (!stationAndPolicyCurrent.getAsBoolean() || !workstation.placeOutput(created, output))) throw new IllegalStateException("analysis output changed");
            var expectedInput = input.copy(); expectedInput.shrink(quoted.accepted());
            if (!stationAndPolicyCurrent.getAsBoolean() || !ItemStack.matches(expectedInput, workstation.physicalInput())
                    || !ItemStack.matches(quoted.createsBlueprint() ? created : output, workstation.outputStack())
                    || !data.getArchivedAnalysisFragments().getOrDefault(target, AnalysisFragmentCounts.EMPTY).equals(quoted.after())) {
                throw new IllegalStateException("analysis commit did not retain its result");
            }
            return new Result(Status.SUCCESS, quoted.accepted(), quoted.createsBlueprint());
        } catch (RuntimeException exception) {
            boolean inventory = false, archive = false;
            try { inventory = workstation.restore(input, output)
                    && ItemStack.matches(input, workstation.physicalInput()) && ItemStack.matches(output, workstation.outputStack()); }
            catch (RuntimeException ignored) { }
            try {
                var actual = data.getArchivedAnalysisFragments().getOrDefault(target, AnalysisFragmentCounts.EMPTY);
                if (actual.equals(quoted.before())) archive = true;
                else if (actual.equals(quoted.after())) {
                    data.applyAnalysisFragmentMutation(new AnalysisFragmentMutation(PlayerProgressValueMutation.Operation.ROLLBACK, target, actual, quoted.before()));
                    archive = data.getArchivedAnalysisFragments().getOrDefault(target, AnalysisFragmentCounts.EMPTY).equals(quoted.before());
                }
            } catch (RuntimeException ignored) { }
            return failure(inventory && archive ? Status.TRANSACTION_FAILED : Status.ROLLBACK_FAILED);
        }
    }
    private static Result failure(Status status) { return new Result(status, 0, false); }
}
