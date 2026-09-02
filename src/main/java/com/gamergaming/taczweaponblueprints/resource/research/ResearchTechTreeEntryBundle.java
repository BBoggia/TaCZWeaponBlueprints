package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionCoordinate;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.ProgressionPosition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.WeaponRating;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** Additive, override-friendly placement metadata for one Research Tech Tree. */
public record ResearchTechTreeEntryBundle(
        int format,
        ResourceLocation tree,
        int priority,
        List<Entry> entries) {
    public static final int LEGACY_FORMAT = 1;
    public static final int CURRENT_FORMAT = 2;
    public static final int MAX_PRIORITY = 1_000_000;
    public static final int MAX_ORDER = 1_000_000;
    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_OVERRIDE_REASON_LENGTH = 512;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            value -> value >= LEGACY_FORMAT && value <= CURRENT_FORMAT
                    ? DataResult.success(value)
                    : DataResult.error(() -> "unsupported Research Tech Tree entry format " + value),
            value -> value >= LEGACY_FORMAT && value <= CURRENT_FORMAT
                    ? DataResult.success(value)
                    : DataResult.error(() -> "unsupported Research Tech Tree entry format " + value));
    private static final Codec<Integer> PRIORITY_CODEC = Codec.INT.flatXmap(
            value -> value >= 0 && value <= MAX_PRIORITY
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Research Tech Tree entry priority must be between zero and "
                            + MAX_PRIORITY),
            value -> value >= 0 && value <= MAX_PRIORITY
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Research Tech Tree entry priority must be between zero and "
                            + MAX_PRIORITY));
    private static final Codec<Integer> ORDER_CODEC = Codec.INT.flatXmap(
            value -> value >= 0 && value <= MAX_ORDER
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Research Tech Tree entry order must be between zero and "
                            + MAX_ORDER),
            value -> value >= 0 && value <= MAX_ORDER
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Research Tech Tree entry order must be between zero and "
                            + MAX_ORDER));
    private static final Codec<Integer> LEVEL_CODEC = Codec.INT.flatXmap(
            value -> value >= 0 && value < ResearchTechTreeContract.MAX_LEVELS_PER_TIER
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Research Tech Tree entry level must be between zero and "
                            + (ResearchTechTreeContract.MAX_LEVELS_PER_TIER - 1)),
            value -> value >= 0 && value < ResearchTechTreeContract.MAX_LEVELS_PER_TIER
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Research Tech Tree entry level must be between zero and "
                            + (ResearchTechTreeContract.MAX_LEVELS_PER_TIER - 1)));
    private static final Codec<Integer> RANK_CODEC = Codec.INT.flatXmap(
            value -> value >= 0 && value <= ResearchTechTreeContract.MAX_PROGRESSION_RANK
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Research Tech Tree entry rank must be between zero and "
                            + ResearchTechTreeContract.MAX_PROGRESSION_RANK),
            value -> value >= 0 && value <= ResearchTechTreeContract.MAX_PROGRESSION_RANK
                    ? DataResult.success(value)
                    : DataResult.error(() -> "Research Tech Tree entry rank must be between zero and "
                            + ResearchTechTreeContract.MAX_PROGRESSION_RANK));
    private static final Codec<String> OVERRIDE_REASON_CODEC = Codec.STRING.flatXmap(
            ResearchTechTreeEntryBundle::validateOverrideReasonResult,
            ResearchTechTreeEntryBundle::validateOverrideReasonResult);
    private static final Codec<Domain> DOMAIN_CODEC = enumCodec(Domain.class, "Research Tech Tree domain");
    private static final Codec<Tier> TIER_CODEC = enumCodec(Tier.class, "Research Tech Tree tier");

    private static final Codec<WeaponRating> RAW_RATING_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    scoreCodec("combat").fieldOf("combat").forGetter(WeaponRating::combat),
                    scoreCodec("utility").fieldOf("utility").forGetter(WeaponRating::utility),
                    scoreCodec("appeal").fieldOf("appeal").forGetter(WeaponRating::appeal))
                    .apply(instance, WeaponRating::new));
    public static final Codec<WeaponRating> WEAPON_RATING_CODEC = StrictRecordCodec.wrap(
            "Research Tech Tree weapon rating",
            RAW_RATING_CODEC,
            "combat",
            "utility",
            "appeal");

    private static final Codec<Entry> RAW_ENTRY_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    BlueprintResearchTarget.CODEC.fieldOf("target").forGetter(Entry::target),
                    DOMAIN_CODEC.fieldOf("domain").forGetter(Entry::domain),
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("lane").forGetter(Entry::lane),
                    TIER_CODEC.fieldOf("tier").forGetter(Entry::tier),
                    new StrictOptionalFieldCodec<>("level", LEVEL_CODEC)
                            .xmap(value -> value.orElse(0), value -> value == 0
                                    ? Optional.empty()
                                    : Optional.of(value))
                            .forGetter(Entry::level),
                    new StrictOptionalFieldCodec<>("rank", RANK_CODEC)
                            .forGetter(Entry::rank),
                    ORDER_CODEC.fieldOf("order").forGetter(Entry::order),
                    new StrictOptionalFieldCodec<>("rating", WEAPON_RATING_CODEC)
                            .forGetter(Entry::rating),
                    new StrictOptionalFieldCodec<>("tier_override_reason", OVERRIDE_REASON_CODEC)
                            .forGetter(Entry::tierOverrideReason),
                    new StrictOptionalFieldCodec<>("fallback", Codec.BOOL)
                            .xmap(value -> value.orElse(false), value -> value
                                    ? Optional.of(true)
                                    : Optional.empty())
                            .forGetter(Entry::fallback))
                    .apply(instance, Entry::new));
    public static final Codec<Entry> ENTRY_CODEC = StrictRecordCodec.wrap(
            "Research Tech Tree entry",
            RAW_ENTRY_CODEC.flatXmap(
                    ResearchTechTreeEntryBundle::validateEntry,
                    ResearchTechTreeEntryBundle::validateEntry),
            "target",
            "domain",
            "lane",
            "tier",
            "level",
            "rank",
            "order",
            "rating",
            "tier_override_reason",
            "fallback");

    private static final Codec<ResearchTechTreeEntryBundle> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(ResearchTechTreeEntryBundle::format),
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("tree")
                            .forGetter(ResearchTechTreeEntryBundle::tree),
                    new StrictOptionalFieldCodec<>("priority", PRIORITY_CODEC)
                            .xmap(value -> value.orElse(0), value -> value == 0
                                    ? Optional.empty()
                                    : Optional.of(value))
                            .forGetter(ResearchTechTreeEntryBundle::priority),
                    ENTRY_CODEC.listOf().fieldOf("entries")
                            .forGetter(ResearchTechTreeEntryBundle::entries))
                    .apply(instance, ResearchTechTreeEntryBundle::new));
    public static final Codec<ResearchTechTreeEntryBundle> CODEC = StrictRecordCodec.wrap(
            "Research Tech Tree entry bundle",
            RAW_CODEC.flatXmap(
                    ResearchTechTreeEntryBundle::validateBundle,
                    ResearchTechTreeEntryBundle::validateBundle),
            "format",
            "tree",
            "priority",
            "entries");

    public ResearchTechTreeEntryBundle {
        entries = entries == null ? List.of() : List.copyOf(entries);
        validateProgrammatic(format, tree, priority, entries);
    }

    public int targetTermCount() {
        return entries.stream().mapToInt(entry ->
                entry.target().blueprints().size()
                        + entry.target().tags().size()
                        + entry.target().selector().map(selector -> selector.termCount()).orElse(0))
                .sum();
    }

    void validateForSnapshot() {
        validateProgrammatic(format, tree, priority, entries);
    }

    public record Entry(
            BlueprintResearchTarget target,
            Domain domain,
            ResourceLocation lane,
            Tier tier,
            int level,
            Optional<Integer> rank,
            int order,
            Optional<WeaponRating> rating,
            Optional<String> tierOverrideReason,
            boolean fallback) {
        /** Backwards-compatible format-1 constructor with an explicit legacy level. */
        public Entry(
                BlueprintResearchTarget target,
                Domain domain,
                ResourceLocation lane,
                Tier tier,
                int level,
                int order,
                Optional<WeaponRating> rating,
                Optional<String> tierOverrideReason,
                boolean fallback) {
            this(
                    target,
                    domain,
                    lane,
                    tier,
                    level,
                    Optional.empty(),
                    order,
                    rating,
                    tierOverrideReason,
                    fallback);
        }

        public Entry(
                BlueprintResearchTarget target,
                Domain domain,
                ResourceLocation lane,
                Tier tier,
                int order,
                Optional<WeaponRating> rating,
                Optional<String> tierOverrideReason,
                boolean fallback) {
            this(target, domain, lane, tier, 0, order, rating, tierOverrideReason, fallback);
        }

        public Entry(
                BlueprintResearchTarget target,
                Domain domain,
                ResourceLocation lane,
                Tier tier,
                int order,
                Optional<WeaponRating> rating,
                Optional<String> tierOverrideReason) {
            this(target, domain, lane, tier, 0, order, rating, tierOverrideReason, false);
        }

        public Entry {
            rank = rank == null ? Optional.empty() : rank;
            rating = rating == null ? Optional.empty() : rating;
            tierOverrideReason = tierOverrideReason == null ? Optional.empty() : tierOverrideReason;
            validateEntryProgrammatic(
                    target, domain, lane, tier, level, rank, order, rating, tierOverrideReason, fallback);
        }

        public ProgressionCoordinate initialProgressionCoordinate(int bundleFormat) {
            if (bundleFormat == LEGACY_FORMAT) {
                return ResearchTechTreeContract.legacyProgressionCoordinate(
                        new ProgressionPosition(tier, level, order));
            }
            if (bundleFormat == CURRENT_FORMAT) {
                return new ProgressionCoordinate(
                        rank.orElseThrow(() -> new IllegalArgumentException(
                                "format-2 Research Tech Tree entry is missing rank")),
                        order,
                        Optional.of(ResearchTechTreeContract.legacyBandId(tier)));
            }
            throw new IllegalArgumentException(
                    "unsupported Research Tech Tree entry format " + bundleFormat);
        }
    }

    private static DataResult<ResearchTechTreeEntryBundle> validateBundle(
            ResearchTechTreeEntryBundle bundle) {
        try {
            bundle.validateForSnapshot();
            return DataResult.success(bundle);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<Entry> validateEntry(Entry entry) {
        try {
            validateEntryProgrammatic(
                    entry.target(),
                    entry.domain(),
                    entry.lane(),
                    entry.tier(),
                    entry.level(),
                    entry.rank(),
                    entry.order(),
                    entry.rating(),
                    entry.tierOverrideReason(),
                    entry.fallback());
            return DataResult.success(entry);
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validateProgrammatic(
            int format,
            ResourceLocation tree,
            int priority,
            List<Entry> entries) {
        if (format < LEGACY_FORMAT || format > CURRENT_FORMAT) {
            throw new IllegalArgumentException("unsupported Research Tech Tree entry format " + format);
        }
        if (tree == null || tree.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException(
                    "Research Tech Tree entry bundle tree is invalid or oversized");
        }
        if (priority < 0 || priority > MAX_PRIORITY) {
            throw new IllegalArgumentException("Research Tech Tree entry priority is outside the supported range");
        }
        if (entries == null || entries.isEmpty() || entries.size() > MAX_ENTRIES
                || entries.stream().anyMatch(value -> value == null)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree entry bundle must contain between 1 and " + MAX_ENTRIES + " entries");
        }
        Set<ResourceLocation> exactOwners = new LinkedHashSet<>();
        for (Entry entry : entries) {
            validateEntryProgrammatic(
                    entry.target(),
                    entry.domain(),
                    entry.lane(),
                    entry.tier(),
                    entry.level(),
                    entry.rank(),
                    entry.order(),
                    entry.rating(),
                    entry.tierOverrideReason(),
                    entry.fallback());
            if (format == LEGACY_FORMAT && entry.rank().isPresent()) {
                throw new IllegalArgumentException(
                        "format-1 Research Tech Tree entries cannot declare rank");
            }
            if (format == CURRENT_FORMAT && entry.rank().isEmpty()) {
                throw new IllegalArgumentException(
                        "format-2 Research Tech Tree entries must declare rank");
            }
            if (entry.fallback() && priority != 0) {
                throw new IllegalArgumentException(
                        "Research Tech Tree fallback entries must use zero bundle priority");
            }
            for (ResourceLocation exactId : entry.target().blueprints()) {
                if (!exactOwners.add(exactId)) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree entry bundle contains duplicate exact target " + exactId);
                }
            }
        }
    }

    private static void validateEntryProgrammatic(
            BlueprintResearchTarget target,
            Domain domain,
            ResourceLocation lane,
            Tier tier,
            int level,
            Optional<Integer> rank,
            int order,
            Optional<WeaponRating> rating,
            Optional<String> tierOverrideReason,
            boolean fallback) {
        if (target == null || domain == null || lane == null || tier == null
                || rank == null || rating == null || tierOverrideReason == null) {
            throw new IllegalArgumentException("Research Tech Tree entry fields cannot be null");
        }
        if (level < 0 || level >= ResearchTechTreeContract.MAX_LEVELS_PER_TIER) {
            throw new IllegalArgumentException(
                    "Research Tech Tree entry level is outside the supported range");
        }
        if (rank.filter(value -> value < 0
                || value > ResearchTechTreeContract.MAX_PROGRESSION_RANK).isPresent()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree entry rank is outside the supported range");
        }
        if (lane.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("Research Tech Tree entry lane is oversized");
        }
        target.validateForSnapshot();
        if (order < 0 || order > MAX_ORDER) {
            throw new IllegalArgumentException("Research Tech Tree entry order is outside the supported range");
        }
        target.selector().ifPresent(selector -> {
            for (BlueprintKind kind : selector.blueprintKinds()) {
                if (Domain.forKind(kind) != domain) {
                    throw new IllegalArgumentException(
                            "Research Tech Tree selector kind " + kind + " cannot target domain " + domain);
                }
            }
        });
        boolean selectorFallback = target.blueprints().isEmpty()
                && target.tags().isEmpty()
                && target.selector().isPresent();
        boolean exactFallback = target.exactOnly()
                && target.blueprints().size() == 1;
        if (fallback && !selectorFallback && !exactFallback) {
            throw new IllegalArgumentException(
                    "Research Tech Tree fallback entries require a selector-only "
                            + "or single exact target");
        }
        if (rating.isPresent()) {
            if (domain != Domain.WEAPONS) {
                throw new IllegalArgumentException("Research Tech Tree ratings are valid only for weapons");
            }
            if (!target.exactOnly() || target.blueprints().size() != 1) {
                throw new IllegalArgumentException(
                        "Research Tech Tree ratings require exactly one exact blueprint target");
            }
            boolean overridden = rating.orElseThrow().suggestedTier() != tier;
            if (overridden != tierOverrideReason.isPresent()) {
                throw new IllegalArgumentException(overridden
                        ? "Research Tech Tree rating tier overrides require a reason"
                        : "Research Tech Tree tier override reason is allowed only when overriding the rated tier");
            }
        } else if (tierOverrideReason.isPresent()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree tier override reason requires a weapon rating");
        }
        if (fallback && (rating.isPresent() || tierOverrideReason.isPresent())) {
            throw new IllegalArgumentException(
                    "Research Tech Tree fallback entries cannot contain reviewed rating evidence");
        }
        tierOverrideReason.ifPresent(value -> {
            if (!validOverrideReason(value)) {
                throw new IllegalArgumentException("Research Tech Tree tier override reason is invalid");
            }
        });
    }

    private static Codec<Integer> scoreCodec(String field) {
        return Codec.INT.flatXmap(
                value -> value >= 0 && value <= 100
                        ? DataResult.success(value)
                        : DataResult.error(() -> "Research Tech Tree " + field
                                + " rating must be between zero and 100"),
                value -> value >= 0 && value <= 100
                        ? DataResult.success(value)
                        : DataResult.error(() -> "Research Tech Tree " + field
                                + " rating must be between zero and 100"));
    }

    private static DataResult<String> validateOverrideReasonResult(String value) {
        return validOverrideReason(value)
                ? DataResult.success(value)
                : DataResult.error(() -> "Research Tech Tree tier override reason must be trimmed, non-empty, "
                        + "free of control characters, and at most " + MAX_OVERRIDE_REASON_LENGTH + " characters");
    }

    private static boolean validOverrideReason(String value) {
        return value != null
                && !value.isBlank()
                && value.equals(value.trim())
                && value.length() <= MAX_OVERRIDE_REASON_LENGTH
                && value.chars().noneMatch(Character::isISOControl);
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
                // Report the supported values below.
            }
        }
        return DataResult.error(() -> "unknown " + description + " " + value);
    }
}
