package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class WeaponCapabilityScorerTest {
    private static final String REFERENCE = "test-capability-v3";

    @Test
    void finalCalibrationUsesTheFullProgressionScaleWithoutChangingPackageScores() {
        assertEquals(0, WeaponCapabilityScorer.calibratedProgression(25));
        assertEquals(20, WeaponCapabilityScorer.calibratedProgression(37));
        assertEquals(52, WeaponCapabilityScorer.calibratedProgression(56));
        assertEquals(85, WeaponCapabilityScorer.calibratedProgression(76));
        assertEquals(100, WeaponCapabilityScorer.calibratedProgression(100));
    }

    @Test
    void projectileCountDoesNotMultiplyImpactOrSustainedDamage() {
        WeaponStatEvidence single = weapon(
                "test:single", "shotgun", 12.0, 0.0, 300.0, 5,
                false, 1, 1.0, null, null, false);
        WeaponStatEvidence pellets = weapon(
                "test:pellets", "shotgun", 12.0, 0.0, 300.0, 5,
                false, 8, 1.0, null, null, false);

        WeaponCapabilityMetrics singleMetrics = WeaponCapabilityMetrics.derive(single);
        WeaponCapabilityMetrics pelletMetrics = WeaponCapabilityMetrics.derive(pellets);

        assertEquals(
                singleMetrics.value(CapabilityMetric.IMPACT_DAMAGE).orElseThrow(),
                pelletMetrics.value(CapabilityMetric.IMPACT_DAMAGE).orElseThrow());
        assertEquals(
                singleMetrics.value(CapabilityMetric.SUSTAINED_DPS).orElseThrow(),
                pelletMetrics.value(CapabilityMetric.SUSTAINED_DPS).orElseThrow());
        assertEquals(8.0,
                pelletMetrics.value(CapabilityMetric.PROJECTILE_COUNT).orElseThrow());
    }

    @Test
    void entityPierceIsNotFoldedIntoArmorBypass() {
        WeaponStatEvidence singleTarget = weaponWithPierce("test:single_target", 0);
        WeaponStatEvidence multiTarget = weaponWithPierce("test:multi_target", 4);
        WeaponCapabilityMetrics single = WeaponCapabilityMetrics.derive(singleTarget);
        WeaponCapabilityMetrics multi = WeaponCapabilityMetrics.derive(multiTarget);

        assertEquals(
                single.value(CapabilityMetric.ARMOR_IGNORE).orElseThrow(),
                multi.value(CapabilityMetric.ARMOR_IGNORE).orElseThrow());
        assertTrue(single.value(CapabilityMetric.TARGET_PENETRATION).orElseThrow()
                < multi.value(CapabilityMetric.TARGET_PENETRATION).orElseThrow());
    }

    @Test
    void largerExplosionRadiusRaisesAreaCapabilityWithoutChangingDirectImpact() {
        WeaponStatEvidence narrow = weapon(
                "test:narrow", "rpg", 10.0, 50.0, 40.0, 1,
                true, 1, 1.0, 2.0, 1.0, false);
        WeaponStatEvidence wide = weapon(
                "test:wide", "rpg", 10.0, 50.0, 40.0, 1,
                true, 1, 1.0, 6.0, 1.0, false);
        WeaponCapabilityReference reference = WeaponCapabilityReference.fromEvidence(
                REFERENCE, List.of(narrow, wide));
        WeaponCapabilityScorer scorer = new WeaponCapabilityScorer();

        WeaponCapabilityScore narrowScore = scorer.score(narrow, reference);
        WeaponCapabilityScore wideScore = scorer.score(wide, reference);

        assertEquals(narrowScore.metricScores().get("impact_damage"),
                wideScore.metricScores().get("impact_damage"));
        assertTrue(narrowScore.packageScores().get(WeaponCapabilityPackage.AREA_CONTROL)
                < wideScore.packageScores().get(WeaponCapabilityPackage.AREA_CONTROL));
        assertTrue(narrowScore.progressionScore() < wideScore.progressionScore());
    }

    @Test
    void bundledCalibrationPlacesGrenadeLauncherAboveBasicSidearm() {
        WeaponStatEvidence sidearm = weapon(
                "test:sidearm", "pistol", 6.0, 0.0, 500.0, 15,
                false, 1, 1.0, null, null, false);
        WeaponStatEvidence launcher = weapon(
                "test:launcher", "rpg", 10.0, 50.0, 60.0, 1,
                true, 1, 1.0, 6.0, 1.5, false);
        WeaponCapabilityScorer scorer = new WeaponCapabilityScorer();
        WeaponCapabilityReference reference =
                WeaponCapabilityReferenceCatalog.bundled().reference();

        WeaponCapabilityScore sidearmScore = scorer.score(sidearm, reference);
        WeaponCapabilityScore launcherScore = scorer.score(launcher, reference);

        assertTrue(launcherScore.progressionScore() > sidearmScore.progressionScore());
        assertTrue(launcherScore.packageScores().containsKey(
                WeaponCapabilityPackage.AREA_CONTROL));
        assertFalse(sidearmScore.packageScores().containsKey(
                WeaponCapabilityPackage.AREA_CONTROL));
        assertFalse(sidearmScore.warnings().stream().anyMatch(value ->
                value.contains("explosion_")));
    }

    @Test
    void scriptedEvidenceRetainsExplicitConfidenceCeiling() {
        WeaponStatEvidence scripted = weapon(
                "test:scripted", "rifle", 10.0, 0.0, 600.0, 30,
                false, 1, 1.0, null, null, true);
        WeaponStatEvidence referenceWeapon = weapon(
                "test:reference", "rifle", 8.0, 0.0, 500.0, 20,
                false, 1, 1.0, null, null, false);
        WeaponCapabilityReference reference = WeaponCapabilityReference.fromEvidence(
                REFERENCE, List.of(scripted, referenceWeapon));

        WeaponCapabilityScore score = new WeaponCapabilityScorer().score(
                scripted, reference);

        assertEquals(WeaponCapabilityScorer.SCRIPT_CONFIDENCE_CAP, score.confidence());
        assertTrue(score.warnings().contains("script_controlled"));
    }

    private static WeaponStatEvidence weapon(
            String id,
            String archetype,
            double damage,
            double explosionDamage,
            double rpm,
            int magazine,
            boolean explosive,
            int projectileCount,
            double retention,
            Double explosionRadius,
            Double explosionDelay,
            boolean scriptControlled) {
        return new WeaponStatEvidence(
                id,
                archetype,
                damage,
                explosionDamage,
                rpm,
                magazine,
                2.0,
                100.0,
                60.0,
                0.1,
                1.5,
                1,
                0.2,
                0.3,
                3.0,
                0.2,
                0.4,
                -0.2,
                1,
                2,
                null,
                "magazine",
                explosive,
                scriptControlled,
                projectileCount,
                retention,
                explosionRadius,
                explosionDelay,
                explosive,
                false,
                null,
                0.0,
                1.7,
                1,
                null,
                null,
                0.0,
                List.of());
    }

    private static WeaponStatEvidence weaponWithPierce(String id, int pierce) {
        return new WeaponStatEvidence(
                id, "rifle", 10.0, 0.0, 600.0, 30, 2.0, 100.0, 60.0,
                0.25, 1.5, pierce, 0.2, 0.3, 3.0, 0.2, 0.4, -0.2,
                2, 2, null, "magazine", false, false, 1, 1.0, null, null,
                false, false, null, 0.0, 1.7, 1, null, null, 0.0, List.of());
    }
}
