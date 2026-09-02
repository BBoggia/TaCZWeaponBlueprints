package com.gamergaming.taczweaponblueprints.menu;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.Test;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/** Regression gates for the Phase 7 Recycler art and physical presentation. */
class ResearchWorkstationsPhaseSevenTest {
    private static final Path PROJECT = Path.of(System.getProperty("user.dir"));
    private static final String MODEL = "taczweaponblueprints:block/blueprint_recycler";
    private static final String TEXTURE = "taczweaponblueprints:block/blueprint_recycler";

    @Test
    void finalModelUsesCustomArtAndPurposefulBoundedGeometry() throws IOException {
        JsonObject model = readJson(
                "src/main/resources/assets/taczweaponblueprints/models/block/blueprint_recycler.json");

        assertEquals("minecraft:block/block", model.get("parent").getAsString());
        assertFalse(model.get("ambientocclusion").getAsBoolean());
        JsonObject textures = model.getAsJsonObject("textures");
        assertEquals(Set.of("particle", "side", "front", "top", "control"), textures.keySet());
        textures.entrySet().forEach(entry -> assertEquals(TEXTURE, entry.getValue().getAsString()));
        assertFalse(model.toString().contains("minecraft:block/iron_block"));

        JsonArray elements = model.getAsJsonArray("elements");
        assertEquals(8, elements.size());
        assertEquals(
                Set.of(
                        "cabinet",
                        "top_deck",
                        "rear_console",
                        "paper_intake",
                        "output_drawer",
                        "front_left_foot",
                        "front_right_foot",
                        "rear_feet"),
                elementNames(elements));

        for (JsonElement value : elements) {
            JsonObject element = value.getAsJsonObject();
            assertBounds(element.getAsJsonArray("from"), element.getAsJsonArray("to"));
            JsonObject faces = element.getAsJsonObject("faces");
            assertEquals(Set.of("north", "east", "south", "west", "up", "down"), faces.keySet());
            for (JsonElement faceValue : faces.asMap().values()) {
                JsonObject face = faceValue.getAsJsonObject();
                assertTrue(face.get("texture").getAsString().startsWith("#"));
                JsonArray uv = face.getAsJsonArray("uv");
                assertEquals(4, uv.size());
                uv.forEach(coordinate -> assertTrue(
                        coordinate.getAsDouble() >= 0.0D && coordinate.getAsDouble() <= 16.0D));
            }
        }
    }

    @Test
    void blockstateRotatesOneNorthAuthoredModelThroughEveryHorizontalFacing()
            throws IOException {
        JsonObject variants = readJson(
                "src/main/resources/assets/taczweaponblueprints/blockstates/blueprint_recycler.json")
                .getAsJsonObject("variants");

        assertEquals(
                Set.of("facing=north", "facing=east", "facing=south", "facing=west"),
                variants.keySet());
        assertVariant(variants, "facing=north", 0);
        assertVariant(variants, "facing=east", 90);
        assertVariant(variants, "facing=south", 180);
        assertVariant(variants, "facing=west", 270);
    }

    @Test
    void inventoryRepresentationReusesTheSameFinalBlockModel() throws IOException {
        JsonObject item = readJson(
                "src/main/resources/assets/taczweaponblueprints/models/item/blueprint_recycler.json");
        assertEquals(MODEL, item.get("parent").getAsString());
        assertEquals(1, item.size());
    }

    @Test
    void textureIsOpaquePowerOfTwoAndRetainsDetailInEveryAtlasPanel() throws IOException {
        Path path = PROJECT.resolve(
                "src/main/resources/assets/taczweaponblueprints/textures/block/blueprint_recycler.png");
        BufferedImage image = ImageIO.read(path.toFile());

        assertNotNull(image);
        assertEquals(256, image.getWidth());
        assertEquals(256, image.getHeight());
        assertTrue(isPowerOfTwo(image.getWidth()));
        assertTrue(isPowerOfTwo(image.getHeight()));
        for (int panelY = 0; panelY < 2; panelY++) {
            for (int panelX = 0; panelX < 2; panelX++) {
                Set<Integer> colors = new HashSet<>();
                for (int y = panelY * 128; y < (panelY + 1) * 128; y++) {
                    for (int x = panelX * 128; x < (panelX + 1) * 128; x++) {
                        int argb = image.getRGB(x, y);
                        assertEquals(255, argb >>> 24);
                        colors.add(argb & 0x00FFFFFF);
                    }
                }
                assertTrue(colors.size() > 256, "Each atlas panel must retain visible material detail");
            }
        }
    }

    @Test
    void runtimeShapeTracksTheNonCubeModelAndDisablesFullCubeOcclusion() throws IOException {
        String block = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/block/BlueprintRecyclerBlock.java"));
        String registration = Files.readString(PROJECT.resolve(
                "src/main/java/com/gamergaming/taczweaponblueprints/init/ModBlocks.java"));
        String northShape = block.substring(
                block.indexOf("NORTH_SHAPE"), block.indexOf("EAST_SHAPE"));
        String recyclerRegistration = registration.substring(
                registration.indexOf("BLUEPRINT_RECYCLER"));

        assertEquals(8, count(northShape, "box("));
        assertTrue(block.contains("case EAST -> EAST_SHAPE"));
        assertTrue(block.contains("case SOUTH -> SOUTH_SHAPE"));
        assertTrue(block.contains("case WEST -> WEST_SHAPE"));
        assertTrue(block.contains("Shapes.box(1.0D - maxZ, minY, minX"));
        assertTrue(recyclerRegistration.contains(".noOcclusion()"));
    }

    private static void assertVariant(JsonObject variants, String name, int expectedRotation) {
        JsonObject variant = variants.getAsJsonObject(name);
        assertEquals(MODEL, variant.get("model").getAsString());
        int rotation = variant.has("y") ? variant.get("y").getAsInt() : 0;
        assertEquals(expectedRotation, rotation);
    }

    private static Set<String> elementNames(JsonArray elements) {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        elements.forEach(value -> names.add(value.getAsJsonObject().get("name").getAsString()));
        return Set.copyOf(names);
    }

    private static void assertBounds(JsonArray from, JsonArray to) {
        assertEquals(3, from.size());
        assertEquals(3, to.size());
        for (int axis = 0; axis < 3; axis++) {
            double minimum = from.get(axis).getAsDouble();
            double maximum = to.get(axis).getAsDouble();
            assertTrue(minimum >= 0.0D);
            assertTrue(maximum <= 16.0D);
            assertTrue(minimum < maximum);
        }
    }

    private static int count(String value, String needle) {
        int matches = 0;
        int offset = 0;
        while ((offset = value.indexOf(needle, offset)) >= 0) {
            matches++;
            offset += needle.length();
        }
        return matches;
    }

    private static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & value - 1) == 0;
    }

    private static JsonObject readJson(String relativePath) throws IOException {
        try (Reader reader = Files.newBufferedReader(PROJECT.resolve(relativePath))) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
