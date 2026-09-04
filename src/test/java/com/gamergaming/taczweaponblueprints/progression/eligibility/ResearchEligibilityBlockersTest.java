package com.gamergaming.taczweaponblueprints.progression.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.Capacity;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.CapacityReason;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.Gate;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.Materials;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.Path;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.Policy;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.PolicyReason;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.ResearchPoints;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchEligibilityBlocker.WorkbenchTier;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition.Disclosure;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateScope;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.resources.ResourceLocation;

class ResearchEligibilityBlockersTest {
    private static final ResourceLocation SUBJECT = id("test:weapon");

    @Test
    void priorityIsStableAndRetainsEveryDetailedReason() {
        List<ResearchEligibilityBlocker> shuffled = new ArrayList<>(List.of(
                new Capacity(SUBJECT, CapacityReason.PROGRESSION_COLLECTION_FULL),
                new Materials(SUBJECT, 2, 7),
                new ResearchPoints(SUBJECT, 5, 10),
                gate(SUBJECT, ResearchInteractionMode.RESEARCH, 0),
                new WorkbenchTier(
                        SUBJECT,
                        ResearchInteractionMode.RESEARCH,
                        Optional.of(ResearchWorkbenchTier.TIER_1),
                        ResearchWorkbenchTier.TIER_2),
                new Path(SUBJECT, 2, 1),
                new Policy(SUBJECT, PolicyReason.DISCOVERY_REQUIRED)));
        Collections.reverse(shuffled);
        ResearchEligibilityBlockers blockers = new ResearchEligibilityBlockers(shuffled);

        assertFalse(blockers.eligible());
        assertInstanceOf(Policy.class, blockers.primary().orElseThrow());
        assertEquals(List.of(
                ResearchEligibilityBlocker.Kind.POLICY,
                ResearchEligibilityBlocker.Kind.PATH,
                ResearchEligibilityBlocker.Kind.WORKBENCH_TIER,
                ResearchEligibilityBlocker.Kind.PROGRESSION_GATE,
                ResearchEligibilityBlocker.Kind.RESEARCH_POINTS,
                ResearchEligibilityBlocker.Kind.MATERIALS,
                ResearchEligibilityBlocker.Kind.CAPACITY),
                blockers.all().stream().map(ResearchEligibilityBlocker::kind).toList());
        assertEquals(6, blockers.secondary().size());
        assertThrows(UnsupportedOperationException.class,
                () -> blockers.all().add(new Path(id("test:other"), 1, 0)));
        assertThrows(UnsupportedOperationException.class,
                () -> blockers.secondary().clear());
    }

    @Test
    void samePriorityTiesUseSubjectThenStableDetail() {
        ResearchPoints z = new ResearchPoints(id("test:z"), 1, 10);
        ResearchPoints aHigh = new ResearchPoints(id("test:a"), 1, 20);
        ResearchPoints aLow = new ResearchPoints(id("test:a"), 1, 10);
        ResearchPoints aSingleDigit = new ResearchPoints(id("test:a"), 1, 2);
        ResearchEligibilityBlockers blockers = new ResearchEligibilityBlockers(List.of(
                z,
                aHigh,
                aLow,
                aSingleDigit));

        assertEquals(List.of(aSingleDigit, aLow, aHigh, z), blockers.all());
        assertEquals(aSingleDigit, blockers.primary().orElseThrow());
    }

