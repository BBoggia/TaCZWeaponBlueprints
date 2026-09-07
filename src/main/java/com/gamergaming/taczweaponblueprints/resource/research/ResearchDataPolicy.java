package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

/** A single input owner. Loading policies does not enable the random workstation action. */
public record ResearchDataPolicy(int format, boolean enabled, int priority, Input input,
        Optional<ResourceLocation> profile, Behavior behavior, AnalyzerTier minimumAnalyzerTier,
        BlueprintResearchCost cost, Pool pool, Optional<Boolean> allowLearned,
        Optional<Boolean> allowUndiscovered, int progressBonus, AnalysisRewardClass rewardClass,
        Optional<ResourceLocation> reconstruction, Map<AnalyzerTier, StationRow> rows) {
    public enum Behavior implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
        ANALYSIS, LEGACY_CURRENCY, DISABLED;
        @Override public String prefix() { return "config.taczweaponblueprints.blueprint.researchDataBehavior"; }
    }
    public static final int MAX_TERMS = 256;
    static final Codec<ResourceLocation> ID = Codec.STRING.flatXmap(value -> {
        var id = ResourceLocation.tryParse(value);
        return id != null && value.contains(":") && value.equals(id.toString()) && value.length() <= 256
                ? com.mojang.serialization.DataResult.success(id)
                : com.mojang.serialization.DataResult.error(() -> "expected a canonical namespace:path ID");
    }, value -> com.mojang.serialization.DataResult.success(value.toString()));
    static final Codec<Boolean> BOOL = AnalyzerOptionalFieldCodec.BOOLEAN;
    static <T> MapCodec<Optional<T>> optional(String name, Codec<T> codec) { return new AnalyzerOptionalFieldCodec<>(name, codec); }
    static <T> MapCodec<T> defaulted(String name, Codec<T> codec, T value) {
        return optional(name, codec).xmap(v -> v.orElse(value), Optional::of);
    }
    private static final Codec<ResearchDataPolicy> RAW = RecordCodecBuilder.create(i -> i.group(
            AnalyzerOutcomeTable.integer(1, 1).fieldOf("format").forGetter(ResearchDataPolicy::format),
            defaulted("enabled", BOOL, true).forGetter(ResearchDataPolicy::enabled),
            defaulted("priority", AnalyzerOutcomeTable.integer(-1_000_000, 1_000_000), 0).forGetter(ResearchDataPolicy::priority),
            Input.CODEC.fieldOf("input").forGetter(ResearchDataPolicy::input),
            optional("profile", ID).forGetter(ResearchDataPolicy::profile),
            BlueprintAnalyzerPolicy.enumCodec(Behavior.class).fieldOf("behavior").forGetter(ResearchDataPolicy::behavior),
            defaulted("minimum_analyzer_tier", AnalyzerTier.CODEC, AnalyzerTier.TIER_1).forGetter(ResearchDataPolicy::minimumAnalyzerTier),
            defaulted("cost", BlueprintResearchCost.CODEC, new BlueprintResearchCost(0, List.of())).forGetter(ResearchDataPolicy::cost),
            Pool.CODEC.fieldOf("pool").forGetter(ResearchDataPolicy::pool),
            optional("allow_learned", BOOL).forGetter(ResearchDataPolicy::allowLearned),
            optional("allow_undiscovered", BOOL).forGetter(ResearchDataPolicy::allowUndiscovered),
            defaulted("progress_bonus", AnalyzerOutcomeTable.integer(0, 1000), 0).forGetter(ResearchDataPolicy::progressBonus),
            defaulted("reward_class", BlueprintAnalyzerPolicy.enumCodec(AnalysisRewardClass.class), AnalysisRewardClass.PROTECTED_PREREQUISITES).forGetter(ResearchDataPolicy::rewardClass),
            optional("reconstruction", ID).forGetter(ResearchDataPolicy::reconstruction),
            defaulted("rows", Codec.unboundedMap(AnalyzerTier.CODEC, StationRow.CODEC), Map.<AnalyzerTier, StationRow>of()).forGetter(ResearchDataPolicy::rows)
    ).apply(i, ResearchDataPolicy::new));
    private static final Codec<ResearchDataPolicy> STRICT = StrictRecordCodec.wrap("Research Data policy", RAW,
            "format", "enabled", "priority", "input", "profile", "behavior", "minimum_analyzer_tier", "cost", "pool",
            "allow_learned", "allow_undiscovered", "progress_bonus", "reward_class", "reconstruction", "rows");
    public static final Codec<ResearchDataPolicy> CODEC = Codec.of(STRICT, new com.mojang.serialization.Decoder<>() {
        @Override public <T> com.mojang.serialization.DataResult<com.mojang.datafixers.util.Pair<ResearchDataPolicy, T>> decode(
                com.mojang.serialization.DynamicOps<T> ops, T input) {
            try {
                if (input instanceof com.google.gson.JsonElement json) ResearchDataJson.validateIds(json, "");
                return AnalyzerAttemptCodec.inspect(ops, input, 0).flatMap(ignored -> STRICT.decode(ops, input));
            } catch (IllegalArgumentException error) {
                return com.mojang.serialization.DataResult.error(error::getMessage);
            }
        }
    });
    public ResearchDataPolicy {
        Objects.requireNonNull(input); Objects.requireNonNull(profile); Objects.requireNonNull(behavior);
        Objects.requireNonNull(minimumAnalyzerTier); Objects.requireNonNull(cost); Objects.requireNonNull(pool);
        Objects.requireNonNull(allowLearned); Objects.requireNonNull(allowUndiscovered);
        Objects.requireNonNull(rewardClass); Objects.requireNonNull(reconstruction);
        rows = Map.copyOf(rows);
        if (format != 1 || Math.abs((long) priority) > 1_000_000 || progressBonus < 0 || progressBonus > 1000)
            throw new IllegalArgumentException("invalid Research Data format, priority, or progress_bonus");
        if (behavior == Behavior.ANALYSIS) {
            if (!rows.keySet().equals(EnumSet.allOf(AnalyzerTier.class))) throw new IllegalArgumentException("analysis requires all three station rows");
            if (rows.values().stream().flatMap(StationRow::outcomes).anyMatch(v -> v.fragmentWeight() > 0) && reconstruction.isEmpty())
                throw new IllegalArgumentException("fragment outcomes require a shared reconstruction reference");
        } else if (!rows.isEmpty() || reconstruction.isPresent()) {
            throw new IllegalArgumentException("only analysis behavior may declare rows or reconstruction");
        }
    }
    public AnalyzerOutcomeTable outcome(AnalyzerTier station, AnalyzerTier target) { return rows.get(station).forTarget(target); }

    public record Input(Optional<ResourceLocation> item, Optional<ResourceLocation> tag) {
        private static final Codec<Input> RAW = RecordCodecBuilder.create(i -> i.group(
                optional("item", ID).forGetter(Input::item), optional("tag", ID).forGetter(Input::tag)).apply(i, Input::new));
        public static final Codec<Input> CODEC = StrictRecordCodec.wrap("Research Data input", RAW, "item", "tag");
        public Input { if (item == null || tag == null || item.isPresent() == tag.isPresent()) throw new IllegalArgumentException("input requires exactly one item or tag"); }
        public int specificity() { return item.isPresent() ? 2 : 1; }
        public boolean matches(ResourceLocation input, java.util.function.BiPredicate<ResourceLocation, ResourceLocation> inTag) {
            return item.map(input::equals).orElseGet(() -> inTag.test(input, tag.orElseThrow()));
        }
    }
    public record Pool(Map<AnalyzerTier, Integer> tierWeights, Optional<BlueprintResearchTarget> include,
            Optional<BlueprintResearchTarget> exclude, Map<ResourceLocation, Integer> weaponWeights,
            Map<ResourceLocation, AnalyzerTier> tierOverrides) {
        private static final Codec<Pool> RAW = RecordCodecBuilder.create(i -> i.group(
                Codec.unboundedMap(AnalyzerTier.CODEC, AnalyzerOutcomeTable.integer(0, AnalyzerOutcomeTable.MAX_WEIGHT)).fieldOf("tier_weights").forGetter(Pool::tierWeights),
                optional("include", BlueprintResearchTarget.CODEC).forGetter(Pool::include),
                optional("exclude", BlueprintResearchTarget.CODEC).forGetter(Pool::exclude),
                defaulted("weapon_weights", Codec.unboundedMap(ID, AnalyzerOutcomeTable.integer(0, AnalyzerOutcomeTable.MAX_WEIGHT)), Map.<ResourceLocation, Integer>of()).forGetter(Pool::weaponWeights),
                defaulted("tier_overrides", Codec.unboundedMap(ID, AnalyzerTier.CODEC), Map.<ResourceLocation, AnalyzerTier>of()).forGetter(Pool::tierOverrides)
        ).apply(i, Pool::new));
        public static final Codec<Pool> CODEC = StrictRecordCodec.wrap("Research Data pool", RAW, "tier_weights", "include", "exclude", "weapon_weights", "tier_overrides");
        public Pool {
            tierWeights = Map.copyOf(tierWeights); weaponWeights = Map.copyOf(weaponWeights); tierOverrides = Map.copyOf(tierOverrides);
            Objects.requireNonNull(include); Objects.requireNonNull(exclude);
            include.ifPresent(BlueprintResearchTarget::validateForSnapshot); exclude.ifPresent(BlueprintResearchTarget::validateForSnapshot);
            if (tierWeights.isEmpty() || tierWeights.values().stream().anyMatch(v -> v < 0 || v > AnalyzerOutcomeTable.MAX_WEIGHT)
                    || tierWeights.values().stream().mapToLong(Integer::longValue).sum() == 0
                    || weaponWeights.size() > MAX_TERMS || tierOverrides.size() > MAX_TERMS
                    || weaponWeights.values().stream().anyMatch(v -> v < 0 || v > AnalyzerOutcomeTable.MAX_WEIGHT))
                throw new IllegalArgumentException("invalid pool weights or term limit");
        }
    }
    public record StationRow(AnalyzerOutcomeTable defaults, Map<AnalyzerTier, AnalyzerOutcomeTable> targets) {
        private static final Codec<StationRow> RAW = RecordCodecBuilder.create(i -> i.group(
                AnalyzerOutcomeTable.CODEC.fieldOf("default").forGetter(StationRow::defaults),
                defaulted("targets", Codec.unboundedMap(AnalyzerTier.CODEC, AnalyzerOutcomeTable.CODEC), Map.<AnalyzerTier, AnalyzerOutcomeTable>of()).forGetter(StationRow::targets)
        ).apply(i, StationRow::new));
        public static final Codec<StationRow> CODEC = StrictRecordCodec.wrap("Research Data station row", RAW, "default", "targets");
        public StationRow {
            Objects.requireNonNull(defaults); targets = Map.copyOf(targets);
            if (java.util.stream.Stream.concat(java.util.stream.Stream.of(defaults), targets.values().stream()).anyMatch(v -> v.noResultWeight() != 0))
                throw new IllegalArgumentException("Research Data outcomes must produce a blueprint or fragments");
        }
        public AnalyzerOutcomeTable forTarget(AnalyzerTier tier) { return targets.getOrDefault(tier, defaults); }
        public java.util.stream.Stream<AnalyzerOutcomeTable> outcomes() { return java.util.stream.Stream.concat(java.util.stream.Stream.of(defaults), targets.values().stream()); }
    }
}
