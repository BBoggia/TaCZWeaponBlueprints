package com.gamergaming.taczweaponblueprints.acceptance;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzerReleaseExchangeTest {
    @TempDir Path root;

    @Test void staleSuccessAndIncompleteRunsCannotBeReusedAsFreshAcceptance() throws Exception {
        for (String artifact : new String[]{"command", "ack", "request.json", "client-error.txt", "server-complete.json", "client-complete.json"}) {
            var path = root.resolve("gameplay-" + artifact);
            Files.writeString(path, "complete");
            assertThrows(IllegalStateException.class, () -> AnalyzerReleaseExchange.requireFreshRun(root, "gameplay"));
            assertEquals("complete", Files.readString(path));
            assertDoesNotThrow(() -> AnalyzerReleaseExchange.requireFreshRun(root, "restart"));
            Files.delete(path);
        }
        assertDoesNotThrow(() -> AnalyzerReleaseExchange.requireFreshRun(root, "gameplay"));
    }

    @Test void anOldCompleteCommandCannotPassWithoutThisRunsLiveConnection() {
        assertThrows(IllegalStateException.class, () -> AnalyzerReleaseExchange.requireConnectedCompletion(false, false));
        assertThrows(IllegalStateException.class, () -> AnalyzerReleaseExchange.requireConnectedCompletion(true, false));
        assertDoesNotThrow(() -> AnalyzerReleaseExchange.requireConnectedCompletion(true, true));
    }
}
