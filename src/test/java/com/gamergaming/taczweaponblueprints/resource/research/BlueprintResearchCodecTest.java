package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

class BlueprintResearchCodecTest {
    @Test
    void formatOneRetainsImplicitAllDomainBehavior() {
        BlueprintResearchProfile profile = decode(
                BlueprintResearchProfile.CODEC,
                validProfileJson());

        assertTrue(profile.domainPolicies().isEmpty());
        for (Domain domain : Domain.values()) {
            assertEquals(BlueprintResearchProfile.DomainPolicy.ENABLED, profile.domainPolicy(domain));
        }
    }

    @Test
    void formatTwoRequiresExplicitStrictPoliciesForEveryDomain() {
        BlueprintResearchProfile profile = decode(
                BlueprintResearchProfile.CODEC,
                formatTwoProfileJson());

        assertTrue(profile.domainPolicy(Domain.WEAPONS).treeEnabled());
        assertTrue(profile.domainPolicy(Domain.WEAPONS).researchEnabled());
        assertFalse(profile.domainPolicy(Domain.ATTACHMENTS).treeEnabled());
        assertFalse(profile.domainPolicy(Domain.ATTACHMENTS).researchEnabled());
        assertFalse(profile.domainPolicy(Domain.AMMO).treeEnabled());
        assertFalse(profile.domainPolicy(Domain.AMMO).researchEnabled());

        assertDecodeFails(
                BlueprintResearchProfile.DomainPolicy.CODEC,
                "{\"tree_enabled\": true, \"research_enabled\": true, \"unknown\": true}");
        assertDecodeFails(
                BlueprintResearchProfile.CODEC,
                formatTwoProfileJson().replace("\"ammo\":", "\"unknown\":"));
        assertDecodeFails(
                BlueprintResearchProfile.CODEC,
                validProfileJson().replace("\"format\": 1", "\"format\": 2"));
        assertDecodeFails(
                BlueprintResearchProfile.CODEC,
                formatTwoProfileJson().replace("\"format\": 2", "\"format\": 1"));
    }

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
        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "target": {"selector": {"namespaces": ["test"], "weight": 2.0}}
                        }
                        """);
        String oversizedId = "test:" + "a".repeat(252);
        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "target": {"selector": {"exclude": ["%s"]}}
                        }
                        """.formatted(oversizedId));
        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "target": {"selector": {"item_types": ["%s"]}}
                        }
                        """.formatted("x".repeat(257)));
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

    @Test
    void legacyPrerequisiteFieldDecodesIntoSingletonGroups() {
        BlueprintResearchRule legacy = decode(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "target": {"blueprints": ["test:advanced"]},
                          "prerequisites": ["test:route_b", "test:route_a"]
                        }
                        """);

        assertEquals(java.util.Optional.of(
                List.of(id("test:route_b"), id("test:route_a"))),
                legacy.prerequisites());
        assertEquals(
                List.of(
                        ResearchPrerequisiteGroup.singleton(id("test:route_a")),
                        ResearchPrerequisiteGroup.singleton(id("test:route_b"))),
                legacy.prerequisiteRequirements().orElseThrow().allOf());
    }

    @Test
    void groupedPrerequisitesAreVersionedCanonicalAndMutuallyExclusive() {
        BlueprintResearchRule grouped = decode(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 2,
                          "profile": "test:profile",
                          "target": {"blueprints": ["test:advanced"]},
                          "prerequisite_groups": [
                            {"any_of": ["test:route_b", "test:route_a"]},
                            {"any_of": ["test:foundation"]}
                          ]
                        }
                        """);

        assertTrue(grouped.prerequisites().isEmpty());
        assertEquals(
                List.of(id("test:foundation"), id("test:route_a"), id("test:route_b")),
                grouped.prerequisiteRequirements().orElseThrow()
                        .conservativeAlternatives());
        assertTrue(grouped.prerequisiteRequirements().orElseThrow()
                .legacySingletons().isEmpty());

        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 2,
                          "profile": "test:profile",
                          "target": {"blueprints": ["test:advanced"]},
                          "prerequisites": ["test:legacy"],
                          "prerequisite_groups": [{"any_of": ["test:route"]}]
                        }
                        """);
        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "target": {"blueprints": ["test:advanced"]},
                          "prerequisite_groups": [{"any_of": ["test:route"]}]
                        }
                        """);
        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 2,
                          "profile": "test:profile",
                          "target": {"selector": {"namespaces": ["test"]}},
                          "prerequisite_groups": [{"any_of": ["test:route"]}]
                        }
                        """);
    }

    @Test
    void reverseEngineeringPolicyIsStrictBoundedAndBackwardsCompatible() {
        BlueprintResearchProfile legacy = decode(
                BlueprintResearchProfile.CODEC,
                validProfileJson());
        assertEquals(BlueprintReverseEngineeringPolicy.DEFAULT, legacy.reverseEngineering());

        BlueprintResearchProfile profile = decode(
                BlueprintResearchProfile.CODEC,
                validProfileJson().replace(
                        "\"creative_bypasses_cost\": false",
                        """
                        "creative_bypasses_cost": false,
                        "reverse_engineering": {
                          "input_count": 16,
                          "cost": {"points": 3},
                          "allow_known": true,
                          "physical_blueprint_learning": "require_tree_prerequisites",
                          "output_recyclable": false
                        }
                        """));
        assertEquals(16, profile.reverseEngineering().inputCount().orElseThrow());
        assertEquals(3, profile.reverseEngineering().cost().points());
        assertEquals(
                com.gamergaming.taczweaponblueprints.progression.PhysicalBlueprintLearningMode
                        .REQUIRE_TREE_PREREQUISITES,
                profile.reverseEngineering().physicalBlueprintLearningMode());

        assertDecodeFails(
                BlueprintResearchProfile.CODEC,
                validProfileJson().replace(
                        "\"creative_bypasses_cost\": false",
                        "\"creative_bypasses_cost\": false, \"reverse_engineering\": {\"input_count\": 65}"));
        assertDecodeFails(
                BlueprintResearchRule.CODEC,
                """
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "target": {"blueprints": ["test:item"]},
                          "reverse_engineering": {"enabled": true, "unknown": 1}
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

    private static String formatTwoProfileJson() {
        return """
                {
                  "format": 2,
                  "journal_enabled": true,
                  "visibility": "silhouette",
                  "research_enabled": true,
                  "recycling_enabled": true,
                  "allow_unlearned_recycling": false,
                  "recycling_value": 1,
                  "research_cost": {"points": 8},
                  "requires_discovery": false,
                  "creative_bypasses_cost": false,
                  "domain_policies": {
                    "weapons": {
                      "tree_enabled": true,
                      "research_enabled": true
                    },
                    "attachments": {
                      "tree_enabled": false,
                      "research_enabled": false
                    },
                    "ammo": {
                      "tree_enabled": false,
                      "research_enabled": false
                    }
                  }
                }
                """;
    }

    private static <T> T decode(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }

    private static net.minecraft.resources.ResourceLocation id(String value) {
        return new net.minecraft.resources.ResourceLocation(value);
    }

    private static <T> void assertDecodeFails(Codec<T> codec, String json) {
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
    }
}
