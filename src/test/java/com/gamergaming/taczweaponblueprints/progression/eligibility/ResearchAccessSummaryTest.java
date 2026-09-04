package com.gamergaming.taczweaponblueprints.progression.eligibility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

class ResearchAccessSummaryTest {
    @Test
    void summariesExposeOnlyTheMinimumClientFacingReason() {
        ResearchAccessSummary tier = ResearchAccessSummary.workbench(
                ResearchWorkbenchTier.TIER_1,
                ResearchWorkbenchTier.TIER_2);
        ResearchAccessSummary gate = ResearchAccessSummary.gate(
                "gate.example.complete_trial");

        assertTrue(tier.blocked());
        assertEquals(Optional.of(ResearchWorkbenchTier.TIER_2), tier.requiredTier());
        assertTrue(tier.messageKey().isEmpty());
        assertTrue(gate.blocked());
        assertEquals(Optional.of("gate.example.complete_trial"), gate.messageKey());
        assertTrue(gate.currentTier().isEmpty());
        assertFalse(ResearchAccessSummary.NONE.blocked());
    }

    @Test
    void malformedOrAlreadySatisfiedReasonsAreRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                ResearchAccessSummary.workbench(
                        ResearchWorkbenchTier.TIER_3,
                        ResearchWorkbenchTier.TIER_2));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchAccessSummary(
                        ResearchAccessSummary.Kind.PROGRESSION_GATE,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                ResearchAccessSummary.gate("not a translation key"));
    }
}
