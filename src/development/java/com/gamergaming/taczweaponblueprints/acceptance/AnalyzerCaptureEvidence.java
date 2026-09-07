package com.gamergaming.taczweaponblueprints.acceptance;

import java.io.IOException;
import java.nio.file.*;

/** A callback is not proof of a successful, fresh native screenshot. */
public final class AnalyzerCaptureEvidence {
    private AnalyzerCaptureEvidence() { }
    public static Path requireFresh(Path root, String filename) {
        Path capture = root.resolve("screenshots").resolve(filename);
        if (Files.exists(capture)) throw new IllegalStateException("Archive previous Analyzer capture before rerunning: " + capture);
        return capture;
    }
    public static void requireWritten(Path capture, boolean reportedSuccess) throws IOException {
        if (!reportedSuccess || !Files.isRegularFile(capture) || Files.size(capture) == 0)
            throw new IllegalStateException("Minecraft did not complete the Analyzer capture: " + capture);
    }
}
