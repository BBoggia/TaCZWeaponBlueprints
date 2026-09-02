package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import net.minecraft.resources.ResourceLocation;

/** Immutable pre-topology role-family partition for automatic weapon candidates. */
public record AutomaticWeaponBranchModel(
        int candidateCount,
        int seedSignatureCount,
        int branchLimit,
        int branchCapacity,
        List<Branch> branches,
        Map<String, Integer> branchIndexByBlueprint) {
    public static final AutomaticWeaponBranchModel EMPTY =
            new AutomaticWeaponBranchModel(0, 0, 0, 0, List.of(), Map.of());

    public AutomaticWeaponBranchModel {
        if (candidateCount < 0
                || candidateCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || seedSignatureCount < 0
                || seedSignatureCount > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || branchLimit < 0
                || branchLimit > AutomaticWeaponBranchAnalyzer.MAX_BRANCHES
                || branchCapacity < 0
                || branchCapacity > AutomaticWeaponBranchAnalyzer.MAX_BRANCHES
                || branches == null || branchIndexByBlueprint == null) {
            throw new IllegalArgumentException("Automatic weapon branch model is invalid");
        }
        if (branches.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch model contains null");
        }
        branches = branches.stream().sorted(Comparator.comparingInt(Branch::index)).toList();
        Map<String, Integer> assignments = immutableAssignments(branchIndexByBlueprint);
        branchIndexByBlueprint = assignments;
        int expectedCapacity = candidateCount == 0
                ? 0
                : AutomaticWeaponBranchAnalyzer.targetBranchCapacity(
                        candidateCount, branchLimit);
        if (branchCapacity != expectedCapacity) {
            throw new IllegalArgumentException(
                    "Automatic weapon branch capacity does not match its candidate population");
        }

        if (candidateCount == 0) {
            if (seedSignatureCount != 0 || branchLimit != 0 || branchCapacity != 0
                    || !branches.isEmpty() || !assignments.isEmpty()) {
                throw new IllegalArgumentException(
                        "Empty automatic weapon branch model is inconsistent");
            }
        } else {
            if (branchLimit < 1 || branchCapacity < 1 || branches.isEmpty()
                    || branches.size() > branchCapacity
                    || assignments.size() != candidateCount) {
                throw new IllegalArgumentException(
                        "Automatic weapon branch model population is inconsistent");
            }
            Set<String> members = new LinkedHashSet<>();
            Set<String> stableKeys = new LinkedHashSet<>();
            for (int index = 0; index < branches.size(); index++) {
                Branch branch = branches.get(index);
                if (branch.index() != index || !stableKeys.add(branch.stableKey())) {
                    throw new IllegalArgumentException(
                            "Automatic weapon branch indexes and stable keys must be unique");
                }
                for (String member : branch.memberBlueprintIds()) {
                    if (!members.add(member)
                            || !Integer.valueOf(index).equals(assignments.get(member))) {
                        throw new IllegalArgumentException(
                                "Automatic weapon branch members are inconsistent");
                    }
                }
            }
            if (!members.equals(assignments.keySet())) {
                throw new IllegalArgumentException(
                        "Automatic weapon branch assignments are not exhaustive");
            }
        }
    }

    public Optional<Branch> branchFor(String blueprintId) {
        Integer index = blueprintId == null ? null : branchIndexByBlueprint.get(blueprintId);
        return index == null ? Optional.empty() : Optional.of(branches.get(index));
    }

    /** Compatibility name retained for Phase-3 diagnostic callers. */
    @Deprecated(forRemoval = false)
    public int seedCandidateCount() {
        return seedSignatureCount;
    }

    public boolean matches(Map<String, AutomaticWeaponRoleSignature> signatures) {
        return matches(signatures, Map.of());
    }

    public boolean matches(
            Map<String, AutomaticWeaponRoleSignature> signatures,
            Map<String, AutomaticWeaponRoleSignature> authoredSignatures) {
        if (signatures == null || !signatures.keySet().equals(branchIndexByBlueprint.keySet())) {
            return false;
        }
        if (authoredSignatures == null) {
            return false;
        }
        if (signatures.entrySet().stream().anyMatch(entry -> entry.getValue() == null
                || !entry.getKey().equals(entry.getValue().blueprintId()))
                || authoredSignatures.entrySet().stream().anyMatch(entry ->
                        entry.getValue() == null
                                || !entry.getKey().equals(entry.getValue().blueprintId()))) {
            return false;
        }
        if (signatures.isEmpty()) {
            return equals(EMPTY);
        }
        long candidateSeeds = signatures.values().stream()
                .filter(AutomaticWeaponRoleSignature::maySeedBranch)
                .count();
        long authoredSeeds = authoredSignatures.values().stream()
                .filter(AutomaticWeaponRoleSignature::maySeedBranch)
                .count();
        long expectedSeeds = candidateSeeds + authoredSeeds;
        if (expectedSeeds != seedSignatureCount) {
            return false;
        }
        Set<String> assignedAuthoredAnchors = new LinkedHashSet<>();
        for (Branch branch : branches) {
            if (branch.medoidBlueprintId().isPresent()) {
                String medoidId = branch.medoidBlueprintId().orElseThrow();
                AutomaticWeaponRoleSignature medoid = signatures.get(medoidId);
                if (medoid == null) {
                    medoid = authoredSignatures.get(medoidId);
                }
                if (medoid == null || !medoid.maySeedBranch()
                        || !branch.memberBlueprintIds().contains(medoid.blueprintId())
                                && !branch.authoredAnchorBlueprintIds().contains(
                                        medoid.blueprintId())
                        || !branch.stableKey().equals(
                                AutomaticWeaponBranchAnalyzer.stableBranchKey(medoid))) {
                    return false;
                }
            } else if (seedSignatureCount != 0
                    || !branch.stableKey().equals(
                            AutomaticWeaponBranchAnalyzer.fallbackBranchKey(
                                    branch.index(), branchCapacity))) {
                return false;
            }
            for (String anchorId : branch.authoredAnchorBlueprintIds()) {
                AutomaticWeaponRoleSignature anchor = authoredSignatures.get(anchorId);
                if (anchor == null || !anchor.maySeedBranch()
                        || !assignedAuthoredAnchors.add(anchorId)) {
                    return false;
                }
            }
            if (branch.terminalBlueprintIds().stream().anyMatch(id -> {
                AutomaticWeaponRoleSignature signature = signatures.get(id);
                return signature == null || !signature.maySeedBranch();
            })) {
                return false;
            }
            AutomaticWeaponTerminalCluster expectedCluster =
                    AutomaticWeaponBranchAnalyzer.resolveTerminalCluster(
                            branch.memberBlueprintIds(), signatures);
            if (!branch.terminalCluster().equals(expectedCluster)
                    || !branch.terminalBlueprintIds().equals(
                            expectedCluster.terminalBlueprintIds())) {
                return false;
            }
            if (branch.layoutStrandCount()
                    != AutomaticWeaponBranchAnalyzer.layoutStrandCount(
                            branch.memberBlueprintIds().size())) {
                return false;
            }
        }
        Set<String> expectedAuthoredAnchors = seedSignatureCount == 0
                ? Set.of()
                : authoredSignatures.values().stream()
                        .filter(AutomaticWeaponRoleSignature::maySeedBranch)
                        .map(AutomaticWeaponRoleSignature::blueprintId)
                        .collect(java.util.stream.Collectors.toSet());
        if (!assignedAuthoredAnchors.equals(expectedAuthoredAnchors)) {
            return false;
        }
        return true;
    }

    private static Map<String, Integer> immutableAssignments(Map<String, Integer> source) {
        Map<String, Integer> copy = new LinkedHashMap<>();
        source.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            if (!validId(entry.getKey()) || entry.getValue() == null
                    || entry.getValue() < 0) {
                throw new IllegalArgumentException(
                        "Automatic weapon branch assignment is invalid");
            }
            copy.put(entry.getKey(), entry.getValue());
        });
        return Collections.unmodifiableMap(copy);
    }

    private static boolean validId(String value) {
        return value != null && ResourceLocation.tryParse(value) != null;
    }

    public record Branch(
            int index,
            String stableKey,
            Optional<String> medoidBlueprintId,
            List<String> memberBlueprintIds,
            List<String> terminalBlueprintIds,
            AutomaticWeaponTerminalCluster terminalCluster,
            List<String> authoredAnchorBlueprintIds,
            int layoutStrandCount) {
        public Branch {
            medoidBlueprintId = medoidBlueprintId == null
                    ? Optional.empty() : medoidBlueprintId;
            memberBlueprintIds = immutableIds(memberBlueprintIds);
            terminalBlueprintIds = immutableIds(terminalBlueprintIds);
            authoredAnchorBlueprintIds = immutableIds(authoredAnchorBlueprintIds);
            String medoid = medoidBlueprintId.orElse(null);
            if (index < 0 || stableKey == null || stableKey.isBlank()
                    || !stableKey.equals(stableKey.trim())
                    || memberBlueprintIds.isEmpty()
                    || medoid != null
                            && !memberBlueprintIds.contains(medoid)
                            && !authoredAnchorBlueprintIds.contains(medoid)
                    || !memberBlueprintIds.containsAll(terminalBlueprintIds)
                    || terminalCluster == null
                    || !terminalBlueprintIds.equals(
                            terminalCluster.terminalBlueprintIds())
                    || !memberBlueprintIds.containsAll(
                            terminalCluster.deferredEquivalentBlueprintIds())
                    || terminalBlueprintIds.size()
                            > AutomaticWeaponTerminalClusterResolver.MAX_TERMINAL_MEMBERS
                    || layoutStrandCount < 1
                    || layoutStrandCount
                            > AutomaticWeaponBranchAnalyzer.MAX_LAYOUT_STRANDS_PER_BRANCH
                    || layoutStrandCount > memberBlueprintIds.size()) {
                throw new IllegalArgumentException("Automatic weapon branch is invalid");
            }
        }

        /** Compatibility constructor for callers predating Phase 7 cluster evidence. */
        public Branch(
                int index,
                String stableKey,
                Optional<String> medoidBlueprintId,
                List<String> memberBlueprintIds,
                List<String> terminalBlueprintIds,
                List<String> authoredAnchorBlueprintIds,
                int layoutStrandCount) {
            this(
                    index,
                    stableKey,
                    medoidBlueprintId,
                    memberBlueprintIds,
                    terminalBlueprintIds,
                    legacyTerminalCluster(terminalBlueprintIds),
                    authoredAnchorBlueprintIds,
                    layoutStrandCount);
        }

        private static List<String> immutableIds(List<String> source) {
            if (source == null || source.stream().anyMatch(value -> !validId(value))) {
                throw new IllegalArgumentException(
                        "Automatic weapon branch IDs are invalid");
            }
            List<String> result = source.stream().distinct().sorted().toList();
            if (result.size() != source.size()) {
                throw new IllegalArgumentException(
                        "Automatic weapon branch IDs contain duplicates");
            }
            return result;
        }

        private static AutomaticWeaponTerminalCluster legacyTerminalCluster(
                List<String> terminalBlueprintIds) {
            if (terminalBlueprintIds == null || terminalBlueprintIds.isEmpty()) {
                return AutomaticWeaponTerminalCluster.none(0);
            }
            List<String> ids = terminalBlueprintIds.stream().distinct().sorted().toList();
            return new AutomaticWeaponTerminalCluster(
                    Optional.of(ids.get(0)),
                    ids,
                    List.of(),
                    ids.size(),
                    ids.size(),
                    1,
                    ids.size() == 1
                            ? AutomaticWeaponTerminalCluster.Resolution.SINGLE
                            : AutomaticWeaponTerminalCluster.Resolution.EQUIVALENT,
                    Optional.empty());
        }
    }
}
