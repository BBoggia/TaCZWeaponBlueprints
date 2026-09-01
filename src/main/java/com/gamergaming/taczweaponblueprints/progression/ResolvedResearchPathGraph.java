package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;

import net.minecraft.resources.ResourceLocation;

/**
 * Canonical, request-local input for the specialized research-path solvers.
 *
 * <p>Node indices are assigned by blueprint ID and are independent of policy
 * discovery order. Prerequisite-first topological order is stored separately
 * for the specialized solvers and the bounded compatibility fallback.</p>
 */
final class ResolvedResearchPathGraph {
    static final int MAX_GRAPH_NODES = PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
    static final int MAX_GRAPH_EDGES = MAX_GRAPH_NODES * 64;
    static final long MAX_CLASSIFICATION_BIT_WORDS = 33_554_432L;
    private ResolvedResearchPathGraph() {
    }

    static Result build(
            ResourceLocation targetId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt,
            ResearchPathAuthority authority) {
        return buildWithControls(
                targetId,
                playerData,
                policyResolver,
                progressionExempt,
                authority,
                BuildLimits.DEFAULT,
                System::nanoTime);
    }

    static Result buildWithControls(
            ResourceLocation targetId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt,
            ResearchPathAuthority authority,
            BuildLimits limits,
            LongSupplier nanoClock) {
        if (!validId(targetId) || playerData == null || policyResolver == null
                || progressionExempt == null || authority == null || limits == null
                || nanoClock == null) {
            return Result.failure(
                    BlueprintResearchService.Status.INVALID_INPUT,
                    Diagnostics.empty(GraphShape.INVALID));
        }
        ResearchPathUnlockPlanner.PlanningBudget budget;
        ResearchPathUnlockPlanner.RequestInputs inputs;
        try {
            budget = new ResearchPathUnlockPlanner.PlanningBudget(
                    new ResearchPathUnlockPlanner.PlanningLimits(
                            limits.maximumPolicyLookups(),
                            ResearchPathUnlockPlanner.MAX_ROUTE_STATES,
                            ResearchPathUnlockPlanner.MAX_ROUTE_MERGES,
                            ResearchPathUnlockPlanner.MAX_DOMINANCE_COMPARISONS,
                            ResearchPathUnlockPlanner.MAX_CLOSURE_NODE_REFERENCES,
                            ResearchPathUnlockPlanner.MAX_CANONICAL_WORK,
                            ResearchPathUnlockPlanner.MAX_FRONTIER_OPTIONS,
                            limits.emergencyTimeoutNanos()),
                    nanoClock);
            inputs = new ResearchPathUnlockPlanner.RequestInputs(
                    policyResolver, progressionExempt, budget);
        } catch (RuntimeException exception) {
            return Result.failure(
                    BlueprintResearchService.Status.POLICY_UNAVAILABLE,
                    Diagnostics.empty(GraphShape.INVALID));
        }
        return buildWithBudget(
                targetId,
                playerData,
                inputs.policyResolver(),
                inputs.progressionExempt(),
                authority,
                limits,
                budget);
    }

    static Result buildWithBudget(
            ResourceLocation targetId,
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt,
            ResearchPathAuthority authority,
            BuildLimits limits,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (!validId(targetId) || playerData == null || policyResolver == null
                || progressionExempt == null || authority == null || limits == null
                || budget == null) {
            return Result.failure(
                    BlueprintResearchService.Status.INVALID_INPUT,
                    Diagnostics.empty(GraphShape.INVALID));
        }
        try {
            if (progressionExempt.test(targetId)) {
                return Result.failure(
                        BlueprintResearchService.Status.POLICY_INELIGIBLE,
                        Diagnostics.empty(GraphShape.INVALID));
            }
            return new Builder(
                    targetId,
                    playerData,
                    policyResolver,
                    progressionExempt,
                    authority,
                    limits,
                    budget).build();
        } catch (ResearchPathUnlockPlanner.RouteComplexityException exception) {
            return Result.failure(
                    BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                    Diagnostics.empty(GraphShape.INVALID));
        } catch (BuildComplexityException exception) {
            return Result.failure(
                    BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                    exception.diagnostics());
        } catch (DepthLimitException exception) {
            return failureWithinBudget(
                    BlueprintResearchService.Status.PATH_TOO_LARGE,
                    exception.diagnostics(),
                    budget);
        } catch (CycleException exception) {
            return failureWithinBudget(
                    BlueprintResearchService.Status.POLICY_INELIGIBLE,
                    exception.diagnostics(),
                    budget);
        } catch (RuntimeException exception) {
            return failureWithinBudget(
                    BlueprintResearchService.Status.POLICY_UNAVAILABLE,
                    Diagnostics.empty(GraphShape.INVALID),
                    budget);
        }
    }

