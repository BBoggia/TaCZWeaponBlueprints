package com.gamergaming.taczweaponblueprints.client.renderer.item;

import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.client.resource.index.ClientAmmoIndex;
import com.tacz.guns.client.resource.index.ClientAttachmentIndex;
import com.tacz.guns.client.resource.index.ClientGunIndex;
import com.tacz.guns.resource.CommonAssetsManager;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
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

import java.util.Optional;

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
    public void renderByItem(@NotNull ItemStack itemStack, @NotNull ItemDisplayContext displayContext, @NotNull PoseStack poseStack,
                             @NotNull MultiBufferSource bufferSource, int packedLight, int packedOverlay) {
        int overlay = OverlayTexture.NO_OVERLAY;

        BlueprintData data = BlueprintDataManager.INSTANCE.getBlueprintData(BlueprintItem.getBpId(itemStack));

        if (data == null) {
            TaCZWeaponBlueprints.LOGGER.error("BlueprintData is null for itemStack: {}", itemStack);
            return;
        }

        boolean isGuiContext = (displayContext == ItemDisplayContext.GUI ||
                                displayContext == ItemDisplayContext.GROUND ||
                                displayContext == ItemDisplayContext.FIXED ||
                                displayContext == ItemDisplayContext.NONE);

        String itemCategory = data.getRecipeId().toString().split(":")[1].split("/")[0];

        // Get overlay texture
        ResourceLocation overlayTexture;

        boolean flipOverlay = true;
        boolean rotateOverlay = false;

        switch (itemCategory) {
            case "gun" -> {
                Optional<ClientGunIndex> index = TimelessAPI.getClientGunIndex(new ResourceLocation(data.getBpId()));

                if (index.isPresent()) {
                    overlayTexture = index.get().getDefaultDisplay().getSlotTexture();
                } else {
                    overlayTexture = data.getDisplaySlotKey();
                }
            }
            case "ammo" -> {
                Optional<ClientAmmoIndex> index = TimelessAPI.getClientAmmoIndex(new ResourceLocation(data.getBpId()));

                if (index.isPresent()) {
                    overlayTexture = index.get().getSlotTextureLocation();
                } else {
                    overlayTexture = data.getDisplaySlotKey();
                }
            }
            case "attachments" -> {
                Optional<ClientAttachmentIndex> index = TimelessAPI.getClientAttachmentIndex(new ResourceLocation(data.getBpId()));

                if (index.isPresent()) {
                    overlayTexture = index.get().getSlotTexture();
                } else {
                    overlayTexture = data.getDisplaySlotKey();
                }
            }

            default -> {
                overlayTexture = data.getDisplaySlotKey();
            }
        }

        float overlayScale = 1.0f;

        switch (data.getItemType()) {
            case "pistol":
                overlayScale = 0.375f;
                break;
            case "smg":
                overlayScale = 0.44f;
                break;
            case "rifle":
                overlayScale = 0.46f;
                break;
            case "shotgun":
                overlayScale = 0.42f;
                break;
            case "mg", "sniper":
                overlayScale = 0.45f;
                break;
            case "rpg":
                overlayScale = 0.435f;
                break;
            case "grip":
                overlayScale = 0.33f;
                break;
            case "stock", "muzzle", "ammo":
                overlayScale = 0.35f;
                break;
            case "scope":
                overlayScale = 0.38f;
                break;
            case "extended_mag":
                overlayScale = 0.275f;
                break;
            default:
                break;
        }

        // Render the blueprint item in inventory, GUI, or on the ground
        if (isGuiContext) {
            int light = LightTexture.FULL_BRIGHT;
            float baseZLevel = 0.0f;
            float overlayZLevel = baseZLevel - 0.01f;


            float baseScale = 1.3f;  // 1.175f

            float xOffset = 0.0f;
            float yOffset = 0.0f;


            overlayScale *= 2.0f;

            if (data.getItemType().equals("ammo")) {
                flipOverlay = false;
            }

//            switch (data.getItemType()) {
//                case "smg", "pistol":
//                    overlayScale = 0.97f;
//                    break;
//                case "muzzle", "grip":
//                    overlayScale = 0.92f;
//                    break;
//                case "scope":
//                    overlayScale = 0.83f;
//                    break;
//                case "stock":
//                    overlayScale = 0.85f;
//                    break;
//                case "ammo":
//                    flipOverlay = false;
//                    overlayScale = 0.8f;
//                    break;
//                case "extended_mag":
//                    overlayScale = 0.7f;
//                    break;
//                default:
//                    break;
//            }

            // Render the base blueprint texture with larger size
            renderTexturedQuad(poseStack, bufferSource, light, overlay, BLUEPRINT_TEXTURE, baseZLevel, xOffset, yOffset, baseScale, displayContext, true, false);

            // Render overlay with dynamic scaling
            renderTexturedQuad(poseStack, bufferSource, light, overlay, overlayTexture, overlayZLevel, xOffset, yOffset, overlayScale, displayContext, flipOverlay, rotateOverlay);

        } else { // Render the blueprint item in first-person view when holding
            int light = packedLight;
            float baseZLevel = 0.0f;
            float overlayZLevel = baseZLevel + 0.01f;
            float baseScale = 1.2f;

            float baseXOffset = 0.0f;
            float baseYOffset = 0.0f;

            float overlayXOffset = 0.0f;
            float overlayYOffset = 0.0f;

            flipOverlay = false;

            if (itemCategory.equals("gun")) {
                rotateOverlay = true;
            }

            switch (data.getItemType()) {
                case "pistol":
                    overlayXOffset -= 0.0325f;
                    overlayYOffset += 0.2025f;
                    break;
                case "smg":
                    overlayXOffset -= 0.071f;
                    overlayYOffset += 0.16f;
                    break;
                case "rifle":
                    overlayXOffset -= 0.08f;
                    overlayYOffset += 0.1625f;
                    break;
                case "shotgun":
                    overlayXOffset -= 0.0625f;
                    overlayYOffset += 0.155f;
                    break;
                case "mg":
                    overlayXOffset -= 0.08f;
                    overlayYOffset += 0.155f;
                    break;
                case "sniper":
                    overlayXOffset -= 0.085f;
                    overlayYOffset += 0.155f;
                break;
                case "rpg":
                    overlayXOffset -= 0.07f;
                    overlayYOffset += 0.158f;
                    break;
                case "grip":
                    overlayXOffset -= 0.22f;
                    overlayYOffset += 0.145f;
                    break;
                case "stock":
                    overlayXOffset -= 0.225f;
                    overlayYOffset += 0.16f;
                    break;
                case "muzzle":
                    overlayXOffset -= 0.24f;
                    overlayYOffset += 0.15f;
                    break;
                case "scope":
                    overlayXOffset -= 0.25f;
                    overlayYOffset += 0.16f;
                    break;
                case "extended_mag":
                    overlayXOffset -= 0.275f;
                    overlayYOffset += 0.175f;
                    break;
                case "ammo":
                    flipOverlay = true;
                    overlayXOffset -= 0.24f;
                    overlayYOffset += 0.18f;
                    break;
                default:
                    overlayXOffset -= 0.14f;
                    overlayYOffset -= 0.07f;
                    break;

            }

            // Render base blueprint texture
            renderTexturedQuad(poseStack, bufferSource, light, overlay, BLUEPRINT_TEXTURE, baseZLevel, baseXOffset, baseYOffset, baseScale, displayContext, false, false);

            // Render overlay at same Z-level
            renderTexturedQuad(poseStack, bufferSource, light, overlay, overlayTexture, overlayZLevel, overlayXOffset, overlayYOffset, overlayScale, displayContext, flipOverlay, rotateOverlay);
        }
    }

    private void renderTexturedQuad(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, ResourceLocation texture, float zLevel, float xOffset, float yOffset, float scale, ItemDisplayContext displayContext, boolean flipOverlay, boolean rotateOverlay) {

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

        // Translate to the center of the quad
        poseStack.translate(0.5f, 0.5f, 0.0f);

        if (rotateOverlay) {
            // Rotate overlay texture 90 degrees
            poseStack.mulPose(com.mojang.math.Axis.ZP.rotationDegrees(-75F));
            poseStack.translate(-0.05f, -0.335f, 0.0f);

        }

        // Apply scaling
        poseStack.scale(scale, scale, scale);

        // Translate back to the original position
        poseStack.translate(-0.5f, -0.5f, 0.0f);


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

        // Decide whether to flip horizontally based on flipOverlay
        float u1 = flipOverlay ? 0.0f : 1.0f;
        float u2 = flipOverlay ? 1.0f : 0.0f;

        vertexConsumer.vertex(matrix, minX, minY, zLevel)
                .color(255, 255, 255, 255)
                .uv(u1, 1.0f)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normalMatrix, normalX, normalY, normalZ)
                .endVertex();

        vertexConsumer.vertex(matrix, maxX, minY, zLevel)
                .color(255, 255, 255, 255)
                .uv(u2, 1.0f)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normalMatrix, normalX, normalY, normalZ)
                .endVertex();

        vertexConsumer.vertex(matrix, maxX, maxY, zLevel)
                .color(255, 255, 255, 255)
                .uv(u2, 0.0f)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normalMatrix, normalX, normalY, normalZ)
                .endVertex();

        vertexConsumer.vertex(matrix, minX, maxY, zLevel)
                .color(255, 255, 255, 255)
                .uv(u1, 0.0f)
                .overlayCoords(overlay)
                .uv2(light)
                .normal(normalMatrix, normalX, normalY, normalZ)
                .endVertex();

        poseStack.popPose();
    }

