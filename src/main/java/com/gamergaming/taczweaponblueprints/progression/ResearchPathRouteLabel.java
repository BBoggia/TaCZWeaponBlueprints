package com.gamergaming.taczweaponblueprints.progression;

import java.util.Arrays;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

/** Immutable route objective shared by the indexed exact solvers. */
final class ResearchPathRouteLabel {
    private static final ResearchPathRouteLabel EMPTY = new ResearchPathRouteLabel(
            0,
            0L,
            0L,
            new int[0],
            new int[0]);

    private final int purchaseCount;
    private final long pointCost;
    private final long materialCount;
    private final int[] purchaseIds;
    private final int[] supportIds;

    private ResearchPathRouteLabel(
            int purchaseCount,
            long pointCost,
            long materialCount,
            int[] purchaseIds,
            int[] supportIds) {
        if (purchaseCount < 0 || pointCost < 0L || materialCount < 0L
                || purchaseIds == null || supportIds == null
                || purchaseCount != purchaseIds.length) {
            throw new IllegalArgumentException("invalid indexed research route label");
        }
        this.purchaseCount = purchaseCount;
        this.pointCost = pointCost;
        this.materialCount = materialCount;
        this.purchaseIds = purchaseIds;
        this.supportIds = supportIds;
    }

    static ResearchPathRouteLabel empty() {
        return EMPTY;
    }

    static ResearchPathRouteLabel extend(
            ResearchPathRouteLabel predecessor,
            ResolvedResearchPathGraph.Node node,
            boolean creativePlayer,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (predecessor == null || node == null || budget == null) {
            throw new IllegalArgumentException("indexed route extension is invalid");
        }
        BlueprintResearchPolicy policy = node.policy().orElseThrow(
                () -> new IllegalArgumentException("active research node has no policy"));
        budget.countClosureReferences(predecessor.supportIds.length + 1L);
        int[] supportIds = insert(predecessor.supportIds, node.index());
        if (node.state() != ResolvedResearchPathGraph.NodeState.PURCHASABLE) {
            return new ResearchPathRouteLabel(
                    predecessor.purchaseCount,
                    predecessor.pointCost,
                    predecessor.materialCount,
                    predecessor.purchaseIds,
                    supportIds);
        }

        boolean bypassed = creativePlayer && policy.creativeBypassesCost();
        long pointCost = predecessor.pointCost;
        long materialCount = predecessor.materialCount;
        if (!bypassed) {
            pointCost = Math.addExact(pointCost, policy.researchCost().points());
            for (BlueprintResearchIngredient ingredient
                    : policy.researchCost().ingredients()) {
                budget.checkpoint();
                materialCount = Math.addExact(materialCount, ingredient.count());
            }
        }
        int purchaseCount = Math.addExact(predecessor.purchaseCount, 1);
        requireSupportedPurchaseCount(purchaseCount);
        budget.countClosureReferences(predecessor.purchaseIds.length + 1L);
        return new ResearchPathRouteLabel(
                purchaseCount,
                pointCost,
                materialCount,
                insert(predecessor.purchaseIds, node.index()),
                supportIds);
    }

    static ResearchPathRouteLabel mergeDisjoint(
            ResearchPathRouteLabel left,
            ResearchPathRouteLabel right,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (left == null || right == null || budget == null) {
            throw new IllegalArgumentException("indexed route composition is invalid");
        }
        if (left == EMPTY) {
            return right;
        }
        if (right == EMPTY) {
            return left;
        }
        int purchaseCount = Math.addExact(left.purchaseCount, right.purchaseCount);
        requireSupportedPurchaseCount(purchaseCount);
        long pointCost = Math.addExact(left.pointCost, right.pointCost);
        long materialCount = Math.addExact(left.materialCount, right.materialCount);
        budget.countClosureReferences(
                Math.addExact(left.purchaseIds.length, right.purchaseIds.length));
        budget.countClosureReferences(
                Math.addExact(left.supportIds.length, right.supportIds.length));
        budget.countCanonicalWork(
                Math.addExact(left.supportIds.length, right.supportIds.length));
        return new ResearchPathRouteLabel(
                purchaseCount,
                pointCost,
                materialCount,
                mergeSortedDisjoint(left.purchaseIds, right.purchaseIds),
                mergeSortedDisjoint(left.supportIds, right.supportIds));
    }

    static int compare(
            ResearchPathRouteLabel left,
            ResearchPathRouteLabel right,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (left == null || right == null || budget == null) {
            throw new IllegalArgumentException("indexed route comparison is invalid");
        }
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
        comparison = compareCanonical(left.purchaseIds, right.purchaseIds, budget);
        if (comparison != 0) {
            return comparison;
        }
        budget.countCanonicalWork(Math.min(
                left.supportIds.length, right.supportIds.length) + 1L);
        return ResearchPathUnlockPlanner.compareCanonicalSupportIds(
                left.supportIds, right.supportIds);
    }

    int purchaseCount() {
        return purchaseCount;
    }

    int supportCount() {
        return supportIds.length;
    }

    private static void requireSupportedPurchaseCount(int purchaseCount) {
        if (purchaseCount > ResearchPathUnlockPlanner.MAX_UNLOCKS_PER_PURCHASE) {
            throw new PathTooLargeException();
        }
    }

    private static int compareCanonical(
            int[] left,
            int[] right,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        int shared = Math.min(left.length, right.length);
        budget.countCanonicalWork(shared + 1L);
        for (int index = 0; index < shared; index++) {
            int comparison = Integer.compare(left[index], right[index]);
            if (comparison != 0) {
                return comparison;
            }
        }
        return Integer.compare(left.length, right.length);
    }

    private static int[] insert(int[] sorted, int nodeIndex) {
        int offset = Arrays.binarySearch(sorted, nodeIndex);
        if (offset >= 0) {
            throw new IllegalArgumentException("indexed research route repeats a node");
        }
        offset = -offset - 1;
        int[] extended = new int[sorted.length + 1];
        System.arraycopy(sorted, 0, extended, 0, offset);
        extended[offset] = nodeIndex;
        System.arraycopy(
                sorted,
                offset,
                extended,
                offset + 1,
                sorted.length - offset);
        return extended;
    }

    private static int[] mergeSortedDisjoint(int[] left, int[] right) {
        int[] merged = new int[Math.addExact(left.length, right.length)];
        int leftIndex = 0;
        int rightIndex = 0;
        int mergedIndex = 0;
        while (leftIndex < left.length && rightIndex < right.length) {
            int leftId = left[leftIndex];
            int rightId = right[rightIndex];
            if (leftId == rightId) {
                throw new IllegalArgumentException(
                        "separable research route components overlap");
            }
            if (leftId < rightId) {
                merged[mergedIndex++] = leftId;
                leftIndex++;
            } else {
                merged[mergedIndex++] = rightId;
                rightIndex++;
            }
        }
        System.arraycopy(left, leftIndex, merged, mergedIndex, left.length - leftIndex);
        mergedIndex += left.length - leftIndex;
        System.arraycopy(right, rightIndex, merged, mergedIndex, right.length - rightIndex);
        return merged;
    }

    static final class PathTooLargeException extends RuntimeException {
        private static final long serialVersionUID = 1L;
    }
}
