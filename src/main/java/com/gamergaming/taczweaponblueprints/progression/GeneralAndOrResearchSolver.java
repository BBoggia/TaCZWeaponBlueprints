package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;

/** Bounded exact retained-label solver for interacting AND/OR research graphs. */
final class GeneralAndOrResearchSolver {
    private GeneralAndOrResearchSolver() {
    }

    static Result solve(
            ResolvedResearchPathGraph.Graph graph,
            boolean creativePlayer,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (graph == null
                || graph.shape()
                        != ResolvedResearchPathGraph.GraphShape.GENERAL_AND_OR_DAG
                || budget == null) {
            return Result.failure(BlueprintResearchService.Status.INVALID_INPUT);
        }

        try {
            Solved solved = solveFrontiers(graph, creativePlayer, budget);
            List<Label> targetFrontier = solved.frontiers()[graph.targetIndex()];
            if (targetFrontier == null || targetFrontier.isEmpty()) {
                return Result.failure(solved.failures()[graph.targetIndex()]);
            }
            Label selected = selectBest(targetFrontier, budget);
            if (selected.purchaseCount() == 0) {
                releaseFrontier(targetFrontier, budget);
                return Result.failure(BlueprintResearchService.Status.ALREADY_LEARNED);
            }
            Closure closure =
                    reconstruct(graph, selected, budget);
            ResearchPathUnlockPlanner.Plan plan =
                    ResearchPathUnlockPlanner.buildPlan(
                            closure.purchases(),
                            closure.supportIds(),
                            creativePlayer,
                            budget);
            releaseFrontier(targetFrontier, budget);
            return Result.success(plan);
        } catch (ResearchPathUnlockPlanner.RouteComplexityException exception) {
            return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
        } catch (SolverFailureException exception) {
            return Result.failure(exception.status());
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE);
        } catch (RuntimeException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
    }

    @SuppressWarnings("unchecked")
    private static Solved solveFrontiers(
            ResolvedResearchPathGraph.Graph graph,
            boolean creativePlayer,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        List<ResolvedResearchPathGraph.Node> nodes = graph.nodes();
        BitSet active = activeNodes(graph, budget);
        List<Label>[] frontiers = (List<Label>[]) new List<?>[nodes.size()];
        BlueprintResearchService.Status[] failures =
                new BlueprintResearchService.Status[nodes.size()];
        int[] remainingConsumers = remainingConsumers(graph, active, budget);
        NodeWeights[] weights = nodeWeights(nodes, creativePlayer, budget);

        for (int nodeIndex : graph.topologicalOrder()) {
            if (!active.get(nodeIndex)) {
                continue;
            }
            ResolvedResearchPathGraph.Node node = nodes.get(nodeIndex);
            if (!node.routeViable()) {
                throw new SolverFailureException(node.failure().orElse(
                        BlueprintResearchService.Status.PREREQUISITES_REQUIRED));
            }
            NodeFrontier solved = solveNode(
                    node,
                    frontiers,
                    failures,
                    weights,
                    budget);
            frontiers[nodeIndex] = solved.labels();
            failures[nodeIndex] = solved.failure();
            releaseConsumedFrontiers(
                    node,
                    frontiers,
                    remainingConsumers,
                    budget);
        }
        return new Solved(frontiers, failures);
    }

