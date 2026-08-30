package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset;

class BlueprintConfigPersistenceTest {
    @Test
    void persistedPresetVerificationRequiresTheExactSerializedSelector() {
        String serialized = "version = 0\nbalancePreset = \"SCARCE\"\nmaxBlueprints = 2\n";

        assertTrue(BlueprintConfig.serializedBalancePresetMatches(
                serialized, BlueprintBalancePreset.SCARCE));
        assertFalse(BlueprintConfig.serializedBalancePresetMatches(
                serialized, BlueprintBalancePreset.BALANCED));
        assertFalse(BlueprintConfig.serializedBalancePresetMatches(
                "# balancePreset = \"SCARCE\"", BlueprintBalancePreset.SCARCE));
        assertFalse(BlueprintConfig.serializedBalancePresetMatches(
                null, BlueprintBalancePreset.SCARCE));
    }
}
