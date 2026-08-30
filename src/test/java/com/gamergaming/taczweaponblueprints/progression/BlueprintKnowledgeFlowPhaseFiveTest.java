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
                List.of("RECYCLE", "REDEEM", "REDEEM_STACK", "REVERSE_ENGINEER"),
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
        assertEquals("36", NetworkHandler.PROTOCOL_VERSION);
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

    @Test
    void phaseRecordCapturesAuthorityRollbackCompatibilityAndManualBoundary()
            throws IOException {
        String record = Files.readString(PROJECT.resolve(
                "docs/blueprint-knowledge-flow-phase-5.md"));
        for (String required : List.of(
                "one physical input plus one extract-only blueprint output",
                "opaque monotonically changing workstation-state token",
                "logical TaCZ identity",
                "restores every owned value",
                "records discovery only after the output exists",
                "non-recyclable",
                "protocol advances exactly once from 25 to 26",
                "Player progression remains data version 2",
                "does not claim the hands-on cases")) {
            assertTrue(record.contains(required), "Missing Phase 5 contract: " + required);
        }
    }
}
