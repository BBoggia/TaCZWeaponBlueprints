package com.gamergaming.taczweaponblueprints.loot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy.TierSource;

import net.minecraft.resources.ResourceLocation;

class BlueprintFragmentLootResolverTest {

    @Test
    void filtersToIncludedFragmentEnabledPoliciesAndPublishesThresholdEvidence() {
        ResourceLocation first = id("test:first");
        ResourceLocation disabled = id("test:disabled");
        ResourceLocation omitted = id("test:omitted");
        Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> policies = Map.of(
                first, policy(first, ResearchWorkbenchTier.TIER_2, 11, true, true),
                disabled, policy(disabled, ResearchWorkbenchTier.TIER_1, 0, false, false));

        BlueprintFragmentLootResolver.Plan plan = BlueprintFragmentLootResolver.resolve(
                List.of(target(omitted, 3), target(disabled, 2), target(first, 1)),
                policies,
                2_000,
                Optional.empty());

        assertTrue(plan.policyAvailable());
        assertTrue(plan.canReplace());
        assertFalse(plan.playerAware());
        assertEquals(List.of(first), plan.candidates().stream()
                .map(BlueprintFragmentLootResolver.Candidate::blueprintId).toList());
        assertEquals(11, plan.candidates().get(0).threshold());
        assertEquals(ResearchWorkbenchTier.TIER_2, plan.candidates().get(0).tier());
        assertTrue(plan.candidates().get(0).exactThreshold());
        assertEquals(Map.of(11, 1), plan.thresholdCounts());
    }

    @Test
    void playerContextStronglyPrefersUnlearnedTargetsWithoutRemovingLearnedValue() {
        ResourceLocation learned = id("test:a_learned");
        ResourceLocation unlearned = id("test:b_unlearned");
        Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> policies = Map.of(
                learned, policy(learned, ResearchWorkbenchTier.TIER_1, 5, true, false),
                unlearned, policy(unlearned, ResearchWorkbenchTier.TIER_1, 5, true, false));

        BlueprintFragmentLootResolver.Plan playerAware = BlueprintFragmentLootResolver.resolve(
                List.of(target(learned, 1), target(unlearned, 1)),
                policies,
                10_000,
                Optional.of(Set.of(learned.toString())));
        BlueprintFragmentLootResolver.Plan generatedContainer = BlueprintFragmentLootResolver.resolve(
                List.of(target(learned, 1), target(unlearned, 1)),
                policies,
                10_000,
                Optional.empty());

        assertTrue(playerAware.playerAware());
        assertEquals(BlueprintFragmentLootResolver.LEARNED_WEIGHT_MULTIPLIER,
                playerAware.candidates().get(0).effectiveWeight());
        assertEquals(BlueprintFragmentLootResolver.UNLEARNED_WEIGHT_MULTIPLIER,
                playerAware.candidates().get(1).effectiveWeight());
        assertEquals(learned, playerAware.select(0.10).orElseThrow());
        assertEquals(unlearned, playerAware.select(0.12).orElseThrow());
        assertFalse(generatedContainer.playerAware());
        assertEquals(learned, generatedContainer.select(0.49).orElseThrow());
        assertEquals(unlearned, generatedContainer.select(0.50).orElseThrow());
    }

    @Test
    void replacementBoundaryAndExpectedValueAreExact() {
        ResourceLocation first = id("test:first");
        BlueprintFragmentLootResolver.Plan plan = BlueprintFragmentLootResolver.resolve(
                List.of(target(first, 1)),
                Map.of(first, policy(first, ResearchWorkbenchTier.TIER_1, 5, true, false)),
                2_000,
                Optional.empty());

        assertTrue(plan.shouldReplace(0));
        assertTrue(plan.shouldReplace(1_999));
        assertFalse(plan.shouldReplace(2_000));
        assertEquals(0.4, plan.expectedFragments(2.0));
        assertThrows(IllegalArgumentException.class, () -> plan.shouldReplace(-1));
        assertThrows(IllegalArgumentException.class, () -> plan.shouldReplace(10_000));
    }

