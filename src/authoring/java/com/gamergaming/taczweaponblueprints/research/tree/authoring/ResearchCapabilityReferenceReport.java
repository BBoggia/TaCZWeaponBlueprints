package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.CapabilityMetric;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityReference;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityReferenceCatalog;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Deterministic writer for the checked-in v3 capability reference. */
public final class ResearchCapabilityReferenceReport {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ResearchCapabilityReferenceReport() {
    }

    public static String toJson(List<TaCZGunStats> stats, String sourceVersion) {
        if (stats == null || stats.isEmpty()
                || stats.stream().anyMatch(java.util.Objects::isNull)
                || sourceVersion == null || sourceVersion.isBlank()) {
            throw new IllegalArgumentException("Capability reference inputs are invalid");
        }
        List<TaCZGunStats> ordered = stats.stream()
                .sorted(java.util.Comparator.comparing(TaCZGunStats::blueprintId))
                .toList();
        if (ordered.stream().map(TaCZGunStats::blueprintId).distinct().count()
                != ordered.size()) {
            throw new IllegalArgumentException("Capability reference contains duplicate IDs");
        }
        WeaponCapabilityReference reference = WeaponCapabilityReference.fromEvidence(
                ResearchTechTreeContract.CAPABILITY_REFERENCE_VERSION,
                ordered.stream().map(TaCZGunStats::mechanicalEvidence).toList());

        JsonObject root = new JsonObject();
        root.addProperty("format", WeaponCapabilityReferenceCatalog.CURRENT_FORMAT);
        root.addProperty("reference_version",
                ResearchTechTreeContract.CAPABILITY_REFERENCE_VERSION);
        root.addProperty("metric_formula",
                ResearchTechTreeContract.CAPABILITY_FORMULA_VERSION);
        JsonObject source = new JsonObject();
        source.addProperty("version", sourceVersion);
        source.addProperty("fingerprint", sourceFingerprint(ordered));
        source.addProperty("recipe_backed_guns", ordered.size());
        root.add("source", source);
        root.addProperty("metrics_fingerprint",
                WeaponCapabilityReferenceCatalog.fingerprint(reference.distributions()));
        JsonArray blueprints = new JsonArray();
        ordered.forEach(stat -> blueprints.add(stat.blueprintId()));
        root.add("blueprints", blueprints);
        JsonObject metrics = new JsonObject();
        for (CapabilityMetric metric : CapabilityMetric.values()) {
            JsonArray samples = new JsonArray();
            reference.distributions().get(metric).forEach(samples::add);
            metrics.add(metric.serializedName(), samples);
        }
        root.add("metrics", metrics);
        return GSON.toJson(root) + System.lineSeparator();
    }

    private static String sourceFingerprint(List<TaCZGunStats> stats) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (TaCZGunStats stat : stats) {
                digest.update(stat.blueprintId().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                digest.update(stat.sourceHash().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) '\n');
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
