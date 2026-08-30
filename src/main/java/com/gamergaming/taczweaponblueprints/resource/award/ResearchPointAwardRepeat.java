package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.Locale;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.resource.loot.StrictOptionalFieldCodec;
import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record ResearchPointAwardRepeat(
        Type type,
        Optional<ResourceLocation> claimId,
        Scope scope,
        Optional<Long> cooldownTicks,
        Optional<Long> windowTicks,
        Optional<Integer> maximumAwards,
        Optional<Integer> maximumPoints) {
    private static final Codec<ResearchPointAwardRepeat> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    Type.CODEC.fieldOf("type").forGetter(ResearchPointAwardRepeat::type),
                    new StrictOptionalFieldCodec<>("claim_id", ResearchPointAwardCodecs.RESOURCE_LOCATION)
                            .forGetter(ResearchPointAwardRepeat::claimId),
                    new StrictOptionalFieldCodec<>("scope", Scope.CODEC)
                            .xmap(value -> value.orElse(Scope.DEFINITION), value ->
                                    value == Scope.DEFINITION ? Optional.empty() : Optional.of(value))
                            .forGetter(ResearchPointAwardRepeat::scope),
                    new StrictOptionalFieldCodec<>("cooldown_ticks", ResearchPointAwardCodecs.POSITIVE_TICKS)
                            .forGetter(ResearchPointAwardRepeat::cooldownTicks),
                    new StrictOptionalFieldCodec<>("window_ticks", ResearchPointAwardCodecs.POSITIVE_TICKS)
                            .forGetter(ResearchPointAwardRepeat::windowTicks),
                    new StrictOptionalFieldCodec<>("max_awards", ResearchPointAwardCodecs.POSITIVE_INT)
                            .forGetter(ResearchPointAwardRepeat::maximumAwards),
                    new StrictOptionalFieldCodec<>("max_points", ResearchPointAwardCodecs.POSITIVE_POINTS)
                            .forGetter(ResearchPointAwardRepeat::maximumPoints))
                    .apply(instance, ResearchPointAwardRepeat::new));

    public static final Codec<ResearchPointAwardRepeat> CODEC = StrictRecordCodec.wrap(
            "Research Point award repeat policy",
            RAW_CODEC.flatXmap(ResearchPointAwardRepeat::validate, ResearchPointAwardRepeat::validate),
            "type",
            "claim_id",
            "scope",
            "cooldown_ticks",
            "window_ticks",
            "max_awards",
            "max_points");

    public ResearchPointAwardRepeat {
        if (type == null || scope == null) {
            throw new IllegalArgumentException("invalid Research Point repeat policy");
        }
        claimId = claimId == null ? Optional.empty() : claimId;
        cooldownTicks = cooldownTicks == null ? Optional.empty() : cooldownTicks;
        windowTicks = windowTicks == null ? Optional.empty() : windowTicks;
        maximumAwards = maximumAwards == null ? Optional.empty() : maximumAwards;
        maximumPoints = maximumPoints == null ? Optional.empty() : maximumPoints;
    }

    public boolean finite() {
        return type == Type.ONCE || type == Type.ONCE_PER_TARGET;
    }

    private static DataResult<ResearchPointAwardRepeat> validate(ResearchPointAwardRepeat repeat) {
        boolean claimAllowed = repeat.type() == Type.ONCE || repeat.type() == Type.ONCE_PER_TARGET;
        if (!claimAllowed && repeat.claimId().isPresent()) {
            return DataResult.error(() -> "claim_id is valid only for once and once_per_target");
        }
        if (repeat.type() != Type.COOLDOWN && repeat.cooldownTicks().isPresent()) {
            return DataResult.error(() -> "cooldown_ticks requires cooldown repeat type");
        }
        if (repeat.type() == Type.COOLDOWN && repeat.cooldownTicks().isEmpty()) {
            return DataResult.error(() -> "cooldown repeat type requires cooldown_ticks");
        }
        boolean hasWindowField = repeat.windowTicks().isPresent()
                || repeat.maximumAwards().isPresent()
                || repeat.maximumPoints().isPresent();
        if (repeat.type() != Type.WINDOWED && hasWindowField) {
            return DataResult.error(() -> "window fields require windowed repeat type");
        }
        if (repeat.type() == Type.WINDOWED
                && (repeat.windowTicks().isEmpty()
                        || repeat.maximumAwards().isEmpty()
                        || repeat.maximumPoints().isEmpty())) {
            return DataResult.error(() ->
                    "windowed repeat type requires window_ticks, max_awards, and max_points");
        }
        if ((repeat.type() == Type.ONCE
                || repeat.type() == Type.ONCE_PER_TARGET
                || repeat.type() == Type.UNLIMITED)
                && repeat.scope() != Scope.DEFINITION) {
            return DataResult.error(() -> "scope is valid only for cooldown and windowed repeat types");
        }
        return DataResult.success(repeat);
    }

    public enum Type {
        ONCE,
        ONCE_PER_TARGET,
        COOLDOWN,
        WINDOWED,
        UNLIMITED;

        public static final Codec<Type> CODEC = Codec.STRING.flatXmap(
                Type::parse,
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));

        private static DataResult<Type> parse(String value) {
            if (value != null) {
                try {
                    return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return DataResult.error(() -> "unknown Research Point repeat policy " + value);
        }
    }

    public enum Scope {
        DEFINITION,
        TARGET;

        public static final Codec<Scope> CODEC = Codec.STRING.flatXmap(
                Scope::parse,
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));

        private static DataResult<Scope> parse(String value) {
            if (value != null) {
                try {
                    return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return DataResult.error(() -> "unknown Research Point repeat scope " + value);
        }
    }
}
