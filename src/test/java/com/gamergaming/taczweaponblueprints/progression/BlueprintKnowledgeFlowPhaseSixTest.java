package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
        assertEquals("36", NetworkHandler.PROTOCOL_VERSION);
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
    void phaseRecordCapturesPersistenceAuthorityAndManualBoundary() throws IOException {
        String record = Files.readString(PROJECT.resolve(
                "docs/blueprint-knowledge-flow-phase-6.md"));
        for (String required : List.of(
                "live access policy",
                "idempotent, additive grants of durable knowledge",
                "without being inserted into `LearnedBlueprints`",
                "Removing an exemption immediately returns the target",
                "Removing an ID from `startingBlueprints` never revokes knowledge",
                "dispatches no discovery or\nlearning Research Point awards",
                "custom network protocol remains `26`",
                "Player progression remains data version 2",
                "does not claim the hands-on cases")) {
            assertTrue(record.contains(required), "Missing Phase 6 contract: " + required);
        }
    }
}
