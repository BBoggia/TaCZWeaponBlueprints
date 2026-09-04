package com.gamergaming.taczweaponblueprints.progression.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria;
import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria.ChangeOperation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;

import net.minecraft.resources.ResourceLocation;

class ProgressionCriterionServiceTest {
    private static final ResourceLocation TRIAL = new ResourceLocation("test:trial");

    @Test
    void futureWeaponTrialCanMixEventIncrementsWithIdempotentQualification() {
        PlayerRecipeData data = new PlayerRecipeData();
        for (int hit = 0; hit < 3; hit++) {
            var event = ProgressionCriterionService.prepare(
                    data, TRIAL, ChangeOperation.INCREMENT, 1);
            assertEquals(ProgressionCriteria.Status.APPLIED,
                    ProgressionCriterionService.commit(data, event).status());
        }

        var qualify = ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.GRANT, 3);
        assertEquals(ProgressionCriteria.Status.UNCHANGED,
                ProgressionCriterionService.commit(data, qualify).status());
        assertEquals(3, data.getProgressionCriteria().get(TRIAL.toString()));
    }

    @Test
    void grantIsIdempotentAndNeverReducesExistingProgress() {
        PlayerRecipeData data = new PlayerRecipeData();
        var first = ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.GRANT, 3);

        assertEquals(ProgressionCriteria.Status.READY,
                ProgressionCriterionService.preflight(data, first).status());
        assertEquals(ProgressionCriteria.Status.APPLIED,
                ProgressionCriterionService.commit(data, first).status());
        assertEquals(3, data.getProgressionCriteria().get("test:trial"));

        var repeated = ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.GRANT, 3);
        assertFalse(repeated.changed());
        assertEquals(ProgressionCriteria.Status.UNCHANGED,
                ProgressionCriterionService.commit(data, repeated).status());

        var lower = ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.GRANT, 1);
        assertEquals(3, lower.resultingValue());
        assertEquals(ProgressionCriteria.Status.UNCHANGED,
                ProgressionCriterionService.preflight(data, lower).status());
    }

    @Test
    void incrementsSaturateWithoutOverflowAndThenBecomeNoOps() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.replaceSupplementalProgression(
                Map.of(),
                Map.of("test:trial", ProgressionCriterionProgress.MAX_VALUE - 1)));

        var increment = ProgressionCriterionService.prepare(
                data,
                TRIAL,
                ChangeOperation.INCREMENT,
                ProgressionCriterionProgress.MAX_VALUE);
        assertEquals(ProgressionCriterionProgress.MAX_VALUE, increment.resultingValue());
        assertEquals(ProgressionCriteria.Status.APPLIED,
                ProgressionCriterionService.commit(data, increment).status());

        var saturated = ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.INCREMENT, 1);
        assertFalse(saturated.changed());
        assertEquals(ProgressionCriteria.Status.UNCHANGED,
                ProgressionCriterionService.commit(data, saturated).status());
    }

    @Test
    void administrativeClearRemovesOnlyTheRequestedCriterion() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.replaceSupplementalProgression(
                Map.of(),
                Map.of("test:trial", 4, "test:other", 2)));

        var clear = ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.ADMINISTRATIVE_CLEAR, 0);
        assertEquals(ProgressionCriteria.Status.APPLIED,
                ProgressionCriterionService.commit(data, clear).status());
        assertEquals(Map.of("test:other", 2), data.getProgressionCriteria());

        var repeated = ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.ADMINISTRATIVE_CLEAR, 0);
        assertEquals(ProgressionCriteria.Status.UNCHANGED,
                ProgressionCriterionService.commit(data, repeated).status());
    }

    @Test
    void staleAndCapacityFailuresDoNotMutateState() {
        PlayerRecipeData data = new PlayerRecipeData();
        var stale = ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.GRANT, 2);
        assertEquals(PlayerProgressValueMutation.Status.APPLIED,
                data.applyProgressionCriterionMutation(
                        PlayerProgressValueMutation.Request.commit("test:trial", 0, 1))
                        .status());
        assertEquals(ProgressionCriteria.Status.STALE,
                ProgressionCriterionService.commit(data, stale).status());
        assertEquals(1, data.getProgressionCriteria().get("test:trial"));

        LinkedHashMap<String, Integer> full = new LinkedHashMap<>();
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA;
                index++) {
            full.put("test:criterion_" + index, 1);
        }
        assertTrue(data.replaceSupplementalProgression(Map.of(), full));
        var overflow = ProgressionCriterionService.prepare(
                data,
                new ResourceLocation("test:overflow"),
                ChangeOperation.GRANT,
                1);
        assertEquals(ProgressionCriteria.Status.CAPACITY_REACHED,
                ProgressionCriterionService.preflight(data, overflow).status());
        assertFalse(data.getProgressionCriteria().containsKey("test:overflow"));
    }

    @Test
    void invalidOperationsAndAmountsAreRejectedBeforeMutation() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertThrows(IllegalArgumentException.class, () -> ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.GRANT, 0));
        assertThrows(IllegalArgumentException.class, () -> ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.INCREMENT, -1));
        assertThrows(IllegalArgumentException.class, () -> ProgressionCriterionService.prepare(
                data, TRIAL, ChangeOperation.ADMINISTRATIVE_CLEAR, 1));
        assertThrows(IllegalArgumentException.class, () -> ProgressionCriterionService.prepare(
                null, TRIAL, ChangeOperation.GRANT, 1));

        PlayerRecipeData malformed = new PlayerRecipeData() {
            @Override
            public Map<String, Integer> getProgressionCriteria() {
                Map<String, Integer> values = new HashMap<>();
                values.put("test:trial", null);
                return values;
            }
        };
        assertThrows(IllegalArgumentException.class, () -> ProgressionCriterionService.prepare(
                malformed, TRIAL, ChangeOperation.GRANT, 1));
    }
}
