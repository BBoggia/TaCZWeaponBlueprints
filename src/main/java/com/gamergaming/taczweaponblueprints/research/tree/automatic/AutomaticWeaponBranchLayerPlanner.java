package com.gamergaming.taczweaponblueprints.research.tree.automatic;

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

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;

/**
 * Assigns a dense shared trunk followed by bounded, gradually tapered role-family ranks.
 * This planner changes semantic ranks only; prerequisite authority remains in the
 * prerequisite planner and authored-rank finalizer.
 */
public final class AutomaticWeaponBranchLayerPlanner {
    static final int TARGET_DENSITY_NUMERATOR = 4;
    static final int TARGET_DENSITY_DENOMINATOR = 5;
    static final int MAX_SHARED_MEMBERSHIP_NUMERATOR = 2;
    static final int MAX_SHARED_MEMBERSHIP_DENOMINATOR = 5;

    private static final Comparator<AutomaticWeaponPlacementProposal> PROGRESSION_ORDER =
            Comparator.comparingInt(AutomaticWeaponPlacementProposal::mechanicalScore)
                    .thenComparingLong(value -> value.position().siblingOrder())
                    .thenComparing(AutomaticWeaponPlacementProposal::blueprintId);

    public Map<String, AutomaticWeaponPlacementProposal> assign(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, AutomaticWeaponRoleSignature> roleSignatures,
            Map<String, AutomaticWeaponRoleSignature> authoredRoleSignatures,
            AutomaticWeaponBranchModel branchModel,
            AutomaticWeaponPlacementPolicy policy) {
        validateInputs(
                proposals, roleSignatures, authoredRoleSignatures, branchModel, policy);
        if (!policy.usesDynamicLayers() || proposals.isEmpty()) {
            return new AutomaticWeaponLayerPlanner().assign(proposals, policy);
        }

        int width = policy.maxNodesPerRank();
        List<AutomaticWeaponPlacementProposal> ordered = proposals.values().stream()
                .sorted(PROGRESSION_ORDER)
                .toList();
        Map<String, AutomaticWeaponPlacementProposal> byId = Map.copyOf(proposals);
        Map<Integer, List<String>> branchMembers = branchMembers(branchModel, byId);
        Map<Integer, List<String>> apexCohorts = apexCohorts(
                branchModel, branchMembers, byId);
        Set<String> protectedApex = apexCohorts.values().stream()
                .flatMap(List::stream)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        int targetRankCount = targetRankCount(proposals.size(), policy);
        int targetSharedRanks = sharedRankCount(targetRankCount);
        Map<String, Integer> rawRanks = new LinkedHashMap<>();
        Map<Integer, Integer> rawWidths = new HashMap<>();
        Map<Integer, Integer> sharedByBranch = new HashMap<>();

        List<AutomaticWeaponPlacementProposal> foundation = ordered.stream()
                .filter(value -> !protectedApex.contains(value.blueprintId()))
                .limit(Math.min(policy.foundationCount(), width))
                .toList();
        if (foundation.isEmpty() && !ordered.isEmpty()) {
            int weakestBranch = branchModel.branchIndexByBlueprint().get(
                    ordered.get(0).blueprintId());
            foundation = apexCohorts.get(weakestBranch).stream()
                    .map(byId::get)
                    .limit(width)
                    .toList();
        }
        assignRank(foundation.stream().map(
                AutomaticWeaponPlacementProposal::blueprintId).toList(), 0,
                rawRanks, rawWidths, width);
        foundation.forEach(value -> sharedByBranch.merge(
                branchModel.branchIndexByBlueprint().get(value.blueprintId()),
                1,
                Math::addExact));

        int desiredSharedNodes = Math.multiplyExact(
                Math.max(0, targetSharedRanks - 1), width);
        List<String> shared = new ArrayList<>();
        for (AutomaticWeaponPlacementProposal candidate : ordered) {
            String id = candidate.blueprintId();
            if (rawRanks.containsKey(id) || protectedApex.contains(id)) {
                continue;
            }
            int branch = branchModel.branchIndexByBlueprint().get(id);
            int branchLimit = Math.floorDiv(
                    Math.multiplyExact(
                            branchMembers.get(branch).size(),
                            MAX_SHARED_MEMBERSHIP_NUMERATOR),
                    MAX_SHARED_MEMBERSHIP_DENOMINATOR);
            if (sharedByBranch.getOrDefault(branch, 0) >= branchLimit) {
                continue;
            }
            shared.add(id);
            sharedByBranch.merge(branch, 1, Math::addExact);
            if (shared.size() == desiredSharedNodes) {
                break;
            }
        }

        int rawRank = 1;
        for (int cursor = 0; cursor < shared.size(); cursor += width) {
            int end = Math.min(shared.size(), cursor + width);
            assignRank(shared.subList(cursor, end), rawRank++, rawRanks, rawWidths, width);
        }
        int firstBranchRank = rawRank;
        int plannedBranchRanks = Math.max(1, targetRankCount - firstBranchRank);

        List<LevelBatch> batches = new ArrayList<>();
        for (AutomaticWeaponBranchModel.Branch branch : branchModel.branches()) {
            List<String> remaining = branchMembers.get(branch.index()).stream()
                    .filter(id -> !rawRanks.containsKey(id))
                    .sorted(Comparator.comparing(byId::get, PROGRESSION_ORDER))
                    .toList();
            if (remaining.isEmpty()) {
                continue;
            }
            Set<String> remainingSet = Set.copyOf(remaining);
            List<String> apex = apexCohorts.get(branch.index()).stream()
                    .filter(remainingSet::contains)
                    .sorted(Comparator.comparing(byId::get, PROGRESSION_ORDER))
                    .toList();
            if (apex.isEmpty()) {
                apex = List.of(remaining.get(remaining.size() - 1));
            }
            Set<String> apexSet = Set.copyOf(apex);
            List<String> body = remaining.stream()
                    .filter(id -> !apexSet.contains(id))
                    .toList();
            List<List<String>> apexLevels = chunks(apex, width);
            int terminalWidth = apexLevels.get(apexLevels.size() - 1).size();
            int bodyLevelCount;
            if (body.isEmpty()) {
                bodyLevelCount = 0;
            } else {
                int minimumBodyLevels = divideRoundUp(body.size(), width);
                int preferredBodyLevels = divideRoundUp(
                        body.size(), Math.max(1, terminalWidth * 2));
                int plannedBodyLevels = Math.max(
                        1, plannedBranchRanks - apexLevels.size());
                bodyLevelCount = Math.max(
                        minimumBodyLevels,
                        Math.min(preferredBodyLevels, plannedBodyLevels));
            }
            List<Integer> bodyWidths = descendingWidths(
                    body.size(), bodyLevelCount, terminalWidth, width);
            List<List<String>> bodyLevels = slice(body, bodyWidths);

            int levelCount = Math.addExact(bodyLevels.size(), apexLevels.size());
            int maximumScore = remaining.stream().map(byId::get)
                    .mapToInt(AutomaticWeaponPlacementProposal::mechanicalScore)
                    .max().orElseThrow();
            int scoreApex = plannedBranchRanks == 1
                    ? 0
                    : roundedDivide(
                            Math.multiplyExact(maximumScore, plannedBranchRanks - 1),
                            ResearchTechTreeContract.SCORE_MAX);
            int apexOffset = Math.max(levelCount - 1, scoreApex);
            int startOffset = apexOffset - levelCount + 1;
            int localLevel = 0;
            for (List<String> level : bodyLevels) {
                batches.add(new LevelBatch(
                        branch.index(),
                        branch.stableKey(),
                        localLevel,
                        Math.addExact(firstBranchRank, startOffset + localLevel),
                        level));
                localLevel++;
            }
            for (int index = 0; index < apexLevels.size(); index++) {
                batches.add(new LevelBatch(
                        branch.index(),
                        branch.stableKey(),
                        localLevel,
                        Math.addExact(firstBranchRank, startOffset + localLevel),
                        apexLevels.get(index)));
                localLevel++;
            }
        }

        batches.sort(Comparator
                .comparingInt(LevelBatch::desiredRank)
                .thenComparing(LevelBatch::branchKey)
                .thenComparingInt(LevelBatch::localLevel));
        Map<Integer, Integer> lastRankByBranch = new HashMap<>();
        for (LevelBatch batch : batches) {
            int candidateRank = Math.max(
                    batch.desiredRank(),
                    Math.addExact(lastRankByBranch.getOrDefault(
                            batch.branchIndex(), firstBranchRank - 1), 1));
            while (Math.addExact(
                    rawWidths.getOrDefault(candidateRank, 0), batch.ids().size()) > width) {
                candidateRank = Math.addExact(candidateRank, 1);
            }
            assignRank(batch.ids(), candidateRank, rawRanks, rawWidths, width);
            lastRankByBranch.put(batch.branchIndex(), candidateRank);
        }

        if (!rawRanks.keySet().equals(proposals.keySet())) {
            throw new IllegalStateException(
                    "Automatic weapon branch layer planner did not assign every candidate");
        }
        Map<Integer, Integer> compactRanks = compactRanks(rawRanks.values());
        Map<String, AutomaticWeaponPlacementProposal> assigned = new LinkedHashMap<>();
        proposals.keySet().stream().sorted().forEach(id -> {
            AutomaticWeaponPlacementProposal proposal = proposals.get(id);
            Optional<net.minecraft.resources.ResourceLocation> bandId = policy
                    .bandForScore(proposal.mechanicalScore())
                    .map(AutomaticWeaponProgressionBand::id);
            assigned.put(id, proposal.withProgressionCoordinate(new ProgressionCoordinate(
                    compactRanks.get(rawRanks.get(id)),
                    proposal.position().siblingOrder(),
                    bandId)));
        });
        validateResult(assigned, branchModel, policy);
        return Collections.unmodifiableMap(assigned);
    }

