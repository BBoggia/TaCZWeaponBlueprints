package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;

/** Stable JSON report writer for human review before authored placements exist. */
public final class ResearchTechTreeRatingReport {
    public static final int CURRENT_FORMAT = 1;
    public static final String PHASE_ZERO_FORMULA = "55-combat_20-utility_25-appeal";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private ResearchTechTreeRatingReport() {
    }

    public static String toJson(
            List<WeaponRatingSuggestion> suggestions,
            String sourceVersion,
            String sourceDescription) {
        if (suggestions == null || suggestions.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Rating suggestions cannot be null");
        }
        if (sourceVersion == null || sourceVersion.isBlank()
                || sourceDescription == null || sourceDescription.isBlank()) {
            throw new IllegalArgumentException("Rating report source metadata cannot be blank");
        }

        JsonObject root = new JsonObject();
        root.addProperty("format", CURRENT_FORMAT);
        root.addProperty("authoritative", false);
        root.addProperty("purpose", "authoring_suggestions_only");
        root.addProperty("formula", PHASE_ZERO_FORMULA);
        root.addProperty("metric_formula", ResearchTechTreeRatingSuggester.FORMULA_VERSION);
        JsonObject source = new JsonObject();
        source.addProperty("version", sourceVersion);
        source.addProperty("description", sourceDescription);
        source.addProperty("fingerprint", fingerprint(suggestions));
        source.addProperty("recipe_backed_guns", suggestions.size());
        root.add("source", source);

        JsonObject policy = new JsonObject();
        policy.addProperty("combat_weight", ResearchTechTreeContract.COMBAT_WEIGHT);
        policy.addProperty("utility_weight", ResearchTechTreeContract.UTILITY_WEIGHT);
        policy.addProperty("appeal_weight", ResearchTechTreeContract.APPEAL_WEIGHT);
        policy.addProperty("unreviewed_appeal", ResearchTechTreeRatingSuggester.DEFAULT_UNREVIEWED_APPEAL);
        policy.addProperty("maximum_appeal_tier_shift", ResearchTechTreeContract.MAX_APPEAL_TIER_SHIFT);
        policy.addProperty("missing_metric_percentile", 50);
        root.add("policy", policy);

        JsonArray entries = new JsonArray();
        for (WeaponRatingSuggestion suggestion : suggestions.stream()
                .sorted(java.util.Comparator
                        .comparingInt(WeaponRatingSuggestion::weightedScore)
                        .thenComparing(value -> value.stats().blueprintId()))
                .toList()) {
            entries.add(entry(suggestion));
        }
        root.add("recommendations", entries);
        return GSON.toJson(root) + System.lineSeparator();
    }

    private static JsonObject entry(WeaponRatingSuggestion suggestion) {
        TaCZGunStats stats = suggestion.stats();
        JsonObject entry = new JsonObject();
        entry.addProperty("blueprint", stats.blueprintId());
        entry.addProperty("gun_type", stats.gunType());
        entry.addProperty("data", stats.dataId());
        entry.addProperty("source_hash", stats.sourceHash());

        JsonObject evidence = new JsonObject();
        add(evidence, "base_damage", stats.baseDamage());
        add(evidence, "explosion_damage", stats.explosionDamage());
        add(evidence, "rounds_per_minute", stats.roundsPerMinute());
        add(evidence, "magazine_capacity", stats.magazineCapacity());
        add(evidence, "reload_seconds", stats.reloadSeconds());
        add(evidence, "projectile_speed", stats.projectileSpeed());
        add(evidence, "effective_range", stats.effectiveRange());
        add(evidence, "armor_ignore", stats.armorIgnore());
        add(evidence, "headshot_multiplier", stats.headshotMultiplier());
        add(evidence, "pierce", stats.pierce());
        add(evidence, "aim_time_seconds", stats.aimTimeSeconds());
        add(evidence, "draw_time_seconds", stats.drawTimeSeconds());
        add(evidence, "weight", stats.weight());
        add(evidence, "aimed_inaccuracy", stats.aimedInaccuracy());
        add(evidence, "recoil_magnitude", stats.recoilMagnitude());
        add(evidence, "movement_speed_while_aiming", stats.movementSpeedWhileAiming());
        add(evidence, "fire_mode_count", stats.fireModeCount());
        add(evidence, "attachment_type_count", stats.attachmentTypeCount());
        add(evidence, "bolt_action_seconds", stats.boltActionSeconds());
        if (stats.scriptId() == null) {
            evidence.add("script", JsonNull.INSTANCE);
        } else {
            evidence.addProperty("script", stats.scriptId());
        }
        evidence.addProperty("reload_type", stats.reloadType());
        evidence.addProperty("explosive", stats.explosive());
        entry.add("evidence", evidence);

        JsonObject derived = new JsonObject();
        add(derived, "effective_damage", suggestion.effectiveDamage());
        add(derived, "sustained_damage_per_second", suggestion.sustainedDamagePerSecond());
        entry.add("derived", derived);

        JsonObject scores = new JsonObject();
        scores.addProperty("combat", suggestion.combatScore());
        scores.addProperty("utility", suggestion.utilityScore());
        scores.addProperty("appeal", suggestion.appealScore());
        scores.addProperty("mechanical", suggestion.mechanicalScore());
        scores.addProperty("weighted", suggestion.weightedScore());
        scores.addProperty("suggested_tier", suggestion.suggestedTier().name().toLowerCase(Locale.ROOT));
        entry.add("scores", scores);

        JsonObject appeal = new JsonObject();
        appeal.addProperty("reviewed", suggestion.appealReviewed());
        if (suggestion.appealReason() == null) {
            appeal.add("reason", JsonNull.INSTANCE);
        } else {
            appeal.addProperty("reason", suggestion.appealReason());
        }
        entry.add("appeal_review", appeal);

        JsonObject percentiles = new JsonObject();
        suggestion.metricPercentiles().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(metric -> percentiles.addProperty(metric.getKey(), metric.getValue()));
        entry.add("metric_percentiles", percentiles);
        JsonArray warnings = new JsonArray();
        suggestion.warnings().forEach(warnings::add);
        entry.add("warnings", warnings);
        return entry;
    }

    private static void add(JsonObject object, String field, Double value) {
        if (value == null) {
            object.add(field, JsonNull.INSTANCE);
        } else {
            object.addProperty(field, rounded(value));
        }
    }

    private static void add(JsonObject object, String field, Integer value) {
        if (value == null) {
            object.add(field, JsonNull.INSTANCE);
        } else {
            object.addProperty(field, value);
        }
    }

    private static double rounded(double value) {
        return Math.round(value * 1000.0) / 1000.0;
    }

    private static String fingerprint(List<WeaponRatingSuggestion> suggestions) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            suggestions.stream()
                    .sorted(java.util.Comparator.comparing(suggestion -> suggestion.stats().blueprintId()))
                    .forEach(suggestion -> {
                        digest.update(suggestion.stats().blueprintId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update((byte) 0);
                        digest.update(suggestion.stats().sourceHash().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update((byte) '\n');
                    });
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
