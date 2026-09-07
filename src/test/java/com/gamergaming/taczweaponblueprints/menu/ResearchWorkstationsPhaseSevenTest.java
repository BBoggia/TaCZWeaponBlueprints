package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;

/** Art contracts for the original Analyzer registry ID after the workstation replacement. */
class ResearchWorkstationsPhaseSevenTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/taczweaponblueprints");

    @Test void basicAnalyzerRetainsTheTransparentInspectionLens() throws Exception {
        var model = JsonParser.parseString(Files.readString(ASSETS.resolve("models/block/blueprint_recycler.json"))).getAsJsonObject();
        assertEquals("forge:composite", model.get("loader").getAsString());
        var children = model.getAsJsonObject("children");
        assertEquals("minecraft:cutout", children.getAsJsonObject("body").get("render_type").getAsString());
        assertEquals("minecraft:translucent", children.getAsJsonObject("glass").get("render_type").getAsString());
        var texture = ImageIO.read(ASSETS.resolve("textures/block/blueprint_recycler.png").toFile());
        assertNotNull(texture);
        assertEquals(512, texture.getWidth()); assertEquals(256, texture.getHeight());
        int translucent = 0, opaque = 0;
        for (int y = 0; y < texture.getHeight(); y++) for (int x = 0; x < texture.getWidth(); x++) {
            int alpha = texture.getRGB(x, y) >>> 24;
            if (alpha > 0 && alpha < 255) translucent++;
            if (alpha == 255) opaque++;
        }
        assertTrue(translucent > 100 && opaque > 10000);
    }

    @Test void simpleManualEquipmentRemainsDistinctFromHigherTiers() throws Exception {
        String body = Files.readString(ASSETS.resolve("models/block/blueprint_recycler.obj"));
        assertTrue(body.contains("manual_magnifier_vertical_rod"));
        assertTrue(body.contains("uneven_lower_shelf_plank"));
        assertTrue(body.contains("single_mismatched_cream_drawer_face"));
        assertFalse(body.contains("binocular")); assertFalse(body.contains("crt"));
    }
}
