package com.gamergaming.taczweaponblueprints.resource.loot;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record BlueprintLootRule(
        int format,
        boolean enabled,
        ResourceLocation pool,
        List<ResourceLocation> lootTables,
        Optional<Float> chance,
        Optional<BlueprintLootRolls> rolls,
        Optional<BlueprintLootTableSelector> lootTableSelector,
        Optional<BlueprintLootRulePredicate> predicate) {
    public static final int CURRENT_FORMAT = 2;
    public static final int MAX_LOOT_TABLES = 4096;

    private static final Codec<Integer> FORMAT_CODEC = Codec.INT.flatXmap(
            BlueprintLootRule::validateFormat,
            BlueprintLootRule::validateFormat);

    private static final Codec<Float> CHANCE_CODEC = Codec.FLOAT.flatXmap(
            BlueprintLootRule::validateChance,
            BlueprintLootRule::validateChance);

    private static final Codec<BlueprintLootRule> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    FORMAT_CODEC.fieldOf("format").forGetter(BlueprintLootRule::format),
                    new StrictOptionalFieldCodec<>("enabled", Codec.BOOL)
                            .xmap(value -> value.orElse(true), Optional::of)
                            .forGetter(BlueprintLootRule::enabled),
                    ResourceLocation.CODEC.fieldOf("pool").forGetter(BlueprintLootRule::pool),
                    ResourceLocation.CODEC.listOf().fieldOf("loot_tables").forGetter(BlueprintLootRule::lootTables),
                    new StrictOptionalFieldCodec<>("chance", CHANCE_CODEC).forGetter(BlueprintLootRule::chance),
                    new StrictOptionalFieldCodec<>("rolls", BlueprintLootRolls.CODEC).forGetter(BlueprintLootRule::rolls),
                    new StrictOptionalFieldCodec<>("loot_table_selector", BlueprintLootTableSelector.CODEC)
                            .forGetter(BlueprintLootRule::lootTableSelector),
                    new StrictOptionalFieldCodec<>("predicate", BlueprintLootRulePredicate.CODEC)
                            .forGetter(BlueprintLootRule::predicate))
                    .apply(instance, BlueprintLootRule::new));

    public static final Codec<BlueprintLootRule> CODEC = StrictRecordCodec.wrap(
            "blueprint loot rule",
            RAW_CODEC.flatXmap(BlueprintLootRule::validateRule, BlueprintLootRule::validateRule),
            "format",
            "enabled",
            "pool",
            "loot_tables",
            "chance",
            "rolls",
            "loot_table_selector",
            "predicate");

    public BlueprintLootRule {
        if (pool == null) {
            throw new IllegalArgumentException("pool cannot be null");
        }
        lootTables = lootTables == null
                ? List.of()
                : List.copyOf(new LinkedHashSet<>(lootTables));
        chance = chance == null ? Optional.empty() : chance;
        rolls = rolls == null ? Optional.empty() : rolls;
        lootTableSelector = lootTableSelector == null ? Optional.empty() : lootTableSelector;
        predicate = predicate == null ? Optional.empty() : predicate;
    }

    public BlueprintLootRule(
            int format,
            boolean enabled,
            ResourceLocation pool,
            List<ResourceLocation> lootTables,
            Optional<Float> chance,
            Optional<BlueprintLootRolls> rolls) {
        this(format, enabled, pool, lootTables, chance, rolls, Optional.empty(), Optional.empty());
    }

    private static DataResult<Integer> validateFormat(int value) {
        return value >= 1 && value <= CURRENT_FORMAT
                ? DataResult.success(value)
                : DataResult.error(() -> "unsupported blueprint loot-rule format " + value);
    }

    private static DataResult<Float> validateChance(float value) {
        return Float.isFinite(value) && value >= 0.0f && value <= 1.0f
                ? DataResult.success(value)
                : DataResult.error(() -> "chance must be finite and between zero and one");
    }

    private static DataResult<BlueprintLootRule> validateRule(BlueprintLootRule rule) {
        if (rule.lootTables().size() > MAX_LOOT_TABLES) {
            return DataResult.error(() -> "blueprint loot rule cannot target more than "
                    + MAX_LOOT_TABLES + " loot tables");
        }
        if (rule.format() == 1 && (rule.lootTableSelector().isPresent() || rule.predicate().isPresent())) {
            return DataResult.error(() -> "format-1 blueprint loot rules cannot use selectors or predicates");
        }
        if (rule.enabled() && rule.lootTables().isEmpty() && rule.lootTableSelector().isEmpty()) {
            return DataResult.error(() -> "enabled blueprint loot rule must target a table or table selector");
        }
        return DataResult.success(rule);
    }
}
