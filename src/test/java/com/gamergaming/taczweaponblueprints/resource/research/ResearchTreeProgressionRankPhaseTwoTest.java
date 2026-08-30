package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

/** Focused Phase 2 coverage for the format-1 conversion and format-2 rank boundary. */
class ResearchTreeProgressionRankPhaseTwoTest {
    private static final ResourceLocation PROFILE = id("rank_test:profile");
    private static final ResourceLocation TREE = id("rank_test:tree");
    private static final ResourceLocation LANE = id("rank_test:weapons/general");

    @AfterEach
    void clearPolicyCache() {
        BlueprintResearchPolicyResolver.clearCache();
    }

    @Test
    void rankAloneControlsProgressionWhileSiblingOrderAndBandRemainHints() {
        ProgressionCoordinate earlierRank = new ProgressionCoordinate(37, 999, Optional.empty());
        ProgressionCoordinate laterRank = new ProgressionCoordinate(
                38,
                0,
                Optional.of(id("rank_test:custom_band")));
        ProgressionCoordinate sameRankEarlierSibling = new ProgressionCoordinate(
                37,
                1,
                Optional.of(id("rank_test:other_band")));

        assertTrue(ResearchTechTreeContract.progressionTransitionAllowed(
                earlierRank, laterRank));
        assertFalse(ResearchTechTreeContract.progressionTransitionAllowed(
                sameRankEarlierSibling, earlierRank));
        assertFalse(ResearchTechTreeContract.progressionTransitionAllowed(
                earlierRank, sameRankEarlierSibling));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionCoordinate(-1, 0, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionCoordinate(
                        ResearchTechTreeContract.MAX_PROGRESSION_RANK + 1,
                        0,
                        Optional.empty()));

        ProgressionCoordinate legacy = ResearchTechTreeContract.legacyProgressionCoordinate(
                new ProgressionPosition(Tier.BASIC, 2, 41));
        assertEquals(ResearchTechTreeContract.LEGACY_RANK_STRIDE + 2, legacy.rank());
        assertEquals(41, legacy.siblingOrder());
        assertEquals(ResearchTechTreeContract.legacyBandId(Tier.BASIC),
                legacy.bandId().orElseThrow());
    }

    @Test
    void entryBundleFormatTwoRequiresOneBoundedExplicitRank() {
        ResearchTechTreeEntryBundle legacy = decode(
                ResearchTechTreeEntryBundle.CODEC,
                bundleJson(1, ""));
        assertEquals(ResearchTechTreeEntryBundle.LEGACY_FORMAT, legacy.format());
        assertTrue(legacy.entries().get(0).rank().isEmpty());

        ResearchTechTreeEntryBundle ranked = decode(
                ResearchTechTreeEntryBundle.CODEC,
                bundleJson(2, "\"rank\": 73,"));
        assertEquals(ResearchTechTreeEntryBundle.CURRENT_FORMAT, ranked.format());
        assertEquals(73, ranked.entries().get(0).rank().orElseThrow());
        assertEquals(73, ranked.entries().get(0)
                .initialProgressionCoordinate(ranked.format()).rank());

        assertDecodeFails(ResearchTechTreeEntryBundle.CODEC, bundleJson(2, ""));
        assertDecodeFails(
                ResearchTechTreeEntryBundle.CODEC,
                bundleJson(1, "\"rank\": 3,"));
        assertDecodeFails(
                ResearchTechTreeEntryBundle.CODEC,
                bundleJson(2, "\"rank\": -1,"));
        assertDecodeFails(
                ResearchTechTreeEntryBundle.CODEC,
                bundleJson(
                        2,
                        "\"rank\": "
                                + (ResearchTechTreeContract.MAX_PROGRESSION_RANK + 1)
                                + ","));
    }

