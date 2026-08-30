package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** Command-line entry point used only by the Gradle authoring task. */
public final class ResearchTechTreeRatingTool {
    private ResearchTechTreeRatingTool() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = parseArguments(args);
        Path pack = requiredPath(options, "--pack");
        Path output = requiredPath(options, "--output");
        String sourceVersion = required(options, "--source-version");
        int expectedCount = positiveInteger(required(options, "--expected-count"), "--expected-count");
        Map<String, AppealRating> appeals = options.containsKey("--appeal")
                ? AppealRatings.load(Path.of(options.get("--appeal")))
                : Map.of();

        var stats = new TaCZGunPackExtractor().extract(pack);
        if (stats.isEmpty()) {
            throw new IOException("TaCZ gun pack contains no recipe-backed guns: " + pack);
        }
        if (stats.size() != expectedCount) {
            throw new IOException("Expected " + expectedCount + " recipe-backed guns but extracted "
                    + stats.size() + " from " + pack);
        }
        var suggestions = new ResearchTechTreeRatingSuggester().suggest(stats, appeals);
        String report = ResearchTechTreeRatingReport.toJson(
                suggestions,
                sourceVersion,
                pack.toAbsolutePath().normalize().getFileName().toString());
        Path normalizedOutput = output.toAbsolutePath().normalize();
        Path parent = normalizedOutput.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Files.writeString(normalizedOutput, report, StandardCharsets.UTF_8);
        long reviewed = suggestions.stream().filter(WeaponRatingSuggestion::appealReviewed).count();
        System.out.println("Wrote " + suggestions.size() + " non-authoritative weapon ratings to "
                + normalizedOutput + " (" + reviewed + " appeal ratings reviewed)");
    }

    private static Map<String, String> parseArguments(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            if (index + 1 >= args.length || !args[index].startsWith("--")) {
                throw new IllegalArgumentException(
                        "Expected --pack, --output, --source-version, --expected-count, "
                                + "and optional --appeal arguments");
            }
            if (result.putIfAbsent(args[index], args[index + 1]) != null) {
                throw new IllegalArgumentException("Duplicate argument " + args[index]);
            }
        }
        String unknown = result.keySet().stream()
                .filter(key -> !java.util.Set.of(
                        "--pack", "--output", "--source-version", "--expected-count", "--appeal")
                        .contains(key))
                .sorted()
                .findFirst()
                .orElse(null);
        if (unknown != null) {
            throw new IllegalArgumentException("Unknown argument " + unknown);
        }
        return Map.copyOf(result);
    }

    private static Path requiredPath(Map<String, String> options, String key) {
        return Path.of(required(options, key));
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
