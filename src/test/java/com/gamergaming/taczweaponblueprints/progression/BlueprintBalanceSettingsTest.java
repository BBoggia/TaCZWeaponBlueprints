package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

class BlueprintBalanceSettingsTest {
    @Test
    void customPreservesHandTunedValuesWhileNamedPresetsAreDeterministic() {
        BlueprintBalanceSettings custom = BlueprintBalanceSettings.resolve(
                BlueprintBalancePreset.CUSTOM, JournalVisibility.SILHOUETTE, 0.27, 2, 4);
        assertEquals(JournalVisibility.SILHOUETTE, custom.maximumUndiscoveredVisibility());
        assertEquals(0.27, custom.lootChance());
        assertEquals(2, custom.minimumLootRolls());
        assertEquals(4, custom.maximumLootRolls());

        BlueprintBalanceSettings accessible = BlueprintBalanceSettings.resolve(
                BlueprintBalancePreset.ACCESSIBLE, JournalVisibility.HIDDEN, 0.01, 0, 0);
        assertEquals(JournalVisibility.FULL, accessible.maximumUndiscoveredVisibility());
        assertEquals(0.35, accessible.lootChance());
        assertEquals(1, accessible.minimumLootRolls());
        assertEquals(3, accessible.maximumLootRolls());

        BlueprintBalanceSettings scarce = BlueprintBalanceSettings.resolve(
                BlueprintBalancePreset.SCARCE, JournalVisibility.FULL, 1.0, 64, 64);
        assertEquals(JournalVisibility.NAME, scarce.maximumUndiscoveredVisibility());
        assertEquals(0.10, scarce.lootChance());
        assertEquals(1, scarce.minimumLootRolls());
        assertEquals(1, scarce.maximumLootRolls());
    }

    @Test
    void effectiveSnapshotUsesPresetWithoutOverwritingCustomFields() {
        BlueprintConfig config = new BlueprintConfig();
        config.maximumUndiscoveredVisibility.accept(JournalVisibility.HIDDEN);
        config.blueprintSpawnChance.accept(0.73);
        config.minBlueprints.accept(4);
        config.maxBlueprints.accept(7);
        config.balancePreset.accept(BlueprintBalancePreset.BALANCED);
        config.onSyncServer();

        assertEquals(JournalVisibility.FULL,
                config.progressionSnapshot().maximumUndiscoveredVisibility());
        assertEquals(0.20, config.balanceSettings().lootChance());
        assertEquals(JournalVisibility.HIDDEN, config.maximumUndiscoveredVisibility.get());
        assertEquals(0.73, config.blueprintSpawnChance.get());

        config.balancePreset.accept(BlueprintBalancePreset.CUSTOM);
        config.onSyncServer();
        assertEquals(JournalVisibility.HIDDEN,
                config.progressionSnapshot().maximumUndiscoveredVisibility());
        assertEquals(0.73, config.balanceSettings().lootChance());
        assertEquals(4, config.balanceSettings().minimumLootRolls());
        assertEquals(7, config.balanceSettings().maximumLootRolls());
    }

    @Test
    void rejectsInvalidEffectiveSettingsAndPresetNames() {
        assertThrows(IllegalArgumentException.class, () -> new BlueprintBalanceSettings(
                BlueprintBalancePreset.CUSTOM, JournalVisibility.FULL, Double.NaN, 1, 2));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintBalanceSettings(
                BlueprintBalancePreset.CUSTOM, JournalVisibility.FULL, 0.2, 3, 2));
        assertThrows(IllegalArgumentException.class, () -> BlueprintBalancePreset.parse("fastest"));
    }

    @Test
    void authoritativeClientPresetPublicationRefreshesOnlyTheOverlay() {
        BlueprintConfig config = new BlueprintConfig();
        config.maximumUndiscoveredVisibility.validateAndSet(JournalVisibility.HIDDEN);
        config.blueprintSpawnChance.validateAndSet(0.73);

        config.acceptSynchronizedBalancePreset(BlueprintBalancePreset.SCARCE);

        assertEquals(BlueprintBalancePreset.SCARCE, config.balancePreset.get());
        assertEquals(JournalVisibility.NAME,
                config.progressionSnapshot().maximumUndiscoveredVisibility());
        assertEquals(0.10, config.balanceSettings().lootChance());
        assertEquals(JournalVisibility.HIDDEN, config.maximumUndiscoveredVisibility.get());
        assertEquals(0.73, config.blueprintSpawnChance.get());
    }

    @Test
    void freshServersUseBalancedWhileVersionZeroServersKeepCustomAuthority() {
        BlueprintConfig fresh = new BlueprintConfig();
        assertEquals(BlueprintBalancePreset.BALANCED, fresh.balancePreset.get());
        assertEquals(0.20, fresh.balanceSettings().lootChance());
        assertEquals(10_000, fresh.progressionSnapshot().pointCap());

        fresh.maximumUndiscoveredVisibility.validateAndSet(JournalVisibility.HIDDEN);
        fresh.blueprintSpawnChance.validateAndSet(0.73);
        fresh.update(0);

        assertEquals(BlueprintBalancePreset.CUSTOM, fresh.balancePreset.get());
        assertEquals(JournalVisibility.HIDDEN,
                fresh.progressionSnapshot().maximumUndiscoveredVisibility());
        assertEquals(0.73, fresh.balanceSettings().lootChance());
    }

    @Test
    void configuredPointCapUsesThePracticalSliderRange() {
        BlueprintConfig config = new BlueprintConfig();
        config.researchPointCap.validateAndSet(
                BlueprintConfig.MAX_CONFIGURED_RESEARCH_POINT_CAP);
        config.onSyncServer();
        assertEquals(
                BlueprintConfig.MAX_CONFIGURED_RESEARCH_POINT_CAP,
                config.progressionSnapshot().pointCap());

        config.researchPointCap.validateAndSet(
                BlueprintConfig.MAX_CONFIGURED_RESEARCH_POINT_CAP + 1);
        config.onSyncServer();
        assertEquals(
                BlueprintConfig.MAX_CONFIGURED_RESEARCH_POINT_CAP,
                config.progressionSnapshot().pointCap());
    }

    @Test
    void inactiveCustomControlsRetainTheirStoredRuntimeValues() {
        BlueprintConfig config = new BlueprintConfig();
        config.maximumUndiscoveredVisibility.validateAndSet(JournalVisibility.HIDDEN);
        config.blueprintSpawnChance.validateAndSet(0.73);

        assertEquals(BlueprintBalancePreset.BALANCED, config.balancePreset.get());
        assertEquals(JournalVisibility.HIDDEN, config.maximumUndiscoveredVisibility.get());
        assertEquals(0.73, config.blueprintSpawnChance.get());
        assertEquals(JournalVisibility.FULL,
                config.balanceSettings().maximumUndiscoveredVisibility());
        assertEquals(0.20, config.balanceSettings().lootChance());
    }
}
