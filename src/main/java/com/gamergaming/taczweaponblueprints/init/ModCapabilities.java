package com.gamergaming.taczweaponblueprints.init;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;

public class ModCapabilities {
    public static final Capability<IPlayerRecipeData> PLAYER_RECIPE_DATA = CapabilityManager.get(new CapabilityToken<>(){});
}
