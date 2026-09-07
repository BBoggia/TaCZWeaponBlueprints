package com.gamergaming.taczweaponblueprints.acceptance;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AnalyzerCaptureEvidenceTest {
    @TempDir Path root;
    @Test void failedCallbacksMissingFilesAndPartialWritesNeverCountAsEvidence() throws Exception {
        var file = AnalyzerCaptureEvidence.requireFresh(root, "case.png");
        assertThrows(IllegalStateException.class, () -> AnalyzerCaptureEvidence.requireWritten(file, true));
        Files.createDirectories(file.getParent()); Files.createFile(file);
        assertThrows(IllegalStateException.class, () -> AnalyzerCaptureEvidence.requireWritten(file, true));
        Files.write(file, new byte[]{1, 2, 3});
        assertThrows(IllegalStateException.class, () -> AnalyzerCaptureEvidence.requireWritten(file, false));
        assertDoesNotThrow(() -> AnalyzerCaptureEvidence.requireWritten(file, true));
        assertThrows(IllegalStateException.class, () -> AnalyzerCaptureEvidence.requireFresh(root, "case.png"));
        assertArrayEquals(new byte[]{1, 2, 3}, Files.readAllBytes(file));
    }
}
