package com.gamergaming.taczweaponblueprints.client.renderer.item;


import com.gamergaming.taczweaponblueprints.client.ClientRendererRegistry;

import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

public class BlueprintItemClientExtensions implements IClientItemExtensions {

    @OnlyIn(Dist.CLIENT)
    @Override
    public BlockEntityWithoutLevelRenderer getCustomRenderer() {
        return ClientRendererRegistry.getBlueprintItemRenderer();
    }
}
