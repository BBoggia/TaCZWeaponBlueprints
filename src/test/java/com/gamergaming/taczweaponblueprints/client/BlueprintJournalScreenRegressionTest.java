package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

/** Source-level guard for detail state that requires a live client to render. */
class BlueprintJournalScreenRegressionTest {
    @Test
    void pagingClearsCurrentUnavailableAndRecentDetailSelections() throws IOException {
        String source = Files.readString(Path.of(System.getProperty("user.dir")).resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/client/"
                        + "BlueprintJournalScreen.java"));
        int start = source.indexOf("private void changePage(int direction)");
        int end = source.indexOf("\n    @Override", start);
        assertTrue(start >= 0 && end > start);
        String method = source.substring(start, end);

        assertTrue(method.contains("selectedEntry = null;"));
        assertTrue(method.contains("selectedHistory = null;"));
        assertTrue(method.contains("selectedRecent = null;"));
    }
}
