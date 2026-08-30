package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

/** Locks Phase 2 to atomic learning plus physical-blueprint activation. */
class BlueprintKnowledgeFlowPhaseTwoTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void physicalItemDelegatesWithoutReimplementingMutationAwardsOrSync()
            throws IOException {
        String item = read(
                "src/main/java/com/gamergaming/taczweaponblueprints/item/BlueprintItem.java");
        assertTrue(item.contains(
                "BlueprintLearningService.learnPhysicalBlueprint"));
        assertFalse(item.contains("stack.shrink(1)"),
                "the item wrapper must not consume outside the learning transaction");
        for (String forbidden : List.of(
                ".addBlueprint(",
                ".addRecipe(",
                "ResearchPointAwardDispatcher",
                "NetworkHandler")) {
            assertFalse(item.contains(forbidden), "BlueprintItem retained " + forbidden);
        }

        String service = read(
                "src/main/java/com/gamergaming/taczweaponblueprints/"
                        + "progression/BlueprintLearningService.java");
        assertTrue(service.contains("applyBlueprintLearning"));
        assertTrue(service.contains("ResearchPointAwardDispatcher.blueprintTransitions"));
        assertTrue(service.contains("NetworkHandler.syncPlayerRecipeData"));
        assertTrue(service.indexOf("physicalBlueprint.shrink(1)")
                < service.indexOf("commitPrepared(preparation.prepared().orElseThrow()"));
        assertTrue(service.indexOf("physicalBlueprint.grow(1)")
                > service.indexOf("commitPrepared(preparation.prepared().orElseThrow()"));
        assertTrue(service.indexOf("result.successful() && result.liveAwardsEligible()")
                < service.indexOf("NetworkHandler.syncPlayerRecipeData"));
    }

    @Test
    void laterTreeActivationKeepsPhaseTwoSaveRecyclerAndWireBoundaries()
            throws IOException {
        String research = read(
                "src/main/java/com/gamergaming/taczweaponblueprints/"
                        + "progression/BlueprintResearchService.java");
        assertTrue(research.contains("BlueprintLearningService.prepare"));
        assertTrue(research.contains("BlueprintLearningService.commitPrepared"));
        assertTrue(research.contains("TreeResearchResultMode"));
        assertTrue(research.contains("input.createOutput(blueprintId)"));
        assertTrue(research.contains("input.deliver(output)"));

        assertEquals(
                Set.of("RECYCLE", "REDEEM", "REDEEM_STACK", "REVERSE_ENGINEER"),
                Arrays.stream(BlueprintRecyclerActionContract.Action.values())
                        .map(Enum::name)
                        .collect(Collectors.toSet()));
        assertEquals(0, BlueprintRecyclerMenu.INPUT_SLOT);
        assertEquals(1, BlueprintRecyclerMenu.OUTPUT_SLOT);
        assertEquals(2, BlueprintRecyclerMenu.FIRST_PLAYER_SLOT);
        assertEquals(2, PlayerProgressionLimits.DATA_VERSION);
        assertEquals("36", NetworkHandler.PROTOCOL_VERSION);
    }

    @Test
    void phaseTwoRecordDocumentsActivationAndDeferrals() throws IOException {
        String record = read("docs/blueprint-knowledge-flow-phase-2.md");
        for (String required : List.of(
                "BlueprintLearningMutation",
                "PREFLIGHT",
                "COMMIT",
                "progression collections",
                "BlueprintLearningService",
                "BYPASS_TREE_PREREQUISITES",
                "only after `SUCCESS`",
                "Research Tree still creates one physical blueprint",
                "protocol remains 25",
                "player data version remains 2")) {
            assertTrue(record.contains(required), "Missing Phase 2 record: " + required);
        }
    }

    private static String read(String relative) throws IOException {
        return Files.readString(PROJECT.resolve(relative));
    }
}
