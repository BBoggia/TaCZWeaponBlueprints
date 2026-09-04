package com.gamergaming.taczweaponblueprints.progression.gate;

import java.util.Map;

import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria;
import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria.ChangeOperation;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;

import net.minecraft.resources.ResourceLocation;

/** Pure compare-and-set preparation and commit logic behind the public API. */
public final class ProgressionCriterionService {
    private ProgressionCriterionService() {
    }

    public static PreparedChange prepare(
            IPlayerRecipeData playerData,
            ResourceLocation criterionId,
            ChangeOperation operation,
            int operand) {
        criterionId = ProgressionIds.require(criterionId, "Progression Gate criterion ID");
        if (playerData == null || operation == null) {
            throw new IllegalArgumentException("criterion change inputs cannot be null");
        }
        if (!operation.acceptsOperand(operand)) {
            throw new IllegalArgumentException("criterion change amount is out of bounds");
        }
        Map<String, Integer> criteria = playerData.getProgressionCriteria();
        if (criteria == null
                || criteria.size() > PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA) {
            throw new IllegalArgumentException("criterion progress map is invalid");
        }
        String key = criterionId.toString();
        Integer saved = criteria.get(key);
        if ((saved == null && criteria.containsKey(key))
                || (saved != null && (saved <= 0
                        || saved > ProgressionCriterionProgress.MAX_VALUE))) {
            throw new IllegalArgumentException("saved criterion progress is out of bounds");
        }
        int previous = saved == null ? 0 : saved;
        int resulting = switch (operation) {
            case GRANT -> Math.max(previous, operand);
            case INCREMENT -> (int) Math.min(
                    (long) ProgressionCriterionProgress.MAX_VALUE,
                    (long) previous + (long) operand);
            case ADMINISTRATIVE_CLEAR -> 0;
        };
        return new PreparedChange(criterionId, operation, operand, previous, resulting);
    }

    public static ProgressionCriteria.ChangeResult preflight(
            IPlayerRecipeData playerData,
            PreparedChange prepared) {
        if (playerData == null || prepared == null) {
            return ProgressionCriteria.ChangeResult.failure(
                    ProgressionCriteria.Status.PLAYER_DATA_UNAVAILABLE);
        }
        if (!prepared.changed()) {
            return prepared.result(ProgressionCriteria.Status.UNCHANGED);
        }
        PlayerProgressValueMutation.Result result = playerData
                .applyProgressionCriterionMutation(PlayerProgressValueMutation.Request.preflight(
                        prepared.criterionId().toString(),
                        prepared.previousValue(),
                        prepared.resultingValue()));
        return map(prepared, result);
    }

    public static ProgressionCriteria.ChangeResult commit(
            IPlayerRecipeData playerData,
            PreparedChange prepared) {
        if (playerData == null || prepared == null) {
            return ProgressionCriteria.ChangeResult.failure(
                    ProgressionCriteria.Status.PLAYER_DATA_UNAVAILABLE);
        }
        if (!prepared.changed()) {
            return prepared.result(ProgressionCriteria.Status.UNCHANGED);
        }
        PlayerProgressValueMutation.Result result = playerData
                .applyProgressionCriterionMutation(PlayerProgressValueMutation.Request.commit(
                        prepared.criterionId().toString(),
                        prepared.previousValue(),
                        prepared.resultingValue()));
        return map(prepared, result);
    }

    private static ProgressionCriteria.ChangeResult map(
            PreparedChange prepared,
            PlayerProgressValueMutation.Result result) {
        if (result == null) {
            return ProgressionCriteria.ChangeResult.failure(
                    ProgressionCriteria.Status.PLAYER_DATA_UNAVAILABLE);
        }
        ProgressionCriteria.Status status = switch (result.status()) {
            case READY -> ProgressionCriteria.Status.READY;
            case APPLIED -> ProgressionCriteria.Status.APPLIED;
            case UNCHANGED -> ProgressionCriteria.Status.UNCHANGED;
            case STALE -> ProgressionCriteria.Status.STALE;
            case INVALID_IDENTITY -> ProgressionCriteria.Status.INVALID_CRITERION;
            case CAPACITY_REACHED -> ProgressionCriteria.Status.CAPACITY_REACHED;
            case UNSUPPORTED -> ProgressionCriteria.Status.UNSUPPORTED;
            case ROLLED_BACK -> ProgressionCriteria.Status.STALE;
        };
        if (status == ProgressionCriteria.Status.STALE) {
            return new ProgressionCriteria.ChangeResult(
                    status,
                    prepared.criterionId(),
                    prepared.operation(),
                    prepared.operand(),
                    result.previousValue(),
                    result.previousValue());
        }
        return prepared.result(status);
    }

    public record PreparedChange(
            ResourceLocation criterionId,
            ChangeOperation operation,
            int operand,
            int previousValue,
            int resultingValue) {
        public PreparedChange {
            criterionId = ProgressionIds.require(criterionId, "Progression Gate criterion ID");
            if (operation == null
                    || !operation.acceptsOperand(operand)
                    || previousValue < 0
                    || previousValue > ProgressionCriterionProgress.MAX_VALUE
                    || resultingValue < 0
                    || resultingValue > ProgressionCriterionProgress.MAX_VALUE) {
                throw new IllegalArgumentException("prepared criterion change is invalid");
            }
            if (operation == ChangeOperation.GRANT && resultingValue < previousValue
                    || operation == ChangeOperation.INCREMENT && resultingValue < previousValue
                    || operation == ChangeOperation.ADMINISTRATIVE_CLEAR && resultingValue != 0) {
                throw new IllegalArgumentException("prepared criterion transition is inconsistent");
            }
        }

        public boolean changed() {
            return previousValue != resultingValue;
        }

        ProgressionCriteria.ChangeResult result(ProgressionCriteria.Status status) {
            return new ProgressionCriteria.ChangeResult(
                    status,
                    criterionId,
                    operation,
                    operand,
                    previousValue,
                    resultingValue);
        }
    }
}
