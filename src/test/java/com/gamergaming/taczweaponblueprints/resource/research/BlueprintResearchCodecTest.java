package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class BlueprintResearchCodecTest {
    @Test
    void decodesStrictVersionedProfileAndRule() {
        BlueprintResearchProfile profile = decode(
                BlueprintResearchProfile.CODEC,
                """
                        {
                          "format": 1,
                          "journal_enabled": true,
                          "visibility": "silhouette",
                          "research_enabled": true,
                          "recycling_enabled": true,
                          "allow_unlearned_recycling": false,
                          "recycling_value": 2,
                          "research_cost": {
                            "points": 12,
                            "ingredients": [
                              {"tag": "forge:ingots/iron", "count": 3}
                            ]
                          },
                          "requires_discovery": false,
                          "creative_bypasses_cost": false
                        }
                        """);
        BlueprintResearchRule rule = decode(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "priority": 25,
                          "target": {
                            "selector": {
                              "namespaces": ["tacz"],
                              "item_types": ["rifle"]
                            }
                          },
                          "visibility": "name",
                          "research_cost": {"points": 20}
                        }
                        """);

        assertEquals(JournalVisibility.SILHOUETTE, profile.visibility());
        assertEquals(12, profile.researchCost().points());
        assertEquals(1, profile.researchCost().ingredients().size());
        assertEquals(25, rule.priority());
        assertEquals(JournalVisibility.NAME, rule.visibility().orElseThrow());
        assertTrue(rule.target().selector().isPresent());
    }

    @Test
    void rejectsUnknownFieldsAtEverySchemaLevel() {
        assertDecodeFails(
                BlueprintResearchProfile.CODEC,
                validProfileJson().replace("\"format\": 1,", "\"format\": 1, \"unknown\": true,"));
        assertDecodeFails(
                BlueprintResearchProfile.CODEC,
                validProfileJson().replace(
                        "\"points\": 8",
                        "\"points\": 8, \"unknown\": true"));
        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "target": {"blueprints": ["test:item"], "unknown": true}
                        }
                        """);
        assertDecodeFails(
                BlueprintResearchCost.CODEC,
                """
                        {"points": 8, "ingredients": [{"items": ["minecraft:paper"], "count": 1, "x": 1}]}
                        """);
    }

    @Test
    void rejectsMalformedIngredientsAndUnsafeLimits() {
        assertDecodeFails(
                BlueprintResearchIngredient.CODEC,
                "{\"items\": [], \"count\": 1}");
        assertDecodeFails(
                BlueprintResearchIngredient.CODEC,
                "{\"items\": [\"minecraft:paper\"], \"tag\": \"forge:paper\", \"count\": 1}");
        assertDecodeFails(
                BlueprintResearchIngredient.CODEC,
                "{\"items\": [\"minecraft:paper\"], \"count\": 65}");
        assertDecodeFails(
                BlueprintResearchCost.CODEC,
                """
                        {
                          "points": 8,
                          "ingredients": [
                            {"items": ["test:a"], "count": 1},
                            {"items": ["test:b"], "count": 1},
                            {"items": ["test:c"], "count": 1},
                            {"items": ["test:d"], "count": 1},
                            {"items": ["test:e"], "count": 1},
                            {"items": ["test:f"], "count": 1},
                            {"items": ["test:g"], "count": 1}
                          ]
                        }
                        """);
    }

    @Test
    void prerequisiteRulesRequireBoundedExactTargets() {
        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "target": {"selector": {"namespaces": ["test"]}},
                          "prerequisites": ["test:basic"]
                        }
                        """);
        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "priority": 1000001,
                          "target": {"blueprints": ["test:item"]}
                        }
                        """);
    }

    private static String validProfileJson() {
        return """
                {
                  "format": 1,
                  "journal_enabled": true,
                  "visibility": "silhouette",
                  "research_enabled": true,
                  "recycling_enabled": true,
                  "allow_unlearned_recycling": false,
                  "recycling_value": 1,
                  "research_cost": {"points": 8},
                  "requires_discovery": false,
                  "creative_bypasses_cost": false
                }
                """;
    }

    private static <T> T decode(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }

    private static <T> void assertDecodeFails(Codec<T> codec, String json) {
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
    }
}
