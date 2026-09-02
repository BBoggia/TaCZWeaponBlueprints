package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Discovers bounded deterministic role families without assigning ranks or parents. */
public final class AutomaticWeaponBranchAnalyzer {
    public static final int MAX_BRANCHES = 12;
    public static final int TARGET_CANDIDATES_PER_SQUARED_BRANCH = 6;
    public static final int MIN_ROLE_DISTANCE_FOR_SPLIT = 20;
    /** Compatibility aliases retained for integrations written against Phase 3. */
    @Deprecated(forRemoval = false)
    public static final int TERMINAL_SCORE_TOLERANCE =
            AutomaticWeaponTerminalClusterResolver.MAX_SCORE_TOLERANCE;
    @Deprecated(forRemoval = false)
    public static final int MAX_TERMINAL_PEERS =
            AutomaticWeaponTerminalClusterResolver.MAX_TERMINAL_MEMBERS;
    public static final int MAX_LAYOUT_STRANDS_PER_BRANCH = 3;

    private static final Comparator<AutomaticWeaponRoleSignature> ROLE_ORDER = (left, right) -> {
        int result;
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            result = Integer.compare(
                    left.relativeMetricOffsets().get(metric.serializedName()),
                    right.relativeMetricOffsets().get(metric.serializedName()));
            if (result != 0) {
                return result;
            }
        }
        result = Boolean.compare(left.explosive(), right.explosive());
        if (result != 0) {
            return result;
        }
        result = left.archetype().compareTo(right.archetype());
        if (result != 0) {
            return result;
        }
        return left.blueprintId().compareTo(right.blueprintId());
    };
    private static final Comparator<AutomaticWeaponRoleSignature> INITIAL_SEED_ORDER =
            Comparator.comparingLong(AutomaticWeaponBranchAnalyzer::roleMagnitude)
                    .thenComparing(ROLE_ORDER);

    public AutomaticWeaponBranchModel discover(
            Map<String, AutomaticWeaponRoleSignature> signatures) {
        return discover(signatures, Map.of(), MAX_BRANCHES);
    }

    public AutomaticWeaponBranchModel discover(
            Map<String, AutomaticWeaponRoleSignature> signatures,
            Map<String, AutomaticWeaponRoleSignature> authoredSignatures,
            int branchLimit) {
        if (signatures == null || authoredSignatures == null
                || branchLimit < 1 || branchLimit > MAX_BRANCHES
                || signatures.size() > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || authoredSignatures.size()
                        > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || (long) signatures.size() + authoredSignatures.size()
                        > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || signatures.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getValue() == null
                                || !entry.getKey().equals(entry.getValue().blueprintId()))
                || authoredSignatures.entrySet().stream().anyMatch(entry ->
                        entry.getKey() == null || entry.getValue() == null
                                || !entry.getKey().equals(entry.getValue().blueprintId()))
                || signatures.keySet().stream().anyMatch(authoredSignatures::containsKey)) {
            throw new IllegalArgumentException("Automatic weapon branch-analysis input is invalid");
        }
        if (signatures.isEmpty()) {
            return AutomaticWeaponBranchModel.EMPTY;
        }
        List<AutomaticWeaponRoleSignature> candidates = signatures.values().stream()
                .sorted(Comparator.comparing(AutomaticWeaponRoleSignature::blueprintId))
                .toList();
        List<AutomaticWeaponRoleSignature> candidateSeeds = candidates.stream()
                .filter(AutomaticWeaponRoleSignature::maySeedBranch)
                .sorted(ROLE_ORDER)
                .toList();
        List<AutomaticWeaponRoleSignature> authoredSeeds = authoredSignatures.values().stream()
                .filter(AutomaticWeaponRoleSignature::maySeedBranch)
                .sorted(ROLE_ORDER)
                .toList();
        List<AutomaticWeaponRoleSignature> seeds = java.util.stream.Stream
                .concat(candidateSeeds.stream(), authoredSeeds.stream())
                .sorted(ROLE_ORDER)
                .toList();
        int capacity = targetBranchCapacity(candidates.size(), branchLimit);
        if (seeds.isEmpty()) {
            return fallbackModel(candidates, branchLimit, capacity);
        }

        List<AutomaticWeaponRoleSignature> medoids = discoverMedoids(seeds, capacity).stream()
                .sorted(ROLE_ORDER)
                .toList();
        List<List<String>> membersByBranch = new ArrayList<>();
        for (int index = 0; index < medoids.size(); index++) {
            membersByBranch.add(new ArrayList<>());
        }
        List<AutomaticWeaponRoleSignature> unscored = new ArrayList<>();
        for (AutomaticWeaponRoleSignature candidate : candidates) {
            if (!candidate.scoredEvidence()) {
                unscored.add(candidate);
                continue;
            }
            int branch = closestBranch(candidate, medoids);
            membersByBranch.get(branch).add(candidate.blueprintId());
        }
        for (AutomaticWeaponRoleSignature candidate : unscored) {
            int branch = fallbackBranch(candidate, medoids, membersByBranch);
            membersByBranch.get(branch).add(candidate.blueprintId());
        }

        List<AutomaticWeaponRoleSignature> populatedMedoids = new ArrayList<>();
        List<List<String>> populatedMembers = new ArrayList<>();
        for (int index = 0; index < medoids.size(); index++) {
            if (!membersByBranch.get(index).isEmpty()) {
                populatedMedoids.add(medoids.get(index));
                populatedMembers.add(membersByBranch.get(index));
            }
        }
        medoids = List.copyOf(populatedMedoids);
        membersByBranch = populatedMembers;

        List<List<String>> authoredAnchorsByBranch = new ArrayList<>();
        for (int index = 0; index < medoids.size(); index++) {
            authoredAnchorsByBranch.add(new ArrayList<>());
        }
        for (AutomaticWeaponRoleSignature anchor : authoredSeeds) {
            int branch = closestBranch(anchor, medoids);
            authoredAnchorsByBranch.get(branch).add(anchor.blueprintId());
        }

        List<AutomaticWeaponBranchModel.Branch> branches = new ArrayList<>();
        Map<String, Integer> branchAssignments = assignments(membersByBranch);
        for (int index = 0; index < medoids.size(); index++) {
            List<String> members = membersByBranch.get(index);
            AutomaticWeaponTerminalCluster terminalCluster =
                    resolveTerminalCluster(members, signatures);
            AutomaticWeaponRoleSignature medoid = medoids.get(index);
            branches.add(new AutomaticWeaponBranchModel.Branch(
                    index,
                    stableBranchKey(medoid),
                    Optional.of(medoid.blueprintId()),
                    members,
                    terminalCluster.terminalBlueprintIds(),
                    terminalCluster,
                    authoredAnchorsByBranch.get(index),
                    layoutStrandCount(members.size())));
        }
        AutomaticWeaponBranchModel result = new AutomaticWeaponBranchModel(
                candidates.size(),
                seeds.size(),
                branchLimit,
                capacity,
                branches,
                branchAssignments);
        if (!result.matches(signatures, authoredSignatures)) {
            throw new IllegalStateException(
                    "Automatic weapon branch analysis produced an inconsistent model");
        }
        return result;
    }

    public static int targetBranchCapacity(int candidateCount) {
        return candidateCount == 0 ? 0 : targetBranchCapacity(candidateCount, MAX_BRANCHES);
    }

    public static int targetBranchCapacity(int candidateCount, int branchLimit) {
        if (candidateCount < 0
                || candidateCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || branchLimit < 1 || branchLimit > MAX_BRANCHES) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch population or limit is out of bounds");
        }
        if (candidateCount == 0) {
            return 0;
        }
        int result = 1;
        while ((long) result * result * TARGET_CANDIDATES_PER_SQUARED_BRANCH
                        < candidateCount
                && result < branchLimit) {
            result++;
        }
        return result;
    }

    public static int branchLimitForLayerWidth(int maximumNodesPerRank) {
        if (maximumNodesPerRank < AutomaticWeaponPlacementPolicy.MIN_MAX_NODES_PER_RANK
                || maximumNodesPerRank
                        > AutomaticWeaponPlacementPolicy.MAX_MAX_NODES_PER_RANK) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch layer width is out of bounds");
        }
        return Math.min(MAX_BRANCHES, Math.max(1, (maximumNodesPerRank + 1) / 2));
    }

    private static List<AutomaticWeaponRoleSignature> discoverMedoids(
            List<AutomaticWeaponRoleSignature> seeds,
            int capacity) {
        List<AutomaticWeaponRoleSignature> medoids = new ArrayList<>();
        medoids.add(seeds.stream().min(INITIAL_SEED_ORDER).orElseThrow());
        while (medoids.size() < capacity) {
            AutomaticWeaponRoleSignature next = null;
            int greatestMinimumDistance = -1;
            for (AutomaticWeaponRoleSignature candidate : seeds) {
                if (medoids.contains(candidate)) {
                    continue;
                }
                int minimumDistance = medoids.stream()
                        .mapToInt(medoid -> roleDistance(candidate, medoid))
                        .min().orElseThrow();
                if (minimumDistance > greatestMinimumDistance
                        || minimumDistance == greatestMinimumDistance
                                && ROLE_ORDER.compare(candidate, next) < 0) {
                    next = candidate;
                    greatestMinimumDistance = minimumDistance;
                }
            }
            if (next == null || greatestMinimumDistance < MIN_ROLE_DISTANCE_FOR_SPLIT) {
                break;
            }
            medoids.add(next);
        }
        return List.copyOf(medoids);
    }

    private static int closestBranch(
            AutomaticWeaponRoleSignature candidate,
            List<AutomaticWeaponRoleSignature> medoids) {
        int bestIndex = 0;
        int bestSimilarity = -1;
        long bestAffinity = 0L;
        for (int index = 0; index < medoids.size(); index++) {
            AutomaticWeaponRoleSignature medoid = medoids.get(index);
            int similarity = candidate.similarityTo(medoid).orElseThrow();
            long affinity = stableAffinity(candidate.blueprintId(), medoid.blueprintId());
            if (similarity > bestSimilarity
                    || similarity == bestSimilarity
                            && Long.compareUnsigned(affinity, bestAffinity) > 0) {
                bestIndex = index;
                bestSimilarity = similarity;
                bestAffinity = affinity;
            }
        }
        return bestIndex;
    }

    private static int fallbackBranch(
            AutomaticWeaponRoleSignature candidate,
            List<AutomaticWeaponRoleSignature> medoids,
            List<List<String>> membersByBranch) {
        List<Integer> archetypeMatches = java.util.stream.IntStream.range(0, medoids.size())
                .filter(index -> candidate.archetype().equals(medoids.get(index).archetype()))
                .boxed().toList();
        List<Integer> choices = archetypeMatches.isEmpty()
                ? java.util.stream.IntStream.range(0, medoids.size()).boxed().toList()
                : archetypeMatches;
        return choices.stream().min(Comparator
                .comparingInt((Integer index) -> membersByBranch.get(index).size())
                .thenComparingInt(Integer::intValue)).orElseThrow();
    }

    static List<String> terminalPeers(
            List<String> members,
            Map<String, AutomaticWeaponRoleSignature> signatures) {
        return resolveTerminalCluster(members, signatures).terminalBlueprintIds();
    }

    static AutomaticWeaponTerminalCluster resolveTerminalCluster(
            List<String> members,
            Map<String, AutomaticWeaponRoleSignature> signatures) {
        return new AutomaticWeaponTerminalClusterResolver().resolve(members, signatures);
    }

    static int layoutStrandCount(int memberCount) {
        if (memberCount < 1
                || memberCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch member count is out of bounds");
        }
        return targetBranchCapacity(memberCount, MAX_LAYOUT_STRANDS_PER_BRANCH);
    }

    static String stableBranchKey(AutomaticWeaponRoleSignature medoid) {
        if (medoid == null || !medoid.scoredEvidence()) {
            throw new IllegalArgumentException(
                    "A stable automatic weapon role key requires scored evidence");
        }
        StringBuilder result = new StringBuilder("role|")
                .append(medoid.archetype()).append('|')
                .append(medoid.explosive() ? '1' : '0');
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            result.append('|').append(
                    medoid.relativeMetricOffsets().get(metric.serializedName()));
        }
        return result.toString();
    }

    static String fallbackBranchKey(int index, int capacity) {
        if (index < 0 || capacity < 1 || index >= capacity) {
            throw new IllegalArgumentException(
                    "Automatic weapon fallback branch key inputs are invalid");
        }
        return "fallback|" + capacity + '|' + index;
    }

    private static Map<String, Integer> assignments(List<List<String>> membersByBranch) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (int index = 0; index < membersByBranch.size(); index++) {
            for (String member : membersByBranch.get(index).stream().sorted().toList()) {
                if (result.put(member, index) != null) {
                    throw new IllegalStateException(
                            "Automatic weapon branch analysis assigned a candidate twice");
                }
            }
        }
        return Map.copyOf(result);
    }

    private static int roleDistance(
            AutomaticWeaponRoleSignature left,
            AutomaticWeaponRoleSignature right) {
        return 100 - left.similarityTo(right).orElseThrow();
    }

    private static long roleMagnitude(AutomaticWeaponRoleSignature signature) {
        long result = 0L;
        for (MechanicalMetric metric : MechanicalMetric.values()) {
            result = Math.addExact(
                    result,
                    Math.multiplyExact(
                            (long) metric.weight(),
                            Math.abs(signature.relativeMetricOffsets().get(
                                    metric.serializedName()))));
        }
        return result;
    }

    private static AutomaticWeaponBranchModel fallbackModel(
            List<AutomaticWeaponRoleSignature> candidates,
            int branchLimit,
            int capacity) {
        List<List<String>> membersByBranch = new ArrayList<>();
        for (int index = 0; index < capacity; index++) {
            membersByBranch.add(new ArrayList<>());
        }
        for (int index = 0; index < candidates.size(); index++) {
            membersByBranch.get(index % capacity).add(candidates.get(index).blueprintId());
        }
        List<AutomaticWeaponBranchModel.Branch> branches = new ArrayList<>();
        for (int index = 0; index < capacity; index++) {
            List<String> members = membersByBranch.get(index);
            branches.add(new AutomaticWeaponBranchModel.Branch(
                    index,
                    fallbackBranchKey(index, capacity),
                    Optional.empty(),
                    members,
                    List.of(),
                    AutomaticWeaponTerminalCluster.none(0),
                    List.of(),
                    layoutStrandCount(members.size())));
        }
        return new AutomaticWeaponBranchModel(
                candidates.size(),
                0,
                branchLimit,
                capacity,
                branches,
                assignments(membersByBranch));
    }

    private static long stableAffinity(String candidateId, String medoidId) {
        long hash = 0xcbf29ce484222325L;
        String value = candidateId + '\0' + medoidId;
        for (int index = 0; index < value.length(); index++) {
            hash ^= value.charAt(index);
            hash *= 0x100000001b3L;
        }
        return hash;
    }
}
