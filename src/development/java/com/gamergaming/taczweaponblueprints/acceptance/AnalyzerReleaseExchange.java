package com.gamergaming.taczweaponblueprints.acceptance;

import java.nio.file.*;
import com.google.gson.*;

/** Local orchestration only. All player actions and previews still cross the Minecraft connection. */
public final class AnalyzerReleaseExchange {
    public static final boolean ENABLED = System.getProperty("tacz.analyzerReleaseQA.output") != null;
    public static final Path ROOT = Path.of(System.getProperty("tacz.analyzerReleaseQA.output", "."));
    public static final String PHASE = System.getProperty("tacz.analyzerReleaseQA.phase", "gameplay");
    public static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    private AnalyzerReleaseExchange() { }
    /** A repeated phase must not inherit a previous pass, request, or acknowledgement. */
    public static void requireFreshRun(Path root, String phase) throws java.io.IOException {
        if (!java.util.Set.of("gameplay", "restart", "migration").contains(phase))
            throw new IllegalArgumentException("unknown Analyzer acceptance phase");
        if (!Files.exists(root)) return;
        try (var entries = Files.list(root)) {
            check(entries.noneMatch(path -> path.getFileName().toString().startsWith(phase + "-")),
                    "Archive previous " + phase + " acceptance files before rerunning this phase");
        }
    }
    public static void requireConnectedCompletion(boolean opened, boolean connected) {
        check(opened && connected, "Completion requires a live client connection in this run; stale completion is not acceptance");
    }
    public static void write(String name, String value) throws java.io.IOException {
        Files.createDirectories(ROOT);
        var target = ROOT.resolve(PHASE + "-" + name);
        var temp = target.resolveSibling(target.getFileName() + ".tmp");
        Files.writeString(temp, value);
        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }
    public static String read(String name) throws java.io.IOException {
        var path = ROOT.resolve(PHASE + "-" + name);
        return Files.exists(path) ? Files.readString(path) : "";
    }
    public static void check(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
