package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Strict, immutable metadata and distributions for one mechanical reference population. */
public final class WeaponMechanicalReferenceCatalog {
    public static final int CURRENT_FORMAT = 1;
    public static final int MAX_REFERENCE_WEAPONS = 4096;
    public static final int MAX_REFERENCE_CHARACTERS = 1_000_000;
    public static final int BUNDLED_WEAPON_COUNT = 53;
    public static final String BUNDLED_SOURCE_VERSION = "1.1.8-hotfix";
    public static final String BUNDLED_SOURCE_FINGERPRINT =
            "765a83ea3df8bb3591aba2a5657c1fff75122178381f947a5e6e079e0730fb95";
    public static final String BUNDLED_METRICS_FINGERPRINT =
            "7fbc1233719df0524d15e66b3065bf47241426befc052887b99fa8cb249438d4";
    public static final String BUNDLED_RESOURCE =
            "/assets/taczweaponblueprints/research/automatic/"
                    + "tacz-1.1.8-mechanical-v2.json";

    private static final Pattern RESOURCE_ID =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "format",
            "reference_version",
            "metric_formula",
            "source",
            "metrics_fingerprint",
            "blueprints",
            "metrics");
    private static final Set<String> SOURCE_FIELDS = Set.of(
            "version", "fingerprint", "recipe_backed_guns");

    private final String referenceVersion;
    private final String sourceVersion;
    private final String sourceFingerprint;
    private final String metricsFingerprint;
    private final Set<String> blueprintIds;
    private final WeaponMetricReference reference;

    private WeaponMechanicalReferenceCatalog(
            String referenceVersion,
            String sourceVersion,
            String sourceFingerprint,
            String metricsFingerprint,
            Set<String> blueprintIds,
            WeaponMetricReference reference) {
        this.referenceVersion = referenceVersion;
        this.sourceVersion = sourceVersion;
        this.sourceFingerprint = sourceFingerprint;
        this.metricsFingerprint = metricsFingerprint;
        this.blueprintIds = Collections.unmodifiableSet(new LinkedHashSet<>(blueprintIds));
        this.reference = reference;
    }

    /** Loads the checked-in reference rather than a datapack-overridable resource. */
    public static WeaponMechanicalReferenceCatalog bundled() {
        return BundledHolder.INSTANCE;
    }

    public static WeaponMechanicalReferenceCatalog parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("Weapon mechanical reference JSON cannot be null");
        }
        return parse(new StringReader(json));
    }

    public static WeaponMechanicalReferenceCatalog parse(Reader reader) {
        String json = readBounded(reader);
        final JsonObject root;
        try {
            JsonElement parsed = JsonParser.parseString(json);
            if (!parsed.isJsonObject()) {
                throw new IllegalArgumentException(
                        "Weapon mechanical reference root must be an object");
            }
            root = parsed.getAsJsonObject();
        } catch (RuntimeException exception) {
            if (exception instanceof IllegalArgumentException illegalArgument) {
                throw illegalArgument;
            }
            throw new IllegalArgumentException(
                    "Weapon mechanical reference JSON is invalid", exception);
        }

        requireFields(root, ROOT_FIELDS, "reference root");
        int format = requiredInteger(root, "format");
        if (format != CURRENT_FORMAT) {
            throw new IllegalArgumentException(
                    "Unsupported weapon mechanical reference format " + format);
        }
        String referenceVersion = requiredText(root, "reference_version");
        String metricFormula = requiredText(root, "metric_formula");
        if (!ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION.equals(metricFormula)) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference uses incompatible formula " + metricFormula);
        }

        JsonObject source = requiredObject(root, "source");
        requireFields(source, SOURCE_FIELDS, "reference source");
        String sourceVersion = requiredText(source, "version");
        String sourceFingerprint = requiredText(source, "fingerprint");
        if (!SHA_256.matcher(sourceFingerprint).matches()) {
            throw new IllegalArgumentException(
                    "Weapon mechanical source fingerprint must be SHA-256");
        }
        int weaponCount = requiredInteger(source, "recipe_backed_guns");
        if (weaponCount <= 0 || weaponCount > MAX_REFERENCE_WEAPONS) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference weapon count is out of bounds");
        }

        Set<String> blueprintIds = blueprints(root.get("blueprints"), weaponCount);
        EnumMap<MechanicalMetric, List<Double>> distributions = distributions(
                requiredObject(root, "metrics"), weaponCount);
        String metricsFingerprint = requiredText(root, "metrics_fingerprint");
        if (!SHA_256.matcher(metricsFingerprint).matches()
                || !metricsFingerprint.equals(fingerprint(distributions))) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference metrics fingerprint does not match");
        }
        WeaponMetricReference reference = WeaponMetricReference.fromMetricValues(
                referenceVersion, distributions);
        return new WeaponMechanicalReferenceCatalog(
                referenceVersion,
                sourceVersion,
                sourceFingerprint,
                metricsFingerprint,
                blueprintIds,
                reference);
    }

    public static String fingerprint(
            Map<MechanicalMetric, ? extends java.util.Collection<Double>> distributions) {
        if (distributions == null) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference distributions cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (MechanicalMetric metric : MechanicalMetric.values()) {
                java.util.Collection<Double> samples = distributions.get(metric);
                if (samples == null) {
                    throw new IllegalArgumentException(
                            "Weapon mechanical reference is missing metric "
                                    + metric.serializedName());
                }
                digest.update(metric.serializedName().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                List<Double> ordered = samples.stream().sorted().toList();
                for (Double sample : ordered) {
                    if (sample == null || !Double.isFinite(sample)) {
                        throw new IllegalArgumentException(
                                "Weapon mechanical reference contains an invalid metric value");
                    }
                    digest.update(Double.toString(sample).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    public String referenceVersion() {
        return referenceVersion;
    }

    public String sourceVersion() {
        return sourceVersion;
    }

    public String sourceFingerprint() {
        return sourceFingerprint;
    }

    public String metricsFingerprint() {
        return metricsFingerprint;
    }

    public Set<String> blueprintIds() {
        return blueprintIds;
    }

    public WeaponMetricReference reference() {
        return reference;
    }

    private static String readBounded(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("Weapon mechanical reference reader cannot be null");
        }
        try {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (result.length() + read > MAX_REFERENCE_CHARACTERS) {
                    throw new IllegalArgumentException(
                            "Weapon mechanical reference exceeds the character limit");
                }
                result.append(buffer, 0, read);
            }
            return result.toString();
        } catch (IOException exception) {
            throw new IllegalArgumentException(
                    "Could not read weapon mechanical reference", exception);
        }
    }

    private static Set<String> blueprints(JsonElement element, int expectedCount) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference blueprints must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != expectedCount) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference blueprint count does not match source metadata");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String previous = null;
        for (JsonElement value : array) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException(
                        "Weapon mechanical reference blueprint IDs must be strings");
            }
            String id = value.getAsString();
            if (!validResourceId(id)
                    || (previous != null && previous.compareTo(id) >= 0)
                    || !result.add(id)) {
                throw new IllegalArgumentException(
                        "Weapon mechanical reference blueprint IDs must be valid, unique, and sorted");
            }
            previous = id;
        }
        return result;
    }

    private static boolean validResourceId(String value) {
        if (!RESOURCE_ID.matcher(value).matches()) {
            return false;
        }
        int separator = value.indexOf(':');
        return java.util.Arrays.stream(value.substring(separator + 1).split("/", -1))
                .noneMatch(segment -> segment.isEmpty()
                        || ".".equals(segment) || "..".equals(segment));
    }

    private static EnumMap<MechanicalMetric, List<Double>> distributions(
            JsonObject metrics,
            int weaponCount) {
        Set<String> expectedFields = java.util.Arrays.stream(MechanicalMetric.values())
                .map(MechanicalMetric::serializedName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        requireFields(metrics, expectedFields, "reference metrics");
        EnumMap<MechanicalMetric, List<Double>> result =
                new EnumMap<>(MechanicalMetric.class);
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            JsonElement element = metrics.get(metric.serializedName());
            if (element == null || !element.isJsonArray()) {
                throw new IllegalArgumentException(
                        "Weapon mechanical reference metric must be an array: "
                                + metric.serializedName());
            }
            List<Double> values = new ArrayList<>();
            for (JsonElement value : element.getAsJsonArray()) {
                if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException(
                            "Weapon mechanical reference metric contains a non-number: "
                                    + metric.serializedName());
                }
                double number = value.getAsDouble();
                if (!Double.isFinite(number)) {
                    throw new IllegalArgumentException(
                            "Weapon mechanical reference metric contains a non-finite value: "
                                    + metric.serializedName());
                }
                values.add(number);
            }
            if (values.size() < WeaponMechanicalScorer.MIN_REFERENCE_SAMPLES
                    || values.size() > weaponCount) {
                throw new IllegalArgumentException(
                        "Weapon mechanical reference metric sample count is invalid: "
                                + metric.serializedName());
            }
            List<Double> ordered = values.stream().sorted().toList();
            if (!values.equals(ordered)) {
                throw new IllegalArgumentException(
                        "Weapon mechanical reference metric samples must be sorted: "
                                + metric.serializedName());
            }
            result.put(metric, ordered);
        }
        return result;
    }

    private static void requireFields(JsonObject object, Set<String> fields, String label) {
        Set<String> actual = object.keySet();
        if (!actual.equals(fields)) {
            Set<String> missing = new java.util.TreeSet<>(fields);
            missing.removeAll(actual);
            Set<String> unknown = new java.util.TreeSet<>(actual);
            unknown.removeAll(fields);
            throw new IllegalArgumentException(
                    "Weapon mechanical " + label + " fields are invalid; missing="
                            + missing + ", unknown=" + unknown);
        }
    }

    private static JsonObject requiredObject(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference field must be an object: " + field);
        }
        return value.getAsJsonObject();
    }

    private static String requiredText(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference field must be text: " + field);
        }
        String result = value.getAsString();
        if (result.isBlank() || !result.equals(result.trim()) || result.length() > 96
                || result.chars().anyMatch(character ->
                        Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference text field is invalid: " + field);
        }
        return result;
    }

    private static int requiredInteger(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference field must be an integer: " + field);
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || Math.rint(number) != number
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException(
                    "Weapon mechanical reference field must be an integer: " + field);
        }
        return (int) number;
    }

    private static final class BundledHolder {
        private static final WeaponMechanicalReferenceCatalog INSTANCE = load();

        private static WeaponMechanicalReferenceCatalog load() {
            try (var stream = WeaponMechanicalReferenceCatalog.class
                    .getResourceAsStream(BUNDLED_RESOURCE)) {
                if (stream == null) {
                    throw new IllegalStateException(
                            "Bundled weapon mechanical reference is missing: "
                                    + BUNDLED_RESOURCE);
                }
                WeaponMechanicalReferenceCatalog catalog = parse(
                        new java.io.InputStreamReader(stream, StandardCharsets.UTF_8));
                if (!ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION.equals(
                        catalog.referenceVersion())
                        || !BUNDLED_SOURCE_VERSION.equals(catalog.sourceVersion())
                        || !BUNDLED_SOURCE_FINGERPRINT.equals(catalog.sourceFingerprint())
                        || !BUNDLED_METRICS_FINGERPRINT.equals(catalog.metricsFingerprint())
                        || catalog.blueprintIds().size() != BUNDLED_WEAPON_COUNT) {
                    throw new IllegalStateException(
                            "Bundled weapon mechanical reference is not the pinned current catalog");
                }
                return catalog;
            } catch (IOException exception) {
                throw new IllegalStateException(
                        "Could not close bundled weapon mechanical reference", exception);
            }
        }
    }
}