    @Test
    void emptyAndMaximumCollectionsAreValidButDuplicatesAndExcessAreRejected() {
        assertTrue(ResearchEligibilityBlockers.NONE.eligible());
        assertTrue(ResearchEligibilityBlockers.NONE.primary().isEmpty());
        assertTrue(ResearchEligibilityBlockers.NONE.secondary().isEmpty());

        List<ResearchEligibilityBlocker> maximum = IntStream
                .range(0, ResearchEligibilityBlockers.MAX_BLOCKERS)
                .mapToObj(index -> new ResearchPoints(id("test:weapon_" + index), 0, 1))
                .map(ResearchEligibilityBlocker.class::cast)
                .toList();
        assertEquals(
                ResearchEligibilityBlockers.MAX_BLOCKERS,
                new ResearchEligibilityBlockers(maximum).all().size());

        List<ResearchEligibilityBlocker> excessive = IntStream
                .range(0, ResearchEligibilityBlockers.MAX_BLOCKERS + 1)
                .mapToObj(index -> new ResearchPoints(id("test:excess_" + index), 0, 1))
                .map(ResearchEligibilityBlocker.class::cast)
                .toList();
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchEligibilityBlockers(excessive));
        ResearchPoints duplicate = new ResearchPoints(SUBJECT, 0, 1);
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchEligibilityBlockers(List.of(duplicate, duplicate)));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchEligibilityBlockers(null));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchEligibilityBlockers(Collections.singletonList(null)));
    }

    @Test
    void typedBlockersRejectStatesThatAreAlreadySatisfiedOrUnbounded() {
        assertThrows(IllegalArgumentException.class,
                () -> new Policy(null, PolicyReason.NOT_INCLUDED));
        assertThrows(IllegalArgumentException.class,
                () -> new Policy(SUBJECT, null));
        assertThrows(IllegalArgumentException.class,
                () -> new Path(SUBJECT, 0, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Path(SUBJECT, ResearchEligibilityBlocker.MAX_PATH_GROUPS + 1, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new Path(SUBJECT, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new WorkbenchTier(
                SUBJECT,
                ResearchInteractionMode.RESEARCH,
                Optional.of(ResearchWorkbenchTier.TIER_3),
                ResearchWorkbenchTier.TIER_2));
        assertThrows(IllegalArgumentException.class, () -> new WorkbenchTier(
                SUBJECT,
                null,
                Optional.empty(),
                ResearchWorkbenchTier.TIER_2));
        assertThrows(IllegalArgumentException.class, () -> new WorkbenchTier(
                SUBJECT,
                ResearchInteractionMode.RESEARCH,
                null,
                ResearchWorkbenchTier.TIER_2));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchPoints(SUBJECT, 10, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchPoints(
                        SUBJECT,
                        0,
                        PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Materials(SUBJECT, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Materials(SUBJECT, 2, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Materials(
                        SUBJECT,
                        ResearchEligibilityBlocker.MAX_MATERIAL_TYPES + 1,
                        ResearchEligibilityBlocker.MAX_MATERIAL_TYPES + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Materials(
                        SUBJECT,
                        1,
                        ResearchEligibilityBlocker.MAX_MATERIAL_UNITS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new Capacity(SUBJECT, null));
    }

    @Test
    void gateBlockerRequiresAnApplicableConditionAndBoundedGroupOrdinal() {
        ProgressionGateCondition researchOnly = new ProgressionGateCondition.Criterion(
                id("test:trial"),
                1,
                ProgressionGateScope.RESEARCH,
                "gate.test.trial",
                Disclosure.PUBLIC);

        assertThrows(IllegalArgumentException.class, () -> new Gate(
                SUBJECT,
                ResearchInteractionMode.CRAFTING,
                0,
                researchOnly));
        assertThrows(IllegalArgumentException.class, () -> new Gate(
                SUBJECT,
                ResearchInteractionMode.RESEARCH,
                ResearchEligibilityBlocker.MAX_GATE_GROUPS,
                researchOnly));
        assertThrows(IllegalArgumentException.class, () -> new Gate(
                SUBJECT,
                ResearchInteractionMode.RESEARCH,
                0,
                null));
        assertEquals(
                researchOnly,
                new Gate(SUBJECT, ResearchInteractionMode.RESEARCH, 0, researchOnly)
                        .condition());
    }

    @Test
    void missingWorkbenchFactoryRepresentsAbsenceWithoutInventingATier() {
        WorkbenchTier missing = WorkbenchTier.missing(
                SUBJECT,
                ResearchInteractionMode.CRAFTING,
                ResearchWorkbenchTier.TIER_1);

        assertTrue(missing.currentTier().isEmpty());
        assertEquals(ResearchWorkbenchTier.TIER_1, missing.requiredTier());
    }

    private static Gate gate(
            ResourceLocation subject,
            ResearchInteractionMode mode,
            int groupOrdinal) {
        return new Gate(
                subject,
                mode,
                groupOrdinal,
                new ProgressionGateCondition.Criterion(
                        id("test:trial"),
                        1,
                        ProgressionGateScope.BOTH,
                        "gate.test.trial",
                        Disclosure.PUBLIC));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
