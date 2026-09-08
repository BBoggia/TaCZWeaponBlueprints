package com.gamergaming.taczweaponblueprints.resource;

import com.google.gson.JsonObject;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraftforge.common.crafting.conditions.ICondition;
import net.minecraftforge.common.crafting.conditions.IConditionSerializer;

/** Filters bundled recipes on data reload; existing blocks and items remain registered. */
public record StationRecipeCondition(boolean analyzer, int tier) implements ICondition {
    public static final ResourceLocation ID = TaCZWeaponBlueprints.loc("station_setup");
    public StationRecipeCondition {
        if (tier < 1 || tier > 3) throw new IllegalArgumentException("Invalid station recipe tier");
    }
    @Override public ResourceLocation getID() { return ID; }
    @Override public boolean test(IContext context) { return ModConfigs.BLUEPRINT.stationSetup().recipeEnabled(analyzer, tier); }
    public static final class Serializer implements IConditionSerializer<StationRecipeCondition> {
        @Override public void write(JsonObject json, StationRecipeCondition value) {
            json.addProperty("analyzer", value.analyzer()); json.addProperty("tier", value.tier());
        }
        @Override public StationRecipeCondition read(JsonObject json) {
            return new StationRecipeCondition(GsonHelper.getAsBoolean(json, "analyzer"), GsonHelper.getAsInt(json, "tier"));
        }
        @Override public ResourceLocation getID() { return ID; }
    }
}
