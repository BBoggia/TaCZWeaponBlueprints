package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.PersonalSettingsSession;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.ResearchTreeClientConfig;

class TechTreeViewPreferenceTest {
    @Test
    void aFreshInstallOpensTheNewTreeGroupedByBranch() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        assertEquals(TechTreeViewMode.NEW, config.techTreeView.get());
        assertEquals(TechTreeLaneMode.BRANCHES, config.techTreeLanes.get());
    }

    @Test
    void viewAndLanesAreOfferedInPersonalSettingsAheadOfTheClassicOnlyOptions() {
        assertEquals(0, PersonalSettingsSession.VISIBLE.indexOf("techTreeView"));
        assertEquals(1, PersonalSettingsSession.VISIBLE.indexOf("techTreeLanes"));
        assertTrue(PersonalSettingsSession.VISIBLE.contains("holdToResearch"));
    }

    @Test
    void theKeyToTheTreeOpensByItselfOnlyOnce(@TempDir Path config) {
        assertTrue(new ResearchTreeGuidancePreference(config).claimTechTreeLegend(), "first visit shows the key");
        assertFalse(new ResearchTreeGuidancePreference(config).claimTechTreeLegend(), "later visits don't");
    }
}