//    private void renderTexturedQuad(PoseStack poseStack, MultiBufferSource bufferSource, int light, int overlay, ResourceLocation texture, float zLevel, float xOffset, float yOffset, float scale, ItemDisplayContext displayContext, boolean flipOverlay) {
//        boolean isGuiContext = (displayContext == ItemDisplayContext.GUI ||
//                                displayContext == ItemDisplayContext.GROUND ||
//                                displayContext == ItemDisplayContext.FIXED ||
//                                displayContext == ItemDisplayContext.NONE);
//
//        RenderType renderType;
//        if (isGuiContext) {
//            renderType = RenderType.entityTranslucent(texture);
//        } else {
//            renderType = RenderType.entityCutout(texture);
//        }
//
//        VertexConsumer vertexConsumer = bufferSource.getBuffer(renderType);
//
//        poseStack.pushPose();
//
//        poseStack.translate(xOffset, yOffset, 0.0f);
//
//        poseStack.scale(scale, scale, scale);
//
//        float deltaX = 0.5f * (1 - scale);
//        float deltaY = 0.5f * (1 - scale);
//        poseStack.translate(deltaX, deltaY, 0.0f);
//
//        if (displayContext == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND) {
//            // Translate slightly left and up
//            poseStack.translate(-0.25f, 0.2f, 0.0f);
//        }
//
//        Matrix4f matrix = poseStack.last().pose();
//        Matrix3f normalMatrix = poseStack.last().normal();
//
//        float minX = 0.0f;
//        float minY = 0.0f;
//        float maxX = 1.0f;
//        float maxY = 1.0f;
//
//        float normalX = 0.0F;
//        float normalY = 0.0F;
//        float normalZ = -1.0F;
//
//        // Decide whether to flip horizontally based on flipOverlay
//        float u1 = flipOverlay ? 0.0f : 1.0f;
//        float u2 = flipOverlay ? 1.0f : 0.0f;
//
//        vertexConsumer.vertex(matrix, minX, minY, zLevel)
//                .color(255, 255, 255, 255)
//                .uv(u1, 1.0f)
//                .overlayCoords(overlay)
//                .uv2(light)
//                .normal(normalMatrix, 0.0f, 0.0f, -1.0f)
//                .endVertex();
//
//        vertexConsumer.vertex(matrix, maxX, minY, zLevel)
//                .color(255, 255, 255, 255)
//                .uv(u2, 1.0f)
//                .overlayCoords(overlay)
//                .uv2(light)
//                .normal(normalMatrix, 0.0f, 0.0f, -1.0f)
//                .endVertex();
//
//        vertexConsumer.vertex(matrix, maxX, maxY, zLevel)
//                .color(255, 255, 255, 255)
//                .uv(u2, 0.0f)
//                .overlayCoords(overlay)
//                .uv2(light)
//                .normal(normalMatrix, 0.0f, 0.0f, -1.0f)
//                .endVertex();
//
//        vertexConsumer.vertex(matrix, minX, maxY, zLevel)
//                .color(255, 255, 255, 255)
//                .uv(u1, 0.0f)
//                .overlayCoords(overlay)
//                .uv2(light)
//                .normal(normalMatrix, 0.0f, 0.0f, -1.0f)
//                .endVertex();
//
//        poseStack.popPose();
//    }
}
