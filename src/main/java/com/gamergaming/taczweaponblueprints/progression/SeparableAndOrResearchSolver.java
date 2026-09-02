package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;

/** Exact dynamic-programming solver for independent AND-of-OR route components. */
final class SeparableAndOrResearchSolver {
    private SeparableAndOrResearchSolver() {
    }

    static Result solve(
            ResolvedResearchPathGraph.Graph graph,
            boolean creativePlayer,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (graph == null
                || graph.shape()
                        != ResolvedResearchPathGraph.GraphShape.SEPARABLE_AND_OR_DAG
                || budget == null) {
            return Result.failure(BlueprintResearchService.Status.INVALID_INPUT);
        }

        try {
            Solved solved = solveLabels(graph, creativePlayer, budget);
            ResearchPathRouteLabel target = solved.labels()[graph.targetIndex()];
            if (target == null) {
                return Result.failure(BlueprintResearchService.Status.PREREQUISITES_REQUIRED);
            }
            if (target.purchaseCount() == 0) {
                return Result.failure(BlueprintResearchService.Status.ALREADY_LEARNED);
            }
            Closure closure = reconstruct(
                    graph,
                    solved.predecessors(),
                    target.purchaseCount(),
                    target.supportCount(),
                    budget);
            return Result.success(
                    ResearchPathUnlockPlanner.buildPlan(
                            closure.purchases(),
                            closure.supportIds(),
                            creativePlayer,
                            budget));
        } catch (ResearchPathUnlockPlanner.RouteComplexityException exception) {
            return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
        } catch (ResearchPathRouteLabel.PathTooLargeException exception) {
            return Result.failure(BlueprintResearchService.Status.PATH_TOO_LARGE);
        } catch (SolverFailureException exception) {
            return Result.failure(exception.status());
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE);
        } catch (RuntimeException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
    }

    private static Solved solveLabels(
            ResolvedResearchPathGraph.Graph graph,
            boolean creativePlayer,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        List<ResolvedResearchPathGraph.Node> nodes = graph.nodes();
        BitSet active = activeNodes(graph, budget);
        ResearchPathRouteLabel[] labels = new ResearchPathRouteLabel[nodes.size()];
        int[][] predecessors = new int[nodes.size()][];

        for (int nodeIndex : graph.topologicalOrder()) {
            if (!active.get(nodeIndex)) {
                continue;
            }
            budget.countRouteState();
            ResolvedResearchPathGraph.Node node = nodes.get(nodeIndex);
            if (!node.routeViable()) {
                throw new SolverFailureException(node.failure().orElse(
                        BlueprintResearchService.Status.PREREQUISITES_REQUIRED));
            }

            ResearchPathRouteLabel combined = ResearchPathRouteLabel.empty();
            int requiredGroupCount = requiredGroupCount(node);
            int[] selectedPredecessors = new int[requiredGroupCount];
            int selectedOffset = 0;
            for (ResolvedResearchPathGraph.RequirementGroup group : node.groups()) {
                if (group.state()
                        == ResolvedResearchPathGraph.GroupState.UNSATISFIABLE) {
                    throw new SolverFailureException(group.failure().orElse(
                            BlueprintResearchService.Status.PREREQUISITES_REQUIRED));
                }
                if (group.state()
                        != ResolvedResearchPathGraph.GroupState
                                .REQUIRES_ALTERNATIVE_SELECTION) {
                    continue;
                }
                AlternativeSelection selection = bestAlternative(group, labels, budget);
                budget.countRouteMerge();
                combined = ResearchPathRouteLabel.mergeDisjoint(
                        combined, selection.label(), budget);
                selectedPredecessors[selectedOffset++] = selection.nodeIndex();
            }

            labels[nodeIndex] = ResearchPathRouteLabel.extend(
                    combined, node, creativePlayer, budget);
            predecessors[nodeIndex] = selectedPredecessors;
        }
        return new Solved(labels, predecessors);
    }

