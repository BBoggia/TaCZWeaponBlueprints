package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.BlueprintAccessPolicy.LearningDecision;
import com.gamergaming.taczweaponblueprints.progression.BlueprintAccessPolicy.LearningFacts;
import com.gamergaming.taczweaponblueprints.progression.BlueprintAccessPolicy.LearningStatus;

class BlueprintAccessPolicyTest {
    @Test
    void vocabularyIsClosedAndHasNoHybridTreeResult() {
        assertEquals(
                List.of(
                        "TREE_RESEARCH",
                        "PHYSICAL_BLUEPRINT",
                        "STARTING_GRANT",
                        "ADMINISTRATOR",
                        "MIGRATION"),
                names(BlueprintUnlockOrigin.values()));
        assertEquals(
                List.of(
                        "BYPASS_TREE_PREREQUISITES",
                        "REQUIRE_TREE_PREREQUISITES",
                        "DISABLED"),
                names(PhysicalBlueprintLearningMode.values()));
        assertEquals(
                List.of("DIRECT_LEARN", "CREATE_BLUEPRINT"),
                names(TreeResearchResultMode.values()));
        assertEquals(
                List.of(
                        "ALLOWED",
                        "CONTENT_UNAVAILABLE",
                        "PLAYER_DATA_UNAVAILABLE",
                        "BLUEPRINTS_DISABLED",
                        "BLOCKED",
                        "PROGRESSION_EXEMPT",
                        "ALREADY_LEARNED",
                        "PHYSICAL_BLUEPRINT_LEARNING_DISABLED",
                        "PREREQUISITES_UNSATISFIED",
                        "PROGRESSION_CAPACITY_EXHAUSTED"),
                names(LearningStatus.values()));

        for (TreeResearchResultMode mode : TreeResearchResultMode.values()) {
            assertTrue(mode.learnsDirectly() ^ mode.createsPhysicalBlueprint());
        }
        assertTrue(PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES
                .learningPermitted());
        assertFalse(PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES
                .prerequisitesRequired());
        assertTrue(PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES
                .learningPermitted());
        assertTrue(PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES
                .prerequisitesRequired());
        assertFalse(PhysicalBlueprintLearningMode.DISABLED.learningPermitted());
    }

    @Test
    void onlyLivePlayerAcquisitionOriginsAreAwardEligible() {
        assertTrue(BlueprintUnlockOrigin.TREE_RESEARCH.liveAwardsEligible());
        assertTrue(BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT.liveAwardsEligible());
        assertFalse(BlueprintUnlockOrigin.STARTING_GRANT.liveAwardsEligible());
        assertFalse(BlueprintUnlockOrigin.ADMINISTRATOR.liveAwardsEligible());
        assertFalse(BlueprintUnlockOrigin.MIGRATION.liveAwardsEligible());
    }

    @Test
    void treeResearchRequiresPrerequisitesAndMarksLiveAwardEligibility() {
        LearningDecision missing = evaluate(
                BlueprintUnlockOrigin.TREE_RESEARCH,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                false);
        assertEquals(LearningStatus.PREREQUISITES_UNSATISFIED, missing.status());
        assertFalse(missing.allowed());

        LearningDecision allowed = valid(BlueprintUnlockOrigin.TREE_RESEARCH);
        assertTrue(allowed.allowed());
        assertFalse(allowed.prerequisitesBypassed());
        assertTrue(allowed.liveAwardsEligible());
    }

    @Test
    void physicalBlueprintModeControlsOnlyItsPrerequisiteLane() {
        LearningDecision bypass = evaluate(
                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                false);
        assertTrue(bypass.allowed());
        assertTrue(bypass.prerequisitesBypassed());
        assertTrue(bypass.liveAwardsEligible());

        LearningDecision required = evaluate(
                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                false);
        assertEquals(LearningStatus.PREREQUISITES_UNSATISFIED, required.status());

        LearningDecision requiredAndSatisfied = evaluate(
                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                true);
        assertTrue(requiredAndSatisfied.allowed());
        assertFalse(requiredAndSatisfied.prerequisitesBypassed());
        assertTrue(requiredAndSatisfied.liveAwardsEligible());

        LearningDecision disabled = evaluate(
                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                PhysicalBlueprintLearningMode.DISABLED,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                true);
        assertEquals(
                LearningStatus.PHYSICAL_BLUEPRINT_LEARNING_DISABLED,
                disabled.status());
    }

