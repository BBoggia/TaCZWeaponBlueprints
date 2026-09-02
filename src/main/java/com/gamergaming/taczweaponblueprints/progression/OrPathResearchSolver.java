package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;

/** Dynamic-programming solver for resolved routes with at most one active group per node. */
final class OrPathResearchSolver {
    private static final int NO_PREDECESSOR = -1;
    private static final int UNRESOLVED = -2;

    private OrPathResearchSolver() {
    }

    static Result solve(
            ResolvedResearchPathGraph.Graph graph,
            boolean creativePlayer,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (graph == null
                || graph.shape() != ResolvedResearchPathGraph.GraphShape.OR_PATH_DAG
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
            Closure closure =
                    reconstruct(graph, solved.predecessors(), target.purchaseCount(), budget);
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
        int[] predecessors = new int[nodes.size()];
        java.util.Arrays.fill(predecessors, UNRESOLVED);

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

            ResearchPathRouteLabel predecessor = ResearchPathRouteLabel.empty();
            int predecessorIndex = NO_PREDECESSOR;
            int requiredGroups = 0;
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
                if (++requiredGroups > 1) {
                    throw new IllegalArgumentException(
                            "OR-path node contains more than one active group");
                }
                AlternativeSelection selection = bestAlternative(group, labels, budget);
                predecessor = selection.label();
                predecessorIndex = selection.nodeIndex();
            }

            labels[nodeIndex] = ResearchPathRouteLabel.extend(
                    predecessor, node, creativePlayer, budget);
            predecessors[nodeIndex] = predecessorIndex;
        }
        return new Solved(labels, predecessors);
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
                        "OR-path alternative was not solved before its dependent");
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
            int[] predecessors,
            int expectedPurchases,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        ArrayDeque<Integer> route = new ArrayDeque<>();
        BitSet visiting = new BitSet(graph.nodes().size());
        int current = graph.targetIndex();
        while (current != NO_PREDECESSOR) {
            budget.countCanonicalWork(1L);
            if (current < 0 || current >= graph.nodes().size()
                    || predecessors[current] == UNRESOLVED
                    || visiting.get(current)) {
                throw new IllegalArgumentException("resolved OR path is inconsistent");
            }
            visiting.set(current);
            route.push(current);
            current = predecessors[current];
        }

        LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> ordered =
                new LinkedHashMap<>();
        List<ResourceLocation> supportIds = new java.util.ArrayList<>(route.size());
        while (!route.isEmpty()) {
            budget.countClosureReferences(1L);
            ResolvedResearchPathGraph.Node node = graph.nodes().get(route.pop());
            supportIds.add(node.blueprintId());
            if (node.state() != ResolvedResearchPathGraph.NodeState.PURCHASABLE) {
                continue;
            }
            BlueprintResearchPolicy policy = node.policy().orElseThrow(
                    () -> new IllegalArgumentException(
                            "purchasable OR-path node has no policy"));
            BlueprintResearchPolicy existing = ordered.putIfAbsent(
                    node.blueprintId(), policy);
            if (existing != null && !existing.equals(policy)) {
                throw new IllegalArgumentException(
                        "OR-path route contains inconsistent policies");
            }
        }
        if (ordered.size() != expectedPurchases) {
            throw new IllegalArgumentException(
                    "OR-path reconstruction does not match its selected label");
        }
        return new Closure(ordered, List.copyOf(supportIds));
    }

    private record Closure(
            LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> purchases,
            List<ResourceLocation> supportIds) {
    }

    record Result(
            BlueprintResearchService.Status status,
            Optional<ResearchPathUnlockPlanner.Plan> plan) {
        Result {
            plan = plan == null ? Optional.empty() : plan;
            if (status == null
                    || (status == BlueprintResearchService.Status.SUCCESS) != plan.isPresent()) {
                throw new IllegalArgumentException("invalid OR-path solver result");
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
                        "successful OR-path result requires a plan");
            }
            return new Result(status, Optional.empty());
        }

        boolean successful() {
            return status == BlueprintResearchService.Status.SUCCESS;
        }
    }

    private record Solved(ResearchPathRouteLabel[] labels, int[] predecessors) {
    }

    private record AlternativeSelection(
            int nodeIndex,
            ResearchPathRouteLabel label) {
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
