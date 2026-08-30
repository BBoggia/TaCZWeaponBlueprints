package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

class AutomaticWeaponTerminalClusterResolverTest {
    private final AutomaticWeaponTerminalClusterResolver resolver =
            new AutomaticWeaponTerminalClusterResolver();

    @Test
    void adaptiveToleranceIncludesLocallyAdjacentPeersButNeverExceedsFour() {
        Map<String, AutomaticWeaponRoleSignature> signatures = signatures(
                signature("terminal:anchor", 90, 50, "rifle", 0, true),
                signature("terminal:near", 86, 46, "rifle", 0, true),
                signature("terminal:outside", 85, 45, "rifle", 0, true));

        AutomaticWeaponTerminalCluster cluster = resolver.resolve(
                List.copyOf(signatures.keySet()), signatures);

        assertEquals(AutomaticWeaponTerminalClusterResolver.MAX_SCORE_TOLERANCE,
                cluster.adaptiveScoreTolerance());
        assertEquals(List.of("terminal:anchor", "terminal:near"),
                cluster.terminalBlueprintIds());
        assertEquals(AutomaticWeaponTerminalCluster.Resolution.EQUIVALENT,
                cluster.resolution());
    }

    @Test
    void aggregateScoreTieDoesNotOverrideWholeMetricOrRoleEvidence() {
        Map<String, AutomaticWeaponRoleSignature> signatures = signatures(
                signature("terminal:anchor", 90, 50, "rifle", 0, true),
                signature("terminal:different_shape", 90, 50, "rifle", 70, true));

        AutomaticWeaponTerminalCluster cluster = resolver.resolve(
                List.copyOf(signatures.keySet()), signatures);

        assertEquals(List.of("terminal:anchor"), cluster.terminalBlueprintIds());
        assertEquals(1, cluster.equivalentCandidateCount());
        assertEquals(AutomaticWeaponTerminalCluster.Resolution.SINGLE,
                cluster.resolution());
    }

    @Test
    void oneSevereMetricMismatchCannotHideInsideAWeightedAverage() {
        Map<String, Integer> neutral = offsets(0);
        Map<String, Integer> outlier = new LinkedHashMap<>(neutral);
        outlier.put(MechanicalMetric.HEADSHOT_MULTIPLIER.serializedName(), 100);
        AutomaticWeaponRoleSignature anchor = signature(
                "terminal:anchor", 90, 50, "rifle", neutral, true);
        AutomaticWeaponRoleSignature candidate = signature(
                "terminal:headshot_outlier", 90, 50, "rifle", outlier, true);
        Map<String, AutomaticWeaponRoleSignature> signatures =
                signatures(anchor, candidate);

        assertTrue(AutomaticWeaponTerminalClusterResolver
                .fullMetricSimilarity(anchor, candidate).orElseThrow()
                >= AutomaticWeaponTerminalClusterResolver.MIN_FULL_METRIC_SIMILARITY);
        assertTrue(AutomaticWeaponTerminalClusterResolver
                .maximumMetricDistance(anchor, candidate).orElseThrow()
                > AutomaticWeaponTerminalClusterResolver.MAX_INDIVIDUAL_METRIC_DISTANCE);
        assertEquals(List.of("terminal:anchor"), resolver.resolve(
                List.copyOf(signatures.keySet()), signatures).terminalBlueprintIds());
    }

    @Test
    void everySelectedPeerMustBeCompatibleWithTheWholeCluster() {
        Map<String, Integer> neutral = offsets(0);
        Map<String, Integer> positive = new LinkedHashMap<>(neutral);
        Map<String, Integer> negative = new LinkedHashMap<>(neutral);
        positive.put(MechanicalMetric.HEADSHOT_MULTIPLIER.serializedName(), 20);
        negative.put(MechanicalMetric.HEADSHOT_MULTIPLIER.serializedName(), -20);
        Map<String, AutomaticWeaponRoleSignature> signatures = signatures(
                signature("terminal:anchor", 90, 50, "rifle", neutral, true),
                signature("terminal:positive", 89, 50, "rifle", positive, true),
                signature("terminal:negative", 89, 50, "rifle", negative, true));

        AutomaticWeaponTerminalCluster cluster = resolver.resolve(
                List.copyOf(signatures.keySet()), signatures);

        assertEquals(List.of("terminal:anchor", "terminal:negative"),
                cluster.terminalBlueprintIds());
        assertEquals(2, cluster.equivalentCandidateCount());
    }

