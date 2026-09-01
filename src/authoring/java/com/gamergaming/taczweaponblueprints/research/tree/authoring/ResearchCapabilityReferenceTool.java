package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Offline command that regenerates the checked-in v3 capability reference. */
public final class ResearchCapabilityReferenceTool {
    private ResearchCapabilityReferenceTool() {
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
            throw new IOException("Expected " + expectedCount + " guns but extracted "
                    + stats.size() + " from " + pack);
        }
        Path normalizedOutput = output.toAbsolutePath().normalize();
        if (normalizedOutput.getParent() != null) {
            Files.createDirectories(normalizedOutput.getParent());
        }
        writeAtomically(
                normalizedOutput,
                ResearchCapabilityReferenceReport.toJson(stats, sourceVersion));
        System.out.println("Wrote " + stats.size()
                + " capability reference weapons to " + normalizedOutput);
    }

    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            if (index + 1 >= args.length || !args[index].startsWith("--")
                    || result.putIfAbsent(args[index], args[index + 1]) != null) {
                throw new IllegalArgumentException("Capability reference arguments are invalid");
            }
        }
        Set<String> allowed = Set.of(
                "--pack", "--output", "--source-version", "--expected-count");
        if (!allowed.containsAll(result.keySet())) {
            throw new IllegalArgumentException("Unknown capability reference argument");
        }
        return Map.copyOf(result);
    }

    private static void writeAtomically(Path output, String content) throws IOException {
        Path parent = output.getParent();
        Path temporary = Files.createTempFile(
                parent == null ? Path.of(".") : parent,
                ".capability-reference-",
                ".tmp");
        try {
            Files.writeString(temporary, content, StandardCharsets.UTF_8);
            try {
                Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
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
                throw new IllegalArgumentException(key + " is out of bounds");
            }
            return parsed;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException(key + " must be an integer", exception);
        }
    }
}
