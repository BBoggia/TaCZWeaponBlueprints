package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.Decoder;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

/** Profile-scoped, server-authored presentation metadata for one Research Tree group. */
public record ResearchTreeGroupDefinition(
        int format,
        ResourceLocation profile,
        String title,
        Optional<String> translationKey,
        ResourceLocation icon,
        int order,
        Optional<Boolean> includeInOverview,
        List<List<ResourceLocation>> ranks) {
    public static final int CURRENT_FORMAT = 1;
    public static final int MAX_TITLE_LENGTH = 80;
    public static final int MAX_TRANSLATION_KEY_LENGTH = 256;
    public static final int MAX_ORDER = 1_000_000;
    public static final int MAX_RANKS = BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH;
    public static final int MAX_MEMBERS = PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            ResearchTreeGroupDefinition::validateFormat,
            ResearchTreeGroupDefinition::validateFormat);
    private static final Codec<String> TITLE_CODEC = Codec.STRING.flatXmap(
            ResearchTreeGroupDefinition::validateTitle,
            ResearchTreeGroupDefinition::validateTitle);
    private static final Codec<String> TRANSLATION_KEY_CODEC = Codec.STRING.flatXmap(
            ResearchTreeGroupDefinition::validateTranslationKey,
            ResearchTreeGroupDefinition::validateTranslationKey);
    private static final Codec<Integer> ORDER_CODEC = Codec.INT.flatXmap(
            ResearchTreeGroupDefinition::validateOrder,
            ResearchTreeGroupDefinition::validateOrder);
    private static final Codec<List<List<ResourceLocation>>> UNBOUNDED_RANKS_CODEC =
            BlueprintResearchCodecs.RESOURCE_LOCATION.listOf().listOf();
    private static final Codec<List<List<ResourceLocation>>> RANKS_CODEC = Codec.of(
            UNBOUNDED_RANKS_CODEC,
            new Decoder<>() {
                @Override
                public <T> DataResult<Pair<List<List<ResourceLocation>>, T>> decode(
                        DynamicOps<T> ops,
                        T input) {
                    return decodeRanks(ops, input);
                }
            },
            "BoundedResearchTreeRanks");

    private static final Codec<RawDefinition> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(RawDefinition::format),
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("profile")
                            .forGetter(RawDefinition::profile),
                    TITLE_CODEC.fieldOf("title").forGetter(RawDefinition::title),
                    new StrictOptionalFieldCodec<>("translation_key", TRANSLATION_KEY_CODEC)
                            .forGetter(RawDefinition::translationKey),
                    BlueprintResearchCodecs.RESOURCE_LOCATION.fieldOf("icon")
                            .forGetter(RawDefinition::icon),
                    ORDER_CODEC.fieldOf("order").forGetter(RawDefinition::order),
                    new StrictOptionalFieldCodec<>("include_in_overview", Codec.BOOL)
                            .forGetter(RawDefinition::includeInOverview),
                    RANKS_CODEC.fieldOf("ranks")
                            .forGetter(RawDefinition::ranks))
                    .apply(instance, RawDefinition::new));

    public static final Codec<ResearchTreeGroupDefinition> CODEC = StrictRecordCodec.wrap(
            "research tree group",
            RAW_CODEC.flatXmap(
                    ResearchTreeGroupDefinition::decodeDefinition,
                    ResearchTreeGroupDefinition::encodeDefinition),
            "format",
            "profile",
            "title",
            "translation_key",
            "icon",
            "order",
            "include_in_overview",
            "ranks");

    /** Backwards-compatible programmatic constructor using the kind default. */
    public ResearchTreeGroupDefinition(
            int format,
            ResourceLocation profile,
            String title,
            Optional<String> translationKey,
            ResourceLocation icon,
            int order,
            List<List<ResourceLocation>> ranks) {
        this(format, profile, title, translationKey, icon, order, Optional.empty(), ranks);
    }

    public ResearchTreeGroupDefinition {
        if (format != CURRENT_FORMAT) {
            throw new IllegalArgumentException("unsupported research-tree group format " + format);
        }
        if (profile == null || title == null || icon == null || ranks == null) {
            throw new IllegalArgumentException("research-tree group fields cannot be null");
        }
        translationKey = translationKey == null ? Optional.empty() : translationKey;
        includeInOverview = includeInOverview == null ? Optional.empty() : includeInOverview;
        validateRankContainerBounds(ranks);
        ranks = ranks.stream().map(rank -> rank == null ? null : List.copyOf(rank)).toList();
        validateProgrammatic(profile, title, translationKey, icon, order, ranks);
    }

    public List<ResourceLocation> members() {
        return ranks.stream().flatMap(List::stream).toList();
    }

    public int memberCount() {
        return ranks.stream().mapToInt(List::size).sum();
    }

    void validateForSnapshot() {
        validateProgrammatic(profile, title, translationKey, icon, order, ranks);
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value == CURRENT_FORMAT
                ? DataResult.success(value)
                : DataResult.error(() -> "unsupported research-tree group format " + value);
    }

    private static DataResult<String> validateTitle(String value) {
        return validTitle(value)
                ? DataResult.success(value)
                : DataResult.error(() -> "research-tree group title must be trimmed, non-empty, free of control "
                        + "characters, and at most " + MAX_TITLE_LENGTH + " characters");
    }

    private static DataResult<String> validateTranslationKey(String value) {
        return validTranslationKey(value)
                ? DataResult.success(value)
                : DataResult.error(() -> "research-tree group translation key must be non-empty, contain no "
                        + "whitespace or control characters, and be at most "
                        + MAX_TRANSLATION_KEY_LENGTH + " characters");
    }

    private static DataResult<Integer> validateOrder(int value) {
        return value >= 0 && value <= MAX_ORDER
                ? DataResult.success(value)
                : DataResult.error(() -> "research-tree group order must be between zero and " + MAX_ORDER);
    }

    private static <T> DataResult<Pair<List<List<ResourceLocation>>, T>> decodeRanks(
            DynamicOps<T> ops,
            T input) {
        DataResult<java.util.stream.Stream<T>> rankValuesResult = ops.getStream(input);
        Optional<java.util.stream.Stream<T>> rankValues = rankValuesResult.result();
        if (rankValues.isEmpty()) {
            return DataResult.error(() -> "research-tree group ranks must be a list: "
                    + errorMessage(rankValuesResult));
        }

        List<List<ResourceLocation>> decodedRanks = new ArrayList<>();
        int memberCount = 0;
        Iterator<T> ranks = rankValues.orElseThrow().iterator();
        while (ranks.hasNext()) {
            if (decodedRanks.size() >= MAX_RANKS) {
                return DataResult.error(() ->
                        "research-tree group cannot contain more than " + MAX_RANKS + " ranks");
            }
            T rankInput = ranks.next();
            DataResult<java.util.stream.Stream<T>> memberValuesResult = ops.getStream(rankInput);
            Optional<java.util.stream.Stream<T>> memberValues = memberValuesResult.result();
            if (memberValues.isEmpty()) {
                return DataResult.error(() -> "research-tree group rank must be a list: "
                        + errorMessage(memberValuesResult));
            }

            List<ResourceLocation> decodedRank = new ArrayList<>();
            Iterator<T> members = memberValues.orElseThrow().iterator();
            while (members.hasNext()) {
                if (memberCount >= MAX_MEMBERS) {
                    return DataResult.error(() ->
                            "research-tree group cannot contain more than " + MAX_MEMBERS + " members");
                }
                DataResult<ResourceLocation> memberResult =
                        BlueprintResearchCodecs.RESOURCE_LOCATION.parse(ops, members.next());
                Optional<ResourceLocation> member = memberResult.result();
                if (member.isEmpty()) {
                    return DataResult.error(() -> "invalid research-tree group member: "
                            + errorMessage(memberResult));
                }
                decodedRank.add(member.orElseThrow());
                memberCount++;
            }
            decodedRanks.add(List.copyOf(decodedRank));
        }
        if (!validRankShape(decodedRanks)) {
            return DataResult.error(() -> "research-tree group may use empty alignment ranks, "
                    + "but its final rank must contain a member");
        }
        return DataResult.success(Pair.of(List.copyOf(decodedRanks), ops.empty()));
    }

    private static String errorMessage(DataResult<?> result) {
        return result.error()
                .map(DataResult.PartialResult::message)
                .orElse("unknown codec error");
    }

    private static DataResult<ResearchTreeGroupDefinition> decodeDefinition(RawDefinition raw) {
        try {
            return DataResult.success(new ResearchTreeGroupDefinition(
                    raw.format(),
                    raw.profile(),
                    raw.title(),
                    raw.translationKey(),
                    raw.icon(),
                    raw.order(),
                    raw.includeInOverview(),
                    raw.ranks()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static DataResult<RawDefinition> encodeDefinition(ResearchTreeGroupDefinition definition) {
        try {
            definition.validateForSnapshot();
            return DataResult.success(new RawDefinition(
                    definition.format(),
                    definition.profile(),
                    definition.title(),
                    definition.translationKey(),
                    definition.icon(),
                    definition.order(),
                    definition.includeInOverview(),
                    definition.ranks()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(exception::getMessage);
        }
    }

    private static void validateProgrammatic(
            ResourceLocation profile,
            String title,
            Optional<String> translationKey,
            ResourceLocation icon,
            int order,
            List<List<ResourceLocation>> ranks) {
        if (profile.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                || icon.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("research-tree group contains an oversized resource ID");
        }
        if (!validTitle(title)) {
            throw new IllegalArgumentException("invalid research-tree group title");
        }
        if (translationKey.isPresent() && !validTranslationKey(translationKey.orElseThrow())) {
            throw new IllegalArgumentException("invalid research-tree group translation key");
        }
        if (order < 0 || order > MAX_ORDER) {
            throw new IllegalArgumentException("research-tree group order is outside the supported range");
        }
        validateRankContainerBounds(ranks);
        Set<ResourceLocation> unique = new LinkedHashSet<>();
        for (List<ResourceLocation> rank : ranks) {
            for (ResourceLocation member : rank) {
                if (member == null
                        || member.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                    throw new IllegalArgumentException("research-tree group contains an invalid member ID");
                }
                if (!unique.add(member)) {
                    throw new IllegalArgumentException(
                            "research-tree group contains duplicate member " + member);
                }
            }
        }
        if (!unique.contains(icon)) {
            throw new IllegalArgumentException("research-tree group icon must identify one of its members");
        }
    }

    private static void validateRankContainerBounds(List<List<ResourceLocation>> ranks) {
        if (ranks == null || ranks.isEmpty() || ranks.size() > MAX_RANKS) {
            throw new IllegalArgumentException(
                    "research-tree group must contain between 1 and " + MAX_RANKS + " ranks");
        }
        long memberCount = 0;
        for (List<ResourceLocation> rank : ranks) {
            if (rank == null) {
                throw new IllegalArgumentException(
                        "research-tree group ranks cannot be null");
            }
            memberCount += rank.size();
            if (memberCount > MAX_MEMBERS) {
                throw new IllegalArgumentException(
                        "research-tree group cannot contain more than " + MAX_MEMBERS + " members");
            }
        }
        if (!validRankShape(ranks)) {
            throw new IllegalArgumentException(
                    "research-tree group may use empty alignment ranks but must end with a member");
        }
    }

    /**
     * Empty ranks are global-depth alignment bands for dependencies that enter
     * or leave another group. A trailing empty rank is rejected because it adds
     * no semantic placement information.
     */
    private static boolean validRankShape(List<List<ResourceLocation>> ranks) {
        for (List<ResourceLocation> rank : ranks) {
            if (rank == null) {
                return false;
            }
        }
        return !ranks.isEmpty() && !ranks.get(ranks.size() - 1).isEmpty();
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

    private record RawDefinition(
            int format,
            ResourceLocation profile,
            String title,
            Optional<String> translationKey,
            ResourceLocation icon,
            int order,
            Optional<Boolean> includeInOverview,
            List<List<ResourceLocation>> ranks) {
    }
}
