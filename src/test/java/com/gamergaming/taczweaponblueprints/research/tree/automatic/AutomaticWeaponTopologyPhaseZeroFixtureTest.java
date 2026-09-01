package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchGroupedRouteBaselineAudit;

class AutomaticWeaponTopologyPhaseZeroFixtureTest {
    @Test
    void currentTopologyBaselinesCoverRepresentativeCatalogSizes() {
        List<AutomaticWeaponTopologyPhaseZeroFixture.Baseline> actual = List.of(
                AutomaticWeaponTopologyPhaseZeroFixture.baseline(
                        AutomaticWeaponTopologyPhaseZeroFixture.small()),
                AutomaticWeaponTopologyPhaseZeroFixture.baseline(
                        AutomaticWeaponTopologyPhaseZeroFixture.medium()),
                AutomaticWeaponTopologyPhaseZeroFixture.baseline(
                        AutomaticWeaponTopologyPhaseZeroFixture.largeAddon()));

        assertEquals(List.of(
                new AutomaticWeaponTopologyPhaseZeroFixture.Baseline(
                        "small",
                        List.of(2, 5),
                        2,
                        10,
                        2,
                        1,
                        7,
                        2,
                        5,
                        1,
                        5,
                        5,
                        10,
                        10,
                        5,
                        16,
                        24,
                        256,
                        108,
                        5,
                        "6073d1b719f8c8ce3036857a3152fa387212f300502e090bedc156f90b2d4e0b"),
                new AutomaticWeaponTopologyPhaseZeroFixture.Baseline(
                        "medium",
                        List.of(2, 9, 9, 9, 9, 9, 9, 9, 9),
                        2,
                        99,
                        2,
                        1,
                        74,
                        2,
                        9,
                        8,
                        27,
                        27,
                        66,
                        99,
                        9,
                        72,
                        104,
                        448,
                        416,
                        9,
                        "3677f18730cfb6e1f4cd208badc22f389daa94dd4139e1807d9f53981b0dab02"),
                new AutomaticWeaponTopologyPhaseZeroFixture.Baseline(
                        "large_addon",
                        List.of(2, 20, 20, 20, 20, 20, 20, 20,
                                20, 20, 20, 20, 20, 20, 20, 5),
                        2,
                        405,
                        2,
                        1,
                        287,
                        2,
                        20,
                        15,
                        120,
                        120,
                        375,
                        405,
                        20,
                        128,
                        256,
                        1060,
                        724,
                        20,
                        "09943a343c0ac8c44abbe30981be6b5e2d6b1e98ebc1e7a556b1a41af69b2997")),
                actual);
        assertTrue(actual.stream().allMatch(value ->
                value.componentCount() == 1
                        && value.reachableNodeCount()
                                == value.rankWidths().stream().mapToInt(Integer::intValue).sum()
                        && value.maximumPrerequisiteCount() <= 2
                        && value.maximumVisualRowPopulation()
                                <= value.rankWidths().stream().mapToInt(Integer::intValue)
                                        .max().orElseThrow()));
    }

    @Test
    void currentTopologyIsIndependentOfCandidateIterationOrder() {
        for (AutomaticWeaponTopologyPhaseZeroFixture.Scenario scenario : List.of(
                AutomaticWeaponTopologyPhaseZeroFixture.small(),
                AutomaticWeaponTopologyPhaseZeroFixture.medium(),
                AutomaticWeaponTopologyPhaseZeroFixture.largeAddon())) {
            assertEquals(
                    AutomaticWeaponTopologyPhaseZeroFixture.baseline(scenario),
                    AutomaticWeaponTopologyPhaseZeroFixture.baseline(scenario, true));
        }
    }

    @Test
    void maximumLayeringPopulationRemainsBoundedAndDeterministic() {
        var forward = AutomaticWeaponTopologyPhaseZeroFixture.maximumLayerBaseline(false);
        var reverse = AutomaticWeaponTopologyPhaseZeroFixture.maximumLayerBaseline(true);

        assertEquals(forward, reverse);
        assertEquals(new AutomaticWeaponTopologyPhaseZeroFixture.LayerBaseline(
                4096,
                206,
                20,
                14,
                "f9cebd10489f3577af98cb83439df48467c8eddf7f498f45d55f6321dae882c5"),
                forward);
    }

