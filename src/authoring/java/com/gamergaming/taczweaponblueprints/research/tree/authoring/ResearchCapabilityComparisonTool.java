package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityComparison;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityScorer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScorer;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Writes an auditable full-catalog v2-versus-v3 shadow comparison. */
public final class ResearchCapabilityComparisonTool {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ResearchCapabilityComparisonTool() {
    }

    public static void main(String[] args) throws IOException {
        Map<String, String> options = arguments(args);
        Path pack = Path.of(required(options, "--pack"));
        Path output = Path.of(required(options, "--output"));
        int expectedCount = Integer.parseInt(required(options, "--expected-count"));
        var stats = new TaCZGunPackExtractor().extract(pack);
        if (expectedCount <= 0 || stats.size() != expectedCount) {
            throw new IOException("Capability comparison gun count does not match");
        }
        var mechanicalReference = WeaponMechanicalReferenceCatalog.bundled();
        var capabilityReference = WeaponCapabilityReferenceCatalog.bundled();
        WeaponMechanicalScorer mechanicalScorer = new WeaponMechanicalScorer();
        WeaponCapabilityScorer capabilityScorer = new WeaponCapabilityScorer();

        JsonArray weapons = new JsonArray();
        int changedBands = 0;
        int maximumAbsoluteDelta = 0;
        for (TaCZGunStats stat : stats) {
            var evidence = stat.mechanicalEvidence();
            var mechanical = mechanicalScorer.score(
                    evidence, mechanicalReference.reference());
            var capability = capabilityScorer.score(
                    evidence, capabilityReference.reference());
            WeaponCapabilityComparison comparison =
                    WeaponCapabilityComparison.compare(mechanical, capability);
            if (comparison.tierDelta() != 0) {
                changedBands++;
            }
            maximumAbsoluteDelta = Math.max(
                    maximumAbsoluteDelta, Math.abs(comparison.scoreDelta()));

            JsonObject weapon = new JsonObject();
            weapon.addProperty("blueprint", stat.blueprintId());
            weapon.addProperty("archetype", stat.gunType());
            weapon.addProperty("mechanical_v2", comparison.mechanicalV2Score());
            weapon.addProperty("capability_v3", comparison.capabilityV3Score());
            weapon.addProperty("delta", comparison.scoreDelta());
            weapon.addProperty("mechanical_v2_tier",
                    comparison.mechanicalV2Tier().name().toLowerCase(Locale.ROOT));
            weapon.addProperty("capability_v3_tier",
                    comparison.capabilityV3Tier().name().toLowerCase(Locale.ROOT));
            weapon.addProperty("tier_delta", comparison.tierDelta());
            weapon.addProperty("confidence", capability.confidence());
            JsonObject packages = new JsonObject();
            capability.packageScores().forEach((key, value) ->
                    packages.addProperty(key.serializedName(), value));
            weapon.add("packages", packages);
            JsonArray warnings = new JsonArray();
            capability.warnings().forEach(warnings::add);
            weapon.add("warnings", warnings);
            weapons.add(weapon);
        }

        JsonObject root = new JsonObject();
        root.addProperty("format", 2);
        root.addProperty("mechanical_formula",
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION);
        root.addProperty("mechanical_reference", mechanicalReference.referenceVersion());
        root.addProperty("capability_formula",
                ResearchTechTreeContract.CAPABILITY_FORMULA_VERSION);
        root.addProperty("capability_reference", capabilityReference.referenceVersion());
        root.addProperty("weapon_count", stats.size());
        root.addProperty("changed_suggested_bands", changedBands);
        root.addProperty("maximum_absolute_score_delta", maximumAbsoluteDelta);
        root.add("weapons", weapons);

        Path normalizedOutput = output.toAbsolutePath().normalize();
        if (normalizedOutput.getParent() != null) {
            Files.createDirectories(normalizedOutput.getParent());
        }
        writeAtomically(
                normalizedOutput,
                GSON.toJson(root) + System.lineSeparator());
        System.out.println("Wrote capability comparison to " + normalizedOutput);
    }

    private static Map<String, String> arguments(String[] args) {
        Map<String, String> result = new LinkedHashMap<>();
        for (int index = 0; index < args.length; index += 2) {
            if (index + 1 >= args.length
                    || result.putIfAbsent(args[index], args[index + 1]) != null) {
                throw new IllegalArgumentException("Capability comparison arguments are invalid");
            }
        }
        if (!java.util.Set.of("--pack", "--output", "--expected-count")
                .containsAll(result.keySet())) {
            throw new IllegalArgumentException("Unknown capability comparison argument");
        }
        return Map.copyOf(result);
    }

    private static void writeAtomically(Path output, String content) throws IOException {
        Path parent = output.getParent();
        Path temporary = Files.createTempFile(
                parent == null ? Path.of(".") : parent,
                ".capability-comparison-",
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
}
