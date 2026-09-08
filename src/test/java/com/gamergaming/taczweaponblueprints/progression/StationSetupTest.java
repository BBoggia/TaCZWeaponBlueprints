package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;

class StationSetupTest {
    @Test void oldCustomSettingsRetainTheirTierRules() {
        var config = ConfigApi.deserializeConfig(new BlueprintConfig(),
                "version = 4\nprogressionPreset = \"CUSTOM\"\ncustomEnforceResearchTiers = false\ncustomEnforceCraftingTiers = true\n").get();
        config.update(4); // The registered file loader invokes migration after parsing.
        assertEquals(StationSetup.ResearchAccess.PRESET, config.stationSetup().research());
        assertFalse(ResearchFeatureConfigSnapshot.from(config).enforceResearchTiers());
        assertTrue(ResearchFeatureConfigSnapshot.from(config).enforceCraftingTiers());
    }
    @Test void modesRoundTripAndLeaveCraftingIndependent() {
        for (var mode : StationSetup.ResearchAccess.values()) {
            var config = new BlueprintConfig(); config.researchAccess.validateAndSet(mode);
            config.analyzerSetup.validateAndSet(StationSetup.AnalyzerMode.SINGLE);
            config.singleAnalyzerDataTier.validateAndSet(AnalyzerTier.TIER_2);
            var restored = ConfigApi.deserializeConfig(new BlueprintConfig(), ConfigApi.serializeConfig(config).get()).get();
            assertEquals(config.stationSetup(), restored.stationSetup());
            assertTrue(ResearchFeatureConfigSnapshot.from(restored).enforceCraftingTiers());
        }
    }
    @Test void simplifiedAccessAndRecipesAgreeAcrossAllModes() {
        for (var analyzer : StationSetup.AnalyzerMode.values()) for (var research : StationSetup.ResearchAccess.values()) {
            var setup = new StationSetup(analyzer, research, AnalyzerTier.TIER_2);
            assertEquals(research == StationSetup.ResearchAccess.SINGLE || research == StationSetup.ResearchAccess.PORTABLE
                    ? ResearchWorkbenchTier.TIER_3 : ResearchWorkbenchTier.TIER_1, setup.researchTier(ResearchWorkbenchTier.TIER_1));
            assertTrue(setup.recipeEnabled(true, 1));
            assertEquals(analyzer == StationSetup.AnalyzerMode.TIERED, setup.recipeEnabled(true, 3));
            assertEquals(research != StationSetup.ResearchAccess.PORTABLE, setup.recipeEnabled(false, 1));
            assertEquals(research == StationSetup.ResearchAccess.TIERED || research == StationSetup.ResearchAccess.PRESET, setup.recipeEnabled(false, 3));
            assertEquals(analyzer == StationSetup.AnalyzerMode.SINGLE ? AnalyzerTier.TIER_2 : AnalyzerTier.TIER_3,
                    setup.dataRewardTier(AnalyzerTier.TIER_3));
        }
    }
}
