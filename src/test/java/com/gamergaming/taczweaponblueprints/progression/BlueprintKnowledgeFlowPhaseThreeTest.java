package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

/** Structural compatibility gates for the direct-learning cutover. */
class BlueprintKnowledgeFlowPhaseThreeTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));

    @Test
    void packagedConfigUsesDirectLearningAndRetainsLegacyChoice() {
        BlueprintConfig config = new BlueprintConfig();

        assertEquals(
                TreeResearchResultMode.DIRECT_LEARN,
                config.progressionSnapshot().treeResearchResultMode());
        assertEquals(
                List.of("DIRECT_LEARN", "CREATE_BLUEPRINT"),
                java.util.Arrays.stream(TreeResearchResultMode.values())
                        .map(Enum::name)
                        .toList());
        assertEquals(
                "config.taczweaponblueprints.blueprint.treeResearchResultMode.DIRECT_LEARN",
                TreeResearchResultMode.DIRECT_LEARN.translationKey());
        assertEquals(
                "config.taczweaponblueprints.blueprint.treeResearchResultMode.CREATE_BLUEPRINT.desc",
                TreeResearchResultMode.CREATE_BLUEPRINT.descriptionKey());
    }

    @Test
    void actionResultExtensionIsAppendOnlyAndWireAndSaveVersionsStayStable() {
        assertEquals(
                18,
                ResearchBenchMenu.ActionResultCode.TRANSACTION_FAILED.ordinal());
        assertEquals(
                19,
                ResearchBenchMenu.ActionResultCode
                        .PROGRESSION_CAPACITY_EXHAUSTED.ordinal());
        assertEquals(20, ResearchBenchMenu.ActionResultCode.ROLLBACK_FAILED.ordinal());
        assertEquals(21, ResearchBenchMenu.ActionResultCode.PATH_TOO_LARGE.ordinal());
        assertEquals(
                ResearchBenchMenu.ActionResultCode.values().length - 1,
                ResearchBenchMenu.ActionResultCode.ROUTE_TOO_COMPLEX.ordinal());
        assertEquals("40", NetworkHandler.PROTOCOL_VERSION);
        assertEquals(2, PlayerProgressionLimits.DATA_VERSION);
    }

    @Test
    void phaseThreeRecordDocumentsAtomicCutoverAndDeferrals()
            throws IOException {
        String record = Files.readString(PROJECT.resolve(
                "docs/blueprint-knowledge-flow-phase-3.md"));

        for (String required : List.of(
                "DIRECT_LEARN",
                "CREATE_BLUEPRINT",
                "BlueprintLearningService.prepare",
                "BlueprintLearningService.commitPrepared",
                "full inventory cannot reject research",
                "complete inventory snapshot and original RP balance are restored",
                "rollback failure",
                "protocol remains `25`",
                "Player data version remains `2`",
                "does not yet implement physical-item reverse engineering")) {
            assertTrue(record.contains(required),
                    "Missing Phase 3 record: " + required);
        }
    }
}
