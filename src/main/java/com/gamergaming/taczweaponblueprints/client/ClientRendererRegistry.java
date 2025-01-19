package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.client.renderer.item.BlueprintItemRenderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class ClientRendererRegistry {

    private static BlockEntityWithoutLevelRenderer blueprintItemRenderer;

    public static BlockEntityWithoutLevelRenderer getBlueprintItemRenderer() {
        if (blueprintItemRenderer == null) {
            blueprintItemRenderer = new BlueprintItemRenderer(
                Minecraft.getInstance().getBlockEntityRenderDispatcher(),
                Minecraft.getInstance().getEntityModels()
            );
        }
        return blueprintItemRenderer;
    }
}