    @Test
    void formatOneSamePositionChainsAreLiftedWithoutChangingResearchAuthority() {
        ResourceLocation root = id("rank_test:root");
        ResourceLocation middle = id("rank_test:middle");
        ResourceLocation top = id("rank_test:top");
        BlueprintResearchSnapshot snapshot = snapshot(
                Map.of(
                        id("rank_test:root_rule"), rule(root, 3, List.of()),
                        id("rank_test:middle_rule"), rule(middle, 5, List.of(root)),
                        id("rank_test:top_rule"), rule(top, 7, List.of(middle))),
                bundle(
                        1,
                        legacyEntry(root, Tier.STARTER, 0, 10),
                        legacyEntry(middle, Tier.STARTER, 0, 20),
                        legacyEntry(top, Tier.STARTER, 0, 30)));

        ProgressionCoordinate rootCoordinate = snapshot
                .techTreeProgressionFor(PROFILE, root).orElseThrow();
        ProgressionCoordinate middleCoordinate = snapshot
                .techTreeProgressionFor(PROFILE, middle).orElseThrow();
        ProgressionCoordinate topCoordinate = snapshot
                .techTreeProgressionFor(PROFILE, top).orElseThrow();
        assertEquals(0, rootCoordinate.rank());
        assertEquals(1, middleCoordinate.rank());
        assertEquals(2, topCoordinate.rank());
        assertEquals(List.of(root), definition(snapshot, middle).prerequisites());
        assertEquals(List.of(middle), definition(snapshot, top).prerequisites());
        assertEquals(5, definition(snapshot, middle).researchCost().points());
        assertEquals(7, definition(snapshot, top).researchCost().points());

        ResearchTechTreePlacementResolver.Placement resolved =
                ResearchTechTreePlacementResolver.resolveForProfile(
                                snapshot,
                                PROFILE,
                                TREE,
                                top,
                                data(top))
                        .placement().orElseThrow();
        assertEquals(2, resolved.progressionCoordinate().rank());
        assertEquals(30, resolved.progressionCoordinate().siblingOrder());
        assertEquals(Tier.STARTER, resolved.tier(),
                "legacy presentation tier must not be rewritten");
        assertEquals(0, resolved.level(),
                "legacy presentation level must not be rewritten");
    }

    @Test
    void formatTwoRanksAreStrictAndDoNotUseTierOrSiblingOrderForEdgeLegality() {
        ResourceLocation root = id("rank_test:ranked_root");
        ResourceLocation dependent = id("rank_test:ranked_dependent");
        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("rank_test:ranked_root_rule"), rule(root, 3, List.of()),
                id("rank_test:ranked_dependent_rule"), rule(dependent, 5, List.of(root)));

        BlueprintResearchSnapshot valid = snapshot(
                rules,
                bundle(
                        2,
                        rankedEntry(root, Tier.APEX, 73, 900),
                        rankedEntry(dependent, Tier.STARTER, 74, 1)));
        assertEquals(73, valid.techTreeProgressionFor(PROFILE, root).orElseThrow().rank());
        assertEquals(74, valid.techTreeProgressionFor(PROFILE, dependent).orElseThrow().rank());