    private static NodeFrontier solveNode(
            ResolvedResearchPathGraph.Node node,
            List<Label>[] frontiers,
            BlueprintResearchService.Status[] failures,
            NodeWeights[] weights,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        List<Label> current = List.of(Label.EMPTY);
        boolean currentRetained = false;
        boolean pathTooLarge = false;

        for (ResolvedResearchPathGraph.RequirementGroup group : node.groups()) {
            if (group.state() == ResolvedResearchPathGraph.GroupState.UNSATISFIABLE) {
                if (currentRetained) {
                    releaseFrontier(current, budget);
                }
                return NodeFrontier.failure(group.failure().orElse(
                        BlueprintResearchService.Status.PREREQUISITES_REQUIRED));
            }
            if (group.state()
                    != ResolvedResearchPathGraph.GroupState
                            .REQUIRES_ALTERNATIVE_SELECTION) {
                continue;
            }

            List<Label> combined = new ArrayList<>();
            boolean groupPathTooLarge = false;
            for (Label base : current) {
                for (ResolvedResearchPathGraph.Alternative alternative
                        : group.alternatives()) {
                    if (!alternative.usable()) {
                        continue;
                    }
                    List<Label> alternativeFrontier =
                            frontiers[alternative.nodeIndex()];
                    if (alternativeFrontier == null || alternativeFrontier.isEmpty()) {
                        groupPathTooLarge |= failures[alternative.nodeIndex()]
                                == BlueprintResearchService.Status.PATH_TOO_LARGE;
                        continue;
                    }
                    for (Label alternativeLabel : alternativeFrontier) {
                        budget.countRouteMerge();
                        budget.countRouteState();
                        Label merged = Label.union(base, alternativeLabel, weights, budget);
                        if (merged == null) {
                            groupPathTooLarge = true;
                            continue;
                        }
                        addNondominated(combined, merged, budget);
                    }
                }
            }
            if (currentRetained) {
                releaseFrontier(current, budget);
            }
            current = combined;
            currentRetained = true;
            pathTooLarge |= groupPathTooLarge;
            if (current.isEmpty()) {
                return NodeFrontier.failure(pathTooLarge
                        ? BlueprintResearchService.Status.PATH_TOO_LARGE
                        : BlueprintResearchService.Status.PREREQUISITES_REQUIRED);
            }
        }

        List<Label> extended = new ArrayList<>();
        boolean extensionTooLarge = false;
        for (Label base : current) {
            budget.countRouteState();
            Label candidate = Label.extend(base, node, weights[node.index()], budget);
            if (candidate == null) {
                extensionTooLarge = true;
                continue;
            }
            addNondominated(extended, candidate, budget);
        }
        if (currentRetained) {
            releaseFrontier(current, budget);
        }
        if (extended.isEmpty()) {
            return NodeFrontier.failure(extensionTooLarge || pathTooLarge
                    ? BlueprintResearchService.Status.PATH_TOO_LARGE
                    : BlueprintResearchService.Status.PREREQUISITES_REQUIRED);
        }
        return NodeFrontier.success(extended);
    }

    private static void addNondominated(
            List<Label> frontier,
            Label candidate,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        retain(candidate, budget);
        for (int index = 0; index < frontier.size();) {
            Label existing = frontier.get(index);
            budget.countDominanceComparison();
            if (existing.dominates(candidate, budget)) {
                release(candidate, budget);
                return;
            }
            budget.countDominanceComparison();
            if (candidate.dominates(existing, budget)) {
                frontier.remove(index);
                release(existing, budget);
                continue;
            }
            index++;
        }
        frontier.add(candidate);
        budget.checkFrontierSize(frontier.size());
    }

    private static Label selectBest(
            List<Label> frontier,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        Label selected = null;
        for (Label candidate : frontier) {
            if (selected == null || Label.compare(candidate, selected, budget) < 0) {
                selected = candidate;
            }
        }
        if (selected == null) {
            throw new SolverFailureException(
                    BlueprintResearchService.Status.PREREQUISITES_REQUIRED);
        }
        return selected;
    }

    private static Closure reconstruct(
            ResolvedResearchPathGraph.Graph graph,
            Label selected,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (!selected.support().contains(graph.targetIndex())
                || !selected.purchase().isSubsetOf(selected.support(), budget)) {
            throw new IllegalArgumentException("general route proof is inconsistent");
        }
        validateProof(graph, selected.support(), budget);

        LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> ordered =
                new LinkedHashMap<>();
        List<ResourceLocation> supportIds = new ArrayList<>(selected.support().cardinality());
        for (int nodeIndex : graph.topologicalOrder()) {
            budget.countCanonicalWork(1L);
            if (selected.support().contains(nodeIndex)) {
                supportIds.add(graph.nodes().get(nodeIndex).blueprintId());
            }
            if (!selected.purchase().contains(nodeIndex)) {
                continue;
            }
            ResolvedResearchPathGraph.Node node = graph.nodes().get(nodeIndex);
            if (node.state() != ResolvedResearchPathGraph.NodeState.PURCHASABLE) {
                throw new IllegalArgumentException(
                        "general route purchases a non-purchasable node");
            }
            BlueprintResearchPolicy policy = node.policy().orElseThrow(
                    () -> new IllegalArgumentException(
                            "general route purchase has no policy"));
            BlueprintResearchPolicy existing = ordered.putIfAbsent(
                    node.blueprintId(), policy);
            if (existing != null && !existing.equals(policy)) {
                throw new IllegalArgumentException(
                        "general route contains inconsistent policies");
            }
        }
        if (ordered.size() != selected.purchaseCount()) {
            throw new IllegalArgumentException(
                    "general route reconstruction does not match its label");
        }
        return new Closure(ordered, List.copyOf(supportIds));
    }

