package com.gamergaming.taczweaponblueprints.analyzer.authoring;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import com.google.gson.*;
import com.mojang.serialization.JsonOps;

/** Reads resolved format-22 catalog quotes; does not reimplement rule or tier selection. */
public final class AnalyzerBalanceReport {
    public static final int FORMAT = 1;
    private static final long MAX_STATES = 20_000_000;
    private static final Set<String> STATUSES = Set.of("legacy", "configured", "disabled", "content_unavailable",
            "tier_unavailable", "stale_progression", "station_tier_required", "origin_disabled", "direct_recovery_only");
    private AnalyzerBalanceReport() { }

    public static JsonObject create(JsonObject catalog, Optional<String> target) {
        if (integer(catalog, "format", 0, Integer.MAX_VALUE) != 22)
            throw new IllegalArgumentException("Analyzer reports require catalog export format 22");
        var result = new JsonObject();
        result.addProperty("format", FORMAT);
        result.addProperty("source_catalog_format", 22);
        result.addProperty("profile", id(catalog, "profile"));
        result.addProperty("scope", "first_blueprint_from_zero_progress");
        result.addProperty("method", "deterministic_recurrence_double_precision");
        result.addProperty("player_eligibility_checked", false);
        result.add("assumptions", new Gson().toJsonTree(List.of(
                "One fixed station, origin, target, and policy until the first physical blueprint.",
                "Each attempt is eligible and paid; inputs, RP, materials, and empty output space remain available.",
                "Fragments are deposited between attempts; reconstruction is free and stops at one blueprint.",
                "No initial progress, competing archive classes, external rewards, RP recovery, or Research Bench purchases.",
                "A physical blueprint does not imply permission to learn it; normal prerequisites still apply.",
                "Expected values are numerical estimates, not exact decimal values or per-player guarantees.")));
        var entries = catalog.getAsJsonArray("entries");
        if (entries == null || entries.size() > 65_536) throw new IllegalArgumentException("missing or oversized catalog entries");
        var ordered = new TreeMap<String, JsonObject>();
        for (var element : entries) {
            var entry = element.getAsJsonObject();
            var name = id(entry, "blueprint");
            if (ordered.put(name, entry) != null) throw new IllegalArgumentException("duplicate catalog target " + name);
        }
        if (target.isPresent() && !ordered.containsKey(target.orElseThrow()))
            throw new IllegalArgumentException("target is absent from catalog: " + target.orElseThrow());
        var rows = new JsonArray();
        var cache = new HashMap<Key, AnalyzerBalanceModel.Estimate>();
        long work = 0;
        int legacy = 0;
        for (var entry : ordered.entrySet()) {
            if (target.isPresent() && !target.orElseThrow().equals(entry.getKey())) continue;
            if (!"gun".equals(string(entry.getValue(), "blueprint_kind"))) {
                if (target.isPresent()) throw new IllegalArgumentException("selected target is not a weapon");
                continue;
            }
            var policy = object(entry.getValue(), "analyzer_policy");
            if (!"policy_only".equals(string(policy, "scope"))) throw new IllegalArgumentException("unsupported Analyzer quote scope");
            if (policy.has("mode") && "legacy".equals(string(policy, "mode"))) { legacy++; continue; }
            var quotes = policy.getAsJsonArray("quotes");
            if (quotes == null || quotes.size() != 9) throw new IllegalArgumentException("expected nine resolved station/origin quotes");
            var sorted = new TreeMap<String, JsonObject>();
            for (var value : quotes) {
                var quote = value.getAsJsonObject();
                var tier = string(quote, "analyzer_tier");
                var origin = string(quote, "origin");
                if (!Set.of("tier_1", "tier_2", "tier_3").contains(tier)
                        || !Set.of("found", "crafted", "unknown").contains(origin)
                        || sorted.put(tier + ":" + origin, quote) != null)
                    throw new IllegalArgumentException("invalid or duplicate station/origin quote");
            }
            for (var quote : sorted.values()) {
                var row = new JsonObject();
                row.addProperty("blueprint", entry.getKey());
                row.add("quote", quote.deepCopy());
                row.addProperty("found_weapon_recovery_mode", string(policy, "found_weapon_recovery_mode"));
                row.addProperty("recovery_mode_scope", policy.has("recovery_mode_scope")
                        ? string(policy, "recovery_mode_scope") : "explicit_diagnostics");
                var status = string(quote, "status");
                if (!STATUSES.contains(status)) throw new IllegalArgumentException("unsupported Analyzer status " + status);
                if (!"configured".equals(status)) {
                    row.addProperty("estimate_status", "not_configured"); rows.add(row); continue;
                }
                var table = AnalyzerOutcomeTable.CODEC.parse(JsonOps.INSTANCE, object(quote, "outcomes"))
                        .getOrThrow(false, message -> { throw new IllegalArgumentException(message); });
                int threshold = integer(quote, "fragment_threshold", 0, AnalyzerBalanceModel.MAX_THRESHOLD);
                int cap = integer(quote, "retained_progress_cap", 0, 1_000_000_000);
                if (cap < threshold) throw new IllegalArgumentException("archive cap is below threshold");
                var attempt = object(quote, "attempt");
                int inputs = attempt.has("input_count") ? integer(attempt, "input_count", 1, 64) : 1;
                var cost = object(attempt, "cost");
                int points = integer(cost, "points", 0, 1_000_000_000);
                var ingredients = cost.has("ingredients") ? cost.getAsJsonArray("ingredients") : new JsonArray();
                if (ingredients == null || ingredients.size() > 6) throw new IllegalArgumentException("invalid ingredient list");
                for (var ingredient : ingredients) integer(ingredient.getAsJsonObject(), "count", 1, 64);
                var key = new Key(table, threshold);
                var estimate = cache.get(key);
                if (estimate == null) {
                    work += table.fragmentWeight() > 0 ? threshold : 1;
                    if (work > MAX_STATES) throw new IllegalArgumentException("report exceeds 20000000 recurrence states; select one --target");
                    estimate = AnalyzerBalanceModel.estimate(table, threshold); cache.put(key, estimate);
                }
                row.addProperty("estimate_status", estimate.reachable() ? "reachable" : "unreachable");
                if (estimate.reachable()) {
                    double expected = estimate.expectedAttempts();
                    row.addProperty("expected_attempts", expected);
                    row.addProperty("expected_weapons_consumed", expected * inputs);
                    row.addProperty("expected_rp_consumed", expected * points);
                    var terms = new JsonArray();
                    for (var ingredient : ingredients) {
                        var term = ingredient.deepCopy().getAsJsonObject();
                        term.addProperty("expected_consumed", expected * integer(term, "count", 1, 64));
                        terms.add(term);
                    }
                    row.add("expected_material_terms", terms);
                } else row.addProperty("reason", "All outcomes are no-result; no finite acquisition expectation.");
                if (estimate.maximumAttempts().isPresent()) row.addProperty("maximum_attempts", estimate.maximumAttempts().getAsLong());
                else row.add("maximum_attempts", JsonNull.INSTANCE);
                rows.add(row);
            }
        }
        result.addProperty("legacy_targets_omitted", legacy);
        result.addProperty("recurrence_states_evaluated", work);
        result.add("rows", rows);
        if (catalog.has("research_data_economy")) result.add("research_economy", ResearchEconomyReport.create(catalog));
        return result;
    }

    private record Key(AnalyzerOutcomeTable table, int threshold) { }
    static String string(JsonObject object, String key) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString())
            throw new IllegalArgumentException("expected string " + key);
        return value.getAsString();
    }
    private static String id(JsonObject object, String key) {
        String value = string(object, key);
        if (value.length() > 256 || !value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"))
            throw new IllegalArgumentException("invalid resource ID " + key);
        return value;
    }
    static JsonObject object(JsonObject object, String key) {
        if (!object.has(key) || !object.get(key).isJsonObject()) throw new IllegalArgumentException("expected object " + key);
        return object.getAsJsonObject(key);
    }
    static int integer(JsonObject object, String key, int min, int max) {
        var value = object.get(key);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber())
            throw new IllegalArgumentException("expected integer " + key);
        int n;
        try { n = value.getAsBigDecimal().intValueExact(); }
        catch (ArithmeticException | NumberFormatException error) { throw new IllegalArgumentException("expected bounded integer " + key, error); }
        if (n < min || n > max) throw new IllegalArgumentException("out of range " + key);
        return n;
    }
}
