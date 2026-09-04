package com.gamergaming.taczweaponblueprints.progression.fragment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy.CompletionMode;

class BlueprintFragmentPolicyTest {
    @Test
    void fixedAndPercentageDiscountsAreBoundedAndConservative() {
        BlueprintFragmentDiscount fixed = BlueprintFragmentDiscount.fixed(20);
        BlueprintFragmentDiscount half = BlueprintFragmentDiscount.percentage(5_000);

        assertEquals(20, fixed.discountFor(100));
        assertEquals(80, fixed.applyTo(100));
        assertEquals(10, fixed.discountFor(10));
        assertEquals(0, fixed.applyTo(10));
        assertEquals(1, half.discountFor(3), "percentage removal rounds down");
        assertEquals(2, half.applyTo(3));
        assertEquals(0, BlueprintFragmentDiscount.NONE.discountFor(100));
        assertEquals(100, BlueprintFragmentDiscount.NONE.applyTo(100));
    }

    @Test
    void percentageMathCannotOverflowAtTheProgressionMaximum() {
        int maximum = PlayerProgressionLimits.MAX_RESEARCH_POINTS;

        assertEquals(maximum, BlueprintFragmentDiscount.percentage(10_000)
                .discountFor(maximum));
        assertEquals(333_300_000, BlueprintFragmentDiscount.percentage(3_333)
                .discountFor(maximum));
        assertEquals(0, BlueprintFragmentDiscount.fixed(maximum).applyTo(maximum));
    }

    @Test
    void malformedDiscountsAndCostsAreRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new BlueprintFragmentDiscount(null, 0));
        assertThrows(IllegalArgumentException.class,
                () -> new BlueprintFragmentDiscount(BlueprintFragmentDiscount.Mode.NONE, 1));
        assertThrows(IllegalArgumentException.class, () -> BlueprintFragmentDiscount.fixed(0));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintFragmentDiscount.fixed(
                        PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintFragmentDiscount.percentage(0));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintFragmentDiscount.percentage(10_001));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintFragmentDiscount.NONE.applyTo(-1));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintFragmentDiscount.NONE.applyTo(
                        PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1));
    }

    @Test
    void rawCountsDeriveCompleteSetsAndRemaindersWithoutRewritingSavedState() {
        BlueprintFragmentProgress empty = new BlueprintFragmentProgress(0, 10);
        BlueprintFragmentProgress partial = new BlueprintFragmentProgress(9, 10);
        BlueprintFragmentProgress complete = new BlueprintFragmentProgress(20, 10);

        assertEquals(0, empty.completedSets());
        assertEquals(10, empty.fragmentsToNextSet());
        assertFalse(empty.hasCompleteSet());
        assertEquals(0, partial.completedSets());
        assertEquals(9, partial.remainder());
        assertEquals(1, partial.fragmentsToNextSet());
        assertEquals(2, complete.completedSets());
        assertEquals(0, complete.remainder());
        assertEquals(0, complete.fragmentsToNextSet());
        assertTrue(complete.hasCompleteSet());
        assertEquals(new BlueprintFragmentProgress(10, 10), complete.consumeSets(1));
        assertEquals(new BlueprintFragmentProgress(0, 10), complete.consumeSets(2));

        assertThrows(IllegalArgumentException.class, () -> complete.consumeSets(-1));
        assertThrows(IllegalArgumentException.class, () -> complete.consumeSets(3));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintFragmentProgress(-1, 10));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintFragmentProgress(1, 0));
    }

    @Test
    void policyModesCannotCombineConflictingCompletionBenefits() {
        assertFalse(BlueprintFragmentPolicy.DISABLED.enabled());
        assertThrows(IllegalStateException.class,
                () -> BlueprintFragmentPolicy.DISABLED.progress(0));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintFragmentPolicy(
                CompletionMode.DISABLED,
                10,
                100,
                BlueprintFragmentDiscount.NONE,
                0));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintFragmentPolicy(
                CompletionMode.TARGETED_RESEARCH_BOOST,
                10,
                100,
                BlueprintFragmentDiscount.NONE,
                0));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintFragmentPolicy(
                CompletionMode.RECONSTRUCT_BLUEPRINT,
                10,
                100,
                BlueprintFragmentDiscount.fixed(1),
                0));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintFragmentPolicy(
                null,
                10,
                100,
                BlueprintFragmentDiscount.fixed(1),
                0));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintFragmentPolicy(
                CompletionMode.RECONSTRUCT_BLUEPRINT,
                10,
                100,
                null,
                0));
    }

    @Test
    void thresholdCapsAndLearnedTargetReturnsAreStrictlyBounded() {
        assertThrows(IllegalArgumentException.class, () -> boost(0, 10, 0));
        assertThrows(IllegalArgumentException.class, () -> boost(11, 10, 0));
        assertThrows(IllegalArgumentException.class,
                () -> boost(BlueprintFragmentPolicy.MAX_THRESHOLD + 1,
                        BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS,
                        0));
        assertThrows(IllegalArgumentException.class,
                () -> boost(10, BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS + 1, 0));
        assertThrows(IllegalArgumentException.class, () -> boost(10, 100, -1));
        assertThrows(IllegalArgumentException.class,
                () -> boost(10, 100, PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1));

        BlueprintFragmentPolicy maximum = boost(
                BlueprintFragmentPolicy.MAX_THRESHOLD,
                BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS,
                PlayerProgressionLimits.MAX_RESEARCH_POINTS);
        assertTrue(maximum.enabled());
    }

    @Test
    void completeSetDiscountAndArchivePlanningArePureAndOverflowSafe() {
        BlueprintFragmentPolicy policy = boost(10, 25, 1);

        assertEquals(0, policy.researchDiscountFor(100, 9));
        assertEquals(25, policy.researchDiscountFor(100, 10));
        assertEquals(
                new BlueprintFragmentPolicy.ArchiveResult(20, 10, 5, 5, 25),
                policy.archive(20, 10));
        assertEquals(
                new BlueprintFragmentPolicy.ArchiveResult(
                        25,
                        Integer.MAX_VALUE,
                        0,
                        Integer.MAX_VALUE,
                        25),
                policy.archive(25, Integer.MAX_VALUE));
        assertEquals(
                new BlueprintFragmentPolicy.ArchiveResult(30, 5, 0, 5, 30),
                policy.archive(30, 5),
                "lowering a cap does not rewrite retained raw progress");

        BlueprintFragmentPolicy reconstruction = new BlueprintFragmentPolicy(
                CompletionMode.RECONSTRUCT_BLUEPRINT,
                10,
                100,
                BlueprintFragmentDiscount.NONE,
                1);
        assertEquals(0, reconstruction.researchDiscountFor(100, 10));
        assertThrows(IllegalArgumentException.class, () -> policy.archive(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> policy.archive(1, -1));
        assertThrows(IllegalArgumentException.class,
                () -> new BlueprintFragmentPolicy.ArchiveResult(
                        BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS + 1,
                        0,
                        0,
                        0,
                        BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new BlueprintFragmentPolicy.ArchiveResult(
                        BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS,
                        1,
                        1,
                        0,
                        BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS + 1));
        assertEquals(policy.progress(0), policy.progress(0).consumeSets(0));
    }

    private static BlueprintFragmentPolicy boost(int threshold, int cap, int learnedPoints) {
        return new BlueprintFragmentPolicy(
                CompletionMode.TARGETED_RESEARCH_BOOST,
                threshold,
                cap,
                BlueprintFragmentDiscount.percentage(2_500),
                learnedPoints);
    }
}
