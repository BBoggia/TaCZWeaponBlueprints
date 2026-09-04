package com.gamergaming.taczweaponblueprints.api.event;

import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria.ChangeOperation;
import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionCriterionProgress;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Cancelable;
import net.minecraftforge.eventbus.api.Event;

/** Server-only observation boundary around one custom criterion transition. */
public abstract class ProgressionCriterionChangeEvent extends Event {
    private final ServerPlayer player;
    private final ResourceLocation criterionId;
    private final ChangeOperation operation;
    private final int operand;
    private final int previousValue;
    private final int resultingValue;

    protected ProgressionCriterionChangeEvent(
            ServerPlayer player,
            ResourceLocation criterionId,
            ChangeOperation operation,
            int operand,
            int previousValue,
            int resultingValue) {
        if (player == null || operation == null || !operation.acceptsOperand(operand)
                || previousValue < 0
                || previousValue > ProgressionCriterionProgress.MAX_VALUE
                || resultingValue < 0
                || resultingValue > ProgressionCriterionProgress.MAX_VALUE
                || previousValue == resultingValue) {
            throw new IllegalArgumentException("invalid Progression Gate criterion event");
        }
        if (operation == ChangeOperation.GRANT
                        && resultingValue < Math.max(previousValue, operand)
                || operation == ChangeOperation.INCREMENT
                        && resultingValue <= previousValue
                || operation == ChangeOperation.ADMINISTRATIVE_CLEAR
                        && resultingValue != 0) {
            throw new IllegalArgumentException(
                    "Progression Gate criterion event transition is inconsistent");
        }
        this.player = player;
        this.criterionId = ProgressionIds.require(
                criterionId, "Progression Gate criterion ID");
        this.operation = operation;
        this.operand = operand;
        this.previousValue = previousValue;
        this.resultingValue = resultingValue;
    }

    public final ServerPlayer getPlayer() {
        return player;
    }

    public final ResourceLocation getCriterionId() {
        return criterionId;
    }

    public final ChangeOperation getOperation() {
        return operation;
    }

    /** Minimum grant value, increment amount, or zero for an administrative clear. */
    public final int getOperand() {
        return operand;
    }

    public final int getPreviousValue() {
        return previousValue;
    }

    public final int getResultingValue() {
        return resultingValue;
    }

    /** Fired after preflight and before any player data changes. */
    @Cancelable
    public static final class Pre extends ProgressionCriterionChangeEvent {
        public Pre(
                ServerPlayer player,
                ResourceLocation criterionId,
                ChangeOperation operation,
                int operand,
                int previousValue,
                int resultingValue) {
            super(player, criterionId, operation, operand, previousValue, resultingValue);
        }
    }

    /** Fired only after the exact transition commits. */
    public static final class Post extends ProgressionCriterionChangeEvent {
        public Post(
                ServerPlayer player,
                ResourceLocation criterionId,
                ChangeOperation operation,
                int operand,
                int previousValue,
                int resultingValue) {
            super(player, criterionId, operation, operand, previousValue, resultingValue);
        }
    }
}