    @Test
    void startingAndAdministratorGrantsBypassPrerequisitesWithoutLiveAwards() {
        for (BlueprintUnlockOrigin origin : List.of(
                BlueprintUnlockOrigin.STARTING_GRANT,
                BlueprintUnlockOrigin.ADMINISTRATOR)) {
            LearningDecision decision = evaluate(
                    origin,
                    PhysicalBlueprintLearningMode.DISABLED,
                    true,
                    true,
                    true,
                    false,
                    false,
                    false,
                    true,
                    false);

            assertTrue(decision.allowed(), origin.name());
            assertTrue(decision.prerequisitesBypassed(), origin.name());
            assertFalse(decision.liveAwardsEligible(), origin.name());
        }
    }

    @Test
    void migrationPreservesKnowledgeAcrossCurrentGameplayPolicy() {
        LearningDecision preserved = evaluate(
                BlueprintUnlockOrigin.MIGRATION,
                PhysicalBlueprintLearningMode.DISABLED,
                true,
                true,
                false,
                true,
                true,
                false,
                true,
                false);

        assertTrue(preserved.allowed());
        assertTrue(preserved.prerequisitesBypassed());
        assertFalse(preserved.liveAwardsEligible());

        assertStatus(
                LearningStatus.CONTENT_UNAVAILABLE,
                evaluate(
                        BlueprintUnlockOrigin.MIGRATION,
                        PhysicalBlueprintLearningMode.DISABLED,
                        false,
                        true,
                        false,
                        true,
                        true,
                        false,
                        true,
                        false));
        assertStatus(
                LearningStatus.PLAYER_DATA_UNAVAILABLE,
                evaluate(
                        BlueprintUnlockOrigin.MIGRATION,
                        PhysicalBlueprintLearningMode.DISABLED,
                        true,
                        false,
                        false,
                        true,
                        true,
                        false,
                        true,
                        false));
        assertStatus(
                LearningStatus.PROGRESSION_CAPACITY_EXHAUSTED,
                evaluate(
                        BlueprintUnlockOrigin.MIGRATION,
                        PhysicalBlueprintLearningMode.DISABLED,
                        true,
                        true,
                        false,
                        true,
                        true,
                        false,
                        false,
                        false));
    }

    @Test
    void policyPrecedenceKeepsBlockExemptionAndPlayerStateDistinct() {
        assertStatus(
                LearningStatus.CONTENT_UNAVAILABLE,
                evaluate(
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        false,
                        false,
                        false,
                        true,
                        true,
                        true,
                        false,
                        false));
        assertStatus(
                LearningStatus.PLAYER_DATA_UNAVAILABLE,
                evaluate(
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        true,
                        false,
                        false,
                        true,
                        true,
                        true,
                        false,
                        false));
        assertStatus(
                LearningStatus.BLUEPRINTS_DISABLED,
                evaluate(
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        true,
                        true,
                        false,
                        true,
                        true,
                        true,
                        false,
                        false));
        assertStatus(
                LearningStatus.BLOCKED,
                evaluate(
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        true,
                        true,
                        true,
                        true,
                        true,
                        true,
                        false,
                        false));

        LearningDecision exempt = evaluate(
                BlueprintUnlockOrigin.TREE_RESEARCH,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                true,
                true,
                true,
                false,
                true,
                true,
                false,
                false);
        assertStatus(LearningStatus.PROGRESSION_EXEMPT, exempt);
        assertTrue(exempt.recipeAlreadyAccessible());

        LearningDecision learned = evaluate(
                BlueprintUnlockOrigin.TREE_RESEARCH,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                true,
                true,
                true,
                false,
                false,
                true,
                false,
                false);
        assertStatus(LearningStatus.ALREADY_LEARNED, learned);
        assertTrue(learned.recipeAlreadyAccessible());

        assertStatus(
                LearningStatus.PREREQUISITES_UNSATISFIED,
                evaluate(
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        false));
        assertStatus(
                LearningStatus.PROGRESSION_CAPACITY_EXHAUSTED,
                evaluate(
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        false,
                        true));
    }