    @Test
    void groupedRouteCounterfactualIsCharacterizedAtRepresentativeScales() {
        List<AutomaticWeaponTopologyPhaseZeroFixture.GroupedRouteBaseline> actual = List.of(
                AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteBaseline(
                        AutomaticWeaponTopologyPhaseZeroFixture.packagedScale()),
                AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteBaseline(
                        AutomaticWeaponTopologyPhaseZeroFixture.largeAddon()));

        assertEquals(List.of(
                new AutomaticWeaponTopologyPhaseZeroFixture.GroupedRouteBaseline(
                        "packaged_scale",
                        53,
                        68,
                        34,
                        17,
                        17,
                        0,
                        2,
                        4,
                        new ResearchGroupedRouteBaselineAudit.IntDistribution(
                                40, 1, 1, 3, 3, 7),
                        new ResearchGroupedRouteBaselineAudit.AlternativeEvidence(
                                17,
                                0,
                                new ResearchGroupedRouteBaselineAudit.IntDistribution(
                                        17, 0, 2_000, 3_333, 4_000, 4_000),
                                new ResearchGroupedRouteBaselineAudit.IntDistribution(
                                        17, 6_000, 8_000, 10_000, 10_000, 10_000),
                                new ResearchGroupedRouteBaselineAudit.IntDistribution(
                                        17, 4_286, 7_143, 10_000, 10_000, 10_000)),
                        new ResearchGroupedRouteBaselineAudit.RouteCostComparison(
                                13,
                                128,
                                new ResearchGroupedRouteBaselineAudit.LongDistribution(
                                        13, 32, 56, 88, 96, 96),
                                new ResearchGroupedRouteBaselineAudit.LongDistribution(
                                        13, 24, 32, 48, 48, 48),
                                new ResearchGroupedRouteBaselineAudit.LongDistribution(
                                        13, 24, 40, 48, 56, 56),
                                13,
                                13,
                                true),
                        "78ecab68f806c1af6262c48ba9a206534a9455ebe37a37a8b83788c59e3ad857"),
                new AutomaticWeaponTopologyPhaseZeroFixture.GroupedRouteBaseline(
                        "large_addon",
                        287,
                        459,
                        111,
                        174,
                        174,
                        0,
                        2,
                        6,
                        new ResearchGroupedRouteBaselineAudit.IntDistribution(
                                260, 1, 1, 3, 3, 10),
                        new ResearchGroupedRouteBaselineAudit.AlternativeEvidence(
                                174,
                                0,
                                new ResearchGroupedRouteBaselineAudit.IntDistribution(
                                        174, 3_333, 5_556, 9_000, 9_231, 9_375),
                                new ResearchGroupedRouteBaselineAudit.IntDistribution(
                                        174, 625, 4_444, 6_667, 6_667, 6_667),
                                new ResearchGroupedRouteBaselineAudit.IntDistribution(
                                        174, 6_786, 10_000, 10_000, 10_000, 10_000)),
                        new ResearchGroupedRouteBaselineAudit.RouteCostComparison(
                                27,
                                128,
                                new ResearchGroupedRouteBaselineAudit.LongDistribution(
                                        27, 104, 312, 360, 368, 368),
                                new ResearchGroupedRouteBaselineAudit.LongDistribution(
                                        27, 48, 112, 120, 120, 120),
                                new ResearchGroupedRouteBaselineAudit.LongDistribution(
                                        27, 48, 112, 120, 128, 128),
                                6,
                                27,
                                true),
                        "052e3b1daeb3c539f4715ba6fba28171448f78eb14f2b2aeeebf744f646ddf62")),
                actual);
    }

    @Test
    void groupedRouteCounterfactualIsIndependentOfCandidateIterationOrder() {
        for (AutomaticWeaponTopologyPhaseZeroFixture.Scenario scenario : List.of(
                AutomaticWeaponTopologyPhaseZeroFixture.packagedScale(),
                AutomaticWeaponTopologyPhaseZeroFixture.largeAddon())) {
            assertEquals(
                    AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteBaseline(scenario),
                    AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteBaseline(
                            scenario, true));
            assertEquals(
                    AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteAudit(
                            scenario, false),
                    AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteAudit(
                            scenario, true));
        }
    }

