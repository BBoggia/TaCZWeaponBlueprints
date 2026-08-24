package com.gamergaming.taczweaponblueprints.resource.research;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

import net.minecraft.resources.ResourceLocation;

final class BlueprintResearchCodecs {
    static final Codec<ResourceLocation> RESOURCE_LOCATION = ResourceLocation.CODEC.flatXmap(
            BlueprintResearchCodecs::validateResourceLocation,
            BlueprintResearchCodecs::validateResourceLocation);

    static final Codec<Integer> POINTS = Codec.INT.flatXmap(
            BlueprintResearchCodecs::validatePoints,
            BlueprintResearchCodecs::validatePoints);

    private BlueprintResearchCodecs() {
    }

    private static DataResult<ResourceLocation> validateResourceLocation(ResourceLocation value) {
        return value != null && value.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                ? DataResult.success(value)
                : DataResult.error(() -> "resource ID exceeds "
                        + PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH + " characters");
    }

    private static DataResult<Integer> validatePoints(int value) {
        return value >= 0 && value <= PlayerProgressionLimits.MAX_RESEARCH_POINTS
                ? DataResult.success(value)
                : DataResult.error(() -> "point value must be between zero and "
                        + PlayerProgressionLimits.MAX_RESEARCH_POINTS);
    }
}
