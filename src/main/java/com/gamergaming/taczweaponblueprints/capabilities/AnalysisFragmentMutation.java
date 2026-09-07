package com.gamergaming.taczweaponblueprints.capabilities;

import java.util.Objects;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts;

/** Compare-and-set all reward classes for one target, including rollback. */
public record AnalysisFragmentMutation(PlayerProgressValueMutation.Operation operation, String target,
        AnalysisFragmentCounts expected, AnalysisFragmentCounts resulting) {
    public AnalysisFragmentMutation {
        Objects.requireNonNull(operation); Objects.requireNonNull(expected); Objects.requireNonNull(resulting);
    }
}
