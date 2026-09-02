package com.gamergaming.taczweaponblueprints.resource.award;

import com.gamergaming.taczweaponblueprints.resource.loot.StrictRecordCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import net.minecraft.resources.ResourceLocation;

public record ResearchPointAwardBudget(
        ResourceLocation id,
        int maximumAwards,
        int maximumPoints,
        long windowTicks) {
    private static final Codec<ResearchPointAwardBudget> RAW_CODEC = RecordCodecBuilder.create(instance ->
            instance.group(
                    ResearchPointAwardCodecs.RESOURCE_LOCATION.fieldOf("id")
                            .forGetter(ResearchPointAwardBudget::id),
                    ResearchPointAwardCodecs.POSITIVE_INT.fieldOf("max_awards")
                            .forGetter(ResearchPointAwardBudget::maximumAwards),
                    ResearchPointAwardCodecs.POSITIVE_POINTS.fieldOf("max_points")
                            .forGetter(ResearchPointAwardBudget::maximumPoints),
                    ResearchPointAwardCodecs.POSITIVE_TICKS.fieldOf("window_ticks")
                            .forGetter(ResearchPointAwardBudget::windowTicks))
                    .apply(instance, ResearchPointAwardBudget::new));
    public static final Codec<ResearchPointAwardBudget> CODEC = StrictRecordCodec.wrap(
            "Research Point shared budget",
            RAW_CODEC,
            "id",
            "max_awards",
            "max_points",
            "window_ticks");

    public ResearchPointAwardBudget {
        if (id == null || maximumAwards <= 0 || maximumPoints <= 0 || windowTicks <= 0L) {
            throw new IllegalArgumentException("invalid Research Point shared budget");
        }
    }
}
