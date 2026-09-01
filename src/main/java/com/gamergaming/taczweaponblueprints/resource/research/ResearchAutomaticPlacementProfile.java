package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponScoringModel;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.LayeringStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.PrerequisiteStrategy;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.ReviewHandling;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponProgressionBand;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** Bounded, datapack-owned activation policy for automatic weapon placement. */
public record ResearchAutomaticPlacementProfile(
        int format,
        ResourceLocation tree,
        AutomaticPlacementMode mode,
        int levelsPerTier,
        int reviewConfidenceThreshold,
        ReviewHandling reviewHandling,
        int maxGeneratedPrerequisites,
        int mergeInterval,
        int maxNodesPerRank,
        List<AutomaticWeaponProgressionBand> progressionBands,
        int foundationCount,
        PrerequisiteStrategy prerequisiteStrategy,
        AutomaticWeaponScoringModel scoringModel) {
    public static final int LEGACY_FORMAT = 1;
    public static final int CURRENT_FORMAT = 4;
    public static final int GROUPED_ROUTES_INTRODUCED_FORMAT = 3;
    public static final int CAPABILITY_SCORING_INTRODUCED_FORMAT = 4;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            ResearchAutomaticPlacementProfile::validateFormat,
            ResearchAutomaticPlacementProfile::validateFormat);
    private static final Codec<AutomaticPlacementMode> MODE_CODEC = Codec.STRING.flatXmap(
            ResearchAutomaticPlacementProfile::decodeMode,
            value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    private static final Codec<Integer> LEVELS_CODEC = Codec.INT.flatXmap(
            ResearchAutomaticPlacementProfile::validateLevels,
            ResearchAutomaticPlacementProfile::validateLevels);
    private static final Codec<Integer> CONFIDENCE_CODEC = Codec.INT.flatXmap(
            ResearchAutomaticPlacementProfile::validateConfidence,
            ResearchAutomaticPlacementProfile::validateConfidence);
    private static final Codec<Integer> MAX_PREREQUISITES_CODEC = Codec.INT.flatXmap(
            ResearchAutomaticPlacementProfile::validateMaxPrerequisites,
            ResearchAutomaticPlacementProfile::validateMaxPrerequisites);
    private static final Codec<Integer> MERGE_INTERVAL_CODEC = Codec.INT.flatXmap(
            ResearchAutomaticPlacementProfile::validateMergeInterval,
            ResearchAutomaticPlacementProfile::validateMergeInterval);
    private static final Codec<Integer> MAX_NODES_PER_RANK_CODEC = Codec.INT.flatXmap(
            ResearchAutomaticPlacementProfile::validateMaxNodesPerRank,
            ResearchAutomaticPlacementProfile::validateMaxNodesPerRank);
    private static final Codec<Integer> FOUNDATION_COUNT_CODEC = Codec.INT.flatXmap(
            ResearchAutomaticPlacementProfile::validateFoundationCount,
            ResearchAutomaticPlacementProfile::validateFoundationCount);
    private static final Codec<Integer> BAND_MAXIMUM_SCORE_CODEC = Codec.INT.flatXmap(
            ResearchAutomaticPlacementProfile::validateBandMaximumScore,
            ResearchAutomaticPlacementProfile::validateBandMaximumScore);
    private static final Codec<String> BAND_TITLE_CODEC = Codec.STRING.flatXmap(
            ResearchAutomaticPlacementProfile::validateBandTitle,
            ResearchAutomaticPlacementProfile::validateBandTitle);
    private static final Codec<String> BAND_TRANSLATION_KEY_CODEC = Codec.STRING.flatXmap(
            ResearchAutomaticPlacementProfile::validateBandTranslationKey,
            ResearchAutomaticPlacementProfile::validateBandTranslationKey);
    private static final Codec<ReviewHandling> REVIEW_HANDLING_CODEC = Codec.STRING.flatXmap(
            ResearchAutomaticPlacementProfile::decodeReviewHandling,
            value -> DataResult.success(value.serializedName()));
    private static final Codec<PrerequisiteStrategy> PREREQUISITE_STRATEGY_CODEC =
            Codec.STRING.flatXmap(
                    ResearchAutomaticPlacementProfile::decodePrerequisiteStrategy,
                    value -> DataResult.success(value.serializedName()));
    private static final Codec<AutomaticWeaponScoringModel> SCORING_MODEL_CODEC =
            Codec.STRING.flatXmap(
                    ResearchAutomaticPlacementProfile::decodeScoringModel,
                    value -> DataResult.success(value.serializedName()));
    private static final Codec<AutomaticWeaponProgressionBand> RAW_BAND_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("id")
                            .forGetter(AutomaticWeaponProgressionBand::id),
                    BAND_MAXIMUM_SCORE_CODEC.fieldOf("maximum_score")
                            .forGetter(AutomaticWeaponProgressionBand::maximumScore),
                    BAND_TITLE_CODEC.fieldOf("title")
                            .forGetter(AutomaticWeaponProgressionBand::title),
                    new StrictOptionalFieldCodec<>(
                            "translation_key", BAND_TRANSLATION_KEY_CODEC)
                            .forGetter(AutomaticWeaponProgressionBand::translationKey))
                    .apply(instance, AutomaticWeaponProgressionBand::new));
    private static final Codec<AutomaticWeaponProgressionBand> BAND_CODEC =
            StrictRecordCodec.wrap(
                    "automatic weapon progression band",
                    RAW_BAND_CODEC,
                    "id",
                    "maximum_score",
                    "title",
                    "translation_key");
    private static final Codec<ResearchAutomaticPlacementProfile> RAW_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    FORMAT_CODEC.fieldOf("format")
                            .forGetter(ResearchAutomaticPlacementProfile::format),
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("tree")
                            .forGetter(ResearchAutomaticPlacementProfile::tree),
                    MODE_CODEC.fieldOf("mode")
                            .forGetter(ResearchAutomaticPlacementProfile::mode),
                    new StrictOptionalFieldCodec<>("levels_per_tier", LEVELS_CODEC)
                            .xmap(
                                    value -> value.orElse(
                                            ResearchTechTreeContract.DEFAULT_LEVELS_PER_TIER),
                                    value -> value
                                            == ResearchTechTreeContract.DEFAULT_LEVELS_PER_TIER
                                                    ? Optional.empty()
                                                    : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile::levelsPerTier),
                    new StrictOptionalFieldCodec<>(
                            "review_confidence_threshold", CONFIDENCE_CODEC)
                            .xmap(
                                    value -> value.orElse(
                                            AutomaticWeaponPlacementPolicy
                                                    .DEFAULT_REVIEW_CONFIDENCE_THRESHOLD),
                                    value -> value
                                            == AutomaticWeaponPlacementPolicy
                                                    .DEFAULT_REVIEW_CONFIDENCE_THRESHOLD
                                                            ? Optional.empty()
                                                            : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile
                                    ::reviewConfidenceThreshold),
                    new StrictOptionalFieldCodec<>("review_handling", REVIEW_HANDLING_CODEC)
                            .xmap(
                                    value -> value.orElse(ReviewHandling.EXCLUDE),
                                    value -> value == ReviewHandling.EXCLUDE
                                            ? Optional.empty()
                                            : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile::reviewHandling),
                    new StrictOptionalFieldCodec<>(
                            "max_prerequisites", MAX_PREREQUISITES_CODEC)
                            .xmap(
                                    value -> value.orElse(
                                            AutomaticWeaponPlacementPolicy
                                                    .DEFAULT_MAX_GENERATED_PREREQUISITES),
                                    value -> value
                                            == AutomaticWeaponPlacementPolicy
                                                    .DEFAULT_MAX_GENERATED_PREREQUISITES
                                                            ? Optional.empty()
                                                            : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile
                                    ::maxGeneratedPrerequisites),
                    new StrictOptionalFieldCodec<>("merge_interval", MERGE_INTERVAL_CODEC)
                            .xmap(
                                    value -> value.orElse(
                                            AutomaticWeaponPlacementPolicy.DEFAULT_MERGE_INTERVAL),
                                    value -> value
                                            == AutomaticWeaponPlacementPolicy.DEFAULT_MERGE_INTERVAL
                                                    ? Optional.empty()
                                                    : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile::mergeInterval),
                    new StrictOptionalFieldCodec<>(
                            "max_nodes_per_rank", MAX_NODES_PER_RANK_CODEC)
                            .xmap(
                                    value -> value.orElse(
                                            AutomaticWeaponPlacementPolicy
                                                    .DEFAULT_MAX_NODES_PER_RANK),
                                    value -> value
                                            == AutomaticWeaponPlacementPolicy
                                                    .DEFAULT_MAX_NODES_PER_RANK
                                                            ? Optional.empty()
                                                            : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile::maxNodesPerRank),
                    new StrictOptionalFieldCodec<>("bands", BAND_CODEC.listOf())
                            .xmap(
                                    value -> value.orElse(List.of()),
                                    value -> value.isEmpty()
                                            ? Optional.empty()
                                            : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile::progressionBands),
                    new StrictOptionalFieldCodec<>("foundation_count", FOUNDATION_COUNT_CODEC)
                            .xmap(
                                    value -> value.orElse(
                                            AutomaticWeaponPlacementPolicy
                                                    .DEFAULT_FOUNDATION_COUNT),
                                    value -> value
                                            == AutomaticWeaponPlacementPolicy
                                                    .DEFAULT_FOUNDATION_COUNT
                                                            ? Optional.empty()
                                                            : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile::foundationCount),
                    new StrictOptionalFieldCodec<>(
                            "prerequisite_strategy", PREREQUISITE_STRATEGY_CODEC)
                            .xmap(
                                    value -> value.orElse(PrerequisiteStrategy.LEGACY_AND),
                                    value -> value == PrerequisiteStrategy.LEGACY_AND
                                            ? Optional.empty()
                                            : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile
                                    ::prerequisiteStrategy),
                    new StrictOptionalFieldCodec<>("scoring_model", SCORING_MODEL_CODEC)
                            .xmap(
                                    value -> value.orElse(
                                            AutomaticWeaponScoringModel.MECHANICAL_V2),
                                    value -> value == AutomaticWeaponScoringModel.MECHANICAL_V2
                                            ? Optional.empty()
                                            : Optional.of(value))
                            .forGetter(ResearchAutomaticPlacementProfile::scoringModel))
                    .apply(instance, ResearchAutomaticPlacementProfile::new));
    public static final Codec<ResearchAutomaticPlacementProfile> CODEC = StrictRecordCodec.wrap(
            "Research Tech Tree automatic-placement profile",
            RAW_CODEC,
            "format",
            "tree",
            "mode",
            "levels_per_tier",
            "review_confidence_threshold",
            "review_handling",
            "max_prerequisites",
            "merge_interval",
            "max_nodes_per_rank",
            "bands",
            "foundation_count",
            "prerequisite_strategy",
            "scoring_model");

    /** Source-compatible constructor for profiles written before scoring-model selection. */
    public ResearchAutomaticPlacementProfile(
            int format,
            ResourceLocation tree,
            AutomaticPlacementMode mode,
            int levelsPerTier,
            int reviewConfidenceThreshold,
            ReviewHandling reviewHandling,
            int maxGeneratedPrerequisites,
            int mergeInterval,
            int maxNodesPerRank,
            List<AutomaticWeaponProgressionBand> progressionBands,
            int foundationCount,
            PrerequisiteStrategy prerequisiteStrategy) {
        this(
                format, tree, mode, levelsPerTier, reviewConfidenceThreshold,
                reviewHandling, maxGeneratedPrerequisites, mergeInterval,
                maxNodesPerRank, progressionBands, foundationCount,
                prerequisiteStrategy, AutomaticWeaponScoringModel.MECHANICAL_V2);
    }

    /** Compatibility constructor for profiles predating prerequisite strategies. */
    public ResearchAutomaticPlacementProfile(
            int format,
            ResourceLocation tree,
            AutomaticPlacementMode mode,
            int levelsPerTier,
            int reviewConfidenceThreshold,
            ReviewHandling reviewHandling,
            int maxGeneratedPrerequisites,
            int mergeInterval,
            int maxNodesPerRank,
            List<AutomaticWeaponProgressionBand> progressionBands,
            int foundationCount) {
        this(
                format,
                tree,
                mode,
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                maxGeneratedPrerequisites,
                mergeInterval,
                maxNodesPerRank,
                progressionBands,
                foundationCount,
                PrerequisiteStrategy.LEGACY_AND);
    }

    /** Compatibility constructor for profiles predating configurable foundations. */
    public ResearchAutomaticPlacementProfile(
            int format,
            ResourceLocation tree,
            AutomaticPlacementMode mode,
            int levelsPerTier,
            int reviewConfidenceThreshold,
            ReviewHandling reviewHandling,
            int maxGeneratedPrerequisites,
            int mergeInterval,
            int maxNodesPerRank,
            List<AutomaticWeaponProgressionBand> progressionBands) {
        this(
                format,
                tree,
                mode,
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                maxGeneratedPrerequisites,
                mergeInterval,
                maxNodesPerRank,
                progressionBands,
                AutomaticWeaponPlacementPolicy.DEFAULT_FOUNDATION_COUNT,
                PrerequisiteStrategy.LEGACY_AND);
    }

    /** Existing programmatic profiles retain their format-specific defaults. */
    public ResearchAutomaticPlacementProfile(
            int format,
            ResourceLocation tree,
            AutomaticPlacementMode mode,
            int levelsPerTier,
            int reviewConfidenceThreshold) {
        this(
                format,
                tree,
                mode,
                levelsPerTier,
                reviewConfidenceThreshold,
                ReviewHandling.EXCLUDE,
                AutomaticWeaponPlacementPolicy.DEFAULT_MAX_GENERATED_PREREQUISITES,
                AutomaticWeaponPlacementPolicy.DEFAULT_MERGE_INTERVAL,
                AutomaticWeaponPlacementPolicy.DEFAULT_MAX_NODES_PER_RANK,
                List.of(),
                AutomaticWeaponPlacementPolicy.DEFAULT_FOUNDATION_COUNT,
                PrerequisiteStrategy.LEGACY_AND);
    }

    /** Existing six-argument profiles inherit the bounded topology defaults. */
    public ResearchAutomaticPlacementProfile(
            int format,
            ResourceLocation tree,
            AutomaticPlacementMode mode,
            int levelsPerTier,
            int reviewConfidenceThreshold,
            ReviewHandling reviewHandling) {
        this(
                format,
                tree,
                mode,
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                AutomaticWeaponPlacementPolicy.DEFAULT_MAX_GENERATED_PREREQUISITES,
                AutomaticWeaponPlacementPolicy.DEFAULT_MERGE_INTERVAL,
                AutomaticWeaponPlacementPolicy.DEFAULT_MAX_NODES_PER_RANK,
                List.of(),
                AutomaticWeaponPlacementPolicy.DEFAULT_FOUNDATION_COUNT,
                PrerequisiteStrategy.LEGACY_AND);
    }

    /** Existing eight-argument profiles inherit the Phase 5 width and band defaults. */
    public ResearchAutomaticPlacementProfile(
            int format,
            ResourceLocation tree,
            AutomaticPlacementMode mode,
            int levelsPerTier,
            int reviewConfidenceThreshold,
            ReviewHandling reviewHandling,
            int maxGeneratedPrerequisites,
            int mergeInterval) {
        this(
                format,
                tree,
                mode,
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                maxGeneratedPrerequisites,
                mergeInterval,
                AutomaticWeaponPlacementPolicy.DEFAULT_MAX_NODES_PER_RANK,
                List.of(),
                AutomaticWeaponPlacementPolicy.DEFAULT_FOUNDATION_COUNT,
                PrerequisiteStrategy.LEGACY_AND);
    }

    public ResearchAutomaticPlacementProfile {
        progressionBands = progressionBands == null
                ? List.of()
                : List.copyOf(progressionBands);
        if (format < LEGACY_FORMAT || format > CURRENT_FORMAT
                || tree == null || mode == null || reviewHandling == null
                || prerequisiteStrategy == null || scoringModel == null) {
            throw new IllegalArgumentException(
                    "Automatic-placement profile identity is invalid");
        }
        if (format < GROUPED_ROUTES_INTRODUCED_FORMAT
                && prerequisiteStrategy != PrerequisiteStrategy.LEGACY_AND) {
            throw new IllegalArgumentException(
                    "Grouped prerequisite strategies require automatic-placement format "
                            + GROUPED_ROUTES_INTRODUCED_FORMAT);
        }
        if (format < CAPABILITY_SCORING_INTRODUCED_FORMAT
                && scoringModel != AutomaticWeaponScoringModel.MECHANICAL_V2) {
            throw new IllegalArgumentException(
                    "Capability scoring requires automatic-placement format "
                            + CAPABILITY_SCORING_INTRODUCED_FORMAT);
        }
        boolean requiresConnectedMode = switch (prerequisiteStrategy) {
            case LEGACY_AND -> false;
            case GROUPED_ROUTES_V1, HYBRID_ROUTES_V1 -> true;
        };
        if (requiresConnectedMode && !mode.createsPrerequisite()) {
            throw new IllegalArgumentException(
                    "Grouped prerequisite strategies require connected placement mode");
        }
        new AutomaticWeaponPlacementPolicy(
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                maxGeneratedPrerequisites,
                mergeInterval,
                format == LEGACY_FORMAT
                        ? LayeringStrategy.LEGACY_SCORE_BUCKETS
                        : LayeringStrategy.DYNAMIC_STAT_LAYERS,
                maxNodesPerRank,
                progressionBands,
                foundationCount,
                prerequisiteStrategy);
        if (format == LEGACY_FORMAT
                && (maxNodesPerRank
                        != AutomaticWeaponPlacementPolicy.DEFAULT_MAX_NODES_PER_RANK
                        || !progressionBands.isEmpty()
                        || foundationCount
                                != AutomaticWeaponPlacementPolicy.DEFAULT_FOUNDATION_COUNT)) {
            throw new IllegalArgumentException(
                    "Format-1 automatic placement cannot declare Phase 5 layering fields");
        }
    }

    public AutomaticWeaponPlacementPolicy placementPolicy() {
        return policy();
    }

    void validateForSnapshot() {
        policy();
    }

    private AutomaticWeaponPlacementPolicy policy() {
        return new AutomaticWeaponPlacementPolicy(
                levelsPerTier,
                reviewConfidenceThreshold,
                reviewHandling,
                maxGeneratedPrerequisites,
                mergeInterval,
                format == LEGACY_FORMAT
                        ? LayeringStrategy.LEGACY_SCORE_BUCKETS
                        : LayeringStrategy.DYNAMIC_STAT_LAYERS,
                maxNodesPerRank,
                progressionBands,
                foundationCount,
                prerequisiteStrategy);
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value >= LEGACY_FORMAT && value <= CURRENT_FORMAT
                ? DataResult.success(value)
                : DataResult.error(() ->
                        "unsupported automatic-placement profile format " + value);
    }

    private static DataResult<AutomaticPlacementMode> decodeMode(String value) {
        if (value == null) {
            return DataResult.error(() -> "automatic-placement mode cannot be null");
        }
        try {
            return DataResult.success(
                    AutomaticPlacementMode.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "unknown automatic-placement mode " + value);
        }
    }

    private static DataResult<Integer> validateLevels(int value) {
        return value >= ResearchTechTreeContract.MIN_LEVELS_PER_TIER
                && value <= ResearchTechTreeContract.MAX_LEVELS_PER_TIER
                        ? DataResult.success(value)
                        : DataResult.error(() -> "levels_per_tier must be between "
                                + ResearchTechTreeContract.MIN_LEVELS_PER_TIER + " and "
                                + ResearchTechTreeContract.MAX_LEVELS_PER_TIER);
    }

    private static DataResult<Integer> validateConfidence(int value) {
        return value >= 0 && value <= ResearchTechTreeContract.SCORE_MAX
                ? DataResult.success(value)
                : DataResult.error(() ->
                        "review_confidence_threshold must be between zero and "
                                + ResearchTechTreeContract.SCORE_MAX);
    }

    private static DataResult<Integer> validateMaxPrerequisites(int value) {
        return value >= 1
                && value <= AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES
                        ? DataResult.success(value)
                        : DataResult.error(() -> "max_prerequisites must be between one and "
                                + AutomaticWeaponPlacementPolicy.MAX_GENERATED_PREREQUISITES);
    }

    private static DataResult<Integer> validateMergeInterval(int value) {
        return value >= 0 && value <= AutomaticWeaponPlacementPolicy.MAX_MERGE_INTERVAL
                ? DataResult.success(value)
                : DataResult.error(() -> "merge_interval must be between zero and "
                        + AutomaticWeaponPlacementPolicy.MAX_MERGE_INTERVAL);
    }

    private static DataResult<Integer> validateMaxNodesPerRank(int value) {
        return value >= AutomaticWeaponPlacementPolicy.MIN_MAX_NODES_PER_RANK
                && value <= AutomaticWeaponPlacementPolicy.MAX_MAX_NODES_PER_RANK
                        ? DataResult.success(value)
                        : DataResult.error(() -> "max_nodes_per_rank must be between "
                                + AutomaticWeaponPlacementPolicy.MIN_MAX_NODES_PER_RANK
                                + " and "
                                + AutomaticWeaponPlacementPolicy.MAX_MAX_NODES_PER_RANK);
    }

    private static DataResult<Integer> validateFoundationCount(int value) {
        return value >= AutomaticWeaponPlacementPolicy.MIN_FOUNDATION_COUNT
                && value <= AutomaticWeaponPlacementPolicy.MAX_FOUNDATION_COUNT
                        ? DataResult.success(value)
                        : DataResult.error(() -> "foundation_count must be between "
                                + AutomaticWeaponPlacementPolicy.MIN_FOUNDATION_COUNT
                                + " and "
                                + AutomaticWeaponPlacementPolicy.MAX_FOUNDATION_COUNT);
    }

    private static DataResult<Integer> validateBandMaximumScore(int value) {
        return value >= 0 && value <= ResearchTechTreeContract.SCORE_MAX
                ? DataResult.success(value)
                : DataResult.error(() ->
                        "automatic progression band maximum_score must be between zero and "
                                + ResearchTechTreeContract.SCORE_MAX);
    }

    private static DataResult<String> validateBandTitle(String value) {
        try {
            validationBand(value, Optional.empty());
            return DataResult.success(value);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> "automatic progression band title is invalid");
        }
    }

    private static DataResult<String> validateBandTranslationKey(String value) {
        try {
            validationBand("Validation", Optional.ofNullable(value));
            return DataResult.success(value);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() ->
                    "automatic progression band translation_key is invalid");
        }
    }

    private static AutomaticWeaponProgressionBand validationBand(
            String title,
            Optional<String> translationKey) {
        return new AutomaticWeaponProgressionBand(
                new ResourceLocation("taczweaponblueprints", "validation"),
                ResearchTechTreeContract.SCORE_MAX,
                title,
                translationKey);
    }

    private static DataResult<ReviewHandling> decodeReviewHandling(String value) {
        if (value == null) {
            return DataResult.error(() ->
                    "automatic-placement review handling cannot be null");
        }
        try {
            return DataResult.success(
                    ReviewHandling.valueOf(value.toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() ->
                    "unknown automatic-placement review handling " + value);
        }
    }

    private static DataResult<PrerequisiteStrategy> decodePrerequisiteStrategy(
            String value) {
        if (value == null) {
            return DataResult.error(() ->
                    "automatic-placement prerequisite strategy cannot be null");
        }
        for (PrerequisiteStrategy strategy : PrerequisiteStrategy.values()) {
            if (strategy.serializedName().equals(value)) {
                return DataResult.success(strategy);
            }
        }
        return DataResult.error(() ->
                "unknown automatic-placement prerequisite strategy " + value);
    }

    private static DataResult<AutomaticWeaponScoringModel> decodeScoringModel(
            String value) {
        try {
            return DataResult.success(AutomaticWeaponScoringModel.decode(value));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }
}
