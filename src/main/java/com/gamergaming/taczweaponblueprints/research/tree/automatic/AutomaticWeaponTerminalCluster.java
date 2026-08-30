package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Immutable evidence explaining one branch's terminal-cluster decision. */
public record AutomaticWeaponTerminalCluster(
        Optional<String> anchorBlueprintId,
        List<String> terminalBlueprintIds,
        List<String> deferredEquivalentBlueprintIds,
        int reliableCandidateCount,
        int equivalentCandidateCount,
        int adaptiveScoreTolerance,
        Resolution resolution,
        Optional<String> diagnostic) {
    public static final String TRUNCATED_DIAGNOSTIC = "terminal_cluster_truncated";

    public AutomaticWeaponTerminalCluster {
        anchorBlueprintId = anchorBlueprintId == null ? Optional.empty() : anchorBlueprintId;
        terminalBlueprintIds = immutableIds(terminalBlueprintIds);
        deferredEquivalentBlueprintIds = immutableIds(deferredEquivalentBlueprintIds);
        diagnostic = diagnostic == null ? Optional.empty() : diagnostic;
        String anchor = anchorBlueprintId.orElse(null);
        if (resolution == null
                || reliableCandidateCount < 0
                || reliableCandidateCount
                        > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || equivalentCandidateCount < 0
                || equivalentCandidateCount > reliableCandidateCount
                || (long) terminalBlueprintIds.size()
                                + deferredEquivalentBlueprintIds.size()
                        > WeaponMechanicalReferenceCatalog.MAX_REFERENCE_WEAPONS
                || adaptiveScoreTolerance < 0
                || adaptiveScoreTolerance
                        > AutomaticWeaponTerminalClusterResolver.MAX_SCORE_TOLERANCE
                || terminalBlueprintIds.size()
                        > AutomaticWeaponTerminalClusterResolver.MAX_TERMINAL_MEMBERS
                || terminalBlueprintIds.stream()
                        .anyMatch(deferredEquivalentBlueprintIds::contains)
                || (anchor != null && !terminalBlueprintIds.contains(anchor))
                || equivalentCandidateCount != Math.addExact(
                        terminalBlueprintIds.size(), deferredEquivalentBlueprintIds.size())
                || diagnostic.filter(value -> !TRUNCATED_DIAGNOSTIC.equals(value)).isPresent()) {
            throw new IllegalArgumentException("Automatic weapon terminal cluster is invalid");
        }
        boolean empty = anchorBlueprintId.isEmpty();
        if (empty != terminalBlueprintIds.isEmpty()
                || empty != (resolution == Resolution.NONE)
                || empty && (equivalentCandidateCount != 0
                        || reliableCandidateCount != 0
                        || adaptiveScoreTolerance != 0
                        || !deferredEquivalentBlueprintIds.isEmpty()
                        || diagnostic.isPresent())
                || !empty && equivalentCandidateCount < 1
                || resolution == Resolution.SINGLE && equivalentCandidateCount != 1
                || resolution == Resolution.EQUIVALENT
                        && (equivalentCandidateCount < 2
                                || equivalentCandidateCount
                                        > AutomaticWeaponTerminalClusterResolver
                                                .MAX_TERMINAL_MEMBERS
                                || !deferredEquivalentBlueprintIds.isEmpty())
                || (resolution == Resolution.ROLE_PARTITIONED
                        || resolution == Resolution.TRUNCATED)
                        && deferredEquivalentBlueprintIds.isEmpty()
                || resolution == Resolution.TRUNCATED
                        && (terminalBlueprintIds.size()
                                != AutomaticWeaponTerminalClusterResolver
                                        .MAX_TERMINAL_MEMBERS
                                || equivalentCandidateCount
                                        <= AutomaticWeaponTerminalClusterResolver
                                                .MAX_TERMINAL_MEMBERS)
                || (resolution == Resolution.TRUNCATED) != diagnostic.isPresent()) {
            throw new IllegalArgumentException(
                    "Automatic weapon terminal-cluster resolution is inconsistent");
        }
    }

    public static AutomaticWeaponTerminalCluster none(int reliableCandidateCount) {
        return new AutomaticWeaponTerminalCluster(
                Optional.empty(),
                List.of(),
                List.of(),
                reliableCandidateCount,
                0,
                0,
                Resolution.NONE,
                Optional.empty());
    }

    public int deferredEquivalentCount() {
        return deferredEquivalentBlueprintIds.size();
    }

    public boolean truncated() {
        return resolution == Resolution.TRUNCATED;
    }

    private static List<String> immutableIds(List<String> source) {
        if (source == null || source.stream().anyMatch(value ->
                value == null || ResourceLocation.tryParse(value) == null)) {
            throw new IllegalArgumentException(
                    "Automatic weapon terminal-cluster IDs are invalid");
        }
        List<String> result = source.stream().distinct().sorted().toList();
        if (result.size() != source.size()) {
            throw new IllegalArgumentException(
                    "Automatic weapon terminal-cluster IDs contain duplicates");
        }
        return result;
    }

    public enum Resolution {
        NONE("none"),
        SINGLE("single"),
        EQUIVALENT("equivalent"),
        ROLE_PARTITIONED("role_partitioned"),
        TRUNCATED("truncated");

        private final String serializedName;

        Resolution(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }
    }
}
