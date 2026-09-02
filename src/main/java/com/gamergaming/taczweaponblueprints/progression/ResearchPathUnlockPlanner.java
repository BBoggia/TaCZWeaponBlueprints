package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayList;
import java.util.Collections;
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
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

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
    /** Bounds policy resolution even when a malformed graph defeats memoization assumptions. */
    public static final int MAX_POLICY_LOOKUPS = PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
    /** Bounds Cartesian route merges separately from retained frontier states. */
    public static final int MAX_ROUTE_MERGES = 262_144;
    /** Bounds subset scans performed by the compatibility frontier solver. */
    public static final int MAX_DOMINANCE_COMPARISONS = 2_097_152;
    /** Bounds cumulative node references copied into candidate route closures. */
    public static final int MAX_CLOSURE_NODE_REFERENCES = 8_388_608;
    /** Bounds canonical ordering and final route reconstruction work. */
    public static final int MAX_CANONICAL_WORK = 8_388_608;
    /** Bounds immutable-set word unions, subset checks, and comparisons. */
    public static final int MAX_BITSET_WORD_WORK = 33_554_432;
    /** Bounds simultaneously retained general-solver labels across the request. */
    public static final int MAX_RETAINED_LABELS = 65_536;
    /** Bounds immutable purchase/support bit words retained by the general solver. */
    public static final int MAX_RETAINED_BIT_WORDS = 4_194_304;
    /** Emergency fuse; deterministic budgets remain the normal failure mechanism. */
    public static final long EMERGENCY_TIMEOUT_NANOS = 2_000_000_000L;
    private static final int TIME_CHECK_INTERVAL = 256;

    private ResearchPathUnlockPlanner() {
    }

    public static RouteSelectionPolicy routeSelectionPolicy() {
        return RouteSelectionPolicy.STABLE_MINIMUM_UNLOCKS;
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
                null,
                ResearchPathAuthority.authored());
    }

    /**
     * Plans the stable globally shortest closure. Inventory is accepted for
     * compatibility, but affordability never changes route identity.
     */
    public static Result plan(
            ResourceLocation targetId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt,
            boolean creativePlayer,
            List<ItemStack> inventoryStacks) {
        return plan(
                targetId,
                playerData,
                policyResolver,
                progressionExempt,
                creativePlayer,
                inventoryStacks,
                ResearchPathAuthority.authored());
    }

    public static Result plan(
            ResourceLocation targetId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt,
            boolean creativePlayer,
            List<ItemStack> inventoryStacks,
            ResearchPathAuthority authority) {
        return planWithControls(
                targetId,
                playerData,
                policyResolver,
                progressionExempt,
                creativePlayer,
                inventoryStacks,
                authority,
                PlanningLimits.DEFAULT,
                System::nanoTime);
    }

    static Result planWithControls(
            ResourceLocation targetId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt,
            boolean creativePlayer,
            List<ItemStack> inventoryStacks,
            ResearchPathAuthority authority,
            PlanningLimits limits,
            LongSupplier nanoClock) {
        if (!validId(targetId) || playerData == null || policyResolver == null
                || progressionExempt == null || authority == null || limits == null
                || nanoClock == null
                || inventoryStacks != null
                        && inventoryStacks.stream().anyMatch(java.util.Objects::isNull)) {
            return Result.failure(BlueprintResearchService.Status.INVALID_INPUT);
        }
        PlanningBudget budget;
        RequestInputs inputs;
        try {
            budget = new PlanningBudget(limits, nanoClock);
            inputs = new RequestInputs(policyResolver, progressionExempt, budget);
        } catch (RuntimeException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
        try {
            if (inputs.progressionExempt().test(targetId)) {
                return Result.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE);
            }
        } catch (RouteComplexityException exception) {
            return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
        } catch (RuntimeException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
        ResolvedResearchPathGraph.Result graphResult =
                ResolvedResearchPathGraph.buildWithBudget(
                        targetId,
                        playerData,
                        inputs.policyResolver(),
                        inputs.progressionExempt(),
                        authority,
                        graphLimits(limits),
                        budget);
        if (!graphResult.successful()
                && graphResult.status()
                        == BlueprintResearchService.Status.ROUTE_TOO_COMPLEX) {
            return Result.failure(graphResult.status());
        }
        if (graphResult.successful()) {
            ResolvedResearchPathGraph.Graph graph = graphResult.graph().orElseThrow();
            if (graph.shape() == ResolvedResearchPathGraph.GraphShape.MANDATORY_DAG) {
                MandatoryResearchPathSolver.Result mandatory =
                        MandatoryResearchPathSolver.solve(graph, creativePlayer, budget);
                try {
                    budget.checkDeadline();
                } catch (RouteComplexityException exception) {
                    return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
                }
                return finishIndexedResult(graph, mandatory.status(), mandatory.plan(), budget);
            }
            if (graph.shape() == ResolvedResearchPathGraph.GraphShape.OR_PATH_DAG) {
                OrPathResearchSolver.Result orPath =
                        OrPathResearchSolver.solve(graph, creativePlayer, budget);
                try {
                    budget.checkDeadline();
                } catch (RouteComplexityException exception) {
                    return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
                }
                return finishIndexedResult(graph, orPath.status(), orPath.plan(), budget);
            }
            if (graph.shape()
                    == ResolvedResearchPathGraph.GraphShape.SEPARABLE_AND_OR_DAG) {
                SeparableAndOrResearchSolver.Result separable =
                        SeparableAndOrResearchSolver.solve(
                                graph, creativePlayer, budget);
                try {
                    budget.checkDeadline();
                } catch (RouteComplexityException exception) {
                    return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
                }
                return finishIndexedResult(graph, separable.status(), separable.plan(), budget);
            }
            if (graph.shape()
                    == ResolvedResearchPathGraph.GraphShape.GENERAL_AND_OR_DAG) {
                ResearchPathComplexityMemo.Key memoKey = null;
                // Only live requests using the shared default limits may affect
                // the process-wide real-time memo. Structurally equal custom
                // limits belong to tests/tools and must remain isolated.
                boolean memoEnabled = limits == PlanningLimits.DEFAULT;
                if (memoEnabled) {
                    try {
                        memoKey = ResearchPathComplexityMemo.key(
                                graph, creativePlayer, budget);
                    } catch (RouteComplexityException exception) {
                        return Result.failure(
                                BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
                    }
                    if (ResearchPathComplexityMemo.contains(
                            memoKey, System.nanoTime())) {
                        return Result.failure(
                                BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
                    }
                }
                GeneralAndOrResearchSolver.Result general =
                        GeneralAndOrResearchSolver.solve(
                                graph, creativePlayer, budget);
                try {
                    budget.checkDeadline();
                } catch (RouteComplexityException exception) {
                    if (memoEnabled) {
                        ResearchPathComplexityMemo.remember(
                                memoKey, System.nanoTime());
                    }
                    return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
                }
                if (memoEnabled) {
                    if (general.status()
                            == BlueprintResearchService.Status.ROUTE_TOO_COMPLEX) {
                        ResearchPathComplexityMemo.remember(
                                memoKey, System.nanoTime());
                    } else {
                        ResearchPathComplexityMemo.forget(memoKey);
                    }
                }
                return finishIndexedResult(graph, general.status(), general.plan(), budget);
            }
        }
        Planner planner;
        try {
            planner = new Planner(
                    playerData,
                    inputs.policyResolver(),
                    inputs.progressionExempt(),
                    authority,
                    budget);
        } catch (RuntimeException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
        Frontier frontier;
        try {
            frontier = planner.visit(targetId, new LinkedHashSet<>());
        } catch (RuntimeException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
        if (!frontier.successful()) {
            try {
                budget.checkDeadline();
            } catch (RouteComplexityException exception) {
                return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
            }
            return Result.failure(frontier.status());
        }
        if (frontier.options().isEmpty()
                || frontier.options().stream().allMatch(option -> option.purchaseCount() == 0)) {
            try {
                budget.checkDeadline();
            } catch (RouteComplexityException exception) {
                return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
            }
            return Result.failure(BlueprintResearchService.Status.ALREADY_LEARNED);
        }
        try {
            Result selected = selectPlan(
                    targetId,
                    frontier.options(),
                    planner,
                    creativePlayer);
            budget.checkDeadline();
            return selected;
        } catch (RouteComplexityException exception) {
            return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE);
        }
    }

    /** Clears process-local route-failure state when the server instance stops. */
    public static void clearComplexityMemo() {
        ResearchPathComplexityMemo.clear();
    }

    private static ResolvedResearchPathGraph.BuildLimits graphLimits(PlanningLimits limits) {
        return new ResolvedResearchPathGraph.BuildLimits(
                ResolvedResearchPathGraph.MAX_GRAPH_NODES,
                ResolvedResearchPathGraph.MAX_GRAPH_EDGES,
                (int) Math.min(ResolvedResearchPathGraph.MAX_GRAPH_NODES,
                        limits.policyLookups()),
                ResolvedResearchPathGraph.MAX_CLASSIFICATION_BIT_WORDS,
                BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH,
                limits.emergencyTimeoutNanos());
    }

    private static Result finishIndexedResult(
            ResolvedResearchPathGraph.Graph graph,
            BlueprintResearchService.Status status,
            Optional<Plan> plan,
            PlanningBudget budget) {
        if (status != BlueprintResearchService.Status.SUCCESS || plan.isEmpty()) {
            return Result.failure(status);
        }
        try {
            Plan selected = plan.orElseThrow();
            SelectedUnlockSolution solution = withSelectedRequirements(
                    graph, selected.solution(), budget);
            budget.checkDeadline();
            return Result.success(new Plan(solution, selected.quote()));
        } catch (RouteComplexityException exception) {
            return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
        } catch (IllegalArgumentException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE);
        }
    }

    /**
     * Derives one canonical prerequisite proof from the solver's selected support
     * closure. Solvers remain free to optimize closures without retaining search
     * predecessors, while consumers receive the exact stable edges that prove the
     * returned route.
     */
    private static SelectedUnlockSolution withSelectedRequirements(
            ResolvedResearchPathGraph.Graph graph,
            SelectedUnlockSolution solution,
            PlanningBudget budget) {
        LinkedHashSet<ResourceLocation> support = new LinkedHashSet<>(solution.supportIds());
        Comparator<ResourceLocation> canonical = Comparator.comparing(ResourceLocation::toString);

        // The graph's topological order is prerequisite-first. Walking it in reverse lets a
        // connected learned prerequisite be added by a dependent before that prerequisite is
        // visited, so its own ancestry is included in the same proof without recursion.
        List<Integer> topologicalOrder = graph.topologicalOrder();
        for (int position = topologicalOrder.size() - 1; position >= 0; position--) {
            ResolvedResearchPathGraph.Node node = graph.nodes().get(
                    topologicalOrder.get(position));
            if (!support.contains(node.blueprintId())) {
                continue;
            }
            for (ResolvedResearchPathGraph.RequirementGroup group : node.groups()) {
                if (!requiresSelectedProof(group)) {
                    continue;
                }
                ResourceLocation prerequisiteId = selectedPrerequisiteId(
                        graph, group, support, canonical);
                if (support.add(prerequisiteId)) {
                    budget.countClosureReferences(1L);
                }
            }
        }

        List<SelectedRequirement> selected = new ArrayList<>();
        for (int nodeIndex : graph.topologicalOrder()) {
            ResolvedResearchPathGraph.Node node = graph.nodes().get(nodeIndex);
            if (!support.contains(node.blueprintId())) {
                continue;
            }
            for (int groupOrdinal = 0; groupOrdinal < node.groups().size(); groupOrdinal++) {
                ResolvedResearchPathGraph.RequirementGroup group = node.groups().get(groupOrdinal);
                if (!requiresSelectedProof(group)) {
                    continue;
                }
                ResourceLocation prerequisiteId = selectedPrerequisiteId(
                        graph, group, support, canonical);
                selected.add(new SelectedRequirement(
                        node.blueprintId(), groupOrdinal, prerequisiteId));
                budget.countCanonicalWork(1L);
            }
        }
        List<ResourceLocation> orderedSupport = graph.topologicalOrder().stream()
                .map(index -> graph.nodes().get(index).blueprintId())
                .filter(support::contains)
                .toList();
        if (orderedSupport.size() != support.size()) {
            throw new IllegalArgumentException(
                    "selected route contains support outside the resolved graph");
        }
        return new SelectedUnlockSolution(
                orderedSupport, solution.nodes(), selected);
    }

    private static boolean requiresSelectedProof(
            ResolvedResearchPathGraph.RequirementGroup group) {
        return group.state()
                        == ResolvedResearchPathGraph.GroupState.REQUIRES_ALTERNATIVE_SELECTION
                || group.state()
                        == ResolvedResearchPathGraph.GroupState.SATISFIED_BY_CONNECTED_SUPPORT;
    }

    private static ResourceLocation selectedPrerequisiteId(
            ResolvedResearchPathGraph.Graph graph,
            ResolvedResearchPathGraph.RequirementGroup group,
            Set<ResourceLocation> support,
            Comparator<ResourceLocation> canonical) {
        boolean requiresSelection = group.state()
                == ResolvedResearchPathGraph.GroupState.REQUIRES_ALTERNATIVE_SELECTION;
        return group.alternatives().stream()
                .filter(ResolvedResearchPathGraph.Alternative::usable)
                .map(alternative -> graph.nodes().get(alternative.nodeIndex()))
                .filter(alternative -> requiresSelection
                        ? support.contains(alternative.blueprintId())
                        : alternative.state()
                                == ResolvedResearchPathGraph.NodeState.LEARNED_CONNECTED
                                && alternative.connected())
                .map(ResolvedResearchPathGraph.Node::blueprintId)
                .min(canonical)
                .orElseThrow(() -> new IllegalArgumentException(
                        "selected route does not prove a requirement group"));
    }

    private static Result selectPlan(
            ResourceLocation targetId,
            List<RouteOption> options,
            Planner planner,
            boolean creativePlayer) {
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
                Plan plan = buildPlan(ordered, creativePlayer, planner.budget);
                Selection candidate = new Selection(
                        plan,
                        totalIngredientCount(plan.ingredients()),
                        canonicalIds(plan.nodes()));
                planner.countCanonicalWork(plan.nodes().size());
                if (selected == null || compareSelections(candidate, selected) < 0) {
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
            Selection right) {
        int comparison = Integer.compare(left.plan().pointCost(), right.plan().pointCost());
        if (comparison != 0) {
            return comparison;
        }
        comparison = Integer.compare(left.materialCount(), right.materialCount());
        if (comparison != 0) {
            return comparison;
        }
        return left.canonicalIds().compareTo(right.canonicalIds());
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

    /**
     * Compares support sets at their first differing canonical ID, preferring
     * the route that contains that ID. Unlike ordinary variable-length list
     * lexicography, this ordering is preserved when both routes gain the same
     * downstream node.
     */
    private static int compareSupportRoutes(RouteOption left, RouteOption right) {
        var leftIds = left.nodes().keySet().iterator();
        var rightIds = right.nodes().keySet().iterator();
        ResourceLocation leftId = leftIds.hasNext() ? leftIds.next() : null;
        ResourceLocation rightId = rightIds.hasNext() ? rightIds.next() : null;
        Comparator<ResourceLocation> canonical = Comparator.comparing(ResourceLocation::toString);
        while (leftId != null && rightId != null) {
            int comparison = canonical.compare(leftId, rightId);
            if (comparison < 0) {
                return -1;
            }
            if (comparison > 0) {
                return 1;
            }
            leftId = leftIds.hasNext() ? leftIds.next() : null;
            rightId = rightIds.hasNext() ? rightIds.next() : null;
        }
        if (leftId != null) {
            return -1;
        }
        return rightId != null ? 1 : 0;
    }

    static int compareCanonicalSupportIds(int[] left, int[] right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("canonical support IDs cannot be null");
        }
        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < left.length && rightIndex < right.length) {
            int leftId = left[leftIndex];
            int rightId = right[rightIndex];
            if (leftId < rightId) {
                return -1;
            }
            if (leftId > rightId) {
                return 1;
            }
            leftIndex++;
            rightIndex++;
        }
        if (leftIndex < left.length) {
            return -1;
        }
        return rightIndex < right.length ? 1 : 0;
    }

    static Plan buildPlan(
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> nodes,
            boolean creativePlayer) {
        return buildPlan(nodes, creativePlayer, null);
    }

    static Plan buildPlan(
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> nodes,
            boolean creativePlayer,
            PlanningBudget budget) {
        return buildPlan(nodes, List.copyOf(nodes.keySet()), creativePlayer, budget);
    }

    static Plan buildPlan(
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> nodes,
            List<ResourceLocation> supportIds,
            boolean creativePlayer,
            PlanningBudget budget) {
        SelectedUnlockSolution solution = selectSolution(
                nodes, supportIds, creativePlayer, budget);
        return new Plan(solution, quote(solution, budget));
    }

    private static SelectedUnlockSolution selectSolution(
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> nodes,
            List<ResourceLocation> supportIds,
            boolean creativePlayer,
            PlanningBudget budget) {
        List<PlannedNode> plannedNodes = new ArrayList<>(nodes.size());
        if (budget != null) {
            budget.countClosureReferences(supportIds.size());
        }
        for (BlueprintResearchPolicy policy : nodes.values()) {
            if (budget != null) {
                budget.countClosureReferences(1L);
            }
            if (!policy.learned()) {
                boolean bypassed = creativePlayer && policy.creativeBypassesCost();
                plannedNodes.add(new PlannedNode(policy.blueprintId(), policy, bypassed));
            }
        }
        return new SelectedUnlockSolution(supportIds, plannedNodes);
    }

    private static RouteQuote quote(
            SelectedUnlockSolution solution,
            PlanningBudget budget) {
        int pointCost = 0;
        boolean anyConfiguredCost = false;
        boolean everyConfiguredCostBypassed = true;
        Map<String, MutableRequirement> requirements = new TreeMap<>();
        for (PlannedNode node : solution.nodes()) {
            BlueprintResearchPolicy policy = node.policy();
            boolean bypassed = node.costBypassed();
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
                    if (budget != null) {
                        budget.checkpoint();
                    }
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
        }
        if (pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("path RP cost exceeds the supported balance");
        }
        List<ResearchIngredientPlanner.Requirement> combinedRequirements = requirements.values()
                .stream()
                .map(value -> new ResearchIngredientPlanner.Requirement(
                        value.items, value.tag, value.count))
                .toList();
        return new RouteQuote(
                pointCost,
                combinedRequirements,
                anyConfiguredCost && everyConfiguredCostBypassed);
    }

    /** Computes a read-only, overlap-safe inventory allocation for a fixed route quote. */
    public static Optional<InventoryAllocation> allocateInventory(
            Plan plan,
            List<ItemStack> stacks) {
        if (plan == null) {
            return Optional.empty();
        }
        return ResearchIngredientPlanner.allocation(stacks, plan.quote().ingredients())
                .map(allocation -> new InventoryAllocation(plan.quote(), allocation));
    }

    /**
     * Binds a selected solution and quote to the exact slot decrements required
     * for an atomic commit. An incomplete allocation cannot become a transaction.
     */
    public static Optional<TransactionPlan> prepareTransaction(
            Plan plan,
            List<ItemStack> stacks) {
        if (plan == null) {
            return Optional.empty();
        }
        return ResearchIngredientPlanner.plan(stacks, plan.quote().ingredients())
                .map(ingredientPlan -> new TransactionPlan(
                        plan.solution(), plan.quote(), ingredientPlan));
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
        private final ResearchPathAuthority authority;
        private final PlanningBudget budget;
        private final int pointBalance;
        private final Map<ResourceLocation, Frontier> memo = new HashMap<>();

        private Planner(
                IPlayerRecipeData playerData,
                Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
                Predicate<ResourceLocation> progressionExempt,
                ResearchPathAuthority authority,
                PlanningBudget budget) {
            this.playerData = playerData;
            this.policyResolver = policyResolver;
            this.progressionExempt = progressionExempt;
            this.authority = authority;
            this.budget = budget;
            this.pointBalance = playerData.getResearchPoints();
        }

        private Frontier visit(ResourceLocation blueprintId, Set<ResourceLocation> visiting) {
            if (!validId(blueprintId)) {
                return Frontier.failure(BlueprintResearchService.Status.INVALID_INPUT);
            }
            if (progressionExempt.test(blueprintId)) {
                return Frontier.success(List.of(RouteOption.EMPTY));
            }
            if (visiting.size() >= BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH) {
                return Frontier.failure(BlueprintResearchService.Status.PATH_TOO_LARGE);
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
                BlueprintResearchService.Status invalid = validatePolicy(
                        blueprintId,
                        policy,
                        playerData,
                        pointBalance,
                        progressionExempt);
                if (invalid == null) {
                    invalid = authority.validate(policy).orElse(null);
                }
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
                    } else if (alternativeFrontier.status()
                            == BlueprintResearchService.Status.TECH_TREE_UNAVAILABLE
                            && fallback != BlueprintResearchService.Status.PATH_TOO_LARGE) {
                        fallback = BlueprintResearchService.Status.TECH_TREE_UNAVAILABLE;
                    } else if (alternativeFrontier.status()
                            == BlueprintResearchService.Status.UNSATISFIABLE
                            && fallback != BlueprintResearchService.Status.PATH_TOO_LARGE
                            && fallback != BlueprintResearchService.Status.TECH_TREE_UNAVAILABLE) {
                        fallback = BlueprintResearchService.Status.UNSATISFIABLE;
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
                        countMerge();
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
            budget.countRouteState();
        }

        private void countMerge() {
            budget.countRouteMerge();
        }

        private void countDominanceComparison() {
            budget.countDominanceComparison();
        }

        private void countClosureReferences(int count) {
            budget.countClosureReferences(count);
        }

        private void countCanonicalWork(int count) {
            budget.countCanonicalWork(count);
        }

        private void addNondominated(List<RouteOption> frontier, RouteOption candidate) {
            countState();
            countClosureReferences(candidate.nodes().size());
            Set<ResourceLocation> candidateIds = candidate.purchaseIds();
            for (int index = 0; index < frontier.size(); index++) {
                countDominanceComparison();
                RouteOption existing = frontier.get(index);
                Set<ResourceLocation> existingIds = existing.purchaseIds();
                if (candidateIds.equals(existingIds)) {
                    countCanonicalWork(Math.addExact(
                            candidate.nodes().size(), existing.nodes().size()));
                    if (compareSupportRoutes(candidate, existing) < 0) {
                        frontier.remove(index);
                        break;
                    }
                    return;
                }
                if (candidateIds.containsAll(existingIds)) {
                    return;
                }
            }
            for (int index = frontier.size() - 1; index >= 0; index--) {
                countDominanceComparison();
                if (frontier.get(index).purchaseIds().containsAll(candidateIds)) {
                    frontier.remove(index);
                }
            }
            frontier.add(candidate);
            budget.checkFrontierSize(frontier.size());
        }

        private List<RouteOption> stable(List<RouteOption> options) {
            countCanonicalWork(options.size());
            return options.stream()
                    .sorted((left, right) -> {
                        int comparison = Integer.compare(
                                left.purchaseCount(), right.purchaseCount());
                        if (comparison != 0) {
                            return comparison;
                        }
                        countCanonicalWork(Math.addExact(
                                left.nodes().size(), right.nodes().size()));
                        return compareSupportRoutes(left, right);
                    })
                    .toList();
        }

        private LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> topologicalOrder(
                ResourceLocation targetId,
                Map<ResourceLocation, BlueprintResearchPolicy> selected) {
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> ordered =
                    new LinkedHashMap<>();
            orderVisit(targetId, selected, new LinkedHashSet<>(), ordered);
            countCanonicalWork(ordered.size());
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

    /** Shared acquisition-policy validation for the legacy and indexed planners. */
    static BlueprintResearchService.Status validatePolicy(
            ResourceLocation blueprintId,
            BlueprintResearchPolicy policy,
            IPlayerRecipeData playerData,
            int pointBalance,
            Predicate<ResourceLocation> progressionExempt) {
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

    /** The player-state-specific support proof and ordered nodes selected for purchase. */
    public record SelectedUnlockSolution(
            List<ResourceLocation> supportIds,
            List<PlannedNode> nodes,
            List<SelectedRequirement> selectedRequirements) {
        public SelectedUnlockSolution {
            supportIds = supportIds == null
                    ? List.of()
                    : List.copyOf(supportIds);
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            selectedRequirements = selectedRequirements == null
                    ? List.of()
                    : List.copyOf(selectedRequirements);
            Set<ResourceLocation> support = new LinkedHashSet<>();
            Set<ResourceLocation> purchases = new LinkedHashSet<>();
            Set<String> selectedGroups = new LinkedHashSet<>();
            if (nodes.isEmpty()
                    || nodes.size() > MAX_UNLOCKS_PER_PURCHASE
                    || supportIds.isEmpty()
                    || supportIds.size() > MAX_SUPPORT_NODES
                    || supportIds.stream().anyMatch(id -> !validId(id))
                    || supportIds.stream().anyMatch(id -> !support.add(id))
                    || nodes.stream().anyMatch(java.util.Objects::isNull)
                    || nodes.stream().anyMatch(node -> node.policy().learned())
                    || nodes.stream().anyMatch(node -> !purchases.add(node.blueprintId()))
                    || !support.containsAll(purchases)
                    || selectedRequirements.size()
                            > BlueprintResearchSnapshot.MAX_TOTAL_PREREQUISITES
                    || selectedRequirements.stream().anyMatch(java.util.Objects::isNull)
                    || selectedRequirements.stream().anyMatch(requirement ->
                            !support.contains(requirement.dependentId())
                                    || !support.contains(requirement.prerequisiteId())
                                    || !selectedGroups.add(
                                            requirement.dependentId() + "\u0000"
                                                    + requirement.groupOrdinal()))) {
                throw new IllegalArgumentException("invalid selected research unlock solution");
            }
        }

        public SelectedUnlockSolution(
                List<ResourceLocation> supportIds,
                List<PlannedNode> nodes) {
            this(supportIds, nodes, List.of());
        }

        public SelectedUnlockSolution(List<PlannedNode> nodes) {
            this(
                    nodes == null
                            ? List.of()
                            : nodes.stream().filter(java.util.Objects::nonNull)
                                    .map(PlannedNode::blueprintId).toList(),
                    nodes,
                    List.of());
        }

        public int unlockCount() {
            return nodes.size();
        }
    }

    /** One stable edge selected to satisfy an ordered prerequisite group. */
    public record SelectedRequirement(
            ResourceLocation dependentId,
            int groupOrdinal,
            ResourceLocation prerequisiteId) {
        public SelectedRequirement {
            if (!validId(dependentId) || !validId(prerequisiteId)
                    || dependentId.equals(prerequisiteId)
                    || groupOrdinal < 0 || groupOrdinal >= ResearchRequirements.MAX_GROUPS) {
                throw new IllegalArgumentException("invalid selected research requirement");
            }
        }
    }

    /** RP and material cost derived solely from one selected unlock solution. */
    public record RouteQuote(
            int pointCost,
            List<ResearchIngredientPlanner.Requirement> ingredients,
            boolean costBypassed) {
        public RouteQuote {
            ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
            int totalIngredients = 0;
            try {
                for (ResearchIngredientPlanner.Requirement ingredient : ingredients) {
                    if (ingredient == null) {
                        throw new IllegalArgumentException("invalid research route quote");
                    }
                    totalIngredients = Math.addExact(totalIngredients, ingredient.count());
                }
            } catch (ArithmeticException exception) {
                throw new IllegalArgumentException("invalid research route quote", exception);
            }
            if (pointCost < 0
                    || pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                    || totalIngredients > ResearchIngredientPlanner.MAX_TOTAL_REQUIREMENT_COUNT) {
                throw new IllegalArgumentException("invalid research route quote");
            }
        }

        public int totalMaterialUnits() {
            return ingredients.stream()
                    .mapToInt(ResearchIngredientPlanner.Requirement::count)
                    .sum();
        }
    }

    /** Read-only allocation of an immutable quote against one inventory snapshot. */
    public static final class InventoryAllocation {
        private final RouteQuote quote;
        private final ResearchIngredientPlanner.Allocation allocation;

        private InventoryAllocation(
                RouteQuote quote,
                ResearchIngredientPlanner.Allocation allocation) {
            if (quote == null
                    || allocation == null
                    || allocation.ingredientCount() != quote.ingredients().size()
                    || allocation.totalRequired() != quote.totalMaterialUnits()) {
                throw new IllegalArgumentException("inventory allocation does not match route quote");
            }
            this.quote = quote;
            this.allocation = allocation;
        }

        public RouteQuote quote() {
            return quote;
        }

        public ResearchIngredientPlanner.Allocation allocation() {
            return allocation;
        }

        public boolean complete() {
            return allocation.complete();
        }
    }

    /** Complete material decrements plus the exact solution and quote to commit atomically. */
    public static final class TransactionPlan {
        private final SelectedUnlockSolution solution;
        private final RouteQuote quote;
        private final ResearchIngredientPlanner.Plan ingredientPlan;

        private TransactionPlan(
                SelectedUnlockSolution solution,
                RouteQuote quote,
                ResearchIngredientPlanner.Plan ingredientPlan) {
            if (solution == null
                    || quote == null
                    || ingredientPlan == null
                    || !quote.equals(ResearchPathUnlockPlanner.quote(solution, null))
                    || ingredientPlan.totalConsumed() != quote.totalMaterialUnits()) {
                throw new IllegalArgumentException("transaction plan does not match route quote");
            }
            this.solution = solution;
            this.quote = quote;
            this.ingredientPlan = ingredientPlan;
        }

        public SelectedUnlockSolution solution() {
            return solution;
        }

        public RouteQuote quote() {
            return quote;
        }

        public ResearchIngredientPlanner.Plan ingredientPlan() {
            return ingredientPlan;
        }

        public int unlockCount() {
            return solution.unlockCount();
        }
    }

    /** Compatibility facade preserving the original planner result API. */
    public record Plan(SelectedUnlockSolution solution, RouteQuote quote) {
        public Plan {
            if (solution == null
                    || quote == null
                    || !quote.equals(ResearchPathUnlockPlanner.quote(solution, null))) {
                throw new IllegalArgumentException("invalid research path unlock plan");
            }
        }

        public Plan(
                List<PlannedNode> nodes,
                int pointCost,
                List<ResearchIngredientPlanner.Requirement> ingredients,
                boolean costBypassed) {
            this(
                    new SelectedUnlockSolution(nodes),
                    new RouteQuote(pointCost, ingredients, costBypassed));
        }

        public List<PlannedNode> nodes() {
            return solution.nodes();
        }

        public int pointCost() {
            return quote.pointCost();
        }

        public List<ResearchIngredientPlanner.Requirement> ingredients() {
            return quote.ingredients();
        }

        public boolean costBypassed() {
            return quote.costBypassed();
        }

        public int unlockCount() {
            return solution.unlockCount();
        }
    }

    public record PlannedNode(
            ResourceLocation blueprintId,
            BlueprintResearchPolicy policy,
            boolean costBypassed) {
        public PlannedNode {
            if (!validId(blueprintId) || policy == null
                    || !blueprintId.equals(policy.blueprintId())
                    || (costBypassed && !policy.creativeBypassesCost())) {
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
            purchaseIds = stable.entrySet().stream()
                    .filter(entry -> !entry.getValue().learned())
                    .map(Map.Entry::getKey)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
            nodes = Collections.unmodifiableMap(new LinkedHashMap<>(stable));
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

        private int purchaseCount() {
            return purchaseIds.size();
        }
    }

    private record Selection(
            Plan plan,
            int materialCount,
            String canonicalIds) {
    }

    public enum RouteSelectionPolicy {
        STABLE_MINIMUM_UNLOCKS
    }

    static record PlanningLimits(
            long policyLookups,
            long routeStates,
            long routeMerges,
            long dominanceComparisons,
            long closureNodeReferences,
            long canonicalWork,
            int frontierOptions,
            long bitSetWordWork,
            long retainedLabels,
            long retainedBitWords,
            long emergencyTimeoutNanos) {
        private static final PlanningLimits DEFAULT = new PlanningLimits(
                MAX_POLICY_LOOKUPS,
                MAX_ROUTE_STATES,
                MAX_ROUTE_MERGES,
                MAX_DOMINANCE_COMPARISONS,
                MAX_CLOSURE_NODE_REFERENCES,
                MAX_CANONICAL_WORK,
                MAX_FRONTIER_OPTIONS,
                MAX_BITSET_WORD_WORK,
                MAX_RETAINED_LABELS,
                MAX_RETAINED_BIT_WORDS,
                EMERGENCY_TIMEOUT_NANOS);

        PlanningLimits(
                long policyLookups,
                long routeStates,
                long routeMerges,
                long dominanceComparisons,
                long closureNodeReferences,
                long canonicalWork,
                int frontierOptions,
                long emergencyTimeoutNanos) {
            this(
                    policyLookups,
                    routeStates,
                    routeMerges,
                    dominanceComparisons,
                    closureNodeReferences,
                    canonicalWork,
                    frontierOptions,
                    MAX_BITSET_WORD_WORK,
                    MAX_RETAINED_LABELS,
                    MAX_RETAINED_BIT_WORDS,
                    emergencyTimeoutNanos);
        }

        PlanningLimits {
            if (policyLookups < 1L || routeStates < 1L || routeMerges < 1L
                    || dominanceComparisons < 1L || closureNodeReferences < 1L
                    || canonicalWork < 1L || frontierOptions < 1
                    || bitSetWordWork < 1L
                    || retainedLabels < 1L || retainedBitWords < 1L
                    || emergencyTimeoutNanos < 1L) {
                throw new IllegalArgumentException("research planning limits are invalid");
            }
        }
    }

    static final class PlanningBudget {
        private final PlanningLimits limits;
        private final LongSupplier nanoClock;
        private final long startedAtNanos;
        private long policyLookups;
        private long routeStates;
        private long routeMerges;
        private long dominanceComparisons;
        private long closureNodeReferences;
        private long canonicalWork;
        private long bitSetWordWork;
        private long retainedLabels;
        private long retainedBitWords;
        private long workSinceTimeCheck;

        PlanningBudget(PlanningLimits limits, LongSupplier nanoClock) {
            if (limits == null || nanoClock == null) {
                throw new IllegalArgumentException("research planning budget is invalid");
            }
            this.limits = limits;
            this.nanoClock = nanoClock;
            this.startedAtNanos = nanoClock.getAsLong();
        }

        void countPolicyLookup() {
            policyLookups = Math.addExact(policyLookups, 1L);
            checkpoint();
            if (policyLookups > limits.policyLookups()) {
                throw new RouteComplexityException();
            }
        }

        void countRouteState() {
            routeStates = Math.addExact(routeStates, 1L);
            checkpoint();
            if (routeStates > limits.routeStates()) {
                throw new RouteComplexityException();
            }
        }

        void countRouteMerge() {
            routeMerges = Math.addExact(routeMerges, 1L);
            checkpoint();
            if (routeMerges > limits.routeMerges()) {
                throw new RouteComplexityException();
            }
        }

        void countDominanceComparison() {
            dominanceComparisons = Math.addExact(dominanceComparisons, 1L);
            checkpoint();
            if (dominanceComparisons > limits.dominanceComparisons()) {
                throw new RouteComplexityException();
            }
        }

        void countClosureReferences(long count) {
            if (count < 0L) {
                throw new IllegalArgumentException("closure work cannot be negative");
            }
            closureNodeReferences = Math.addExact(closureNodeReferences, count);
            checkpoint();
            if (closureNodeReferences > limits.closureNodeReferences()) {
                throw new RouteComplexityException();
            }
        }

        void countCanonicalWork(long count) {
            if (count < 0L) {
                throw new IllegalArgumentException("canonical work cannot be negative");
            }
            canonicalWork = Math.addExact(canonicalWork, count);
            checkpoint();
            if (canonicalWork > limits.canonicalWork()) {
                throw new RouteComplexityException();
            }
        }

        void checkFrontierSize(int size) {
            checkpoint();
            if (size > limits.frontierOptions()) {
                throw new RouteComplexityException();
            }
        }

        void retainGeneralLabel(long bitWords) {
            if (bitWords < 0L) {
                throw new IllegalArgumentException("retained bit words cannot be negative");
            }
            retainedLabels = Math.addExact(retainedLabels, 1L);
            retainedBitWords = Math.addExact(retainedBitWords, bitWords);
            checkpoint();
            if (retainedLabels > limits.retainedLabels()
                    || retainedBitWords > limits.retainedBitWords()) {
                throw new RouteComplexityException();
            }
        }

        void countBitSetWordWork(long words) {
            if (words < 0L) {
                throw new IllegalArgumentException("bit-set work cannot be negative");
            }
            bitSetWordWork = Math.addExact(bitSetWordWork, words);
            checkpoint();
            if (bitSetWordWork > limits.bitSetWordWork()) {
                throw new RouteComplexityException();
            }
        }

        void releaseGeneralLabels(long labels, long bitWords) {
            if (labels < 0L || bitWords < 0L
                    || labels > retainedLabels || bitWords > retainedBitWords) {
                throw new IllegalArgumentException(
                        "general solver retained-memory accounting is inconsistent");
            }
            retainedLabels -= labels;
            retainedBitWords -= bitWords;
            checkpoint();
        }

        void checkpoint() {
            workSinceTimeCheck = Math.addExact(workSinceTimeCheck, 1L);
            if (workSinceTimeCheck < TIME_CHECK_INTERVAL) {
                return;
            }
            workSinceTimeCheck = 0L;
            checkDeadline();
        }

        void checkDeadline() {
            if (nanoClock.getAsLong() - startedAtNanos
                    > limits.emergencyTimeoutNanos()) {
                throw new RouteComplexityException();
            }
        }
    }

    static final class RequestInputs {
        private final Function<ResourceLocation, BlueprintResearchPolicy> policyResolver;
        private final Predicate<ResourceLocation> progressionExempt;

        RequestInputs(
                Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
                Predicate<ResourceLocation> progressionExempt,
                PlanningBudget budget) {
            if (policyResolver == null || progressionExempt == null || budget == null) {
                throw new IllegalArgumentException("research planning inputs are invalid");
            }
            Map<ResourceLocation, CachedPolicy> policies = new HashMap<>();
            this.policyResolver = id -> {
                CachedPolicy cached = policies.get(id);
                if (cached == null) {
                    budget.countPolicyLookup();
                    try {
                        cached = new CachedPolicy(policyResolver.apply(id), null);
                    } catch (RuntimeException exception) {
                        cached = new CachedPolicy(null, exception);
                    }
                    policies.put(id, cached);
                }
                if (cached.failure() != null) {
                    throw cached.failure();
                }
                return cached.policy();
            };
            Map<ResourceLocation, CachedExemption> exemptions = new HashMap<>();
            this.progressionExempt = id -> {
                CachedExemption cached = exemptions.get(id);
                if (cached == null) {
                    budget.checkpoint();
                    try {
                        cached = new CachedExemption(progressionExempt.test(id), null);
                    } catch (RuntimeException exception) {
                        cached = new CachedExemption(false, exception);
                    }
                    exemptions.put(id, cached);
                }
                if (cached.failure() != null) {
                    throw cached.failure();
                }
                return cached.exempt();
            };
        }

        Function<ResourceLocation, BlueprintResearchPolicy> policyResolver() {
            return policyResolver;
        }

        Predicate<ResourceLocation> progressionExempt() {
            return progressionExempt;
        }
    }

    private record CachedPolicy(
            BlueprintResearchPolicy policy,
            RuntimeException failure) {
    }

    private record CachedExemption(
            boolean exempt,
            RuntimeException failure) {
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

    static final class RouteComplexityException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
