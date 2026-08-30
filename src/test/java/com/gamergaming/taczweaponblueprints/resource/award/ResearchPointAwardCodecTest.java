package com.gamergaming.taczweaponblueprints.resource.award;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.IntStream;
import java.util.Map;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchPointAwardCodecTest {
    @Test
    void decodesAndRoundTripsThePhaseZeroRepresentativeDefinition() {
        ResearchPointAwardDefinition definition = decode(
                ResearchPointAwardDefinition.CODEC,
                """
                        {
                          "format": 1,
                          "enabled": true,
                          "profiles": ["taczweaponblueprints:duplicate_recovery"],
                          "award_group": "taczweaponblueprints:pistol_discovery",
                          "priority": 100,
                          "trigger": {
                            "type": "blueprint_discovered",
                            "target": {
                              "catalog_selector": {"category": "pistol"}
                            }
                          },
                          "reward": {"points": 2, "overflow": "clamp"},
                          "repeat": {
                            "type": "once_per_target",
                            "claim_id": "example:pistol_discovery"
                          },
                          "presentation": {
                            "visibility": "public",
                            "name": "research_points.example.pistol_discovery"
                          }
                        }
                        """);

        assertEquals(100, definition.priority());
        assertEquals(2, definition.reward().points());
        assertEquals(ResearchPointAwardRepeat.Type.ONCE_PER_TARGET, definition.repeat().type());
        assertEquals(new ResourceLocation("example", "pistol_discovery"),
                definition.repeat().claimId().orElseThrow());
        assertEquals("pistol", definition.trigger().target().orElseThrow()
                .catalogSelector().orElseThrow().category().orElseThrow());

        JsonElement encoded = ResearchPointAwardDefinition.CODEC
                .encodeStart(JsonOps.INSTANCE, definition).result().orElseThrow();
        assertEquals(definition, ResearchPointAwardDefinition.CODEC
                .parse(JsonOps.INSTANCE, encoded).result().orElseThrow());
    }

    @Test
    void rejectsUnknownFieldsAtEverySchemaLevel() {
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"format\": 1,", "\"format\": 1, \"extra\": true,"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"points\": 2", "\"points\": 2, \"extra\": true"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"type\": \"once_per_target\"",
                        "\"type\": \"once_per_target\", \"extra\": true"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"ids\": [\"test:item\"]",
                        "\"ids\": [\"test:item\"], \"extra\": true"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"visibility\": \"public\"",
                        "\"visibility\": \"public\", \"extra\": true"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"overflow\": \"clamp\"",
                        "\"overflow\": \"clamp\", \"extra\": true"));
    }

    @Test
    void rejectsInvalidEnumsValuesAndUnsafeReplayCombinations() {
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("blueprint_discovered", "not_a_trigger"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"points\": 2", "\"points\": 0"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"priority\": 100", "\"priority\": 1000001"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("once_per_target", "unknown_repeat"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"overflow\": \"clamp\"",
                        "\"overflow\": \"unknown\""));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"overflow\": \"clamp\"",
                        "\"overflow\": \"require_full\"")
                        .replace("\"retroactive\": true,", ""));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"type\": \"once_per_target\"",
                        "\"type\": \"unlimited\"")
                        .replace(",\n    \"claim_id\": \"test:claim\"", ""));
    }

    @Test
    void enforcesTypedTriggerAndRepeatFields() {
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"type\": \"blueprint_discovered\"",
                        "\"type\": \"blueprint_milestone\""));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"type\": \"blueprint_discovered\"",
                        "\"type\": \"entity_killed\", \"retroactive\": true"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"target\": {",
                        "\"combat\": {},\n    \"target\": {"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"type\": \"once_per_target\"",
                        "\"type\": \"cooldown\""));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"type\": \"once_per_target\"",
                        "\"type\": \"once_per_target\", \"cooldown_ticks\": 20"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"type\": \"once_per_target\"",
                        "\"type\": \"windowed\", \"window_ticks\": 200, \"max_awards\": 2"));
    }

    @Test
    void acceptsEverySupportedRepeatShapeAndSharedBudget() {
        String base = validDefinition();
        decode(ResearchPointAwardDefinition.CODEC, base);
        decode(ResearchPointAwardDefinition.CODEC, base
                .replace("\"type\": \"once_per_target\"", "\"type\": \"once\""));
        decode(ResearchPointAwardDefinition.CODEC, repeatableDefinition(
                "\"type\": \"cooldown\", \"scope\": \"target\", \"cooldown_ticks\": 200"));
        decode(ResearchPointAwardDefinition.CODEC, repeatableDefinition(
                "\"type\": \"windowed\", \"window_ticks\": 1200, "
                        + "\"max_awards\": 4, \"max_points\": 20"));
        ResearchPointAwardDefinition unlimited = decode(
                ResearchPointAwardDefinition.CODEC,
                repeatableDefinition("\"type\": \"unlimited\"")
                        .replace("\"presentation\": {",
                                "\"budget\": {\"id\": \"test:combat\", \"max_awards\": 8, "
                                        + "\"max_points\": 32, \"window_ticks\": 1200},\n"
                                        + "  \"presentation\": {"));

        assertEquals(new ResourceLocation("test", "combat"),
                unlimited.budget().orElseThrow().id());
    }

    @Test
    void enforcesSelectorProfileAndPresentationBounds() {
        String profiles = IntStream.range(0, 65)
                .mapToObj(index -> "\"test:profile_" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"profiles\": [\"test:profile\"]",
                        "\"profiles\": [" + profiles + "]"));

        String ids = IntStream.range(0, 257)
                .mapToObj(index -> "\"test:item_" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"ids\": [\"test:item\"]",
                        "\"ids\": [" + ids + "]"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"visibility\": \"public\",\n    \"name\": \"test.award\"",
                        "\"visibility\": \"public\""));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                validDefinition().replace("\"ids\": [\"test:item\"]",
                        "\"catalog_selector\": {}"));
    }

    @Test
    void requiresSafeInventoryTurnInAndMilestoneShapes() {
        String inventory = repeatableDefinition("\"type\": \"unlimited\"")
                .replace("blueprint_discovered", "inventory_turn_in")
                .replace("\"overflow\": \"clamp\"", "\"overflow\": \"require_full\"");
        decode(ResearchPointAwardDefinition.CODEC, inventory);
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                inventory.replace("\"ids\": [\"test:item\"]", "\"ids\": []"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                inventory.replace("\"overflow\": \"require_full\"", "\"overflow\": \"clamp\""));

        String milestone = validDefinition()
                .replace("\"type\": \"blueprint_discovered\"",
                        "\"type\": \"blueprint_milestone\", "
                                + "\"milestone\": {\"state\": \"learned\", \"threshold\": 10}")
                .replace("\"type\": \"once_per_target\"", "\"type\": \"once\"");
        decode(ResearchPointAwardDefinition.CODEC, milestone);
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                milestone.replace("\"type\": \"once\"", "\"type\": \"once_per_target\""));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                milestone.replace("\"type\": \"once\"", "\"type\": \"unlimited\""));
    }

    @Test
    void integrationTriggersAreExplicitLiveServerEventsWithBoundedSelectors() {
        String integration = repeatableDefinition("\"type\": \"cooldown\", \"cooldown_ticks\": 20")
                .replace("blueprint_discovered", "integration");
        ResearchPointAwardDefinition decoded = decode(
                ResearchPointAwardDefinition.CODEC, integration);
        assertEquals(ResearchPointAwardTrigger.Type.INTEGRATION, decoded.trigger().type());
        ResearchPointAwardSnapshot integrationSnapshot = ResearchPointAwardSnapshot.create(Map.of(
                new ResourceLocation("test:integration_award"), decoded));
        var resolution = ResearchPointAwardResolver.resolve(
                integrationSnapshot,
                ResearchPointAwardContext.simple(
                        ResearchPointAwardTrigger.Type.INTEGRATION,
                        new ResourceLocation("test:profile"),
                        new ResourceLocation("test:item")));
        assertEquals(1, resolution.awards().size());
        assertEquals(java.util.List.of(new ResourceLocation("test:item")),
                ResearchPointAwardDiagnostics.integrationSourceIds(integrationSnapshot));

        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                integration.replace("\"target\": {", "\"retroactive\": true, \"target\": {"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                integration.replace("\"ids\": [\"test:item\"]",
                        "\"tags\": [\"test:integration_sources\"]"));
        assertDecodeFails(ResearchPointAwardDefinition.CODEC,
                integration.replace("\"ids\": [\"test:item\"]",
                        "\"catalog_selector\": {\"category\": \"pistol\"}"));
    }

    private static String validDefinition() {
        return """
                {
                  "format": 1,
                  "enabled": true,
                  "profiles": ["test:profile"],
                  "award_group": "test:group",
                  "priority": 100,
                  "trigger": {
                    "type": "blueprint_discovered",
                    "retroactive": true,
                    "target": {"ids": ["test:item"]}
                  },
                  "reward": {"points": 2, "overflow": "clamp"},
                  "repeat": {
                    "type": "once_per_target",
                    "claim_id": "test:claim"
                  },
                  "presentation": {
                    "visibility": "public",
                    "name": "test.award"
                  }
                }
                """;
    }

    private static String repeatableDefinition(String repeatFields) {
        return validDefinition()
                .replace("\"retroactive\": true,\n", "")
                .replace("\"type\": \"once_per_target\",\n    \"claim_id\": \"test:claim\"",
                        repeatFields);
    }

    private static <T> T decode(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }

    private static <T> void assertDecodeFails(Codec<T> codec, String json) {
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent(), json);
    }
}
