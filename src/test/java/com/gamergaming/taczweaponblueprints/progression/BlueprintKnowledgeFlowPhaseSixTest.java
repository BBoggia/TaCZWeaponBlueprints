package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

/** Release-contract gates for progression exemptions and starting knowledge. */
class BlueprintKnowledgeFlowPhaseSixTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void accessDefaultsAreEmptyAndCompatibilityDoesNotMove() {
        assertTrue(BlueprintAccessConfigSnapshot.EMPTY.progressionExemptBlueprints().isEmpty());
        assertTrue(BlueprintAccessConfigSnapshot.EMPTY.progressionExemptKinds().isEmpty());
        assertTrue(BlueprintAccessConfigSnapshot.EMPTY.progressionExemptItemTypes().isEmpty());
        assertTrue(BlueprintAccessConfigSnapshot.EMPTY.startingBlueprints().isEmpty());
        assertEquals("55", NetworkHandler.PROTOCOL_VERSION);
    }

    @Test
    void lifecycleAndArtifactGatesRequireTheSharedAuthorities() throws IOException {
        String login = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/event/ModEventHandler.java"));
        String reload = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/event/BlueprintResourceEvents.java"));
        String config = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/compat/fzzy_config/BlueprintConfig.java"));
        assertTrue(login.contains("StartingBlueprintGrantService.applyConfiguredGrants(serverPlayer)"));
        assertTrue(reload.contains("StartingBlueprintGrantService.applyConfiguredGrants(player)"));
        assertTrue(config.contains("StartingBlueprintGrantService.applyConfiguredGrants(onlinePlayer)"));
        assertTrue(config.contains("NetworkHandler.syncPlayerRecipeData(onlinePlayer)"));

        String build = Files.readString(PROJECT.resolve("build.gradle"));
        for (String required : List.of(
                "progression/BlueprintAccessConfigSnapshot.class",
                "progression/BlueprintProgressionAccess.class",
                "progression/StartingBlueprintGrantService.class")) {
            assertTrue(build.contains(required), "Missing Phase 6 artifact gate: " + required);
        }
        assertTrue(build.contains("exemptionPersistence: 'live_policy_only'"));
        assertTrue(build.contains("awardPolicy: 'starting_grants_do_not_award'"));
    }

    @Test
    void progressionExemptCraftingStillPassesThroughTierAndGateAuthority()
            throws IOException {
        String crafting = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/"
                        + "CraftingEligibilityService.java"));
        int exemption = crafting.indexOf(
                "boolean exempt = BlueprintProgressionAccess.isProgressionExempt");
        int publication = crafting.indexOf(
                "var policyAccess = ProgressionPolicyAccessService.acquireCrafting(",
                exemption);
        int craftingMap = crafting.indexOf("policyAccess.profileCraftingPolicies()", publication);
        int policyEvaluation = crafting.indexOf(
                "private static Evaluation evaluatePolicy(", craftingMap);
        int tier = crafting.indexOf("evaluateWorkbenchAccess(", policyEvaluation);
        int gate = crafting.indexOf(
                "ProgressionGateEvaluator.evaluateRequirements(", tier);

        assertTrue(exemption >= 0);
        assertTrue(publication > exemption);
        assertTrue(craftingMap > publication);
        assertTrue(policyEvaluation > craftingMap);
        assertTrue(tier > policyEvaluation);
        assertTrue(gate > tier);
        assertTrue(crafting.substring(exemption, publication)
                .contains("if (!exempt &&"));
        assertFalse(crafting.substring(exemption, publication)
                .contains("return Evaluation.permitted()"));
    }

    @Test
    void disabledBlueprintModeOnlyRetainsAuthorityForNativeCraftingWorkbenches()
            throws IOException {
        String crafting = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/progression/"
                        + "CraftingEligibilityService.java"));
        int snapshot = crafting.indexOf("public static Snapshot snapshot(");
        int nativeWorkbench = crafting.indexOf(
                "isNativeCraftingWorkbench(menu.getBlockId())", snapshot);
        int conditionalAuthority = crafting.indexOf(
                "if (blueprintsEnabled || nativeWorkbench)", nativeWorkbench);
        int authority = crafting.indexOf(
                "workbench = authenticatedWorkbench(player, menu)", conditionalAuthority);
        int disabled = crafting.indexOf("if (!blueprintsEnabled)", snapshot);

        assertTrue(snapshot >= 0);
        assertTrue(nativeWorkbench > snapshot);
        assertTrue(conditionalAuthority > nativeWorkbench);
        assertTrue(authority > conditionalAuthority);
        assertTrue(disabled > snapshot);
        assertTrue(disabled > authority);
    }

}
