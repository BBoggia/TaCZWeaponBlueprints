package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/**
 * Client-only Phase 12 partitioner for a single semantic rank. It preserves the
 * authoritative node order and rank, but chooses responsive wrap boundaries
 * that avoid cutting a mature automatic family whenever the row capacity makes
 * that possible.
 */
final class ResearchTechTreeVisualMotifPlanner {
    private static final int UNREACHABLE_COUNT = Integer.MAX_VALUE;
    private static final long UNREACHABLE_BALANCE = Long.MAX_VALUE;

    private ResearchTechTreeVisualMotifPlanner() {
    }

    static Plan partition(
            List<ResourceLocation> orderedNodes,
            int rowCapacity,
            Function<ResourceLocation, Optional<Integer>> matureFamily) {
        if (orderedNodes == null || orderedNodes.isEmpty()
                || orderedNodes.stream().anyMatch(java.util.Objects::isNull)
                || new LinkedHashSet<>(orderedNodes).size() != orderedNodes.size()
                || orderedNodes.size() > ResearchTreeGraph.MAX_NODES
                || rowCapacity < 1
                || rowCapacity > ResearchTechTreeLayoutPolicy.MAXIMUM_NODES_PER_ROW
                || matureFamily == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree visual-motif inputs are invalid");
        }
        List<ResourceLocation> nodes = List.copyOf(orderedNodes);
        List<Integer> families = new ArrayList<>(nodes.size());
        for (ResourceLocation nodeId : nodes) {
            Optional<Integer> family = matureFamily.apply(nodeId);
            if (family == null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree visual-motif family is invalid");
            }
            families.add(family.orElse(null));
        }
        int rowCount = divideRoundUp(nodes.size(), rowCapacity);
        if (rowCount == 1 || families.stream().allMatch(java.util.Objects::isNull)) {
            return summarize(balancedRows(nodes, rowCount), families);
        }

        /*
         * A minimum-row partition has fewer than rowCapacity unused slots in
         * total. Track only that cumulative deficit instead of every possible
         * node boundary. This bounds memory by O(rowCount * rowCapacity), even
         * for 4,096 nodes at a one-node capacity.
         */
        int unusedSlots = rowCount * rowCapacity - nodes.size();
        int[][] severelyUnderfilled = new int[rowCount + 1][unusedSlots + 1];
        int[][] familySplits = new int[rowCount + 1][unusedSlots + 1];
        long[][] balance = new long[rowCount + 1][unusedSlots + 1];
        int[][] previousDeficit = new int[rowCount + 1][unusedSlots + 1];
        for (int[] row : severelyUnderfilled) {
            Arrays.fill(row, UNREACHABLE_COUNT);
        }
        for (int[] row : familySplits) {
            Arrays.fill(row, UNREACHABLE_COUNT);
        }
        for (long[] row : balance) {
            Arrays.fill(row, UNREACHABLE_BALANCE);
        }
        for (int[] row : previousDeficit) {
            Arrays.fill(row, -1);
        }
        severelyUnderfilled[0][0] = 0;
        familySplits[0][0] = 0;
        balance[0][0] = 0L;

        int baseRowSize = nodes.size() / rowCount;
        int largerRows = nodes.size() % rowCount;
        for (int row = 1; row <= rowCount; row++) {
            int desiredSize = baseRowSize + (row <= largerRows ? 1 : 0);
            for (int deficit = 0; deficit <= unusedSlots; deficit++) {
                int end = row * rowCapacity - deficit;
                if (end > nodes.size()) {
                    continue;
                }
                for (int priorDeficit = 0;
                        priorDeficit <= deficit;
                        priorDeficit++) {
                    if (severelyUnderfilled[row - 1][priorDeficit]
                            == UNREACHABLE_COUNT) {
                        continue;
                    }
                    int start = (row - 1) * rowCapacity - priorDeficit;
                    int actualSize = end - start;
                    int candidateUnderfilled = severelyUnderfilled[row - 1][priorDeficit]
                            + (isSeverelyUnderfilled(actualSize, desiredSize) ? 1 : 0);
                    int candidateSplits = familySplits[row - 1][priorDeficit]
                            + familySplit(families, start);
                    long candidateBalance = balance[row - 1][priorDeficit]
                            + balancePenalty(actualSize, desiredSize);
                    if (isBetter(
                            candidateUnderfilled,
                            candidateSplits,
                            candidateBalance,
                            start,
                            severelyUnderfilled[row][deficit],
                            familySplits[row][deficit],
                            balance[row][deficit],
                            previousDeficit[row][deficit] < 0
                                    ? Integer.MAX_VALUE
                                    : (row - 1) * rowCapacity
                                            - previousDeficit[row][deficit])) {
                        severelyUnderfilled[row][deficit] = candidateUnderfilled;
                        familySplits[row][deficit] = candidateSplits;
                        balance[row][deficit] = candidateBalance;
                        previousDeficit[row][deficit] = priorDeficit;
                    }
                }
            }
        }
        if (previousDeficit[rowCount][unusedSlots] < 0) {
            throw new IllegalArgumentException(
                    "Research Tech Tree visual-motif partition is unreachable");
        }

        List<List<ResourceLocation>> reversed = new ArrayList<>(rowCount);
        int end = nodes.size();
        int deficit = unusedSlots;
        for (int row = rowCount; row > 0; row--) {
            int priorDeficit = previousDeficit[row][deficit];
            int start = (row - 1) * rowCapacity - priorDeficit;
            reversed.add(List.copyOf(nodes.subList(start, end)));
            end = start;
            deficit = priorDeficit;
        }
        java.util.Collections.reverse(reversed);
        return summarize(reversed, families);
    }

