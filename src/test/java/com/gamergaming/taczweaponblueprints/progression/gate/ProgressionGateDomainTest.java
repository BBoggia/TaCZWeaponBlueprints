package com.gamergaming.taczweaponblueprints.progression.gate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Advancement;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Criterion;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Disclosure;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.WorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

class ProgressionGateDomainTest {
    private static final String MESSAGE = "gate.test.required";

    @Test
    void scopeTruthTableIsExplicitForBothActions() {
        assertTrue(ProgressionGateScope.RESEARCH.appliesTo(ResearchInteractionMode.RESEARCH));
        assertFalse(ProgressionGateScope.RESEARCH.appliesTo(ResearchInteractionMode.CRAFTING));
        assertFalse(ProgressionGateScope.CRAFTING.appliesTo(ResearchInteractionMode.RESEARCH));
        assertTrue(ProgressionGateScope.CRAFTING.appliesTo(ResearchInteractionMode.CRAFTING));
        assertTrue(ProgressionGateScope.BOTH.appliesTo(ResearchInteractionMode.RESEARCH));
        assertTrue(ProgressionGateScope.BOTH.appliesTo(ResearchInteractionMode.CRAFTING));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionGateScope.BOTH.appliesTo(null));
    }

    @Test
    void criterionProgressCanonicalizesSatisfiesAndSaturatesWithoutOverflow() {
        ProgressionCriterionProgress progress = ProgressionCriterionProgress.of(
                " TEST:Weapon-Trial ",
                ProgressionCriterionProgress.MAX_VALUE - 1);

        assertEquals(id("test:weapon-trial"), progress.criterionId());
        assertTrue(progress.satisfies(ProgressionCriterionProgress.MAX_VALUE - 1));
        assertFalse(new ProgressionCriterionProgress(progress.criterionId(), 2).satisfies(3));
        assertEquals(
                ProgressionCriterionProgress.MAX_VALUE,
                progress.increment(Integer.MAX_VALUE).value());
        assertEquals(progress, progress.increment(0));

        assertThrows(IllegalArgumentException.class,
                () -> ProgressionCriterionProgress.of(null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionCriterionProgress(id("test:a"), -1));
        assertThrows(IllegalArgumentException.class,
                () -> progress.satisfies(0));
        assertThrows(IllegalArgumentException.class,
                () -> progress.increment(-1));
    }

    @Test
    void typedConditionsUseCanonicalIdsMessagesAndEvidence() {
        Criterion criterion = Criterion.of(
                " TEST:Trial ",
                3,
                ProgressionGateScope.BOTH,
                " GATE.TEST.TRIAL ",
                Disclosure.PUBLIC);
        Advancement advancement = Advancement.of(
                " MINECRAFT:Story/Smelt_Iron ",
                ProgressionGateScope.RESEARCH,
                MESSAGE,
                Disclosure.HIDDEN);
        WorkbenchTier bench = new WorkbenchTier(
                ResearchWorkbenchTier.TIER_2,
                ProgressionGateScope.BOTH,
                MESSAGE,
                Disclosure.PUBLIC);
        ProgressionGateEvidence evidence = evidence(
                List.of(ProgressionCriterionProgress.of("test:trial", 3)),
                Set.of(id("minecraft:story/smelt_iron")),
                ResearchWorkbenchTier.TIER_3,
                ResearchInteractionMode.RESEARCH);

        assertEquals(id("test:trial"), criterion.criterionId());
        assertEquals("gate.test.trial", criterion.messageKey());
        assertTrue(criterion.satisfiedBy(ResearchInteractionMode.RESEARCH, evidence));
        assertTrue(advancement.satisfiedBy(ResearchInteractionMode.RESEARCH, evidence));
        assertTrue(bench.satisfiedBy(ResearchInteractionMode.RESEARCH, evidence));
        assertTrue(advancement.satisfiedBy(
                ResearchInteractionMode.CRAFTING,
                ProgressionGateEvidence.EMPTY),
                "a condition outside the current action imposes no requirement");
        assertFalse(bench.satisfiedBy(
                ResearchInteractionMode.CRAFTING,
                evidence),
                "a research session cannot authorize a crafting gate");
        assertTrue(criterion.canonicalKey().contains("test:trial"));
        assertFalse(advancement.canonicalKey().contains(advancement.messageKey()));
    }

    @Test
    void conditionConstructorsRejectNullsInvalidIdsAndInvalidThresholds() {
        assertThrows(IllegalArgumentException.class, () -> new Criterion(
                null, 1, ProgressionGateScope.BOTH, MESSAGE, Disclosure.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new Criterion(
                id("test:a"), 0, ProgressionGateScope.BOTH, MESSAGE, Disclosure.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new Criterion(
                id("test:a"), 1, null, MESSAGE, Disclosure.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new Criterion(
                id("test:a"), 1, ProgressionGateScope.BOTH, "not valid", Disclosure.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new Criterion(
                id("test:a"), 1, ProgressionGateScope.BOTH, MESSAGE, null));
        assertThrows(IllegalArgumentException.class, () -> new Advancement(
                null, ProgressionGateScope.BOTH, MESSAGE, Disclosure.PUBLIC));
        assertThrows(IllegalArgumentException.class, () -> new WorkbenchTier(
                null, ProgressionGateScope.BOTH, MESSAGE, Disclosure.PUBLIC));
        assertThrows(IllegalArgumentException.class,
                () -> condition("test:a").satisfiedBy(ResearchInteractionMode.RESEARCH, null));
        assertThrows(IllegalArgumentException.class,
                () -> condition("test:a").requirementSatisfiedBy(null));
    }

    @Test
    void gateGroupsUseOrWhileThePolicyUsesAnd() {
        ProgressionGateGroup eitherTrialOrAdvancement = new ProgressionGateGroup(List.of(
                condition("test:trial"),
                advancement("test:milestone")));
        ProgressionGateGroup requiredBench = new ProgressionGateGroup(List.of(
                bench(ResearchWorkbenchTier.TIER_2)));
        ProgressionGateRequirements requirements = new ProgressionGateRequirements(List.of(
                requiredBench,
                eitherTrialOrAdvancement));

        ProgressionGateEvidence none = evidence(
                List.of(), Set.of(), ResearchWorkbenchTier.TIER_1,
                ResearchInteractionMode.RESEARCH);
        ProgressionGateEvidence trialOnly = evidence(
                List.of(ProgressionCriterionProgress.of("test:trial", 1)),
                Set.of(), ResearchWorkbenchTier.TIER_1,
                ResearchInteractionMode.RESEARCH);
        ProgressionGateEvidence complete = evidence(
                List.of(ProgressionCriterionProgress.of("test:trial", 1)),
                Set.of(), ResearchWorkbenchTier.TIER_3,
                ResearchInteractionMode.RESEARCH);

        assertFalse(eitherTrialOrAdvancement.evaluate(
                ResearchInteractionMode.RESEARCH, none).satisfied());
        assertTrue(eitherTrialOrAdvancement.evaluate(
                ResearchInteractionMode.RESEARCH, trialOnly).satisfied());
        assertFalse(requirements.satisfiedBy(ResearchInteractionMode.RESEARCH, trialOnly));
        assertTrue(requirements.satisfiedBy(ResearchInteractionMode.RESEARCH, complete));
        assertEquals(3, requirements.conditionCount());
        assertEquals(1, requirements.evaluate(
                ResearchInteractionMode.RESEARCH, trialOnly).unmetGroups().size());
    }

    @Test
    void actionSpecificGroupsAreInactiveRatherThanImpossible() {
        ProgressionGateGroup researchOnly = new ProgressionGateGroup(List.of(new Criterion(
                id("test:research"),
                1,
                ProgressionGateScope.RESEARCH,
                MESSAGE,
                Disclosure.PUBLIC)));
        ProgressionGateGroup.Evaluation crafting = researchOnly.evaluate(
                ResearchInteractionMode.CRAFTING,
                ProgressionGateEvidence.EMPTY);

        assertFalse(crafting.applies());
        assertTrue(crafting.satisfied());
        assertTrue(new ProgressionGateRequirements(List.of(researchOnly)).satisfiedBy(
                ResearchInteractionMode.CRAFTING,
                ProgressionGateEvidence.EMPTY));
    }

    @Test
    void groupsAndPoliciesAreCanonicalImmutableAndRejectDuplicates() {
        ProgressionGateCondition a = condition("test:a");
        ProgressionGateCondition b = condition("test:b");
        ProgressionGateGroup group = new ProgressionGateGroup(List.of(b, a));

        assertEquals(List.of(a, b), group.anyOf());
        assertThrows(UnsupportedOperationException.class, () -> group.anyOf().add(a));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateGroup(List.of()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateGroup(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateGroup(List.of(a, a)));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateGroup(conditions(
                        "oversized",
                        ProgressionGateGroup.MAX_ALTERNATIVES + 1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateRequirements(List.of(group, group)));

        ProgressionGateRequirements canonical = new ProgressionGateRequirements(List.of(
                new ProgressionGateGroup(List.of(condition("test:z"))),
                group));
        assertEquals(group, canonical.allOf().get(0));
        assertThrows(UnsupportedOperationException.class,
                () -> canonical.allOf().add(group));
    }

    @Test
    void maximumCountsAreAcceptedAndEveryExcessShapeIsRejected() {
        List<ProgressionGateGroup> maximum = IntStream.range(0, 4)
                .mapToObj(group -> new ProgressionGateGroup(
                        conditions("g" + group, ProgressionGateGroup.MAX_ALTERNATIVES)))
                .toList();
        assertEquals(
                ProgressionGateRequirements.MAX_TOTAL_CONDITIONS,
                new ProgressionGateRequirements(maximum).conditionCount());

        List<ProgressionGateGroup> tooManyConditions = IntStream.range(0, 5)
                .mapToObj(group -> new ProgressionGateGroup(
                        conditions("x" + group, ProgressionGateGroup.MAX_ALTERNATIVES)))
                .toList();
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateRequirements(tooManyConditions));

        List<ProgressionGateGroup> tooManyGroups = IntStream
                .range(0, ProgressionGateRequirements.MAX_GROUPS + 1)
                .mapToObj(group -> new ProgressionGateGroup(List.of(
                        condition("test:group_" + group))))
                .toList();
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateRequirements(tooManyGroups));

        ProgressionGateGroup.Evaluation oneCondition = new ProgressionGateGroup(
                List.of(condition("test:evaluation")))
                .evaluate(ResearchInteractionMode.RESEARCH, ProgressionGateEvidence.EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateRequirements.Evaluation(
                        ResearchInteractionMode.RESEARCH,
                        Collections.nCopies(
                                ProgressionGateRequirements.MAX_GROUPS + 1,
                                oneCondition)));

        ProgressionGateGroup.Evaluation fullGroup = maximum.get(0).evaluate(
                ResearchInteractionMode.RESEARCH,
                ProgressionGateEvidence.EMPTY);
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateRequirements.Evaluation(
                        ResearchInteractionMode.RESEARCH,
                        Collections.nCopies(5, fullGroup)));
    }

    @Test
    void evidenceIsCanonicalBoundedAndRejectsAmbiguousDuplicates() {
        ProgressionGateEvidence evidence = new ProgressionGateEvidence(
                List.of(
                        ProgressionCriterionProgress.of("test:z", 2),
                        ProgressionCriterionProgress.of("test:a", 1)),
                List.of(id("test:z"), id("test:a")),
                Optional.empty());

        assertEquals(id("test:a"), evidence.criteria().get(0).criterionId());
        assertEquals(List.of(id("test:a"), id("test:z")),
                new ArrayList<>(evidence.completedAdvancements()));
        assertEquals(0, evidence.criterionValue(id("test:missing")));
        assertThrows(UnsupportedOperationException.class,
                () -> evidence.criteria().add(ProgressionCriterionProgress.of("test:b", 1)));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionGateEvidence(
                List.of(
                        ProgressionCriterionProgress.of("test:a", 1),
                        ProgressionCriterionProgress.of("test:a", 2)),
                Set.of(),
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionGateEvidence(
                List.of(),
                List.of(id("test:a"), id("test:a")),
                Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionGateEvidence(null, Set.of(), Optional.empty()));

        List<ProgressionCriterionProgress> oversized = Collections.nCopies(
                PlayerProgressionLimits.MAX_IDS_PER_COLLECTION + 1,
                ProgressionCriterionProgress.of("test:a", 1));
        assertThrows(IllegalArgumentException.class, () -> new ProgressionGateEvidence(
                oversized,
                Set.of(),
                Optional.empty()));
    }

    private static Criterion condition(String id) {
        return new Criterion(
                id(id),
                1,
                ProgressionGateScope.BOTH,
                MESSAGE,
                Disclosure.PUBLIC);
    }

    private static Advancement advancement(String id) {
        return new Advancement(
                id(id),
                ProgressionGateScope.BOTH,
                MESSAGE,
                Disclosure.PUBLIC);
    }

    private static WorkbenchTier bench(ResearchWorkbenchTier tier) {
        return new WorkbenchTier(
                tier,
                ProgressionGateScope.BOTH,
                MESSAGE,
                Disclosure.PUBLIC);
    }

    private static List<ProgressionGateCondition> conditions(String prefix, int count) {
        return IntStream.range(0, count)
                .mapToObj(index -> condition("test:" + prefix + "_" + index))
                .map(ProgressionGateCondition.class::cast)
                .toList();
    }

    private static ProgressionGateEvidence evidence(
            List<ProgressionCriterionProgress> criteria,
            Set<ResourceLocation> advancements,
            ResearchWorkbenchTier tier,
            ResearchInteractionMode mode) {
        ResearchWorkbenchContext context = new ResearchWorkbenchContext(
                BlockPos.ZERO,
                id("test:dimension"),
                id("test:bench"),
                tier,
                mode,
                1L);
        return new ProgressionGateEvidence(criteria, advancements, Optional.of(context));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
