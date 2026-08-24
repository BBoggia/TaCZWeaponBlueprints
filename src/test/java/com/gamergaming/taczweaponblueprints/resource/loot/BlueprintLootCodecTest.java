package com.gamergaming.taczweaponblueprints.resource.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BlueprintLootCodecTest {

    @Test
    void decodesVersionedPoolAndOptionalRulePolicy() {
        BlueprintLootPool pool = decode(
                BlueprintLootPool.CODEC,
                """
                        {
                          "format": 1,
                          "entries": [
                            {"blueprint": "classicr:ak_alpha", "weight": 12.5}
                          ]
                        }
                        """);
        BlueprintLootRule rule = decode(
                BlueprintLootRule.CODEC,
                """
                        {
                          "format": 1,
                          "pool": "test:weapons",
                          "loot_tables": ["minecraft:chests/simple_dungeon"],
                          "chance": 0.75,
                          "rolls": {"min": 2, "max": 4}
                        }
                        """);

        assertEquals(id("classicr:ak_alpha"), pool.entries().get(0).blueprint());
        assertEquals(12.5f, pool.entries().get(0).weight());
        assertEquals(Optional.of(0.75f), rule.chance());
        assertEquals(Optional.of(new BlueprintLootRolls(2, 4)), rule.rolls());
    }

    @Test
    void rejectsUnknownFormatsAndUnsafeValues() {
        assertTrue(BlueprintLootPool.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"format\":3,\"entries\":[{\"blueprint\":\"test:item\",\"weight\":1}]}"))
                .error().isPresent());
        assertTrue(BlueprintLootRule.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"format\":1,\"pool\":\"test:pool\",\"loot_tables\":[\"test:loot\"],\"chance\":2}"))
                .error().isPresent());
        assertTrue(BlueprintLootRule.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString("{\"format\":1,\"pool\":\"test:pool\",\"loot_tables\":[\"test:loot\"],\"rolls\":{\"min\":4,\"max\":2}}"))
                .error().isPresent());
    }

    @Test
    void rejectsMalformedOptionalValuesInsteadOfApplyingDefaults() {
        assertTrue(BlueprintLootRule.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                        "{\"format\":1,\"enabled\":\"false\",\"pool\":\"test:pool\",\"loot_tables\":[\"test:loot\"]}"))
                .error().isPresent());
        assertTrue(BlueprintLootRule.CODEC.parse(
                JsonOps.INSTANCE,
                JsonParser.parseString(
                        "{\"format\":1,\"pool\":\"test:pool\",\"loot_tables\":[\"test:loot\"],\"chance\":\"0.5\"}"))
                .error().isPresent());
    }

    @Test
    void rejectsUnknownFieldsAtEverySchemaLevel() {
        assertDecodeFails(
                BlueprintLootPool.CODEC,
                "{\"format\":1,\"entries\":[{\"blueprint\":\"test:item\",\"weight\":1}],\"extra\":true}");
        assertDecodeFails(
                BlueprintLootPool.CODEC,
                "{\"format\":1,\"entries\":[{\"blueprint\":\"test:item\",\"weight\":1,\"extra\":true}]}");
        assertDecodeFails(
                BlueprintLootRule.CODEC,
                "{\"format\":1,\"pool\":\"test:pool\",\"loot_tables\":[\"test:loot\"],\"probability\":0.5}");
        assertDecodeFails(
                BlueprintLootRule.CODEC,
                "{\"format\":1,\"pool\":\"test:pool\",\"loot_tables\":[\"test:loot\"],\"rolls\":{\"min\":1,\"max\":2,\"extra\":true}}");
    }

    @Test
    void snapshotBuildsDeterministicBindingsAndAllowsIntentionalOverlap() {
        BlueprintLootPool pool = new BlueprintLootPool(
                1,
                List.of(new BlueprintLootEntry(id("test:blueprint"), 1.0f)));
        BlueprintLootRule firstRule = rule("test:pool", true, "minecraft:chests/simple_dungeon");
        BlueprintLootRule secondRule = rule("test:pool", true, "minecraft:chests/simple_dungeon");
        BlueprintLootRule disabledRule = rule("missing:pool", false, "minecraft:chests/simple_dungeon");

        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(
                Map.of(id("test:pool"), pool),
                Map.of(
                        id("test:z_rule"), secondRule,
                        id("test:a_rule"), firstRule,
                        id("test:disabled"), disabledRule));

        assertTrue(snapshot.active());
        assertEquals(2, snapshot.bindingCount());
        assertEquals(
                List.of(id("test:a_rule"), id("test:z_rule")),
                snapshot.rulesFor(id("minecraft:chests/simple_dungeon")).stream()
                        .map(BlueprintLootSnapshot.RuleBinding::ruleId)
                        .toList());
        assertFalse(snapshot.rulesByLootTable().containsKey(id("test:unused")));
    }

    @Test
    void snapshotRejectsEnabledRuleWithMissingPool() {
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintLootSnapshot.create(
                        Map.of(),
                        Map.of(id("test:rule"), rule("test:missing", true, "test:loot"))));
    }

    @Test
    void disabledRulesStillOwnDistributionWithoutActiveBindings() {
        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(
                Map.of(),
                Map.of(id("test:disabled"), rule("missing:pool", false, "test:loot")));

        assertTrue(snapshot.ownsDistribution());
        assertFalse(snapshot.active());
        assertEquals(0, snapshot.bindingCount());
        assertTrue(snapshot.ownsLootTable(id("test:loot")));
        assertFalse(snapshot.ownsLootTable(id("test:any_loot_table")));

        BlueprintLootSnapshot globalOptOut = BlueprintLootSnapshot.create(
                Map.of(),
                Map.of(id("test:disabled"), rule("missing:pool", false)));
        assertTrue(globalOptOut.ownsLootTable(id("test:any_loot_table")));
    }

    @Test
    void poolsAloneDoNotDisableLegacyFallbackAndPartialRulesOwnOnlyTheirTargets() {
        BlueprintLootPool pool = new BlueprintLootPool(
                1,
                List.of(new BlueprintLootEntry(id("test:blueprint"), 1.0f)));
        BlueprintLootSnapshot poolsOnly = BlueprintLootSnapshot.create(
                Map.of(id("test:pool"), pool),
                Map.of());
        assertFalse(poolsOnly.ownsDistribution());
        assertFalse(poolsOnly.ownsLootTable(id("test:loot")));

        BlueprintLootSnapshot partial = BlueprintLootSnapshot.create(
                Map.of(id("test:pool"), pool),
                Map.of(id("test:rule"), rule("test:pool", true, "test:owned")));
        assertTrue(partial.ownsLootTable(id("test:owned")));
        assertFalse(partial.ownsLootTable(id("test:legacy")));
    }

    @Test
    void derivesStableDefinitionIdsFromDatapackPaths() {
        assertEquals(
                id("example:rare/nether"),
                BlueprintLootDataManager.definitionId(
                        id("example:taczweaponblueprints/loot_pools/rare/nether.json"),
                        BlueprintLootDataManager.POOL_DIRECTORY));
    }

    private static BlueprintLootRule rule(String pool, boolean enabled, String... lootTables) {
        return new BlueprintLootRule(
                1,
                enabled,
                id(pool),
                List.of(lootTables).stream().map(BlueprintLootCodecTest::id).toList(),
                Optional.empty(),
                Optional.empty());
    }

    private static <T> T decode(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }

    private static <T> void assertDecodeFails(Codec<T> codec, String json) {
        assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException(value);
        }
        return id;
    }
}
