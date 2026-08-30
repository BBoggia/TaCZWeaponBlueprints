package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

/** Pure policy inputs for non-authoritative automatic placement proposals. */
public record AutomaticWeaponPlacementPolicy(
        int levelsPerTier,
        int reviewConfidenceThreshold,
        ReviewHandling reviewHandling,
        int maxGeneratedPrerequisites,
        int mergeInterval,
        LayeringStrategy layeringStrategy,
        int maxNodesPerRank,
        List<AutomaticWeaponProgressionBand> progressionBands,
        int foundationCount) {
    public static final int DEFAULT_REVIEW_CONFIDENCE_THRESHOLD = 60;
    public static final int DEFAULT_MAX_GENERATED_PREREQUISITES = 2;
    public static final int MAX_GENERATED_PREREQUISITES = 3;
    public static final int DEFAULT_MERGE_INTERVAL = 4;
    public static final int MAX_MERGE_INTERVAL = 64;
    public static final int DEFAULT_MAX_NODES_PER_RANK = 9;
    public static final int MIN_MAX_NODES_PER_RANK = 1;
    public static final int MAX_MAX_NODES_PER_RANK = 32;
    public static final int DEFAULT_FOUNDATION_COUNT = 2;
    public static final int MIN_FOUNDATION_COUNT = 1;
    public static final int MAX_FOUNDATION_COUNT = 3;
    public static final int MAX_PROGRESSION_BANDS = 32;
    public static final AutomaticWeaponPlacementPolicy DEFAULT =
            new AutomaticWeaponPlacementPolicy(
                    ResearchTechTreeContract.DEFAULT_LEVELS_PER_TIER,
                    DEFAULT_REVIEW_CONFIDENCE_THRESHOLD,
                    ReviewHandling.EXCLUDE,
                    DEFAULT_MAX_GENERATED_PREREQUISITES,
                    DEFAULT_MERGE_INTERVAL,
                    LayeringStrategy.LEGACY_SCORE_BUCKETS,
                    DEFAULT_MAX_NODES_PER_RANK,
                    List.of(),
                    DEFAULT_FOUNDATION_COUNT);

    /** Compatibility constructor for policies predating configurable foundations. */
    public AutomaticWeaponPlacementPolicy(
            int levelsPerTier,
            int reviewConfidenceThreshold,
            ReviewHandling reviewHandling,
            int maxGeneratedPrerequisites,
            int mergeInterval,
            LayeringStrategy layeringStrategy,
            int maxNodesPerRank,
            List<AutomaticWeaponProgressionBand> progressionBands) {
        this(
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                maxGeneratedPrerequisites,
                mergeInterval,
                layeringStrategy,
                maxNodesPerRank,
                progressionBands,
                DEFAULT_FOUNDATION_COUNT);
    }

    /** Backward-compatible policy construction retains the previous safe review gate. */
    public AutomaticWeaponPlacementPolicy(
            int levelsPerTier,
            int reviewConfidenceThreshold) {
        this(
                levelsPerTier,
                reviewConfidenceThreshold,
                ReviewHandling.EXCLUDE,
                DEFAULT_MAX_GENERATED_PREREQUISITES,
                DEFAULT_MERGE_INTERVAL,
                LayeringStrategy.LEGACY_SCORE_BUCKETS,
                DEFAULT_MAX_NODES_PER_RANK,
                List.of(),
                DEFAULT_FOUNDATION_COUNT);
    }

    /** Existing three-argument call sites inherit the bounded merge defaults. */
    public AutomaticWeaponPlacementPolicy(
            int levelsPerTier,
            int reviewConfidenceThreshold,
            ReviewHandling reviewHandling) {
        this(
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                DEFAULT_MAX_GENERATED_PREREQUISITES,
                DEFAULT_MERGE_INTERVAL,
                LayeringStrategy.LEGACY_SCORE_BUCKETS,
                DEFAULT_MAX_NODES_PER_RANK,
                List.of(),
                DEFAULT_FOUNDATION_COUNT);
    }

    /** Existing five-argument call sites retain Phase 4 score-bucket behavior. */
    public AutomaticWeaponPlacementPolicy(
            int levelsPerTier,
            int reviewConfidenceThreshold,
            ReviewHandling reviewHandling,
            int maxGeneratedPrerequisites,
            int mergeInterval) {
        this(
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                maxGeneratedPrerequisites,
                mergeInterval,
                LayeringStrategy.LEGACY_SCORE_BUCKETS,
                DEFAULT_MAX_NODES_PER_RANK,
                List.of(),
                DEFAULT_FOUNDATION_COUNT);
    }

    public AutomaticWeaponPlacementPolicy {
        if (levelsPerTier < ResearchTechTreeContract.MIN_LEVELS_PER_TIER
                || levelsPerTier > ResearchTechTreeContract.MAX_LEVELS_PER_TIER) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement levels per tier are out of bounds");
        }
        if (reviewConfidenceThreshold < 0
                || reviewConfidenceThreshold > ResearchTechTreeContract.SCORE_MAX) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement review confidence is out of bounds");
        }
        if (reviewHandling == null || layeringStrategy == null
                || progressionBands == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement review handling cannot be null");
        }
        if (maxGeneratedPrerequisites < 1
                || maxGeneratedPrerequisites > MAX_GENERATED_PREREQUISITES) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement prerequisite limit is out of bounds");
        }
        if (mergeInterval < 0 || mergeInterval > MAX_MERGE_INTERVAL) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement merge interval is out of bounds");
        }
        if (maxNodesPerRank < MIN_MAX_NODES_PER_RANK
                || maxNodesPerRank > MAX_MAX_NODES_PER_RANK) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement rank width is out of bounds");
        }
        if (foundationCount < MIN_FOUNDATION_COUNT
                || foundationCount > MAX_FOUNDATION_COUNT) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement foundation count is out of bounds");
        }
        progressionBands = List.copyOf(progressionBands);
        if (progressionBands.size() > MAX_PROGRESSION_BANDS
                || progressionBands.stream().anyMatch(java.util.Objects::isNull)
                || new LinkedHashSet<>(progressionBands.stream()
                        .map(AutomaticWeaponProgressionBand::id).toList()).size()
                        != progressionBands.size()) {
            throw new IllegalArgumentException(
                    "Automatic weapon progression bands are invalid");
        }
        int previousMaximum = -1;
        for (AutomaticWeaponProgressionBand band : progressionBands) {
            if (band.maximumScore() <= previousMaximum) {
                throw new IllegalArgumentException(
                        "Automatic weapon progression bands must have increasing score bounds");
            }
            previousMaximum = band.maximumScore();
        }
        if (!progressionBands.isEmpty()
                && previousMaximum != ResearchTechTreeContract.SCORE_MAX) {
            throw new IllegalArgumentException(
                    "Automatic weapon progression bands must cover score 100");
        }
        if (layeringStrategy == LayeringStrategy.LEGACY_SCORE_BUCKETS
                && !progressionBands.isEmpty()) {
            throw new IllegalArgumentException(
                    "Legacy automatic placement cannot declare custom progression bands");
        }
    }

    public boolean usesDynamicLayers() {
        return layeringStrategy == LayeringStrategy.DYNAMIC_STAT_LAYERS;
    }

    /** Returns this policy with the tree-owned generated-layer capacity. */
    public AutomaticWeaponPlacementPolicy withMaxNodesPerRank(int capacity) {
        return capacity == maxNodesPerRank
                ? this
                : new AutomaticWeaponPlacementPolicy(
                        levelsPerTier,
                        reviewConfidenceThreshold,
                        reviewHandling,
                        maxGeneratedPrerequisites,
                        mergeInterval,
                        layeringStrategy,
                        capacity,
                        progressionBands,
                        foundationCount);
    }

    public java.util.Optional<AutomaticWeaponProgressionBand> bandForScore(int score) {
        if (score < 0 || score > ResearchTechTreeContract.SCORE_MAX) {
            throw new IllegalArgumentException("Automatic weapon score is out of bounds");
        }
        return progressionBands.stream().filter(band -> band.contains(score)).findFirst();
    }

    public enum LayeringStrategy {
        LEGACY_SCORE_BUCKETS,
        DYNAMIC_STAT_LAYERS
    }

    /** Controls whether warning-bearing but structurally valid proposals may be published. */
    public enum ReviewHandling {
        EXCLUDE(false, false),
        PLACE_INDEPENDENT(true, false),
        PLACE_CONNECTED(true, true);

        private final boolean assignsPlacement;
        private final boolean createsPrerequisite;

        ReviewHandling(boolean assignsPlacement, boolean createsPrerequisite) {
            this.assignsPlacement = assignsPlacement;
            this.createsPrerequisite = createsPrerequisite;
        }

        public boolean assignsPlacement() {
            return assignsPlacement;
        }

        public boolean createsPrerequisite() {
            return createsPrerequisite;
        }

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
