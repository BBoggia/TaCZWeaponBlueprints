package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

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
