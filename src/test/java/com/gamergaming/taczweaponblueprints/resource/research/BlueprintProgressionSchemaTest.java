package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

class BlueprintProgressionSchemaTest {
    @Test
    void gateConditionCodecLoadsAValidDiscriminator() {
        var result = BlueprintProgressionCodecs.GATE_CONDITION.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("""
                        {
                          "type": "criterion",
                          "id": "test:trial",
                          "value": 2,
                          "scope": "research",
                          "message": "gate.test.trial",
                          "disclosure": "public"
                        }
                        """));
        assertTrue(result.result().isPresent(), () -> result.error()
                .map(error -> error.message()).orElse("missing result without error"));
    }

    @Test
    void formatThreeProfileLoadsStrictTierFragmentAndGatePolicy() {
        var result = BlueprintResearchProfile.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(profileJson(3, true)));
        BlueprintResearchProfile profile = result.result().orElseThrow(() -> new AssertionError(
                result.error().map(error -> error.message()).orElse("missing result without error")));

        assertEquals(ResearchWorkbenchTier.TIER_2, profile.progression().fallbackTiers().researchTier());
        assertEquals(9, profile.progression().fragments().thresholds().get(ResearchWorkbenchTier.TIER_2));
        assertEquals(1, profile.progression().gates().conditionCount());
        assertTrue(profile.progression().gates().allOf().get(0).anyOf().get(0)
                instanceof ProgressionGateCondition.Criterion);
    }

    @Test
    void progressionIsRejectedByLegacyProfileFormat() {
        assertTrue(BlueprintResearchProfile.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(profileJson(2, true))).error().isPresent());
    }

    @Test
    void unknownNestedFieldsAndGateTypesAreRejected() {
        assertTrue(BlueprintResearchProfile.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(profileJson(3, true)
                        .replace("\"fallback_tiers\": {", "\"unknown\": true, \"fallback_tiers\": {")))
                .error().isPresent());
        assertTrue(BlueprintResearchProfile.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(profileJson(3, true)
                        .replace("\"criterion\"", "\"script\"")))
                .error().isPresent());
    }

    @Test
    void invalidNestedBoundsReturnCodecErrors() {
        assertTrue(BlueprintResearchProfile.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(profileJson(3, true)
                        .replace("\"value\": 2", "\"value\": 0")))
                .error().isPresent());
        assertTrue(BlueprintResearchProfile.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(profileJson(3, true)
                        .replace("\"tier_2\": 9", "\"tier_2\": 101")))
                .error().isPresent());
    }

    @Test
    void valueEquivalentProgressionStillRequiresFormatThree() {
        String profile = profileJson(2, false).replace(
                "\"domain_policies\": {",
                "\"progression\": {\"fallback_tiers\": {\"research\": \"tier_1\", "
                        + "\"crafting\": \"tier_1\"}}, \"domain_policies\": {");
        assertTrue(BlueprintResearchProfile.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(profile)).error().isPresent());
    }

    @Test
    void fragmentThresholdOverrideRequiresExactTarget() {
        String rule = """
                {
                  "format": 3,
                  "profile": "test:profile",
                  "target": {"selector": {"blueprint_kinds": ["gun"]}},
                  "progression": {"fragment_threshold": 12}
                }
                """;
        assertTrue(BlueprintResearchRule.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(rule)).error().isPresent());
    }

    private static String profileJson(int format, boolean progression) {
        String extension = progression ? """
                ,
                  "progression": {
                    "fallback_tiers": {"research": "tier_2", "crafting": "tier_3"},
                    "authored_tier_bands": {
                      "starter": {"research": "tier_1", "crafting": "tier_1"}
                    },
                    "fragments": {
                      "mode": "targeted_research_boost",
                      "thresholds": {"tier_1": 4, "tier_2": 9, "tier_3": 15},
                      "retained_progress_cap": 100,
                      "research_discount": {"mode": "percentage", "value": 2500},
                      "learned_target_rp": 1
                    },
                    "gates": {"all_of": [{"any_of": [{
                      "type": "criterion",
                      "id": "test:trial",
                      "value": 2,
                      "scope": "research",
                      "message": "gate.test.trial",
                      "disclosure": "public"
                    }]}]}
                  }
                """ : "";
        return """
                {
                  "format": %s,
                  "journal_enabled": true,
                  "visibility": "full",
                  "research_enabled": true,
                  "recycling_enabled": true,
                  "allow_unlearned_recycling": false,
                  "recycling_value": 1,
                  "research_cost": {"points": 8},
                  "requires_discovery": false,
                  "creative_bypasses_cost": false,
                  "domain_policies": {
                    "weapons": {"tree_enabled": true, "research_enabled": true},
                    "attachments": {"tree_enabled": false, "research_enabled": false},
                    "ammo": {"tree_enabled": false, "research_enabled": false}
                  }%s
                }
                """.formatted(format, extension);
    }
}
