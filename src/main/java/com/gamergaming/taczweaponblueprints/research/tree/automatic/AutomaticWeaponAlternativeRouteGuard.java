package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

/**
 * Group-aware safety review for a proposed generated OR alternative.
 *
 * <p>The legacy guard prices the union of two closures because both parents
 * are mandatory under {@code legacy_and}. That calculation is deliberately
 * not reused here: grouped routes ask the player to buy one route. This guard
 * therefore compares safe minimum-route bounds, records ancestry diversity,
 * and rejects only a cost imbalance proven by the lower bound. Ambiguous
 * authored AND-of-OR ancestry remains eligible and is marked bounded.</p>
 */
public final class AutomaticWeaponAlternativeRouteGuard {
    public static final String CONTRACT = "group_aware_route_balance_v1";
    public static final long MAXIMUM_PROVEN_ROUTE_COST_RATIO_BASIS_POINTS =
            80_000L;

    private static final Comparator<ResourceLocation> ID_ORDER =
            Comparator.comparing(ResourceLocation::toString);

    private AutomaticWeaponAlternativeRouteGuard() {
    }

    public static AutomaticWeaponPrerequisiteDecision.AlternativeRouteReview review(
            List<ResourceLocation> selected,
            ResourceLocation candidate,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, BlueprintResearchPolicy> policies) {
        Map<ResourceLocation, ResearchRequirements> generatedRequirements =
                new LinkedHashMap<>();
        if (generated != null) {
            generated.forEach((dependent, parents) -> generatedRequirements.put(
                    dependent,
                    parents == null || parents.isEmpty()
                            ? ResearchRequirements.EMPTY
                            : new ResearchRequirements(List.of(
                                    new ResearchPrerequisiteGroup(parents)))));
        }
        return review(
                selected,
                candidate,
                generated,
                generatedRequirements,
                policies);
    }

    /** Reviews an alternative against the canonical generated AND-of-OR graph. */
    public static AutomaticWeaponPrerequisiteDecision.AlternativeRouteReview review(
            List<ResourceLocation> selected,
            ResourceLocation candidate,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, ResearchRequirements> generatedRequirements,
            Map<ResourceLocation, BlueprintResearchPolicy> policies) {
        if (selected == null || selected.isEmpty() || candidate == null
                || generated == null || generatedRequirements == null || policies == null
                || selected.stream().anyMatch(java.util.Objects::isNull)
                || selected.contains(candidate)
                || !generatedRequirements.keySet().equals(generated.keySet())) {
            throw new IllegalArgumentException(
                    "Automatic alternative-route review inputs are invalid");
        }
        Map<ResourceLocation, RouteProfile> memo = new LinkedHashMap<>();
        List<RouteProfile> existingProfiles = selected.stream()
                .sorted(ID_ORDER)
                .map(parent -> profile(
                        parent, generated, generatedRequirements,
                        policies, memo, new LinkedHashSet<>()))
                .toList();
        RouteProfile existing = alternatives(existingProfiles);
        RouteProfile proposed = profile(
                candidate, generated, generatedRequirements,
                policies, memo, new LinkedHashSet<>());

        long minimumLower = Math.min(existing.lowerBound(), proposed.lowerBound());
        long minimumUpper = Math.min(existing.upperBound(), proposed.upperBound());
        long maximumLower = Math.max(existing.lowerBound(), proposed.lowerBound());
        long maximumUpper = Math.max(existing.upperBound(), proposed.upperBound());
        long ratioLower = ratioBasisPoints(maximumLower, minimumUpper);
        long ratioUpper = ratioBasisPoints(maximumUpper, minimumLower);

        LinkedHashSet<ResourceLocation> shared = new LinkedHashSet<>(
                existing.mandatoryClosure());
        shared.retainAll(proposed.mandatoryClosure());
        LinkedHashSet<ResourceLocation> union = new LinkedHashSet<>(
                existing.mandatoryClosure());
        union.addAll(proposed.mandatoryClosure());
        int overlap = basisPoints(shared.size(), union.size());
        int divergent = Math.subtractExact(union.size(), shared.size());

        boolean definitelyZeroCostImbalanced = minimumUpper == 0L
                && maximumLower > 0L;
        boolean rejected = definitelyZeroCostImbalanced
                || ratioLower > MAXIMUM_PROVEN_ROUTE_COST_RATIO_BASIS_POINTS;
        boolean exact = existing.exact() && proposed.exact();
        AutomaticWeaponPrerequisiteDecision.AlternativeRouteOutcome outcome = rejected
                ? AutomaticWeaponPrerequisiteDecision.AlternativeRouteOutcome
                        .REJECTED_PROVEN_COST_IMBALANCE
                : exact
                        ? AutomaticWeaponPrerequisiteDecision.AlternativeRouteOutcome
                                .ACCEPTED_EXACT
                        : AutomaticWeaponPrerequisiteDecision.AlternativeRouteOutcome
                                .ACCEPTED_BOUNDED;
        return new AutomaticWeaponPrerequisiteDecision.AlternativeRouteReview(
                candidate,
                outcome,
                existing.lowerBound(),
                existing.upperBound(),
                proposed.lowerBound(),
                proposed.upperBound(),
                ratioLower,
                ratioUpper,
                overlap,
                divergent,
                exact);
    }