    private record Closure(
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> purchases,
            List<ResourceLocation> supportIds) {
    }

    private static void validateProof(
            ResolvedResearchPathGraph.Graph graph,
            ImmutableNodeSet support,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        for (int nodeIndex = support.nextSetBit(0);
                nodeIndex >= 0;
                nodeIndex = support.nextSetBit(nodeIndex + 1)) {
            budget.countClosureReferences(1L);
            ResolvedResearchPathGraph.Node node = graph.nodes().get(nodeIndex);
            if (!node.routeViable()) {
                throw new IllegalArgumentException("general route uses an invalid node");
            }
            for (ResolvedResearchPathGraph.RequirementGroup group : node.groups()) {
                if (group.state()
                        != ResolvedResearchPathGraph.GroupState
                                .REQUIRES_ALTERNATIVE_SELECTION) {
                    continue;
                }
                boolean satisfied = false;
                for (ResolvedResearchPathGraph.Alternative alternative
                        : group.alternatives()) {
                    budget.checkpoint();
                    if (alternative.usable()
                            && support.contains(alternative.nodeIndex())) {
                        satisfied = true;
                        break;
                    }
                }
                if (!satisfied) {
                    throw new IllegalArgumentException(
                            "general route does not prove every requirement group");
                }
            }
        }
    }

    private static BitSet activeNodes(
            ResolvedResearchPathGraph.Graph graph,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        BitSet active = new BitSet(graph.nodes().size());
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        active.set(graph.targetIndex());
        pending.addLast(graph.targetIndex());
        while (!pending.isEmpty()) {
            budget.checkpoint();
            ResolvedResearchPathGraph.Node node =
                    graph.nodes().get(pending.removeLast());
            for (ResolvedResearchPathGraph.RequirementGroup group : node.groups()) {
                if (group.state()
                        != ResolvedResearchPathGraph.GroupState
                                .REQUIRES_ALTERNATIVE_SELECTION) {
                    continue;
                }
                for (ResolvedResearchPathGraph.Alternative alternative
                        : group.alternatives()) {
                    budget.checkpoint();
                    if (alternative.usable()
                            && !active.get(alternative.nodeIndex())) {
                        active.set(alternative.nodeIndex());
                        pending.addLast(alternative.nodeIndex());
                    }
                }
            }
        }
        return active;
    }

    private static int[] remainingConsumers(
            ResolvedResearchPathGraph.Graph graph,
            BitSet active,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        int[] consumers = new int[graph.nodes().size()];
        for (int dependent = active.nextSetBit(0);
                dependent >= 0;
                dependent = active.nextSetBit(dependent + 1)) {
            for (ResolvedResearchPathGraph.RequirementGroup group
                    : graph.nodes().get(dependent).groups()) {
                if (group.state()
                        != ResolvedResearchPathGraph.GroupState
                                .REQUIRES_ALTERNATIVE_SELECTION) {
                    continue;
                }
                for (ResolvedResearchPathGraph.Alternative alternative
                        : group.alternatives()) {
                    budget.checkpoint();
                    if (alternative.usable()) {
                        consumers[alternative.nodeIndex()] = Math.addExact(
                                consumers[alternative.nodeIndex()], 1);
                    }
                }
            }
        }
        return consumers;
    }

