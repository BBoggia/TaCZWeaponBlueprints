package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;

/** Linear-time closure solver for resolved research graphs without active choices. */
final class MandatoryResearchPathSolver {
    private MandatoryResearchPathSolver() {
    }

    static Result solve(
            ResolvedResearchPathGraph.Graph graph,
            boolean creativePlayer,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (graph == null
                || graph.shape() != ResolvedResearchPathGraph.GraphShape.MANDATORY_DAG
                || budget == null) {
            return Result.failure(BlueprintResearchService.Status.INVALID_INPUT);
        }

        try {
            Closure closure =
                    mandatoryClosure(graph, budget);
            if (closure.purchases().isEmpty()) {
                return Result.failure(BlueprintResearchService.Status.ALREADY_LEARNED);
            }
            return Result.success(
                    ResearchPathUnlockPlanner.buildPlan(
                            closure.purchases(),
                            closure.supportIds(),
                            creativePlayer,
                            budget));
        } catch (ResearchPathUnlockPlanner.RouteComplexityException exception) {
            return Result.failure(BlueprintResearchService.Status.ROUTE_TOO_COMPLEX);
        } catch (PathTooLargeException exception) {
            return Result.failure(BlueprintResearchService.Status.PATH_TOO_LARGE);
        } catch (SolverFailureException exception) {
            return Result.failure(exception.status());
        } catch (ArithmeticException | IllegalArgumentException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_INELIGIBLE);
        } catch (RuntimeException exception) {
            return Result.failure(BlueprintResearchService.Status.POLICY_UNAVAILABLE);
        }
    }

    /**
     * Reconstructs the prerequisite-first order used by the compatibility
     * planner. The explicit stack avoids relying on the JVM call stack for a
     * malformed or unusually deep graph.
     */
    private static Closure mandatoryClosure(
            ResolvedResearchPathGraph.Graph graph,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        List<ResolvedResearchPathGraph.Node> nodes = graph.nodes();
        BitSet visiting = new BitSet(nodes.size());
        BitSet complete = new BitSet(nodes.size());
        ArrayDeque<Frame> pending = new ArrayDeque<>();
        push(graph.targetIndex(), nodes, visiting, pending);

        LinkedHashMap<ResourceLocation, BlueprintResearchPolicy> ordered =
                new LinkedHashMap<>();
        while (!pending.isEmpty()) {
            Frame frame = pending.peek();
            if (frame.hasNext()) {
                budget.countRouteMerge();
                int prerequisite = frame.next();
                if (complete.get(prerequisite)) {
                    continue;
                }
                if (visiting.get(prerequisite)) {
                    throw new SolverFailureException(
                            BlueprintResearchService.Status.POLICY_INELIGIBLE);
                }
                push(prerequisite, nodes, visiting, pending);
                continue;
            }

            pending.pop();
            budget.countRouteState();
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
                            "purchasable research node has no policy"));
            BlueprintResearchPolicy existing = ordered.putIfAbsent(
                    node.blueprintId(), policy);
            if (existing != null && !existing.equals(policy)) {
                throw new IllegalArgumentException(
                        "mandatory research closure contains inconsistent policies");
            }
            if (ordered.size() > ResearchPathUnlockPlanner.MAX_UNLOCKS_PER_PURCHASE) {
                throw new PathTooLargeException();
            }
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
            BitSet visiting,
            ArrayDeque<Frame> pending) {
        if (nodeIndex < 0 || nodeIndex >= nodes.size()) {
            throw new IllegalArgumentException("mandatory research edge is out of range");
        }
        ResolvedResearchPathGraph.Node node = nodes.get(nodeIndex);
        if (!node.routeViable()) {
            throw new SolverFailureException(node.failure().orElse(
                    BlueprintResearchService.Status.PREREQUISITES_REQUIRED));
        }
        visiting.set(nodeIndex);
        pending.push(new Frame(nodeIndex, mandatoryPrerequisites(node)));
    }

    private static int[] mandatoryPrerequisites(ResolvedResearchPathGraph.Node node) {
        int requiredGroupCount = 0;
        for (ResolvedResearchPathGraph.RequirementGroup group : node.groups()) {
            if (group.state()
                    == ResolvedResearchPathGraph.GroupState.REQUIRES_ALTERNATIVE_SELECTION) {
                requiredGroupCount++;
            } else if (group.state()
                    == ResolvedResearchPathGraph.GroupState.UNSATISFIABLE) {
                throw new SolverFailureException(group.failure().orElse(
                        BlueprintResearchService.Status.PREREQUISITES_REQUIRED));
            }
        }

        int[] prerequisites = new int[requiredGroupCount];
        int offset = 0;
        for (ResolvedResearchPathGraph.RequirementGroup group : node.groups()) {
            if (group.state()
                    != ResolvedResearchPathGraph.GroupState.REQUIRES_ALTERNATIVE_SELECTION) {
                continue;
            }
            List<Integer> alternatives = group.usableAlternativeIndices();
            if (alternatives.size() != 1) {
                throw new IllegalArgumentException(
                        "mandatory research group does not have exactly one route");
            }
            prerequisites[offset++] = alternatives.get(0);
        }
        return prerequisites;
    }

    record Result(
            BlueprintResearchService.Status status,
            Optional<ResearchPathUnlockPlanner.Plan> plan) {
        Result {
            plan = plan == null ? Optional.empty() : plan;
            if (status == null
                    || (status == BlueprintResearchService.Status.SUCCESS) != plan.isPresent()) {
                throw new IllegalArgumentException(
                        "invalid mandatory research solver result");
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
                        "successful mandatory research result requires a plan");
            }
            return new Result(status, Optional.empty());
        }

        boolean successful() {
            return status == BlueprintResearchService.Status.SUCCESS;
        }
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

    private static final class PathTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
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
