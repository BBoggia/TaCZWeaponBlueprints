package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.Collections;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.regex.Pattern;

import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Explainable attachment defaults based on a fixed policy or canonical item type. */
public record BlueprintAttachmentCraftingPolicy(
        Mode mode,
        BlueprintCraftingAccessPolicy fallback,
        Map<String, BlueprintCraftingAccessPolicy> itemTypePolicies) {
    public static final int MAX_ITEM_TYPE_POLICIES = 128;
    public static final int MAX_ITEM_TYPE_LENGTH = 128;
    private static final Pattern ITEM_TYPE_PATTERN = Pattern.compile("[a-z0-9_.-]+");

    public static final BlueprintAttachmentCraftingPolicy DEFAULT = fixed(
            BlueprintCraftingAccessPolicy.TIER_1);
    public static final BlueprintAttachmentCraftingPolicy LEGACY = unrestricted();

    private static final Codec<Mode> MODE_CODEC = Codec.STRING.flatXmap(
            Mode::parse,
            value -> DataResult.success(value.serializedName()));
    private static final Codec<Map<String, BlueprintCraftingAccessPolicy>> ITEM_TYPE_POLICIES_CODEC =
            Codec.unboundedMap(Codec.STRING, BlueprintCraftingAccessPolicy.CODEC);
    private static final Codec<Fields> FIELDS_CODEC = StrictRecordCodec.wrap(
            "attachment crafting policy",
            RecordCodecBuilder.create(instance -> instance.group(
                    MODE_CODEC.fieldOf("mode").forGetter(Fields::mode),
                    BlueprintCraftingAccessPolicy.CODEC.fieldOf("fallback")
                            .forGetter(Fields::fallback),
                    new StrictOptionalFieldCodec<>("item_type_policies", ITEM_TYPE_POLICIES_CODEC)
                            .xmap(value -> value.orElse(Map.of()), value -> value.isEmpty()
                                    ? Optional.empty()
                                    : Optional.of(value))
                            .forGetter(Fields::itemTypePolicies))
                    .apply(instance, Fields::new)),
            "mode",
            "fallback",
            "item_type_policies");
    public static final Codec<BlueprintAttachmentCraftingPolicy> CODEC = FIELDS_CODEC.flatXmap(
            BlueprintAttachmentCraftingPolicy::fromFields,
            value -> DataResult.success(new Fields(
                    value.mode(), value.fallback(), value.itemTypePolicies())));

    public BlueprintAttachmentCraftingPolicy {
        if (mode == null || fallback == null || itemTypePolicies == null
                || itemTypePolicies.size() > MAX_ITEM_TYPE_POLICIES) {
            throw new IllegalArgumentException("attachment crafting policy is invalid or oversized");
        }
        TreeMap<String, BlueprintCraftingAccessPolicy> normalized = new TreeMap<>();
        itemTypePolicies.forEach((itemType, policy) -> {
            if (!isCanonicalItemType(itemType) || policy == null) {
                throw new IllegalArgumentException("attachment item-type policy is invalid");
            }
            normalized.put(itemType, policy);
        });
        itemTypePolicies = Collections.unmodifiableMap(normalized);
        if ((mode == Mode.TYPE_MAPPED) != !itemTypePolicies.isEmpty()) {
            throw new IllegalArgumentException(
                    "item_type_policies must be non-empty only when attachment mode is type_mapped");
        }
        if (mode == Mode.FIXED && fallback.disposition() != BlueprintCraftingDisposition.TIERED
                || mode == Mode.UNRESTRICTED
                        && fallback.disposition() != BlueprintCraftingDisposition.UNRESTRICTED
                || mode == Mode.DISABLED
                        && fallback.disposition() != BlueprintCraftingDisposition.DISABLED) {
            throw new IllegalArgumentException(
                    "attachment fallback disposition must agree with fixed, unrestricted, or disabled mode");
        }
    }

    public static BlueprintAttachmentCraftingPolicy fixed(BlueprintCraftingAccessPolicy policy) {
        return new BlueprintAttachmentCraftingPolicy(Mode.FIXED, policy, Map.of());
    }

    public static BlueprintAttachmentCraftingPolicy unrestricted() {
        return new BlueprintAttachmentCraftingPolicy(
                Mode.UNRESTRICTED, BlueprintCraftingAccessPolicy.UNRESTRICTED, Map.of());
    }

    public static BlueprintAttachmentCraftingPolicy disabled() {
        return new BlueprintAttachmentCraftingPolicy(
                Mode.DISABLED, BlueprintCraftingAccessPolicy.DISABLED, Map.of());
    }

    /**
     * Returns whether a value is a stable canonical attachment type suitable
     * for exact policy lookup. Resolution deliberately does not normalize or
     * infer types from display names or resource paths.
     */
    public static boolean isCanonicalItemType(String itemType) {
        return itemType != null
                && !itemType.isBlank()
                && itemType.length() <= MAX_ITEM_TYPE_LENGTH
                && ITEM_TYPE_PATTERN.matcher(itemType).matches();
    }

    private static DataResult<BlueprintAttachmentCraftingPolicy> fromFields(Fields fields) {
        try {
            return DataResult.success(new BlueprintAttachmentCraftingPolicy(
                    fields.mode(), fields.fallback(), fields.itemTypePolicies()));
        } catch (IllegalArgumentException exception) {
            return DataResult.error(() -> exception.getMessage());
        }
    }

    public enum Mode {
        FIXED,
        TYPE_MAPPED,
        UNRESTRICTED,
        DISABLED;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        private static DataResult<Mode> parse(String value) {
            try {
                return DataResult.success(valueOf(value.trim().toUpperCase(Locale.ROOT)));
            } catch (RuntimeException exception) {
                return DataResult.error(() -> "unknown attachment crafting mode " + value);
            }
        }
    }

    private record Fields(
            Mode mode,
            BlueprintCraftingAccessPolicy fallback,
            Map<String, BlueprintCraftingAccessPolicy> itemTypePolicies) {
    }
}
