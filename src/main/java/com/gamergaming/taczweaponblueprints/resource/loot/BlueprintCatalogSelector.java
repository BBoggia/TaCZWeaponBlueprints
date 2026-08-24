package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintCatalogSelector(
        List<String> namespaces,
        List<String> itemTypes,
        List<String> pathPrefixes,
        List<ResourceLocation> exclude,
        float weight) {
    public static final int MAX_TERMS = 256;

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

    private static final Codec<BlueprintCatalogSelector> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    strictList("namespaces", NAMESPACE_STRING).forGetter(BlueprintCatalogSelector::namespaces),
                    strictList("item_types", NON_BLANK_STRING).forGetter(BlueprintCatalogSelector::itemTypes),
                    strictList("path_prefixes", PATH_PREFIX_STRING).forGetter(BlueprintCatalogSelector::pathPrefixes),
                    strictList("exclude", ResourceLocation.CODEC).forGetter(BlueprintCatalogSelector::exclude),
                    WEIGHT_CODEC.fieldOf("weight").forGetter(BlueprintCatalogSelector::weight))
                    .apply(instance, BlueprintCatalogSelector::new));

    public static final Codec<BlueprintCatalogSelector> CODEC = StrictRecordCodec.wrap(
            "blueprint catalog selector",
            RAW_CODEC.flatXmap(BlueprintCatalogSelector::validateSelector, BlueprintCatalogSelector::validateSelector),
            "namespaces",
            "item_types",
            "path_prefixes",
            "exclude",
            "weight");

    public BlueprintCatalogSelector {
        namespaces = normalizeStrings(namespaces);
        itemTypes = normalizeStrings(itemTypes);
        pathPrefixes = normalizeStrings(pathPrefixes);
        exclude = exclude == null ? List.of() : List.copyOf(new LinkedHashSet<>(exclude));
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
        return new BlueprintCatalogSelector(namespaces, itemTypes, pathPrefixes, exclude, narrowed);
    }

    private static List<String> normalizeStrings(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.forEach(value -> normalized.add(value.toLowerCase(Locale.ROOT)));
        return List.copyOf(normalized);
    }

    private static DataResult<String> validateString(String value) {
        return value != null && !value.isBlank()
                ? DataResult.success(value)
                : DataResult.error(() -> "selector terms cannot be blank");
    }

    private static DataResult<String> validateNamespace(String value) {
        return value != null && ResourceLocation.tryBuild(value, "value") != null
                ? DataResult.success(value)
                : DataResult.error(() -> "invalid selector namespace " + value);
    }

    private static DataResult<String> validatePathPrefix(String value) {
        return value != null && ResourceLocation.tryBuild("test", value) != null
                ? DataResult.success(value)
                : DataResult.error(() -> "invalid selector path prefix " + value);
    }

    private static DataResult<Float> validateWeight(float value) {
        return Float.isFinite(value) && value > 0.0f
                ? DataResult.success(value)
                : DataResult.error(() -> "selector weight must be finite and greater than zero");
    }

    private static DataResult<BlueprintCatalogSelector> validateSelector(BlueprintCatalogSelector selector) {
        int termCount = selector.namespaces().size()
                + selector.itemTypes().size()
                + selector.pathPrefixes().size()
                + selector.exclude().size();
        return termCount <= MAX_TERMS
                ? DataResult.success(selector)
                : DataResult.error(() -> "catalog selector cannot contain more than " + MAX_TERMS + " terms");
    }

    private static <T> com.mojang.serialization.MapCodec<List<T>> strictList(String name, Codec<T> elementCodec) {
        return new StrictOptionalFieldCodec<>(name, elementCodec.listOf())
                .xmap(value -> value.orElse(List.of()), value -> optionalList(value));
    }

    private static <T> Optional<List<T>> optionalList(List<T> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values);
    }
}
