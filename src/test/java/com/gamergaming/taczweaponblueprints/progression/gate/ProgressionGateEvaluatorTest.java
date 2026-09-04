package com.gamergaming.taczweaponblueprints.progression.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Advancement;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Criterion;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Disclosure;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.WorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

class ProgressionGateEvaluatorTest {
    private static final ResourceLocation SUBJECT = id("test:rifle");
    private static final String MESSAGE = "gate.test.required";

    @Test
    void evaluatesAndOfOrAgainstCriteriaAdvancementsAndActiveBench() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.replaceSupplementalProgression(
                Map.of(), Map.of("test:trial", 1)));
        ProgressionGateRequirements requirements = new ProgressionGateRequirements(List.of(
                new ProgressionGateGroup(List.of(
                        criterion("test:trial", 2, Disclosure.PUBLIC),
                        advancement("test:milestone", Disclosure.PUBLIC))),
                new ProgressionGateGroup(List.of(new WorkbenchTier(
                        ResearchWorkbenchTier.TIER_2,
                        ProgressionGateScope.BOTH,
                        MESSAGE,
                        Disclosure.PUBLIC)))));

        ProgressionGateEvaluation blocked = ProgressionGateEvaluator.evaluate(
                SUBJECT,
                requirements,
                ResearchInteractionMode.RESEARCH,
                data,
                advancementId -> advancementId.equals(id("test:milestone")),
                Optional.of(context(ResearchWorkbenchTier.TIER_1)));
        assertFalse(blocked.satisfied());
        assertEquals(1, blocked.unmetGroups().size());
        assertEquals(ProgressionGateCondition.Type.WORKBENCH_TIER,
                blocked.primaryUnmetGroup().orElseThrow().alternatives().get(0).type());

        ProgressionGateEvaluation allowed = ProgressionGateEvaluator.evaluate(
                SUBJECT,
                requirements,
                ResearchInteractionMode.RESEARCH,
                data,
                ignored -> true,
                Optional.of(context(ResearchWorkbenchTier.TIER_3)));
        assertTrue(allowed.satisfied());
        assertTrue(allowed.unmetGroups().isEmpty());
    }

    @Test
    void returnsOnlyUnmetGroupsAndRedactsEveryHiddenDetail() {
        PlayerRecipeData data = new PlayerRecipeData();
        Criterion hidden = criterion("secret:weapon_trial", 9, Disclosure.HIDDEN);
        Criterion visible = criterion("test:public_trial", 3, Disclosure.PUBLIC);
        ProgressionGateRequirements requirements = new ProgressionGateRequirements(List.of(
                new ProgressionGateGroup(List.of(hidden, visible))));

        ProgressionGateEvaluation evaluation = ProgressionGateEvaluator.evaluate(
                SUBJECT,
                requirements,
                ResearchInteractionMode.RESEARCH,
                data,
                ignored -> false,
                Optional.empty());
        assertFalse(evaluation.satisfied());
        var alternatives = evaluation.unmetGroups().get(0).alternatives();
        var hiddenHint = alternatives.stream()
                .filter(hint -> hint.disclosure() == Disclosure.HIDDEN)
                .findFirst().orElseThrow();
        assertEquals(MESSAGE, hiddenHint.messageKey());
        assertTrue(hiddenHint.publicRequirementId().isEmpty());
        assertTrue(hiddenHint.currentValue().isEmpty());
        assertTrue(hiddenHint.requiredValue().isEmpty());
        assertTrue(hiddenHint.currentTier().isEmpty());
        assertTrue(hiddenHint.requiredTier().isEmpty());

        var publicHint = alternatives.stream()
                .filter(hint -> hint.disclosure() == Disclosure.PUBLIC)
                .findFirst().orElseThrow();
        assertEquals(id("test:public_trial"),
                publicHint.publicRequirementId().orElseThrow());
        assertEquals(0, publicHint.currentValue().orElseThrow());
        assertEquals(3, publicHint.requiredValue().orElseThrow());
    }

    @Test
    void inactiveScopesPerformNoAdvancementLookup() {
        AtomicInteger lookups = new AtomicInteger();
        ProgressionGateRequirements researchOnly = new ProgressionGateRequirements(List.of(
                new ProgressionGateGroup(List.of(new Advancement(
                        id("test:research_only"),
                        ProgressionGateScope.RESEARCH,
                        MESSAGE,
                        Disclosure.PUBLIC)))));

        ProgressionGateEvaluation crafting = ProgressionGateEvaluator.evaluate(
                SUBJECT,
                researchOnly,
                ResearchInteractionMode.CRAFTING,
                new PlayerRecipeData(),
                ignored -> {
                    lookups.incrementAndGet();
                    return false;
                },
                Optional.empty());
        assertTrue(crafting.satisfied());
        assertEquals(0, lookups.get());
    }

    @Test
    void duplicateAdvancementReferencesAreReadOnlyOncePerEvaluation() {
        AtomicInteger lookups = new AtomicInteger();
        Advancement advancement = advancement("test:shared", Disclosure.PUBLIC);
        ProgressionGateRequirements requirements = new ProgressionGateRequirements(List.of(
                new ProgressionGateGroup(List.of(advancement)),
                new ProgressionGateGroup(List.of(
                        advancement,
                        criterion("test:alternative", 1, Disclosure.PUBLIC)))));

        ProgressionGateEvaluation evaluation = ProgressionGateEvaluator.evaluate(
                SUBJECT,
                requirements,
                ResearchInteractionMode.RESEARCH,
                new PlayerRecipeData(),
                ignored -> {
                    lookups.incrementAndGet();
                    return true;
                },
                Optional.empty());
        assertTrue(evaluation.satisfied());
        assertEquals(1, lookups.get());
    }

    @Test
    void advancementLookupFailuresFailClosedWithoutExposingPolicyDetails() {
        ProgressionGateRequirements requirements = new ProgressionGateRequirements(List.of(
                new ProgressionGateGroup(List.of(
                        advancement("secret:broken", Disclosure.HIDDEN)))));
        ProgressionGateEvaluation evaluation = ProgressionGateEvaluator.evaluate(
                SUBJECT,
                requirements,
                ResearchInteractionMode.RESEARCH,
                new PlayerRecipeData(),
                ignored -> {
                    throw new IllegalStateException("unavailable");
                },
                Optional.empty());

        assertEquals(ProgressionGateEvaluation.Status.ADVANCEMENT_STATE_UNAVAILABLE,
                evaluation.status());
        assertTrue(evaluation.unmetGroups().isEmpty());
        assertTrue(evaluation.blocked());
    }

    @Test
    void malformedRelevantCriterionStateFailsClosed() {
        PlayerRecipeData malformed = new PlayerRecipeData() {
            @Override
            public Map<String, Integer> getProgressionCriteria() {
                Map<String, Integer> values = new HashMap<>();
                values.put("test:trial", null);
                return values;
            }
        };
        ProgressionGateRequirements requirements = new ProgressionGateRequirements(List.of(
                new ProgressionGateGroup(List.of(
                        criterion("test:trial", 1, Disclosure.PUBLIC)))));
        ProgressionGateEvaluation evaluation = ProgressionGateEvaluator.evaluate(
                SUBJECT,
                requirements,
                ResearchInteractionMode.RESEARCH,
                malformed,
                ignored -> false,
                Optional.empty());

        assertEquals(ProgressionGateEvaluation.Status.PLAYER_DATA_UNAVAILABLE,
                evaluation.status());
        assertTrue(evaluation.unmetGroups().isEmpty());
    }

    @Test
    void evaluationResultRejectsHiddenLeaksAndInconsistentStates() {
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateEvaluation.RequirementHint(
                        ProgressionGateCondition.Type.CRITERION,
                        MESSAGE,
                        Disclosure.HIDDEN,
                        Optional.of(id("secret:leak")),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionGateEvaluation.unavailable(
                        SUBJECT,
                        ResearchInteractionMode.RESEARCH,
                        ProgressionGateEvaluation.Status.EVALUATED));
    }

    private static Criterion criterion(
            String criterionId,
            int required,
            Disclosure disclosure) {
        return new Criterion(
                id(criterionId),
                required,
                ProgressionGateScope.BOTH,
                MESSAGE,
                disclosure);
    }

    private static Advancement advancement(String advancementId, Disclosure disclosure) {
        return new Advancement(
                id(advancementId),
                ProgressionGateScope.BOTH,
                MESSAGE,
                disclosure);
    }

    private static ResearchWorkbenchContext context(ResearchWorkbenchTier tier) {
        return new ResearchWorkbenchContext(
                BlockPos.ZERO,
                id("minecraft:overworld"),
                id("test:bench"),
                tier,
                ResearchInteractionMode.RESEARCH,
                1L);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