    private static void releaseConsumedFrontiers(
            ResolvedResearchPathGraph.Node dependent,
            List<Label>[] frontiers,
            int[] remainingConsumers,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        for (ResolvedResearchPathGraph.RequirementGroup group : dependent.groups()) {
            if (group.state()
                    != ResolvedResearchPathGraph.GroupState.REQUIRES_ALTERNATIVE_SELECTION) {
                continue;
            }
            for (ResolvedResearchPathGraph.Alternative alternative
                    : group.alternatives()) {
                if (!alternative.usable()) {
                    continue;
                }
                int prerequisite = alternative.nodeIndex();
                remainingConsumers[prerequisite]--;
                if (remainingConsumers[prerequisite] < 0) {
                    throw new IllegalArgumentException(
                            "general route consumer count became negative");
                }
                if (remainingConsumers[prerequisite] == 0
                        && frontiers[prerequisite] != null) {
                    releaseFrontier(frontiers[prerequisite], budget);
                    frontiers[prerequisite] = null;
                }
            }
        }
    }

    private static NodeWeights[] nodeWeights(
            List<ResolvedResearchPathGraph.Node> nodes,
            boolean creativePlayer,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        NodeWeights[] weights = new NodeWeights[nodes.size()];
        for (ResolvedResearchPathGraph.Node node : nodes) {
            budget.checkpoint();
            if (node.state() != ResolvedResearchPathGraph.NodeState.PURCHASABLE) {
                weights[node.index()] = NodeWeights.ZERO;
                continue;
            }
            BlueprintResearchPolicy policy = node.policy().orElseThrow(
                    () -> new IllegalArgumentException(
                            "purchasable general node has no policy"));
            if (creativePlayer && policy.creativeBypassesCost()) {
                weights[node.index()] = NodeWeights.ZERO;
                continue;
            }
            long materialCount = 0L;
            for (BlueprintResearchIngredient ingredient
                    : policy.researchCost().ingredients()) {
                budget.checkpoint();
                materialCount = Math.addExact(materialCount, ingredient.count());
            }
            weights[node.index()] = new NodeWeights(
                    policy.researchCost().points(), materialCount);
        }
        return weights;
    }

    private static void retain(
            Label label,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        budget.retainGeneralLabel(label.retainedBitWords());
    }

    private static void release(
            Label label,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        budget.releaseGeneralLabels(1L, label.retainedBitWords());
    }

    private static void releaseFrontier(
            List<Label> frontier,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        long bitWords = 0L;
        for (Label label : frontier) {
            bitWords = Math.addExact(bitWords, label.retainedBitWords());
        }
        budget.releaseGeneralLabels(frontier.size(), bitWords);
    }

    record Result(
            BlueprintResearchService.Status status,
            Optional<ResearchPathUnlockPlanner.Plan> plan) {
        Result {
            plan = plan == null ? Optional.empty() : plan;
            if (status == null
                    || (status == BlueprintResearchService.Status.SUCCESS) != plan.isPresent()) {
                throw new IllegalArgumentException("invalid general solver result");
            }
        }

        static Result success(ResearchPathUnlockPlanner.Plan plan) {
            return new Result(
                    BlueprintResearchService.Status.SUCCESS,
                    Optional.of(plan));
        }

        static Result failure(BlueprintResearchService.Status status) {
            if (status == null || status == BlueprintResearchService.Status.SUCCESS) {
                throw new IllegalArgumentException(
                        "successful general result requires a plan");
            }
            return new Result(status, Optional.empty());
        }

        boolean successful() {
            return status == BlueprintResearchService.Status.SUCCESS;
        }
    }

    private record Solved(
            List<Label>[] frontiers,
            BlueprintResearchService.Status[] failures) {
    }

    private record NodeFrontier(
            List<Label> labels,
            BlueprintResearchService.Status failure) {
        private NodeFrontier {
            labels = labels == null ? List.of() : labels;
            if (failure == null && labels.isEmpty()
                    || failure != null && !labels.isEmpty()) {
                throw new IllegalArgumentException("invalid general node frontier");
            }
        }

        static NodeFrontier success(List<Label> labels) {
            return new NodeFrontier(labels, null);
        }

        static NodeFrontier failure(BlueprintResearchService.Status status) {
            return new NodeFrontier(List.of(), status);
        }
    }

    private record NodeWeights(long pointCost, long materialCount) {
        private static final NodeWeights ZERO = new NodeWeights(0L, 0L);

        private NodeWeights {
            if (pointCost < 0L || materialCount < 0L) {
                throw new IllegalArgumentException("general node weights cannot be negative");
            }
        }
    }

