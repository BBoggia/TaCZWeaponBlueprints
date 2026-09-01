package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Builds the exact, bounded prerequisite closure for a direct Research Tree purchase. */
public final class ResearchPathUnlockPlanner {
    /** Prevents one click from constructing an unreasonably large atomic transaction. */
    public static final int MAX_UNLOCKS_PER_PURCHASE = 1_024;
    /** Bounds already-learned support retained while proving a complete route. */
    public static final int MAX_SUPPORT_NODES = PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
    /** Maximum nondominated closures retained for any one dependent node. */
    public static final int MAX_FRONTIER_OPTIONS = 4_096;
    /** Global deterministic work budget for one route-planning request. */
    public static final int MAX_ROUTE_STATES = 262_144;

    private ResearchPathUnlockPlanner() {
    }

    /** Structural planning compatibility overload without inventory-aware tie-breaking. */
    public static Result plan(
            ResourceLocation targetId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt,
            boolean creativePlayer) {
        return plan(
                targetId,
                playerData,
                policyResolver,
                progressionExempt,
                creativePlayer,
                null);
    }

    /**
     * Plans all globally shortest closures and, when inventory is supplied,
     * prefers one the player can pay for now before the stable economic ties.
     */
    public static Result plan(
            ResourceLocation targetId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt,
            boolean creativePlayer,
            List<ItemStack> inventoryStacks) {
        if (!validId(targetId) || playerData == null || policyResolver == null
                || progressionExempt == null
                || inventoryStacks != null
                        && inventoryStacks.stream().anyMatch(java.util.Objects::isNull)) {
            return Result.failure(BlueprintResearchService.Status.INVALID_INPUT);
        }
        try {
            if (progressionExempt.test(targetId)) {
                return Result.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE);
            }
        } catch (RuntimeException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
        Planner planner = new Planner(playerData, policyResolver, progressionExempt);
        Frontier frontier;
        try {
            frontier = planner.visit(targetId, new LinkedHashSet<>());
        } catch (RuntimeException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
        if (!frontier.successful()) {
            return Result.failure(frontier.status());
        }
        if (frontier.options().isEmpty()
                || frontier.options().stream().allMatch(option -> option.purchaseCount() == 0)) {
            return Result.failure(BlueprintResearchService.Status.ALREADY_LEARNED);
        }
        try {
            return selectPlan(
                    targetId,
                    frontier.options(),
                    planner,
                    playerData.getResearchPoints(),
                    creativePlayer,
                    inventoryStacks);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE);
        }
    }

