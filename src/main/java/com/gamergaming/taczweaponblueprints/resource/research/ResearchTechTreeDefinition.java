package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/**
 * Server-authored shell for one Research Tech Tree.
 *
 * <p>This definition owns labels, domains, tiers, lanes, and bounded layout
 * policy. Its weapon placement mode also chooses whether weapon placement and
 * prerequisite topology come from authored resources or the automatic
 * generator. Research costs and non-weapon prerequisites continue to come from
 * research rules.
 */
public record ResearchTechTreeDefinition(
        int format,
        String title,
        Optional<String> translationKey,
        Optional<ResourceLocation> icon,
        WeaponPlacementMode weaponPlacementMode,
        LayoutDefinition layout,
        BandPolicyDefinition bandPolicy,
        List<TierDefinition> tiers,
        List<DomainDefinition> domains) {
    public static final int LEGACY_FORMAT = 1;
    public static final int CURRENT_FORMAT = 2;
    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_TRANSLATION_KEY_LENGTH = 256;
    public static final int MAX_LANES_PER_DOMAIN = 64;
    public static final int MAX_TOTAL_LANES = 192;
    public static final int MAX_PRESENTATION_BANDS = 32;
    public static final int MAX_ORDER = 1_000_000;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            value -> value >= LEGACY_FORMAT && value <= CURRENT_FORMAT
                    ? DataResult.success(value)
                    : DataResult.error(() -> "unsupported Research Tech Tree format " + value),
            value -> value >= LEGACY_FORMAT && value <= CURRENT_FORMAT
                    ? DataResult.success(value)
                    : DataResult.error(() -> "unsupported Research Tech Tree format " + value));
    private static final Codec<String> TITLE_CODEC = Codec.STRING.flatXmap(
            ResearchTechTreeDefinition::validateTitleResult,
            ResearchTechTreeDefinition::validateTitleResult);
    private static final Codec<String> TRANSLATION_KEY_CODEC = Codec.STRING.flatXmap(
            ResearchTechTreeDefinition::validateTranslationKeyResult,
            ResearchTechTreeDefinition::validateTranslationKeyResult);
    private static final Codec<Integer> ORDER_CODEC = Codec.INT.flatXmap(
            ResearchTechTreeDefinition::validateOrderResult,
            ResearchTechTreeDefinition::validateOrderResult);
    private static final Codec<Domain> DOMAIN_CODEC = enumCodec(Domain.class, "Research Tech Tree domain");
    private static final Codec<Tier> TIER_CODEC = enumCodec(Tier.class, "Research Tech Tree tier");
    private static final Codec<BandMode> BAND_MODE_CODEC = Codec.STRING.flatXmap(
            ResearchTechTreeDefinition::decodeBandMode,
            value -> value == BandMode.LEGACY
                    ? DataResult.error(() -> "legacy is not an authorable band mode")
                    : DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    private static final Codec<BandBasis> BAND_BASIS_CODEC =
            enumCodec(BandBasis.class, "Research Tech Tree band basis");
    private static final Codec<WidthMode> WIDTH_MODE_CODEC =
            enumCodec(WidthMode.class, "Research Tech Tree width mode");
    private static final Codec<WeaponPlacementMode> WEAPON_PLACEMENT_MODE_CODEC =
            enumCodec(WeaponPlacementMode.class, "Research Tech Tree weapon placement mode");
    private static final Codec<Integer> BAND_COLOR_CODEC = Codec.intRange(0, 0xFFFFFF);
    private static final Codec<Integer> BAND_MAXIMUM_CODEC = Codec.intRange(
            0,
            com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract
                    .MAX_PROGRESSION_RANK);

    private static final Codec<LayoutDefinition> RAW_LAYOUT_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    Codec.intRange(
                            LayoutDefinition.MIN_NODES_PER_LAYER,
                            LayoutDefinition.MAX_NODES_PER_LAYER)
                            .fieldOf("max_nodes_per_layer")
                            .forGetter(LayoutDefinition::maxNodesPerLayer),
                    new StrictOptionalFieldCodec<>("width_mode", WIDTH_MODE_CODEC)
                            .forGetter(value -> value.widthMode() == WidthMode.FIXED
                                    ? Optional.empty()
                                    : Optional.of(value.widthMode())),
                    new StrictOptionalFieldCodec<>(
                            "min_nodes_per_layer",
                            Codec.intRange(
                                    LayoutDefinition.MIN_NODES_PER_LAYER,
                                    LayoutDefinition.MAX_NODES_PER_LAYER))
                            .forGetter(value -> value.widthMode() == WidthMode.FIXED
                                    ? Optional.empty()
                                    : Optional.of(value.minNodesPerLayer())))
                    .apply(instance, LayoutDefinition::decode));
    public static final Codec<LayoutDefinition> LAYOUT_DEFINITION_CODEC =
            StrictRecordCodec.wrap(
                    "Research Tech Tree layout",
                    RAW_LAYOUT_CODEC,
                    "max_nodes_per_layer",
                    "width_mode",
                    "min_nodes_per_layer");

    private static final Codec<BandDefinition> RAW_BAND_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("id")
                            .forGetter(BandDefinition::id),
                    TITLE_CODEC.fieldOf("title").forGetter(BandDefinition::title),
                    new StrictOptionalFieldCodec<>("translation_key", TRANSLATION_KEY_CODEC)
                            .forGetter(BandDefinition::translationKey),
                    new StrictOptionalFieldCodec<>("color", BAND_COLOR_CODEC)
                            .forGetter(BandDefinition::color),
                    new StrictOptionalFieldCodec<>("icon", BlueprintResearchCodecs.RESOURCE_LOCATION)
                            .forGetter(BandDefinition::icon),
                    new StrictOptionalFieldCodec<>("maximum", BAND_MAXIMUM_CODEC)
                            .forGetter(BandDefinition::maximum))
                    .apply(instance, BandDefinition::new));
    public static final Codec<BandDefinition> BAND_DEFINITION_CODEC = StrictRecordCodec.wrap(
            "Research Tech Tree presentation band",
            RAW_BAND_CODEC,
            "id",
            "title",
            "translation_key",
            "color",
            "icon",
            "maximum");

    private static final Codec<BandPolicyDefinition> RAW_BAND_POLICY_CODEC =
            RecordCodecBuilder.create(instance -> instance.group(
                    BAND_MODE_CODEC.fieldOf("mode").forGetter(BandPolicyDefinition::mode),
                    new StrictOptionalFieldCodec<>(
                            "ranks_per_band",
                            Codec.intRange(
                                    BandPolicyDefinition.MIN_RANKS_PER_BAND,
                                    BandPolicyDefinition.MAX_RANKS_PER_BAND))
                            .xmap(
                                    value -> value.orElse(
                                            BandPolicyDefinition.DEFAULT_RANKS_PER_BAND),
                                    value -> value == BandPolicyDefinition.DEFAULT_RANKS_PER_BAND
                                            ? Optional.empty()
                                            : Optional.of(value))
                            .forGetter(BandPolicyDefinition::ranksPerBand),
                    new StrictOptionalFieldCodec<>("basis", BAND_BASIS_CODEC)
                            .xmap(
                                    value -> value.orElse(BandBasis.RANK),
                                    value -> value == BandBasis.RANK
                                            ? Optional.empty()
                                            : Optional.of(value))
                            .forGetter(BandPolicyDefinition::basis),
                    new StrictOptionalFieldCodec<>("definitions", BAND_DEFINITION_CODEC.listOf())
                            .xmap(
                                    value -> value.orElse(List.of()),
                                    value -> value.isEmpty()
                                            ? Optional.empty()
                                            : Optional.of(value))
                            .forGetter(BandPolicyDefinition::definitions))
                    .apply(instance, BandPolicyDefinition::new));
    public static final Codec<BandPolicyDefinition> BAND_POLICY_DEFINITION_CODEC =
            StrictRecordCodec.wrap(
                    "Research Tech Tree band policy",
                    RAW_BAND_POLICY_CODEC,
                    "mode",
                    "ranks_per_band",
                    "basis",
                    "definitions");

    private static final Codec<TierDefinition> RAW_TIER_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    TIER_CODEC.fieldOf("id").forGetter(TierDefinition::tier),
                    TITLE_CODEC.fieldOf("title").forGetter(TierDefinition::title),
                    new StrictOptionalFieldCodec<>("translation_key", TRANSLATION_KEY_CODEC)
                            .forGetter(TierDefinition::translationKey))
                    .apply(instance, TierDefinition::new));
    public static final Codec<TierDefinition> TIER_DEFINITION_CODEC = StrictRecordCodec.wrap(
            "Research Tech Tree tier",
            RAW_TIER_CODEC.flatXmap(
                    ResearchTechTreeDefinition::validateTierDefinition,
                    ResearchTechTreeDefinition::validateTierDefinition),
            "id",
            "title",
            "translation_key");

    private static final Codec<LaneDefinition> RAW_LANE_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("id")
                            .forGetter(LaneDefinition::id),
                    TITLE_CODEC.fieldOf("title").forGetter(LaneDefinition::title),
                    new StrictOptionalFieldCodec<>("translation_key", TRANSLATION_KEY_CODEC)
                            .forGetter(LaneDefinition::translationKey),
                    new StrictOptionalFieldCodec<>("icon", BlueprintResearchCodecs.RESOURCE_LOCATION)
                            .forGetter(LaneDefinition::icon),
                    ORDER_CODEC.fieldOf("order").forGetter(LaneDefinition::order))
                    .apply(instance, LaneDefinition::new));
    public static final Codec<LaneDefinition> LANE_DEFINITION_CODEC = StrictRecordCodec.wrap(
            "Research Tech Tree lane",
            RAW_LANE_CODEC.flatXmap(
                    ResearchTechTreeDefinition::validateLaneDefinition,
                    ResearchTechTreeDefinition::validateLaneDefinition),
            "id",
            "title",
            "translation_key",
            "icon",
            "order");

    private static final Codec<DomainDefinition> RAW_DOMAIN_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    DOMAIN_CODEC.fieldOf("id").forGetter(DomainDefinition::domain),
                    TITLE_CODEC.fieldOf("title").forGetter(DomainDefinition::title),
                    new StrictOptionalFieldCodec<>("translation_key", TRANSLATION_KEY_CODEC)
                            .forGetter(DomainDefinition::translationKey),
                    new StrictOptionalFieldCodec<>("icon", BlueprintResearchCodecs.RESOURCE_LOCATION)
                            .forGetter(DomainDefinition::icon),
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("fallback_lane")
                            .forGetter(DomainDefinition::fallbackLane),
                    TIER_CODEC.fieldOf("fallback_tier").forGetter(DomainDefinition::fallbackTier),
                    LANE_DEFINITION_CODEC.listOf().fieldOf("lanes")
                            .forGetter(DomainDefinition::lanes))
                    .apply(instance, DomainDefinition::new));
    public static final Codec<DomainDefinition> DOMAIN_DEFINITION_CODEC = StrictRecordCodec.wrap(
            "Research Tech Tree domain",
            RAW_DOMAIN_CODEC.flatXmap(
                    ResearchTechTreeDefinition::validateDomainDefinition,
                    ResearchTechTreeDefinition::validateDomainDefinition),
            "id",
            "title",
            "translation_key",
            "icon",
            "fallback_lane",
            "fallback_tier",
            "lanes");

    private static final Codec<ResearchTechTreeDefinition> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(ResearchTechTreeDefinition::format),
                    TITLE_CODEC.fieldOf("title").forGetter(ResearchTechTreeDefinition::title),
                    new StrictOptionalFieldCodec<>("translation_key", TRANSLATION_KEY_CODEC)
                            .forGetter(ResearchTechTreeDefinition::translationKey),
                    new StrictOptionalFieldCodec<>("icon", BlueprintResearchCodecs.RESOURCE_LOCATION)
                            .forGetter(ResearchTechTreeDefinition::icon),
                    new StrictOptionalFieldCodec<>(
                            "weapon_placement_mode", WEAPON_PLACEMENT_MODE_CODEC)
                            .xmap(
                                    value -> value.orElse(WeaponPlacementMode.AUTHORED_ONLY),
                                    value -> value == WeaponPlacementMode.AUTHORED_ONLY
                                            ? Optional.empty()
                                            : Optional.of(value))
                            .forGetter(ResearchTechTreeDefinition::weaponPlacementMode),
                    new StrictOptionalFieldCodec<>("layout", LAYOUT_DEFINITION_CODEC)
                            .forGetter(definition -> definition.format() == CURRENT_FORMAT
                                    ? Optional.of(definition.layout())
                                    : Optional.empty()),
                    new StrictOptionalFieldCodec<>("bands", BAND_POLICY_DEFINITION_CODEC)
                            .forGetter(definition -> definition.format() == CURRENT_FORMAT
                                            && definition.bandPolicy().mode() != BandMode.NONE
                                    ? Optional.of(definition.bandPolicy())
                                    : Optional.empty()),
                    new StrictOptionalFieldCodec<>("tiers", TIER_DEFINITION_CODEC.listOf())
                            .forGetter(definition -> definition.tiers().isEmpty()
                                    ? Optional.empty()
                                    : Optional.of(definition.tiers())),
                    DOMAIN_DEFINITION_CODEC.listOf().fieldOf("domains")
                            .forGetter(ResearchTechTreeDefinition::domains))
                    .apply(instance, ResearchTechTreeDefinition::decode));
    public static final Codec<ResearchTechTreeDefinition> CODEC = StrictRecordCodec.wrap(
            "Research Tech Tree",
            RAW_CODEC.flatXmap(
                    ResearchTechTreeDefinition::validateDefinition,
                    ResearchTechTreeDefinition::validateDefinition),
            "format",
            "title",
            "translation_key",
            "icon",
            "weapon_placement_mode",
            "layout",
            "bands",
            "tiers",
            "domains");

    /** Compatibility constructor for version-1 definitions and existing fixtures. */
    public ResearchTechTreeDefinition(
            int format,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> icon,
            List<TierDefinition> tiers,
            List<DomainDefinition> domains) {
        this(
                format,
                title,
                translationKey,
                icon,
                WeaponPlacementMode.AUTHORED_ONLY,
                LayoutDefinition.DEFAULT,
                format == LEGACY_FORMAT
                        ? BandPolicyDefinition.LEGACY
                        : BandPolicyDefinition.NONE,
                tiers,
                domains);
    }

    /** Compatibility constructor for definitions predating tree-owned band policy. */
    public ResearchTechTreeDefinition(
            int format,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> icon,
            LayoutDefinition layout,
            List<TierDefinition> tiers,
            List<DomainDefinition> domains) {
        this(
                format,
                title,
                translationKey,
                icon,
                WeaponPlacementMode.AUTHORED_ONLY,
                layout,
                format == LEGACY_FORMAT
                        ? BandPolicyDefinition.LEGACY
                        : BandPolicyDefinition.NONE,
                tiers,
                domains);
    }

    /** Compatibility constructor for definitions predating exclusive weapon authority. */
    public ResearchTechTreeDefinition(
            int format,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> icon,
            LayoutDefinition layout,
            BandPolicyDefinition bandPolicy,
            List<TierDefinition> tiers,
            List<DomainDefinition> domains) {
        this(
                format,
                title,
                translationKey,
                icon,
                WeaponPlacementMode.AUTHORED_ONLY,
                layout,
                bandPolicy,
                tiers,
                domains);
    }

    public ResearchTechTreeDefinition {
        translationKey = translationKey == null ? Optional.empty() : translationKey;
        icon = icon == null ? Optional.empty() : icon;
        weaponPlacementMode = weaponPlacementMode == null
                ? WeaponPlacementMode.AUTHORED_ONLY
                : weaponPlacementMode;
        layout = layout == null ? LayoutDefinition.DEFAULT : layout;
        bandPolicy = bandPolicy == null
                ? (format == LEGACY_FORMAT
                        ? BandPolicyDefinition.LEGACY
                        : BandPolicyDefinition.NONE)
                : bandPolicy;
        tiers = tiers == null ? List.of() : List.copyOf(tiers);
        domains = domains == null ? List.of() : List.copyOf(domains);
        validateProgrammatic(
                format, title, translationKey, icon, weaponPlacementMode,
                layout, bandPolicy, tiers, domains);

        tiers = tiers.stream()
                .sorted(Comparator.comparingInt(value -> value.tier().ordinal()))
                .toList();
        domains = domains.stream()
                .sorted(Comparator.comparingInt(value -> value.domain().ordinal()))
                .toList();
    }

    public Optional<DomainDefinition> domain(Domain domain) {
        return domains.stream().filter(value -> value.domain() == domain).findFirst();
    }

    public Optional<DomainDefinition> domainForLane(ResourceLocation laneId) {
        if (laneId == null) {
            return Optional.empty();
        }
        return domains.stream()
                .filter(value -> value.lanes().stream().anyMatch(lane -> lane.id().equals(laneId)))
                .findFirst();
    }

    public boolean containsTier(Tier tier) {
        return tier != null && (format == CURRENT_FORMAT
                || tiers.stream().anyMatch(value -> value.tier() == tier));
    }

    public boolean usesAutomaticWeaponPlacement() {
        return weaponPlacementMode == WeaponPlacementMode.AUTOMATIC;
    }

    void validateForSnapshot() {
        validateProgrammatic(
                format, title, translationKey, icon, weaponPlacementMode,
                layout, bandPolicy, tiers, domains);
    }

    /** Exclusive authority for the weapon population of one Tech Tree. */
    public enum WeaponPlacementMode {
        AUTHORED_ONLY,
        AUTOMATIC
    }

    /** Width policy shared by automatic rank generation and later client layout. */
    public record LayoutDefinition(
            WidthMode widthMode,
            int minNodesPerLayer,
            int maxNodesPerLayer) {
        public static final int DEFAULT_NODES_PER_LAYER = 9;
        public static final int MIN_NODES_PER_LAYER = 8;
        /**
         * Opt-in authoring ceiling. The bundled tree deliberately remains capped
         * at 20; larger catalogs may raise their own ceiling without changing
         * existing datapack geometry.
         */
        public static final int MAX_NODES_PER_LAYER = 28;
        public static final LayoutDefinition DEFAULT =
                new LayoutDefinition(DEFAULT_NODES_PER_LAYER);

        /** Existing one-value definitions remain exact fixed-width policies. */
        public LayoutDefinition(int nodesPerLayer) {
            this(WidthMode.FIXED, nodesPerLayer, nodesPerLayer);
        }

        public LayoutDefinition {
            if (widthMode == null
                    || minNodesPerLayer < MIN_NODES_PER_LAYER
                    || minNodesPerLayer > maxNodesPerLayer
                    || maxNodesPerLayer > MAX_NODES_PER_LAYER) {
                throw new IllegalArgumentException(
                        "Research Tech Tree layer width range must remain between "
                                + MIN_NODES_PER_LAYER + " and " + MAX_NODES_PER_LAYER);
            }
            if (widthMode == WidthMode.FIXED
                    && minNodesPerLayer != maxNodesPerLayer) {
                throw new IllegalArgumentException(
                        "Fixed Research Tech Tree layout requires one exact layer width");
            }
        }

        public boolean dynamic() {
            return widthMode == WidthMode.DYNAMIC;
        }

        public boolean acceptsResolvedWidth(int width) {
            return width >= minNodesPerLayer && width <= maxNodesPerLayer;
        }

        private static LayoutDefinition decode(
                int maximum,
                Optional<WidthMode> mode,
                Optional<Integer> minimum) {
            WidthMode resolvedMode = mode.orElse(WidthMode.FIXED);
            int resolvedMinimum = minimum.orElse(
                    resolvedMode == WidthMode.FIXED
                            ? maximum
                            : DEFAULT_NODES_PER_LAYER);
            return new LayoutDefinition(resolvedMode, resolvedMinimum, maximum);
        }
    }

    /** Tree-owned presentation policy. Only rank remains progression authority. */
    public record BandPolicyDefinition(
            BandMode mode,
            int ranksPerBand,
            BandBasis basis,
            List<BandDefinition> definitions) {
        public static final int DEFAULT_RANKS_PER_BAND = 3;
        public static final int MIN_RANKS_PER_BAND = 1;
        public static final int MAX_RANKS_PER_BAND = 64;
        public static final BandPolicyDefinition LEGACY = new BandPolicyDefinition(
                BandMode.LEGACY,
                DEFAULT_RANKS_PER_BAND,
                BandBasis.RANK,
                List.of());
        public static final BandPolicyDefinition NONE = new BandPolicyDefinition(
                BandMode.NONE,
                DEFAULT_RANKS_PER_BAND,
                BandBasis.RANK,
                List.of());
        public static final BandPolicyDefinition DYNAMIC = new BandPolicyDefinition(
                BandMode.DYNAMIC,
                DEFAULT_RANKS_PER_BAND,
                BandBasis.RANK,
                List.of());

        public BandPolicyDefinition {
            definitions = definitions == null ? List.of() : List.copyOf(definitions);
            if (mode == null || basis == null
                    || ranksPerBand < MIN_RANKS_PER_BAND
                    || ranksPerBand > MAX_RANKS_PER_BAND
                    || definitions.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException(
                        "Research Tech Tree band policy is invalid");
            }
            if (mode != BandMode.CONFIGURED && !definitions.isEmpty()) {
                throw new IllegalArgumentException(
                        "Only configured Research Tech Tree bands may declare definitions");
            }
            if (mode != BandMode.DYNAMIC
                    && ranksPerBand != DEFAULT_RANKS_PER_BAND) {
                throw new IllegalArgumentException(
                        "Only dynamic Research Tech Tree bands may change ranks_per_band");
            }
            if (mode == BandMode.CONFIGURED) {
                validateConfiguredBands(basis, definitions);
            } else if (basis != BandBasis.RANK) {
                throw new IllegalArgumentException(
                        "Only configured Research Tech Tree bands may use score boundaries");
            }
        }
    }

    public record BandDefinition(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            Optional<Integer> color,
            Optional<ResourceLocation> icon,
            Optional<Integer> maximum) {
        public BandDefinition {
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            color = color == null ? Optional.empty() : color;
            icon = icon == null ? Optional.empty() : icon;
            maximum = maximum == null ? Optional.empty() : maximum;
            if (id == null
                    || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                    || !validTitle(title)
                    || translationKey.filter(value -> !validTranslationKey(value)).isPresent()
                    || color.filter(value -> value < 0 || value > 0xFFFFFF).isPresent()
                    || icon.filter(value -> value.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()
                    || maximum.filter(value -> value < 0
                            || value > com.gamergaming.taczweaponblueprints.research.tree
                                    .ResearchTechTreeContract.MAX_PROGRESSION_RANK).isPresent()) {
                throw new IllegalArgumentException(
                        "Research Tech Tree presentation band is invalid");
            }
        }
    }

    public enum BandMode {
        LEGACY,
        NONE,
        DYNAMIC,
        CONFIGURED
    }

    public enum BandBasis {
        RANK,
        SCORE
    }

    public enum WidthMode {
        FIXED,
        DYNAMIC
    }

    public record TierDefinition(
            Tier tier,
            String title,
            Optional<String> translationKey) {
        public TierDefinition {
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            validateTierProgrammatic(tier, title, translationKey);
        }
    }

    public record LaneDefinition(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> icon,
            int order) {
        public LaneDefinition {
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            icon = icon == null ? Optional.empty() : icon;
            validateLaneProgrammatic(id, title, translationKey, icon, order);
        }
    }

    public record DomainDefinition(
            Domain domain,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> icon,
            ResourceLocation fallbackLane,
            Tier fallbackTier,
            List<LaneDefinition> lanes) {
        public DomainDefinition {
            translationKey = translationKey == null ? Optional.empty() : translationKey;
            icon = icon == null ? Optional.empty() : icon;
            lanes = lanes == null ? List.of() : List.copyOf(lanes);
            validateDomainProgrammatic(
                    domain, title, translationKey, icon, fallbackLane, fallbackTier, lanes);
            lanes = lanes.stream()
                    .sorted(Comparator.comparingInt(LaneDefinition::order)
                            .thenComparing(value -> value.id().toString()))
                    .toList();
        }
    }

    private static DataResult<ResearchTechTreeDefinition> validateDefinition(
            ResearchTechTreeDefinition definition) {
        try {
            definition.validateForSnapshot();
            return DataResult.success(definition);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<TierDefinition> validateTierDefinition(TierDefinition definition) {
        try {
            validateTierProgrammatic(definition.tier(), definition.title(), definition.translationKey());
            return DataResult.success(definition);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<LaneDefinition> validateLaneDefinition(LaneDefinition definition) {
        try {
            validateLaneProgrammatic(
                    definition.id(),
                    definition.title(),
                    definition.translationKey(),
                    definition.icon(),
                    definition.order());
            return DataResult.success(definition);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<DomainDefinition> validateDomainDefinition(DomainDefinition definition) {
        try {
            validateDomainProgrammatic(
                    definition.domain(),
                    definition.title(),
                    definition.translationKey(),
                    definition.icon(),
                    definition.fallbackLane(),
                    definition.fallbackTier(),
                    definition.lanes());
            return DataResult.success(definition);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validateProgrammatic(
            int format,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> icon,
            WeaponPlacementMode weaponPlacementMode,
            LayoutDefinition layout,
            BandPolicyDefinition bandPolicy,
            List<TierDefinition> tiers,
            List<DomainDefinition> domains) {
        if (format < LEGACY_FORMAT || format > CURRENT_FORMAT) {
            throw new IllegalArgumentException("unsupported Research Tech Tree format " + format);
        }
        if (weaponPlacementMode == null || layout == null || bandPolicy == null
                || format == LEGACY_FORMAT && !layout.equals(LayoutDefinition.DEFAULT)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree format 1 must use the default layout policy");
        }
        if (format == LEGACY_FORMAT && bandPolicy.mode() != BandMode.LEGACY
                || format == CURRENT_FORMAT && bandPolicy.mode() == BandMode.LEGACY) {
            throw new IllegalArgumentException(
                    "Research Tech Tree band policy does not match its format");
        }
        if (format == LEGACY_FORMAT
                && weaponPlacementMode != WeaponPlacementMode.AUTHORED_ONLY) {
            throw new IllegalArgumentException(
                    "Research Tech Tree format 1 cannot use automatic weapon placement");
        }
        validateText(title, translationKey, "Research Tech Tree");
        validateOptionalId(icon, "Research Tech Tree icon");
        if (tiers == null || tiers.stream().anyMatch(value -> value == null)
                || format == LEGACY_FORMAT && tiers.size() != Tier.values().length
                || format == CURRENT_FORMAT
                        && !tiers.isEmpty() && tiers.size() != Tier.values().length) {
            throw new IllegalArgumentException(
                    "Research Tech Tree legacy labels must define all "
                            + Tier.values().length + " tiers exactly once");
        }
        EnumSet<Tier> uniqueTiers = EnumSet.noneOf(Tier.class);
        tiers.forEach(value -> {
            validateTierProgrammatic(value.tier(), value.title(), value.translationKey());
            if (!uniqueTiers.add(value.tier())) {
                throw new IllegalArgumentException("Research Tech Tree contains duplicate tier " + value.tier());
            }
        });
        if (!tiers.isEmpty() && uniqueTiers.size() != Tier.values().length) {
            throw new IllegalArgumentException("Research Tech Tree must define every contract tier");
        }
        if (domains == null || domains.isEmpty() || domains.size() > Domain.values().length
                || domains.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException("Research Tech Tree must define between 1 and 3 domains");
        }
        EnumSet<Domain> uniqueDomains = EnumSet.noneOf(Domain.class);
        Set<ResourceLocation> uniqueLanes = new LinkedHashSet<>();
        int totalLanes = 0;
        for (DomainDefinition domain : domains) {
            validateDomainProgrammatic(
                    domain.domain(),
                    domain.title(),
                    domain.translationKey(),
                    domain.icon(),
                    domain.fallbackLane(),
                    domain.fallbackTier(),
                    domain.lanes());
            if (!uniqueDomains.add(domain.domain())) {
                throw new IllegalArgumentException(
                        "Research Tech Tree contains duplicate domain " + domain.domain());
            }
            for (LaneDefinition lane : domain.lanes()) {
                if (!uniqueLanes.add(lane.id())) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree contains duplicate lane ID " + lane.id());
                }
                totalLanes++;
            }
        }
        if (totalLanes > MAX_TOTAL_LANES) {
            throw new IllegalArgumentException(
                    "Research Tech Tree cannot contain more than " + MAX_TOTAL_LANES + " lanes");
        }
    }

    private static ResearchTechTreeDefinition decode(
            int format,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> icon,
            WeaponPlacementMode weaponPlacementMode,
            Optional<LayoutDefinition> layout,
            Optional<BandPolicyDefinition> bandPolicy,
            Optional<List<TierDefinition>> tiers,
            List<DomainDefinition> domains) {
        if (format == CURRENT_FORMAT && layout.isEmpty()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree format 2 requires a layout object");
        }
        if (format == LEGACY_FORMAT && layout.isPresent()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree format 1 cannot declare a layout object");
        }
        if (format == LEGACY_FORMAT && bandPolicy.isPresent()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree format 1 cannot declare a bands object");
        }
        return new ResearchTechTreeDefinition(
                format,
                title,
                translationKey,
                icon,
                weaponPlacementMode,
                layout.orElse(LayoutDefinition.DEFAULT),
                bandPolicy.orElse(format == LEGACY_FORMAT
                        ? BandPolicyDefinition.LEGACY
                        : BandPolicyDefinition.NONE),
                tiers.orElse(List.of()),
                domains);
    }

    private static void validateConfiguredBands(
            BandBasis basis,
            List<BandDefinition> definitions) {
        if (definitions.isEmpty() || definitions.size() > MAX_PRESENTATION_BANDS) {
            throw new IllegalArgumentException(
                    "Configured Research Tech Tree bands must define between 1 and "
                            + MAX_PRESENTATION_BANDS + " bands");
        }
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        int previousMaximum = -1;
        for (int index = 0; index < definitions.size(); index++) {
            BandDefinition definition = definitions.get(index);
            if (!ids.add(definition.id())) {
                throw new IllegalArgumentException(
                        "Configured Research Tech Tree bands contain duplicate IDs");
            }
            boolean last = index == definitions.size() - 1;
            if (last != definition.maximum().isEmpty()) {
                throw new IllegalArgumentException(
                        "Only the final configured Research Tech Tree band may omit maximum");
            }
            if (!last) {
                int maximum = definition.maximum().orElseThrow();
                int supportedMaximum = basis == BandBasis.SCORE
                        ? com.gamergaming.taczweaponblueprints.research.tree
                                .ResearchTechTreeContract.SCORE_MAX
                        : com.gamergaming.taczweaponblueprints.research.tree
                                .ResearchTechTreeContract.MAX_PROGRESSION_RANK;
                if (maximum <= previousMaximum || maximum >= supportedMaximum) {
                    throw new IllegalArgumentException(
                            "Configured Research Tech Tree band maxima must increase below "
                                    + supportedMaximum);
                }
                previousMaximum = maximum;
            }
        }
    }

    private static DataResult<BandMode> decodeBandMode(String value) {
        if (value != null) {
            try {
                BandMode mode = BandMode.valueOf(value.toUpperCase(Locale.ROOT));
                if (mode != BandMode.LEGACY) {
                    return DataResult.success(mode);
                }
            } catch (IllegalArgumentException ignored) {
                // Report the stable schema error below.
            }
        }
        return DataResult.error(() -> "unknown Research Tech Tree band mode " + value
                + "; expected one of [none, dynamic, configured]");
    }

    private static void validateTierProgrammatic(
            Tier tier,
            String title,
            Optional<String> translationKey) {
        if (tier == null) {
            throw new IllegalArgumentException("Research Tech Tree tier ID cannot be null");
        }
        validateText(title, translationKey, "Research Tech Tree tier");
    }

    private static void validateLaneProgrammatic(
            ResourceLocation id,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> icon,
            int order) {
        if (id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("Research Tech Tree lane ID is invalid or oversized");
        }
        validateText(title, translationKey, "Research Tech Tree lane");
        validateOptionalId(icon, "Research Tech Tree lane icon");
        if (order < 0 || order > MAX_ORDER) {
            throw new IllegalArgumentException("Research Tech Tree lane order is outside the supported range");
        }
    }

    private static void validateDomainProgrammatic(
            Domain domain,
            String title,
            Optional<String> translationKey,
            Optional<ResourceLocation> icon,
            ResourceLocation fallbackLane,
            Tier fallbackTier,
            List<LaneDefinition> lanes) {
        if (domain == null || fallbackTier == null) {
            throw new IllegalArgumentException("Research Tech Tree domain and fallback tier cannot be null");
        }
        validateText(title, translationKey, "Research Tech Tree domain");
        validateOptionalId(icon, "Research Tech Tree domain icon");
        if (fallbackLane == null
                || fallbackLane.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("Research Tech Tree fallback lane is invalid or oversized");
        }
        if (lanes == null || lanes.isEmpty() || lanes.size() > MAX_LANES_PER_DOMAIN
                || lanes.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree domain must define between 1 and "
                            + MAX_LANES_PER_DOMAIN + " lanes");
        }
        Set<ResourceLocation> unique = new LinkedHashSet<>();
        for (LaneDefinition lane : lanes) {
            validateLaneProgrammatic(
                    lane.id(), lane.title(), lane.translationKey(), lane.icon(), lane.order());
            if (!unique.add(lane.id())) {
                throw new IllegalArgumentException(
                        "Research Tech Tree domain contains duplicate lane " + lane.id());
            }
        }
        if (!unique.contains(fallbackLane)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree fallback lane " + fallbackLane + " is not in domain " + domain);
        }
    }

    private static void validateText(
            String title,
            Optional<String> translationKey,
            String owner) {
        if (!validTitle(title)) {
            throw new IllegalArgumentException(owner + " title is invalid");
        }
        if (translationKey == null
                || translationKey.filter(value -> !validTranslationKey(value)).isPresent()) {
            throw new IllegalArgumentException(owner + " translation key is invalid");
        }
    }

    private static void validateOptionalId(Optional<ResourceLocation> id, String owner) {
        if (id == null || id.filter(value -> value == null
                || value.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()) {
            throw new IllegalArgumentException(owner + " is invalid or oversized");
        }
    }

    private static DataResult<String> validateTitleResult(String value) {
        return validTitle(value)
                ? DataResult.success(value)
                : DataResult.error(() -> "Research Tech Tree titles must be trimmed, non-empty, free of control "
                        + "characters, and at most " + MAX_TITLE_LENGTH + " characters");
    }

    private static DataResult<String> validateTranslationKeyResult(String value) {
        return validTranslationKey(value)
                ? DataResult.success(value)
                : DataResult.error(() -> "Research Tech Tree translation keys must be non-empty, contain no "
                        + "whitespace or control characters, and be at most "
                        + MAX_TRANSLATION_KEY_LENGTH + " characters");
    }

    private static DataResult<Integer> validateOrderResult(int value) {
        return value >= 0 && value <= MAX_ORDER
                ? DataResult.success(value)
                : DataResult.error(() -> "Research Tech Tree order must be between zero and " + MAX_ORDER);
    }

    private static boolean validTitle(String value) {
        return value != null
                && !value.isBlank()
                && value.equals(value.trim())
                && value.length() <= MAX_TITLE_LENGTH
                && value.chars().noneMatch(Character::isISOControl);
    }

    private static boolean validTranslationKey(String value) {
        return value != null
                && !value.isBlank()
                && value.length() <= MAX_TRANSLATION_KEY_LENGTH
                && value.chars().noneMatch(character -> Character.isWhitespace(character)
                        || Character.isISOControl(character));
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(
            Class<E> type,
            String description) {
        return Codec.STRING.flatXmap(
                value -> parseEnum(type, description, value),
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));
    }

    private static <E extends Enum<E>> DataResult<E> parseEnum(
            Class<E> type,
            String description,
            String value) {
        if (value != null) {
            try {
                return DataResult.success(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Report a stable schema error below.
            }
        }
        List<String> supported = new ArrayList<>();
        for (E enumValue : type.getEnumConstants()) {
            supported.add(enumValue.name().toLowerCase(Locale.ROOT));
        }
        return DataResult.error(() -> "unknown " + description + " " + value
                + "; expected one of " + supported);
    }
}