    private record Label(
            ImmutableNodeSet purchase,
            ImmutableNodeSet support,
            int purchaseCount,
            long pointCost,
            long materialCount) {
        private static final Label EMPTY = new Label(
                ImmutableNodeSet.EMPTY,
                ImmutableNodeSet.EMPTY,
                0,
                0L,
                0L);

        private Label {
            if (purchase == null || support == null || purchaseCount < 0
                    || pointCost < 0L || materialCount < 0L
                    || purchaseCount != purchase.cardinality()
                    || !purchase.isSubsetOfUnchecked(support)) {
                throw new IllegalArgumentException("invalid general route label");
            }
        }

        static Label union(
                Label left,
                Label right,
                NodeWeights[] weights,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            ImmutableNodeSet purchase = left.purchase.union(right.purchase, budget);
            int purchaseCount = purchase.cardinality();
            if (purchaseCount > ResearchPathUnlockPlanner.MAX_UNLOCKS_PER_PURCHASE) {
                return null;
            }
            long pointCost = left.pointCost;
            long materialCount = left.materialCount;
            for (int node = right.purchase.nextSetBit(0);
                    node >= 0;
                    node = right.purchase.nextSetBit(node + 1)) {
                budget.checkpoint();
                if (!left.purchase.contains(node)) {
                    pointCost = Math.addExact(pointCost, weights[node].pointCost());
                    materialCount = Math.addExact(
                            materialCount, weights[node].materialCount());
                }
            }
            return new Label(
                    purchase,
                    left.support.union(right.support, budget),
                    purchaseCount,
                    pointCost,
                    materialCount);
        }

        static Label extend(
                Label predecessor,
                ResolvedResearchPathGraph.Node node,
                NodeWeights weights,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            if (predecessor.support.contains(node.index())) {
                throw new IllegalArgumentException(
                        "general research route repeats its dependent node");
            }
            ImmutableNodeSet support = predecessor.support.with(node.index(), budget);
            if (node.state() != ResolvedResearchPathGraph.NodeState.PURCHASABLE) {
                return new Label(
                        predecessor.purchase.copy(budget),
                        support,
                        predecessor.purchaseCount,
                        predecessor.pointCost,
                        predecessor.materialCount);
            }
            ImmutableNodeSet purchase = predecessor.purchase.with(node.index(), budget);
            int purchaseCount = purchase.cardinality();
            if (purchaseCount > ResearchPathUnlockPlanner.MAX_UNLOCKS_PER_PURCHASE) {
                return null;
            }
            return new Label(
                    purchase,
                    support,
                    purchaseCount,
                    Math.addExact(predecessor.pointCost, weights.pointCost()),
                    Math.addExact(predecessor.materialCount, weights.materialCount()));
        }

        boolean dominates(
                Label other,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            return purchase.isSubsetOf(other.purchase, budget)
                    && support.isSubsetOf(other.support, budget);
        }

        long retainedBitWords() {
            return Math.addExact(purchase.wordCount(), support.wordCount());
        }

        static int compare(
                Label left,
                Label right,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            budget.countDominanceComparison();
            int comparison = Integer.compare(left.purchaseCount, right.purchaseCount);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Long.compare(left.pointCost, right.pointCost);
            if (comparison != 0) {
                return comparison;
            }
            comparison = Long.compare(left.materialCount, right.materialCount);
            if (comparison != 0) {
                return comparison;
            }
            comparison = left.purchase.compareCanonical(right.purchase, budget);
            return comparison != 0
                    ? comparison
                    : left.support.compareCanonicalSupport(right.support, budget);
        }
    }

    /** Immutable, trimmed bit-word set. No backing array escapes this class. */
    private static final class ImmutableNodeSet {
        private static final ImmutableNodeSet EMPTY = new ImmutableNodeSet(new long[0]);
        private final long[] words;

        private ImmutableNodeSet(long[] words) {
            this(words, false);
        }

        private ImmutableNodeSet(long[] words, boolean ownsWords) {
            int length = words.length;
            while (length > 0 && words[length - 1] == 0L) {
                length--;
            }
            this.words = ownsWords && length == words.length
                    ? words
                    : java.util.Arrays.copyOf(words, length);
        }

