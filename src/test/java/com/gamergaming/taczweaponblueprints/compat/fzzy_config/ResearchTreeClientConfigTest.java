package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchTreeDisplayPolicy;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeLayoutPreset;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;

class ResearchTreeClientConfigTest {
    @Test
    void defaultsPublishTheStableLayoutPolicy() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();

        assertEquals(ResearchTreeLayoutPreset.BALANCED, config.layoutPreset.get());
        assertEquals(ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW, config.layoutPolicy());
        assertTrue(config.holdToResearchEnabled());
        assertEquals(
                ResearchTreeClientConfig.DEFAULT_HOLD_DURATION_MILLIS,
                config.holdDurationMillis());
        assertEquals(ResearchTreeDisplayPolicy.DEFAULT, config.displayPolicy());
        assertTrue(config.showResearchPointNotifications.get());
    }

    @Test
    void updatePublishesOneCompleteImmutableSnapshot() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        ResearchTreeLayoutPolicy previous = config.layoutPolicy();
        config.nodeGap.validateAndSet(3);
        config.tierGap.validateAndSet(7);
        config.maxRankBlockWidth.validateAndSet(512);
        config.orderingSweeps.validateAndSet(2);
        config.compactionSweeps.validateAndSet(4);

        config.update(0);

        ResearchTreeLayoutPolicy updated = config.layoutPolicy();
        assertNotSame(previous, updated);
        assertEquals(3, updated.nodeGap());
        assertEquals(7, updated.tierGap());
        assertEquals(512, updated.maxRankBlockWidth());
        assertEquals(2, updated.orderingSweeps());
        assertEquals(4, updated.compactionSweeps());
        assertEquals(ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW.nodeGap(), previous.nodeGap());
    }

    @Test
    void interGroupSpacingIsNormalizedAboveIntraGroupSpacing() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        config.intraGroupGap.validateAndSet(80);
        config.interGroupGap.validateAndSet(20);

        config.update(0);

        assertEquals(80, config.interGroupGap.get());
        assertEquals(80, config.layoutPolicy().interGroupGap());
    }

    @Test
    void holdShortcutSettingsRemainIndependentFromLayoutSnapshots() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        ResearchTreeLayoutPolicy layout = config.layoutPolicy();

        config.holdToResearch.validateAndSet(false);
        config.holdDurationMillis.validateAndSet(1_200);
        config.update(0);

        assertFalse(config.holdToResearchEnabled());
        assertEquals(1_200, config.holdDurationMillis());
        assertEquals(layout, config.layoutPolicy());
    }

    @Test
    void displayPreferencesPublishAtomicallyWithoutRebuildingLayoutPolicy() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        ResearchTreeLayoutPolicy layout = config.layoutPolicy();

        config.reduceMotion.validateAndSet(true);
        config.showBackgroundGrid.validateAndSet(true);
        config.update(0);

        assertEquals(new ResearchTreeDisplayPolicy(true, true), config.displayPolicy());
        assertEquals(layout, config.layoutPolicy());
    }

    @Test
    void namedPresetOverridesWithoutErasingDormantCustomValues() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        config.layoutPreset.validateAndSet(ResearchTreeLayoutPreset.CUSTOM);
        config.nodeGap.validateAndSet(71);
        config.interGroupGap.validateAndSet(93);
        config.onUpdateClient();
        assertEquals(71, config.layoutPolicy().nodeGap());

        config.layoutPreset.validateAndSet(ResearchTreeLayoutPreset.COMPACT);
        config.onUpdateClient();

        assertEquals(
                ResearchTreeLayoutPreset.COMPACT.resolve(ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW),
                config.layoutPolicy());
        assertEquals(71, config.nodeGap.getUnconditional());
        assertEquals(93, config.interGroupGap.getUnconditional());

        config.layoutPreset.validateAndSet(ResearchTreeLayoutPreset.CUSTOM);
        config.onUpdateClient();
        assertEquals(71, config.layoutPolicy().nodeGap());
        assertEquals(93, config.layoutPolicy().interGroupGap());
    }

    @Test
    void versionZeroMigrationPreservesAdvancedLayoutAuthority() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        config.nodeGap.validateAndSet(47);

        config.update(0);

        assertEquals(ResearchTreeLayoutPreset.CUSTOM, config.layoutPreset.get());
        assertEquals(47, config.layoutPolicy().nodeGap());
    }

    @Test
    void inactiveDependentControlsStillExposeTheirStoredRuntimeValue() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        config.holdDurationMillis.validateAndSet(1_400);
        config.holdToResearch.validateAndSet(false);

        assertEquals(1_400, config.holdDurationMillis());
        assertEquals(1_400, config.holdDurationMillis.get());
    }

    @Test
    void restoreTreeAppearanceResetsPresetAndAdvancedValues() {
        ResearchTreeClientConfig config = new ResearchTreeClientConfig();
        config.layoutPreset.validateAndSet(ResearchTreeLayoutPreset.CUSTOM);
        config.nodeGap.validateAndSet(99);

        config.restoreTreeAppearanceDefaults();

        assertEquals(ResearchTreeLayoutPreset.BALANCED, config.layoutPreset.get());
        assertEquals(ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW.nodeGap(),
                config.nodeGap.getUnconditional());
        assertEquals(ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW, config.layoutPolicy());
    }
}
