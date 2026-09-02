package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintCatalogSelector(
        List<String> namespaces,
        List<String> itemTypes,
        List<String> pathPrefixes,
        List<ResourceLocation> exclude,
        List<BlueprintKind> blueprintKinds,
        float weight) {
    public static final int MAX_TERMS = 256;
    public static final int MAX_TERM_LENGTH = 256;

    private static final Codec<String> NON_BLANK_STRING = Codec.STRING.flatXmap(
            BlueprintCatalogSelector::validateString,
            BlueprintCatalogSelector::validateString);
    private static final Codec<String> NAMESPACE_STRING = Codec.STRING.flatXmap(
            BlueprintCatalogSelector::validateNamespace,
            BlueprintCatalogSelector::validateNamespace);
    private static final Codec<String> PATH_PREFIX_STRING = Codec.STRING.flatXmap(
            BlueprintCatalogSelector::validatePathPrefix,
            BlueprintCatalogSelector::validatePathPrefix);
    private static final Codec<Float> WEIGHT_CODEC = Codec.FLOAT.flatXmap(
            BlueprintCatalogSelector::validateWeight,
            BlueprintCatalogSelector::validateWeight);
    private static final Codec<ResourceLocation> BOUNDED_RESOURCE_LOCATION = ResourceLocation.CODEC.flatXmap(
            BlueprintCatalogSelector::validateResourceLocation,
            BlueprintCatalogSelector::validateResourceLocation);

    private static final Codec<BlueprintCatalogSelector> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    strictList("namespaces", NAMESPACE_STRING).forGetter(BlueprintCatalogSelector::namespaces),
                    strictList("item_types", NON_BLANK_STRING).forGetter(BlueprintCatalogSelector::itemTypes),
                    strictList("path_prefixes", PATH_PREFIX_STRING).forGetter(BlueprintCatalogSelector::pathPrefixes),
                    strictList("exclude", BOUNDED_RESOURCE_LOCATION).forGetter(BlueprintCatalogSelector::exclude),
                    strictList("blueprint_kinds", BlueprintKind.CODEC)
                            .forGetter(BlueprintCatalogSelector::blueprintKinds),
                    new StrictOptionalFieldCodec<>("weight", WEIGHT_CODEC)
                            .xmap(
                                    value -> value.orElse(1.0f),
                                    value -> value == 1.0f ? Optional.empty() : Optional.of(value))
                            .forGetter(BlueprintCatalogSelector::weight))
                    .apply(instance, BlueprintCatalogSelector::new));

    private static final Codec<BlueprintCatalogSelector> RAW_RESEARCH_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    strictList("namespaces", NAMESPACE_STRING).forGetter(BlueprintCatalogSelector::namespaces),
                    strictList("item_types", NON_BLANK_STRING).forGetter(BlueprintCatalogSelector::itemTypes),
                    strictList("path_prefixes", PATH_PREFIX_STRING).forGetter(BlueprintCatalogSelector::pathPrefixes),
                    strictList("exclude", BOUNDED_RESOURCE_LOCATION).forGetter(BlueprintCatalogSelector::exclude),
                    strictList("blueprint_kinds", BlueprintKind.CODEC)
                            .forGetter(BlueprintCatalogSelector::blueprintKinds))
                    .apply(instance, (namespaces, itemTypes, pathPrefixes, exclude, blueprintKinds) ->
                            new BlueprintCatalogSelector(
                                    namespaces,
                                    itemTypes,
                                    pathPrefixes,
                                    exclude,
                                    blueprintKinds,
                                    1.0F)));

    public static final Codec<BlueprintCatalogSelector> CODEC = StrictRecordCodec.wrap(
            "blueprint catalog selector",
            RAW_CODEC.flatXmap(BlueprintCatalogSelector::validateSelector, BlueprintCatalogSelector::validateSelector),
            "namespaces",
            "item_types",
            "path_prefixes",
            "exclude",
            "blueprint_kinds",
            "weight");

    public static final Codec<BlueprintCatalogSelector> RESEARCH_CODEC = StrictRecordCodec.wrap(
            "blueprint research catalog selector",
            RAW_RESEARCH_CODEC.flatXmap(
                    BlueprintCatalogSelector::validateSelector,
                    BlueprintCatalogSelector::validateSelector),
            "namespaces",
            "item_types",
            "path_prefixes",
            "exclude",
            "blueprint_kinds");

    /** Backwards-compatible constructor for selectors authored before coarse kinds. */
    public BlueprintCatalogSelector(
            List<String> namespaces,
            List<String> itemTypes,
            List<String> pathPrefixes,
            List<ResourceLocation> exclude,
            float weight) {
        this(namespaces, itemTypes, pathPrefixes, exclude, List.of(), weight);
    }

    public BlueprintCatalogSelector {
        namespaces = normalizeStrings(namespaces);
        itemTypes = normalizeStrings(itemTypes);
        pathPrefixes = normalizeStrings(pathPrefixes);
        if (exclude != null && exclude.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("catalog selector exclusions cannot be null");
        }
        exclude = exclude == null ? List.of() : List.copyOf(new LinkedHashSet<>(exclude));
        if (blueprintKinds != null && blueprintKinds.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("catalog selector blueprint kinds cannot be null");
        }
        blueprintKinds = blueprintKinds == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(blueprintKinds));
        if (!Float.isFinite(weight) || weight <= 0.0f) {
            throw new IllegalArgumentException("selector weight must be finite and greater than zero");
        }
    }

    public boolean matches(ResourceLocation blueprintId, BlueprintData data) {
        if (blueprintId == null || data == null || exclude.contains(blueprintId)) {
            return false;
        }
        if (!namespaces.isEmpty() && !namespaces.contains(blueprintId.getNamespace())) {
            return false;
        }
        if (!itemTypes.isEmpty()) {
            String itemType = data.getItemType();
            if (itemType == null || !itemTypes.contains(itemType.toLowerCase(Locale.ROOT))) {
                return false;
            }
        }
        if (!blueprintKinds.isEmpty() && !blueprintKinds.contains(data.getKind())) {
            return false;
        }
        return pathPrefixes.isEmpty()
                || pathPrefixes.stream().anyMatch(prefix -> blueprintId.getPath().startsWith(prefix));
    }

    public BlueprintCatalogSelector multiplyWeight(float multiplier) {
        double multiplied = (double) weight * multiplier;
        float narrowed = (float) multiplied;
        if (!Double.isFinite(multiplied) || multiplied <= 0.0 || multiplied > Float.MAX_VALUE
                || !Float.isFinite(narrowed) || narrowed <= 0.0f) {
            throw new IllegalArgumentException("catalog selector weight overflow or underflow");
        }
        return new BlueprintCatalogSelector(
                namespaces, itemTypes, pathPrefixes, exclude, blueprintKinds, narrowed);
    }

    public void validateForUse() {
        if (termCount() > MAX_TERMS) {
            throw new IllegalArgumentException("catalog selector cannot contain more than " + MAX_TERMS + " terms");
        }
        if (namespaces.stream().anyMatch(value ->
                value.length() > MAX_TERM_LENGTH || ResourceLocation.tryBuild(value, "value") == null)) {
            throw new IllegalArgumentException("catalog selector contains an invalid namespace");
        }
        if (itemTypes.stream().anyMatch(String::isBlank)) {
            throw new IllegalArgumentException("catalog selector contains a blank item type");
        }
        if (itemTypes.stream().anyMatch(value -> value.length() > MAX_TERM_LENGTH)) {
            throw new IllegalArgumentException("catalog selector contains an oversized item type");
        }
        if (pathPrefixes.stream().anyMatch(value ->
                value.length() > MAX_TERM_LENGTH || ResourceLocation.tryBuild("test", value) == null)) {
            throw new IllegalArgumentException("catalog selector contains an invalid path prefix");
        }
        if (exclude.stream().anyMatch(value -> value.toString().length() > MAX_TERM_LENGTH)) {
            throw new IllegalArgumentException("catalog selector contains an oversized excluded resource ID");
        }
    }

    public int termCount() {
        return termCount(namespaces, itemTypes, pathPrefixes, exclude) + blueprintKinds.size();
    }

    private static List<String> normalizeStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.forEach(value -> {
            if (value == null) {
                throw new IllegalArgumentException("catalog selector terms cannot be null");
            }
            normalized.add(value.toLowerCase(Locale.ROOT));
        });
        return List.copyOf(normalized);
    }

    private static DataResult<String> validateString(String value) {
        return value != null && !value.isBlank() && value.length() <= MAX_TERM_LENGTH
                ? DataResult.success(value)
                : DataResult.error(() -> "selector terms must be non-blank and at most "
                        + MAX_TERM_LENGTH + " characters");
    }

    private static DataResult<String> validateNamespace(String value) {
        return value != null
                && value.length() <= MAX_TERM_LENGTH
                && ResourceLocation.tryBuild(value, "value") != null
                ? DataResult.success(value)
                : DataResult.error(() -> "invalid selector namespace " + value);
    }

    private static DataResult<String> validatePathPrefix(String value) {
        return value != null
                && value.length() <= MAX_TERM_LENGTH
                && ResourceLocation.tryBuild("test", value) != null
                ? DataResult.success(value)
                : DataResult.error(() -> "invalid selector path prefix " + value);
    }

    private static DataResult<Float> validateWeight(float value) {
        return Float.isFinite(value) && value > 0.0f
                ? DataResult.success(value)
                : DataResult.error(() -> "selector weight must be finite and greater than zero");
    }

    private static DataResult<ResourceLocation> validateResourceLocation(ResourceLocation value) {
        return value != null && value.toString().length() <= MAX_TERM_LENGTH
                ? DataResult.success(value)
                : DataResult.error(() -> "selector resource IDs cannot exceed "
                        + MAX_TERM_LENGTH + " characters");
    }

    private static DataResult<BlueprintCatalogSelector> validateSelector(BlueprintCatalogSelector selector) {
        return termCount(
                selector.namespaces(),
                selector.itemTypes(),
                selector.pathPrefixes(),
                selector.exclude()) + selector.blueprintKinds().size() <= MAX_TERMS
                ? DataResult.success(selector)
                : DataResult.error(() -> "catalog selector cannot contain more than " + MAX_TERMS + " terms");
    }

    private static int termCount(
            List<String> namespaces,
            List<String> itemTypes,
            List<String> pathPrefixes,
            List<ResourceLocation> exclude) {
        return namespaces.size() + itemTypes.size() + pathPrefixes.size() + exclude.size();
    }

    private static <T> com.mojang.serialization.MapCodec<List<T>> strictList(String name, Codec<T> elementCodec) {
        return new StrictOptionalFieldCodec<>(name, elementCodec.listOf())
                .xmap(value -> value.orElse(List.of()), value -> optionalList(value));
    }

    private static <T> Optional<List<T>> optionalList(List<T> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values);
    }
}
