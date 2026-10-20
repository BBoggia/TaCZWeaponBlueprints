package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.nio.charset.StandardCharsets;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.google.gson.*;
import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class ResearchDataPolicyTest {
    static final ResourceLocation PROFILE = BlueprintAnalyzerPolicyTest.PROFILE, POLICY = id("test:data"), SHARED = id("test:shared");
    static final ResearchDataPoolResolver.PlayerView EMPTY_PLAYER = new ResearchDataPoolResolver.PlayerView(Set.of(), Set.of(), Map.of());
    static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    static JsonObject json(String name) {
        try (var input = ResearchDataPolicyTest.class.getResourceAsStream("/research-data-policy/" + name + ".json")) {
            return JsonParser.parseString(new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) { throw new AssertionError(e); }
    }
    static <T> T decode(Codec<T> codec, JsonElement json) { return BlueprintAnalyzerPolicyTest.decode(codec, json); }
    static ResearchDataPolicy policy(String name) { return decode(ResearchDataPolicy.CODEC, json(name)); }
    static AnalysisReconstructionDefinition shared() { return decode(AnalysisReconstructionDefinition.CODEC, json("shared")); }
    static BlueprintAnalyzerPolicyResolver.Context context(ResearchDataPolicy policy, Map<ResourceLocation, Integer> tiers) {
        return context(policy, tiers, Map.of(SHARED, shared()), false);
    }
    static BlueprintAnalyzerPolicyResolver.Context context(ResearchDataPolicy policy, Map<ResourceLocation, Integer> tiers,
            Map<ResourceLocation, AnalysisReconstructionDefinition> shared, boolean tiered) {
        var profile = BlueprintAnalyzerPolicyTest.profileJson();
        profile.addProperty("tree_enabled", true);
        profile.getAsJsonObject("domain_policies").getAsJsonObject("weapons").addProperty("tree_enabled", true);
        if (!tiered) { profile.remove("analyzer"); profile.addProperty("format", 4); }
        var catalog = new HashMap<ResourceLocation, BlueprintData>(); var rules = new HashMap<ResourceLocation, BlueprintResearchRule>();
        tiers.forEach((id, tier) -> {
            catalog.put(id, new BlueprintData(id.toString(), "name", "tip", id("test:recipe/" + id.getPath()), null, "rifle", id("tacz:slot"), BlueprintKind.GUN));
            rules.put(id, decode(BlueprintResearchRule.CODEC, JsonParser.parseString("{\"format\":5,\"profile\":\"" + PROFILE
                    + "\",\"target\":{\"blueprints\":[\"" + id + "\"]},\"progression\":{\"research_tier\":\"tier_" + tier + "\"}}")));
        });
        var snapshot = BlueprintResearchSnapshot.create(Map.of(), Map.of(PROFILE, decode(BlueprintResearchProfile.CODEC, profile)), rules)
                .withResearchData(new ResearchDataSnapshot(Map.of(POLICY, policy), shared));
        var progression = BlueprintProgressionPolicyResolver.resolve(snapshot, catalog, 4, 7, Map.of(), AutomaticWorkbenchTierPercentiles.DEFAULT);
        return new BlueprintAnalyzerPolicyResolver.Context(snapshot, catalog, 7, 4, progression);
    }
    static ResearchDataPoolResolver.Pool pool(BlueprintAnalyzerPolicyResolver.Context source, ResearchDataPoolResolver.PlayerView player) {
        return ResearchDataPoolResolver.evaluate(new ResearchDataPoolResolver.Context(source), POLICY, PROFILE, AnalyzerTier.TIER_1,
                ResearchDataPoolResolver.Defaults.STANDARD, player, ignored -> true);
    }
    static Map<ResourceLocation, Integer> threeTiers() { return Map.of(id("test:one"), 1, id("test:two"), 2, id("test:three"), 3); }

    @ParameterizedTest @CsvSource({"note,1", "report,2", "dossier,3"})
    void defaultPoolsAndRoundTrips(String name, int tierCount) {
        var p = policy(name);
        assertEquals(p, decode(ResearchDataPolicy.CODEC, ResearchDataPolicy.CODEC.encodeStart(JsonOps.INSTANCE, p).getOrThrow()));
        var resolved = pool(context(p, threeTiers()), EMPTY_PLAYER);
        assertTrue(resolved.ready()); assertEquals(tierCount, resolved.tiers().size());
        assertTrue(resolved.tiers().stream().allMatch(t -> t.tier().level() <= tierCount));
        if (name.equals("note")) for (var station : AnalyzerTier.values()) assertEquals(0, p.outcome(station, AnalyzerTier.TIER_1).blueprintWeight());
        assertEquals(AnalysisRewardClass.PROTECTED_PREREQUISITES, p.rewardClass());
    }
    @ParameterizedTest @CsvSource({"note,1", "report,2", "dossier,3"})
    void singleAnalyzerUsesChosenDataRewardsWithoutChangingTargetPools(String name, int targetTiers) {
        var definition = json(name); definition.addProperty("minimum_analyzer_tier", "tier_3");
        var policy = decode(ResearchDataPolicy.CODEC, definition);
        var original = context(policy, threeTiers());
        var setup = new com.gamergaming.taczweaponblueprints.progression.StationSetup(
                com.gamergaming.taczweaponblueprints.progression.StationSetup.AnalyzerMode.SINGLE,
                com.gamergaming.taczweaponblueprints.progression.StationSetup.ResearchAccess.SINGLE, AnalyzerTier.TIER_2);
        var source = new BlueprintAnalyzerPolicyResolver.Context(original.research(), original.catalog(),
                original.researchRevision(), original.catalogRevision(), original.progression(), setup);
        for (var station : AnalyzerTier.values()) {
            var result = ResearchDataPoolResolver.evaluate(new ResearchDataPoolResolver.Context(source), POLICY, PROFILE,
                    station, ResearchDataPoolResolver.Defaults.STANDARD, EMPTY_PLAYER, ignored -> true);
            assertTrue(result.ready()); assertEquals(targetTiers, result.tiers().size());
            for (var tier : result.tiers()) for (var candidate : tier.candidates()) {
                assertTrue(candidate.tier().level() <= targetTiers);
                assertEquals(policy.outcome(AnalyzerTier.TIER_2, candidate.tier()), candidate.outcomes());
            }
        }
    }

    @ParameterizedTest @ValueSource(strings = {
            "\"format\":2", "\"enabled\":null", "\"enabled\":\"true\"", "\"priority\":1.5", "\"progress_bonus\":1001",
            "\"unknown\":true", "\"input\":{\"item\":\"paper\"}", "\"input\":{\"item\":\"test:data\",\"tag\":\"test:data\"}",
            "\"pool\":{\"tier_weights\":{\"tier_1\":0}}", "\"pool\":{\"tier_weights\":{\"tier_4\":1}}",
            "\"pool\":{\"tier_weights\":{\"tier_1\":1.00000000001}}", "\"pool\":{\"tier_weights\":{\"tier_1\":1000000001}}",
            "\"cost\":{\"points\":0.1}", "\"cost\":{\"points\":0,\"ingredients\":[{\"items\":[\"minecraft:paper\"],\"count\":1.5}]}"
    })
    void malformedPoliciesAreRejected(String change) {
        var j = json("report"); JsonParser.parseString("{" + change + "}").getAsJsonObject().entrySet().forEach(e -> j.add(e.getKey(), e.getValue()));
        assertTrue(ResearchDataPolicy.CODEC.parse(JsonOps.INSTANCE, j).error().isPresent(), j.toString());
    }
    @Test void impossibleRowsAndReferencesFailBeforePublication() {
        var j = json("report"); j.remove("reconstruction"); assertTrue(ResearchDataPolicy.CODEC.parse(JsonOps.INSTANCE, j).error().isPresent());
        j = json("report"); j.getAsJsonObject("rows").remove("tier_2"); assertTrue(ResearchDataPolicy.CODEC.parse(JsonOps.INSTANCE, j).error().isPresent());
        j = json("report"); var row = j.getAsJsonObject("rows").getAsJsonObject("tier_1").getAsJsonObject("default");
        row.addProperty("max_fragments", 65); assertTrue(ResearchDataPolicy.CODEC.parse(JsonOps.INSTANCE, j).error().isPresent());
        row.addProperty("max_fragments", 3); row.addProperty("no_result_weight", 1); assertTrue(ResearchDataPolicy.CODEC.parse(JsonOps.INSTANCE, j).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> new ResearchDataSnapshot(Map.of(POLICY, policy("report")), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> new ResearchDataSnapshot(Map.of(), Map.of(SHARED, shared(), id("test:other"), shared())));
    }
    @Test void tierProbabilityDoesNotDependOnCatalogPopulationAndRowsAreAggregated() {
        var j = json("dossier");
        j.getAsJsonObject("rows").getAsJsonObject("tier_1").add("targets", JsonParser.parseString("{\"tier_3\":{\"blueprint_weight\":1,\"fragment_weight\":0,\"no_result_weight\":0,\"min_fragments\":0,\"max_fragments\":0}}"));
        var targets = new HashMap<>(threeTiers());
        for (int i = 0; i < 100; i++) targets.put(id("test:extra_" + i), 1);
        var resolved = pool(context(decode(ResearchDataPolicy.CODEC, j), targets), EMPTY_PLAYER);
        assertEquals(3, resolved.tiers().size());
        assertEquals(1.04 / 3, resolved.blueprintProbability(), 1e-12);
        var calls = new ArrayList<Long>();
        var outcome = resolved.select(bound -> { calls.add(bound); return bound - 1; });
        assertEquals(id("test:three"), outcome.target()); assertEquals(AnalyzerOutcomeTable.Kind.BLUEPRINT, outcome.kind());
        assertEquals(List.of(3L), calls); // One tier draw; only one T3 target and one possible outcome.
    }
    @Test void learnedFilterDefaultsToDuplicatesAndEmptyTiersRenormalize() {
        var learned = new ResearchDataPoolResolver.PlayerView(threeTiers().keySet(), Set.of(), Map.of());
        assertTrue(pool(context(policy("dossier"), threeTiers()), learned).ready());
        var j = json("dossier"); j.addProperty("allow_learned", false); var p = decode(ResearchDataPolicy.CODEC, j);
        assertEquals(ResearchDataPoolResolver.Status.ALL_LEARNED, pool(context(p, threeTiers()), learned).status());
        var twoLearned = new ResearchDataPoolResolver.PlayerView(Set.of(id("test:one"), id("test:two")), Set.of(), Map.of());
        var resolved = pool(context(p, threeTiers()), twoLearned);
        assertEquals(List.of(AnalyzerTier.TIER_3), resolved.tiers().stream().map(ResearchDataPoolResolver.TierPool::tier).toList());
        assertEquals(id("test:three"), resolved.select(bound -> 0).target());
    }
    @Test void globalDefaultsAreOverridableAndUndiscoveredIsSeparateFromLearned() {
        var p = policy("report"); var source = context(p, threeTiers());
        var strict = ResearchDataPoolResolver.evaluate(new ResearchDataPoolResolver.Context(source), POLICY, PROFILE, AnalyzerTier.TIER_1,
                new ResearchDataPoolResolver.Defaults(false, false), EMPTY_PLAYER, ignored -> true);
        assertEquals(ResearchDataPoolResolver.Status.EMPTY_POOL, strict.status());
        var j = json("report"); j.addProperty("allow_undiscovered", true); j.addProperty("allow_learned", true);
        var override = context(decode(ResearchDataPolicy.CODEC, j), threeTiers());
        assertTrue(ResearchDataPoolResolver.evaluate(new ResearchDataPoolResolver.Context(override), POLICY, PROFILE, AnalyzerTier.TIER_1,
                new ResearchDataPoolResolver.Defaults(false, false), new ResearchDataPoolResolver.PlayerView(threeTiers().keySet(), Set.of(), Map.of()), ignored -> true).ready());
    }
    @Test void cacheNeverRetainsPlayerFiltersAndUnavailablePoolDoesNotDraw() {
        var j = json("dossier"); j.addProperty("allow_learned", false);
        var source = context(decode(ResearchDataPolicy.CODEC, j), threeTiers()); var cached = new ResearchDataPoolResolver.Context(source);
        var a = ResearchDataPoolResolver.evaluate(cached, POLICY, PROFILE, AnalyzerTier.TIER_1, ResearchDataPoolResolver.Defaults.STANDARD, EMPTY_PLAYER, ignored -> true);
        var b = ResearchDataPoolResolver.evaluate(cached, POLICY, PROFILE, AnalyzerTier.TIER_1, ResearchDataPoolResolver.Defaults.STANDARD,
                new ResearchDataPoolResolver.PlayerView(threeTiers().keySet(), Set.of(), Map.of()), ignored -> true);
        assertTrue(a.ready()); assertEquals(ResearchDataPoolResolver.Status.ALL_LEARNED, b.status()); assertEquals(3, cached.cachedCandidateCount());
        assertThrows(IllegalStateException.class, () -> b.select(bound -> { fail("must not draw"); return 0; }));
        assertThrows(IllegalArgumentException.class, () -> a.select(bound -> bound));
    }
    @Test void largeCatalogKeepsPlayerFilteringLinearAndSamplingBounded() {
        var tiers = new LinkedHashMap<ResourceLocation, Integer>();
        for (int i = 0; i < 4096; i++) tiers.put(id("test:large_" + i), i % 3 + 1);
        var source = context(policy("dossier"), tiers);
        var cached = new ResearchDataPoolResolver.Context(source);
        for (var station : AnalyzerTier.values()) {
            var visits = new java.util.concurrent.atomic.AtomicInteger();
            var pool = ResearchDataPoolResolver.evaluate(cached, POLICY, PROFILE, station,
                    ResearchDataPoolResolver.Defaults.STANDARD, EMPTY_PLAYER, target -> { visits.incrementAndGet(); return true; });
            assertTrue(pool.ready());
            assertEquals(tiers.size(), visits.get(), "one eligibility check per candidate");
            assertEquals(tiers.size(), pool.tiers().stream().mapToInt(t -> t.candidates().size()).sum());
            var draws = new java.util.concurrent.atomic.AtomicInteger();
            var selected = pool.select(bound -> { draws.incrementAndGet(); return bound - 1; });
            assertTrue(tiers.containsKey(selected.target()));
            assertTrue(draws.get() <= 4, "tier, weapon, outcome, quantity draws remain bounded");
            assertTrue(cached.cachedCandidateCount() <= ResearchDataPoolResolver.MAX_CACHED_CANDIDATES);
        }
        var oversized = new HashMap<>(source.catalog());
        var entry = source.catalog().values().iterator().next();
        for (int i = oversized.size(); i <= ResearchDataPoolResolver.MAX_CATALOG; i++) oversized.put(id("test:overflow_" + i), entry);
        assertThrows(IllegalArgumentException.class, () -> new ResearchDataPoolResolver.Context(
                new BlueprintAnalyzerPolicyResolver.Context(source.research(), oversized, 7, 4, source.progression())));
    }
    @Test void inputResolutionIsPriorityThenSpecificityThenStableIdAndDisabledOwnerWins() {
        var exact = policy("note"); var tagJson = json("note"); tagJson.add("input", JsonParser.parseString("{\"tag\":\"test:data\"}"));
        var tag = decode(ResearchDataPolicy.CODEC, tagJson);
        var snapshot = new ResearchDataSnapshot(Map.of(id("test:a"), tag, id("test:z"), exact), Map.of(SHARED, shared()));
        var selected = snapshot.select(id("taczweaponblueprints:research_note"), PROFILE, (item, group) -> true);
        assertEquals(Optional.of(id("test:z")), selected.selected()); assertEquals(List.of(id("test:a")), selected.shadowed());
        tagJson.addProperty("priority", 1); tagJson.addProperty("enabled", false);
        snapshot = new ResearchDataSnapshot(Map.of(id("test:a"), decode(ResearchDataPolicy.CODEC, tagJson), id("test:z"), exact), Map.of(SHARED, shared()));
        assertEquals(Optional.of(id("test:a")), snapshot.select(id("taczweaponblueprints:research_note"), PROFILE, (item, group) -> true).selected());
        snapshot = new ResearchDataSnapshot(Map.of(id("test:z"), exact, id("test:b"), exact), Map.of(SHARED, shared()));
        assertEquals(Optional.of(id("test:b")), snapshot.select(id("taczweaponblueprints:research_note"), PROFILE, (item, group) -> false).selected());
    }
    @Test void partialProgressOnlyWeightsMatchingClassWithinTier() {
        var j = json("dossier"); j.addProperty("progress_bonus", 10);
        var targets = new HashMap<>(threeTiers()); targets.put(id("test:other"), 1);
        var player = new ResearchDataPoolResolver.PlayerView(Set.of(), Set.of(), Map.of(id("test:one"), new AnalysisFragmentCounts(4, 0, 0, 0),
                id("test:other"), new AnalysisFragmentCounts(0, 4, 0, 0)));
        var resolved = pool(context(decode(ResearchDataPolicy.CODEC, j), targets), player);
        var first = resolved.tiers().get(0); assertEquals(1, first.weight()); assertEquals(10, first.targetWeight());
        assertEquals(9, first.candidates().stream().filter(c -> c.target().equals(id("test:one"))).findFirst().orElseThrow().weight());
    }
    @Test void contentFiltersOverridesAndStalenessNeverSelectAnUnrelatedFallback() {
        var j = json("note"); j.getAsJsonObject("pool").add("tier_overrides", JsonParser.parseString("{\"test:three\":\"tier_1\"}"));
        j.getAsJsonObject("pool").add("exclude", JsonParser.parseString("{\"blueprints\":[\"test:one\"]}"));
        var source = context(decode(ResearchDataPolicy.CODEC, j), threeTiers());
        assertEquals(id("test:three"), pool(source, EMPTY_PLAYER).select(bound -> 0).target());
        var blocked = ResearchDataPoolResolver.evaluate(new ResearchDataPoolResolver.Context(source), POLICY, PROFILE, AnalyzerTier.TIER_1,
                ResearchDataPoolResolver.Defaults.STANDARD, EMPTY_PLAYER, ignored -> false);
        assertEquals(ResearchDataPoolResolver.Status.EMPTY_POOL, blocked.status());
        var stale = new BlueprintAnalyzerPolicyResolver.Context(source.research(), source.catalog(), 8, 4, source.progression());
        assertEquals(ResearchDataPoolResolver.Status.STALE_PROGRESSION, pool(stale, EMPTY_PLAYER).status());
        assertEquals(ResearchDataPoolResolver.Status.EMPTY_POOL, pool(context(policy("note"), Map.of(id("test:three"), 3)), EMPTY_PLAYER).status());
    }
    @Test void duplicateJsonAndNoncanonicalNestedIdsAreRejected() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> ResearchDataJson.parse("{\"format\":1,\"format\":1}"));
        assertThrows(IllegalArgumentException.class, () -> ResearchDataJson.parse("{\"pool\":{\"tier_1\":1,\"tier_1\":2}}"));
        var j = json("note"); j.getAsJsonObject("pool").add("include", JsonParser.parseString("{\"blueprints\":[\"rifle\"]}"));
        assertTrue(ResearchDataPolicy.CODEC.parse(JsonOps.INSTANCE, j).error().isPresent());
        j = json("note"); j.getAsJsonObject("pool").add("include", JsonParser.parseString("{\"selector\":{\"exclude\":[\"rifle\"]}}"));
        assertTrue(ResearchDataPolicy.CODEC.parse(JsonOps.INSTANCE, j).error().isPresent());
    }
    @Test void aliasesAndMissingProgressionDoNotBecomeExtraTickets() {
        var source = context(policy("dossier"), threeTiers());
        var catalog = new HashMap<>(source.catalog()); catalog.put(id("test:recipe_alias"), catalog.get(id("test:one")));
        catalog.put(id("test:missing_tier"), new BlueprintData("test:missing_tier", "n", "t", id("test:recipe/missing"), null, "rifle", id("tacz:slot"), BlueprintKind.GUN));
        var resolved = pool(new BlueprintAnalyzerPolicyResolver.Context(source.research(), catalog, 7, 4, source.progression()), EMPTY_PLAYER);
        assertEquals(3, resolved.tiers().stream().mapToInt(t -> t.candidates().size()).sum());
        assertEquals(1, resolved.exclusions().get("noncanonical_or_nonweapon"));
        assertEquals(1, resolved.exclusions().get("tier_unavailable"));
    }
    @Test void targetSelectorsZeroWeightsAndProfileScopesAreIndependent() {
        var j = json("dossier");
        j.getAsJsonObject("pool").add("include", JsonParser.parseString("{\"selector\":{\"namespaces\":[\"test\"],\"path_prefixes\":[\"t\"]}}"));
        j.getAsJsonObject("pool").add("weapon_weights", JsonParser.parseString("{\"test:two\":0}"));
        var source = context(decode(ResearchDataPolicy.CODEC, j), threeTiers());
        assertEquals(id("test:three"), pool(source, EMPTY_PLAYER).select(bound -> 0).target());
        j.addProperty("profile", "test:missing");
        var p = decode(ResearchDataPolicy.CODEC, j);
        assertThrows(IllegalArgumentException.class, () -> context(p, threeTiers()));
        j = json("dossier"); j.getAsJsonObject("pool").add("include", JsonParser.parseString("{\"tags\":[\"test:missing\"]}"));
        var badTag = decode(ResearchDataPolicy.CODEC, j);
        assertThrows(IllegalArgumentException.class, () -> context(badTag, threeTiers()));
    }
    @Test void largestAuthoredWeightsAndProgressBonusUseWideArithmetic() {
        var j = json("dossier"); j.addProperty("progress_bonus", 1000);
        j.getAsJsonObject("pool").add("tier_weights", JsonParser.parseString("{\"tier_1\":1000000000,\"tier_2\":1000000000,\"tier_3\":1000000000}"));
        j.getAsJsonObject("pool").add("weapon_weights", JsonParser.parseString("{\"test:one\":1000000000,\"test:other\":1000000000}"));
        var targets = new HashMap<>(threeTiers()); targets.put(id("test:other"), 1);
        var resolved = pool(context(decode(ResearchDataPolicy.CODEC, j), targets),
                new ResearchDataPoolResolver.PlayerView(Set.of(), Set.of(), Map.of(id("test:one"), new AnalysisFragmentCounts(4,0,0,0))));
        assertEquals(802_000_000_000L, resolved.tiers().get(0).targetWeight());
        var calls = new ArrayList<Long>(); resolved.select(bound -> { calls.add(bound); return bound - 1; });
        assertEquals(3_000_000_000L, calls.get(0));
        assertThrows(UnsupportedOperationException.class, () -> resolved.tiers().clear());
        assertThrows(UnsupportedOperationException.class, () -> resolved.tiers().get(0).candidates().clear());
    }
    @Test void stationAndBehaviorGatesDoNotPrepareOrSampleCandidates() {
        var j = json("note"); j.addProperty("minimum_analyzer_tier", "tier_3");
        assertEquals(ResearchDataPoolResolver.Status.STATION_TIER_REQUIRED, pool(context(decode(ResearchDataPolicy.CODEC, j), threeTiers()), EMPTY_PLAYER).status());
        j.addProperty("enabled", false);
        assertEquals(ResearchDataPoolResolver.Status.DISABLED, pool(context(decode(ResearchDataPolicy.CODEC, j), threeTiers()), EMPTY_PLAYER).status());
        j = json("note"); j.addProperty("behavior", "legacy_currency"); j.remove("rows"); j.remove("reconstruction");
        assertEquals(ResearchDataPoolResolver.Status.LEGACY_CURRENCY, pool(context(decode(ResearchDataPolicy.CODEC, j), threeTiers()), EMPTY_PLAYER).status());
    }

}
