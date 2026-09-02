package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BlueprintLootPool(
        int format,
        List<BlueprintLootEntry> entries,
        List<BlueprintLootPoolReference> includes,
        List<BlueprintLootTagReference> tags,
        List<BlueprintCatalogSelector> selectors) {
    public static final int CURRENT_FORMAT = 2;
    public static final int MAX_ENTRIES = 4096;
    public static final int MAX_SOURCES = 4096;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            BlueprintLootPool::validateFormat,
            BlueprintLootPool::validateFormat);

    private static final Codec<BlueprintLootPool> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(BlueprintLootPool::format),
                    strictList("entries", BlueprintLootEntry.CODEC).forGetter(BlueprintLootPool::entries),
                    strictList("includes", BlueprintLootPoolReference.CODEC).forGetter(BlueprintLootPool::includes),
                    strictList("tags", BlueprintLootTagReference.CODEC).forGetter(BlueprintLootPool::tags),
                    strictList("selectors", BlueprintCatalogSelector.CODEC).forGetter(BlueprintLootPool::selectors))
                    .apply(instance, BlueprintLootPool::new));

    public static final Codec<BlueprintLootPool> CODEC = StrictRecordCodec.wrap(
            "blueprint loot pool",
            RAW_CODEC.flatXmap(BlueprintLootPool::validatePool, BlueprintLootPool::validatePool),
            "format",
            "entries",
            "includes",
            "tags",
            "selectors");

    public BlueprintLootPool {
        entries = entries == null ? List.of() : List.copyOf(entries);
        includes = includes == null ? List.of() : List.copyOf(includes);
        tags = tags == null ? List.of() : List.copyOf(tags);
        selectors = selectors == null ? List.of() : List.copyOf(selectors);
    }

    public BlueprintLootPool(int format, List<BlueprintLootEntry> entries) {
        this(format, entries, List.of(), List.of(), List.of());
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value >= 1 && value <= CURRENT_FORMAT
                ? DataResult.success(value)
                : DataResult.error(() -> "unsupported blueprint loot-pool format " + value);
    }

    private static DataResult<BlueprintLootPool> validatePool(BlueprintLootPool pool) {
        if (pool.entries().size() > MAX_ENTRIES) {
            return DataResult.error(() -> "blueprint loot pool cannot contain more than " + MAX_ENTRIES + " entries");
        }
        int sourceCount = pool.includes().size() + pool.tags().size() + pool.selectors().size();
        if (sourceCount > MAX_SOURCES) {
            return DataResult.error(() -> "blueprint loot pool cannot contain more than " + MAX_SOURCES
                    + " composed sources");
        }
        if (pool.entries().isEmpty() && sourceCount == 0) {
            return DataResult.error(() -> "blueprint loot pool must contain at least one source");
        }
        if (pool.format() == 1 && sourceCount != 0) {
            return DataResult.error(() -> "format-1 blueprint loot pools cannot use includes, tags, or selectors");
        }
        return DataResult.success(pool);
    }

    private static <T> com.mojang.serialization.MapCodec<List<T>> strictList(String name, Codec<T> elementCodec) {
        return new StrictOptionalFieldCodec<>(name, elementCodec.listOf())
                .xmap(
                        value -> value.orElse(List.of()),
                        value -> value == null || value.isEmpty() ? Optional.empty() : Optional.of(value));
    }
}