    private static List<List<ResourceLocation>> balancedRows(
            List<ResourceLocation> nodes,
            int rowCount) {
        int baseRowSize = nodes.size() / rowCount;
        int largerRows = nodes.size() % rowCount;
        List<List<ResourceLocation>> rows = new ArrayList<>(rowCount);
        int cursor = 0;
        for (int row = 0; row < rowCount; row++) {
            int rowSize = baseRowSize + (row < largerRows ? 1 : 0);
            rows.add(List.copyOf(nodes.subList(cursor, cursor + rowSize)));
            cursor += rowSize;
        }
        return List.copyOf(rows);
    }

    private static int familySplit(List<Integer> families, int boundary) {
        if (boundary <= 0 || boundary >= families.size()) {
            return 0;
        }
        Integer left = families.get(boundary - 1);
        Integer right = families.get(boundary);
        return left != null && left.equals(right) ? 1 : 0;
    }

    private static boolean isSeverelyUnderfilled(int actualSize, int desiredSize) {
        return actualSize * 2 < desiredSize;
    }

    private static boolean isBetter(
            int candidateUnderfilled,
            int candidateSplits,
            long candidateBalance,
            int candidateStart,
            int currentUnderfilled,
            int currentSplits,
            long currentBalance,
            int currentStart) {
        if (candidateUnderfilled != currentUnderfilled) {
            return candidateUnderfilled < currentUnderfilled;
        }
        if (candidateSplits != currentSplits) {
            return candidateSplits < currentSplits;
        }
        if (candidateBalance != currentBalance) {
            return candidateBalance < currentBalance;
        }
        return candidateStart < currentStart;
    }

    private static long balancePenalty(int actualSize, int desiredSize) {
        long difference = actualSize - (long) desiredSize;
        return difference * difference;
    }

    private static Plan summarize(
            List<List<ResourceLocation>> rows,
            List<Integer> families) {
        int splitFamilyBoundaries = 0;
        int cursor = 0;
        for (int row = 0; row < rows.size(); row++) {
            if (row > 0 && familySplit(families, cursor) != 0) {
                splitFamilyBoundaries++;
            }
            cursor += rows.get(row).size();
        }
        int mixedFamilyRows = 0;
        int severelyUnderfilledRows = 0;
        int totalNodes = rows.stream().mapToInt(List::size).sum();
        int baseRowSize = totalNodes / rows.size();
        int largerRows = totalNodes % rows.size();
        cursor = 0;
        int rowIndex = 0;
        for (List<ResourceLocation> row : rows) {
            long distinctFamilies = families.subList(cursor, cursor + row.size()).stream()
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .count();
            if (distinctFamilies > 1L) {
                mixedFamilyRows++;
            }
            int desiredSize = baseRowSize + (rowIndex < largerRows ? 1 : 0);
            if (isSeverelyUnderfilled(row.size(), desiredSize)) {
                severelyUnderfilledRows++;
            }
            cursor += row.size();
            rowIndex++;
        }
        return new Plan(
                rows,
                splitFamilyBoundaries,
                mixedFamilyRows,
                severelyUnderfilledRows);
    }

    private static int divideRoundUp(int value, int divisor) {
        return 1 + (value - 1) / divisor;
    }

    record Plan(
            List<List<ResourceLocation>> rows,
            int splitFamilyBoundaries,
            int mixedFamilyRows,
            int severelyUnderfilledRows) {
        Plan {
            rows = rows == null ? List.of() : rows.stream().map(List::copyOf).toList();
            if (rows.isEmpty() || rows.stream().anyMatch(List::isEmpty)
                    || splitFamilyBoundaries < 0 || splitFamilyBoundaries >= rows.size()
                    || mixedFamilyRows < 0 || mixedFamilyRows > rows.size()
                    || severelyUnderfilledRows < 0
                    || severelyUnderfilledRows > rows.size()) {
                throw new IllegalArgumentException(
                        "invalid Research Tech Tree visual-motif plan");
            }
        }
    }
}