    @Test
    void duplicateCandidateWeightsMergeWithoutDuplicatingTargets() {
        ResourceLocation first = id("test:first");
        BlueprintFragmentLootResolver.Plan plan = BlueprintFragmentLootResolver.resolve(
                List.of(target(first, 1.5), target(first, 2.5)),
                Map.of(first, policy(first, ResearchWorkbenchTier.TIER_3, 15, true, false)),
                5_000,
                Optional.empty());

        assertEquals(1, plan.candidates().size());
        assertEquals(4.0, plan.candidates().get(0).baseWeight());
        assertEquals(4.0, plan.candidates().get(0).effectiveWeight());
    }

    @Test
    void disabledEmptyAndInvalidPlansFailSafely() {
        BlueprintFragmentLootResolver.Plan disabled = BlueprintFragmentLootResolver.resolve(
                List.of(), Map.of(), 0, Optional.empty());
        BlueprintFragmentLootResolver.Plan empty = BlueprintFragmentLootResolver.resolve(
                List.of(target(id("test:missing"), 1)), Map.of(), 2_000, Optional.empty());

        assertFalse(disabled.canReplace());
        assertFalse(empty.canReplace());
        assertTrue(empty.select(0.5).isEmpty());
        assertEquals(0.0, empty.expectedFragments(4));
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintFragmentLootResolver.resolve(List.of(), Map.of(), -1, Optional.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> new BlueprintFragmentLootResolver.WeightedTarget(id("test:bad"), Double.NaN));
    }

    @Test
    void simulationsStayNearConfiguredShareForSmallMediumAndLargeCatalogs() {
        for (int size : List.of(8, 256, 4_096)) {
            List<BlueprintFragmentLootResolver.WeightedTarget> targets =
                    java.util.stream.IntStream.range(0, size)
                            .mapToObj(index -> target(id("test:item_" + index), 1 + index % 7))
                            .toList();
            Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> policies = new LinkedHashMap<>();
            targets.forEach(target -> policies.put(
                    target.blueprintId(),
                    policy(target.blueprintId(), tierFor(target.blueprintId()),
                            thresholdFor(target.blueprintId()), true, false)));
            BlueprintFragmentLootResolver.Plan plan = BlueprintFragmentLootResolver.resolve(
                    targets, policies, 2_000, Optional.empty());
            Random random = new Random(0x5EEDL + size);
            int fragments = 0;
            int rolls = 100_000;
            for (int roll = 0; roll < rolls; roll++) {
                if (plan.shouldReplace(random.nextInt(BlueprintFragmentLootResolver.BASIS_POINTS))) {
                    assertTrue(plan.select(random.nextDouble()).isPresent());
                    fragments++;
                }
            }
            double observed = fragments / (double) rolls;
            assertTrue(Math.abs(observed - 0.20) < 0.01,
                    () -> "unexpected fragment share for size " + size + ": " + observed);
            assertEquals(size, plan.candidates().size());
        }
    }

    private static BlueprintFragmentLootResolver.WeightedTarget target(
            ResourceLocation id,
            double weight) {
        return new BlueprintFragmentLootResolver.WeightedTarget(id, weight);
    }

    private static ResolvedBlueprintProgressionPolicy policy(
            ResourceLocation id,
            ResearchWorkbenchTier tier,
            int threshold,
            boolean enabled,
            boolean exact) {
        BlueprintFragmentPolicy fragments = enabled
                ? new BlueprintFragmentPolicy(
                        BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                        threshold,
                        Math.max(1_000, threshold),
                        BlueprintFragmentDiscount.percentage(2_500),
                        1)
                : BlueprintFragmentPolicy.DISABLED;
        return new ResolvedBlueprintProgressionPolicy(
                id("test:profile"),
                id,
                tier,
                fragments,
                ProgressionGateRequirements.EMPTY,
                TierSource.FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                exact);
    }

    private static ResearchWorkbenchTier tierFor(ResourceLocation id) {
        return switch (Math.floorMod(id.getPath().hashCode(), 3)) {
            case 0 -> ResearchWorkbenchTier.TIER_1;
            case 1 -> ResearchWorkbenchTier.TIER_2;
            default -> ResearchWorkbenchTier.TIER_3;
        };
    }

    private static int thresholdFor(ResourceLocation id) {
        return switch (tierFor(id)) {
            case TIER_1 -> 5;
            case TIER_2 -> 10;
            case TIER_3 -> 15;
        };
    }

    private static ResourceLocation id(String value) {
        ResourceLocation parsed = ResourceLocation.tryParse(value);
        if (parsed == null) {
            throw new IllegalArgumentException(value);
        }
        return parsed;
    }
}
