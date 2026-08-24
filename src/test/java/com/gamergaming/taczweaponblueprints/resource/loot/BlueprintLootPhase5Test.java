package com.gamergaming.taczweaponblueprints.resource.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BlueprintLootPhase5Test {

    @Test
    void decodesStrictFormatTwoCompositionAndRulePredicates() {
        BlueprintLootPool pool = decode(BlueprintLootPool.CODEC, """
                {
                  "format": 2,
                  "includes": [{"pool": "test:base", "weight": 2.0}],
                  "tags": [{"tag": "test:sidearms", "weight": 3.0}],
                  "selectors": [{
                    "namespaces": ["tacz"],
                    "item_types": ["rifle"],
                    "path_prefixes": ["gun/"],
                    "exclude": ["tacz:gun/blocked"],
                    "weight": 4.0
                  }]
                }
                """);
        BlueprintLootRule rule = decode(BlueprintLootRule.CODEC, """
                {
                  "format": 2,
                  "pool": "test:composed",
                  "loot_tables": [],
                  "loot_table_selector": {
                    "namespaces": ["minecraft"],
                    "path_prefixes": ["chests/"]
                  },
                  "predicate": {
                    "dimensions": ["minecraft:the_nether"],
                    "min_luck": 1.0,
                    "max_luck": 3.0
                  }
                }
                """);

        assertEquals(id("test:base"), pool.includes().get(0).pool());
        assertEquals(4.0f, pool.selectors().get(0).weight());
        assertTrue(rule.lootTableSelector().orElseThrow().matches(id("minecraft:chests/bastion_treasure")));
        assertTrue(rule.predicate().orElseThrow().matches(id("minecraft:the_nether"), 2.0f));
        assertFalse(rule.predicate().orElseThrow().matches(id("minecraft:overworld"), 2.0f));
        assertFalse(rule.predicate().orElseThrow().matches(id("minecraft:the_nether"), 4.0f));
    }

    @Test
    void formatOneRejectsPhaseFiveFieldsAndFormatTwoOptionalsStayStrict() {
        assertDecodeFails(BlueprintLootPool.CODEC, """
                {"format":1,"entries":[{"blueprint":"test:item","weight":1}],"includes":[{"pool":"test:base"}]}
                """);
        assertDecodeFails(BlueprintLootRule.CODEC, """
                {"format":1,"pool":"test:pool","loot_tables":["test:loot"],"predicate":{"min_luck":1}}
                """);
        assertDecodeFails(BlueprintLootRule.CODEC, """
                {"format":3,"pool":"test:pool","loot_tables":["test:loot"]}
                """);
        assertDecodeFails(BlueprintLootPool.CODEC, """
                {"format":2,"entries":{},"selectors":[{"weight":1}]}
                """);
        assertDecodeFails(BlueprintLootPool.CODEC, """
                {"format":2,"includes":[{"pool":"test:base","weight":"2"}]}
                """);
        assertDecodeFails(BlueprintLootRule.CODEC, """
                {"format":2,"pool":"test:pool","loot_tables":[],"loot_table_selector":{"namespaces":"minecraft"}}
                """);
        assertDecodeFails(BlueprintLootPool.CODEC, """
                {"format":2,"selectors":[{"namespaces":["Invalid Namespace"],"weight":1}]}
                """);
    }

    @Test
    void resolvesTagsAndInheritedPoolsWithAdditiveWeights() {
        BlueprintLootTag tag = new BlueprintLootTag(1, List.of(id("test:a"), id("test:b")));
        BlueprintLootPool base = new BlueprintLootPool(
                2,
                List.of(new BlueprintLootEntry(id("test:a"), 1.0f)),
                List.of(),
                List.of(),
                List.of(new BlueprintCatalogSelector(List.of("tacz"), List.of("rifle"), List.of(), List.of(), 2.0f)));
        BlueprintLootPool composed = new BlueprintLootPool(
                2,
                List.of(new BlueprintLootEntry(id("test:c"), 4.0f)),
                List.of(new BlueprintLootPoolReference(id("test:base"), 2.0f)),
                List.of(new BlueprintLootTagReference(id("test:group"), 3.0f)),
                List.of());

        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(
                Map.of(id("test:group"), tag),
                Map.of(id("test:base"), base, id("test:composed"), composed),
                Map.of(id("test:rule"), rule(id("test:composed"))));
        BlueprintLootPool resolved = snapshot.pools().get(id("test:composed"));

        assertEquals(
                Map.of(id("test:a"), 5.0f, id("test:b"), 3.0f, id("test:c"), 4.0f),
                resolved.entries().stream().collect(java.util.stream.Collectors.toMap(
                        BlueprintLootEntry::blueprint,
                        BlueprintLootEntry::weight)));
        assertEquals(4.0f, resolved.selectors().get(0).weight());
        assertTrue(resolved.includes().isEmpty());
        assertTrue(resolved.tags().isEmpty());
    }

    @Test
    void rejectsMissingCompositionReferencesAndInheritanceCycles() {
        BlueprintLootPool missingTag = new BlueprintLootPool(
                2, List.of(), List.of(), List.of(new BlueprintLootTagReference(id("test:missing"), 1.0f)), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintLootSnapshot.create(Map.of(), Map.of(id("test:pool"), missingTag), Map.of()));

        BlueprintLootPool first = new BlueprintLootPool(
                2, List.of(), List.of(new BlueprintLootPoolReference(id("test:second"), 1.0f)), List.of(), List.of());
        BlueprintLootPool second = new BlueprintLootPool(
                2, List.of(), List.of(new BlueprintLootPoolReference(id("test:first"), 1.0f)), List.of(), List.of());
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintLootSnapshot.create(
                        Map.of(),
                        Map.of(id("test:first"), first, id("test:second"), second),
                        Map.of()));
    }

    @Test
    void rejectsInheritedWeightsThatUnderflowWhenNarrowedToFloat() {
        BlueprintLootPool base = new BlueprintLootPool(
                2,
                List.of(),
                List.of(),
                List.of(),
                List.of(new BlueprintCatalogSelector(
                        List.of("tacz"), List.of(), List.of(), List.of(), Float.MIN_VALUE)));
        BlueprintLootPool inherited = new BlueprintLootPool(
                2,
                List.of(),
                List.of(new BlueprintLootPoolReference(id("test:base"), Float.MIN_VALUE)),
                List.of(),
                List.of());

        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintLootSnapshot.create(
                        Map.of(),
                        Map.of(id("test:base"), base, id("test:inherited"), inherited),
                        Map.of()));
    }

    @Test
    void catalogSelectorsResolveDeterministicallyAndCombineWithExplicitEntries() {
        ResourceLocation selectedId = id("tacz:gun/ak47");
        ResourceLocation blockedId = id("tacz:gun/blocked");
        BlueprintLootPool pool = new BlueprintLootPool(
                2,
                List.of(new BlueprintLootEntry(selectedId, 1.0f)),
                List.of(),
                List.of(),
                List.of(new BlueprintCatalogSelector(
                        List.of("tacz"),
                        List.of("rifle"),
                        List.of("gun/"),
                        List.of(blockedId),
                        2.0f)));

        List<BlueprintLootEntry> resolved = BlueprintLootCatalogCache.resolve(
                pool,
                Map.of(
                        selectedId, blueprint(selectedId, "rifle"),
                        blockedId, blueprint(blockedId, "rifle"),
                        id("classicr:gun/other"), blueprint(id("classicr:gun/other"), "rifle"),
                        id("tacz:ammo/9mm"), blueprint(id("tacz:ammo/9mm"), "ammo")));

        assertEquals(List.of(new BlueprintLootEntry(selectedId, 3.0f)), resolved);
    }

    @Test
    void tableSelectorsAreDeduplicatedAgainstExactBindingsAndOwnMatchingTables() {
        BlueprintLootPool pool = new BlueprintLootPool(
                1,
                List.of(new BlueprintLootEntry(id("test:blueprint"), 1.0f)));
        ResourceLocation table = id("minecraft:chests/simple_dungeon");
        BlueprintLootRule rule = new BlueprintLootRule(
                2,
                true,
                id("test:pool"),
                List.of(table),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new BlueprintLootTableSelector(List.of("minecraft"), List.of("chests/"))),
                Optional.empty());
        BlueprintLootSnapshot snapshot = BlueprintLootSnapshot.create(
                Map.of(id("test:pool"), pool),
                Map.of(id("test:rule"), rule));

        assertEquals(1, snapshot.rulesFor(table).size());
        assertEquals(1, snapshot.rulesFor(id("minecraft:chests/another")).size());
        assertTrue(snapshot.ownsLootTable(id("minecraft:chests/another")));
        assertFalse(snapshot.ownsLootTable(id("other:chests/another")));

        BlueprintLootRule disabledSelector = new BlueprintLootRule(
                2,
                false,
                id("missing:pool"),
                List.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new BlueprintLootTableSelector(List.of("minecraft"), List.of("chests/"))),
                Optional.empty());
        BlueprintLootSnapshot disabled = BlueprintLootSnapshot.create(
                Map.of(),
                Map.of(id("test:disabled"), disabledSelector));
        assertTrue(disabled.ownsLootTable(id("minecraft:chests/another")));
        assertFalse(disabled.ownsLootTable(id("other:chests/another")));
    }

    private static BlueprintLootRule rule(ResourceLocation pool) {
        return new BlueprintLootRule(
                1,
                true,
                pool,
                List.of(id("test:loot")),
                Optional.empty(),
                Optional.empty());
    }

    private static BlueprintData blueprint(ResourceLocation id, String itemType) {
        return new BlueprintData(
                id.toString(),
                "item.test.name",
                "item.test.tooltip",
                new ResourceLocation("test", "recipe/" + id.getPath()),
                null,
                itemType,
                new ResourceLocation("test", "display/" + itemType));
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
