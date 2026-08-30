package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

final class BlueprintIconRenderingContractTest {
    private static final Path PROJECT_DIRECTORY = Path.of(System.getProperty("user.dir"));

    @Test
    void overlaysAreSmallerAndGunRotationsAreContextSpecific() throws IOException {
        String renderer = read("src/main/java/com/gamergaming/taczweaponblueprints/client/renderer/item/BlueprintItemRenderer.java");

        assertAll(
                () -> assertTrue(renderer.contains("OVERLAY_SCALE_FACTOR = 0.9F")),
                () -> assertTrue(renderer.contains("ICON_GUN_OVERLAY_ROTATION_DEGREES = 65.0F")),
                () -> assertTrue(renderer.contains("HELD_GUN_OVERLAY_ROTATION_DEGREES = -80.0F")),
                () -> assertTrue(renderer.contains("boolean gunOverlay = itemCategory.equals(\"gun\")")),
                () -> assertTrue(renderer.contains("FIRST_PERSON_LEFT_HAND, FIRST_PERSON_RIGHT_HAND")),
                () -> assertTrue(renderer.contains("THIRD_PERSON_LEFT_HAND, THIRD_PERSON_RIGHT_HAND -> HELD_GUN_OVERLAY_ROTATION_DEGREES")),
                () -> assertTrue(renderer.contains("default -> ICON_GUN_OVERLAY_ROTATION_DEGREES")),
                () -> assertTrue(renderer.contains("if (gunOverlay) {\n            overlayScale *= OVERLAY_SCALE_FACTOR;")),
                () -> assertFalse(renderer.contains("GUN_OVERLAY_ROTATION_DEGREES = 70.0F")));
    }

    @Test
    void heldGunAdjustmentIsIsolatedFromLegacyAmmoAndAttachmentPlacement() throws IOException {
        String renderer = read("src/main/java/com/gamergaming/taczweaponblueprints/client/renderer/item/BlueprintItemRenderer.java");
        int gunOffset = renderer.indexOf("applyGunDisplayContextOffset(poseStack, displayContext, alignRotatedHeldOverlay);");
        int layerOffset = renderer.indexOf("poseStack.translate(xOffset, yOffset, 0.0f);");
        int layerRotation = renderer.indexOf("Axis.ZP.rotationDegrees(rotationDegrees)");

        assertAll(
                () -> assertTrue(renderer.contains("FIRST_PERSON_RIGHT_GUN_X_OFFSET = -0.3F")),
                () -> assertTrue(renderer.contains("FIRST_PERSON_RIGHT_GUN_Y_OFFSET = 0.24F")),
                () -> assertTrue(renderer.contains("HELD_GUN_X_ADJUSTMENT = 0.06F")),
                () -> assertTrue(renderer.contains("HELD_GUN_Y_ADJUSTMENT = -0.16F")),
                () -> assertTrue(renderer.contains("overlayXOffset += HELD_GUN_X_ADJUSTMENT")),
                () -> assertTrue(renderer.contains("overlayYOffset += HELD_GUN_Y_ADJUSTMENT")),
                () -> assertTrue(renderer.contains("overlayRotationDegrees, gunOverlay")),
                () -> assertTrue(renderer.contains("&& !alignRotatedHeldOverlay")),
                () -> assertTrue(renderer.contains("poseStack.translate(-0.25F, 0.2F, 0.0F)")),
                () -> assertTrue(gunOffset >= 0),
                () -> assertTrue(gunOffset < layerOffset),
                () -> assertTrue(layerOffset < layerRotation),
                () -> assertFalse(renderer.contains("FIRST_PERSON_RIGHT_SHARED_X_OFFSET")));
    }

    @Test
    void creativeTabsUseTheSameDynamicBlueprintRendererAsTheirContents() throws IOException {
        String tabs = read("src/main/java/com/gamergaming/taczweaponblueprints/init/ModCreativeTabs.java");

        assertAll(
                () -> assertTrue(tabs.contains("return BlueprintItem.createBlueprint(blueprintId)")),
                () -> assertTrue(tabs.contains("blueprintIcon(\"tacz:ak47\")")),
                () -> assertTrue(tabs.contains("blueprintIcon(\"tacz:762x39\")")),
                () -> assertFalse(tabs.contains(".icon(() -> new ItemStack(ModItems.RIFLE_BLUEPRINT_ITEM.get()))")));
    }

    private static String read(String relativePath) throws IOException {
        return Files.readString(PROJECT_DIRECTORY.resolve(relativePath));
    }
}