    static int targetRankCount(
            int candidateCount,
            AutomaticWeaponPlacementPolicy policy) {
        if (candidateCount < 1 || policy == null || !policy.usesDynamicLayers()) {
            throw new IllegalArgumentException(
                    "Automatic branch target-rank inputs are invalid");
        }
        int width = policy.maxNodesPerRank();
        int foundation = Math.min(candidateCount, Math.min(policy.foundationCount(), width));
        int remaining = candidateCount - foundation;
        int minimum = 1 + divideRoundUp(remaining, width);
        int relaxed = 1 + divideRoundUp(
                Math.multiplyExact(remaining, TARGET_DENSITY_DENOMINATOR),
                Math.multiplyExact(width, TARGET_DENSITY_NUMERATOR));
        return Math.max(
                minimum,
                Math.min(relaxed, BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH));
    }

    static int sharedRankCount(int targetRankCount) {
        return ResearchTechTreeContract.taperedBranchFamilyStartIndex(
                targetRankCount);
    }

    private static void validateInputs(
            Map<String, AutomaticWeaponPlacementProposal> proposals,
            Map<String, AutomaticWeaponRoleSignature> roleSignatures,
            Map<String, AutomaticWeaponRoleSignature> authoredRoleSignatures,
            AutomaticWeaponBranchModel branchModel,
            AutomaticWeaponPlacementPolicy policy) {
        if (proposals == null || roleSignatures == null
                || authoredRoleSignatures == null || branchModel == null || policy == null
                || !proposals.keySet().equals(roleSignatures.keySet())
                || proposals.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getValue() == null
                                || !entry.getKey().equals(entry.getValue().blueprintId())
                                || roleSignatures.get(entry.getKey()) == null
                                || entry.getValue().mechanicalScore()
                                        != roleSignatures.get(entry.getKey())
                                                .mechanicalScore()
                                || entry.getValue().confidence()
                                        != roleSignatures.get(entry.getKey()).confidence())
                || !branchModel.matches(roleSignatures, authoredRoleSignatures)) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch layer planner inputs are inconsistent");
        }
        if (!proposals.isEmpty()
                && branchModel.branchLimit()
                        != AutomaticWeaponBranchAnalyzer.branchLimitForLayerWidth(
                                policy.maxNodesPerRank())) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch model does not match the layer width");
        }
    }

    private static Map<Integer, List<String>> branchMembers(
            AutomaticWeaponBranchModel branchModel,
            Map<String, AutomaticWeaponPlacementProposal> proposals) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        for (AutomaticWeaponBranchModel.Branch branch : branchModel.branches()) {
            result.put(branch.index(), branch.memberBlueprintIds().stream()
                    .sorted(Comparator.comparing(proposals::get, PROGRESSION_ORDER))
                    .toList());
        }
        return Map.copyOf(result);
    }

    private static Map<Integer, List<String>> apexCohorts(
            AutomaticWeaponBranchModel branchModel,
            Map<Integer, List<String>> branchMembers,
            Map<String, AutomaticWeaponPlacementProposal> proposals) {
        Map<Integer, List<String>> result = new LinkedHashMap<>();
        for (AutomaticWeaponBranchModel.Branch branch : branchModel.branches()) {
            List<String> members = branchMembers.get(branch.index());
            List<String> apex;
            if (!branch.terminalBlueprintIds().isEmpty()) {
                apex = branch.terminalBlueprintIds().stream()
                        .sorted(Comparator.comparing(proposals::get, PROGRESSION_ORDER))
                        .toList();
            } else {
                int count = Math.min(branch.layoutStrandCount(), members.size());
                apex = List.copyOf(members.subList(members.size() - count, members.size()));
            }
            result.put(branch.index(), apex);
        }
        return Map.copyOf(result);
    }

    private static List<Integer> descendingWidths(
            int total,
            int levels,
            int terminalWidth,
            int maximumWidth) {
        if (total == 0) {
            return List.of();
        }
        if (levels < 1 || terminalWidth < 1 || maximumWidth < 1
                || (long) levels * maximumWidth < total) {
            throw new IllegalArgumentException(
                    "Automatic weapon taper dimensions are invalid");
        }
        int floor = (long) terminalWidth * levels <= total ? terminalWidth : total / levels;
        floor = Math.max(1, floor);
        List<Integer> widths = new ArrayList<>(Collections.nCopies(levels, floor));
        int remaining = total - Math.multiplyExact(floor, levels);
        while (remaining > 0) {
            boolean progressed = false;
            for (int prefix = levels; prefix >= 1 && remaining > 0; prefix--) {
                for (int index = 0; index < prefix && remaining > 0; index++) {
                    if (widths.get(index) < maximumWidth) {
                        widths.set(index, widths.get(index) + 1);
                        remaining--;
                        progressed = true;
                    }
                }
            }
            if (!progressed) {
                throw new IllegalStateException(
                        "Automatic weapon taper could not satisfy its width bound");
            }
        }
        for (int index = 1; index < widths.size(); index++) {
            if (widths.get(index - 1) < widths.get(index)) {
                throw new IllegalStateException(
                        "Automatic weapon taper widened toward its apex");
            }
        }
        return List.copyOf(widths);
    }

    private static List<List<String>> slice(List<String> values, List<Integer> widths) {
        List<List<String>> result = new ArrayList<>();
        int cursor = 0;
        for (int width : widths) {
            result.add(List.copyOf(values.subList(cursor, cursor + width)));
            cursor += width;
        }
        if (cursor != values.size()) {
            throw new IllegalStateException(
                    "Automatic weapon taper did not consume its branch population");
        }
        return List.copyOf(result);
    }

    private static List<List<String>> chunks(List<String> values, int width) {
        List<List<String>> result = new ArrayList<>();
        for (int cursor = 0; cursor < values.size(); cursor += width) {
            result.add(List.copyOf(values.subList(
                    cursor, Math.min(values.size(), cursor + width))));
        }
        return List.copyOf(result);
    }

    private static void assignRank(
            List<String> ids,
            int rank,
            Map<String, Integer> ranks,
            Map<Integer, Integer> widths,
            int maximumWidth) {
        if (ids.isEmpty()) {
            return;
        }
        if (rank < 0) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch rank cannot be negative");
        }
        int newWidth = Math.addExact(widths.getOrDefault(rank, 0), ids.size());
        if (newWidth > maximumWidth) {
            throw new IllegalStateException(
                    "Automatic weapon branch rank exceeds its width");
        }
        for (String id : ids) {
            if (ranks.put(id, rank) != null) {
                throw new IllegalStateException(
                        "Automatic weapon branch candidate was assigned twice");
            }
        }
        widths.put(rank, newWidth);
    }

    private static Map<Integer, Integer> compactRanks(java.util.Collection<Integer> ranks) {
        List<Integer> occupied = ranks.stream().distinct().sorted().toList();
        if (occupied.size() > ResearchTechTreeContract.MAX_PROGRESSION_RANK + 1) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch layout exceeds the supported rank range");
        }
        Map<Integer, Integer> result = new HashMap<>();
        for (int index = 0; index < occupied.size(); index++) {
            result.put(occupied.get(index), index);
        }
        return Map.copyOf(result);
    }

    private static void validateResult(
            Map<String, AutomaticWeaponPlacementProposal> assigned,
            AutomaticWeaponBranchModel branchModel,
            AutomaticWeaponPlacementPolicy policy) {
        Map<Integer, Long> widths = assigned.values().stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        value -> value.progressionCoordinate().rank(),
                        java.util.stream.Collectors.counting()));
        if (widths.values().stream().anyMatch(width -> width > policy.maxNodesPerRank())) {
            throw new IllegalStateException(
                    "Automatic weapon branch allocation exceeded its rank width");
        }
        List<Integer> occupied = widths.keySet().stream().sorted().toList();
        for (int index = 0; index < occupied.size(); index++) {
            if (occupied.get(index) != index) {
                throw new IllegalStateException(
                        "Automatic weapon branch allocation contains an empty rank");
            }
        }
        for (AutomaticWeaponBranchModel.Branch branch : branchModel.branches()) {
            List<String> retainedTerminals = branch.terminalBlueprintIds().stream()
                    .filter(assigned::containsKey)
                    .toList();
            if (retainedTerminals.size() > 1
                    && retainedTerminals.size() <= policy.maxNodesPerRank()
                    && retainedTerminals.stream().map(id -> assigned.get(id)
                            .progressionCoordinate().rank()).distinct().count() != 1) {
                throw new IllegalStateException(
                        "Automatic weapon terminal peers were split across ranks");
            }
        }
    }

    private static int divideRoundUp(int value, int divisor) {
        if (value < 0 || divisor < 1) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch division inputs are invalid");
        }
        return value == 0 ? 0 : 1 + (value - 1) / divisor;
    }

    private static int roundedDivide(int numerator, int denominator) {
        if (numerator < 0 || denominator < 1) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch rounded division inputs are invalid");
        }
        return Math.floorDiv(Math.addExact(numerator, denominator / 2), denominator);
    }

    private record LevelBatch(
            int branchIndex,
            String branchKey,
            int localLevel,
            int desiredRank,
            List<String> ids) {
        private LevelBatch {
            ids = List.copyOf(ids);
            if (branchIndex < 0 || branchKey == null || branchKey.isBlank()
                    || localLevel < 0 || desiredRank < 0 || ids.isEmpty()) {
                throw new IllegalArgumentException(
                        "Automatic weapon branch level is invalid");
            }
        }
    }
}