    private static Result selectPlan(
            ResourceLocation targetId,
            List<RouteOption> options,
            Planner planner,
            int pointBalance,
            boolean creativePlayer,
            List<ItemStack> inventoryStacks) {
        int shortest = options.stream()
                .mapToInt(RouteOption::purchaseCount)
                .min()
                .orElseThrow();
        Selection selected = null;
        for (RouteOption option : options) {
            if (option.purchaseCount() != shortest) {
                continue;
            }
            try {
                LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> ordered =
                        planner.topologicalOrder(targetId, option.nodes());
                Plan plan = buildPlan(ordered, creativePlayer);
                boolean affordable = inventoryStacks != null
                        && pointBalance >= plan.pointCost()
                        && ResearchIngredientPlanner.plan(
                                inventoryStacks, plan.ingredients()).isPresent();
                Selection candidate = new Selection(
                        plan,
                        affordable,
                        totalIngredientCount(plan.ingredients()),
                        canonicalIds(plan.nodes()));
                if (selected == null
                        || compareSelections(candidate, selected, inventoryStacks != null) < 0) {
                    selected = candidate;
                }
            } catch (ArithmeticException | IllegalArgumentException exception) {
                // Another globally shortest closure may still have a bounded economy.
            }
        }
        return selected == null
                ? Result.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE)
                : Result.success(selected.plan());
    }

    private static int compareSelections(
            Selection left,
            Selection right,
            boolean inventoryAware) {
        if (inventoryAware) {
            int affordable = Boolean.compare(right.affordable(), left.affordable());
            if (affordable != 0) {
                return affordable;
            }
        }
        int comparison = Integer.compare(left.plan().pointCost(), right.plan().pointCost());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.materialCount(), right.materialCount());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(
                left.plan().ingredients().size(), right.plan().ingredients().size());
        return comparison != 0 ? comparison : left.canonicalIds().compareTo(right.canonicalIds());
    }

    private static int totalIngredientCount(
            List<ResearchIngredientPlanner.Requirement> requirements) {
        int total = 0;
        for (ResearchIngredientPlanner.Requirement requirement : requirements) {
            total = Math.addExact(total, requirement.count());
        }
        return total;
    }

    private static String canonicalIds(List<PlannedNode> nodes) {
        return nodes.stream()
                .map(PlannedNode::blueprintId)
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .map(ResourceLocation::toString)
                .collect(java.util.stream.Collectors.joining("\u0000"));
    }

    private static Plan buildPlan(
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> nodes,
            boolean creativePlayer) {
        int pointCost = 0;
        boolean anyConfiguredCost = false;
        boolean everyConfiguredCostBypassed = creativePlayer;
        Map<String, MutableRequirement> requirements = new TreeMap<>();
        List<PlannedNode> plannedNodes = new ArrayList<>(nodes.size());
        for (BlueprintResearchPolicy policy : nodes.values()) {
            if (policy.learned()) {
                continue;
            }
            boolean bypassed = creativePlayer && policy.creativeBypassesCost();
            boolean configuredCost = policy.researchCost().points() > 0
                    || !policy.researchCost().ingredients().isEmpty();
            anyConfiguredCost |= configuredCost;
            if (configuredCost && !bypassed) {
                everyConfiguredCostBypassed = false;
            }
            if (!bypassed) {
                pointCost = Math.addExact(pointCost, policy.researchCost().points());
                for (BlueprintResearchIngredient ingredient
                        : policy.researchCost().ingredients()) {
                    String key = ingredientKey(ingredient);
                    MutableRequirement existing = requirements.get(key);
                    if (existing == null) {
                        requirements.put(key, new MutableRequirement(
                                ingredient.items(), ingredient.tag(), ingredient.count()));
                    } else {
                        existing.count = Math.addExact(existing.count, ingredient.count());
                    }
                }
            }
            plannedNodes.add(new PlannedNode(policy.blueprintId(), policy, bypassed));
        }
        if (pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("path RP cost exceeds the supported balance");
        }
        List<ResearchIngredientPlanner.Requirement> combinedRequirements = requirements.values()
                .stream()
                .map(value -> new ResearchIngredientPlanner.Requirement(
                        value.items, value.tag, value.count))
                .toList();
        return new Plan(
                List.copyOf(plannedNodes),
                pointCost,
                combinedRequirements,
                anyConfiguredCost && everyConfiguredCostBypassed);
    }

    private static String ingredientKey(BlueprintResearchIngredient ingredient) {
        if (ingredient.tag().isPresent()) {
            return "tag\u0000" + ingredient.tag().orElseThrow();
        }
        return "items\u0000" + String.join(
                "\u0000", ingredient.items().stream()
                        .map(ResourceLocation::toString)
                        .sorted()
                        .toList());
    }

    private static boolean validId(ResourceLocation id) {
        return id != null
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    private static final class Planner {
        private final IPlayerRecipeData playerData;
        private final Function<ResourceLocation, BlueprintResearchPolicy> policyResolver;
        private final Predicate<ResourceLocation> progressionExempt;
        private final int pointBalance;
        private final Map<ResourceLocation, Frontier> memo = new HashMap<>();
        private int routeStates;

        private Planner(
                IPlayerRecipeData playerData,
                Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
                Predicate<ResourceLocation> progressionExempt) {
            this.playerData = playerData;
            this.policyResolver = policyResolver;
            this.progressionExempt = progressionExempt;
            this.pointBalance = playerData.getResearchPoints();
        }

        private Frontier visit(ResourceLocation blueprintId, Set<ResourceLocation> visiting) {
            if (!validId(blueprintId)) {
                return Frontier.failure(BlueprintResearchService.Status.INVALID_INPUT);
            }
            if (progressionExempt.test(blueprintId)) {
                return Frontier.success(List.of(RouteOption.EMPTY));
            }
            Frontier cached = memo.get(blueprintId);
            if (cached != null) {
                return cached;
            }
            if (!visiting.add(blueprintId)) {
                return Frontier.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE);
            }
            Frontier result;
            try {
                BlueprintResearchPolicy policy = policyResolver.apply(blueprintId);
                BlueprintResearchService.Status invalid = validate(blueprintId, policy);
                if (invalid != null) {
                    result = Frontier.failure(invalid);
                } else {
                    result = combineRequirements(policy, visiting);
                }
            } catch (RouteComplexityException exception) {
                result = Frontier.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
            } catch (RuntimeException exception) {
                result = Frontier.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
            } finally {
                visiting.remove(blueprintId);
            }
            memo.put(blueprintId, result);
            return result;
        }

        private Frontier combineRequirements(
                BlueprintResearchPolicy policy,
                Set<ResourceLocation> visiting) {
            List<RouteOption> current = List.of(RouteOption.EMPTY);
            for (ResearchPrerequisiteGroup group : policy.requirements().allOf()) {
                if (group.satisfiedBy(progressionExempt)) {
                    continue;
                }
                List<RouteOption> groupOptions = new ArrayList<>();
                BlueprintResearchService.Status fallback =
                        BlueprintResearchService.Status.PREREQUISITES_REQUIRED;
                for (ResourceLocation alternative : group.anyOf()) {
                    Frontier alternativeFrontier = visit(alternative, visiting);
                    if (alternativeFrontier.status()
                            == BlueprintResearchService.Status.ROUTE_TOO_COMPLEX) {
                        return alternativeFrontier;
                    }
                    if (alternativeFrontier.status()
                            == BlueprintResearchService.Status.PATH_TOO_LARGE) {
                        fallback = BlueprintResearchService.Status.PATH_TOO_LARGE;
                    }
                    if (alternativeFrontier.successful()) {
                        for (RouteOption option : alternativeFrontier.options()) {
                            addNondominated(groupOptions, option);
                        }
                    }
                }
                if (groupOptions.isEmpty()) {
                    return Frontier.failure(fallback);
                }
                List<RouteOption> combined = new ArrayList<>();
                boolean tooLarge = false;
                for (RouteOption base : current) {
                    for (RouteOption alternative : groupOptions) {
                        countState();
                        RouteOption merged = base.merge(alternative);
                        if (merged.purchaseCount() > MAX_UNLOCKS_PER_PURCHASE
                                || merged.nodes().size() > MAX_SUPPORT_NODES) {
                            tooLarge = true;
                            continue;
                        }
                        addNondominated(combined, merged);
                    }
                }
                if (combined.isEmpty()) {
                    return Frontier.failure(tooLarge
                            ? BlueprintResearchService.Status.PATH_TOO_LARGE
                            : BlueprintResearchService.Status.PREREQUISITES_REQUIRED);
                }
                current = stable(combined);
            }
            List<RouteOption> withNode = new ArrayList<>();
            for (RouteOption option : current) {
                RouteOption extended = option.with(policy);
                if (extended.purchaseCount() > MAX_UNLOCKS_PER_PURCHASE
                        || extended.nodes().size() > MAX_SUPPORT_NODES) {
                    continue;
                }
                addNondominated(withNode, extended);
            }
            return withNode.isEmpty()
                    ? Frontier.failure(BlueprintResearchService.Status.PATH_TOO_LARGE)
                    : Frontier.success(stable(withNode));
        }

        private void countState() {
            routeStates++;
            if (routeStates > MAX_ROUTE_STATES) {
                throw new RouteComplexityException();
            }
        }

        private void addNondominated(List<RouteOption> frontier, RouteOption candidate) {
            countState();
            Set<ResourceLocation> candidateIds = candidate.purchaseIds();
            for (RouteOption existing : frontier) {
                Set<ResourceLocation> existingIds = existing.purchaseIds();
                if (candidateIds.containsAll(existingIds)) {
                    return;
                }
            }
            frontier.removeIf(existing -> existing.purchaseIds().containsAll(candidateIds));
            frontier.add(candidate);
            if (frontier.size() > MAX_FRONTIER_OPTIONS) {
                throw new RouteComplexityException();
            }
        }

        private List<RouteOption> stable(List<RouteOption> options) {
            return options.stream()
                    .sorted(Comparator.comparingInt(RouteOption::purchaseCount)
                            .thenComparing(RouteOption::canonicalKey))
                    .toList();
        }

        private BlueprintResearchService.Status validate(
                ResourceLocation blueprintId,
                BlueprintResearchPolicy policy) {
            if (policy == null) {
                return BlueprintResearchService.Status.POLICY_UNAVAILABLE;
            }
            if (!blueprintId.equals(policy.blueprintId())) {
                return BlueprintResearchService.Status.POLICY_MISMATCH;
            }
            boolean learned = playerData.hasBlueprint(blueprintId.toString());
            if (!policy.playerDataAvailable()
                    || policy.researchPoints() != pointBalance
                    || policy.learned() != learned
                    || policy.discovered()
                            != playerData.hasDiscoveredBlueprint(blueprintId.toString())) {
                return BlueprintResearchService.Status.STALE_POLICY;
            }
            // Learned nodes still prove their ancestry, but their acquisition
            // policy no longer gates or charges the repair route.
            if (learned) {
                return null;
            }
            if (!policy.available()) {
                return BlueprintResearchService.Status.CONTENT_UNAVAILABLE;
            }
            if (policy.blocked()) {
                return BlueprintResearchService.Status.BLOCKED;
            }
            if (!policy.researchEnabled()) {
                return BlueprintResearchService.Status.RESEARCH_DISABLED;
            }
            if (!policy.treeEnabled()
                    || !policy.visibility().allowsServerSelection()
                    || progressionExempt.test(blueprintId)
                    || policy.researchCost().points() > policy.pointCap()) {
                return BlueprintResearchService.Status.POLICY_INELIGIBLE;
            }
            if (policy.requiresDiscovery() && !policy.discovered()) {
                return BlueprintResearchService.Status.DISCOVERY_REQUIRED;
            }
            return null;
        }

        private LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> topologicalOrder(
                ResourceLocation targetId,
                Map<ResourceLocation, BlueprintResearchPolicy> selected) {
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> ordered =
                    new LinkedHashMap<>();
            orderVisit(targetId, selected, new LinkedHashSet<>(), ordered);
            if (ordered.size() != selected.size()
                    || ordered.isEmpty()
                    || !targetId.equals(new ArrayList<>(ordered.keySet()).get(ordered.size() - 1))) {
                throw new IllegalArgumentException("selected research route is not connected");
            }
            return ordered;
        }

        private void orderVisit(
                ResourceLocation blueprintId,
                Map<ResourceLocation, BlueprintResearchPolicy> selected,
                Set<ResourceLocation> visiting,
                LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> ordered) {
            if (ordered.containsKey(blueprintId) || !selected.containsKey(blueprintId)) {
                return;
            }
            if (!visiting.add(blueprintId)) {
                throw new IllegalArgumentException("cycle in selected research route");
            }
            BlueprintResearchPolicy policy = selected.get(blueprintId);
            for (ResearchPrerequisiteGroup group : policy.requirements().allOf()) {
                if (group.satisfiedBy(progressionExempt)) {
                    continue;
                }
                List<ResourceLocation> selectedAlternatives = group.anyOf().stream()
                        .filter(selected::containsKey)
                        .toList();
                if (selectedAlternatives.isEmpty()) {
                    throw new IllegalArgumentException("selected route does not satisfy a group");
                }
                selectedAlternatives.forEach(id -> orderVisit(id, selected, visiting, ordered));
            }
            visiting.remove(blueprintId);
            ordered.put(blueprintId, policy);
        }
    }

    public record Plan(
            List<PlannedNode> nodes,
            int pointCost,
            List<ResearchIngredientPlanner.Requirement> ingredients,
            boolean costBypassed) {
        public Plan {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
            if (nodes.isEmpty()
                    || nodes.size() > MAX_UNLOCKS_PER_PURCHASE
                    || pointCost < 0
                    || pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || nodes.stream().anyMatch(java.util.Objects::isNull)
                    || ingredients.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("invalid research path unlock plan");
            }
        }

        public int unlockCount() {
            return nodes.size();
        }
    }

    public record PlannedNode(
            ResourceLocation blueprintId,
            BlueprintResearchPolicy policy,
            boolean costBypassed) {
        public PlannedNode {
            if (!validId(blueprintId) || policy == null
                    || !blueprintId.equals(policy.blueprintId())) {
                throw new IllegalArgumentException("invalid planned research path node");
            }
        }
    }

    public record Result(BlueprintResearchService.Status status, Optional<Plan> plan) {
        public Result {
            plan = plan == null ? Optional.empty() : plan;
            if (status == null
                    || (status == BlueprintResearchService.Status.SUCCESS) != plan.isPresent()) {
                throw new IllegalArgumentException("invalid research path planning result");
            }
        }

        public static Result success(Plan plan) {
            return new Result(BlueprintResearchService.Status.SUCCESS, Optional.of(plan));
        }

        public static Result failure(BlueprintResearchService.Status status) {
            if (status == BlueprintResearchService.Status.SUCCESS) {
                throw new IllegalArgumentException("successful path plan requires a plan");
            }
            return new Result(status, Optional.empty());
        }

        public boolean successful() {
            return status == BlueprintResearchService.Status.SUCCESS;
        }
    }

    private record Frontier(BlueprintResearchService.Status status, List<RouteOption> options) {
        private Frontier {
            options = options == null ? List.of() : List.copyOf(options);
        }

        private static Frontier success(List<RouteOption> options) {
            return new Frontier(BlueprintResearchService.Status.SUCCESS, options);
        }

        private static Frontier failure(BlueprintResearchService.Status status) {
            return new Frontier(status, List.of());
        }

        private boolean successful() {
            return status == BlueprintResearchService.Status.SUCCESS;
        }
    }

    private record RouteOption(
            Map<ResourceLocation, BlueprintResearchPolicy> nodes,
            Set<ResourceLocation> purchaseIds) {
        private static final RouteOption EMPTY = new RouteOption(Map.of());

        private RouteOption(Map<ResourceLocation, BlueprintResearchPolicy> nodes) {
            this(nodes, null);
        }

        private RouteOption {
            TreeMap<ResourceLocation, BlueprintResearchPolicy> stable = new TreeMap<>(
                    Comparator.comparing(ResourceLocation::toString));
            if (nodes != null) {
                stable.putAll(nodes);
            }
            nodes = Map.copyOf(stable);
            purchaseIds = nodes.entrySet().stream()
                    .filter(entry -> !entry.getValue().learned())
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }

        private RouteOption merge(RouteOption other) {
            Map<ResourceLocation, BlueprintResearchPolicy> merged = new HashMap<>(nodes);
            other.nodes.forEach((id, policy) -> {
                BlueprintResearchPolicy existing = merged.putIfAbsent(id, policy);
                if (existing != null && !existing.equals(policy)) {
                    throw new IllegalArgumentException("route contains inconsistent policies");
                }
            });
            return new RouteOption(merged);
        }

        private RouteOption with(BlueprintResearchPolicy policy) {
            Map<ResourceLocation, BlueprintResearchPolicy> extended = new HashMap<>(nodes);
            BlueprintResearchPolicy existing = extended.putIfAbsent(policy.blueprintId(), policy);
            if (existing != null && !existing.equals(policy)) {
                throw new IllegalArgumentException("route contains inconsistent target policy");
            }
            return new RouteOption(extended);
        }

        private String canonicalKey() {
            return nodes.keySet().stream()
                    .map(ResourceLocation::toString)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining("\u0000"));
        }

        private int purchaseCount() {
            return purchaseIds.size();
        }
    }

    private record Selection(
            Plan plan,
            boolean affordable,
            int materialCount,
            String canonicalIds) {
    }

    private static final class MutableRequirement {
        private final List<ResourceLocation> items;
        private final Optional<ResourceLocation> tag;
        private int count;

        private MutableRequirement(
                List<ResourceLocation> items,
                Optional<ResourceLocation> tag,
                int count) {
            this.items = items;
            this.tag = tag;
            this.count = count;
        }
    }

    private static final class RouteComplexityException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