    /**
     * Safe cost bounds for choosing the cheapest member of one generated OR
     * route. Package-private so the hybrid mandatory-gateway guard can price
     * the already-selected alternative group without flattening it into AND.
     */
    static RouteCostBounds alternativeCostBounds(
            List<ResourceLocation> alternatives,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, ResearchRequirements> generatedRequirements,
            Map<ResourceLocation, BlueprintResearchPolicy> policies) {
        if (alternatives == null || alternatives.isEmpty()
                || generated == null || generatedRequirements == null
                || policies == null
                || alternatives.stream().anyMatch(java.util.Objects::isNull)
                || alternatives.stream().distinct().count() != alternatives.size()
                || !generatedRequirements.keySet().equals(generated.keySet())) {
            throw new IllegalArgumentException(
                    "Automatic route cost-bound inputs are invalid");
        }
        Map<ResourceLocation, RouteProfile> memo = new LinkedHashMap<>();
        RouteProfile result = alternatives(alternatives.stream()
                .sorted(ID_ORDER)
                .map(parent -> profile(
                        parent,
                        generated,
                        generatedRequirements,
                        policies,
                        memo,
                        new LinkedHashSet<>()))
                .toList());
        return new RouteCostBounds(
                result.lowerBound(), result.upperBound(), result.exact());
    }

    private static RouteProfile profile(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, ResearchRequirements> generatedRequirements,
            Map<ResourceLocation, BlueprintResearchPolicy> policies,
            Map<ResourceLocation, RouteProfile> memo,
            Set<ResourceLocation> visiting) {
        RouteProfile known = memo.get(node);
        if (known != null) {
            return known;
        }
        if (!visiting.add(node)) {
            throw new IllegalArgumentException(
                    "Automatic alternative-route review encountered a cycle");
        }
        try {
            ResearchRequirements requirements = requirementsFor(
                    node, generated, generatedRequirements, policies);
            long ownCost = pointCost(node, policies);
            long lowerParentCost = 0L;
            long upperParentCost = 0L;
            LinkedHashSet<ResourceLocation> mandatory = new LinkedHashSet<>();
            for (ResearchPrerequisiteGroup group : requirements.allOf()) {
                List<RouteProfile> routes = new ArrayList<>();
                for (ResourceLocation alternative : group.anyOf()) {
                    routes.add(profile(
                            alternative, generated, generatedRequirements,
                            policies, memo, visiting));
                }
                RouteProfile alternatives = alternatives(routes);
                lowerParentCost = Math.max(
                        lowerParentCost, alternatives.lowerBound());
                upperParentCost = saturatedAdd(
                        upperParentCost, alternatives.upperBound());
                mandatory.addAll(alternatives.mandatoryClosure());
            }
            mandatory.add(node);
            long lower = saturatedAdd(ownCost, lowerParentCost);
            long upper = saturatedAdd(ownCost, upperParentCost);
            RouteProfile result = new RouteProfile(
                    lower,
                    upper,
                    lower == upper,
                    mandatory);
            memo.put(node, result);
            return result;
        } finally {
            visiting.remove(node);
        }
    }

