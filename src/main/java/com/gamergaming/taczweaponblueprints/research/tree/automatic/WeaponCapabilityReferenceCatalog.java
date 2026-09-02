package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
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

/** Strict pinned catalog for the shadow v3 capability formula. */
public final class WeaponCapabilityReferenceCatalog {
    public static final int CURRENT_FORMAT = 1;
    public static final int BUNDLED_WEAPON_COUNT = 53;
    public static final String BUNDLED_SOURCE_VERSION = "1.1.8-hotfix";
    public static final String BUNDLED_SOURCE_FINGERPRINT =
            "765a83ea3df8bb3591aba2a5657c1fff75122178381f947a5e6e079e0730fb95";
    public static final String BUNDLED_METRICS_FINGERPRINT =
            "2ab8c81e48fff1ba1a419c85423c5981e13fe9eeac8dba9f6f8e4170e2e42d89";
    public static final String BUNDLED_RESOURCE =
            "/assets/taczweaponblueprints/research/automatic/"
                    + "tacz-1.1.8-capability-v3.json";

    private static final Pattern RESOURCE_ID =
            Pattern.compile("[a-z0-9_.-]+:[a-z0-9/._-]+");
    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "format", "reference_version", "metric_formula", "source",
            "metrics_fingerprint", "blueprints", "metrics");
    private static final Set<String> SOURCE_FIELDS = Set.of(
            "version", "fingerprint", "recipe_backed_guns");

    private final String sourceVersion;
    private final String sourceFingerprint;
    private final String metricsFingerprint;
    private final Set<String> blueprintIds;
    private final WeaponCapabilityReference reference;

    private WeaponCapabilityReferenceCatalog(
            String sourceVersion,
            String sourceFingerprint,
            String metricsFingerprint,
            Set<String> blueprintIds,
            WeaponCapabilityReference reference) {
        this.sourceVersion = sourceVersion;
        this.sourceFingerprint = sourceFingerprint;
        this.metricsFingerprint = metricsFingerprint;
        this.blueprintIds = Collections.unmodifiableSet(new LinkedHashSet<>(blueprintIds));
        this.reference = reference;
    }

    public static WeaponCapabilityReferenceCatalog bundled() {
        return BundledHolder.INSTANCE;
    }

    public static WeaponCapabilityReferenceCatalog parse(String json) {
        if (json == null) {
            throw new IllegalArgumentException("Capability reference JSON cannot be null");
        }
        return parse(new StringReader(json));
    }

    public static WeaponCapabilityReferenceCatalog parse(Reader reader) {
        String json = readBounded(reader);
        final JsonElement parsed;
        try {
            parsed = JsonParser.parseString(json);
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Capability reference JSON is invalid", exception);
        }
        if (!parsed.isJsonObject()) {
            throw new IllegalArgumentException("Capability reference root must be an object");
        }
        JsonObject root = parsed.getAsJsonObject();
        requireFields(root, ROOT_FIELDS, "capability reference");
        if (requiredInteger(root, "format") != CURRENT_FORMAT) {
            throw new IllegalArgumentException("Unsupported capability reference format");
        }
        String referenceVersion = requiredText(root, "reference_version");
        String formulaVersion = requiredText(root, "metric_formula");
        if (!ResearchTechTreeContract.CAPABILITY_REFERENCE_VERSION.equals(referenceVersion)
                || !ResearchTechTreeContract.CAPABILITY_FORMULA_VERSION.equals(formulaVersion)) {
            throw new IllegalArgumentException("Capability reference versions are incompatible");
        }
        JsonObject source = requiredObject(root, "source");
        requireFields(source, SOURCE_FIELDS, "capability reference source");
        String sourceVersion = requiredText(source, "version");
        String sourceFingerprint = requiredText(source, "fingerprint");
        int weaponCount = requiredInteger(source, "recipe_backed_guns");
        if (!SHA_256.matcher(sourceFingerprint).matches() || weaponCount <= 0
                || weaponCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS) {
            throw new IllegalArgumentException("Capability reference source is invalid");
        }
        Set<String> blueprints = blueprints(root.get("blueprints"), weaponCount);
        EnumMap<CapabilityMetric, List<Double>> distributions = distributions(
                requiredObject(root, "metrics"), weaponCount);
        String metricsFingerprint = requiredText(root, "metrics_fingerprint");
        if (!SHA_256.matcher(metricsFingerprint).matches()
                || !metricsFingerprint.equals(fingerprint(distributions))) {
            throw new IllegalArgumentException(
                    "Capability reference metrics fingerprint does not match");
        }
        return new WeaponCapabilityReferenceCatalog(
                sourceVersion,
                sourceFingerprint,
                metricsFingerprint,
                blueprints,
                WeaponCapabilityReference.fromMetricValues(
                        referenceVersion, distributions));
    }

    public static String fingerprint(
            Map<CapabilityMetric, ? extends Collection<Double>> distributions) {
        if (distributions == null) {
            throw new IllegalArgumentException("Capability distributions cannot be null");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (CapabilityMetric metric : CapabilityMetric.values()) {
                Collection<Double> samples = distributions.get(metric);
                if (samples == null) {
                    throw new IllegalArgumentException(
                            "Capability reference is missing " + metric.serializedName());
                }
                digest.update(metric.serializedName().getBytes(StandardCharsets.UTF_8));
                digest.update((byte) 0);
                for (Double sample : samples.stream().sorted().toList()) {
                    if (sample == null || !Double.isFinite(sample)) {
                        throw new IllegalArgumentException(
                                "Capability reference contains an invalid value");
                    }
                    digest.update(Double.toString(sample).getBytes(StandardCharsets.UTF_8));
                    digest.update((byte) '\n');
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public String referenceVersion() {
        return reference.version();
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

    public WeaponCapabilityReference reference() {
        return reference;
    }

    private static String readBounded(Reader reader) {
        if (reader == null) {
            throw new IllegalArgumentException("Capability reference reader cannot be null");
        }
        try {
            StringBuilder result = new StringBuilder();
            char[] buffer = new char[4096];
            int read;
            while ((read = reader.read(buffer)) >= 0) {
                if (result.length() + read
                        > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_CHARACTERS) {
                    throw new IllegalArgumentException(
                            "Capability reference exceeds the character limit");
                }
                result.append(buffer, 0, read);
            }
            return result.toString();
        } catch (IOException exception) {
            throw new IllegalArgumentException("Could not read capability reference", exception);
        }
    }

    private static Set<String> blueprints(JsonElement element, int expectedCount) {
        if (element == null || !element.isJsonArray()) {
            throw new IllegalArgumentException("Capability blueprints must be an array");
        }
        JsonArray array = element.getAsJsonArray();
        if (array.size() != expectedCount) {
            throw new IllegalArgumentException("Capability blueprint count does not match");
        }
        LinkedHashSet<String> result = new LinkedHashSet<>();
        String previous = null;
        for (JsonElement value : array) {
            if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) {
                throw new IllegalArgumentException("Capability blueprint ID must be text");
            }
            String id = value.getAsString();
            if (!validResourceId(id) || !result.add(id)
                    || previous != null && previous.compareTo(id) >= 0) {
                throw new IllegalArgumentException("Capability blueprint IDs are invalid");
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

    private static EnumMap<CapabilityMetric, List<Double>> distributions(
            JsonObject metrics,
            int weaponCount) {
        Set<String> expected = java.util.Arrays.stream(CapabilityMetric.values())
                .map(CapabilityMetric::serializedName)
                .collect(java.util.stream.Collectors.toSet());
        if (!metrics.keySet().equals(expected)) {
            throw new IllegalArgumentException("Capability metric keys are invalid");
        }
        EnumMap<CapabilityMetric, List<Double>> result =
                new EnumMap<>(CapabilityMetric.class);
        for (CapabilityMetric metric : CapabilityMetric.values()) {
            JsonElement value = metrics.get(metric.serializedName());
            if (!value.isJsonArray()
                    || value.getAsJsonArray().size()
                            < WeaponCapabilityScorer.MIN_REFERENCE_SAMPLES
                    || value.getAsJsonArray().size() > weaponCount) {
                throw new IllegalArgumentException(
                        "Capability metric distribution is invalid: "
                                + metric.serializedName());
            }
            List<Double> samples = new ArrayList<>();
            for (JsonElement sample : value.getAsJsonArray()) {
                if (!sample.isJsonPrimitive() || !sample.getAsJsonPrimitive().isNumber()) {
                    throw new IllegalArgumentException("Capability metric sample is invalid");
                }
                double number = sample.getAsDouble();
                if (!Double.isFinite(number)) {
                    throw new IllegalArgumentException("Capability metric sample is not finite");
                }
                samples.add(number);
            }
            if (!samples.equals(samples.stream().sorted().toList())) {
                throw new IllegalArgumentException("Capability samples must be sorted");
            }
            result.put(metric, List.copyOf(samples));
        }
        return result;
    }

    private static JsonObject requiredObject(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonObject()) {
            throw new IllegalArgumentException("Missing capability object " + field);
        }
        return value.getAsJsonObject();
    }

    private static String requiredText(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isString()
                || value.getAsString().isBlank()) {
            throw new IllegalArgumentException("Missing capability text " + field);
        }
        String result = value.getAsString();
        if (!result.equals(result.trim()) || result.length() > 96
                || result.chars().anyMatch(character ->
                        Character.isWhitespace(character) || Character.isISOControl(character))) {
            throw new IllegalArgumentException("Capability text is invalid " + field);
        }
        return result;
    }

    private static int requiredInteger(JsonObject object, String field) {
        JsonElement value = object.get(field);
        if (value == null || !value.isJsonPrimitive()
                || !value.getAsJsonPrimitive().isNumber()) {
            throw new IllegalArgumentException("Missing capability integer " + field);
        }
        double number = value.getAsDouble();
        if (!Double.isFinite(number) || Math.rint(number) != number
                || number < Integer.MIN_VALUE || number > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Capability integer is invalid " + field);
        }
        return (int) number;
    }

    private static void requireFields(JsonObject object, Set<String> fields, String label) {
        if (!object.keySet().equals(fields)) {
            throw new IllegalArgumentException(label + " fields are invalid");
        }
    }

    private static final class BundledHolder {
        private static final WeaponCapabilityReferenceCatalog INSTANCE = load();

        private static WeaponCapabilityReferenceCatalog load() {
            try (InputStream stream = WeaponCapabilityReferenceCatalog.class
                    .getResourceAsStream(BUNDLED_RESOURCE)) {
                if (stream == null) {
                    throw new IllegalStateException("Bundled capability reference is missing");
                }
                WeaponCapabilityReferenceCatalog catalog = parse(
                        new InputStreamReader(stream, StandardCharsets.UTF_8));
                if (catalog.blueprintIds().size() != BUNDLED_WEAPON_COUNT
                        || !BUNDLED_SOURCE_VERSION.equals(catalog.sourceVersion())
                        || !BUNDLED_SOURCE_FINGERPRINT.equals(catalog.sourceFingerprint())
                        || !BUNDLED_METRICS_FINGERPRINT.equals(
                                catalog.metricsFingerprint())) {
                    throw new IllegalStateException("Bundled capability reference drifted");
                }
                return catalog;
            } catch (IOException exception) {
                throw new IllegalStateException("Could not close capability reference", exception);
            }
        }
    }
}
