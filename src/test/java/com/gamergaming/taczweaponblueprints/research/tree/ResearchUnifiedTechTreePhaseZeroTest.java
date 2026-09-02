package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.BrowseIntent;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ClassificationRole;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.DomainCanvasStructure;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;

class ResearchUnifiedTechTreePhaseZeroTest {
    @Test
    void legacyBrowseIntentsRemainWeaponOnlyWhileTechTreeAcceptsEveryKind() {
        for (BrowseIntent intent : new BrowseIntent[] {
                BrowseIntent.BRANCHES,
                BrowseIntent.ALL_WEAPONS}) {
            assertTrue(ResearchTechTreeContract.includesKind(intent, BlueprintKind.GUN));
            assertFalse(ResearchTechTreeContract.includesKind(intent, BlueprintKind.ATTACHMENT));
            assertFalse(ResearchTechTreeContract.includesKind(intent, BlueprintKind.AMMO));
        }
        for (BlueprintKind kind : BlueprintKind.values()) {
            assertTrue(ResearchTechTreeContract.includesKind(BrowseIntent.TECH_TREE, kind));
        }
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.includesKind(null, BlueprintKind.GUN));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.includesKind(BrowseIntent.TECH_TREE, null));
    }

    @Test
    void eachDomainIsOneUnifiedCanvasAndClassificationsAreOnlyHints() {
        ResearchTechTreeContract.UnifiedDomainPolicy policy =
                ResearchTechTreeContract.UNIFIED_DOMAIN_POLICY;
        assertEquals(DomainCanvasStructure.SINGLE_UNIFIED_GRAPH, policy.canvasStructure());
        assertEquals(ClassificationRole.AUTHORING_HINT_ONLY, policy.classificationRole());
        assertTrue(policy.oneDomainVisibleAtATime());
        assertTrue(policy.requiresAcyclicGraph());
        assertTrue(policy.requiresSingleWeakComponent());
        assertTrue(policy.requiresReachabilityFromEntryPoints());

        assertEquals(
                ResearchTechTreeContract.RelationshipSurface.INTERNAL_EDGE,
                ResearchTechTreeContract.relationshipSurface(
                        Domain.WEAPONS, Domain.WEAPONS));
    }

    @Test
    void pinnedDefaultTargetIsExactlyFiftyThreeNinetyFiveAndTwentyFour() {
        assertEquals(
                Map.of(
                        Domain.WEAPONS, 53,
                        Domain.ATTACHMENTS, 95,
                        Domain.AMMO, 24),
                ResearchTechTreeContract.DEFAULT_CONTENT_TARGETS);
        assertEquals(172, ResearchTechTreeContract.DEFAULT_CONTENT_TOTAL);

        ResearchTechTreeContract.DefaultContentCoverage complete =
                ResearchTechTreeContract.defaultContentCoverage(
                        ResearchTechTreeContract.DEFAULT_CONTENT_TARGETS);
        assertTrue(complete.complete());
        assertEquals(172, complete.publishedTotal());
        assertEquals(0, complete.missingTotal());
    }

    @Test
    void currentWeaponOnlyPublicationIsExplicitlyNotTheFinishedDefault() {
        ResearchTechTreeContract.DefaultContentCoverage current =
                ResearchTechTreeContract.defaultContentCoverage(Map.of(Domain.WEAPONS, 53));

        assertFalse(current.complete());
        assertEquals(53, current.publishedTotal());
        assertEquals(119, current.missingTotal());
        assertEquals(0, current.missingCounts().get(Domain.WEAPONS));
        assertEquals(95, current.missingCounts().get(Domain.ATTACHMENTS));
        assertEquals(24, current.missingCounts().get(Domain.AMMO));
        assertThrows(UnsupportedOperationException.class,
                () -> current.missingCounts().put(Domain.AMMO, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.defaultContentCoverage(
                        Map.of(Domain.AMMO, -1)));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchTechTreeContract.DefaultContentCoverage(
                        Map.of(
                                Domain.WEAPONS, 53,
                                Domain.ATTACHMENTS, 0,
                                Domain.AMMO, 0),
                        Map.of(
                                Domain.WEAPONS, 0,
                                Domain.ATTACHMENTS, 0,
                                Domain.AMMO, 0)));
    }

    @Test
    void authoritativeProgressionCanRemainInTierOrMoveUpButNeverDown() {
        assertTrue(ResearchTechTreeContract.tierTransitionAllowed(
                Tier.STARTER, Tier.STARTER));
        assertTrue(ResearchTechTreeContract.tierTransitionAllowed(
                Tier.STARTER, Tier.APEX));
        assertFalse(ResearchTechTreeContract.tierTransitionAllowed(
                Tier.ELITE, Tier.ADVANCED));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.tierTransitionAllowed(null, Tier.BASIC));
    }
}
