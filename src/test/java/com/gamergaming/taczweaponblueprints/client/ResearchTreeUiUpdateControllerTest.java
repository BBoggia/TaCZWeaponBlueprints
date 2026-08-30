package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ResearchTreeUiUpdateControllerTest {
    @Test
    void unchangedSnapshotsDoNotRebuildWidgetsUntilInvalidated() {
        ResearchTreeUiUpdateController updates = new ResearchTreeUiUpdateController();
        Snapshot first = new Snapshot("browse", 1);

        assertTrue(updates.shouldRefreshWidgets(first));
        assertFalse(updates.shouldRefreshWidgets(new Snapshot("browse", 1)));
        assertTrue(updates.shouldRefreshWidgets(new Snapshot("browse", 2)));
        assertFalse(updates.shouldRefreshWidgets(new Snapshot("browse", 2)));

        updates.invalidateWidgets();
        assertTrue(updates.shouldRefreshWidgets(new Snapshot("browse", 2)));
        assertThrows(IllegalArgumentException.class,
                () -> updates.shouldRefreshWidgets(null));
    }

    private record Snapshot(String mode, int revision) {
    }
}