        assertThrows(IllegalArgumentException.class, () -> snapshot(
                rules,
                bundle(
                        2,
                        rankedEntry(root, Tier.STARTER, 73, 10),
                        rankedEntry(dependent, Tier.STARTER, 73, 20))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                rules,
                bundle(
                        2,
                        rankedEntry(root, Tier.STARTER, 74, 10),
                        rankedEntry(dependent, Tier.APEX, 73, 20))));
    }

    @Test
    void catalogAwareValidationRejectsEqualAndBackwardFormatTwoSelectorRanks() {
        ResourceLocation root = id("rank_test:selector_root");
        ResourceLocation dependent = id("rank_test:selector_dependent");
        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("rank_test:selector_root_rule"), rule(root, 3, List.of()),
                id("rank_test:selector_dependent_rule"),
                rule(dependent, 5, List.of(root)));
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                root, data(root),
                dependent, data(dependent));

        BlueprintResearchSnapshot valid = snapshot(
                rules,
                bundle(
                        2,
                        rankedEntry(root, Tier.APEX, 73, 10),
                        rankedSelectorEntry(74, 20)));
        assertDoesNotThrow(() -> ResearchTechTreeCatalogValidator.validate(
                valid, catalog));

        BlueprintResearchSnapshot equal = snapshot(
                rules,
                bundle(
                        2,
                        rankedEntry(root, Tier.STARTER, 73, 10),
                        rankedSelectorEntry(73, 20)));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeCatalogValidator.validate(equal, catalog));

        BlueprintResearchSnapshot backward = snapshot(
                rules,
                bundle(
                        2,
                        rankedEntry(root, Tier.STARTER, 74, 10),
                        rankedSelectorEntry(73, 20)));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchTechTreeCatalogValidator.validate(backward, catalog));
    }

    private static BlueprintResearchSnapshot snapshot(
            Map<ResourceLocation, BlueprintResearchRule> rules,
            ResearchTechTreeEntryBundle bundle) {
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                rules,
                Map.of(),
                Map.of(TREE, tree()),
                Map.of(id("rank_test:entries"), bundle));
    }

    private static ResearchTechTreeEntryBundle bundle(
            int format,
            ResearchTechTreeEntryBundle.Entry... entries) {
        return new ResearchTechTreeEntryBundle(format, TREE, 0, List.of(entries));
    }

    private static ResearchTechTreeEntryBundle.Entry legacyEntry(
            ResourceLocation id,
            Tier tier,
            int level,
            int order) {
        return new ResearchTechTreeEntryBundle.Entry(
                exactTarget(id),
                Domain.WEAPONS,
                LANE,
                tier,
                level,
                order,
                Optional.empty(),
                Optional.empty(),
                false);
    }

    private static ResearchTechTreeEntryBundle.Entry rankedEntry(
            ResourceLocation id,
            Tier tier,
            int rank,
            int order) {
        return new ResearchTechTreeEntryBundle.Entry(
                exactTarget(id),
                Domain.WEAPONS,
                LANE,
                tier,
                0,
                Optional.of(rank),
                order,
                Optional.empty(),
                Optional.empty(),
                false);
    }

    private static ResearchTechTreeEntryBundle.Entry rankedSelectorEntry(
            int rank,
            int order) {
        BlueprintCatalogSelector selector = new BlueprintCatalogSelector(
                List.of("rank_test"),
                List.of(),
                List.of(),
                List.of(),
                List.of(BlueprintKind.GUN),
                1.0F);
        return new ResearchTechTreeEntryBundle.Entry(
                new BlueprintResearchTarget(
                        List.of(), List.of(), Optional.of(selector)),
                Domain.WEAPONS,
                LANE,
                Tier.STARTER,
                0,
                Optional.of(rank),
                order,
                Optional.empty(),
                Optional.empty(),
                false);
    }

    private static BlueprintResearchRule rule(
            ResourceLocation target,
            int points,
            List<ResourceLocation> prerequisites) {
        return new BlueprintResearchRule(
                1,
                PROFILE,
                0,
                exactTarget(target),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(new BlueprintResearchCost(points, List.of())),
                Optional.empty(),
                prerequisites.isEmpty() ? Optional.empty() : Optional.of(prerequisites),
                Optional.empty());
    }

    private static BlueprintResearchProfile profile() {
        return new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.SILHOUETTE,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false,
                true,
                List.of(),
                Optional.of(TREE));
    }

    private static ResearchTechTreeDefinition tree() {
        List<ResearchTechTreeDefinition.TierDefinition> tiers = Arrays.stream(Tier.values())
                .map(tier -> new ResearchTechTreeDefinition.TierDefinition(
                        tier,
                        tier.name(),
                        Optional.empty()))
                .toList();
        ResearchTechTreeDefinition.LaneDefinition lane =
                new ResearchTechTreeDefinition.LaneDefinition(
                        LANE,
                        "General",
                        Optional.empty(),
                        Optional.empty(),
                        10);
        ResearchTechTreeDefinition.DomainDefinition domain =
                new ResearchTechTreeDefinition.DomainDefinition(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.empty(),
                        LANE,
                        Tier.STARTER,
                        List.of(lane));
        return new ResearchTechTreeDefinition(
                1,
                "Ranks",
                Optional.empty(),
                Optional.empty(),
                tiers,
                List.of(domain));
    }

    private static BlueprintResearchPolicyDefinition definition(
            BlueprintResearchSnapshot snapshot,
            ResourceLocation blueprintId) {
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                id("rank_test:root"), data(id("rank_test:root")),
                id("rank_test:middle"), data(id("rank_test:middle")),
                id("rank_test:top"), data(id("rank_test:top")));
        return BlueprintResearchPolicyResolver.definitionFor(
                snapshot,
                catalog,
                PROFILE,
                blueprintId);
    }

    private static BlueprintData data(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "name",
                "tooltip",
                id,
                null,
                "gun",
                id("rank_test:slot"),
                BlueprintKind.GUN);
    }

    private static BlueprintResearchTarget exactTarget(ResourceLocation id) {
        return new BlueprintResearchTarget(List.of(id), List.of(), Optional.empty());
    }

    private static String bundleJson(int format, String rankField) {
        return """
                {
                  "format": %d,
                  "tree": "rank_test:tree",
                  "entries": [{
                    "target": {"blueprints": ["rank_test:root"]},
                    "domain": "weapons",
                    "lane": "rank_test:weapons/general",
                    "tier": "starter",
                    %s
                    "order": 10
                  }]
                }
                """.formatted(format, rankField);
    }

    private static <T> T decode(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }

    private static <T> void assertDecodeFails(Codec<T> codec, String json) {
        try {
            assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
        } catch (IllegalArgumentException expected) {
            // Programmatic record construction enforces the same format-aware invariant.
        }
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
