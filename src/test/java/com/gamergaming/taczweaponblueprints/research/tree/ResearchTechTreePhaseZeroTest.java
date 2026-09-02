package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchTreePresentationContract;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeProjection;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeProjectionCache;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;

class ResearchTechTreePhaseZeroTest {
    @Test
    void thirdBrowseIntentIsFrozenAndExposedAfterItsProjectionAndLayoutPhases() {
        assertEquals(
                List.of(
                        ResearchTechTreeContract.BrowseIntent.BRANCHES,
                        ResearchTechTreeContract.BrowseIntent.ALL_WEAPONS,
                        ResearchTechTreeContract.BrowseIntent.TECH_TREE),
                ResearchTechTreeContract.BROWSE_ORDER);
        assertEquals(
                List.of(
                        ResearchTreePresentationContract.BrowseView.BRANCHES,
                        ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                        ResearchTreePresentationContract.BrowseView.TECH_TREE),
                List.of(ResearchTreePresentationContract.BrowseView.values()));
    }

    @Test
    void currentBranchAndOverviewProjectionSemanticsRemainFrozen() {
        ResearchTreePublication publication = ResearchTreeRedesignFixture.connectedProgression();
        ResearchTreePresentation.Group firstGroup = publication.presentation().groups().get(0);
        ResearchTreeProjectionCache cache = new ResearchTreeProjectionCache();
        cache.update(publication);

        ResearchTreeProjection branch = cache.projection(
                ResearchTreePresentationContract.BrowseView.BRANCHES,
                firstGroup.id());
        assertEquals(firstGroup.id(), branch.groupId().orElseThrow());
        assertEquals(firstGroup.members().size(), branch.graph().nodes().size());
        assertEquals(1, branch.layout().groupRegions().size());

        ResearchTreeProjection overview = cache.projection(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                null);
        assertTrue(overview.groupId().isEmpty());
        assertEquals(publication.graph().nodes().size(), overview.graph().nodes().size());
        assertTrue(overview.layout().groupRegions().isEmpty());
        assertEquals(publication, cache.publication());
    }

