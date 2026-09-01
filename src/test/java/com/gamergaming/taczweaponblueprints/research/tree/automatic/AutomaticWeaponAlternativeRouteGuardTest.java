package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

class AutomaticWeaponAlternativeRouteGuardTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation ROOT = id("test:root");
    private static final ResourceLocation LEFT = id("test:left");
    private static final ResourceLocation RIGHT = id("test:right");
    private static final ResourceLocation EXPENSIVE = id("test:expensive");
    private static final ResourceLocation MULTI = id("test:multi");

    @Test
    void acceptsEqualCostRoutesWithoutPricingTheirUnion() {
        Map<ResourceLocation, BlueprintResearchPolicy> policies = Map.of(
                ROOT, policy(ROOT, 10, ResearchRequirements.EMPTY),
                LEFT, policy(LEFT, 20, singleton(ROOT)),
                RIGHT, policy(RIGHT, 20, singleton(ROOT)));

        var review = AutomaticWeaponAlternativeRouteGuard.review(
                List.of(LEFT), RIGHT, Map.of(), policies);

        assertTrue(review.accepted());
        assertTrue(review.exact());
        assertEquals(30L, review.existingRouteCostLowerBound());
        assertEquals(30L, review.candidateRouteCostUpperBound());
        assertEquals(10_000L, review.routeCostRatioUpperBoundBasisPoints());
        assertEquals(3_333, review.mandatoryAncestryOverlapBasisPoints());
        assertEquals(2, review.divergentMandatoryNodeCount());
    }

    @Test
    void rejectsOnlyAProvenExtremeRouteCostMismatch() {
        Map<ResourceLocation, BlueprintResearchPolicy> policies = Map.of(
                LEFT, policy(LEFT, 10, ResearchRequirements.EMPTY),
                EXPENSIVE, policy(EXPENSIVE, 90, ResearchRequirements.EMPTY));

        var review = AutomaticWeaponAlternativeRouteGuard.review(
                List.of(LEFT), EXPENSIVE, Map.of(), policies);

        assertFalse(review.accepted());
        assertEquals(
                AutomaticWeaponPrerequisiteDecision.AlternativeRouteOutcome
                        .REJECTED_PROVEN_COST_IMBALANCE,
                review.outcome());
        assertEquals(90_000L, review.routeCostRatioLowerBoundBasisPoints());
    }

    @Test
    void retainsUncertainAuthoredAndOfOrRouteWithHonestBounds() {
        ResourceLocation otherRoot = id("test:other_root");
        Map<ResourceLocation, BlueprintResearchPolicy> policies = Map.of(
                ROOT, policy(ROOT, 10, ResearchRequirements.EMPTY),
                otherRoot, policy(otherRoot, 10, ResearchRequirements.EMPTY),
                MULTI, policy(MULTI, 5, new ResearchRequirements(List.of(
                        ResearchPrerequisiteGroup.singleton(ROOT),
                        ResearchPrerequisiteGroup.singleton(otherRoot)))),
                RIGHT, policy(RIGHT, 20, ResearchRequirements.EMPTY));

        var review = AutomaticWeaponAlternativeRouteGuard.review(
                List.of(MULTI), RIGHT, Map.of(), policies);

        assertTrue(review.accepted());
        assertFalse(review.exact());
        assertEquals(15L, review.existingRouteCostLowerBound());
        assertEquals(25L, review.existingRouteCostUpperBound());
        assertEquals(
                AutomaticWeaponPrerequisiteDecision.AlternativeRouteOutcome
                        .ACCEPTED_BOUNDED,
                review.outcome());
    }

    @Test
    void pricesGeneratedParentPairsAsOneInclusiveOrGroup() {
        Map<ResourceLocation, BlueprintResearchPolicy> policies = Map.of(
                LEFT, policy(LEFT, 10, ResearchRequirements.EMPTY),
                EXPENSIVE, policy(EXPENSIVE, 100, ResearchRequirements.EMPTY),
                MULTI, policy(MULTI, 0, ResearchRequirements.EMPTY),
                RIGHT, policy(RIGHT, 10, ResearchRequirements.EMPTY));

        var review = AutomaticWeaponAlternativeRouteGuard.review(
                List.of(MULTI),
                RIGHT,
                Map.of(MULTI, List.of(LEFT, EXPENSIVE)),
                policies);

        assertTrue(review.accepted());
        assertTrue(review.exact());
        assertEquals(10L, review.existingRouteCostLowerBound());
        assertEquals(10L, review.existingRouteCostUpperBound());
        assertEquals(10_000L, review.routeCostRatioUpperBoundBasisPoints());
    }

    @Test
    void pricesHybridGeneratedAncestryFromCanonicalAndOfOrGroups() {
        Map<ResourceLocation, BlueprintResearchPolicy> policies = Map.of(
                ROOT, policy(ROOT, 5, ResearchRequirements.EMPTY),
                LEFT, policy(LEFT, 10, ResearchRequirements.EMPTY),
                EXPENSIVE, policy(EXPENSIVE, 100, ResearchRequirements.EMPTY),
                MULTI, policy(MULTI, 0, ResearchRequirements.EMPTY),
                RIGHT, policy(RIGHT, 12, ResearchRequirements.EMPTY));
        Map<ResourceLocation, List<ResourceLocation>> generated = Map.of(
                MULTI, List.of(LEFT, EXPENSIVE, ROOT));
        Map<ResourceLocation, ResearchRequirements> requirements = Map.of(
                MULTI,
                new ResearchRequirements(List.of(
                        new ResearchPrerequisiteGroup(List.of(LEFT, EXPENSIVE)),
                        ResearchPrerequisiteGroup.singleton(ROOT))));

        var review = AutomaticWeaponAlternativeRouteGuard.review(
                List.of(MULTI), RIGHT, generated, requirements, policies);

        assertTrue(review.accepted());
        assertFalse(review.exact());
        assertEquals(10L, review.existingRouteCostLowerBound());
        assertEquals(15L, review.existingRouteCostUpperBound());
        assertEquals(
                AutomaticWeaponPrerequisiteDecision.AlternativeRouteOutcome
                        .ACCEPTED_BOUNDED,
                review.outcome());
    }

    private static ResearchRequirements singleton(ResourceLocation prerequisite) {
        return new ResearchRequirements(List.of(
                ResearchPrerequisiteGroup.singleton(prerequisite)));
    }

    private static BlueprintResearchPolicy policy(
            ResourceLocation id,
            int cost,
            ResearchRequirements requirements) {
        return new BlueprintResearchPolicy(
                id,
                PROFILE,
                true,
                false,
                true,
                false,
                true,
                0,
                10_000,
                false,
                true,
                true,
                JournalVisibility.FULL,
                true,
                false,
                false,
                0,
                new BlueprintResearchCost(cost, List.of()),
                false,
                requirements,
                requirements.conservativeAlternatives(),
                true,
                false,
                Optional.empty(),
                BlueprintResearchTarget.MatchSpecificity.NONE);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