    /** Combines profiles that belong to one inclusive-OR group. */
    private static RouteProfile alternatives(List<RouteProfile> routes) {
        if (routes == null || routes.isEmpty()) {
            return RouteProfile.EMPTY;
        }
        long lower = Long.MAX_VALUE;
        long upper = Long.MAX_VALUE;
        LinkedHashSet<ResourceLocation> mandatory = null;
        for (RouteProfile route : routes) {
            lower = Math.min(lower, route.lowerBound());
            upper = Math.min(upper, route.upperBound());
            if (mandatory == null) {
                mandatory = new LinkedHashSet<>(route.mandatoryClosure());
            } else {
                mandatory.retainAll(route.mandatoryClosure());
            }
        }
        return new RouteProfile(
                lower,
                upper,
                lower == upper,
                mandatory == null ? Set.of() : mandatory);
    }

    private static ResearchRequirements requirementsFor(
            ResourceLocation node,
            Map<ResourceLocation, List<ResourceLocation>> generated,
            Map<ResourceLocation, ResearchRequirements> generatedRequirements,
            Map<ResourceLocation, BlueprintResearchPolicy> policies) {
        ResearchRequirements canonical = generatedRequirements.get(node);
        if (canonical != null) {
            return canonical;
        }
        if (generated.containsKey(node)) {
            throw new IllegalArgumentException(
                    "Generated alternative-route authority is incomplete");
        }
        BlueprintResearchPolicy policy = policies.get(node);
        return policy == null ? ResearchRequirements.EMPTY : policy.requirements();
    }

    private static long pointCost(
            ResourceLocation node,
            Map<ResourceLocation, BlueprintResearchPolicy> policies) {
        BlueprintResearchPolicy policy = policies.get(node);
        return policy == null ? 0L : policy.researchCost().points();
    }

    private static int basisPoints(long numerator, long denominator) {
        if (denominator == 0L) {
            return 0;
        }
        return Math.toIntExact(Math.min(
                10_000L, Math.round(numerator * 10_000.0 / denominator)));
    }

    private static long ratioBasisPoints(long maximum, long minimum) {
        if (minimum == 0L) {
            return maximum == 0L ? 10_000L : Long.MAX_VALUE;
        }
        if (maximum > Long.MAX_VALUE / 10_000L) {
            return Long.MAX_VALUE;
        }
        return Math.max(10_000L, maximum * 10_000L / minimum);
    }

    private static long saturatedAdd(long left, long right) {
        return Long.MAX_VALUE - left < right ? Long.MAX_VALUE : left + right;
    }

    private record RouteProfile(
            long lowerBound,
            long upperBound,
            boolean exact,
            Set<ResourceLocation> mandatoryClosure) {
        private static final RouteProfile EMPTY = new RouteProfile(
                0L, 0L, true, Set.of());

        private RouteProfile {
            mandatoryClosure = Set.copyOf(mandatoryClosure);
            if (lowerBound < 0L || upperBound < lowerBound
                    || exact != (lowerBound == upperBound)) {
                throw new IllegalArgumentException(
                        "Automatic alternative route profile is invalid");
            }
        }
    }

    record RouteCostBounds(long lowerBound, long upperBound, boolean exact) {
        RouteCostBounds {
            if (lowerBound < 0L || upperBound < lowerBound
                    || exact != (lowerBound == upperBound)) {
                throw new IllegalArgumentException(
                        "Automatic route cost bounds are invalid");
            }
        }
    }
}