    @Test
    void everyOrdinaryOriginRespectsGlobalBlockAndExemptionPolicy() {
        for (BlueprintUnlockOrigin origin : List.of(
                BlueprintUnlockOrigin.TREE_RESEARCH,
                BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                BlueprintUnlockOrigin.STARTING_GRANT,
                BlueprintUnlockOrigin.ADMINISTRATOR)) {
            assertStatus(
                    LearningStatus.BLUEPRINTS_DISABLED,
                    evaluate(
                            origin,
                            PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                            true,
                            true,
                            false,
                            false,
                            false,
                            false,
                            true,
                            true));
            assertStatus(
                    LearningStatus.BLOCKED,
                    evaluate(
                            origin,
                            PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                            true,
                            true,
                            true,
                            true,
                            false,
                            false,
                            true,
                            true));
            LearningDecision exempt = evaluate(
                    origin,
                    PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                    true,
                    true,
                    true,
                    false,
                    true,
                    false,
                    true,
                    true);
            assertStatus(LearningStatus.PROGRESSION_EXEMPT, exempt);
            assertTrue(exempt.recipeAlreadyAccessible(), origin.name());
        }
    }

    @Test
    void malformedFactsAndImpossibleDeniedDecisionsFailClosed() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintAccessPolicy.evaluateLearning(null));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LearningFacts(
                        null,
                        PhysicalBlueprintLearningMode.DISABLED,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        true,
                        true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LearningFacts(
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        null,
                        true,
                        true,
                        true,
                        false,
                        false,
                        false,
                        true,
                        true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LearningDecision(
                        LearningStatus.BLOCKED,
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        false,
                        true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LearningDecision(
                        LearningStatus.ALLOWED,
                        BlueprintUnlockOrigin.MIGRATION,
                        false,
                        true));
        assertThrows(
                IllegalArgumentException.class,
                () -> new LearningDecision(
                        LearningStatus.ALLOWED,
                        BlueprintUnlockOrigin.TREE_RESEARCH,
                        false,
                        false));
    }

    private static LearningDecision valid(BlueprintUnlockOrigin origin) {
        return evaluate(
                origin,
                PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES,
                true,
                true,
                true,
                false,
                false,
                false,
                true,
                true);
    }

    private static LearningDecision evaluate(
            BlueprintUnlockOrigin origin,
            PhysicalBlueprintLearningMode physicalMode,
            boolean contentAvailable,
            boolean playerDataAvailable,
            boolean blueprintsEnabled,
            boolean blocked,
            boolean progressionExempt,
            boolean alreadyLearned,
            boolean progressionCapacityAvailable,
            boolean prerequisitesSatisfied) {
        return BlueprintAccessPolicy.evaluateLearning(new LearningFacts(
                origin,
                physicalMode,
                contentAvailable,
                playerDataAvailable,
                blueprintsEnabled,
                blocked,
                progressionExempt,
                alreadyLearned,
                progressionCapacityAvailable,
                prerequisitesSatisfied));
    }

    private static void assertStatus(
            LearningStatus expected,
            LearningDecision actual) {
        assertEquals(expected, actual.status());
        assertFalse(actual.allowed());
        assertFalse(actual.prerequisitesBypassed());
        assertFalse(actual.liveAwardsEligible());
    }

    private static List<String> names(Enum<?>[] values) {
        return Arrays.stream(values).map(Enum::name).toList();
    }
}