    private static int requiredGroupCount(ResolvedResearchPathGraph.Node node) {
        int requiredGroups = 0;
        for (ResolvedResearchPathGraph.RequirementGroup group : node.groups()) {
            if (group.state()
                    == ResolvedResearchPathGraph.GroupState.REQUIRES_ALTERNATIVE_SELECTION) {
                requiredGroups = Math.addExact(requiredGroups, 1);
            }
        }
        return requiredGroups;
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

    private static AlternativeSelection bestAlternative(
            ResolvedResearchPathGraph.RequirementGroup group,
            ResearchPathRouteLabel[] labels,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        AlternativeSelection selected = null;
        for (ResolvedResearchPathGraph.Alternative alternative : group.alternatives()) {
            if (!alternative.usable()) {
                continue;
            }
            budget.countRouteMerge();
            int nodeIndex = alternative.nodeIndex();
            ResearchPathRouteLabel label = labels[nodeIndex];
            if (label == null) {
                throw new IllegalArgumentException(
                        "separable alternative was not solved before its dependent");
            }
            if (selected == null
                    || ResearchPathRouteLabel.compare(
                            label, selected.label(), budget) < 0) {
                selected = new AlternativeSelection(nodeIndex, label);
            }
        }
        if (selected == null) {
            throw new SolverFailureException(group.failure().orElse(
                    BlueprintResearchService.Status.PREREQUISITES_REQUIRED));
        }
        return selected;
    }

    private static Closure reconstruct(
            ResolvedResearchPathGraph.Graph graph,
            int[][] predecessors,
            int expectedPurchases,
            int expectedSupport,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        List<ResolvedResearchPathGraph.Node> nodes = graph.nodes();
        BitSet visiting = new BitSet(nodes.size());
        BitSet complete = new BitSet(nodes.size());
        ArrayDeque<Frame> pending = new ArrayDeque<>();
        push(graph.targetIndex(), nodes, predecessors, visiting, pending);

        LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> ordered =
                new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            Frame frame = pending.peek();
            if (frame.hasNext()) {
                budget.countCanonicalWork(1L);
                int prerequisite = frame.next();
                if (complete.get(prerequisite)) {
                    continue;
                }
                if (visiting.get(prerequisite)) {
                    throw new IllegalArgumentException(
                            "selected separable route contains a cycle");
                }
                push(prerequisite, nodes, predecessors, visiting, pending);
                continue;
            }

            pending.pop();
            budget.countClosureReferences(1L);
            visiting.clear(frame.nodeIndex());
            if (complete.get(frame.nodeIndex())) {
                continue;
            }
            complete.set(frame.nodeIndex());
            ResolvedResearchPathGraph.Node node = nodes.get(frame.nodeIndex());
            if (node.state() != ResolvedResearchPathGraph.NodeState.PURCHASABLE) {
                continue;
            }
            BlueprintResearchPolicy policy = node.policy().orElseThrow(
                    () -> new IllegalArgumentException(
                            "purchasable separable node has no policy"));
            BlueprintResearchPolicy existing = ordered.putIfAbsent(
                    node.blueprintId(), policy);
            if (existing != null && !existing.equals(policy)) {
                throw new IllegalArgumentException(
                        "separable route contains inconsistent policies");
            }
        }
        if (complete.cardinality() != expectedSupport
                || ordered.size() != expectedPurchases) {
            throw new IllegalArgumentException(
                    "separable reconstruction does not match its selected label");
        }
        List<ResourceLocation> supportIds = graph.topologicalOrder().stream()
                .filter(complete::get)
                .map(index -> nodes.get(index).blueprintId())
                .toList();
        return new Closure(ordered, supportIds);
    }

    private record Closure(
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> purchases,
            List<ResourceLocation> supportIds) {
    }

    private static void push(
            int nodeIndex,
            List<ResolvedResearchPathGraph.Node> nodes,
            int[][] predecessors,
            BitSet visiting,
            ArrayDeque<Frame> pending) {
        if (nodeIndex < 0 || nodeIndex >= nodes.size()
                || predecessors[nodeIndex] == null) {
            throw new IllegalArgumentException(
                    "selected separable route contains an unresolved node");
        }
        ResolvedResearchPathGraph.Node node = nodes.get(nodeIndex);
        if (!node.routeViable()) {
            throw new SolverFailureException(node.failure().orElse(
                    BlueprintResearchService.Status.PREREQUISITES_REQUIRED));
        }
        visiting.set(nodeIndex);
        pending.push(new Frame(nodeIndex, predecessors[nodeIndex]));
    }

    record Result(
            BlueprintResearchService.Status status,
            Optional<ResearchPathUnlockPlanner.Plan> plan) {
        Result {
            plan = plan == null ? Optional.empty() : plan;
            if (status == null
                    || (status == BlueprintResearchService.Status.SUCCESS) != plan.isPresent()) {
                throw new IllegalArgumentException(
                        "invalid separable research solver result");
            }
        }

        static Result success(ResearchPathUnlockPlanner.Plan plan) {
            return new Result(
                    BlueprintResearchService.Status.SUCCESS,
                    Optional.of(plan));
        }

        static Result failure(BlueprintResearchService.Status status) {
            if (status == BlueprintResearchService.Status.SUCCESS) {
                throw new IllegalArgumentException(
                        "successful separable result requires a plan");
            }
            return new Result(status, Optional.empty());
        }

        boolean successful() {
            return status == BlueprintResearchService.Status.SUCCESS;
        }
    }

    private record Solved(
            ResearchPathRouteLabel[] labels,
            int[][] predecessors) {
    }

    private record AlternativeSelection(
            int nodeIndex,
            ResearchPathRouteLabel label) {
    }

    private static final class Frame {
        private final int nodeIndex;
        private final int[] prerequisites;
        private int nextPrerequisite;

        private Frame(int nodeIndex, int[] prerequisites) {
            this.nodeIndex = nodeIndex;
            this.prerequisites = prerequisites;
        }

        private int nodeIndex() {
            return nodeIndex;
        }

        private boolean hasNext() {
            return nextPrerequisite < prerequisites.length;
        }

        private int next() {
            return prerequisites[nextPrerequisite++];
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
