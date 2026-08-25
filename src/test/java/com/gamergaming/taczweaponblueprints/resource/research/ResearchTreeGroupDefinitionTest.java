package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeGroupDefinitionTest {
    @Test
    void decodesStrictVersionedGroupAndPreservesAuthoredOrder() {
        ResearchTreeGroupDefinition group = decode("""
                {
                  "format": 1,
                  "profile": "test:profile",
                  "title": "Pistols",
                  "translation_key": "gui.test.groups.pistols",
                  "icon": "test:starter",
                  "order": 20,
                  "ranks": [
                    ["test:starter"],
                    ["test:left", "test:right"]
                  ]
                }
                """);

        assertEquals("Pistols", group.title());
        assertEquals(Optional.of("gui.test.groups.pistols"), group.translationKey());
        assertEquals(20, group.order());
        assertEquals(2, group.ranks().size());
        assertEquals(List.of(id("test:starter"), id("test:left"), id("test:right")), group.members());
    }

    @Test
    void rejectsUnknownFieldsMalformedTextAndUnsafeBounds() {
        assertDecodeFails(validJson().replace("\"order\": 10,", "\"order\": 10, \"unknown\": true,"));
        assertDecodeFails(validJson().replace("\"Pistols\"", "\" Pistols\""));
        assertDecodeFails(validJson().replace(
                "\"gui.test.groups.pistols\"",
                "\"gui.test.groups bad\""));
        assertDecodeFails(validJson().replace("\"order\": 10", "\"order\": -1"));
        assertDecodeFails(validJson().replace("\"format\": 1", "\"format\": 2"));
        assertDecodeFails(validJson().replace(
                "[\"test:starter\"],\n    [\"test:advanced\"]",
                "[\"test:starter\"],\n    []"));
    }

    @Test
    void rejectsDuplicateMembersAndIconsOutsideTheGroup() {
        assertDecodeFails(validJson().replace("\"test:advanced\"", "\"test:starter\""));
        assertDecodeFails(validJson().replace(
                "\"icon\": \"test:starter\"",
                "\"icon\": \"test:not_a_member\""));
    }

    @Test
    void programmaticDefinitionsAreDeeplyImmutableAndRevalidated() {
        List<ResourceLocation> mutableRank = new ArrayList<>();
        mutableRank.add(id("test:starter"));
        List<List<ResourceLocation>> mutableRanks = new ArrayList<>();
        mutableRanks.add(mutableRank);
        ResearchTreeGroupDefinition definition = new ResearchTreeGroupDefinition(
                1,
                id("test:profile"),
                "Pistols",
                Optional.empty(),
                id("test:starter"),
                10,
                mutableRanks);

        mutableRank.add(id("test:late"));
        mutableRanks.clear();
        assertEquals(List.of(List.of(id("test:starter"))), definition.ranks());
        assertThrows(UnsupportedOperationException.class, () ->
                definition.ranks().get(0).add(id("test:other")));
        assertThrows(IllegalArgumentException.class, () -> new ResearchTreeGroupDefinition(
                1,
                id("test:profile"),
                "Pistols",
                Optional.empty(),
                id("test:starter"),
                10,
                List.of(List.of(id("test:starter")), List.of(id("test:starter")))));
    }

    @Test
    void rejectsRankAndMemberCountsAtTheCodecBoundary() {
        String tooManyRanks = IntStream.rangeClosed(0, ResearchTreeGroupDefinition.MAX_RANKS)
                .mapToObj(index -> "[\"test:member_" + index + "\"]")
                .collect(java.util.stream.Collectors.joining(","));
        assertDecodeFails(groupJson("test:member_0", tooManyRanks));

        String tooManyMembers = IntStream.rangeClosed(0, ResearchTreeGroupDefinition.MAX_MEMBERS)
                .mapToObj(index -> "\"test:member_" + index + "\"")
                .collect(java.util.stream.Collectors.joining(","));
        assertDecodeFails(groupJson("test:member_0", "[" + tooManyMembers + "]"));
    }

    private static String validJson() {
        return """
                {
                  "format": 1,
                  "profile": "test:profile",
                  "title": "Pistols",
                  "translation_key": "gui.test.groups.pistols",
                  "icon": "test:starter",
                  "order": 10,
                  "ranks": [
                    ["test:starter"],
                    ["test:advanced"]
                  ]
                }
                """;
    }

    private static String groupJson(String icon, String ranks) {
        return """
                {
                  "format": 1,
                  "profile": "test:profile",
                  "title": "Bounded",
                  "icon": "%s",
                  "order": 10,
                  "ranks": [%s]
                }
                """.formatted(icon, ranks);
    }

    private static ResearchTreeGroupDefinition decode(String json) {
        return ResearchTreeGroupDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString(json))
                .result()
                .orElseThrow();
    }

    private static void assertDecodeFails(String json) {
        assertTrue(ResearchTreeGroupDefinition.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString(json))
                .error()
                .isPresent());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
