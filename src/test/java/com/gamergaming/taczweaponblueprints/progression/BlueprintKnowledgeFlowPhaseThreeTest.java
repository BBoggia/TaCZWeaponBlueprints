package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

/** Structural compatibility gates for the direct-learning cutover. */
class BlueprintKnowledgeFlowPhaseThreeTest {
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
        assertEquals(22, ResearchBenchMenu.ActionResultCode.ROUTE_TOO_COMPLEX.ordinal());
        assertEquals(23,
                ResearchBenchMenu.ActionResultCode.TECH_TREE_UNAVAILABLE.ordinal());
        assertEquals(24, ResearchBenchMenu.ActionResultCode.UNSATISFIABLE.ordinal());
        assertEquals(25, ResearchBenchMenu.ActionResultCode.STALE_PREVIEW.ordinal());
        assertEquals(
                ResearchBenchMenu.ActionResultCode.values().length - 1,
                ResearchBenchMenu.ActionResultCode.REQUEST_THROTTLED.ordinal());
        assertEquals("42", NetworkHandler.PROTOCOL_VERSION);
        assertEquals(2, PlayerProgressionLimits.DATA_VERSION);
    }

}
