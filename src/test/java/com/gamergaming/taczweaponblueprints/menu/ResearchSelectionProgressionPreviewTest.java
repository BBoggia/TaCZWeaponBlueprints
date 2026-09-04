package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.DisclosedCraftingAccess;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingDisposition;

class ResearchSelectionProgressionPreviewTest {
    @Test
    void fragmentDisplayClampsRetainedOverflowWithoutLosingCompletionState() {
        var progress = new ResearchSelectionProgressionPreview.FragmentProgress(
                9,
                5,
                BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                true);

        assertEquals(5, progress.displayedArchived());
        assertTrue(progress.complete());
    }

    @Test
    void rejectsPartialTierContextsAndImpossibleDiscountClaims() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchSelectionProgressionPreview(
                        Optional.of(ResearchWorkbenchTier.TIER_1),
                        Optional.empty(),
                        Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchSelectionProgressionPreview.FragmentProgress(
                        2,
                        5,
                        BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                        true));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchSelectionProgressionPreview.FragmentProgress(
                        5,
                        5,
                        BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT,
                        true));
        assertThrows(IllegalArgumentException.class, () ->
                new DisclosedCraftingAccess(
                        BlueprintCraftingDisposition.TIERED, Optional.empty()));
        assertThrows(IllegalArgumentException.class, () ->
                new DisclosedCraftingAccess(
                        BlueprintCraftingDisposition.UNRESTRICTED,
                        Optional.of(ResearchWorkbenchTier.TIER_1)));
    }
}