        ImmutableNodeSet copy(ResearchPathUnlockPlanner.PlanningBudget budget) {
            budget.countClosureReferences(words.length);
            return new ImmutableNodeSet(words);
        }

        ImmutableNodeSet with(
                int nodeIndex,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            if (nodeIndex < 0) {
                throw new IllegalArgumentException("general route node index is negative");
            }
            int wordIndex = nodeIndex >>> 6;
            int length = Math.max(words.length, wordIndex + 1);
            budget.countClosureReferences(length);
            long[] result = java.util.Arrays.copyOf(words, length);
            result[wordIndex] |= 1L << (nodeIndex & 63);
            return new ImmutableNodeSet(result, true);
        }

        ImmutableNodeSet union(
                ImmutableNodeSet other,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            int length = Math.max(words.length, other.words.length);
            budget.countClosureReferences(length);
            budget.countBitSetWordWork(length);
            long[] result = new long[length];
            for (int index = 0; index < length; index++) {
                budget.checkpoint();
                result[index] = word(index) | other.word(index);
            }
            return new ImmutableNodeSet(result, true);
        }

        boolean isSubsetOf(
                ImmutableNodeSet other,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            int length = Math.max(words.length, other.words.length);
            budget.countBitSetWordWork(length);
            return isSubsetOfUnchecked(other);
        }

        boolean isSubsetOfUnchecked(ImmutableNodeSet other) {
            int length = Math.max(words.length, other.words.length);
            for (int index = 0; index < length; index++) {
                if ((word(index) & ~other.word(index)) != 0L) {
                    return false;
                }
            }
            return true;
        }

        boolean contains(int nodeIndex) {
            if (nodeIndex < 0) {
                return false;
            }
            int wordIndex = nodeIndex >>> 6;
            return wordIndex < words.length
                    && (words[wordIndex] & 1L << (nodeIndex & 63)) != 0L;
        }

        int cardinality() {
            int cardinality = 0;
            for (long word : words) {
                cardinality = Math.addExact(cardinality, Long.bitCount(word));
            }
            return cardinality;
        }

        int nextSetBit(int fromIndex) {
            if (fromIndex < 0) {
                throw new IllegalArgumentException("bit-set search index is negative");
            }
            int wordIndex = fromIndex >>> 6;
            if (wordIndex >= words.length) {
                return -1;
            }
            long word = words[wordIndex] & (-1L << (fromIndex & 63));
            while (true) {
                if (word != 0L) {
                    return (wordIndex << 6) + Long.numberOfTrailingZeros(word);
                }
                if (++wordIndex >= words.length) {
                    return -1;
                }
                word = words[wordIndex];
            }
        }

        int compareCanonical(
                ImmutableNodeSet other,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            int left = nextSetBit(0);
            int right = other.nextSetBit(0);
            while (left >= 0 && right >= 0) {
                budget.countCanonicalWork(1L);
                int comparison = Integer.compare(left, right);
                if (comparison != 0) {
                    return comparison;
                }
                left = nextSetBit(left + 1);
                right = other.nextSetBit(right + 1);
            }
            budget.countCanonicalWork(1L);
            return Integer.compare(left, right);
        }

        int compareCanonicalSupport(
                ImmutableNodeSet other,
                ResearchPathUnlockPlanner.PlanningBudget budget) {
            int length = Math.max(words.length, other.words.length);
            budget.countBitSetWordWork(length);
            for (int index = 0; index < length; index++) {
                budget.countCanonicalWork(1L);
                long difference = word(index) ^ other.word(index);
                if (difference == 0L) {
                    continue;
                }
                int bit = Long.numberOfTrailingZeros(difference);
                return (word(index) & 1L << bit) != 0L ? -1 : 1;
            }
            return 0;
        }

        long wordCount() {
            return words.length;
        }

        private long word(int index) {
            return index < words.length ? words[index] : 0L;
        }
    }

    private static final class SolverFailureException extends RuntimeException {
        private static final long serialVersionUID = 1L;
        private final BlueprintResearchService.Status status;

        private SolverFailureException(BlueprintResearchService.Status status) {
            this.status = status;
        }

        private BlueprintResearchService.Status status() {
            return status;
        }
    }
}