    private static Result failureWithinBudget(
            BlueprintResearchService.Status status,
            Diagnostics diagnostics,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        try {
            budget.checkDeadline();
            return Result.failure(status, diagnostics);
        } catch (ResearchPathUnlockPlanner.RouteComplexityException exception) {
            return Result.failure(
                    BlueprintResearchService.Status.ROUTE_TOO_COMPLEX,
                    diagnostics);
        }
    }

    private static boolean validId(ResourceLocation id) {
        return id != null
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    enum GraphShape {
        INVALID,
        MANDATORY_DAG,
        OR_PATH_DAG,
        SEPARABLE_AND_OR_DAG,
        GENERAL_AND_OR_DAG
    }

    enum NodeState {
        PROGRESSION_EXEMPT,
        LEARNED_CONNECTED,
        LEARNED_DISCONNECTED,
        PURCHASABLE,
        UNUSABLE
    }

    enum GroupState {
        SATISFIED_BY_EXEMPTION,
        SATISFIED_BY_CONNECTED_SUPPORT,
        REQUIRES_ALTERNATIVE_SELECTION,
        UNSATISFIABLE
    }

    record Alternative(
            int nodeIndex,
            NodeState state,
            Optional<BlueprintResearchService.Status> failure) {
        Alternative {
            failure = failure == null ? Optional.empty() : failure;
            if (nodeIndex < 0 || state == null
                    || (state == NodeState.UNUSABLE) != failure.isPresent()) {
                throw new IllegalArgumentException(
                        "normalized research alternative is invalid");
            }
        }

        boolean usable() {
            return failure.isEmpty();
        }
    }

    record RequirementGroup(
            GroupState state,
            List<Alternative> alternatives,
            Optional<BlueprintResearchService.Status> failure) {
        RequirementGroup {
            alternatives = alternatives == null ? List.of() : List.copyOf(alternatives);
            failure = failure == null ? Optional.empty() : failure;
            if (state == null || alternatives.isEmpty()
                    || alternatives.stream().anyMatch(java.util.Objects::isNull)
                    || (state == GroupState.UNSATISFIABLE) != failure.isPresent()) {
                throw new IllegalArgumentException(
                        "normalized research requirement group is invalid");
            }
        }

        List<Integer> usableAlternativeIndices() {
            return alternatives.stream()
                    .filter(Alternative::usable)
                    .map(Alternative::nodeIndex)
                    .toList();
        }
    }

    record Node(
            int index,
            ResourceLocation blueprintId,
            Optional<BlueprintResearchPolicy> policy,
            NodeState state,
            Optional<ResearchPathAuthority.RootProvenance> rootProvenance,
            boolean routeViable,
            boolean connected,
            List<RequirementGroup> groups,
            Optional<BlueprintResearchService.Status> failure) {
        Node {
            policy = policy == null ? Optional.empty() : policy;
            rootProvenance = rootProvenance == null ? Optional.empty() : rootProvenance;
            groups = groups == null ? List.of() : List.copyOf(groups);
            failure = failure == null ? Optional.empty() : failure;
            if (index < 0 || !validId(blueprintId) || state == null
                    || groups.stream().anyMatch(java.util.Objects::isNull)
                    || routeViable == failure.isPresent()
                    || connected && state != NodeState.PROGRESSION_EXEMPT
                            && state != NodeState.LEARNED_CONNECTED
                    || rootProvenance.isPresent() && !groups.isEmpty()) {
                throw new IllegalArgumentException("normalized research node is invalid");
            }
        }
    }

    record Graph(
            ResourceLocation targetId,
            int targetIndex,
            List<Node> nodes,
            Map<ResourceLocation, Integer> nodeIndices,
            List<Integer> topologicalOrder,
            GraphShape shape,
            Diagnostics diagnostics) {
        Graph {
            nodes = nodes == null ? List.of() : List.copyOf(nodes);
            nodeIndices = nodeIndices == null ? Map.of() : Map.copyOf(nodeIndices);
            topologicalOrder = topologicalOrder == null
                    ? List.of()
                    : List.copyOf(topologicalOrder);
            int stableNodeCount = nodes.size();
            if (!validId(targetId) || targetIndex < 0 || targetIndex >= nodes.size()
                    || shape == null || shape == GraphShape.INVALID || diagnostics == null
                    || nodes.size() != nodeIndices.size()
                    || nodes.size() != topologicalOrder.size()
                    || !targetId.equals(nodes.get(targetIndex).blueprintId())
                    || nodes.get(targetIndex).failure().isPresent()) {
                throw new IllegalArgumentException("resolved research graph is invalid");
            }
            for (int index = 0; index < nodes.size(); index++) {
                if (nodes.get(index).index() != index
                        || !Integer.valueOf(index).equals(
                                nodeIndices.get(nodes.get(index).blueprintId()))) {
                    throw new IllegalArgumentException(
                            "resolved research graph indices are inconsistent");
                }
            }
            if (new LinkedHashSet<>(topologicalOrder).size() != nodes.size()
                    || topologicalOrder.stream().anyMatch(
                            index -> index < 0 || index >= stableNodeCount)) {
                throw new IllegalArgumentException(
                        "resolved research graph order is inconsistent");
            }
        }

        Node target() {
            return nodes.get(targetIndex);
        }

        Optional<Node> node(ResourceLocation blueprintId) {
            Integer index = nodeIndices.get(blueprintId);
            return index == null ? Optional.empty() : Optional.of(nodes.get(index));
        }
    }

    record Diagnostics(
            GraphShape shape,
            int nodeCount,
            int edgeCount,
            int policyLookups,
            int rootCount,
            int activeNodeCount,
            int activeGroupCount,
            int choiceGroupCount,
            int maximumDepth,
            int maximumActiveGroupsPerNode,
            int maximumUsableAlternativesPerGroup,
            int maximumGroupOverlapWidth,
            int generalSearchNodeCount,
            long classificationBitWords,
            boolean cycleDetected) {
        Diagnostics {
            if (shape == null || nodeCount < 0 || edgeCount < 0 || policyLookups < 0
                    || rootCount < 0 || activeNodeCount < 0 || activeGroupCount < 0
                    || choiceGroupCount < 0 || maximumDepth < 0
                    || maximumActiveGroupsPerNode < 0
                    || maximumUsableAlternativesPerGroup < 0
                    || maximumGroupOverlapWidth < 0 || generalSearchNodeCount < 0
                    || classificationBitWords < 0L) {
                throw new IllegalArgumentException(
                        "resolved research graph diagnostics are invalid");
            }
        }

        private static Diagnostics empty(GraphShape shape) {
            return new Diagnostics(
                    shape, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0L, false);
        }
    }

    record Result(
            BlueprintResearchService.Status status,
            Optional<Graph> graph,
            Diagnostics diagnostics) {
        Result {
            graph = graph == null ? Optional.empty() : graph;
            if (status == null || diagnostics == null
                    || (status == BlueprintResearchService.Status.SUCCESS)
                            != graph.isPresent()) {
                throw new IllegalArgumentException(
                        "resolved research graph result is invalid");
            }
        }

        static Result success(Graph graph) {
            return new Result(
                    BlueprintResearchService.Status.SUCCESS,
                    Optional.of(graph),
                    graph.diagnostics());
        }

        static Result failure(
                BlueprintResearchService.Status status,
                Diagnostics diagnostics) {
            if (status == BlueprintResearchService.Status.SUCCESS) {
                throw new IllegalArgumentException("failed graph result cannot be successful");
            }
            return new Result(status, Optional.empty(), diagnostics);
        }

        boolean successful() {
            return status == BlueprintResearchService.Status.SUCCESS;
        }
    }

    record BuildLimits(
            int maximumNodes,
            int maximumEdges,
            int maximumPolicyLookups,
            long maximumClassificationBitWords,
            int maximumDepth,
            long emergencyTimeoutNanos) {
        private static final BuildLimits DEFAULT = new BuildLimits(
                MAX_GRAPH_NODES,
                MAX_GRAPH_EDGES,
                MAX_GRAPH_NODES,
                MAX_CLASSIFICATION_BIT_WORDS,
                BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH,
                ResearchPathUnlockPlanner.EMERGENCY_TIMEOUT_NANOS);

        BuildLimits {
            if (maximumNodes < 1 || maximumEdges < 1 || maximumPolicyLookups < 1
                    || maximumClassificationBitWords < 1L || maximumDepth < 1
                    || emergencyTimeoutNanos < 1L) {
                throw new IllegalArgumentException(
                        "resolved research graph limits are invalid");
            }
        }
    }

    private static final class Builder {
        private final ResourceLocation targetId;
        private final IPlayerRecipeData playerData;
        private final Function<ResourceLocation, BlueprintResearchPolicy> policyResolver;
        private final Predicate<ResourceLocation> progressionExempt;
        private final ResearchPathAuthority authority;
        private final BuildLimits limits;
        private final ResearchPathUnlockPlanner.PlanningBudget budget;
        private final int pointBalance;
        private final Map<ResourceLocation, DraftNode> drafts = new LinkedHashMap<>();
        private final Map<ResourceLocation, Boolean> exemptions = new HashMap<>();
        private int edgeCount;
        private int policyLookups;
        private int observedMaximumDepth;
        private long classificationBitWords;

        private Builder(
                ResourceLocation targetId,
                IPlayerRecipeData playerData,
                Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
                Predicate<ResourceLocation> progressionExempt,
                ResearchPathAuthority authority,
                BuildLimits limits,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            this.targetId = targetId;
            this.playerData = playerData;
            this.policyResolver = policyResolver;
            this.progressionExempt = progressionExempt;
            this.authority = authority;
            this.limits = limits;
            this.budget = budget;
            this.pointBalance = playerData.getResearchPoints();
        }

        private Result build() {
            discover();
            Indexed indexed = index();
            Ordered ordered = order(indexed);
            Evaluated evaluated = evaluate(indexed, ordered.topologicalOrder());
            budget.checkDeadline();
            int targetIndex = indexed.indices().get(targetId);
            BlueprintResearchService.Status targetStatus = evaluated.statuses()[targetIndex];
            if (targetStatus != BlueprintResearchService.Status.SUCCESS) {
                return Result.failure(
                        targetStatus,
                        diagnostics(
                                GraphShape.INVALID,
                                indexed,
                                ordered,
                                evaluated,
                                Classification.EMPTY,
                                false));
            }
            List<Node> nodes = materialize(indexed, evaluated);
            Classification classification = classify(nodes, ordered.topologicalOrder(), targetIndex);
            Diagnostics diagnostics = diagnostics(
                    classification.shape(),
                    indexed,
                    ordered,
                    evaluated,
                    classification,
                    false);
            Graph graph = new Graph(
                    targetId,
                    targetIndex,
                    nodes,
                    indexed.indices(),
                    ordered.topologicalOrder(),
                    classification.shape(),
                    diagnostics);
            budget.checkDeadline();
            return Result.success(graph);
        }

        private void discover() {
            ArrayDeque<ResourceLocation> pending = new ArrayDeque<>();
            DraftNode target = ensureDraft(targetId);
            pending.addLast(targetId);
            while (!pending.isEmpty()) {
                ResourceLocation blueprintId = pending.removeLast();
                DraftNode draft = drafts.get(blueprintId);
                if (draft.resolved) {
                    continue;
                }
                countWork();
                if (isExempt(blueprintId)) {
                    draft.exempt = true;
                    draft.resolved = true;
                    continue;
                }
                countPolicyLookup();
                BlueprintResearchPolicy policy = policyResolver.apply(blueprintId);
                BlueprintResearchService.Status status =
                        ResearchPathUnlockPlanner.validatePolicy(
                                blueprintId,
                                policy,
                                playerData,
                                pointBalance,
                                progressionExempt);
                if (status == null) {
                    status = authority.validate(policy).orElse(null);
                }
                draft.policy = policy;
                draft.status = status;
                draft.resolved = true;
                if (status != null) {
                    continue;
                }
                for (ResearchPrerequisiteGroup group : policy.requirements().allOf()) {
                    List<ResourceLocation> exemptAlternatives = group.anyOf().stream()
                            .filter(this::isExempt)
                            .toList();
                    if (!exemptAlternatives.isEmpty()) {
                        countEdges(exemptAlternatives.size());
                        exemptAlternatives.forEach(alternative -> {
                            DraftNode prerequisite = ensureDraft(alternative);
                            prerequisite.exempt = true;
                            prerequisite.resolved = true;
                        });
                        draft.groups.add(new DraftGroup(exemptAlternatives, true));
                        continue;
                    }
                    countEdges(group.anyOf().size());
                    draft.groups.add(new DraftGroup(group.anyOf(), false));
                    for (ResourceLocation alternative : group.anyOf()) {
                        DraftNode prerequisite = ensureDraft(alternative);
                        if (!prerequisite.resolved) {
                            pending.addLast(alternative);
                        }
                    }
                }
            }
        }

        private Indexed index() {
            List<DraftNode> orderedDrafts = drafts.values().stream()
                    .sorted(Comparator.comparing(draft -> draft.id.toString()))
                    .toList();
            Map<ResourceLocation, Integer> indices = new LinkedHashMap<>();
            for (int index = 0; index < orderedDrafts.size(); index++) {
                countWork();
                indices.put(orderedDrafts.get(index).id, index);
            }
            return new Indexed(orderedDrafts, Collections.unmodifiableMap(indices));
        }

        private Ordered order(Indexed indexed) {
            int size = indexed.drafts().size();
            List<Set<Integer>> dependents = new ArrayList<>(size);
            int[] indegree = new int[size];
            for (int index = 0; index < size; index++) {
                countWork();
                dependents.add(new LinkedHashSet<>());
            }
            for (int dependentIndex = 0; dependentIndex < size; dependentIndex++) {
                countWork();
                DraftNode dependent = indexed.drafts().get(dependentIndex);
                if (dependent.status != null || dependent.exempt) {
                    continue;
                }
                Set<Integer> uniquePrerequisites = new LinkedHashSet<>();
                for (DraftGroup group : dependent.groups) {
                    countWork();
                    if (group.satisfiedByExemption()) {
                        continue;
                    }
                    for (ResourceLocation alternative : group.alternatives()) {
                        countWork();
                        uniquePrerequisites.add(indexed.indices().get(alternative));
                    }
                }
                indegree[dependentIndex] = uniquePrerequisites.size();
                int stableDependentIndex = dependentIndex;
                uniquePrerequisites.forEach(prerequisite ->
                        dependents.get(prerequisite).add(stableDependentIndex));
            }
            PriorityQueue<Integer> ready = new PriorityQueue<>();
            for (int index = 0; index < size; index++) {
                countWork();
                if (indegree[index] == 0) {
                    ready.add(index);
                }
            }
            List<Integer> topological = new ArrayList<>(size);
            while (!ready.isEmpty()) {
                countWork();
                int prerequisite = ready.remove();
                topological.add(prerequisite);
                for (int dependent : dependents.get(prerequisite)) {
                    countWork();
                    if (--indegree[dependent] == 0) {
                        ready.add(dependent);
                    }
                }
            }
            if (topological.size() != size) {
                throw new CycleException(diagnosticsForFailure(true));
            }
            int[] depth = new int[size];
            int maximumDepth = 0;
            for (int nodeIndex : topological) {
                countWork();
                DraftNode node = indexed.drafts().get(nodeIndex);
                if (node.exempt || node.status != null) {
                    continue;
                }
                int nodeDepth = 1;
                for (DraftGroup group : node.groups) {
                    countWork();
                    if (group.satisfiedByExemption()) {
                        continue;
                    }
                    for (ResourceLocation alternative : group.alternatives()) {
                        countWork();
                        nodeDepth = Math.max(
                                nodeDepth,
                                depth[indexed.indices().get(alternative)] + 1);
                    }
                }
                depth[nodeIndex] = nodeDepth;
                maximumDepth = Math.max(maximumDepth, nodeDepth);
                observedMaximumDepth = maximumDepth;
                if (nodeDepth > limits.maximumDepth()) {
                    throw new DepthLimitException(diagnosticsForFailure(false));
                }
            }
            return new Ordered(List.copyOf(topological), maximumDepth);
        }

        @SuppressWarnings("unchecked")
        private Evaluated evaluate(Indexed indexed, List<Integer> topologicalOrder) {
            int size = indexed.drafts().size();
            BlueprintResearchService.Status[] statuses =
                    new BlueprintResearchService.Status[size];
            boolean[] connected = new boolean[size];
            List<EvaluatedGroup>[] groups = new List[size];
            for (int nodeIndex : topologicalOrder) {
                countWork();
                DraftNode node = indexed.drafts().get(nodeIndex);
                groups[nodeIndex] = new ArrayList<>();
                if (node.exempt) {
                    statuses[nodeIndex] = BlueprintResearchService.Status.SUCCESS;
                    connected[nodeIndex] = true;
                    continue;
                }
                if (node.status != null) {
                    statuses[nodeIndex] = node.status;
                    continue;
                }
                BlueprintResearchService.Status nodeFailure = null;
                boolean everyGroupConnected = true;
                for (DraftGroup group : node.groups) {
                    countWork();
                    if (group.satisfiedByExemption()) {
                        groups[nodeIndex].add(new EvaluatedGroup(
                                GroupState.SATISFIED_BY_EXEMPTION,
                                Optional.empty()));
                        continue;
                    }
                    boolean hasConnectedAlternative = false;
                    boolean hasViableAlternative = false;
                    BlueprintResearchService.Status groupFailure =
                            BlueprintResearchService.Status.PREREQUISITES_REQUIRED;
                    for (ResourceLocation alternative : group.alternatives()) {
                        countWork();
                        int alternativeIndex = indexed.indices().get(alternative);
                        hasConnectedAlternative |= connected[alternativeIndex];
                        BlueprintResearchService.Status alternativeStatus =
                                statuses[alternativeIndex];
                        hasViableAlternative |= alternativeStatus
                                == BlueprintResearchService.Status.SUCCESS;
                        if (alternativeStatus != BlueprintResearchService.Status.SUCCESS) {
                            groupFailure = preferredFailure(groupFailure, alternativeStatus);
                        }
                    }
                    if (hasConnectedAlternative) {
                        groups[nodeIndex].add(new EvaluatedGroup(
                                GroupState.SATISFIED_BY_CONNECTED_SUPPORT,
                                Optional.empty()));
                    } else if (hasViableAlternative) {
                        everyGroupConnected = false;
                        groups[nodeIndex].add(new EvaluatedGroup(
                                GroupState.REQUIRES_ALTERNATIVE_SELECTION,
                                Optional.empty()));
                    } else {
                        everyGroupConnected = false;
                        nodeFailure = preferredFailure(nodeFailure, groupFailure);
                        groups[nodeIndex].add(new EvaluatedGroup(
                                GroupState.UNSATISFIABLE,
                                Optional.of(groupFailure)));
                    }
                }
                statuses[nodeIndex] = nodeFailure == null
                        ? BlueprintResearchService.Status.SUCCESS
                        : nodeFailure;
                connected[nodeIndex] = nodeFailure == null
                        && node.policy.learned()
                        && everyGroupConnected;
            }
            return new Evaluated(statuses, connected, groups);
        }

        private List<Node> materialize(Indexed indexed, Evaluated evaluated) {
            List<Node> nodes = new ArrayList<>(indexed.drafts().size());
            for (int index = 0; index < indexed.drafts().size(); index++) {
                countWork();
                DraftNode draft = indexed.drafts().get(index);
                BlueprintResearchService.Status status = evaluated.statuses()[index];
                NodeState state = nodeState(draft, status, evaluated.connected()[index]);
                List<RequirementGroup> groups = new ArrayList<>();
                for (int groupIndex = 0; groupIndex < draft.groups.size(); groupIndex++) {
                    countWork();
                    DraftGroup draftGroup = draft.groups.get(groupIndex);
                    EvaluatedGroup evaluatedGroup = evaluated.groups()[index].get(groupIndex);
                    List<Alternative> alternatives = new ArrayList<>(
                            draftGroup.alternatives().size());
                    for (ResourceLocation alternative : draftGroup.alternatives()) {
                        countWork();
                        int alternativeIndex = indexed.indices().get(alternative);
                        NodeState alternativeState = nodeState(
                                indexed.drafts().get(alternativeIndex),
                                evaluated.statuses()[alternativeIndex],
                                evaluated.connected()[alternativeIndex]);
                        alternatives.add(new Alternative(
                                alternativeIndex,
                                alternativeState,
                                failure(evaluated.statuses()[alternativeIndex])));
                    }
                    groups.add(new RequirementGroup(
                            evaluatedGroup.state(),
                            alternatives,
                            evaluatedGroup.failure()));
                }
                Optional<ResearchPathAuthority.RootProvenance> rootProvenance =
                        rootProvenance(draft);
                nodes.add(new Node(
                        index,
                        draft.id,
                        Optional.ofNullable(draft.policy),
                        state,
                        rootProvenance,
                        status == BlueprintResearchService.Status.SUCCESS,
                        evaluated.connected()[index],
                        groups,
                        failure(status)));
            }
            return List.copyOf(nodes);
        }

        private Optional<ResearchPathAuthority.RootProvenance> rootProvenance(
                DraftNode draft) {
            if (draft.exempt) {
                return Optional.of(
                        ResearchPathAuthority.RootProvenance.PROGRESSION_EXEMPT_BOUNDARY);
            }
            if (draft.status != null || draft.policy == null
                    || !draft.policy.requirements().allOf().isEmpty()) {
                return Optional.empty();
            }
            return authority.rootProvenance(draft.id).or(() -> Optional.of(
                    ResearchPathAuthority.RootProvenance.AUTHORED_ROOT));
        }

        private Classification classify(
                List<Node> nodes,
                List<Integer> topologicalOrder,
                int targetIndex) {
            BitSet active = new BitSet(nodes.size());
            ArrayDeque<Integer> pending = new ArrayDeque<>();
            active.set(targetIndex);
            pending.add(targetIndex);
            while (!pending.isEmpty()) {
                countWork();
                Node node = nodes.get(pending.removeLast());
                for (RequirementGroup group : node.groups()) {
                    countWork();
                    if (group.state() != GroupState.REQUIRES_ALTERNATIVE_SELECTION) {
                        continue;
                    }
                    for (Alternative alternative : group.alternatives()) {
                        countWork();
                        if (alternative.usable() && !active.get(alternative.nodeIndex())) {
                            active.set(alternative.nodeIndex());
                            pending.add(alternative.nodeIndex());
                        }
                    }
                }
            }

            BitSet[] universes = new BitSet[nodes.size()];
            int activeGroups = 0;
            int choiceGroups = 0;
            int maximumGroups = 0;
            int maximumAlternatives = 0;
            int maximumOverlapWidth = 0;
            int generalNodes = 0;
            boolean allMandatory = true;
            for (int nodeIndex : topologicalOrder) {
                countWork();
                BitSet universe = new BitSet(nodes.size());
                universes[nodeIndex] = universe;
                if (!active.get(nodeIndex)) {
                    continue;
                }
                universe.set(nodeIndex);
                List<BitSet> groupUniverses = new ArrayList<>();
                int nodeActiveGroups = 0;
                for (RequirementGroup group : nodes.get(nodeIndex).groups()) {
                    countWork();
                    if (group.state() != GroupState.REQUIRES_ALTERNATIVE_SELECTION) {
                        continue;
                    }
                    nodeActiveGroups++;
                    activeGroups++;
                    List<Integer> alternatives = group.usableAlternativeIndices();
                    maximumAlternatives = Math.max(maximumAlternatives, alternatives.size());
                    if (alternatives.size() > 1) {
                        choiceGroups++;
                        allMandatory = false;
                    }
                    BitSet groupUniverse = new BitSet(nodes.size());
                    for (int alternative : alternatives) {
                        countWork();
                        groupUniverse.or(universes[alternative]);
                        countBitWords(universes[alternative]);
                    }
                    universe.or(groupUniverse);
                    countBitWords(groupUniverse);
                    groupUniverses.add(groupUniverse);
                }
                maximumGroups = Math.max(maximumGroups, nodeActiveGroups);
                int overlapWidth = largestOverlapComponent(groupUniverses);
                maximumOverlapWidth = Math.max(maximumOverlapWidth, overlapWidth);
                if (overlapWidth > 1) {
                    generalNodes++;
                }
            }
            GraphShape shape = allMandatory
                    ? GraphShape.MANDATORY_DAG
                    : maximumGroups <= 1
                            ? GraphShape.OR_PATH_DAG
                            : maximumOverlapWidth <= 1
                                    ? GraphShape.SEPARABLE_AND_OR_DAG
                                    : GraphShape.GENERAL_AND_OR_DAG;
            return new Classification(
                    shape,
                    active.cardinality(),
                    activeGroups,
                    choiceGroups,
                    maximumGroups,
                    maximumAlternatives,
                    maximumOverlapWidth,
                    generalNodes);
        }

        private int largestOverlapComponent(List<BitSet> universes) {
            if (universes.isEmpty()) {
                return 0;
            }
            int[] parent = new int[universes.size()];
            int[] size = new int[universes.size()];
            for (int index = 0; index < parent.length; index++) {
                parent[index] = index;
                size[index] = 1;
            }
            for (int left = 0; left < universes.size(); left++) {
                for (int right = left + 1; right < universes.size(); right++) {
                    BitSet overlap = (BitSet) universes.get(left).clone();
                    overlap.and(universes.get(right));
                    countBitWords(universes.get(left));
                    if (!overlap.isEmpty()) {
                        union(parent, size, left, right);
                    }
                }
            }
            int largest = 1;
            for (int index = 0; index < parent.length; index++) {
                int root = find(parent, index);
                largest = Math.max(largest, size[root]);
            }
            return largest;
        }

        private Diagnostics diagnostics(
                GraphShape shape,
                Indexed indexed,
                Ordered ordered,
                Evaluated evaluated,
                Classification classification,
                boolean cycleDetected) {
            int rootCount = 0;
            for (int index = 0; index < indexed.drafts().size(); index++) {
                countWork();
                DraftNode draft = indexed.drafts().get(index);
                if (evaluated.statuses()[index] == BlueprintResearchService.Status.SUCCESS
                        && (draft.exempt || draft.policy != null
                                && draft.policy.requirements().allOf().isEmpty())) {
                    rootCount++;
                }
            }
            return new Diagnostics(
                    shape,
                    indexed.drafts().size(),
                    edgeCount,
                    policyLookups,
                    rootCount,
                    classification.activeNodeCount(),
                    classification.activeGroupCount(),
                    classification.choiceGroupCount(),
                    ordered.maximumDepth(),
                    classification.maximumActiveGroupsPerNode(),
                    classification.maximumUsableAlternativesPerGroup(),
                    classification.maximumGroupOverlapWidth(),
                    classification.generalSearchNodeCount(),
                    classificationBitWords,
                    cycleDetected);
        }

        private Diagnostics diagnosticsForFailure(boolean cycleDetected) {
            return new Diagnostics(
                    GraphShape.INVALID,
                    drafts.size(),
                    edgeCount,
                    policyLookups,
                    0,
                    0,
                    0,
                    0,
                    observedMaximumDepth,
                    0,
                    0,
                    0,
                    0,
                    classificationBitWords,
                    cycleDetected);
        }

        private DraftNode ensureDraft(ResourceLocation blueprintId) {
            if (!validId(blueprintId)) {
                throw new IllegalArgumentException("research graph contains an invalid ID");
            }
            DraftNode existing = drafts.get(blueprintId);
            if (existing != null) {
                return existing;
            }
            if (drafts.size() >= limits.maximumNodes()) {
                throw new BuildComplexityException(diagnosticsForFailure(false));
            }
            countWork();
            DraftNode created = new DraftNode(blueprintId);
            drafts.put(blueprintId, created);
            return created;
        }

        private boolean isExempt(ResourceLocation blueprintId) {
            Boolean cached = exemptions.get(blueprintId);
            if (cached != null) {
                return cached;
            }
            boolean exempt = progressionExempt.test(blueprintId);
            exemptions.put(blueprintId, exempt);
            return exempt;
        }

        private void countEdges(int count) {
            edgeCount = Math.addExact(edgeCount, count);
            countWork();
            if (edgeCount > limits.maximumEdges()) {
                throw new BuildComplexityException(diagnosticsForFailure(false));
            }
        }

        private void countPolicyLookup() {
            policyLookups++;
            countWork();
            if (policyLookups > limits.maximumPolicyLookups()) {
                throw new BuildComplexityException(diagnosticsForFailure(false));
            }
        }

        private void countBitWords(BitSet value) {
            classificationBitWords = Math.addExact(
                    classificationBitWords,
                    Math.max(1L, (value.length() + 63L) / 64L));
            countWork();
            if (classificationBitWords > limits.maximumClassificationBitWords()) {
                throw new BuildComplexityException(diagnosticsForFailure(false));
            }
        }

        private void countWork() {
            budget.checkpoint();
        }
    }

    private static NodeState nodeState(
            DraftNode draft,
            BlueprintResearchService.Status status,
            boolean connected) {
        if (draft.exempt) {
            return NodeState.PROGRESSION_EXEMPT;
        }
        if (status != BlueprintResearchService.Status.SUCCESS) {
            return NodeState.UNUSABLE;
        }
        if (draft.policy.learned()) {
            return connected
                    ? NodeState.LEARNED_CONNECTED
                    : NodeState.LEARNED_DISCONNECTED;
        }
        return NodeState.PURCHASABLE;
    }

    private static Optional<BlueprintResearchService.Status> failure(
            BlueprintResearchService.Status status) {
        return status == BlueprintResearchService.Status.SUCCESS
                ? Optional.empty()
                : Optional.of(status);
    }

    private static BlueprintResearchService.Status preferredFailure(
            BlueprintResearchService.Status current,
            BlueprintResearchService.Status candidate) {
        if (candidate == null || candidate == BlueprintResearchService.Status.SUCCESS) {
            return current;
        }
        if (current == null) {
            return candidate;
        }
        return failurePriority(candidate) > failurePriority(current)
                ? candidate
                : current;
    }

    private static int failurePriority(BlueprintResearchService.Status status) {
        return switch (status) {
            case ROUTE_TOO_COMPLEX -> 4;
            case PATH_TOO_LARGE -> 3;
            case TECH_TREE_UNAVAILABLE -> 2;
            case UNSATISFIABLE -> 1;
            default -> 0;
        };
    }

    private static int find(int[] parent, int value) {
        int current = value;
        while (parent[current] != current) {
            parent[current] = parent[parent[current]];
            current = parent[current];
        }
        return current;
    }

    private static void union(int[] parent, int[] size, int left, int right) {
        int leftRoot = find(parent, left);
        int rightRoot = find(parent, right);
        if (leftRoot == rightRoot) {
            return;
        }
        if (size[leftRoot] < size[rightRoot]) {
            int swap = leftRoot;
            leftRoot = rightRoot;
            rightRoot = swap;
        }
        parent[rightRoot] = leftRoot;
        size[leftRoot] += size[rightRoot];
    }

    private record DraftGroup(
            List<ResourceLocation> alternatives,
            boolean satisfiedByExemption) {
        private DraftGroup {
            alternatives = List.copyOf(alternatives);
        }
    }

    private static final class DraftNode {
        private final ResourceLocation id;
        private final List<DraftGroup> groups = new ArrayList<>();
        private BlueprintResearchPolicy policy;
        private BlueprintResearchService.Status status;
        private boolean resolved;
        private boolean exempt;

        private DraftNode(ResourceLocation id) {
            this.id = id;
        }
    }

    private record Indexed(
            List<DraftNode> drafts,
            Map<ResourceLocation, Integer> indices) {
    }

    private record Ordered(List<Integer> topologicalOrder, int maximumDepth) {
    }

    private record Evaluated(
            BlueprintResearchService.Status[] statuses,
            boolean[] connected,
            List<EvaluatedGroup>[] groups) {
    }

    private record EvaluatedGroup(
            GroupState state,
            Optional<BlueprintResearchService.Status> failure) {
    }

    private record Classification(
            GraphShape shape,
            int activeNodeCount,
            int activeGroupCount,
            int choiceGroupCount,
            int maximumActiveGroupsPerNode,
            int maximumUsableAlternativesPerGroup,
            int maximumGroupOverlapWidth,
            int generalSearchNodeCount) {
        private static final Classification EMPTY = new Classification(
                GraphShape.INVALID, 0, 0, 0, 0, 0, 0, 0);
    }

    private static final class BuildComplexityException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Diagnostics diagnostics;

        private BuildComplexityException(Diagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        private Diagnostics diagnostics() {
            return diagnostics;
        }
    }

    private static final class DepthLimitException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Diagnostics diagnostics;

        private DepthLimitException(Diagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        private Diagnostics diagnostics() {
            return diagnostics;
        }
    }

    private static final class CycleException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final Diagnostics diagnostics;

        private CycleException(Diagnostics diagnostics) {
            this.diagnostics = diagnostics;
        }

        private Diagnostics diagnostics() {
            return diagnostics;
        }
    }
}
