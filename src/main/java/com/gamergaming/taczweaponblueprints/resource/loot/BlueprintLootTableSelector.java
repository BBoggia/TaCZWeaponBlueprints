package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintLootTableSelector(List<String> namespaces, List<String> pathPrefixes) {
    public static final int MAX_TERMS = 256;

    private static final Codec<String> NAMESPACE_STRING = Codec.STRING.flatXmap(
            BlueprintLootTableSelector::validateNamespace,
            BlueprintLootTableSelector::validateNamespace);
    private static final Codec<String> PATH_PREFIX_STRING = Codec.STRING.flatXmap(
            BlueprintLootTableSelector::validatePathPrefix,
            BlueprintLootTableSelector::validatePathPrefix);

    private static final Codec<BlueprintLootTableSelector> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    strictList("namespaces", NAMESPACE_STRING).forGetter(BlueprintLootTableSelector::namespaces),
                    strictList("path_prefixes", PATH_PREFIX_STRING).forGetter(BlueprintLootTableSelector::pathPrefixes))
                    .apply(instance, BlueprintLootTableSelector::new));

    public static final Codec<BlueprintLootTableSelector> CODEC = StrictRecordCodec.wrap(
            "blueprint loot-table selector",
            RAW_CODEC.flatXmap(
                    BlueprintLootTableSelector::validateSelector,
                    BlueprintLootTableSelector::validateSelector),
            "namespaces",
            "path_prefixes");

    public BlueprintLootTableSelector {
        namespaces = normalize(namespaces);
        pathPrefixes = normalize(pathPrefixes);
    }

    public boolean matches(ResourceLocation lootTableId) {
        if (lootTableId == null) {
            return false;
        }
        if (!namespaces.isEmpty() && !namespaces.contains(lootTableId.getNamespace())) {
            return false;
        }
        return pathPrefixes.isEmpty()
                || pathPrefixes.stream().anyMatch(prefix -> lootTableId.getPath().startsWith(prefix));
    }

    private static List<String> normalize(List<String> values) {
        if (values == null) {
            return List.of();
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        values.forEach(value -> normalized.add(value.toLowerCase(Locale.ROOT)));
        return List.copyOf(normalized);
    }

    private static DataResult<String> validateNamespace(String value) {
        return value != null && ResourceLocation.tryBuild(value, "value") != null
                ? DataResult.success(value)
                : DataResult.error(() -> "invalid loot-table namespace " + value);
    }

    private static DataResult<String> validatePathPrefix(String value) {
        return value != null && ResourceLocation.tryBuild("test", value) != null
                ? DataResult.success(value)
                : DataResult.error(() -> "invalid loot-table path prefix " + value);
    }

    private static DataResult<BlueprintLootTableSelector> validateSelector(BlueprintLootTableSelector selector) {
        int terms = selector.namespaces().size() + selector.pathPrefixes().size();
        if (terms == 0) {
            return DataResult.error(() -> "loot-table selector must contain a namespace or path prefix");
        }
        return terms <= MAX_TERMS
                ? DataResult.success(selector)
                : DataResult.error(() -> "loot-table selector cannot contain more than " + MAX_TERMS + " terms");
    }

    private static com.mojang.serialization.MapCodec<List<String>> strictList(String name, Codec<String> codec) {
        return new StrictOptionalFieldCodec<>(name, codec.listOf())
                .xmap(value -> value.orElse(List.of()), value -> optionalList(value));
    }

    private static Optional<List<String>> optionalList(List<String> values) {
        return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values);
    }
}
