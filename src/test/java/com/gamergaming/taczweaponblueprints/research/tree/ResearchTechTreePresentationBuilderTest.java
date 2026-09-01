package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.WeaponRating;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementProposal;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPrerequisitePlan;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchRule;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchAutomaticPlacementProfile;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.BandBasis;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.BandDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.BandMode;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.BandPolicyDefinition;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeEntryBundle;

import net.minecraft.resources.ResourceLocation;

class ResearchTechTreePresentationBuilderTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation TREE = id("test:progression");
    private static final ResourceLocation WEAPONS = id("test:weapons");
    private static final ResourceLocation ATTACHMENTS = id("test:attachments");
    private static final ResourceLocation AMMO = id("test:ammo");

    @Test
    void publicationOmitsPrivatePlacementsAndEmptyDomainsAndSanitizesIcons() {
        ResourceLocation weapon = id("test:weapon");
        ResourceLocation secret = id("test:secret");
        ResourceLocation scope = id("test:scope");
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(secret, data(secret, BlueprintKind.GUN));
        catalog.put(scope, data(scope, BlueprintKind.ATTACHMENT));
        catalog.put(weapon, data(weapon, BlueprintKind.GUN));

        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                1,
                TREE,
                0,
                List.of(
                        entry(weapon, Domain.WEAPONS, WEAPONS, Tier.APEX, 20,
                                Optional.of(new WeaponRating(90, 90, 90))),
                        entry(secret, Domain.WEAPONS, WEAPONS, Tier.BASIC, 10, Optional.empty()),
                        entry(scope, Domain.ATTACHMENTS, ATTACHMENTS, Tier.STARTER, 0, Optional.empty())));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(
                        id("test:weapon_rule"), rule(weapon, JournalVisibility.FULL, List.of()),
                        id("test:secret_rule"), rule(secret, JournalVisibility.NAME, List.of()),
                        id("test:scope_rule"), rule(scope, JournalVisibility.FULL, List.of())),
                Map.of(),
                Map.of(TREE, definition(Optional.of(secret))),
                Map.of(id("test:entries"), entries));

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                catalog, snapshot, config(), new PlayerRecipeData(), ignored -> false);

        assertEquals(3, publication.graph().nodes().size());
        assertTrue(publication.graph().node(secret).isEmpty());
        assertTrue(publication.graph().nodes().stream()
                .anyMatch(node -> node.visibility() == JournalVisibility.NAME
                        && node.blueprintId().getPath().startsWith("undisclosed/")));

        ResearchTechTreePresentation techTree = publication.techTree();
        assertTrue(techTree.available());
        assertEquals(TREE, techTree.treeId().orElseThrow());
        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS),
                techTree.domains().stream().map(ResearchTechTreePresentation.DomainView::domain).toList());
        assertEquals(2, techTree.memberCount());
        assertFalse(techTree.domains().stream()
                .anyMatch(domain -> domain.domain() == Domain.AMMO));
        assertTrue(techTree.domains().stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .noneMatch(member -> member.nodeId().equals(secret)
                        || member.nodeId().getPath().startsWith("undisclosed/")));

        ResearchTechTreePresentation.DomainView weapons = techTree.domain(Domain.WEAPONS).orElseThrow();
        ResearchTechTreePresentation.Member weaponMember = weapons.lanes().get(0).members().get(0);
        assertEquals(weapon, weaponMember.nodeId());
        assertEquals(Optional.of(new WeaponRating(90, 90, 90)), weaponMember.rating());
        assertEquals(Optional.of(weapon), techTree.iconNodeId());
        assertEquals(Optional.of(weapon), weapons.iconNodeId());
        assertEquals(Optional.of(weapon), weapons.lanes().get(0).iconNodeId());
        assertEquals(List.of(Tier.values()),
                techTree.tiers().stream().map(ResearchTechTreePresentation.TierLabel::tier).toList());
    }

    @Test
    void catalogSpecificLegacySelectorIsLiftedIntoStrictRankOrder() {
        ResourceLocation prerequisite = id("test:a");
        ResourceLocation dependent = id("test:b");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                prerequisite, data(prerequisite, BlueprintKind.GUN),
                dependent, data(dependent, BlueprintKind.GUN));
        BlueprintCatalogSelector allTestGuns = new BlueprintCatalogSelector(
                List.of("test"),
                List.of(),
                List.of(),
                List.of(),
                List.of(BlueprintKind.GUN),
                1.0F);
        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                1,
                TREE,
                0,
                List.of(new ResearchTechTreeEntryBundle.Entry(
                        new BlueprintResearchTarget(List.of(), List.of(), Optional.of(allTestGuns)),
                        Domain.WEAPONS,
                        WEAPONS,
                        Tier.STARTER,
                        0,
                        Optional.empty(),
                        Optional.empty())));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(
                        id("test:a_rule"), rule(prerequisite, JournalVisibility.FULL, List.of()),
                        id("test:b_rule"), rule(dependent, JournalVisibility.FULL, List.of(prerequisite))),
                Map.of(),
                Map.of(TREE, definition(Optional.empty())),
                Map.of(id("test:selector_entries"), entries));

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                catalog, snapshot, config(), new PlayerRecipeData(), ignored -> false);

        assertEquals(2, publication.graph().nodes().size());
        assertEquals(List.of(new ResearchTreeGraph.Edge(prerequisite, dependent)),
                publication.graph().edges());
        assertFalse(publication.presentation().groups().isEmpty());
        assertTrue(publication.techTree().available());
        List<ResearchTechTreePresentation.Member> members = publication.techTree()
                .domain(Domain.WEAPONS).orElseThrow().lanes().get(0).members();
        assertEquals(0, members.get(0).rank());
        assertEquals(1, members.get(1).rank());
    }

    @Test
    void formatTwoPublicationKeepsRankAuthorityAcrossReversedLegacyBands() {
        ResourceLocation prerequisite = id("test:ranked_prerequisite");
        ResourceLocation dependent = id("test:ranked_dependent");
        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                2,
                TREE,
                0,
                List.of(
                        rankedEntry(prerequisite, Tier.APEX, 73, 900),
                        rankedEntry(dependent, Tier.STARTER, 74, 1)));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(
                        id("test:ranked_prerequisite_rule"), rule(
                                prerequisite, JournalVisibility.FULL, List.of()),
                        id("test:ranked_dependent_rule"), rule(
                                dependent, JournalVisibility.FULL, List.of(prerequisite))),
                Map.of(),
                Map.of(TREE, definition(Optional.empty())),
                Map.of(id("test:ranked_entries"), entries));

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                Map.of(
                        prerequisite, data(prerequisite, BlueprintKind.GUN),
                        dependent, data(dependent, BlueprintKind.GUN)),
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false);

        List<ResearchTechTreePresentation.Member> members = publication.techTree()
                .domain(Domain.WEAPONS).orElseThrow().lanes().get(0).members();
        assertEquals(List.of(73, 74), members.stream()
                .map(ResearchTechTreePresentation.Member::rank).toList());
        assertEquals(ResearchTechTreeContract.legacyBandId(Tier.APEX),
                members.get(0).bandId().orElseThrow());
        assertEquals(ResearchTechTreeContract.legacyBandId(Tier.STARTER),
                members.get(1).bandId().orElseThrow());
        assertTrue(ResearchTechTreeContract.progressionTransitionAllowed(
                members.get(0).position(), members.get(1).position()));
    }

    @Test
    void formatTwoBandModesChangeOnlyPresentationAndPublishNoEmptyBands() {
        List<ResourceLocation> nodes = List.of(
                id("test:band_root"),
                id("test:band_second"),
                id("test:band_third"),
                id("test:band_fourth"));
        ResearchTreePublication none = rankedChainPublication(
                nodes, BandPolicyDefinition.NONE);
        ResearchTreePublication dynamic = rankedChainPublication(
                nodes,
                new BandPolicyDefinition(
                        BandMode.DYNAMIC, 2, BandBasis.RANK, List.of()));
        ResourceLocation early = id("test:field_issue");
        ResourceLocation late = id("test:specialized");
        ResearchTreePublication configured = rankedChainPublication(
                nodes,
                new BandPolicyDefinition(
                        BandMode.CONFIGURED,
                        BandPolicyDefinition.DEFAULT_RANKS_PER_BAND,
                        BandBasis.RANK,
                        List.of(
                                new BandDefinition(
                                        early,
                                        "Field Issue",
                                        Optional.empty(),
                                        Optional.of(0x335577),
                                        Optional.of(nodes.get(0)),
                                        Optional.of(1)),
                                new BandDefinition(
                                        late,
                                        "Specialized",
                                        Optional.of("tree.band.test.specialized"),
                                        Optional.of(0xAA7733),
                                        Optional.of(nodes.get(0)),
                                        Optional.empty()))));
        ResearchTreePublication scoreConfigured = rankedChainPublication(
                nodes,
                new BandPolicyDefinition(
                        BandMode.CONFIGURED,
                        BandPolicyDefinition.DEFAULT_RANKS_PER_BAND,
                        BandBasis.SCORE,
                        List.of(
                                new BandDefinition(
                                        id("test:low_score"), "Low", Optional.empty(),
                                        Optional.empty(), Optional.empty(), Optional.of(49)),
                                new BandDefinition(
                                        id("test:high_score"), "High", Optional.empty(),
                                        Optional.empty(), Optional.empty(), Optional.empty()))));

        assertEquals(none.graph(), dynamic.graph());
        assertEquals(none.graph(), configured.graph());
        assertEquals(none.graph(), scoreConfigured.graph());
        assertEquals(memberRanks(none), memberRanks(dynamic));
        assertEquals(memberRanks(none), memberRanks(configured));
        assertTrue(none.techTree().tiers().isEmpty());
        assertTrue(none.techTree().bands().isEmpty());
        assertTrue(none.techTree().domains().stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .allMatch(member -> member.bandId().isEmpty()));

        assertEquals(List.of(
                        id("taczweaponblueprints:dynamic_band/0"),
                        id("taczweaponblueprints:dynamic_band/1")),
                dynamic.techTree().bands().stream()
                        .map(ResearchTechTreePresentation.BandLabel::id)
                        .toList());
        assertEquals(List.of(0, 0, 1, 1), memberBandIndexes(dynamic));

        assertEquals(List.of(early, late), configured.techTree().bands().stream()
                .map(ResearchTechTreePresentation.BandLabel::id).toList());
        assertEquals(Optional.of(0x335577), configured.techTree().bands().get(0).color());
        assertEquals(Optional.of(nodes.get(0)),
                configured.techTree().bands().get(0).icon());
        assertTrue(configured.techTree().bands().get(1).icon().isEmpty(),
                "a band cannot disclose or borrow another band's member icon");
        assertEquals(List.of(0, 0, 1, 1), memberBandIndexes(configured));
        assertTrue(scoreConfigured.techTree().bands().isEmpty(),
                "authored members have no mechanical-score evidence to fabricate");
    }

    @Test
    void dynamicBandsCoalesceAtTheWireLimitWithoutPublishingEmptyLabels() {
        List<ResourceLocation> nodes = java.util.stream.IntStream.range(0, 33)
                .mapToObj(index -> id("test:dynamic_limit_" + index))
                .toList();
        ResearchTreePublication publication = rankedChainPublication(
                nodes,
                new BandPolicyDefinition(
                        BandMode.DYNAMIC, 1, BandBasis.RANK, List.of()));

        assertEquals(17, publication.techTree().bands().size());
        assertEquals(17, publication.techTree().domains().stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .map(ResearchTechTreePresentation.Member::bandId)
                .map(Optional::orElseThrow)
                .distinct()
                .count());
    }

    @Test
    void configuredScoreBandsAreOmittedWhenOneRankStraddlesIntervals() {
        ResourceLocation low = id("test:low_score_weapon");
        ResourceLocation high = id("test:high_score_weapon");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                low, data(low, BlueprintKind.GUN),
                high, data(high, BlueprintKind.GUN));
        BlueprintCatalogSelector testGuns = new BlueprintCatalogSelector(
                List.of("test"),
                List.of(),
                List.of(),
                List.of(),
                List.of(BlueprintKind.GUN),
                1.0F);
        ResearchTechTreeEntryBundle fallback = new ResearchTechTreeEntryBundle(
                2,
                TREE,
                0,
                List.of(new ResearchTechTreeEntryBundle.Entry(
                        new BlueprintResearchTarget(
                                List.of(), List.of(), Optional.of(testGuns)),
                        Domain.WEAPONS,
                        WEAPONS,
                        Tier.STARTER,
                        0,
                        Optional.of(0),
                        900_000,
                        Optional.empty(),
                        Optional.empty(),
                        true)));
        BandPolicyDefinition scoreBands = new BandPolicyDefinition(
                BandMode.CONFIGURED,
                BandPolicyDefinition.DEFAULT_RANKS_PER_BAND,
                BandBasis.SCORE,
                List.of(
                        new BandDefinition(
                                id("test:low_score"), "Low", Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.of(49)),
                        new BandDefinition(
                                id("test:high_score"), "High", Optional.empty(),
                                Optional.empty(), Optional.empty(), Optional.empty())));
        ResearchAutomaticPlacementProfile automaticProfile =
                new ResearchAutomaticPlacementProfile(
                        2,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(
                        id("test:low_score_rule"), rule(
                                low, JournalVisibility.FULL, List.of()),
                        id("test:high_score_rule"), rule(
                                high, JournalVisibility.FULL, List.of())),
                Map.of(),
                Map.of(TREE, automaticDefinition(formatTwoDefinition(scoreBands))),
                Map.of(id("test:score_fallback"), fallback),
                Map.of(id("test:automatic"), automaticProfile));
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                0,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                9,
                List.of());
        Map<String, AutomaticWeaponPlacementProposal> proposals = new LinkedHashMap<>();
        proposals.put(low.toString(), scoreProposal(low, 20, 0));
        proposals.put(high.toString(), scoreProposal(high, 80, 1));
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        policy,
                        5L,
                        7L,
                        2,
                        proposals,
                        Map.of(),
                        java.util.Set.of(),
                        java.util.Set.of());

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false,
                candidates);

        assertEquals(List.of(0, 0), memberRanks(publication));
        assertTrue(publication.techTree().bands().isEmpty());
        assertTrue(publication.techTree().domains().stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .allMatch(member -> member.bandId().isEmpty()));
    }

    @Test
    void eligibleAutomaticFallbackUsesLosslessPositionWithoutCreatingAnEdge() {
        ResourceLocation addOn = id("test:addon_pistol");
        BlueprintData addOnData = data(addOn, BlueprintKind.GUN);
        BlueprintCatalogSelector testGuns = new BlueprintCatalogSelector(
                List.of("test"),
                List.of(),
                List.of(),
                List.of(),
                List.of(BlueprintKind.GUN),
                1.0F);
        ResearchTechTreeEntryBundle fallback = new ResearchTechTreeEntryBundle(
                1,
                TREE,
                0,
                List.of(new ResearchTechTreeEntryBundle.Entry(
                        new BlueprintResearchTarget(List.of(), List.of(), Optional.of(testGuns)),
                        Domain.WEAPONS,
                        WEAPONS,
                        Tier.BASIC,
                        900_000,
                        Optional.empty(),
                        Optional.empty(),
                        true)));
        ResearchAutomaticPlacementProfile automaticProfile =
                new ResearchAutomaticPlacementProfile(
                        2,
                        TREE,
                        AutomaticPlacementMode.DISTRIBUTED,
                        4,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(id("test:addon_rule"), rule(addOn, JournalVisibility.FULL, List.of())),
                Map.of(),
                Map.of(TREE, automaticDefinition(definition(Optional.empty()))),
                Map.of(id("test:fallback"), fallback),
                Map.of(id("test:automatic"), automaticProfile));
        int score = 50;
        long siblingOrder = Math.addExact(
                Math.multiplyExact(score, 1L << 56), 17L);
        ProgressionPosition position = new ProgressionPosition(
                Tier.forScore(score),
                ResearchTechTreeContract.levelForScore(score, 4),
                siblingOrder);
        AutomaticWeaponPlacementProposal proposal = new AutomaticWeaponPlacementProposal(
                addOn.toString(),
                score,
                100,
                position,
                4,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.DISTRIBUTED,
                        new AutomaticWeaponPlacementPolicy(4, 0),
                        5L,
                        7L,
                        1,
                        Map.of(addOn.toString(), proposal),
                        Map.of(),
                        java.util.Set.of(),
                        java.util.Set.of());

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                Map.of(addOn, addOnData),
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false,
                candidates);

        assertTrue(publication.graph().edges().isEmpty());
        ResearchTechTreePresentation.Member member = publication.techTree()
                .domain(Domain.WEAPONS).orElseThrow()
                .lanes().get(0).members().get(0);
        assertEquals(PlacementOrigin.AUTOMATIC, member.origin());
        assertEquals(position.tier(), member.tier());
        assertEquals(position.level(), member.level());
        assertEquals(position.siblingOrder(), member.siblingOrder());
    }

    @Test
    void automaticTopologyOwnsPlacementAndLiftsItsDependent() {
        ResourceLocation prerequisite = id("test:reviewed_pistol");
        ResourceLocation addOn = id("test:addon_pistol");
        BlueprintCatalogSelector testGuns = new BlueprintCatalogSelector(
                List.of("test"),
                List.of(),
                List.of(),
                List.of(),
                List.of(BlueprintKind.GUN),
                1.0F);
        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                1,
                TREE,
                0,
                List.of(
                        entry(prerequisite, Domain.WEAPONS, WEAPONS, Tier.BASIC, 10,
                                Optional.empty()),
                        new ResearchTechTreeEntryBundle.Entry(
                                new BlueprintResearchTarget(
                                        List.of(), List.of(), Optional.of(testGuns)),
                                Domain.WEAPONS,
                                WEAPONS,
                                Tier.BASIC,
                                900_000,
                                Optional.empty(),
                                Optional.empty(),
                                true)));
        ResearchAutomaticPlacementProfile automaticProfile =
                new ResearchAutomaticPlacementProfile(
                        2,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        4,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(
                        id("test:prerequisite_rule"), rule(
                                prerequisite, JournalVisibility.FULL, List.of()),
                        id("test:addon_rule"), rule(
                                addOn, JournalVisibility.FULL, List.of(prerequisite))),
                Map.of(),
                Map.of(TREE, automaticDefinition(definition(Optional.empty()))),
                Map.of(id("test:entries"), entries),
                Map.of(id("test:automatic"), automaticProfile));

        int score = 17;
        ProgressionPosition contradictoryPosition = new ProgressionPosition(
                Tier.forScore(score),
                ResearchTechTreeContract.levelForScore(score, 4),
                1L);
        AutomaticWeaponPlacementProposal proposal = new AutomaticWeaponPlacementProposal(
                addOn.toString(),
                score,
                100,
                contradictoryPosition,
                4,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
        AutomaticWeaponPlacementProposal prerequisiteProposal =
                new AutomaticWeaponPlacementProposal(
                        prerequisite.toString(),
                        5,
                        100,
                        new ProgressionPosition(
                                Tier.forScore(5),
                                ResearchTechTreeContract.levelForScore(5, 4),
                                0L),
                        4,
                        ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                        ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                        ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                        List.of());
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        new AutomaticWeaponPlacementPolicy(4, 0),
                        5L,
                        7L,
                        2,
                        Map.of(
                                prerequisite.toString(), prerequisiteProposal,
                                addOn.toString(), proposal),
                        Map.of(),
                        java.util.Set.of(),
                        java.util.Set.of());
        AutomaticWeaponPrerequisitePlan prerequisites =
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        2,
                        Map.of(addOn, List.of(prerequisite)),
                        Map.of(prerequisite, "generated_root"));

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                Map.of(
                        prerequisite, data(prerequisite, BlueprintKind.GUN),
                        addOn, data(addOn, BlueprintKind.GUN)),
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false,
                candidates,
                prerequisites);

        assertTrue(publication.techTree().available());
        assertEquals(List.of(new ResearchTreeGraph.Edge(prerequisite, addOn)),
                publication.graph().edges());
        Map<ResourceLocation, ResearchTechTreePresentation.Member> members =
                publication.techTree()
                .domain(Domain.WEAPONS).orElseThrow()
                .lanes().get(0).members().stream()
                .collect(java.util.stream.Collectors.toMap(
                        ResearchTechTreePresentation.Member::nodeId,
                        value -> value));
        ResearchTechTreePresentation.Member member = members.get(addOn);
        assertEquals(PlacementOrigin.AUTOMATIC, member.origin());
        assertEquals(PlacementOrigin.AUTOMATIC, members.get(prerequisite).origin());
        assertTrue(member.rank() > members.get(prerequisite).rank());
        assertEquals(1L, member.siblingOrder());
    }

    @Test
    void automaticAuthorityOmitsWeaponsWithoutAutomaticProposals() {
        ResourceLocation foundation = id("test:foundation");
        ResourceLocation automaticPrerequisite = id("test:automatic_prerequisite");
        ResourceLocation authoredDependent = id("test:authored_dependent");
        ResourceLocation laterAutomatic = id("test:later_automatic");
        BlueprintCatalogSelector testGuns = new BlueprintCatalogSelector(
                List.of("test"),
                List.of(),
                List.of(),
                List.of(),
                List.of(BlueprintKind.GUN),
                1.0F);
        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                2,
                TREE,
                0,
                List.of(
                        rankedEntry(foundation, Tier.STARTER, 0, 0),
                        rankedEntry(authoredDependent, Tier.BASIC, 2, 0),
                        new ResearchTechTreeEntryBundle.Entry(
                                new BlueprintResearchTarget(
                                        List.of(), List.of(), Optional.of(testGuns)),
                                Domain.WEAPONS,
                                WEAPONS,
                                Tier.STARTER,
                                0,
                                Optional.of(0),
                                900_000,
                                Optional.empty(),
                                Optional.empty(),
                                true)));
        ResearchAutomaticPlacementProfile automaticProfile =
                new ResearchAutomaticPlacementProfile(
                        2,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        3,
                        0,
                        AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                        2,
                        4,
                        9,
                        List.of());
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(
                        id("test:foundation_rule"), rule(
                                foundation, JournalVisibility.FULL, List.of()),
                        id("test:automatic_prerequisite_rule"), rule(
                                automaticPrerequisite, JournalVisibility.FULL, List.of()),
                        id("test:authored_dependent_rule"), rule(
                                authoredDependent,
                                JournalVisibility.FULL,
                                List.of(automaticPrerequisite)),
                        id("test:later_automatic_rule"), rule(
                                laterAutomatic, JournalVisibility.FULL, List.of())),
                Map.of(),
                Map.of(TREE, automaticDefinition(
                        formatTwoDefinition(BandPolicyDefinition.NONE))),
                Map.of(id("test:entries"), entries),
                Map.of(id("test:automatic"), automaticProfile));
        AutomaticWeaponPlacementPolicy policy = new AutomaticWeaponPlacementPolicy(
                3,
                0,
                AutomaticWeaponPlacementPolicy.ReviewHandling.PLACE_CONNECTED,
                2,
                4,
                AutomaticWeaponPlacementPolicy.LayeringStrategy.DYNAMIC_STAT_LAYERS,
                9,
                List.of());
        AutomaticWeaponPlacementProposal prerequisiteProposal =
                scoreProposal(automaticPrerequisite, 25, 1L)
                        .withProgressionCoordinate(new ProgressionCoordinate(
                                1, 1L, Optional.empty()));
        AutomaticWeaponPlacementProposal laterProposal =
                scoreProposal(laterAutomatic, 75, 2L)
                        .withProgressionCoordinate(new ProgressionCoordinate(
                                3, 2L, Optional.empty()));
        AutomaticWeaponPlacementCandidateSnapshot candidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        policy,
                        5L,
                        7L,
                        4,
                        Map.of(
                                automaticPrerequisite.toString(), prerequisiteProposal,
                                laterAutomatic.toString(), laterProposal),
                        Map.of(),
                        java.util.Set.of(),
                        java.util.Set.of(
                                foundation.toString(), authoredDependent.toString()));
        AutomaticWeaponPrerequisitePlan prerequisites =
                new AutomaticWeaponPrerequisitePlan(
                        PROFILE,
                        TREE,
                        AutomaticPlacementMode.CONNECTED,
                        5L,
                        7L,
                        2,
                        Map.of(
                                automaticPrerequisite, List.of(foundation),
                                laterAutomatic, List.of(foundation)),
                        Map.of(),
                        Map.of(),
                        Map.of(
                                automaticPrerequisite,
                                new AutomaticWeaponPrerequisitePlan.BranchCoordinate(
                                        0, 0, 0, 0),
                                laterAutomatic,
                                new AutomaticWeaponPrerequisitePlan.BranchCoordinate(
                                        0, 0, 0, 0)));

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                Map.of(
                        foundation, data(foundation, BlueprintKind.GUN),
                        automaticPrerequisite,
                                data(automaticPrerequisite, BlueprintKind.GUN),
                        authoredDependent, data(authoredDependent, BlueprintKind.GUN),
                        laterAutomatic, data(laterAutomatic, BlueprintKind.GUN)),
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false,
                candidates,
                prerequisites);

        assertTrue(publication.techTree().available());
        Map<ResourceLocation, ResearchTechTreePresentation.Member> members =
                publication.techTree().domains().stream()
                        .flatMap(domain -> domain.lanes().stream())
                        .flatMap(lane -> lane.members().stream())
                        .collect(java.util.stream.Collectors.toMap(
                                ResearchTechTreePresentation.Member::nodeId,
                                member -> member));
        assertEquals(PlacementOrigin.AUTOMATIC,
                members.get(automaticPrerequisite).origin());
        assertEquals(1, members.get(automaticPrerequisite).rank());
        assertFalse(members.containsKey(foundation));
        assertFalse(members.containsKey(authoredDependent));
        assertEquals(2, members.size());
        assertEquals(3, members.get(laterAutomatic).rank());
    }

    @Test
    void authoredAuthorityIgnoresAnAutomaticPlacementCandidate() {
        ResourceLocation authored = id("test:authored_pistol");
        ResourceLocation unspecified = id("test:unspecified_rifle");
        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                1,
                TREE,
                0,
                List.of(entry(
                        authored,
                        Domain.WEAPONS,
                        WEAPONS,
                        Tier.BASIC,
                        42,
                        Optional.empty())));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(
                        id("test:authored_rule"), rule(
                                authored, JournalVisibility.FULL, List.of()),
                        id("test:unspecified_rule"), rule(
                                unspecified, JournalVisibility.FULL, List.of())),
                Map.of(),
                Map.of(TREE, definition(Optional.empty())),
                Map.of(id("test:entries"), entries));

        int score = 50;
        AutomaticWeaponPlacementProposal proposal = new AutomaticWeaponPlacementProposal(
                authored.toString(),
                score,
                100,
                new ProgressionPosition(
                        Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(score, 4),
                        50L),
                4,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
        AutomaticWeaponPlacementCandidateSnapshot inconsistentCandidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        TREE,
                        AutomaticPlacementMode.DISTRIBUTED,
                        new AutomaticWeaponPlacementPolicy(4, 0),
                        5L,
                        7L,
                        2,
                        Map.of(authored.toString(), proposal),
                        Map.of(),
                        java.util.Set.of(),
                        java.util.Set.of(unspecified.toString()));

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                Map.of(
                        authored, data(authored, BlueprintKind.GUN),
                        unspecified, data(unspecified, BlueprintKind.GUN)),
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false,
                inconsistentCandidates);

        assertTrue(publication.techTree().available());
        assertEquals(List.of(authored), publication.graph().nodes().stream()
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList());
        ResearchTechTreePresentation.Member member = publication.techTree()
                .domain(Domain.WEAPONS).orElseThrow()
                .lanes().get(0).members().get(0);
        assertEquals(PlacementOrigin.EXACT, member.origin());
        assertEquals(Tier.BASIC, member.tier());
        assertEquals(0, member.level());
        assertEquals(42L, member.siblingOrder());
    }

    @Test
    void authoredAuthorityIgnoresAnUnrelatedAutomaticPublication() {
        ResourceLocation weapon = id("test:authored_failure_guard");
        ResearchTechTreeEntryBundle entries = new ResearchTechTreeEntryBundle(
                1,
                TREE,
                0,
                List.of(entry(
                        weapon,
                        Domain.WEAPONS,
                        WEAPONS,
                        Tier.BASIC,
                        42,
                        Optional.empty())));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of(id("test:failure_guard_rule"), rule(
                        weapon, JournalVisibility.FULL, List.of())),
                Map.of(),
                Map.of(TREE, definition(Optional.empty())),
                Map.of(id("test:failure_guard_entries"), entries));
        AutomaticWeaponPlacementCandidateSnapshot wrongTreeCandidates =
                new AutomaticWeaponPlacementCandidateSnapshot(
                        id("test:different_tree"),
                        AutomaticPlacementMode.DISTRIBUTED,
                        new AutomaticWeaponPlacementPolicy(4, 0),
                        5L,
                        7L,
                        1,
                        Map.of(),
                        Map.of(),
                        java.util.Set.of(weapon.toString()),
                        java.util.Set.of());

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                Map.of(weapon, data(weapon, BlueprintKind.GUN)),
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false,
                wrongTreeCandidates);

        assertTrue(publication.techTree().available());
        assertFalse(publication.presentation().groups().isEmpty());
        assertEquals(1, publication.graph().nodes().size());
        assertEquals(PlacementOrigin.EXACT, publication.techTree()
                .domain(Domain.WEAPONS).orElseThrow()
                .lanes().get(0).members().get(0).origin());
    }

    @Test
    void presentationValidationRejectsAnAnonymousGraphNode() {
        ResourceLocation opaque = ResearchTreeGraph.redactedNodeId(0);
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(new ResearchTreeGraph.Node(
                        0,
                        opaque,
                        "name.safe",
                        ResearchTreeGraph.REDACTED_ITEM_TYPE,
                        ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                        JournalVisibility.NAME,
                        false,
                        false,
                        false,
                        0,
                        0,
                        0,
                        0,
                        ResearchTreeGraph.Availability.REDACTED)),
                List.of());
        ResearchTechTreePresentation presentation = new ResearchTechTreePresentation(
                Optional.of(TREE),
                "Progression",
                Optional.empty(),
                Optional.empty(),
                Arrays.stream(Tier.values())
                        .map(tier -> new ResearchTechTreePresentation.TierLabel(
                                tier, title(tier), Optional.empty()))
                        .toList(),
                List.of(new ResearchTechTreePresentation.DomainView(
                        Domain.WEAPONS,
                        "Weapons",
                        Optional.empty(),
                        Optional.empty(),
                        List.of(new ResearchTechTreePresentation.LaneView(
                                WEAPONS,
                                "Weapons",
                                Optional.empty(),
                                Optional.empty(),
                                0,
                                List.of(new ResearchTechTreePresentation.Member(
                                        opaque, Tier.STARTER, 0, Optional.empty())))))));

        assertThrows(IllegalArgumentException.class, () -> presentation.validateAgainst(graph));
    }

    private static BlueprintResearchProfile profile() {
        return new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.FULL,
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

    private static BlueprintResearchRule rule(
            ResourceLocation target,
            JournalVisibility visibility,
            List<ResourceLocation> prerequisites) {
        return new BlueprintResearchRule(
                1,
                PROFILE,
                0,
                new BlueprintResearchTarget(List.of(target), List.of(), Optional.empty()),
                Optional.of(visibility),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                prerequisites.isEmpty() ? Optional.empty() : Optional.of(prerequisites),
                Optional.empty());
    }

    private static ResearchTechTreeEntryBundle.Entry entry(
            ResourceLocation target,
            Domain domain,
            ResourceLocation lane,
            Tier tier,
            int order,
            Optional<WeaponRating> rating) {
        return new ResearchTechTreeEntryBundle.Entry(
                new BlueprintResearchTarget(List.of(target), List.of(), Optional.empty()),
                domain,
                lane,
                tier,
                order,
                rating,
                Optional.empty());
    }

    private static ResearchTechTreeEntryBundle.Entry rankedEntry(
            ResourceLocation target,
            Tier tier,
            int rank,
            int order) {
        return new ResearchTechTreeEntryBundle.Entry(
                new BlueprintResearchTarget(List.of(target), List.of(), Optional.empty()),
                Domain.WEAPONS,
                WEAPONS,
                tier,
                0,
                Optional.of(rank),
                order,
                Optional.empty(),
                Optional.empty(),
                false);
    }

    private static ResearchTreePublication rankedChainPublication(
            List<ResourceLocation> nodes,
            BandPolicyDefinition bandPolicy) {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        Map<ResourceLocation, BlueprintResearchRule> rules = new LinkedHashMap<>();
        List<ResearchTechTreeEntryBundle.Entry> entries = new java.util.ArrayList<>();
        for (int index = 0; index < nodes.size(); index++) {
            ResourceLocation node = nodes.get(index);
            catalog.put(node, data(node, BlueprintKind.GUN));
            rules.put(id("test:band_rule_" + index), rule(
                    node,
                    JournalVisibility.FULL,
                    index == 0 ? List.of() : List.of(nodes.get(index - 1))));
            entries.add(rankedEntry(node, Tier.STARTER, index, index));
        }
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                rules,
                Map.of(),
                Map.of(TREE, formatTwoDefinition(bandPolicy)),
                Map.of(id("test:band_entries"), new ResearchTechTreeEntryBundle(
                        2, TREE, 0, entries)));
        return ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false);
    }

    private static List<Integer> memberRanks(ResearchTreePublication publication) {
        return publication.techTree().domains().stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .map(ResearchTechTreePresentation.Member::rank)
                .toList();
    }

    private static List<Integer> memberBandIndexes(ResearchTreePublication publication) {
        Map<ResourceLocation, Integer> indexes = new LinkedHashMap<>();
        for (int index = 0; index < publication.techTree().bands().size(); index++) {
            indexes.put(publication.techTree().bands().get(index).id(), index);
        }
        return publication.techTree().domains().stream()
                .flatMap(domain -> domain.lanes().stream())
                .flatMap(lane -> lane.members().stream())
                .map(member -> indexes.get(member.bandId().orElseThrow()))
                .toList();
    }

    private static ResearchTechTreeDefinition formatTwoDefinition(
            BandPolicyDefinition bandPolicy) {
        return new ResearchTechTreeDefinition(
                2,
                "Progression",
                Optional.of("tree.test.progression"),
                Optional.empty(),
                new ResearchTechTreeDefinition.LayoutDefinition(9),
                bandPolicy,
                List.of(),
                List.of(domain(
                        Domain.WEAPONS,
                        "Weapons",
                        WEAPONS,
                        Optional.empty())));
    }

    private static ResearchTechTreeDefinition automaticDefinition(
            ResearchTechTreeDefinition definition) {
        return new ResearchTechTreeDefinition(
                ResearchTechTreeDefinition.CURRENT_FORMAT,
                definition.title(),
                definition.translationKey(),
                definition.icon(),
                ResearchTechTreeDefinition.WeaponPlacementMode.AUTOMATIC,
                definition.format() == ResearchTechTreeDefinition.CURRENT_FORMAT
                        ? definition.layout()
                        : new ResearchTechTreeDefinition.LayoutDefinition(9),
                definition.format() == ResearchTechTreeDefinition.CURRENT_FORMAT
                        ? definition.bandPolicy()
                        : ResearchTechTreeDefinition.BandPolicyDefinition.NONE,
                definition.tiers(),
                definition.domains());
    }

    private static AutomaticWeaponPlacementProposal scoreProposal(
            ResourceLocation blueprintId,
            int score,
            long siblingOrder) {
        return new AutomaticWeaponPlacementProposal(
                blueprintId.toString(),
                score,
                100,
                new ProgressionPosition(
                        Tier.forScore(score),
                        ResearchTechTreeContract.levelForScore(score, 3),
                        siblingOrder),
                new ProgressionCoordinate(0, siblingOrder, Optional.empty()),
                3,
                ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                ResearchTechTreeContract.AUTOMATIC_PLACEMENT_VERSION,
                List.of());
    }

    private static ResearchTechTreeDefinition definition(Optional<ResourceLocation> privateIcon) {
        List<ResearchTechTreeDefinition.TierDefinition> tiers = Arrays.stream(Tier.values())
                .map(tier -> new ResearchTechTreeDefinition.TierDefinition(
                        tier, title(tier), Optional.empty()))
                .toList();
        return new ResearchTechTreeDefinition(
                1,
                "Progression",
                Optional.of("tree.test.progression"),
                privateIcon,
                tiers,
                List.of(
                        domain(Domain.WEAPONS, "Weapons", WEAPONS, privateIcon),
                        domain(Domain.ATTACHMENTS, "Attachments", ATTACHMENTS, Optional.empty()),
                        domain(Domain.AMMO, "Ammo", AMMO, Optional.empty())));
    }

    private static ResearchTechTreeDefinition.DomainDefinition domain(
            Domain domain,
            String title,
            ResourceLocation lane,
            Optional<ResourceLocation> privateIcon) {
        return new ResearchTechTreeDefinition.DomainDefinition(
                domain,
                title,
                Optional.empty(),
                privateIcon,
                lane,
                Tier.STARTER,
                List.of(new ResearchTechTreeDefinition.LaneDefinition(
                        lane,
                        title,
                        Optional.empty(),
                        privateIcon,
                        domain.ordinal())));
    }

    private static BlueprintProgressionConfigSnapshot config() {
        return new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                false,
                100,
                false,
                PROFILE);
    }

    private static BlueprintData data(ResourceLocation blueprintId, BlueprintKind kind) {
        return new BlueprintData(
                blueprintId.toString(),
                "name." + blueprintId.getPath(),
                "tooltip." + blueprintId.getPath(),
                id("test:recipe/" + blueprintId.getPath()),
                null,
                kind == BlueprintKind.GUN ? "pistol" : kind.name().toLowerCase(java.util.Locale.ROOT),
                id("test:slot/" + blueprintId.getPath()),
                kind);
    }

    private static String title(Tier tier) {
        String lower = tier.name().toLowerCase(java.util.Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
