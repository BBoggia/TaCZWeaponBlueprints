package com.gamergaming.taczweaponblueprints.api;

import java.util.Locale;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.api.event.ProgressionCriterionChangeEvent;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;
import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionCriterionProgress;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionCriterionService;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionCriterionService.PreparedChange;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;

/**
 * Stable server-only API for durable, player-scoped Progression Gate criteria.
 *
 * <p>Gameplay integrations, including a future Weapon Trials module, call this
 * only after authoritatively accepting an event on the owning server thread.
 * {@link Status#APPLIED} is a final commit and {@link Status#UNCHANGED}
 * is a successful idempotent no-op; callers must not infer blueprint learning
 * or Bench eligibility from the mutation result.</p>
 */
public final class ProgressionCriteria {
    private ProgressionCriteria() {
    }

    /** Returns the current value without changing player state. */
    public static Inspection inspect(ServerPlayer player, ResourceLocation criterionId) {
        Status authority = validatePlayer(player);
        if (authority != null) {
            return Inspection.failure(authority);
        }
        try {
            criterionId = ProgressionIds.require(
                    criterionId, "Progression Gate criterion ID");
        } catch (IllegalArgumentException exception) {
            return Inspection.failure(Status.INVALID_CRITERION);
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return Inspection.failure(Status.PLAYER_DATA_UNAVAILABLE);
        }
        var criteria = data.getProgressionCriteria();
        if (criteria == null
                || criteria.size() > PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA) {
            return Inspection.failure(Status.PLAYER_DATA_UNAVAILABLE);
        }
        String key = criterionId.toString();
        Integer saved = criteria.get(key);
        if ((saved == null && criteria.containsKey(key))
                || (saved != null && (saved <= 0
                        || saved > ProgressionCriterionProgress.MAX_VALUE))) {
            return Inspection.failure(Status.PLAYER_DATA_UNAVAILABLE);
        }
        int value = saved == null ? 0 : saved;
        return new Inspection(Status.INSPECTED, criterionId, value);
    }

    /** Idempotently raises a criterion to at least one. */
    public static ChangeResult grant(ServerPlayer player, ResourceLocation criterionId) {
        return grant(player, criterionId, 1);
    }

    /** Idempotently raises a criterion to at least {@code minimumValue}. */
    public static ChangeResult grant(
            ServerPlayer player,
            ResourceLocation criterionId,
            int minimumValue) {
        return change(player, criterionId, ChangeOperation.GRANT, minimumValue);
    }

    /** Adds positive progress, saturating at the hard player-progression limit. */
    public static ChangeResult increment(
            ServerPlayer player,
            ResourceLocation criterionId,
            int amount) {
        return change(player, criterionId, ChangeOperation.INCREMENT, amount);
    }

    /**
     * Clears one criterion. The administrative name is intentional: ordinary
     * gameplay integrations should grant progress, not revoke it.
     */
    public static ChangeResult clearAdministratively(
            ServerPlayer player,
            ResourceLocation criterionId) {
        return change(player, criterionId, ChangeOperation.ADMINISTRATIVE_CLEAR, 0);
    }

    /** Permission-checked command bridge for the destructive clear operation. */
    public static ChangeResult clearFromCommand(
            CommandSourceStack commandSource,
            ServerPlayer player,
            ResourceLocation criterionId) {
        boolean authorized = commandSource != null
                && commandSource.hasPermission(2)
                && player != null
                && commandSource.getServer() == player.server;
        return authorized
                ? clearAdministratively(player, criterionId)
                : ChangeResult.failure(Status.ADMIN_PERMISSION_REQUIRED);
    }

    private static ChangeResult change(
            ServerPlayer player,
            ResourceLocation criterionId,
            ChangeOperation operation,
            int operand) {
        Status authority = validatePlayer(player);
        if (authority != null) {
            return ChangeResult.failure(authority);
        }
        try {
            criterionId = ProgressionIds.require(
                    criterionId, "Progression Gate criterion ID");
        } catch (IllegalArgumentException exception) {
            return ChangeResult.failure(Status.INVALID_CRITERION);
        }
        if (operation == null || !operation.acceptsOperand(operand)) {
            return ChangeResult.failure(Status.INVALID_AMOUNT);
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return ChangeResult.failure(Status.PLAYER_DATA_UNAVAILABLE);
        }

        PreparedChange prepared;
        try {
            prepared = ProgressionCriterionService.prepare(
                    data, criterionId, operation, operand);
        } catch (IllegalArgumentException exception) {
            return ChangeResult.failure(Status.PLAYER_DATA_UNAVAILABLE);
        }
        ChangeResult preflight = ProgressionCriterionService.preflight(data, prepared);
        if (preflight.status() == Status.UNCHANGED || !preflight.successful()) {
            return preflight;
        }
        ProgressionCriterionChangeEvent.Pre pre = new ProgressionCriterionChangeEvent.Pre(
                player,
                criterionId,
                operation,
                operand,
                prepared.previousValue(),
                prepared.resultingValue());
        boolean cancelled;
        try {
            cancelled = MinecraftForge.EVENT_BUS.post(pre);
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Progression Gate criterion Pre event failed for {} and {}; no change was committed",
                    player.getGameProfile().getName(),
                    criterionId,
                    exception);
            return new ChangeResult(
                    Status.EVENT_ERROR,
                    criterionId,
                    operation,
                    operand,
                    prepared.previousValue(),
                    prepared.previousValue());
        }
        if (cancelled) {
            return new ChangeResult(
                    Status.CANCELLED,
                    criterionId,
                    operation,
                    operand,
                    prepared.previousValue(),
                    prepared.previousValue());
        }

