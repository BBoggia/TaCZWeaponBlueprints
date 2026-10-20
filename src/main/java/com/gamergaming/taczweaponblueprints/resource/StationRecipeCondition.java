package com.gamergaming.taczweaponblueprints.resource;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.conditions.ICondition;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Filters bundled recipes on data reload; existing blocks and items remain registered. */
public record StationRecipeCondition(boolean analyzer, int tier) implements ICondition {
    public static final MapCodec<StationRecipeCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.BOOL.fieldOf("analyzer").forGetter(StationRecipeCondition::analyzer),
            Codec.intRange(1, 3).fieldOf("tier").forGetter(StationRecipeCondition::tier)
    ).apply(instance, StationRecipeCondition::new));
    private static final DeferredRegister<MapCodec<? extends ICondition>> CONDITIONS =
            DeferredRegister.create(NeoForgeRegistries.Keys.CONDITION_CODECS, TaCZWeaponBlueprints.MODID);
    static { CONDITIONS.register("station_setup", () -> CODEC); }

    public StationRecipeCondition {
        if (tier < 1 || tier > 3) throw new IllegalArgumentException("Invalid station recipe tier");
    }
    @Override public MapCodec<StationRecipeCondition> codec() { return CODEC; }
    @Override public boolean test(IContext context) { return ModConfigs.BLUEPRINT.stationSetup().recipeEnabled(analyzer, tier); }
    public static void register(IEventBus bus) { CONDITIONS.register(bus); }
}
