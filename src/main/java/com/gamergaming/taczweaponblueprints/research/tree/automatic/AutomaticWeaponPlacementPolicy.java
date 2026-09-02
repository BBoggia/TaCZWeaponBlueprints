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
        int foundationCount,
        PrerequisiteStrategy prerequisiteStrategy) {
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
    public static final String LEGACY_GENERATED_PARENT_COST_GUARD =
            "conservative_legacy_and_union_closure_v1";
    public static final String GROUPED_GENERATED_PARENT_COST_GUARD =
            AutomaticWeaponAlternativeRouteGuard.CONTRACT;
    public static final String HYBRID_GENERATED_PARENT_COST_GUARD =
            "hybrid_route_and_gateway_v1";
    /** Compatibility alias retained for integrations that only know legacy AND. */
    public static final String GENERATED_PARENT_COST_GUARD =
            LEGACY_GENERATED_PARENT_COST_GUARD;
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
                    DEFAULT_FOUNDATION_COUNT,
                    PrerequisiteStrategy.LEGACY_AND);

    /** Compatibility constructor for policies predating versioned prerequisite strategies. */
    public AutomaticWeaponPlacementPolicy(
            int levelsPerTier,
            int reviewConfidenceThreshold,
            ReviewHandling reviewHandling,
            int maxGeneratedPrerequisites,
            int mergeInterval,
            LayeringStrategy layeringStrategy,
            int maxNodesPerRank,
            List<AutomaticWeaponProgressionBand> progressionBands,
            int foundationCount) {
        this(
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                maxGeneratedPrerequisites,
                mergeInterval,
                layeringStrategy,
                maxNodesPerRank,
                progressionBands,
                foundationCount,
                PrerequisiteStrategy.LEGACY_AND);
    }

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
                DEFAULT_FOUNDATION_COUNT,
                PrerequisiteStrategy.LEGACY_AND);
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
                DEFAULT_FOUNDATION_COUNT,
                PrerequisiteStrategy.LEGACY_AND);
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
                DEFAULT_FOUNDATION_COUNT,
                PrerequisiteStrategy.LEGACY_AND);
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
                DEFAULT_FOUNDATION_COUNT,
                PrerequisiteStrategy.LEGACY_AND);
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
                || prerequisiteStrategy == null
                || progressionBands == null) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement review handling cannot be null");
        }
        if (maxGeneratedPrerequisites < 1
                || maxGeneratedPrerequisites > MAX_GENERATED_PREREQUISITES) {
            throw new IllegalArgumentException(
                    "Automatic weapon placement prerequisite limit is out of bounds");
        }
        int strategyMaximumPrerequisites = switch (prerequisiteStrategy) {
            case LEGACY_AND -> MAX_GENERATED_PREREQUISITES;
            case GROUPED_ROUTES_V1 -> 2;
            case HYBRID_ROUTES_V1 -> MAX_GENERATED_PREREQUISITES;
        };
        if (maxGeneratedPrerequisites > strategyMaximumPrerequisites) {
            throw new IllegalArgumentException(
                    "Grouped automatic routes support at most two alternatives");
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
        if (prerequisiteStrategy == PrerequisiteStrategy.HYBRID_ROUTES_V1
                && layeringStrategy != LayeringStrategy.DYNAMIC_STAT_LAYERS) {
            throw new IllegalArgumentException(
                    "Hybrid automatic routes require dynamic stat layers");
        }
    }

    public boolean usesDynamicLayers() {
        return layeringStrategy == LayeringStrategy.DYNAMIC_STAT_LAYERS;
    }

    /**
     * Describes whether {@code merge_interval} can affect this policy's selected
     * parent set. Grouped routes deliberately ignore the legacy scheduling knob:
     * their gradual two-route taper is owned by the branch maturity schedule.
     */
    public MergeIntervalBehavior mergeIntervalBehavior() {
        return mergeIntervalBehavior(
                layeringStrategy,
                prerequisiteStrategy,
                maxGeneratedPrerequisites,
                mergeInterval);
    }

    public boolean schedulesPeriodicMerge() {
        return mergeIntervalBehavior().active();
    }

    public static MergeIntervalBehavior mergeIntervalBehavior(
            LayeringStrategy layeringStrategy,
            PrerequisiteStrategy prerequisiteStrategy,
            int maxGeneratedPrerequisites,
            int mergeInterval) {
        if (layeringStrategy == null || prerequisiteStrategy == null
                || maxGeneratedPrerequisites < 1
                || maxGeneratedPrerequisites > MAX_GENERATED_PREREQUISITES
                || prerequisiteStrategy == PrerequisiteStrategy.GROUPED_ROUTES_V1
                        && maxGeneratedPrerequisites > 2
                || mergeInterval < 0 || mergeInterval > MAX_MERGE_INTERVAL) {
            throw new IllegalArgumentException(
                    "Automatic weapon merge-interval policy is invalid");
        }
        if (mergeInterval == 0) {
            return MergeIntervalBehavior.DISABLED;
        }
        if (prerequisiteStrategy == PrerequisiteStrategy.GROUPED_ROUTES_V1) {
            return MergeIntervalBehavior.IGNORED_GROUPED_ROUTES_V1;
        }
        if (prerequisiteStrategy == PrerequisiteStrategy.HYBRID_ROUTES_V1) {
            return maxGeneratedPrerequisites >= 2
                    ? MergeIntervalBehavior.HYBRID_MANDATORY_GATEWAY_SCHEDULE
                    : MergeIntervalBehavior.INERT_PREREQUISITE_CEILING;
        }
        if (layeringStrategy == LayeringStrategy.LEGACY_SCORE_BUCKETS) {
            return maxGeneratedPrerequisites >= 2
                    ? MergeIntervalBehavior.LEGACY_SECOND_PARENT_SCHEDULE
                    : MergeIntervalBehavior.INERT_PREREQUISITE_CEILING;
        }
        return maxGeneratedPrerequisites >= 3
                ? MergeIntervalBehavior.LEGACY_THIRD_PARENT_SCHEDULE
                : MergeIntervalBehavior.INERT_PREREQUISITE_CEILING;
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
                        foundationCount,
                        prerequisiteStrategy);
    }

    public java.util.Optional<AutomaticWeaponProgressionBand> bandForScore(int score) {
        if (score < 0 || score > ResearchTechTreeContract.SCORE_MAX) {
            throw new IllegalArgumentException("Automatic weapon score is out of bounds");
        }
        return progressionBands.stream().filter(band -> band.contains(score)).findFirst();
    }

    public String generatedParentCostGuard() {
        return switch (prerequisiteStrategy) {
            case LEGACY_AND -> LEGACY_GENERATED_PARENT_COST_GUARD;
            case GROUPED_ROUTES_V1 -> GROUPED_GENERATED_PARENT_COST_GUARD;
            case HYBRID_ROUTES_V1 -> HYBRID_GENERATED_PARENT_COST_GUARD;
        };
    }

    public enum LayeringStrategy {
        LEGACY_SCORE_BUCKETS,
        DYNAMIC_STAT_LAYERS
    }

    /** Versioned interpretation of automatically selected parent sets. */
    public enum PrerequisiteStrategy {
        LEGACY_AND("legacy_and"),
        GROUPED_ROUTES_V1("grouped_routes_v1"),
        HYBRID_ROUTES_V1("hybrid_routes_v1");

        private final String serializedName;

        PrerequisiteStrategy(String serializedName) {
            this.serializedName = serializedName;
        }

        public String serializedName() {
            return serializedName;
        }

    }

    /** Operator-facing classification for the legacy merge-interval setting. */
    public enum MergeIntervalBehavior {
        DISABLED("disabled", false),
        IGNORED_GROUPED_ROUTES_V1("ignored_grouped_routes_v1", false),
        HYBRID_MANDATORY_GATEWAY_SCHEDULE(
                "hybrid_mandatory_gateway_schedule", true),
        INERT_PREREQUISITE_CEILING("inert_prerequisite_ceiling", false),
        LEGACY_SECOND_PARENT_SCHEDULE("legacy_second_parent_schedule", true),
        LEGACY_THIRD_PARENT_SCHEDULE("legacy_third_parent_schedule", true);

        private final String serializedName;
        private final boolean active;

        MergeIntervalBehavior(String serializedName, boolean active) {
            this.serializedName = serializedName;
            this.active = active;
        }

        public String serializedName() {
            return serializedName;
        }

        public boolean active() {
            return active;
        }
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