        // Listeners run on the same server thread but may still change this
        // criterion. Recheck the exact advertised transition after the callback;
        // a listener-side mutation makes this request stale rather than silently
        // committing a different transition than the Pre event described.
        preflight = ProgressionCriterionService.preflight(data, prepared);
        if (!preflight.successful()) {
            return preflight;
        }
        ChangeResult committed = ProgressionCriterionService.commit(data, prepared);
        if (committed.status() == Status.APPLIED) {
            BlueprintProgressionSyncScheduler.markDirty(player);
            try {
                MinecraftForge.EVENT_BUS.post(new ProgressionCriterionChangeEvent.Post(
                        player,
                        criterionId,
                        operation,
                        operand,
                        committed.previousValue(),
                        committed.resultingValue()));
            } catch (RuntimeException exception) {
                // The player-data commit is final. Report the committed result so
                // a caller never retries an increment because an observer failed.
                TaCZWeaponBlueprints.LOGGER.error(
                        "Progression Gate criterion Post event failed for {} and {}; the committed value is {}",
                        player.getGameProfile().getName(),
                        criterionId,
                        committed.resultingValue(),
                        exception);
            }
        }
        return committed;
    }

    private static Status validatePlayer(ServerPlayer player) {
        if (player == null || player.server == null || player.level().isClientSide) {
            return Status.INVALID_PLAYER;
        }
        return player.server.isSameThread() ? null : Status.WRONG_THREAD;
    }

    public enum ChangeOperation {
        GRANT,
        INCREMENT,
        ADMINISTRATIVE_CLEAR;

        public boolean acceptsOperand(int operand) {
            return this == ADMINISTRATIVE_CLEAR
                    ? operand == 0
                    : operand >= 1 && operand <= ProgressionCriterionProgress.MAX_VALUE;
        }

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Status {
        INSPECTED,
        READY,
        APPLIED,
        UNCHANGED,
        CANCELLED,
        INVALID_PLAYER,
        WRONG_THREAD,
        INVALID_CRITERION,
        INVALID_AMOUNT,
        PLAYER_DATA_UNAVAILABLE,
        CAPACITY_REACHED,
        STALE,
        UNSUPPORTED,
        EVENT_ERROR,
        ADMIN_PERMISSION_REQUIRED;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Inspection(
            Status status,
            ResourceLocation criterionId,
            int value) {
        public Inspection {
            if (status == null
                    || value < 0
                    || value > ProgressionCriterionProgress.MAX_VALUE
                    || (status == Status.INSPECTED) != (criterionId != null)) {
                throw new IllegalArgumentException("invalid criterion inspection result");
            }
        }

        public boolean successful() {
            return status == Status.INSPECTED;
        }

        private static Inspection failure(Status status) {
            return new Inspection(status, null, 0);
        }
    }

    public record ChangeResult(
            Status status,
            ResourceLocation criterionId,
            ChangeOperation operation,
            int operand,
            int previousValue,
            int resultingValue) {
        public ChangeResult {
            if (status == null
                    || previousValue < 0
                    || previousValue > ProgressionCriterionProgress.MAX_VALUE
                    || resultingValue < 0
                    || resultingValue > ProgressionCriterionProgress.MAX_VALUE
                    || (criterionId == null) != (operation == null)) {
                throw new IllegalArgumentException("invalid criterion change result");
            }
            if (criterionId != null) {
                criterionId = ProgressionIds.require(
                        criterionId, "Progression Gate criterion ID");
            }
            if (operation != null && !operation.acceptsOperand(operand)) {
                throw new IllegalArgumentException("invalid criterion change operand");
            }
            if (status == Status.APPLIED && previousValue == resultingValue
                    || status == Status.UNCHANGED && previousValue != resultingValue
                    || status == Status.READY && previousValue == resultingValue) {
                throw new IllegalArgumentException("criterion change status and values disagree");
            }
        }

        public boolean successful() {
            return status == Status.READY
                    || status == Status.APPLIED
                    || status == Status.UNCHANGED;
        }

        public boolean changed() {
            return status == Status.APPLIED;
        }

        public static ChangeResult failure(Status status) {
            if (status == Status.INSPECTED || status == Status.READY
                    || status == Status.APPLIED || status == Status.UNCHANGED) {
                throw new IllegalArgumentException("successful status cannot describe a failure");
            }
            return new ChangeResult(status, null, null, 0, 0, 0);
        }
    }
}
