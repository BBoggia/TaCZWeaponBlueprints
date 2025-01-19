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

        // Get the blueprint data
        BlueprintData data = BlueprintDataManager.INSTANCE.getBlueprintData(BlueprintItem.getBpId(itemStack));

        // Determine if we're in a GUI context
        boolean isGuiContext = (displayContext == ItemDisplayContext.GUI ||
                                displayContext == ItemDisplayContext.GROUND ||
                                displayContext == ItemDisplayContext.FIXED ||
                                displayContext == ItemDisplayContext.NONE);

        if (isGuiContext) {
            int light = LightTexture.FULL_BRIGHT;
            float baseZLevel = 0.0f;
            float overlayZLevel = baseZLevel - 0.01f; // Bring overlay closer

            // Define scales
            float baseScale = 1.175f; // Slightly increase the size of the base texture
            float overlayScale = 1.0f; // Default overlay scale

            // Position offsets (if needed)
            float xOffset = 0.0f;
            float yOffset = 0.0f;

            // Render the base blueprint texture with increased size
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

                // Get the overlay texture
                ResourceLocation overlayTexture = new ResourceLocation(data.getDisplaySlotKey());

                // Render the overlay with dynamic scaling
                renderTexturedQuad(poseStack, bufferSource, light, overlay, overlayTexture, overlayZLevel, xOffset, yOffset, overlayScale, displayContext);
            }
        } else {
            // In in-world contexts
            int light = packedLight;
            float zLevel = 0.0f;
            float scale = 1.0f;
            float xOffset = 0.0f;
            float yOffset = 0.0f;

            // Render the base blueprint texture
            renderTexturedQuad(poseStack, bufferSource, light, overlay, BLUEPRINT_TEXTURE, zLevel, xOffset, yOffset, scale, displayContext);

            if (data != null) {
                // Get the overlay texture
                ResourceLocation overlayTexture = new ResourceLocation(data.getDisplaySlotKey());

                // Render the overlay at the same Z-level
                renderTexturedQuad(poseStack, bufferSource, light, overlay, overlayTexture, zLevel, xOffset, yOffset, scale, displayContext);
            }
        }
    }
    
    // @Override
    // public void renderByItem(ItemStack itemStack, ItemDisplayContext displayContext, PoseStack poseStack,
    //                          MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
    //     int overlay = OverlayTexture.NO_OVERLAY;

    //     // Get the blueprint data
    //     BlueprintData data = BlueprintDataManager.INSTANCE.getBlueprintData(BlueprintItem.getBpId(itemStack));

    //     // Determine if we're in a GUI context
    //     boolean isGuiContext = (displayContext == ItemDisplayContext.GUI ||
    //                             displayContext == ItemDisplayContext.GROUND ||
    //                             displayContext == ItemDisplayContext.FIXED ||
    //                             displayContext == ItemDisplayContext.NONE);

    //     float scale = 1.0f; 
    //     float xOffset = 0.0f;
    //     float yOffset = 0.0f;

    //     if (isGuiContext) {
    //         int light = LightTexture.FULL_BRIGHT;
    //         float baseZLevel = 0.0f;
    //         float overlayZLevel = baseZLevel - 0.01f; // Bring overlay closer

    //         // Render the base blueprint texture
    //         renderTexturedQuad(poseStack, bufferSource, light, overlay, BLUEPRINT_TEXTURE, baseZLevel, xOffset, yOffset, scale, displayContext);

    //         if (data.getItemType().equals("ammo")) {
    //             // Scale down for ammo blueprints
    //             scale = 0.75f;
    //             xOffset -= 0.25f;
    //             yOffset += 0.25f;
    //         }

    //         if (data != null) {
    //             // Get the gun texture
    //             ResourceLocation gunTexture = new ResourceLocation(data.getDisplaySlotKey());

    //             // Render the overlay
    //             renderTexturedQuad(poseStack, bufferSource, light, overlay, gunTexture, overlayZLevel, xOffset, yOffset, scale, displayContext);
    //         }
    //     } else {
    //         // In in-world contexts
    //         int light = packedLight;

    //         // Render the base blueprint texture
    //         renderTexturedQuad(poseStack, bufferSource, light, overlay, BLUEPRINT_TEXTURE, 0.0f, xOffset, yOffset, scale, displayContext);

    //         if (data != null) {
    //             // Get the gun texture
    //             ResourceLocation gunTexture = new ResourceLocation(data.getDisplaySlotKey());

    //             // Render the overlay at the same Z-level
    //             renderTexturedQuad(poseStack, bufferSource, light, overlay, gunTexture, 0.0f, xOffset, yOffset, scale, displayContext);
    //         }
    //     }
    // }

    // @Override
    // public void renderByItem(ItemStack itemStack, ItemDisplayContext displayContext, PoseStack poseStack,
    //                         MultiBufferSource bufferSource, int packedLight, int packedOverlay) {

    //     // Adjust light level for GUI rendering
    //     int light = packedLight;
    //     if (displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.FIXED
    //         || displayContext == ItemDisplayContext.GROUND || displayContext == ItemDisplayContext.NONE) {
    //         light = 0xF000F0; // Full brightness
    //     }

    //     // Render the base blueprint texture
    //     renderTexturedQuad(poseStack, bufferSource, light, packedOverlay, BLUEPRINT_TEXTURE);

    //     // Get the blueprint data
    //     BlueprintData data = BlueprintDataManager.INSTANCE.getBlueprintData(BlueprintItem.getBpId(itemStack));

    //     if (data != null) {
    //         // Get the gun texture
    //         ResourceLocation gunTexture = getGunTexture(data);

    //         // Render the gun overlay
    //         renderTexturedQuad(poseStack, bufferSource, light, packedOverlay, gunTexture);
    //     }
    // }

    // private ResourceLocation getGunTexture(BlueprintData data) {
    //     String texturePath = data.getRecipeId().toString();
    //     texturePath = texturePath.replace("/", "/slot/");

    //     if (texturePath.contains(":attachments")) {
    //         texturePath = texturePath.replace(":attachments", ":attachment");
    //     }

    //     if (texturePath.contains("qbz191")) {
    //         texturePath = texturePath.replace("qbz191_renewed", "qbz_191");
    //     } else if (texturePath.contains("x95r")) {
    //         texturePath = texturePath.replace("x95r", "x95r_sand");
    //     } else if (texturePath.contains("type_95_longbow")) {
    //         texturePath = texturePath.replace("type_95_longbow", "qjb_95_longbow");
    //     }else if (texturePath.contains("m320")) {
    //         texturePath = texturePath.replace("m320", "m320_slot");
    //     } else if (texturePath.contains("tacz:gun/slot/hk416d")) {
    //         texturePath = texturePath.replace("tacz:gun/slot/hk416d", "tacz:gun/slot/hk416d_slot");
    //     } else if (texturePath.contains("hk:gun/slot/hk416a")) {
    //         texturePath = texturePath.replace("hk:gun/slot/hk416a", "hk:gun/slot/hk416_slot");
    //     }

    //     return new ResourceLocation(texturePath);
    // }

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
    
        // Apply position offsets before scaling (if needed)
        // If you want offsets to scale with the texture, apply them here
        poseStack.translate(xOffset, yOffset, 0.0f);
    
        // Apply scaling from the origin
        poseStack.scale(scale, scale, scale);
    
        // Compute translation to keep the quad centered
        float deltaX = 0.5f * (1 - scale);
        float deltaY = 0.5f * (1 - scale);
        poseStack.translate(deltaX, deltaY, 0.0f);
    
        // Additional adjustments for specific contexts
        if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
            // Translate slightly to the left and up
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
    
        // Define the four vertices of the quad
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
    
    // private void renderTexturedQuad(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, ResourceLocation texture, float zLevel, float xOffset, float yOffset, float scale, ItemDisplayContext displayContext) {
    //     RenderType renderType;
    //     if ((displayContext == ItemDisplayContext.GUI || displayContext == ItemDisplayContext.GROUND ||
    //         displayContext == ItemDisplayContext.FIXED || displayContext == ItemDisplayContext.NONE)) {

    //         renderType = RenderType.entityTranslucent(texture);
    //     } else {
    //         renderType = RenderType.entityCutout(texture);
    //     }

    //     VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);

    //     poseStack.pushPose();

    //     poseStack.scale(scale, scale, scale);

    //     if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
    //         // Translate slightly to the left and up
    //         poseStack.translate(-0.25f, 0.2f, 0.0f);
    //     }

    //     Matrix4f matrix = poseStack.last().pose();
    //     Matrix3f normalMatrix = poseStack.last().normal();

    //     float minX = 0.0f;
    //     float minY = 0.0f;
    //     float maxX = 1.0f;
    //     float maxY = 1.0f;

    //     float normalX = 0.0F;
    //     float normalY = 0.0F;
    //     float normalZ = -1.0F;

    //     vertexConsumer.vertex(matrix, minX, minY, zLevel)
    //                   .color(255, 255, 255, 255)
    //                   .uv(0.0f, 1.0f)
    //                   .overlayCoords(overlay)
    //                   .uv2(light)
    //                   .normal(normalMatrix, normalX, normalY, normalZ)
    //                   .endVertex();

    //     vertexConsumer.vertex(matrix, maxX, minY, zLevel)
    //                   .color(255, 255, 255, 255)
    //                   .uv(1.0f, 1.0f)
    //                   .overlayCoords(overlay)
    //                   .uv2(light)
    //                   .normal(normalMatrix, normalX, normalY, normalZ)
    //                   .endVertex();

    //     vertexConsumer.vertex(matrix, maxX, maxY, zLevel)
    //                   .color(255, 255, 255, 255)
    //                   .uv(1.0f, 0.0f)
    //                   .overlayCoords(overlay)
    //                   .uv2(light)
    //                   .normal(normalMatrix, normalX, normalY, normalZ)
    //                   .endVertex();

    //     vertexConsumer.vertex(matrix, minX, maxY, zLevel)
    //                   .color(255, 255, 255, 255)
    //                   .uv(0.0f, 0.0f)
    //                   .overlayCoords(overlay)
    //                   .uv2(light)
    //                   .normal(normalMatrix, normalX, normalY, normalZ)
    //                   .endVertex();

    //     poseStack.popPose();
    // }
}