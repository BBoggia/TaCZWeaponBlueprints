package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Offline command that regenerates the checked-in default mechanical reference. */
public final class ResearchAutomaticReferenceTool {
    private ResearchAutomaticReferenceTool() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = parseArguments(args);
        Path pack = Path.of(required(options, "--pack"));
        Path output = Path.of(required(options, "--output"));
        String sourceVersion = required(options, "--source-version");
        int expectedCount = positiveInteger(
                required(options, "--expected-count"), "--expected-count");

        var stats = new TaCZGunPackExtractor().extract(pack);
        if (stats.size() != expectedCount) {
            throw new IOException(
                    "Expected " + expectedCount + " recipe-backed guns but extracted "
                            + stats.size() + " from " + pack);
        }
        String json = ResearchAutomaticReferenceReport.toJson(stats, sourceVersion);
        Path normalizedOutput = output.toAbsolutePath().normalize();
        Path parent = normalizedOutput.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(normalizedOutput, json, StandardCharsets.UTF_8);
        System.out.println(
                "Wrote " + stats.size() + " mechanical reference weapons to "
                        + normalizedOutput);
    }

    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            if (index + 1 >= args.length || !args[index].startsWith("--")) {
                throw new IllegalArgumentException(
                        "Expected --pack, --output, --source-version, and --expected-count arguments");
            }
            if (result.putIfAbsent(args[index], args[index + 1]) != null) {
                throw new IllegalArgumentException("Duplicate argument " + args[index]);
            }
        }
        String unknown = result.keySet().stream()
                .filter(key -> !Set.of(
                        "--pack", "--output", "--source-version", "--expected-count")
                        .contains(key))
                .sorted()
                .findFirst()
                .orElse(null);
        if (unknown != null) {
            throw new IllegalArgumentException("Unknown argument " + unknown);
        }
        return Map.copyOf(result);
    }

    private static String required(Map<String, String> options, String key) {
        String value = options.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing argument " + key);
        }
        return value;
    }

    private static int positiveInteger(String value, String key) {
        try {
            int parsed = Integer.parseInt(value);
            if (parsed <= 0 || parsed > TaCZGunPackExtractor.MAX_GUNS) {
                throw new IllegalArgumentException(
                        key + " must be between 1 and " + TaCZGunPackExtractor.MAX_GUNS);
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }
}
