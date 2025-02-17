package com.gamergaming.taczweaponblueprints.client.renderer.item;

import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.geom.EntityModelSet;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public class BlueprintItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static final ResourceLocation BLUEPRINT_TEXTURE = new ResourceLocation("taczweaponblueprints", "textures/item/blueprint_base.png");

    public BlueprintItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public BlueprintItemRenderer(BlockEntityRenderDispatcher dispatcher, EntityModelSet itemRenderer) {
        super(dispatcher, itemRenderer);
    }

    @Override
    public void renderByItem(ItemStack itemStack, ItemDisplayContext displayContext, PoseStack poseStack,
                            MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        int overlay = OverlayTexture.NO_OVERLAY;

        BlueprintData data = BlueprintDataManager.INSTANCE.getBlueprintData(BlueprintItem.getBpId(itemStack));

        boolean isGuiContext = (displayContext == ItemDisplayContext.GUI ||
                                displayContext == ItemDisplayContext.GROUND ||
                                displayContext == ItemDisplayContext.FIXED ||
                                displayContext == ItemDisplayContext.NONE);

        if (isGuiContext) {
            int light = LightTexture.FULL_BRIGHT;
            float baseZLevel = 0.0f;
            float overlayZLevel = baseZLevel - 0.01f; // Bring overlay closer

            float baseScale = 1.175f; // Slightly increase the size of the base texture
            float overlayScale = 1.0f; // Default overlay scale

            float xOffset = 0.0f;
            float yOffset = 0.0f;

            // Render the base blueprint texture with larger size
            renderTexturedQuad(poseStack, bufferSource, light, overlay, BLUEPRINT_TEXTURE, baseZLevel, xOffset, yOffset, baseScale, displayContext);

            if (data != null) {
                switch (data.getItemType()) {
                    case "grip", "smg":
                        overlayScale = 0.97f;
                        break;
                    case "muzzle", "pistol":
                        overlayScale = 0.95f;
                        break;
                    case "stock":
                        overlayScale = 0.93f;
                        break;
                    case "ammo":
                        overlayScale = 0.85f;
                        break;
                    case "extended_mag":
                        overlayScale = 0.75f;
                        break;
                    default:
                        break;
                }

                ResourceLocation overlayTexture = new ResourceLocation(data.getDisplaySlotKey());

                // Render overlay with dynamic scaling
                renderTexturedQuad(poseStack, bufferSource, light, overlay, overlayTexture, overlayZLevel, xOffset, yOffset, overlayScale, displayContext);
            }
        } else {
            // In in-world contexts
            int light = packedLight;
            float zLevel = 0.0f;
            float scale = 1.0f;
            float xOffset = 0.0f;
            float yOffset = 0.0f;

            // Render base blueprint texture
            renderTexturedQuad(poseStack, bufferSource, light, overlay, BLUEPRINT_TEXTURE, zLevel, xOffset, yOffset, scale, displayContext);

            if (data != null) {
                ResourceLocation overlayTexture = new ResourceLocation(data.getDisplaySlotKey());

                // Render overlay at the same Z-level
                renderTexturedQuad(poseStack, bufferSource, light, overlay, overlayTexture, zLevel, xOffset, yOffset, scale, displayContext);
            }
        }
    }

    private void renderTexturedQuad(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, ResourceLocation texture, float zLevel, float xOffset, float yOffset, float scale, ItemDisplayContext displayContext) {
        boolean isGuiContext = (displayContext == ItemDisplayContext.GUI ||
                                displayContext == ItemDisplayContext.GROUND ||
                                displayContext == ItemDisplayContext.FIXED ||
                                displayContext == ItemDisplayContext.NONE);
        
        RenderType renderType;
        if (isGuiContext) {
            renderType = RenderType.entityTranslucent(texture);
        } else {
            renderType = RenderType.entityCutout(texture);
        }
    
        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
    
        poseStack.pushPose();
    
        poseStack.translate(xOffset, yOffset, 0.0f);
    
        // Apply scaling from origin
        poseStack.scale(scale, scale, scale);
    
        // Compute translation to keep quad centered
        float deltaX = 0.5f * (1 - scale);
        float deltaY = 0.5f * (1 - scale);
        poseStack.translate(deltaX, deltaY, 0.0f);
    
        if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            // Translate slightly left and up
            poseStack.translate(-0.25f, 0.2f, 0.0f);
        }
    
        Matrix4f matrix = poseStack.last().pose();
        Matrix3f normalMatrix = poseStack.last().normal();
    
        float minX = 0.0f;
        float minY = 0.0f;
        float maxX = 1.0f;
        float maxY = 1.0f;
    
        float normalX = 0.0F;
        float normalY = 0.0F;
        float normalZ = -1.0F;
    
        vertexConsumer.vertex(matrix, minX, minY, zLevel)
                      .color(255, 255, 255, 255)
                      .uv(1.0f, 1.0f)
                      .overlayCoords(overlay)
                      .uv2(light)
                      .normal(normalMatrix, normalX, normalY, normalZ)
                      .endVertex();
    
        vertexConsumer.vertex(matrix, maxX, minY, zLevel)
                      .color(255, 255, 255, 255)
                      .uv(0.0f, 1.0f)
                      .overlayCoords(overlay)
                      .uv2(light)
                      .normal(normalMatrix, normalX, normalY, normalZ)
                      .endVertex();
    
        vertexConsumer.vertex(matrix, maxX, maxY, zLevel)
                      .color(255, 255, 255, 255)
                      .uv(0.0f, 0.0f)
                      .overlayCoords(overlay)
                      .uv2(light)
                      .normal(normalMatrix, normalX, normalY, normalZ)
                      .endVertex();
    
        vertexConsumer.vertex(matrix, minX, maxY, zLevel)
                      .color(255, 255, 255, 255)
                      .uv(1.0f, 0.0f)
                      .overlayCoords(overlay)
                      .uv2(light)
                      .normal(normalMatrix, normalX, normalY, normalZ)
                      .endVertex();
    
        poseStack.popPose();
    }
}
