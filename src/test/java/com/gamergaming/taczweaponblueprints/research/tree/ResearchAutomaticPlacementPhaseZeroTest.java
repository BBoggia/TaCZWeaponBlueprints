package com.gamergaming.taczweaponblueprints.research.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.MechanicalRating;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;

class ResearchAutomaticPlacementPhaseZeroTest {
    @Test
    void automaticRatingIsMechanicalVersionedAndContainsNoAppealInput() {
        MechanicalRating balanced = MechanicalRating.current(50, 50, 100);
        assertEquals(50, balanced.score());
        assertEquals(Tier.ESTABLISHED, balanced.suggestedTier());
        assertEquals(ResearchTechTreeContract.AUTOMATIC_FORMULA_VERSION,
                balanced.formulaVersion());
        assertEquals(ResearchTechTreeContract.AUTOMATIC_REFERENCE_VERSION,
                balanced.referenceVersion());

        MechanicalRating combatOnly = MechanicalRating.current(100, 0, 80);
        MechanicalRating utilityOnly = MechanicalRating.current(0, 100, 80);
        assertEquals(75, combatOnly.score());
        assertEquals(25, utilityOnly.score());
        assertEquals(Tier.ELITE, combatOnly.suggestedTier());
        assertEquals(Tier.BASIC, utilityOnly.suggestedTier());

        assertThrows(IllegalArgumentException.class,
                () -> MechanicalRating.current(50, 50, 101));
        assertThrows(IllegalArgumentException.class,
                () -> new MechanicalRating(50, 50, 50, "bad version", "reference"));
    }

    @Test
    void scoreBandsDivideIntoStableBottomToTopProgressionLevels() {
        assertEquals(3, ResearchTechTreeContract.DEFAULT_LEVELS_PER_TIER);
        assertEquals(0, ResearchTechTreeContract.levelForScore(0, 3));
        assertEquals(1, ResearchTechTreeContract.levelForScore(6, 3));
        assertEquals(2, ResearchTechTreeContract.levelForScore(12, 3));
        assertEquals(0, ResearchTechTreeContract.levelForScore(17, 3));
        assertEquals(2, ResearchTechTreeContract.levelForScore(33, 3));
        assertEquals(0, ResearchTechTreeContract.levelForScore(85, 3));
        assertEquals(2, ResearchTechTreeContract.levelForScore(100, 3));

        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.levelForScore(50, 0));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.levelForScore(50, 6));
    }

    @Test
    void automaticEdgeSpanStaysAtTwoUntilTheDepthLimitMakesItImpossible() {
        assertEquals(2, ResearchTechTreeContract.automaticEdgeRankSpanLimit(0, 64));
        assertEquals(2, ResearchTechTreeContract.automaticEdgeRankSpanLimit(129, 64));
        assertEquals(3, ResearchTechTreeContract.automaticEdgeRankSpanLimit(130, 64));
        assertEquals(4, ResearchTechTreeContract.automaticEdgeRankSpanLimit(205, 64));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.automaticEdgeRankSpanLimit(-1, 64));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchTechTreeContract.automaticEdgeRankSpanLimit(10, 0));
    }

    @Test
    void progressionCoordinatesExcludeClientOnlyWrapRows() {
        ProgressionPosition starterTop = new ProgressionPosition(Tier.STARTER, 2, 100);
        ProgressionPosition basicBottom = new ProgressionPosition(Tier.BASIC, 0, 0);
        assertTrue(ResearchTechTreeContract.progressionTransitionAllowed(
                starterTop, basicBottom));

        ProgressionPosition basicLevelOne = new ProgressionPosition(Tier.BASIC, 1, 100);
        ProgressionPosition basicLevelTwo = new ProgressionPosition(Tier.BASIC, 2, 0);
        assertTrue(ResearchTechTreeContract.progressionTransitionAllowed(
                basicLevelOne, basicLevelTwo));

        ProgressionPosition earlierSibling = new ProgressionPosition(Tier.BASIC, 1, 10);
        ProgressionPosition laterSibling = new ProgressionPosition(Tier.BASIC, 1, 20);
        assertTrue(ResearchTechTreeContract.progressionTransitionAllowed(
                earlierSibling, laterSibling));
        assertFalse(ResearchTechTreeContract.progressionTransitionAllowed(
                laterSibling, earlierSibling));
        assertFalse(ResearchTechTreeContract.progressionTransitionAllowed(
                laterSibling, laterSibling));

        assertThrows(IllegalArgumentException.class,
                () -> new ProgressionPosition(Tier.BASIC, 5, 0));
    }

    @Test
    void placementOriginsFreezeTheFutureOverrideOrder() {
        assertEquals(
                List.of(
                        PlacementOrigin.EXACT,
                        PlacementOrigin.TAG,
                        PlacementOrigin.SELECTOR,
                        PlacementOrigin.AUTOMATIC,
                        PlacementOrigin.LEGACY_FALLBACK),
                java.util.Arrays.stream(PlacementOrigin.values())
                        .sorted(java.util.Comparator.comparingInt(
                                PlacementOrigin::precedence).reversed())
                        .toList());
        assertTrue(PlacementOrigin.EXACT.outranks(PlacementOrigin.TAG));
        assertTrue(PlacementOrigin.SELECTOR.outranks(PlacementOrigin.AUTOMATIC));
        assertTrue(PlacementOrigin.AUTOMATIC.outranks(PlacementOrigin.LEGACY_FALLBACK));
        assertTrue(PlacementOrigin.EXACT.authored());
        assertTrue(PlacementOrigin.TAG.authored());
        assertTrue(PlacementOrigin.SELECTOR.authored());
        assertFalse(PlacementOrigin.AUTOMATIC.authored());
        assertFalse(PlacementOrigin.LEGACY_FALLBACK.authored());
    }

    @Test
    void automaticModesRemainBackwardCompatibleUntilExplicitlyEnabled() {
        assertFalse(AutomaticPlacementMode.INDEPENDENT.assignsPlacement());
        assertFalse(AutomaticPlacementMode.INDEPENDENT.createsPrerequisite());
        assertTrue(AutomaticPlacementMode.DISTRIBUTED.assignsPlacement());
        assertFalse(AutomaticPlacementMode.DISTRIBUTED.createsPrerequisite());
        assertTrue(AutomaticPlacementMode.CONNECTED.assignsPlacement());
        assertTrue(AutomaticPlacementMode.CONNECTED.createsPrerequisite());
    }
}
