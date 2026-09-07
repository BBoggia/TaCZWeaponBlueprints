package com.gamergaming.taczweaponblueprints.analyzer.authoring;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;

/** Offline authoring CLI. Never mutates a catalog, datapack, configuration, or player save. */
public final class AnalyzerBalanceTool {
    private AnalyzerBalanceTool() { }
    public static void main(String[] args) throws Exception {
        var options = new HashMap<String, String>();
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 == args.length || !Set.of("--catalog", "--output", "--target").contains(args[i])
                    || options.put(args[i], args[i + 1]) != null)
                throw new IllegalArgumentException("Usage: --catalog export.json --output report.json [--target namespace:weapon]");
        }
        if (!options.containsKey("--catalog") || !options.containsKey("--output"))
            throw new IllegalArgumentException("--catalog and --output are required");
        Path input = Path.of(options.get("--catalog")).toRealPath();
        Path output = Path.of(options.get("--output")).toAbsolutePath().normalize();
        if (input.equals(output) || Files.exists(output) && Files.isSameFile(input, output))
            throw new IllegalArgumentException("report must not overwrite its input catalog");
        if (Files.size(input) > 256L * 1024 * 1024) throw new IllegalArgumentException("catalog exceeds 256 MiB input limit");
        JsonObject report;
        try (var reader = Files.newBufferedReader(input)) {
            report = AnalyzerBalanceReport.create(JsonParser.parseReader(reader).getAsJsonObject(), Optional.ofNullable(options.get("--target")));
        }
        Files.createDirectories(output.getParent());
        var temporary = Files.createTempFile(output.getParent(), ".analyzer-report-", ".json");
        try {
            Files.writeString(temporary, new GsonBuilder().setPrettyPrinting().create().toJson(report) + "\n");
            try { Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException unsupported) { Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING); }
        } finally { Files.deleteIfExists(temporary); }
        System.out.println("Analyzer balance report: " + output);
    }
}