    @Test
    void groupedRoutePhaseAndBranchEvidenceCoversEveryAutomaticDecision() {
        var audit = AutomaticWeaponTopologyPhaseZeroFixture.groupedRouteAudit(
                AutomaticWeaponTopologyPhaseZeroFixture.largeAddon(), false);

        assertEquals(
                List.of(2, 111, 51, 69, 54),
                audit.strategies().stream()
                        .map(ResearchGroupedRouteBaselineAudit.StrategySummary::targetCount)
                        .toList());
        assertEquals(
                List.of(0, 202, 102, 90, 65),
                audit.strategies().stream()
                        .map(ResearchGroupedRouteBaselineAudit.StrategySummary
                                ::parentReferenceCount)
                        .toList());
        assertEquals(
                List.of(0, 91, 51, 21, 11),
                audit.strategies().stream()
                        .map(ResearchGroupedRouteBaselineAudit.StrategySummary
                                ::multiParentTargetCount)
                        .toList());
        assertEquals(
                List.of(0, 90, 68, 90, 65),
                audit.strategies().stream()
                        .map(ResearchGroupedRouteBaselineAudit.StrategySummary
                                ::sameFamilyReferenceCount)
                        .toList());
        assertEquals(
                List.of(0, 112, 34, 0, 0),
                audit.strategies().stream()
                        .map(ResearchGroupedRouteBaselineAudit.StrategySummary
                                ::crossFamilyReferenceCount)
                        .toList());
        assertEquals(
                List.of(0, 68, 26, 9, 7),
                audit.strategies().stream()
                        .map(ResearchGroupedRouteBaselineAudit.StrategySummary
                                ::closureInflationRejectionCount)
                        .toList());
        assertEquals(
                List.of(0, 3, 4, 2, 2),
                audit.strategies().stream()
                        .map(value -> value.parentFanOut().percentile95())
                        .toList());
        assertEquals(
                List.of(0, 10, 7, 2, 2),
                audit.strategies().stream()
                        .map(value -> value.parentFanOut().maximum())
                        .toList());
        assertEquals(
                List.of(
                        new ResearchGroupedRouteBaselineAudit.BranchEntrySummary(
                                0, 4, 0, 4, 8),
                        new ResearchGroupedRouteBaselineAudit.BranchEntrySummary(
                                1, 4, 0, 4, 8),
                        new ResearchGroupedRouteBaselineAudit.BranchEntrySummary(
                                2, 4, 0, 4, 8),
                        new ResearchGroupedRouteBaselineAudit.BranchEntrySummary(
                                4, 4, 0, 4, 8),
                        new ResearchGroupedRouteBaselineAudit.BranchEntrySummary(
                                5, 4, 0, 4, 8)),
                audit.branchEntries());
        assertEquals(
                audit.automaticTargetCount(),
                audit.strategies().stream()
                        .mapToInt(ResearchGroupedRouteBaselineAudit.StrategySummary::targetCount)
                        .sum());
        assertEquals(
                audit.generatedReferenceCount(),
                audit.strategies().stream()
                        .mapToInt(ResearchGroupedRouteBaselineAudit.StrategySummary
                                ::parentReferenceCount)
                        .sum());
        assertEquals(
                audit.alternativeGroupCandidateCount(),
                audit.strategies().stream()
                        .mapToInt(ResearchGroupedRouteBaselineAudit.StrategySummary
                                ::multiParentTargetCount)
                        .sum());
        assertFalse(audit.branchEntries().isEmpty());
        assertTrue(audit.branchEntries().stream()
                .allMatch(entry -> entry.parentReferenceCount() >= entry.targetCount()));
    }

    @Test
    void phaseTwoRoleAnalysisCasesAreExplicitAndInternallyConsistent() {
        Map<String, List<WeaponMechanicalScore>> cases =
                AutomaticWeaponTopologyPhaseZeroFixture.mechanicalCases();

        assertEquals(List.of(
                "equal_power_different_roles",
                "same_role_different_power",
                "terminal_ties",
                "low_confidence",
                "skewed_roles"), List.copyOf(cases.keySet()));
        assertEquals(2, cases.get("equal_power_different_roles").size());
        assertEquals(
                cases.get("equal_power_different_roles").get(0).score(),
                cases.get("equal_power_different_roles").get(1).score());
        assertFalse(cases.get("equal_power_different_roles").get(0).metricScores().equals(
                cases.get("equal_power_different_roles").get(1).metricScores()));
        assertTrue(cases.get("same_role_different_power").get(0).score()
                < cases.get("same_role_different_power").get(1).score());
        assertEquals(5, cases.get("terminal_ties").size());
        assertTrue(cases.get("terminal_ties").stream()
                .map(WeaponMechanicalScore::score).distinct().count() == 1);
        assertTrue(cases.get("low_confidence").stream()
                .allMatch(value -> value.rating().confidence() < 100));
        assertEquals(37, cases.get("skewed_roles").size());
    }
}
