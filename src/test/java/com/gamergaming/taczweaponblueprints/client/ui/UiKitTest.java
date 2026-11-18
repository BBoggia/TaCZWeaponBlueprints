package com.gamergaming.taczweaponblueprints.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Contracts for the shared UI kit: readable colours and complete, sliceable sprites. */
class UiKitTest {
    private static final Path SPRITES = Path.of(System.getProperty("user.dir"),
            "src/main/resources/assets/taczweaponblueprints");
    private static final double READABLE = 4.5D;

    @Test
    void textIsReadableOnEverySurfaceItIsDrawnOn() {
        assertReadable("panel text", UiTheme.TEXT, UiTheme.STEEL);
        assertReadable("muted panel text", UiTheme.TEXT_MUTED, UiTheme.STEEL);
        assertReadable("button text", UiTheme.TEXT, UiTheme.STEEL_RAISED);
        assertReadable("hovered button text", UiTheme.TEXT, UiTheme.STEEL_HIGHLIGHT);
        assertReadable("primary button text", UiTheme.AMBER_TEXT, UiTheme.AMBER);
        assertReadable("hovered primary button text", UiTheme.AMBER_TEXT, UiTheme.AMBER_HIGHLIGHT);
        assertReadable("tag text", UiTheme.MANILA_TEXT, UiTheme.MANILA);
        assertReadable("tooltip text", UiTheme.TEXT, UiTheme.STEEL_DARKEST);
        assertReadable("status text", UiTheme.AMBER_HIGHLIGHT, UiTheme.STEEL);
        assertReadable("met requirement", UiTheme.GREEN, UiTheme.STEEL);
        assertReadable("error text", UiTheme.RED, UiTheme.STEEL);
        assertReadable("sheet ink", UiTheme.INK, UiTheme.SHEET);
        assertReadable("gate text", UiTheme.INK_DIM, UiTheme.SHEET_GATE);
        assertReadable("learned tile name", UiTheme.INK, UiTheme.SHEET_LEARNED);
    }

    @Test
    void lockedContentIsQuieterThanAvailableContent() {
        assertTrue(UiTheme.contrast(UiTheme.PENCIL, UiTheme.SHEET) < UiTheme.contrast(UiTheme.INK_DIM, UiTheme.SHEET));
        assertTrue(UiTheme.contrast(UiTheme.INK_DIM, UiTheme.SHEET) < UiTheme.contrast(UiTheme.INK, UiTheme.SHEET));
    }

    @Test
    void everySpriteShipsWithNineSliceScalingThatFitsItsImage() throws IOException {
        for (var sprite : UiSprites.ALL) {
            String name = sprite.texture().getPath();
            BufferedImage image = ImageIO.read(SPRITES.resolve(name).toFile());
            JsonObject scaling = JsonParser.parseString(Files.readString(SPRITES.resolve(name + ".mcmeta")))
                    .getAsJsonObject().getAsJsonObject("gui").getAsJsonObject("scaling");
            assertEquals("nine_slice", scaling.get("type").getAsString(), name);
            assertEquals(image.getWidth(), scaling.get("width").getAsInt(), name);
            assertEquals(image.getHeight(), scaling.get("height").getAsInt(), name);
            int border = scaling.get("border").getAsInt();
            assertEquals(sprite.size(), image.getWidth(), name);
            assertEquals(sprite.border(), border, name);
            assertTrue(border > 0 && border * 2 < Math.min(image.getWidth(), image.getHeight()),
                    name + " needs a stretchable centre");
            assertEquals(0xFF, image.getRGB(image.getWidth() / 2, image.getHeight() / 2) >>> 24,
                    name + " centre must be opaque");
        }
    }

    @Test
    void disabledButtonsNeverLookActionable() {
        for (UiButton.Variant variant : UiButton.Variant.values()) {
            assertEquals(UiSprites.BUTTON_DISABLED, UiButton.spriteFor(variant, false, false));
            assertEquals(UiSprites.BUTTON_DISABLED, UiButton.spriteFor(variant, false, true));
            assertEquals(UiTheme.TEXT_DISABLED, UiButton.textColorFor(variant, false));
            assertNotEquals(UiButton.spriteFor(variant, true, false), UiButton.spriteFor(variant, true, true));
        }
        assertEquals(UiSprites.BUTTON_PRIMARY, UiButton.spriteFor(UiButton.Variant.PRIMARY, true, false));
        assertEquals(UiSprites.BUTTON, UiButton.spriteFor(UiButton.Variant.SECONDARY, true, false));
    }

    @Test
    void onlyAResearchableTileIsAmberAndLockedStaysTheQuietest() {
        for (UiSheet.TileState state : UiSheet.TileState.values()) {
            assertEquals(state == UiSheet.TileState.READY, UiSheet.tileBorder(state) == UiTheme.AMBER, state.name());
        }
        assertTrue(UiTheme.contrast(UiSheet.tileBorder(UiSheet.TileState.LOCKED), UiTheme.SHEET)
                < UiTheme.contrast(UiSheet.tileBorder(UiSheet.TileState.OPEN), UiTheme.SHEET));
        assertReadable("open tile name", UiSheet.tileText(UiSheet.TileState.OPEN), UiSheet.tileFill(UiSheet.TileState.OPEN));
    }

    private static void assertReadable(String what, int text, int surface) {
        double ratio = UiTheme.contrast(text, surface);
        assertTrue(ratio >= READABLE, what + " contrast is " + ratio);
    }
}
