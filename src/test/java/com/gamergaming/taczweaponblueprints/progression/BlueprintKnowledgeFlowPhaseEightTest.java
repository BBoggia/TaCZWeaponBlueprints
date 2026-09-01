package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Release-contract gates for the reversible setup-assistant boundary. */
class BlueprintKnowledgeFlowPhaseEightTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void setupCommandIsPermissionGatedPreviewFirstAndExplicitlyConfirmed() throws IOException {
        String root = read("src/main/java/com/gamergaming/taczweaponblueprints/command/RootCommand.java");
        String command = read("src/main/java/com/gamergaming/taczweaponblueprints/command/sub/BlueprintSetupCommand.java");
        assertTrue(root.contains("hasPermission(2)"));
        assertTrue(command.contains("literal(\"preview\")"));
        assertTrue(command.contains("literal(\"confirm\")"));
        assertTrue(command.contains("setup-assessment.json"));
        assertFalse(command.contains("learnedBlueprint"));
        assertFalse(command.contains("discoveredBlueprint"));
    }

    @Test
    void artifactAndCandidateGatesRecordTheCompleteAssistantContract() throws IOException {
        String build = read("build.gradle");
        for (String required : List.of(
                "progression/BlueprintBalancePreset.class",
                "progression/BlueprintBalanceSettings.class",
                "progression/BlueprintSetupAssistant.class",
                "command/sub/BlueprintSetupCommand.class",
                "network/SyncBalancePresetPacket.class",
                "scope               : 'discovery_pacing_only'",
                "presets             : ['custom', 'accessible', 'balanced', 'scarce']",
                "application         : 'preview_then_explicit_confirm'",
                "customValues        : 'preserved'",
                "playerProgression   : 'unchanged'",
                "exportFormat        : 1")) {
            assertTrue(build.contains(required), "Missing Phase 8 gate: " + required);
        }
    }

    @Test
    void everySupportedConsumerUsesAndReportsTheEffectivePreset() throws IOException {
        String legacyLoot = read(
                "src/main/java/com/gamergaming/taczweaponblueprints/loot/AddItemsModifier.java");
        String lootCommand = read(
                "src/main/java/com/gamergaming/taczweaponblueprints/command/sub/BlueprintLootCommand.java");
        String config = read(
                "src/main/java/com/gamergaming/taczweaponblueprints/compat/fzzy_config/BlueprintConfig.java");

        assertTrue(legacyLoot.contains("BlueprintLootRuntimeConfig.capture()"));
        assertFalse(legacyLoot.contains("blueprintSpawnChance.get()"));
        assertFalse(legacyLoot.contains("minBlueprints.get()"));
        assertFalse(legacyLoot.contains("maxBlueprints.get()"));
        assertTrue(lootCommand.contains("var balance = ModConfigs.BLUEPRINT.balanceSettings()"));
        assertTrue(config.contains("isBalancePresetPersisted(preset)"));
        assertTrue(config.contains("NetworkHandler.syncBalancePreset"));
    }

    private static String read(String relative) throws IOException {
        return Files.readString(PROJECT.resolve(relative));
    }
}
