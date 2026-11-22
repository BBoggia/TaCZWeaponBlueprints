package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.ResearchTreeClientConfig;

class TechTreeViewPreferenceTest {
    @Test
    void aFreshInstallOpensTheNewTreeGroupedByBranch() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        assertEquals(TechTreeLaneMode.BRANCHES, config.techTreeLanes.get());
    }

    @Test
    void retiringClassicPreservesOtherPreferences() {
        var config = me.fzzyhmstrs.fzzy_config.api.ConfigApi.deserializeConfig(new ResearchTreeClientConfig(), """
                version = 3
                techTreeView = "CLASSIC"
                techTreeLanes = "TYPES"
                holdDurationMillis = 1100
                """).get();
        assertEquals(TechTreeLaneMode.TYPES, config.techTreeLanes.get());
        assertEquals(1100, config.holdDurationMillis());
        assertFalse(me.fzzyhmstrs.fzzy_config.api.ConfigApi.serializeConfig(config).get().contains("techTreeView"));
    }

    @Test
    void theKeyToTheTreeOpensByItselfOnlyOnce(@TempDir Path config) {
        assertTrue(new ResearchTreeGuidancePreference(config).claimTechTreeLegend(), "first visit shows the key");
        assertFalse(new ResearchTreeGuidancePreference(config).claimTechTreeLegend(), "later visits don't");
    }
}
