package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.Locale;

import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record ResearchPointAwardReward(int points, Overflow overflow) {
    private static final Codec<ResearchPointAwardReward> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResearchPointAwardCodecs.POSITIVE_POINTS.fieldOf("points")
                            .forGetter(ResearchPointAwardReward::points),
                    Overflow.CODEC.fieldOf("overflow").forGetter(ResearchPointAwardReward::overflow))
                    .apply(instance, ResearchPointAwardReward::new));
    public static final Codec<ResearchPointAwardReward> CODEC = StrictRecordCodec.wrap(
            "Research Point award reward",
            RAW_CODEC,
            "points",
            "overflow");

    public ResearchPointAwardReward {
        if (points <= 0 || overflow == null) {
            throw new IllegalArgumentException("invalid Research Point award reward");
        }
    }

    public enum Overflow {
        CLAMP,
        REQUIRE_FULL;

        public static final Codec<Overflow> CODEC = Codec.STRING.flatXmap(
                Overflow::parse,
                value -> DataResult.success(value.name().toLowerCase(Locale.ROOT)));

        private static DataResult<Overflow> parse(String value) {
            if (value != null) {
                try {
                    return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
                } catch (IllegalArgumentException ignored) {
                }
            }
            return DataResult.error(() -> "unknown Research Point overflow policy " + value);
        }
    }
}
