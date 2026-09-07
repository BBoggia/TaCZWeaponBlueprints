package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryMode;
import com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BlueprintAnalyzerPolicyTest {
    static final ResourceLocation PROFILE = id("test:analyzer"), TARGET = id("test:rifle"), TAG = id("test:weapons");
    static final Map<ResourceLocation, BlueprintData> CATALOG = Map.of(TARGET, new BlueprintData(TARGET.toString(),
            "item.test", "tooltip.test", id("test:recipe/rifle"), null, "rifle", id("tacz:slot"), BlueprintKind.GUN, 1));
    @AfterEach void clearCache() { BlueprintResearchPolicyResolver.clearCache(); }

    @ParameterizedTest
    @CsvSource({"1,1,70", "1,2,30", "1,3,10", "2,1,100", "2,2,70", "2,3,30", "3,1,100", "3,2,100", "3,3,80"})
    void allNinePairsResolveWithIndependentReconstructionThreshold(int station, int weapon, int probability) {
        var rule = rule("{\"weapon_tier\":\"tier_" + weapon + "\"}");
        var snapshot = snapshot(profileJson(), Map.of(id("test:tier"), rule));
        var quote = quote(snapshot, station);
        assertEquals(AnalyzerPolicyQuote.Status.CONFIGURED, quote.status());
        assertEquals(probability / 100.0, quote.outcomes().orElseThrow().blueprintProbability());
        assertEquals(weapon * 5, quote.fragmentThreshold());
        assertEquals("analyzer_rule", quote.tierSource());
        assertFalse(quote.reviewRequired());
        assertEquals(3, quote.attempt().orElseThrow().cost().points());
        assertFalse(quote.attempt().orElseThrow().allowKnown());
        assertEquals(PhysicalBlueprintLearningMode.REQUIRE_TREE_PREREQUISITES,
                quote.attempt().orElseThrow().physicalBlueprintLearningMode());
        // The runtime reverse-engineering policy is deliberately unaffected.
        var legacy = BlueprintResearchPolicyResolver.reverseEngineeringPolicyFor(snapshot, CATALOG, PROFILE, TARGET);
        assertTrue(legacy.allowKnown());
        assertEquals(PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES, legacy.physicalBlueprintLearningMode());
    }

    @Test void exactAndStableWinnerDoNotAccumulateLowerRulesOrShadowCoreCosts() {
        var tag = rule("{\"weapon_tier\":\"tier_3\",\"attempt\":{\"cost\":{\"points\":99}}}");
        var tagJson = BlueprintResearchRule.CODEC.encodeStart(JsonOps.INSTANCE, tag).getOrThrow(false, ignored -> {}).getAsJsonObject();
        tagJson.add("target", JsonParser.parseString("{\"tags\":[\"test:weapons\"]}")); tagJson.addProperty("priority", 1000);
        var coreJson = JsonParser.parseString("{\"format\":5,\"profile\":\"test:analyzer\",\"target\":{\"tags\":[\"test:weapons\"]},\"reverse_engineering\":{\"cost\":{\"points\":7}}}");
        var snapshot = snapshot(profileJson(), Map.of(id("test:tag"), decode(BlueprintResearchRule.CODEC, tagJson),
                id("test:z_exact"), rule("{\"weapon_tier\":\"tier_3\"}"),
                id("test:a_exact"), rule("{\"weapon_tier\":\"tier_1\"}"),
                id("test:core"), decode(BlueprintResearchRule.CODEC, coreJson)));
        var quote = quote(snapshot, 1);
        assertEquals(Optional.of(id("test:a_exact")), quote.selectedRuleId());
        assertEquals(Optional.of(AnalyzerTier.TIER_1), quote.weaponTier());
        assertEquals(7, quote.attempt().orElseThrow().cost().points());
        assertEquals(7, BlueprintResearchPolicyResolver.reverseEngineeringPolicyFor(snapshot, CATALOG, PROFILE, TARGET).cost().points());
    }

    @Test void selectedRuleAttemptOverridesRowWhileOnlyOneRowIsReplaced() {
        var json = profileJson();
        var row = json.getAsJsonObject("analyzer").getAsJsonObject("tiers").getAsJsonObject("tier_1").getAsJsonObject("tier_2");
        row.add("attempt", JsonParser.parseString("{\"cost\":{\"points\":15}}"));
        var snapshot = snapshot(json, Map.of(id("test:override"), rule("""
                {"attempt":{"cost":{"points":2}},"tiers":{"tier_1":{"tier_2":{"outcomes":
                {"blueprint_weight":0,"fragment_weight":1,"no_result_weight":0,"min_fragments":2,"max_fragments":3}}}}}
                """)));
        assertEquals(2, quote(snapshot, 1).attempt().orElseThrow().cost().points());
        assertEquals(0.0, quote(snapshot, 1).outcomes().orElseThrow().blueprintProbability());
        assertEquals(0.7, quote(snapshot, 2).outcomes().orElseThrow().blueprintProbability());
        assertEquals(2, quote(snapshot, 2).attempt().orElseThrow().cost().points());
    }

    @Test void unavailableTierRequiresExplicitFallbackAndDisabledTreeIsNotUsedAsTier() {
        var json = profileJson();
        assertFalse(json.get("tree_enabled").getAsBoolean());
        var fallback = quote(snapshot(json, Map.of()), 1);
        assertEquals(Optional.of(AnalyzerTier.TIER_2), fallback.weaponTier());
        assertTrue(fallback.reviewRequired());
        json.getAsJsonObject("analyzer").remove("fallback_weapon_tier");
        assertEquals(AnalyzerPolicyQuote.Status.TIER_UNAVAILABLE, quote(snapshot(json, Map.of()), 1).status());
    }

    @Test void revisionMatchedProgressionPrecedesFallbackAndStaleProgressionCannotDowngrade() {
        var json = profileJson(); json.addProperty("tree_enabled", true);
        json.getAsJsonObject("domain_policies").getAsJsonObject("weapons").addProperty("tree_enabled", true);
        var snapshot = snapshot(json, Map.of());
        var progression = BlueprintProgressionPolicyResolver.resolve(snapshot, CATALOG, 4, 7, Map.of(), AutomaticWorkbenchTierPercentiles.DEFAULT);
        var context = new BlueprintAnalyzerPolicyResolver.Context(snapshot, CATALOG, 7, 4, progression);
        var q = BlueprintAnalyzerPolicyResolver.resolve(context, PROFILE, TARGET, AnalyzerTier.TIER_1,
                BlueprintAnalyzerPolicy.Origin.UNKNOWN, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
        assertEquals(AnalyzerPolicyQuote.Status.CONFIGURED, q.status());
        assertTrue(q.tierSource().startsWith("progression:"));
        var stale = new BlueprintAnalyzerPolicyResolver.Context(snapshot, CATALOG, 8, 4, progression);
        assertEquals(AnalyzerPolicyQuote.Status.STALE_PROGRESSION, BlueprintAnalyzerPolicyResolver.resolve(stale, PROFILE, TARGET,
                AnalyzerTier.TIER_1, BlueprintAnalyzerPolicy.Origin.UNKNOWN, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY).status());
    }

    @Test void modeOriginAndMinimumTierAreIndependentConfigurationGates() {
        var json = profileJson();
        json.getAsJsonObject("analyzer").addProperty("minimum_analyzer_tier", "tier_2");
        json.getAsJsonObject("analyzer").add("allowed_origins", JsonParser.parseString("[\"found\"]"));
        var snapshot = snapshot(json, Map.of());
        assertEquals(AnalyzerPolicyQuote.Status.STATION_TIER_REQUIRED, quote(snapshot, 1).status());
        assertEquals(AnalyzerPolicyQuote.Status.ORIGIN_DISABLED, quote(snapshot, 2).status());
        var context = context(snapshot);
        assertEquals(AnalyzerPolicyQuote.Status.DIRECT_RECOVERY_ONLY, BlueprintAnalyzerPolicyResolver.resolve(context, PROFILE, TARGET,
                AnalyzerTier.TIER_2, BlueprintAnalyzerPolicy.Origin.FOUND, FoundWeaponRecoveryMode.DIRECT_RP_ONLY).status());
        var found = BlueprintAnalyzerPolicyResolver.resolve(context, PROFILE, TARGET, AnalyzerTier.TIER_2,
                BlueprintAnalyzerPolicy.Origin.FOUND, FoundWeaponRecoveryMode.PLAYER_CHOICE);
        assertTrue(found.attempt().orElseThrow().outputRecyclable());
        assertEquals(AnalyzerPolicyQuote.Status.DISABLED, quote(snapshot(json, Map.of(id("test:disable"), rule("{\"mode\":\"disabled\"}"))), 3).status());
    }

    @Test void missingExtensionsRemainLegacyAndOldFormatsRejectExplicitAnalyzerFields() {
        var json = profileJson(); json.addProperty("format", 4);
        assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        json.remove("analyzer");
        assertEquals(AnalyzerPolicyQuote.Status.LEGACY, quote(snapshot(json, Map.of()), 1).status());
        var rule = BlueprintResearchRule.CODEC.encodeStart(JsonOps.INSTANCE, rule("{\"mode\":\"disabled\"}")).getOrThrow(false, ignored -> {}).getAsJsonObject();
        rule.addProperty("format", 4);
        assertTrue(BlueprintResearchRule.CODEC.parse(JsonOps.INSTANCE, rule).error().isPresent());
    }

    @Test void incompleteUnknownOrNullPolicyFieldsFailClosed() {
        var json = profileJson(); json.getAsJsonObject("analyzer").getAsJsonObject("tiers").remove("tier_3");
        assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        json = profileJson(); json.getAsJsonObject("analyzer").addProperty("unexpected", 1);
        assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        json = profileJson(); json.add("analyzer", JsonNull.INSTANCE);
        assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        json = profileJson(); json.getAsJsonObject("analyzer").addProperty("fallback_weapon_tier", "tier_4");
        assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test void partialPoliciesCannotLoseReconstructionOrLearningAndCannotLaunderCraftedInputs() {
        var missing = profileJson(); missing.getAsJsonObject("analyzer").remove("reconstruction");
        assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, missing).error().isPresent());
        var badCap = profileJson(); badCap.getAsJsonObject("analyzer").getAsJsonObject("reconstruction").addProperty("retained_progress_cap", 2);
        assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, badCap).error().isPresent());
        assertThrows(IllegalArgumentException.class, () -> snapshot(profileJson(), Map.of(id("test:disabled"), rule("{\"attempt\":{\"physical_blueprint_learning\":\"disabled\"}}"))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(profileJson(), Map.of(id("test:unsafe"), rule("{\"attempt\":{\"output_recyclable\":true}}"))));
        assertDoesNotThrow(() -> snapshot(profileJson(), Map.of(id("test:expert"), rule("{\"attempt\":{\"output_recyclable\":true,\"expert_allow_economy_loop\":true}}"))));
        // Invalid lower-priority policies are also rejected, even when shadowed.
        assertThrows(IllegalArgumentException.class, () -> snapshot(profileJson(), Map.of(id("test:a_valid"), rule("{}"),
                id("test:z_invalid"), rule("{\"attempt\":{\"physical_blueprint_learning\":\"disabled\"}}"))));
    }

    @Test void analyzerCostsParticipateInItemAndTagValidationAndAreImmutable() {
        var snapshot = snapshot(profileJson(), Map.of(id("test:material"), rule("""
                {"attempt":{"cost":{"points":1,"ingredients":[{"items":["test:missing"],"count":2},{"tag":"test:metal","count":1}]}}}
                """)));
        assertThrows(IllegalArgumentException.class, () -> BlueprintResearchIngredientValidator.validateExactItems(snapshot, ignored -> false));
        assertEquals(Set.of(id("test:metal")), BlueprintResearchIngredientValidator.unresolvedTags(snapshot, ignored -> false));
        var tiers = snapshot.profiles().get(PROFILE).analyzer().orElseThrow().tiers();
        assertThrows(UnsupportedOperationException.class, () -> tiers.clear());
        assertThrows(UnsupportedOperationException.class, () -> tiers.get(AnalyzerTier.TIER_1).clear());
    }

    @Test void diagnosticsContainEffectiveOddsCostsThresholdsAndNoUpcomingRoll() {
        var snapshot = snapshot(profileJson(), Map.of());
        var exported = BlueprintAnalyzerDiagnostics.export(context(snapshot), PROFILE, TARGET, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
        assertTrue(exported.get("gameplay_enabled").getAsBoolean());
        assertEquals(9, exported.getAsJsonArray("quotes").size());
        var first = exported.getAsJsonArray("quotes").get(0).getAsJsonObject();
        assertEquals(0.3, first.get("blueprint_probability").getAsDouble());
        assertEquals(10, first.get("fragment_threshold").getAsInt());
        assertEquals(3, first.getAsJsonObject("attempt").getAsJsonObject("cost").get("points").getAsInt());
        assertFalse(exported.toString().contains("seed"));
        assertFalse(exported.toString().contains("upcoming"));
    }

    @Test void attemptNumbersAndExplicitNullOverridesAreStrict() {
        for (String attempt : List.of("{\"input_count\":1.5}", "{\"cost\":{\"points\":1.5}}",
                "{\"cost\":{\"points\":1,\"ingredients\":[{\"items\":[\"test:metal\"],\"count\":1.5}]}}",
                "{\"allow_known\":null}", "{\"allow_known\":1}", "{\"enabled\":\"false\"}")) {
            var json = profileJson(); json.getAsJsonObject("analyzer").add("attempt", JsonParser.parseString(attempt));
            assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent(), attempt);
        }
        var json = profileJson(); json.getAsJsonObject("analyzer").add("fallback_weapon_tier", JsonNull.INSTANCE);
        assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
    }

    @Test void canonicalCodecRoundTripAndCatalogExportRetainAllNinePairings() {
        var profile = decode(BlueprintResearchProfile.CODEC, profileJson());
        var encoded = BlueprintResearchProfile.CODEC.encodeStart(JsonOps.INSTANCE, profile).getOrThrow(false, ignored -> {});
        assertEquals(profile, decode(BlueprintResearchProfile.CODEC, encoded));
        var snapshot = snapshot(profileJson(), Map.of());
        var exported = JsonParser.parseString(BlueprintResearchCatalogExporter.export(snapshot, CATALOG, PROFILE)).getAsJsonObject();
        assertEquals(22, exported.get("format").getAsInt());
        var analyzer = exported.getAsJsonArray("entries").get(0).getAsJsonObject().getAsJsonObject("analyzer_policy");
        assertEquals(9, analyzer.getAsJsonArray("quotes").size());
        assertEquals("protected_mode_baseline", analyzer.get("recovery_mode_scope").getAsString());
    }

    @Test void rowDisablingAndNoResultOnlyAreExplicitAndValid() {
        var json = profileJson();
        var row = json.getAsJsonObject("analyzer").getAsJsonObject("tiers").getAsJsonObject("tier_1").getAsJsonObject("tier_2");
        row.addProperty("enabled", false);
        assertEquals(AnalyzerPolicyQuote.Status.DISABLED, quote(snapshot(json, Map.of()), 1).status());
        row.addProperty("enabled", 0);
        assertTrue(BlueprintResearchProfile.CODEC.parse(JsonOps.INSTANCE, json).error().isPresent());
        row.addProperty("enabled", true);
        row.add("outcomes", JsonParser.parseString("{\"blueprint_weight\":0,\"fragment_weight\":0,\"no_result_weight\":1,\"min_fragments\":0,\"max_fragments\":0}"));
        var quote = quote(snapshot(json, Map.of()), 1);
        assertEquals(AnalyzerPolicyQuote.Status.CONFIGURED, quote.status());
        assertEquals(1.0, quote.outcomes().orElseThrow().noResultProbability());
        json.remove("analyzer");
        assertThrows(IllegalArgumentException.class, () -> snapshot(json, Map.of(id("test:row"), rule("{\"tiers\":{\"tier_1\":{\"tier_2\":" + row + "}}}"))));
    }

    @Test void explicitTierWorksWithoutCurrentProgressionAndRecoveryPermissionsStayOriginSpecific() {
        var json = profileJson();
        var snapshot = snapshot(json, Map.of(id("test:explicit"), rule("{\"weapon_tier\":\"tier_3\"}")));
        var stale = BlueprintProgressionPolicyResolver.resolve(snapshot, CATALOG, 1, 1, Map.of(), AutomaticWorkbenchTierPercentiles.DEFAULT);
        var context = new BlueprintAnalyzerPolicyResolver.Context(snapshot, CATALOG, 7, 4, stale);
        for (var origin : BlueprintAnalyzerPolicy.Origin.values()) {
            for (var recovery : FoundWeaponRecoveryMode.values()) {
                var quote = BlueprintAnalyzerPolicyResolver.resolve(context, PROFILE, TARGET, AnalyzerTier.TIER_1, origin, recovery);
                assertEquals(Optional.of(AnalyzerTier.TIER_3), quote.weaponTier());
                assertEquals(origin == BlueprintAnalyzerPolicy.Origin.FOUND && recovery.foundBlueprintRecyclable(),
                        quote.attempt().orElseThrow().outputRecyclable());
                assertEquals(origin == BlueprintAnalyzerPolicy.Origin.FOUND && !recovery.blueprintExtractionEnabled()
                        ? AnalyzerPolicyQuote.Status.DIRECT_RECOVERY_ONLY : AnalyzerPolicyQuote.Status.CONFIGURED, quote.status());
            }
        }
        json.getAsJsonObject("analyzer").add("allowed_origins", JsonParser.parseString("[\"found\"]"));
        assertDoesNotThrow(() -> snapshot(json, Map.of(id("test:found"), rule("{\"attempt\":{\"output_recyclable\":true}}"))));
    }

    static JsonObject profileJson() {
        try (var input = BlueprintAnalyzerPolicyTest.class.getResourceAsStream("/analyzer-policy/profile.json")) {
            return JsonParser.parseString(new String(Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception error) { throw new AssertionError(error); }
    }
    static BlueprintResearchSnapshot snapshot(JsonObject profile, Map<ResourceLocation, BlueprintResearchRule> rules) {
        return BlueprintResearchSnapshot.create(Map.of(TAG, new BlueprintLootTag(1, List.of(TARGET))),
                Map.of(PROFILE, decode(BlueprintResearchProfile.CODEC, profile)), rules);
    }
    static BlueprintResearchRule rule(String analyzer) {
        return decode(BlueprintResearchRule.CODEC, JsonParser.parseString("{\"format\":5,\"profile\":\"test:analyzer\",\"target\":{\"blueprints\":[\"test:rifle\"]},\"analyzer\":" + analyzer + "}"));
    }
    static BlueprintAnalyzerPolicyResolver.Context context(BlueprintResearchSnapshot snapshot) {
        return new BlueprintAnalyzerPolicyResolver.Context(snapshot, CATALOG, 7, 4, BlueprintProgressionPolicySnapshot.EMPTY);
    }
    static AnalyzerPolicyQuote quote(BlueprintResearchSnapshot snapshot, int station) {
        return BlueprintAnalyzerPolicyResolver.resolve(context(snapshot), PROFILE, TARGET, AnalyzerTier.fromLevel(station),
                BlueprintAnalyzerPolicy.Origin.UNKNOWN, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
    }
    static <T> T decode(Codec<T> codec, JsonElement json) {
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow(false, message -> { throw new AssertionError(message); });
    }
    static ResourceLocation id(String value) { return new ResourceLocation(value); }
}
