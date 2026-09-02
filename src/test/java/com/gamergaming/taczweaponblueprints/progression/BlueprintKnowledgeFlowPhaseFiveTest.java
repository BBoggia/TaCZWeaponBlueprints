package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

/** Release-contract gates for the live two-slot Blueprint Analyzer. */
class BlueprintKnowledgeFlowPhaseFiveTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void analyzerWireAndSlotExtensionsAreAppendOnlyAndExact() {
        assertEquals(
                List.of("RECYCLE", "REDEEM", "REDEEM_STACK", "REVERSE_ENGINEER", "RECOVER_POINTS"),
                Arrays.stream(BlueprintRecyclerActionContract.Action.values())
                        .map(Enum::name)
                        .toList());
        assertEquals(
                List.of("EMPTY", "INVALID", "BLUEPRINT", "RESEARCH_DATA", "PHYSICAL_ITEM"),
                Arrays.stream(BlueprintRecyclerPreview.InputKind.values())
                        .map(Enum::name)
                        .toList());
        assertEquals(0, BlueprintRecyclerMenu.INPUT_SLOT);
        assertEquals(1, BlueprintRecyclerMenu.OUTPUT_SLOT);
        assertEquals(2, BlueprintRecyclerMenu.FIRST_PLAYER_SLOT);
        assertEquals("47", NetworkHandler.PROTOCOL_VERSION);
    }

    @Test
    void packagedArtifactGateRequiresTheCompleteAnalyzerAuthority()
            throws IOException {
        String build = Files.readString(PROJECT.resolve("build.gradle"));
        for (String required : List.of(
                "capabilities/BlueprintLearningMutation.class",
                "progression/BlueprintLearningService.class",
                "progression/PhysicalItemBlueprintResolver.class",
                "progression/BlueprintReverseEngineeringEvaluator.class",
                "progression/BlueprintReverseEngineeringService.class",
                "item/BlueprintProvenance.class",
                "resource/research/BlueprintReverseEngineeringPolicy.class",
                "menu/BlueprintRecyclerMenu.class",
                "network/BlueprintRecyclerActionPacket.class",
                "network/SyncBlueprintRecyclerPreviewPacket.class")) {
            assertTrue(build.contains(required), "Missing Analyzer artifact gate: " + required);
        }
        assertTrue(build.contains("presentation: 'two_slot_contextual_analyzer'"));
        assertTrue(build.contains("localOutputs: 1"));
        assertTrue(build.contains("'reverse_engineer'"));
    }

}