    @Test
    void blueprintKindsMapToThreeStableDomains() {
        assertEquals(
                List.of(
                        ResearchTechTreeContract.Domain.WEAPONS,
                        ResearchTechTreeContract.Domain.ATTACHMENTS,
                        ResearchTechTreeContract.Domain.AMMO),
                ResearchTechTreeContract.DOMAIN_ORDER);
        assertEquals(ResearchTechTreeContract.Domain.WEAPONS,
                ResearchTechTreeContract.Domain.forKind(BlueprintKind.GUN));
        assertEquals(ResearchTechTreeContract.Domain.ATTACHMENTS,
                ResearchTechTreeContract.Domain.forKind(BlueprintKind.ATTACHMENT));
        assertEquals(ResearchTechTreeContract.Domain.AMMO,
                ResearchTechTreeContract.Domain.forKind(BlueprintKind.AMMO));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.Domain.forKind(null));
    }

    @Test
    void sixTiersRemainOrderedFromBottomToTop() {
        assertEquals(List.of(ResearchTechTreeContract.Tier.values()),
                ResearchTechTreeContract.TIER_ORDER);
        assertEquals(6, ResearchTechTreeContract.TIER_ORDER.size());
        assertEquals(ResearchTechTreeContract.Tier.STARTER,
                ResearchTechTreeContract.Tier.forScore(0));
        assertEquals(ResearchTechTreeContract.Tier.BASIC,
                ResearchTechTreeContract.Tier.forScore(17));
        assertEquals(ResearchTechTreeContract.Tier.ESTABLISHED,
                ResearchTechTreeContract.Tier.forScore(50));
        assertEquals(ResearchTechTreeContract.Tier.APEX,
                ResearchTechTreeContract.Tier.forScore(100));
        assertTrue(ResearchTechTreeContract.Tier.APEX
                .appearsAbove(ResearchTechTreeContract.Tier.ELITE));
        assertFalse(ResearchTechTreeContract.Tier.STARTER
                .appearsAbove(ResearchTechTreeContract.Tier.BASIC));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.Tier.forScore(101));
    }

    @Test
    void weaponRatingUsesDocumentedWeightsAndCapsAppealMovement() {
        ResearchTechTreeContract.WeaponRating balanced =
                new ResearchTechTreeContract.WeaponRating(50, 50, 50);
        assertEquals(50, balanced.mechanicalScore());
        assertEquals(50, balanced.weightedScore());
        assertEquals(ResearchTechTreeContract.Tier.ESTABLISHED,
                balanced.suggestedTier());

        ResearchTechTreeContract.WeaponRating appealOnly =
                new ResearchTechTreeContract.WeaponRating(0, 0, 100);
        assertEquals(0, appealOnly.mechanicalScore());
        assertEquals(25, appealOnly.weightedScore());
        assertEquals(ResearchTechTreeContract.Tier.BASIC,
                appealOnly.suggestedTier());

        ResearchTechTreeContract.WeaponRating dislikedApex =
                new ResearchTechTreeContract.WeaponRating(100, 100, 0);
        assertEquals(100, dislikedApex.mechanicalScore());
        assertEquals(75, dislikedApex.weightedScore());
        assertEquals(ResearchTechTreeContract.Tier.ELITE,
                dislikedApex.suggestedTier());

        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTechTreeContract.WeaponRating(-1, 50, 50));
    }

    @Test
    void gameplayAndPresentationConcernsHaveDistinctAuthorities() {
        assertEquals(ResearchTechTreeContract.AuthoritySource.RESEARCH_RULES,
                ResearchTechTreeContract.authorityFor(
                        ResearchTechTreeContract.Concern.PREREQUISITES));
        assertEquals(ResearchTechTreeContract.AuthoritySource.RESEARCH_RULES,
                ResearchTechTreeContract.authorityFor(
                        ResearchTechTreeContract.Concern.RESEARCH_ELIGIBILITY));
        assertEquals(ResearchTechTreeContract.AuthoritySource.RESEARCH_TREE_GROUPS,
                ResearchTechTreeContract.authorityFor(
                        ResearchTechTreeContract.Concern.BRANCH_MEMBERSHIP));
        assertEquals(ResearchTechTreeContract.AuthoritySource.TECH_TREE_DATA,
                ResearchTechTreeContract.authorityFor(
                        ResearchTechTreeContract.Concern.TECH_TIER));
        assertEquals(ResearchTechTreeContract.AuthoritySource.TECH_TREE_DATA,
                ResearchTechTreeContract.authorityFor(
                        ResearchTechTreeContract.Concern.TECH_RANK));
        assertEquals(ResearchTechTreeContract.AuthoritySource.AUTHORING_TOOL,
                ResearchTechTreeContract.authorityFor(
                        ResearchTechTreeContract.Concern.SCORE_RECOMMENDATION));
        assertEquals(ResearchTechTreeContract.AuthoritySource.CLIENT_STATE,
                ResearchTechTreeContract.authorityFor(
                        ResearchTechTreeContract.Concern.CAMERA));
    }

    @Test
    void hiddenIdentityCannotPublishAClassifyingDomain() {
        assertEquals(
                ResearchTechTreeContract.Domain.WEAPONS,
                ResearchTechTreeContract.publicDomain(BlueprintKind.GUN, true).orElseThrow());
        assertTrue(ResearchTechTreeContract
                .publicDomain(BlueprintKind.GUN, false)
                .isEmpty());
    }

    @Test
    void domainFallbackAndCrossDomainRelationshipsAreDeterministic() {
        Set<ResearchTechTreeContract.Domain> ammoOnly =
                EnumSet.of(ResearchTechTreeContract.Domain.AMMO);
        assertEquals(
                ResearchTechTreeContract.Domain.AMMO,
                ResearchTechTreeContract.fallbackDomain(
                        ammoOnly,
                        ResearchTechTreeContract.Domain.ATTACHMENTS).orElseThrow());

        Set<ResearchTechTreeContract.Domain> weaponsAndAmmo = EnumSet.of(
                ResearchTechTreeContract.Domain.WEAPONS,
                ResearchTechTreeContract.Domain.AMMO);
        assertEquals(
                ResearchTechTreeContract.Domain.WEAPONS,
                ResearchTechTreeContract.fallbackDomain(weaponsAndAmmo, null).orElseThrow());
        assertTrue(ResearchTechTreeContract.fallbackDomain(Set.of(), null).isEmpty());

        assertEquals(
                ResearchTechTreeContract.RelationshipSurface.INTERNAL_EDGE,
                ResearchTechTreeContract.relationshipSurface(
                        ResearchTechTreeContract.Domain.WEAPONS,
                        ResearchTechTreeContract.Domain.WEAPONS));
        assertEquals(
                ResearchTechTreeContract.RelationshipSurface.BOUNDARY_PORTAL,
                ResearchTechTreeContract.relationshipSurface(
                        ResearchTechTreeContract.Domain.WEAPONS,
                        ResearchTechTreeContract.Domain.AMMO));
    }

    @Test
    void unratedFallbackCannotChangeGameplayTopologyOrEligibility() {
        assertFalse(ResearchTechTreeContract.UNRATED_FALLBACK.createsPrerequisite());
        assertFalse(ResearchTechTreeContract.UNRATED_FALLBACK.changesResearchEligibility());
        assertTrue(ResearchTechTreeContract.UNRATED_FALLBACK.authoredPlacementOverrides());
    }
}
