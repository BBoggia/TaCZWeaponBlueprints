package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BlueprintProgressionSyncSchedulerTest {
    @AfterEach
    void clearScheduler() {
        BlueprintProgressionSyncScheduler.clearAll();
    }

    @Test
    void coalescesRepeatedDirtyMarksUntilTheNextFlush() {
        UUID playerId = UUID.randomUUID();

        assertTrue(BlueprintProgressionSyncScheduler.markDirty(playerId));
        assertFalse(BlueprintProgressionSyncScheduler.markDirty(playerId));
        assertTrue(BlueprintProgressionSyncScheduler.consume(playerId));
        assertFalse(BlueprintProgressionSyncScheduler.consume(playerId));
        assertTrue(BlueprintProgressionSyncScheduler.markDirty(playerId));
    }

    @Test
    void keepsDifferentPlayersIndependent() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();

        assertTrue(BlueprintProgressionSyncScheduler.markDirty(first));
        assertTrue(BlueprintProgressionSyncScheduler.markDirty(second));
        assertTrue(BlueprintProgressionSyncScheduler.consume(first));
        assertFalse(BlueprintProgressionSyncScheduler.consume(first));
        assertTrue(BlueprintProgressionSyncScheduler.consume(second));
    }
}
