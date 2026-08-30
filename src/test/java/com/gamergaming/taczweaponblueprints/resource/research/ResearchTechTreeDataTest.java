package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.WeaponRating;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponBranchAnalyzer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementDiagnostics;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.ReviewHandling;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlanner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScorer;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponStatEvidence;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponCandidatePositioner;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateClassifier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTechTreeDataTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TREE = id("test:progression");
    private static final ResourceLocation WEAPONS_LANE = id("test:weapons/general");

    @Test
    void strictCodecsDecodeACompleteMapAndOptionalProfileSelection() {
        ResearchTechTreeDefinition definition = decode(
                ResearchTechTreeDefinition.CODEC,
                validTreeJson().replace(
                        "\"order\": 10",
                        "\"order\": 10, \"translation_key\": \"tree.lane.general\""));
        BlueprintResearchProfile profile = decode(
                BlueprintResearchProfile.CODEC,
                validProfileJson(true));

        assertEquals(List.of(Tier.values()), definition.tiers().stream()
                .map(ResearchTechTreeDefinition.TierDefinition::tier)
                .toList());
        assertEquals(TREE, profile.techTree().orElseThrow());
        assertEquals("tree.lane.general", definition.domains().get(0).lanes().get(0)
                .translationKey().orElseThrow());

        BlueprintResearchProfile legacy = decode(
                BlueprintResearchProfile.CODEC,
                validProfileJson(false));
        assertTrue(legacy.techTree().isEmpty());
    }

    @Test
    void strictCodecsRejectUnknownFieldsAndInvalidMapShape() {
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                validTreeJson().replace("\"format\": 1", "\"format\": 1, \"unknown\": true"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                validTreeJson().replace("\"id\": \"apex\"", "\"id\": \"elite\""));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                validTreeJson().replace("\"fallback_lane\": \"test:weapons/general\"",
                        "\"fallback_lane\": \"test:missing\""));
        assertDecodeFails(
                ResearchTechTreeEntryBundle.CODEC,
                validBundleJson().replace("\"order\": 10", "\"order\": 10, \"unknown\": true"));
    }

    @Test
    void treeLayoutPolicySupportsCompatibleFixedAndBoundedDynamicWidths() {
        String formatTwo = validTreeJson().replace(
                "\"format\": 1,",
                "\"format\": 2, \"layout\": {\"max_nodes_per_layer\": 8},");
        ResearchTechTreeDefinition definition = decode(
                ResearchTechTreeDefinition.CODEC,
                formatTwo);

        assertEquals(2, definition.format());
        assertEquals(ResearchTechTreeDefinition.WidthMode.FIXED,
                definition.layout().widthMode());
        assertEquals(8, definition.layout().minNodesPerLayer());
        assertEquals(8, definition.layout().maxNodesPerLayer());
        assertEquals(20, decode(
                ResearchTechTreeDefinition.CODEC,
                formatTwo.replace("max_nodes_per_layer\": 8", "max_nodes_per_layer\": 20"))
                .layout().maxNodesPerLayer());
        assertEquals(28, decode(
                ResearchTechTreeDefinition.CODEC,
                formatTwo.replace("max_nodes_per_layer\": 8", "max_nodes_per_layer\": 28"))
                .layout().maxNodesPerLayer());
        ResearchTechTreeDefinition dynamic = decode(
                ResearchTechTreeDefinition.CODEC,
                formatTwo.replace(
                        "max_nodes_per_layer\": 8",
                        "max_nodes_per_layer\": 20, \"width_mode\": \"dynamic\", "
                                + "\"min_nodes_per_layer\": 9"));
        assertEquals(ResearchTechTreeDefinition.WidthMode.DYNAMIC,
                dynamic.layout().widthMode());
        assertEquals(9, dynamic.layout().minNodesPerLayer());
        assertEquals(20, dynamic.layout().maxNodesPerLayer());
        assertEquals(9, decode(ResearchTechTreeDefinition.CODEC, validTreeJson())
                .layout().maxNodesPerLayer());
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                validTreeJson().replace("\"format\": 1", "\"format\": 2"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                validTreeJson().replace(
                        "\"format\": 1,",
                        "\"format\": 1, \"layout\": {\"max_nodes_per_layer\": 9},"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwo.replace("max_nodes_per_layer\": 8", "max_nodes_per_layer\": 7"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwo.replace("max_nodes_per_layer\": 8", "max_nodes_per_layer\": 29"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwo.replace(
                        "max_nodes_per_layer\": 8",
                        "max_nodes_per_layer\": 10, \"width_mode\": \"dynamic\", "
                                + "\"min_nodes_per_layer\": 11"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwo.replace(
                        "max_nodes_per_layer\": 8",
                        "max_nodes_per_layer\": 10, \"min_nodes_per_layer\": 9"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwo.replace(
                        "max_nodes_per_layer\": 8",
                        "max_nodes_per_layer\": 8, \"unknown\": true"));
    }

    @Test
    void formatTwoBandPoliciesAreOptionalDynamicOrConfiguredPresentationOnly() {
        ResearchTechTreeDefinition none = decode(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson(""));
        assertEquals(ResearchTechTreeDefinition.BandMode.NONE,
                none.bandPolicy().mode());
        assertTrue(none.tiers().isEmpty());

        ResearchTechTreeDefinition dynamic = decode(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson("""
                        ,"bands":{"mode":"dynamic","ranks_per_band":4}
                        """));
        assertEquals(ResearchTechTreeDefinition.BandMode.DYNAMIC,
                dynamic.bandPolicy().mode());
        assertEquals(4, dynamic.bandPolicy().ranksPerBand());

        ResearchTechTreeDefinition configured = decode(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson("""
                        ,"bands":{
                          "mode":"configured",
                          "basis":"rank",
                          "definitions":[
                            {"id":"test:field","title":"Field","maximum":3,
                             "color":3368601,"icon":"test:field_icon"},
                            {"id":"test:specialized","title":"Specialized",
                             "translation_key":"tree.band.specialized"}
                          ]
                        }
                        """));
        assertEquals(ResearchTechTreeDefinition.BandMode.CONFIGURED,
                configured.bandPolicy().mode());
        assertEquals(ResearchTechTreeDefinition.BandBasis.RANK,
                configured.bandPolicy().basis());
        assertEquals(Optional.of(0x336699),
                configured.bandPolicy().definitions().get(0).color());
        assertEquals(Optional.of(id("test:field_icon")),
                configured.bandPolicy().definitions().get(0).icon());

        ResearchTechTreeDefinition scoreConfigured = decode(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson("""
                        ,"bands":{
                          "mode":"configured",
                          "basis":"score",
                          "definitions":[
                            {"id":"test:early","title":"Early","maximum":49},
                            {"id":"test:late","title":"Late"}
                          ]
                        }
                        """));
        assertEquals(ResearchTechTreeDefinition.BandBasis.SCORE,
                scoreConfigured.bandPolicy().basis());

        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                validTreeJson().replace(
                        "\"format\": 1,",
                        "\"format\": 1, \"bands\": {\"mode\":\"none\"},"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson(
                        ",\"bands\":{\"mode\":\"legacy\"}"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson(
                        ",\"bands\":{\"mode\":\"dynamic\",\"ranks_per_band\":0}"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson(
                        ",\"bands\":{\"mode\":\"none\",\"ranks_per_band\":4}"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson(
                        ",\"bands\":{\"mode\":\"configured\",\"definitions\":[]}"));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson("""
                        ,"bands":{"mode":"configured","definitions":[
                          {"id":"test:a","title":"A","maximum":5},
                          {"id":"test:b","title":"B","maximum":6}
                        ]}
                        """));
        assertDecodeFails(
                ResearchTechTreeDefinition.CODEC,
                formatTwoTreeJson("""
                        ,"bands":{"mode":"configured","basis":"score","definitions":[
                          {"id":"test:a","title":"A","maximum":101},
                          {"id":"test:b","title":"B"}
                        ]}
                        """));
    }

    @Test
    void automaticPlacementProfilesAreStrictBoundedAndUniquePerTree() {
        ResearchAutomaticPlacementProfile autoProfile = decode(
                ResearchAutomaticPlacementProfile.CODEC,
                """
                        {
                          "format": 1,
                          "tree": "test:progression",
                          "mode": "distributed"
                        }
                        """);
        assertEquals(AutomaticPlacementMode.DISTRIBUTED, autoProfile.mode());
        assertEquals(3, autoProfile.levelsPerTier());
        assertEquals(60, autoProfile.reviewConfidenceThreshold());
        assertEquals(ReviewHandling.EXCLUDE, autoProfile.reviewHandling());
        assertEquals(2, autoProfile.maxGeneratedPrerequisites());
        assertEquals(4, autoProfile.mergeInterval());
        assertEquals(2, autoProfile.foundationCount());

        ResearchAutomaticPlacementProfile connectedReviews = decode(
                ResearchAutomaticPlacementProfile.CODEC,
                """
                        {
                          "format": 1,
                          "tree": "test:progression",
                          "mode": "connected",
                          "levels_per_tier": 4,
                          "review_handling": "place_connected",
                          "max_prerequisites": 3,
                          "merge_interval": 6
                        }
                        """);
        assertEquals(ReviewHandling.PLACE_CONNECTED, connectedReviews.reviewHandling());
        assertEquals(4, connectedReviews.placementPolicy().levelsPerTier());
        assertEquals(3, connectedReviews.placementPolicy().maxGeneratedPrerequisites());
        assertEquals(6, connectedReviews.placementPolicy().mergeInterval());

        ResearchAutomaticPlacementProfile dynamic = decode(
                ResearchAutomaticPlacementProfile.CODEC,
                """
                        {
                          "format": 2,
                          "tree": "test:progression",
                          "mode": "connected",
                          "review_handling": "place_connected",
                          "foundation_count": 3,
                          "max_nodes_per_rank": 9,
                          "bands": [
                            {"id":"test:early","maximum_score":39,"title":"Early"},
                            {"id":"test:late","maximum_score":100,"title":"Late",
                             "translation_key":"tree.band.late"}
                          ]
                        }
                        """);
        assertTrue(dynamic.placementPolicy().usesDynamicLayers());
        assertEquals(9, dynamic.maxNodesPerRank());
        assertEquals(3, dynamic.foundationCount());
        assertEquals(List.of(id("test:early"), id("test:late")),
                dynamic.progressionBands().stream().map(value -> value.id()).toList());

        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":1,\"tree\":\"test:progression\",\"mode\":\"distributed\",\"extra\":true}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":1,\"tree\":\"test:progression\",\"mode\":\"unsafe\"}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":1,\"tree\":\"test:progression\",\"mode\":\"distributed\",\"levels_per_tier\":0}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":1,\"tree\":\"test:progression\",\"mode\":\"connected\",\"review_handling\":\"unsafe\"}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":1,\"tree\":\"test:progression\",\"mode\":\"connected\",\"max_prerequisites\":0}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":1,\"tree\":\"test:progression\",\"mode\":\"connected\",\"merge_interval\":65}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":1,\"tree\":\"test:progression\",\"mode\":\"connected\",\"max_nodes_per_rank\":8}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":1,\"tree\":\"test:progression\",\"mode\":\"connected\",\"foundation_count\":1}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":2,\"tree\":\"test:progression\",\"mode\":\"connected\",\"foundation_count\":0}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":2,\"tree\":\"test:progression\",\"mode\":\"connected\",\"foundation_count\":4}");
        assertDecodeFails(
                ResearchAutomaticPlacementProfile.CODEC,
                "{\"format\":2,\"tree\":\"test:progression\",\"mode\":\"connected\",\"bands\":[{\"id\":\"test:partial\",\"maximum_score\":99,\"title\":\"Partial\"}]}");

        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(TREE)),
                Map.of(),
                Map.of(),
                Map.of(TREE, tree()),
                Map.of(),
                Map.of(id("test:auto"), autoProfile));
        assertEquals(autoProfile, snapshot.automaticPlacementProfileForTree(TREE).orElseThrow());

        assertThrows(IllegalArgumentException.class, () -> BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(TREE)),
                Map.of(),
                Map.of(),
                Map.of(TREE, tree()),
                Map.of(),
                Map.of(id("test:auto_a"), autoProfile, id("test:auto_b"), autoProfile)));
        assertThrows(IllegalArgumentException.class, () -> BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(TREE)),
                Map.of(),
                Map.of(),
                Map.of(TREE, tree()),
                Map.of(),
                Map.of(id("test:auto"), new ResearchAutomaticPlacementProfile(
                        1, id("test:missing"), AutomaticPlacementMode.INDEPENDENT, 3, 60))));
    }

    @Test
    void weaponRatingsAreAuditableAndCannotLeakIntoBroadOrNonWeaponPlacements() {
        ResearchTechTreeEntryBundle rated = decode(
                ResearchTechTreeEntryBundle.CODEC,
                validBundleJson());
        assertEquals(Tier.STARTER, rated.entries().get(0).rating().orElseThrow().suggestedTier());
        assertEquals(0, rated.entries().get(0).level());
        ResearchTechTreeEntryBundle leveled = decode(
                ResearchTechTreeEntryBundle.CODEC,
                validBundleJson().replace("\"order\": 10", "\"level\": 2, \"order\": 10"));
        assertEquals(2, leveled.entries().get(0).level());
        assertDecodeFails(
                ResearchTechTreeEntryBundle.CODEC,
                validBundleJson().replace("\"order\": 10", "\"level\": 5, \"order\": 10"));

        assertDecodeFails(
                ResearchTechTreeEntryBundle.CODEC,
                validBundleJson().replace("\"tier\": \"starter\"", "\"tier\": \"apex\""));
        ResearchTechTreeEntryBundle overridden = decode(
                ResearchTechTreeEntryBundle.CODEC,
                validBundleJson().replace(
                        "\"tier\": \"starter\"",
                        "\"tier\": \"apex\", \"tier_override_reason\": \"Iconic player favorite\""));
        assertEquals(Tier.APEX, overridden.entries().get(0).tier());

        assertDecodeFails(
                ResearchTechTreeEntryBundle.CODEC,
                validBundleJson()
                        .replace("\"domain\": \"weapons\"", "\"domain\": \"ammo\"")
                        .replace("\"lane\": \"test:weapons/general\"", "\"lane\": \"test:ammo/general\""));
        assertDecodeFails(
                ResearchTechTreeEntryBundle.CODEC,
                validBundleJson().replace(
                        "\"blueprints\": [\"test:starter\"]",
                        "\"selector\": {\"blueprint_kinds\": [\"gun\"]}"));
    }

    @Test
    void snapshotRejectsMissingProfileTreeBundleTagAndLaneReferences() {
        assertThrows(IllegalArgumentException.class, () -> BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(id("test:missing"))),
                Map.of(),
                Map.of(),
                Map.of(TREE, tree()),
                Map.of()));

        ResearchTechTreeEntryBundle missingTree = bundle(
                id("test:missing"),
                0,
                entry(exactTarget("test:a"), WEAPONS_LANE, Tier.STARTER, 10));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of(), Map.of(), missingTree));

        ResearchTechTreeEntryBundle missingTag = bundle(
                TREE,
                0,
                entry(new BlueprintResearchTarget(
                        List.of(), List.of(id("test:missing_tag")), Optional.empty()),
                        WEAPONS_LANE,
                        Tier.STARTER,
                        10));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of(), Map.of(), missingTag));

        ResearchTechTreeEntryBundle missingLane = bundle(
                TREE,
                0,
                entry(exactTarget("test:a"), id("test:missing_lane"), Tier.STARTER, 10));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of(), Map.of(), missingLane));
    }

    @Test
    void placementResolutionUsesSpecificityThenPriorityThenStableSourceId() {
        ResourceLocation weapon = id("test:weapon");
        ResourceLocation tagId = id("test:tag");
        ResearchTechTreeEntryBundle selector = bundle(
                TREE,
                100,
                entry(selectorTarget(), WEAPONS_LANE, Tier.APEX, 90));
        ResearchTechTreeEntryBundle tag = bundle(
                TREE,
                50,
                entry(new BlueprintResearchTarget(
                        List.of(), List.of(tagId), Optional.empty()),
                        WEAPONS_LANE,
                        Tier.ADVANCED,
                        50));
        ResearchTechTreeEntryBundle exactA = bundle(
                TREE,
                10,
                entry(exactTarget("test:weapon"), WEAPONS_LANE, Tier.BASIC, 20));
        ResearchTechTreeEntryBundle exactB = bundle(
                TREE,
                10,
                entry(exactTarget("test:weapon"), WEAPONS_LANE, Tier.ESTABLISHED, 30));
        BlueprintResearchSnapshot tagSnapshot = BlueprintResearchSnapshot.create(
                Map.of(tagId, new BlueprintLootTag(1, List.of(weapon))),
                Map.of(PROFILE, profile(TREE)),
                Map.of(),
                Map.of(),
                Map.of(TREE, tree()),
                Map.of(id("test:z_selector"), selector, id("test:m_tag"), tag));
        assertEquals(
                Tier.ADVANCED,
                ResearchTechTreePlacementResolver.resolve(
                        tagSnapshot,
                        TREE,
                        weapon,
                        data(weapon, BlueprintKind.GUN))
                        .placement().orElseThrow().tier());
        assertEquals(
                PlacementOrigin.TAG,
                ResearchTechTreePlacementResolver.resolve(
                                tagSnapshot,
                                TREE,
                                weapon,
                                data(weapon, BlueprintKind.GUN))
                        .placement().orElseThrow().origin());

        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(tagId, new BlueprintLootTag(1, List.of(weapon))),
                Map.of(PROFILE, profile(TREE)),
                Map.of(),
                Map.of(),
                Map.of(TREE, tree()),
                Map.of(
                        id("test:z_selector"), selector,
                        id("test:m_tag"), tag,
                        id("test:a_exact"), exactA,
                        id("test:b_exact"), exactB));

        ResearchTechTreePlacementResolver.Selection selection =
                ResearchTechTreePlacementResolver.resolve(
                        snapshot,
                        TREE,
                        weapon,
                        data(weapon, BlueprintKind.GUN));
        assertEquals(Tier.BASIC, selection.placement().orElseThrow().tier());
        assertEquals(PlacementOrigin.EXACT, selection.placement().orElseThrow().origin());
        assertEquals(id("test:a_exact"), selection.placement().orElseThrow().source().bundleId());
        assertTrue(selection.hasCompetition());
        assertEquals(2, selection.competingSources().size());

        assertThrows(IllegalArgumentException.class, () -> ResearchTechTreePlacementResolver.resolve(
                snapshot,
                TREE,
                weapon,
                data(weapon, BlueprintKind.AMMO)));
    }

    @Test
    void fallbackMarkersAreExplicitBoundedAndWeakerThanAuthoredSelectors() {
        ResourceLocation weapon = id("test:addon_weapon");
        ResearchTechTreeEntryBundle authoredSelector = bundle(
                TREE,
                0,
                entry(selectorTarget(), WEAPONS_LANE, Tier.ADVANCED, 10));
        BlueprintResearchSnapshot authoredSnapshot = snapshot(
                Map.of(), Map.of(), authoredSelector);
        assertEquals(
                PlacementOrigin.SELECTOR,
                ResearchTechTreePlacementResolver.resolve(
                                authoredSnapshot,
                                TREE,
                                weapon,
                                data(weapon, BlueprintKind.GUN))
                        .placement().orElseThrow().origin());

        ResearchTechTreeEntryBundle.Entry fallbackEntry = new ResearchTechTreeEntryBundle.Entry(
                selectorTarget(),
                Domain.WEAPONS,
                WEAPONS_LANE,
                Tier.BASIC,
                900_000,
                Optional.empty(),
                Optional.empty(),
                true);
        ResearchTechTreeEntryBundle fallback = bundle(TREE, 0, fallbackEntry);
        BlueprintResearchSnapshot fallbackSnapshot = snapshot(Map.of(), Map.of(), fallback);
        ResearchTechTreePlacementResolver.Placement placement =
                ResearchTechTreePlacementResolver.resolve(
                                fallbackSnapshot,
                                TREE,
                                weapon,
                                data(weapon, BlueprintKind.GUN))
                        .placement().orElseThrow();
        assertEquals(PlacementOrigin.LEGACY_FALLBACK, placement.origin());
        assertEquals(Tier.BASIC, placement.tier());
        assertEquals(900_000, placement.order());

        BlueprintResearchSnapshot combined = snapshot(
                Map.of(), Map.of(), fallback, authoredSelector);
        ResearchTechTreePlacementResolver.Selection combinedSelection =
                ResearchTechTreePlacementResolver.resolve(
                        combined,
                        TREE,
                        weapon,
                        data(weapon, BlueprintKind.GUN));
        assertEquals(
                PlacementOrigin.SELECTOR,
                combinedSelection.placement().orElseThrow().origin());
        assertEquals(Tier.ADVANCED, combinedSelection.placement().orElseThrow().tier());
        assertFalse(combinedSelection.hasCompetition());

        assertThrows(IllegalArgumentException.class,
                () -> bundle(TREE, 1, fallbackEntry));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTechTreeEntryBundle.Entry(
                        exactTarget("test:addon_weapon"),
                        Domain.WEAPONS,
                        WEAPONS_LANE,
                        Tier.BASIC,
                        10,
                        Optional.empty(),
                        Optional.empty(),
                        true));
    }

    @Test
    void automaticEligibilityCannotReplaceAuthoredPlacement() {
        ResourceLocation authoredId = id("test:authored");
        ResourceLocation addOnId = id("test:addon_weapon");
        ResearchTechTreeEntryBundle authored = bundle(
                TREE,
                10,
                entry(exactTarget(authoredId.toString()), WEAPONS_LANE, Tier.STARTER, 10));
        ResearchTechTreeEntryBundle.Entry fallbackEntry = new ResearchTechTreeEntryBundle.Entry(
                selectorTarget(),
                Domain.WEAPONS,
                WEAPONS_LANE,
                Tier.BASIC,
                900_000,
                Optional.empty(),
                Optional.empty(),
                true);
        ResearchTechTreeEntryBundle fallback = bundle(TREE, 0, fallbackEntry);
        ResearchAutomaticPlacementProfile autoProfile = new ResearchAutomaticPlacementProfile(
                1, TREE, AutomaticPlacementMode.DISTRIBUTED, 4, 0);
        BlueprintResearchSnapshot research = snapshotWithAutomaticProfile(
                autoProfile, authored, fallback);
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                authoredId, data(authoredId, BlueprintKind.GUN),
                addOnId, data(addOnId, BlueprintKind.GUN));
        AutomaticWeaponEvidenceSnapshot evidence = evidence(addOnId.toString(), autoProfile);

        var candidates = positionedCandidates(
                research, 7L, catalog, 11L, evidence, autoProfile);
        assertEquals(Set.of(authoredId.toString()), candidates.authoredBlueprintIds());
        assertEquals(
                Set.of(addOnId.toString()),
                candidates.eligibleProposals().keySet(),
                candidates.excludedAutomaticCandidates().toString());
        assertEquals(1, candidates.automaticCandidateCount());

        var effective = ResearchTechTreePlacementResolver.resolveWithAutomatic(
                research, TREE, addOnId, catalog.get(addOnId), candidates);
        assertEquals(PlacementOrigin.LEGACY_FALLBACK,
                effective.base().placement().orElseThrow().origin());
        assertEquals(PlacementOrigin.AUTOMATIC, effective.effectiveOrigin().orElseThrow());
        assertEquals(4, effective.automaticProposal().orElseThrow().levelsPerTier());

        ResearchAutomaticPlacementProfile independent = new ResearchAutomaticPlacementProfile(
                1, TREE, AutomaticPlacementMode.INDEPENDENT, 4, 0);
        BlueprintResearchSnapshot independentResearch = snapshotWithAutomaticProfile(
                independent, authored, fallback);
        var independentCandidates = positionedCandidates(
                independentResearch, 8L, catalog, 11L, evidence, independent);
        assertTrue(independentCandidates.eligibleProposals().isEmpty());
        assertEquals("mode_independent",
                independentCandidates.excludedAutomaticCandidates().get(addOnId.toString()));
        assertEquals(PlacementOrigin.LEGACY_FALLBACK,
                ResearchTechTreePlacementResolver.resolveWithAutomatic(
                        independentResearch,
                        TREE,
                        addOnId,
                        catalog.get(addOnId),
                        independentCandidates)
                        .effectiveOrigin().orElseThrow());
    }

    @Test
    void scoredGunWithoutAnyEntryBecomesAnAutomaticCandidate() {
        ResourceLocation addOnId = id("orphan:scored_weapon");
        ResourceLocation ammoId = id("orphan:ammo");
        ResearchAutomaticPlacementProfile profile = new ResearchAutomaticPlacementProfile(
                1, TREE, AutomaticPlacementMode.DISTRIBUTED, 4, 0);
        BlueprintResearchSnapshot research = snapshotWithAutomaticProfile(profile);
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                addOnId, data(addOnId, BlueprintKind.GUN),
                ammoId, data(ammoId, BlueprintKind.AMMO));

        var candidates = positionedCandidates(
                research,
                7L,
                catalog,
                11L,
                evidence(addOnId.toString(), profile),
                profile);

        assertEquals(1, candidates.catalogWeaponCount());
        assertEquals(Set.of(addOnId.toString()), candidates.eligibleProposals().keySet());
        assertTrue(candidates.authoredBlueprintIds().isEmpty());
        assertTrue(candidates.unplacedBlueprintIds().isEmpty());
        assertFalse(candidates.eligibleProposals().containsKey(ammoId.toString()));

        var effective = ResearchTechTreePlacementResolver.resolveWithAutomatic(
                research, TREE, addOnId, catalog.get(addOnId), candidates);
        assertTrue(effective.base().placement().isEmpty());
        assertTrue(effective.automaticProposal().isPresent());
        assertEquals(PlacementOrigin.AUTOMATIC, effective.effectiveOrigin().orElseThrow());
    }

    @Test
    void formatTwoTreeOwnsAutomaticLayerCapacity() {
        ResourceLocation addOnId = id("addon:tree_owned_width");
        ResearchAutomaticPlacementProfile automatic = new ResearchAutomaticPlacementProfile(
                2,
                TREE,
                AutomaticPlacementMode.DISTRIBUTED,
                3,
                0,
                ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                10,
                List.of());
        BlueprintResearchSnapshot research = snapshotWithAutomaticProfile(
                automatic,
                treeWithCapacity(8));
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                addOnId, data(addOnId, BlueprintKind.GUN));

        var candidates = positionedCandidates(
                research,
                7L,
                catalog,
                11L,
                evidence(addOnId.toString(), automatic),
                automatic);

        assertEquals(8, candidates.policy().maxNodesPerRank());
        var prerequisitePlan = new AutomaticWeaponPrerequisitePlanner().plan(
                research, catalog, PROFILE, candidates);
        String exported = BlueprintResearchCatalogExporter.export(
                research,
                catalog,
                PROFILE,
                AutomaticWeaponPlacementDiagnostics.create(
                        PROFILE, candidates, prerequisitePlan));
        var automaticExport = JsonParser.parseString(exported).getAsJsonObject()
                .getAsJsonObject("automatic_placement");
        var presentationExport = JsonParser.parseString(exported).getAsJsonObject()
                .getAsJsonObject("tech_tree_presentation");
        assertEquals(12, JsonParser.parseString(exported).getAsJsonObject()
                .get("format").getAsInt());
        assertEquals(TREE.toString(), presentationExport.get("tree").getAsString());
        assertEquals("none", presentationExport.get("band_mode").getAsString());
        assertEquals("fixed", presentationExport.get("width_mode").getAsString());
        assertEquals(8, presentationExport.get("min_nodes_per_layer").getAsInt());
        assertEquals(8, presentationExport.get("max_nodes_per_layer").getAsInt());
        assertEquals(2, automaticExport.get("foundation_count").getAsInt());
        assertEquals(1, automaticExport.get("topology_weapon_count").getAsInt());
        assertEquals(8, automaticExport.get("resolved_nodes_per_layer").getAsInt());
        assertEquals(8, automaticExport.get("max_nodes_per_layer").getAsInt());
        assertEquals("tree_layout",
                automaticExport.get("layer_capacity_source").getAsString());
        assertEquals("fixed", automaticExport.get("width_mode").getAsString());
        assertEquals(8,
                automaticExport.get("configured_min_nodes_per_layer").getAsInt());
        assertEquals(8,
                automaticExport.get("configured_max_nodes_per_layer").getAsInt());
        assertFalse(automaticExport.has("max_nodes_per_rank"));
    }

    @Test
    void dynamicTreeWidthUsesTheEligibleTopologyPopulation() {
        ResearchAutomaticPlacementProfile automatic = new ResearchAutomaticPlacementProfile(
                2,
                TREE,
                AutomaticPlacementMode.DISTRIBUTED,
                3,
                0,
                ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                10,
                List.of());
        BlueprintResearchSnapshot research = snapshotWithAutomaticProfile(
                automatic,
                treeWithDynamicCapacity(9, 20),
                bundle(TREE, 1, entry(
                        exactTarget("tacz:dynamic_authored"),
                        WEAPONS_LANE,
                        Tier.STARTER,
                        0)));
        Map<ResourceLocation, BlueprintData> catalog = new java.util.LinkedHashMap<>();
        ResourceLocation authored = id("tacz:dynamic_authored");
        catalog.put(authored, data(authored, BlueprintKind.GUN));
        for (int index = 0; index < 144; index++) {
            ResourceLocation id = id("addon:dynamic_" + index);
            catalog.put(id, data(id, BlueprintKind.GUN));
        }

        var classification = AutomaticWeaponPlacementCandidateClassifier.classify(
                research,
                7L,
                catalog,
                11L,
                AutomaticWeaponEvidenceSnapshot.emptyForCatalog(11L),
                automatic);
        assertEquals(10, classification.basePolicy().maxNodesPerRank());
        assertEquals(classification.eligibleProposals().keySet(),
                classification.roleSignatures().keySet());
        assertTrue(classification.roleSignatures().values().stream()
                .noneMatch(value -> value.maySeedBranch()));
        assertEquals(0, classification.branchModel().seedSignatureCount());
        assertEquals(5, classification.branchModel().branches().size());
        assertTrue(classification.branchModel().branches().stream()
                .allMatch(branch -> branch.medoidBlueprintId().isEmpty()
                        && branch.terminalBlueprintIds().isEmpty()));
        var candidates = AutomaticWeaponCandidatePositioner.position(
                classification, research.techTrees().get(TREE));

        assertEquals(144, candidates.eligibleProposals().size());
        assertEquals(Set.of(authored.toString()), candidates.authoredBlueprintIds());
        assertEquals(14, candidates.policy().maxNodesPerRank());
        assertTrue(candidates.eligibleProposals().values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.stream.Collectors.counting()))
                .values().stream()
                .allMatch(count -> count <= 14));
    }

    @Test
    void largeScoredMixedCatalogUsesTheCompleteLiveClassificationPath() {
        ResearchAutomaticPlacementProfile automatic = new ResearchAutomaticPlacementProfile(
                2,
                TREE,
                AutomaticPlacementMode.DISTRIBUTED,
                3,
                0,
                ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                10,
                List.of());
        List<ResourceLocation> ids = java.util.stream.IntStream.range(0, 287)
                .mapToObj(index -> id("large_live:weapon_" + index))
                .toList();
        ResearchTechTreeEntryBundle.Entry[] authoredEntries = ids.stream().limit(53)
                .map(value -> entry(
                        exactTarget(value.toString()),
                        WEAPONS_LANE,
                        Tier.STARTER,
                        0))
                .toArray(ResearchTechTreeEntryBundle.Entry[]::new);
        BlueprintResearchSnapshot research = snapshotWithAutomaticProfile(
                automatic,
                treeWithDynamicCapacity(9, 20),
                bundle(TREE, 1, authoredEntries));
        Map<ResourceLocation, BlueprintData> catalog = new java.util.LinkedHashMap<>();
        ids.forEach(value -> catalog.put(value, data(value, BlueprintKind.GUN)));

        var classification = AutomaticWeaponPlacementCandidateClassifier.classify(
                research,
                7L,
                catalog,
                11L,
                evidence(ids.stream().map(ResourceLocation::toString).toList(), automatic),
                automatic);
        var positioned = AutomaticWeaponCandidatePositioner.position(
                classification, research.techTrees().get(TREE));

        assertEquals(234, classification.eligibleProposals().size());
        assertEquals(53, classification.authoredBlueprintIds().size());
        assertEquals(53, classification.authoredRoleSignatures().size());
        assertTrue(classification.authoredRoleSignatures().values().stream()
                .allMatch(value -> value.scoredEvidence() && value.maySeedBranch()));
        assertEquals(10, classification.branchModel().branchLimit());
        assertEquals(7, classification.branchModel().branchCapacity());
        assertTrue(classification.branchModel().matches(
                classification.roleSignatures(), classification.authoredRoleSignatures()));
        assertTrue(classification.branchModel().branches().stream()
                .allMatch(branch -> branch.terminalBlueprintIds().size()
                                <= AutomaticWeaponBranchAnalyzer.MAX_TERMINAL_PEERS
                        && branch.layoutStrandCount()
                                <= AutomaticWeaponBranchAnalyzer
                                        .MAX_LAYOUT_STRANDS_PER_BRANCH));
        assertEquals(20, positioned.policy().maxNodesPerRank());
    }

    @Test
    void nonAuthoredReferenceWeaponRemainsEligibleForAnAutomaticOnlyTree() {
        ResourceLocation referenceId = id("tacz:glock_17");
        ResearchAutomaticPlacementProfile profile = new ResearchAutomaticPlacementProfile(
                1, TREE, AutomaticPlacementMode.DISTRIBUTED, 4, 0);
        BlueprintResearchSnapshot research = snapshotWithAutomaticProfile(profile);
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                referenceId, data(referenceId, BlueprintKind.GUN));
        AutomaticWeaponEvidenceSnapshot scored = evidence(referenceId.toString(), profile);
        AutomaticWeaponEvidenceSnapshot referenceEvidence = new AutomaticWeaponEvidenceSnapshot(
                scored.catalogRevision(),
                scored.referenceVersion(),
                scored.sourceVersion(),
                1,
                scored.referenceWeaponCount(),
                1,
                Set.of(referenceId.toString()),
                scored.evidenceByBlueprint(),
                scored.scoresByBlueprint(),
                Map.of(),
                AutomaticWeaponPlacementPlan.EMPTY);

        var candidates = positionedCandidates(
                research,
                7L,
                catalog,
                11L,
                referenceEvidence,
                profile);

        assertEquals(Set.of(referenceId.toString()), candidates.eligibleProposals().keySet());
        assertTrue(candidates.excludedAutomaticCandidates().isEmpty());
        assertTrue(candidates.authoredBlueprintIds().isEmpty());
    }

    @Test
    void explicitReviewPolicyPublishesReviewedAndUnscoreableFallbackWeapons() {
        ResourceLocation reviewedId = id("addon:scripted_pistol");
        ResourceLocation unscoreableId = id("addon:missing_rifle");
        ResearchTechTreeEntryBundle fallback = bundle(
                TREE,
                0,
                new ResearchTechTreeEntryBundle.Entry(
                        new BlueprintResearchTarget(
                                List.of(),
                                List.of(),
                                Optional.of(new BlueprintCatalogSelector(
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(),
                                        List.of(BlueprintKind.GUN),
                                        1.0F))),
                        Domain.WEAPONS,
                        WEAPONS_LANE,
                        Tier.BASIC,
                        900_000,
                        Optional.empty(),
                        Optional.empty(),
                        true));
        ResearchAutomaticPlacementProfile profile = new ResearchAutomaticPlacementProfile(
                1,
                TREE,
                AutomaticPlacementMode.CONNECTED,
                3,
                0,
                ReviewHandling.PLACE_CONNECTED);
        BlueprintResearchSnapshot research = snapshotWithAutomaticProfile(profile, fallback);
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                reviewedId, data(reviewedId, BlueprintKind.GUN),
                unscoreableId, new BlueprintData(
                        unscoreableId.toString(),
                        "name.missing_rifle",
                        "tooltip",
                        id("addon:recipe/missing_rifle"),
                        null,
                        "rifle",
                        id("addon:slot/missing_rifle"),
                        BlueprintKind.GUN));
        AutomaticWeaponEvidenceSnapshot scored = evidence(
                reviewedId.toString(), profile, true);
        AutomaticWeaponEvidenceSnapshot evidence = new AutomaticWeaponEvidenceSnapshot(
                11L,
                scored.referenceVersion(),
                scored.sourceVersion(),
                2,
                scored.referenceWeaponCount(),
                0,
                Set.of(),
                scored.evidenceByBlueprint(),
                scored.scoresByBlueprint(),
                Map.of(unscoreableId.toString(), "missing_tacz_gun_index"),
                scored.placementPlan());

        var candidates = positionedCandidates(
                research, 7L, catalog, 11L, evidence, profile);

        assertEquals(Set.of(reviewedId.toString(), unscoreableId.toString()),
                candidates.eligibleProposals().keySet());
        assertTrue(candidates.excludedAutomaticCandidates().isEmpty());
        assertTrue(candidates.eligibleProposals().get(reviewedId.toString())
                .reviewReasons().contains("script_controlled"));
        var fallbackProposal = candidates.eligibleProposals().get(unscoreableId.toString());
        assertTrue(fallbackProposal.reviewReasons().contains("unscored_fallback"));
        assertTrue(fallbackProposal.reviewReasons().stream().anyMatch(
                reason -> reason.startsWith("evidence_unavailable:evidence_rejected:")));
        assertTrue(fallbackProposal.position().level() >= 0
                && fallbackProposal.position().level() < 3);

        ResearchAutomaticPlacementProfile safeDefault = new ResearchAutomaticPlacementProfile(
                1, TREE, AutomaticPlacementMode.CONNECTED, 3, 0);
        BlueprintResearchSnapshot safeResearch = snapshotWithAutomaticProfile(
                safeDefault, fallback);
        var excluded = positionedCandidates(
                safeResearch, 8L, catalog, 11L, evidence, safeDefault);
        assertTrue(excluded.eligibleProposals().isEmpty());
        assertTrue(excluded.excludedAutomaticCandidates().get(reviewedId.toString())
                .startsWith("review_required:"));
        assertTrue(excluded.excludedAutomaticCandidates().get(unscoreableId.toString())
                .startsWith("evidence_rejected:"));
    }

    @Test
    void authoritativePrerequisitesMustMoveForwardThroughTierAndOrder() {
        BlueprintResearchRule rootRule = rule("test:root", List.of());
        BlueprintResearchRule dependentRule = rule("test:dependent", List.of(id("test:root")));
        Map<ResourceLocation, BlueprintResearchRule> rules = Map.of(
                id("test:root_rule"), rootRule,
                id("test:dependent_rule"), dependentRule);

        ResearchTechTreeEntryBundle valid = bundle(
                TREE,
                0,
                entry(exactTarget("test:root"), WEAPONS_LANE, Tier.STARTER, 10),
                entry(exactTarget("test:dependent"), WEAPONS_LANE, Tier.STARTER, 20));
        assertEquals(2, snapshot(Map.of(), rules, valid).techTreeEntriesFor(TREE).size());

        ResearchTechTreeEntryBundle reversedOrder = bundle(
                TREE,
                0,
                entry(exactTarget("test:root"), WEAPONS_LANE, Tier.STARTER, 20),
                entry(exactTarget("test:dependent"), WEAPONS_LANE, Tier.STARTER, 10));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of(), rules, reversedOrder));

        ResearchTechTreeEntryBundle reversedTier = bundle(
                TREE,
                0,
                entry(exactTarget("test:root"), WEAPONS_LANE, Tier.BASIC, 10),
                entry(exactTarget("test:dependent"), WEAPONS_LANE, Tier.STARTER, 20));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of(), rules, reversedTier));

        ResearchTechTreeEntryBundle forwardLevel = bundle(
                TREE,
                0,
                entry(exactTarget("test:root"), WEAPONS_LANE, Tier.STARTER, 1, 50),
                entry(exactTarget("test:dependent"), WEAPONS_LANE, Tier.STARTER, 2, 10));
        assertEquals(2, snapshot(Map.of(), rules, forwardLevel).techTreeEntriesFor(TREE).size());

        ResearchTechTreeEntryBundle reversedLevel = bundle(
                TREE,
                0,
                entry(exactTarget("test:root"), WEAPONS_LANE, Tier.STARTER, 2, 10),
                entry(exactTarget("test:dependent"), WEAPONS_LANE, Tier.STARTER, 1, 50));
        assertThrows(IllegalArgumentException.class, () -> snapshot(Map.of(), rules, reversedLevel));
    }

    @Test
    void expandedTagIndexesAreBoundedBeforeCompilation() {
        ResourceLocation tagId = id("test:large_tag");
        List<ResourceLocation> values = new ArrayList<>();
        for (int index = 0; index < BlueprintLootTag.MAX_VALUES; index++) {
            values.add(id("test:weapon_" + index));
        }
        List<ResearchTechTreeEntryBundle.Entry> entries = new ArrayList<>();
        for (int index = 0; index < 65; index++) {
            entries.add(entry(
                    new BlueprintResearchTarget(
                            List.of(), List.of(tagId), Optional.empty()),
                    WEAPONS_LANE,
                    Tier.STARTER,
                    index));
        }
        ResearchTechTreeEntryBundle bundle = new ResearchTechTreeEntryBundle(
                1,
                TREE,
                0,
                entries);

        assertThrows(IllegalArgumentException.class, () -> snapshot(
                Map.of(tagId, new BlueprintLootTag(1, values)),
                Map.of(),
                bundle));
    }

    private static BlueprintResearchSnapshot snapshot(
            Map<ResourceLocation, BlueprintLootTag> tags,
            Map<ResourceLocation, BlueprintResearchRule> rules,
            ResearchTechTreeEntryBundle... bundles) {
        java.util.LinkedHashMap<ResourceLocation, ResearchTechTreeEntryBundle> indexed =
                new java.util.LinkedHashMap<>();
        for (int index = 0; index < bundles.length; index++) {
            indexed.put(id("test:bundle_" + index), bundles[index]);
        }
        return BlueprintResearchSnapshot.create(
                tags,
                Map.of(PROFILE, profile(TREE)),
                rules,
                Map.of(),
                Map.of(TREE, tree()),
                indexed);
    }

    private static BlueprintResearchSnapshot snapshotWithAutomaticProfile(
            ResearchAutomaticPlacementProfile automaticProfile,
            ResearchTechTreeEntryBundle... bundles) {
        return snapshotWithAutomaticProfile(automaticProfile, tree(), bundles);
    }

    private static BlueprintResearchSnapshot snapshotWithAutomaticProfile(
            ResearchAutomaticPlacementProfile automaticProfile,
            ResearchTechTreeDefinition tree,
            ResearchTechTreeEntryBundle... bundles) {
        java.util.LinkedHashMap<ResourceLocation, ResearchTechTreeEntryBundle> indexed =
                new java.util.LinkedHashMap<>();
        for (int index = 0; index < bundles.length; index++) {
            indexed.put(id("test:auto_bundle_" + index), bundles[index]);
        }
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile(TREE)),
                Map.of(),
                Map.of(),
                Map.of(TREE, tree),
                indexed,
                Map.of(id("test:auto_profile"), automaticProfile));
    }

    private static AutomaticWeaponEvidenceSnapshot evidence(
            String blueprintId,
            ResearchAutomaticPlacementProfile profile) {
        return evidence(blueprintId, profile, false);
    }

    private static AutomaticWeaponEvidenceSnapshot evidence(
            String blueprintId,
            ResearchAutomaticPlacementProfile profile,
            boolean scriptControlled) {
        WeaponStatEvidence raw = weaponEvidence(blueprintId, 0, scriptControlled);
        WeaponMechanicalReferenceCatalog references = WeaponMechanicalReferenceCatalog.bundled();
        var score = new WeaponMechanicalScorer().score(raw, references.reference());
        Map<String, com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalScore> scores =
                Map.of(blueprintId, score);
        var plan = new AutomaticWeaponPlacementPlanner().plan(
                scores, List.of(blueprintId), profile.placementPolicy());
        return new AutomaticWeaponEvidenceSnapshot(
                11L,
                references.referenceVersion(),
                references.sourceVersion(),
                1,
                references.blueprintIds().size(),
                0,
                Set.of(),
                Map.of(blueprintId, raw),
                scores,
                Map.of(),
                plan);
    }

    private static AutomaticWeaponEvidenceSnapshot evidence(
            List<String> blueprintIds,
            ResearchAutomaticPlacementProfile profile) {
        WeaponMechanicalReferenceCatalog references = WeaponMechanicalReferenceCatalog.bundled();
        Map<String, WeaponStatEvidence> evidence = new java.util.LinkedHashMap<>();
        Map<String, com.gamergaming.taczweaponblueprints.research.tree.automatic
                .WeaponMechanicalScore> scores = new java.util.LinkedHashMap<>();
        for (int index = 0; index < blueprintIds.size(); index++) {
            String blueprintId = blueprintIds.get(index);
            WeaponStatEvidence raw = weaponEvidence(blueprintId, index, false);
            evidence.put(blueprintId, raw);
            scores.put(blueprintId,
                    new WeaponMechanicalScorer().score(raw, references.reference()));
        }
        var plan = new AutomaticWeaponPlacementPlanner().plan(
                scores, blueprintIds, profile.placementPolicy());
        return new AutomaticWeaponEvidenceSnapshot(
                11L,
                references.referenceVersion(),
                references.sourceVersion(),
                blueprintIds.size(),
                references.blueprintIds().size(),
                0,
                Set.of(),
                evidence,
                scores,
                Map.of(),
                plan);
    }

    private static WeaponStatEvidence weaponEvidence(
            String blueprintId,
            int index,
            boolean scriptControlled) {
        int role = Math.floorMod(index, 3);
        return new WeaponStatEvidence(
                blueprintId,
                switch (role) {
                    case 0 -> "rifle";
                    case 1 -> "sniper";
                    default -> "smg";
                },
                8.0 + role * 4.0,
                0.0,
                500.0 + role * 150.0,
                15 + role * 10,
                2.0,
                100.0 + role * 200.0,
                50.0,
                0.1,
                1.5,
                1,
                0.2,
                0.3,
                2.0,
                0.2,
                0.4,
                -0.2,
                1,
                2,
                null,
                "magazine",
                false,
                scriptControlled,
                List.of());
    }

    private static ResearchTechTreeDefinition tree() {
        List<ResearchTechTreeDefinition.TierDefinition> tiers = Arrays.stream(Tier.values())
                .map(tier -> new ResearchTechTreeDefinition.TierDefinition(
                        tier,
                        title(tier),
                        Optional.empty()))
                .toList();
        ResearchTechTreeDefinition.LaneDefinition lane =
                new ResearchTechTreeDefinition.LaneDefinition(
                        WEAPONS_LANE,
                        "General",
                        Optional.empty(),
                        Optional.empty(),
                        10);
        ResearchTechTreeDefinition.DomainDefinition weapons =
                new ResearchTechTreeDefinition.DomainDefinition(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.empty(),
                        WEAPONS_LANE,
                        Tier.STARTER,
                        List.of(lane));
        return new ResearchTechTreeDefinition(
                1,
                "Progression",
                Optional.empty(),
                Optional.empty(),
                tiers,
                List.of(weapons));
    }

    private static ResearchTechTreeDefinition treeWithCapacity(int capacity) {
        ResearchTechTreeDefinition legacy = tree();
        return new ResearchTechTreeDefinition(
                ResearchTechTreeDefinition.CURRENT_FORMAT,
                legacy.title(),
                legacy.translationKey(),
                legacy.icon(),
                new ResearchTechTreeDefinition.LayoutDefinition(capacity),
                legacy.tiers(),
                legacy.domains());
    }

    private static ResearchTechTreeDefinition treeWithDynamicCapacity(
            int minimum,
            int maximum) {
        ResearchTechTreeDefinition legacy = tree();
        return new ResearchTechTreeDefinition(
                ResearchTechTreeDefinition.CURRENT_FORMAT,
                legacy.title(),
                legacy.translationKey(),
                legacy.icon(),
                new ResearchTechTreeDefinition.LayoutDefinition(
                        ResearchTechTreeDefinition.WidthMode.DYNAMIC,
                        minimum,
                        maximum),
                legacy.tiers(),
                legacy.domains());
    }

    private static ResearchTechTreeEntryBundle bundle(
            ResourceLocation tree,
            int priority,
            ResearchTechTreeEntryBundle.Entry... entries) {
        return new ResearchTechTreeEntryBundle(1, tree, priority, List.of(entries));
    }

    private static ResearchTechTreeEntryBundle.Entry entry(
            BlueprintResearchTarget target,
            ResourceLocation lane,
            Tier tier,
            int order) {
        return new ResearchTechTreeEntryBundle.Entry(
                target,
                Domain.WEAPONS,
                lane,
                tier,
                order,
                Optional.empty(),
                Optional.empty());
    }

    private static ResearchTechTreeEntryBundle.Entry entry(
            BlueprintResearchTarget target,
            ResourceLocation lane,
            Tier tier,
            int level,
            int order) {
        return new ResearchTechTreeEntryBundle.Entry(
                target,
                Domain.WEAPONS,
                lane,
                tier,
                level,
                order,
                Optional.empty(),
                Optional.empty(),
                false);
    }

    private static BlueprintResearchTarget exactTarget(String id) {
        return new BlueprintResearchTarget(List.of(id(id)), List.of(), Optional.empty());
    }

    private static BlueprintResearchTarget selectorTarget() {
        return new BlueprintResearchTarget(
                List.of(),
                List.of(),
                Optional.of(new BlueprintCatalogSelector(
                        List.of("test"),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(BlueprintKind.GUN),
                        1.0F)));
    }

    private static BlueprintResearchProfile profile(ResourceLocation tree) {
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
                Optional.of(tree));
    }

    private static BlueprintResearchRule rule(String target, List<ResourceLocation> prerequisites) {
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
                Optional.empty(),
                prerequisites.isEmpty() ? Optional.empty() : Optional.of(prerequisites),
                Optional.empty());
    }

    private static BlueprintData data(ResourceLocation id, BlueprintKind kind) {
        return new BlueprintData(
                id.toString(),
                "name",
                "tooltip",
                id,
                null,
                kind == BlueprintKind.AMMO ? "ammo" : "gun",
                id("test:slot"),
                kind);
    }

    private static AutomaticWeaponPlacementCandidateSnapshot positionedCandidates(
            BlueprintResearchSnapshot research,
            long researchRevision,
            Map<ResourceLocation, BlueprintData> catalog,
            long catalogRevision,
            AutomaticWeaponEvidenceSnapshot evidence,
            ResearchAutomaticPlacementProfile profile) {
        return AutomaticWeaponCandidatePositioner.position(
                AutomaticWeaponPlacementCandidateClassifier.classify(
                        research,
                        researchRevision,
                        catalog,
                        catalogRevision,
                        evidence,
                        profile),
                research.techTrees().get(profile.tree()));
    }

    private static String title(Tier tier) {
        String lower = tier.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String validTreeJson() {
        return """
                {
                  "format": 1,
                  "title": "Progression",
                  "tiers": [
                    {"id": "starter", "title": "Starter"},
                    {"id": "basic", "title": "Basic"},
                    {"id": "established", "title": "Established"},
                    {"id": "advanced", "title": "Advanced"},
                    {"id": "elite", "title": "Elite"},
                    {"id": "apex", "title": "Apex"}
                  ],
                  "domains": [{
                    "id": "weapons",
                    "title": "Weapons",
                    "fallback_lane": "test:weapons/general",
                    "fallback_tier": "starter",
                    "lanes": [{"id": "test:weapons/general", "title": "General", "order": 10}]
                  }]
                }
                """;
    }

    private static String formatTwoTreeJson(String optionalBandField) {
        return """
                {
                  "format": 2,
                  "title": "Progression",
                  "layout": {"max_nodes_per_layer": 9}%s,
                  "domains": [{
                    "id": "weapons",
                    "title": "Weapons",
                    "fallback_lane": "test:weapons/general",
                    "fallback_tier": "starter",
                    "lanes": [{"id": "test:weapons/general", "title": "General", "order": 10}]
                  }]
                }
                """.formatted(optionalBandField == null ? "" : optionalBandField.strip());
    }

    private static String validBundleJson() {
        return """
                {
                  "format": 1,
                  "tree": "test:progression",
                  "priority": 10,
                  "entries": [{
                    "target": {"blueprints": ["test:starter"]},
                    "domain": "weapons",
                    "lane": "test:weapons/general",
                    "tier": "starter",
                    "order": 10,
                    "rating": {"combat": 5, "utility": 5, "appeal": 5}
                  }]
                }
                """;
    }

    private static String validProfileJson(boolean includeTree) {
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
                  "creative_bypasses_cost": false%s
                }
                """.formatted(includeTree ? ",\n  \"tech_tree\": \"test:progression\"" : "");
    }

    private static <T> T decode(Codec<T> codec, String json) {
        return codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
    }

    private static <T> void assertDecodeFails(Codec<T> codec, String json) {
        try {
            assertTrue(codec.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).error().isPresent());
        } catch (IllegalArgumentException expected) {
            // Programmatic record construction also enforces the same invariant.
        }
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