    @Test
    void secondaryRoleDifferenceDividesAnOversizedEquivalentFamily() {
        Map<String, AutomaticWeaponRoleSignature> signatures = signatures(
                signature("terminal:rifle_anchor", 90, 50, "rifle", 0, true),
                signature("terminal:rifle_peer", 89, 49, "rifle", 0, true),
                signature("terminal:smg_a", 89, 49, "smg", 0, true),
                signature("terminal:smg_b", 89, 49, "smg", 0, true),
                signature("terminal:smg_c", 89, 49, "smg", 0, true));

        AutomaticWeaponTerminalCluster cluster = resolver.resolve(
                List.copyOf(signatures.keySet()), signatures);

        assertEquals(AutomaticWeaponTerminalCluster.Resolution.ROLE_PARTITIONED,
                cluster.resolution());
        assertEquals(List.of("terminal:rifle_anchor", "terminal:rifle_peer"),
                cluster.terminalBlueprintIds());
        assertEquals(3, cluster.deferredEquivalentCount());
        assertFalse(cluster.diagnostic().isPresent());
    }

    @Test
    void unresolvedOversizedTieSelectsThreeAndEmitsTruncationDiagnostic() {
        Map<String, AutomaticWeaponRoleSignature> signatures = new LinkedHashMap<>();
        for (int index = 0; index < 5; index++) {
            AutomaticWeaponRoleSignature signature = signature(
                    "terminal:tied_" + index, 90, 50, "rifle", 0, true);
            signatures.put(signature.blueprintId(), signature);
        }

        AutomaticWeaponTerminalCluster cluster = resolver.resolve(
                List.copyOf(signatures.keySet()), signatures);

        assertEquals(AutomaticWeaponTerminalCluster.Resolution.TRUNCATED,
                cluster.resolution());
        assertEquals(List.of(
                "terminal:tied_0", "terminal:tied_1", "terminal:tied_2"),
                cluster.terminalBlueprintIds());
        assertEquals(List.of("terminal:tied_3", "terminal:tied_4"),
                cluster.deferredEquivalentBlueprintIds());
        assertEquals(AutomaticWeaponTerminalCluster.TRUNCATED_DIAGNOSTIC,
                cluster.diagnostic().orElseThrow());
    }

    @Test
    void lowConfidenceCandidateCannotBecomeAnchorOrPeer() {
        Map<String, AutomaticWeaponRoleSignature> signatures = signatures(
                signature("terminal:uncertain_strong", 100, 55, "rifle", 0, false),
                signature("terminal:trusted", 80, 50, "rifle", 0, true));

        AutomaticWeaponTerminalCluster cluster = resolver.resolve(
                List.copyOf(signatures.keySet()), signatures);

        assertEquals("terminal:trusted", cluster.anchorBlueprintId().orElseThrow());
        assertEquals(List.of("terminal:trusted"), cluster.terminalBlueprintIds());
        assertEquals(1, cluster.reliableCandidateCount());
        assertTrue(cluster.deferredEquivalentBlueprintIds().isEmpty());
    }

    private static Map<String, AutomaticWeaponRoleSignature> signatures(
            AutomaticWeaponRoleSignature... values) {
        Map<String, AutomaticWeaponRoleSignature> result = new LinkedHashMap<>();
        for (AutomaticWeaponRoleSignature value : values) {
            result.put(value.blueprintId(), value);
        }
        return result;
    }

    private static AutomaticWeaponRoleSignature signature(
            String id,
            int score,
            int baseline,
            String archetype,
            int shape,
            boolean reliable) {
        Map<String, Integer> offsets = new LinkedHashMap<>();
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            int direction = metric.ordinal() % 2 == 0 ? shape : -shape;
            offsets.put(metric.serializedName(), direction);
        }
        return signature(id, score, baseline, archetype, offsets, reliable);
    }

    private static Map<String, Integer> offsets(int value) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            result.put(metric.serializedName(), value);
        }
        return result;
    }

    private static AutomaticWeaponRoleSignature signature(
            String id,
            int score,
            int baseline,
            String archetype,
            Map<String, Integer> offsets,
            boolean reliable) {
        return new AutomaticWeaponRoleSignature(
                id,
                score,
                reliable ? 100 : 20,
                archetype,
                false,
                baseline,
                offsets,
                true,
                reliable ? List.of() : List.of("low_confidence"));
    }
}
