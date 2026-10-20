package com.gamergaming.taczweaponblueprints.block;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.imageio.ImageIO;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

/** Checks the packaged assets that Forge actually loads, including every placement facing. */
class WorkstationModelAssetsTest {
    private static final Path ASSETS = Path.of("src/main/resources/assets/taczweaponblueprints");
    private static final List<String> IDS = List.of("workbench_lvl1", "workbench_lvl2", "workbench_lvl3",
            "research_bench", "advanced_research_bench", "experimental_research_bench",
            "blueprint_recycler", "advanced_blueprint_analyzer", "experimental_blueprint_analyzer");
    private static final List<String> DIRECTIONS = List.of("north", "east", "south", "west");

    @Test
    void everyFacingCoversItsRootAndClockwiseExtensionWithoutDuplicatingTheModel() throws IOException {
        for (String id : IDS) {
            JsonObject variants = json(ASSETS.resolve("blockstates/" + id + ".json"))
                    .getAsJsonObject("variants");
            for (int facing = 0; facing < 4; facing++) {
                String direction = DIRECTIONS.get(facing);
                JsonObject variant = variants.getAsJsonObject("extension=false,facing=" + direction);
                JsonObject model = json(modelPath(variant.get("model").getAsString()));
                List<double[]> points = new ArrayList<>();
                for (JsonObject layer : layers(model)) points.addAll(faceVertices(objectPath(layer)));
                int turns = variant.has("y") ? variant.get("y").getAsInt() / 90 : 0;
                for (double[] p : points) {
                    for (int i = 0; i < turns; i++) {
                        double x = p[0]; p[0] = 1 - p[2]; p[2] = x;
                    }
                    double minX = facing == 2 ? -1 : 0, maxX = facing == 0 ? 2 : 1;
                    double minZ = facing == 3 ? -1 : 0, maxZ = facing == 1 ? 2 : 1;
                    assertTrue(p[0] >= minX - .001 && p[0] <= maxX + .001, id + " " + direction + " X");
                    assertTrue(p[2] >= minZ - .001 && p[2] <= maxZ + .001, id + " " + direction + " Z");
                    assertTrue(p[1] >= -.001 && p[1] <= 2, id + " Y");
                }
                assertEquals("minecraft:block/air", variants.getAsJsonObject("extension=true,facing=" + direction)
                        .get("model").getAsString());
            }
        }
    }

    @Test
    void materialsResolveAndObjectNamesCannotCollideInForgesWhitespaceTokenizer() throws IOException {
        Set<Path> checked = new HashSet<>();
        for (String id : IDS) {
            List<Path> models = new ArrayList<>();
            models.add(ASSETS.resolve("models/item/" + id + ".json"));
            for (String direction : DIRECTIONS) {
                models.add(ASSETS.resolve("models/block/" + id
                        + (id.contains("research") && !direction.equals("north") ? "_" + direction : "") + ".json"));
            }
            for (Path modelPath : models) for (JsonObject layer : layers(json(modelPath))) {
                Path obj = objectPath(layer);
                if (!checked.add(obj)) continue;
                assertTrue(layer.get("flip_v").getAsBoolean(), obj.toString());
                assertFalse(layer.get("automatic_culling").getAsBoolean(), obj.toString());
                String texture = layer.getAsJsonObject("textures").get("texture0").getAsString();
                assertEquals("taczweaponblueprints:block/" + id, texture);
                assertNotNull(ImageIO.read(ASSETS.resolve("textures/" + texture.split(":")[1] + ".png").toFile()));
                Set<String> objects = new HashSet<>();
                int faceCount = 0;
                for (String line : Files.readAllLines(obj)) {
                    if (line.startsWith("o ")) {
                        assertEquals(2, line.split("\\s+").length, obj + " " + line);
                        assertTrue(objects.add(line.substring(2)), "Forge would replace an earlier object: " + line);
                    } else if (line.startsWith("mtllib ")) {
                        String material = Files.readString(obj.resolveSibling(line.substring(7)));
                        assertTrue(material.contains("newmtl workstation\n"));
                        assertTrue(material.contains("map_Kd #texture0"));
                    } else if (line.startsWith("usemtl ")) {
                        assertEquals("usemtl workstation", line);
                    } else if (line.startsWith("f ")) {
                        assertEquals(5, line.split("\\s+").length, "Forge quad: " + obj);
                        faceCount++;
                    }
                }
                assertFalse(objects.isEmpty());
                assertEquals(objects.size() * 6, faceCount, "Every authored cube keeps all six faces: " + obj);
                assertFalse(faceVertices(obj).isEmpty());
            }
        }
    }

    @Test
    void eachResearchTierHasDistinctGeometryAndGlassIsSeparateFromOpaqueEquipment() throws IOException {
        Set<String> bodies = new HashSet<>();
        for (String id : IDS.subList(3, 6)) {
            for (String domain : List.of("block", "item")) {
                JsonObject model = json(ASSETS.resolve("models/" + domain + "/" + id + ".json"));
                assertEquals("neoforge:composite", model.get("loader").getAsString());
                JsonObject children = model.getAsJsonObject("children");
                assertEquals(Set.of("body", "glass"), children.keySet());
                JsonObject body = children.getAsJsonObject("body"), glass = children.getAsJsonObject("glass");
                assertEquals("minecraft:cutout", body.get("render_type").getAsString());
                assertEquals("minecraft:translucent", glass.get("render_type").getAsString());
                assertEquals(List.of("body", "glass"), model.getAsJsonArray("item_render_order").asList()
                        .stream().map(e -> e.getAsString()).toList());
                if (domain.equals("block")) assertTrue(bodies.add(body.get("model").getAsString()));
                String glassObj = Files.readString(objectPath(glass));
                assertEquals(3, glassObj.lines().filter(l -> l.startsWith("o ")).count());
                assertTrue(glassObj.lines().filter(l -> l.startsWith("o "))
                        .allMatch(l -> l.contains("magnifying_glass")));
                assertFalse(Files.readString(objectPath(body)).contains("magnifying_glass"));
            }
        }
    }

    @Test
    void inventoryGeometryIsCenteredAroundTheItemTransformOrigin() throws IOException {
        for (String id : IDS) {
            List<double[]> points = new ArrayList<>();
            for (JsonObject layer : layers(json(ASSETS.resolve("models/item/" + id + ".json"))))
                points.addAll(faceVertices(objectPath(layer)));
            for (int axis = 0; axis < 3; axis++) {
                final int i = axis;
                double min = points.stream().mapToDouble(p -> p[i]).min().orElseThrow();
                double max = points.stream().mapToDouble(p -> p[i]).max().orElseThrow();
                assertEquals(.5, (min + max) / 2, .025, id + " item center axis " + i);
            }
        }
    }

    private static List<double[]> faceVertices(Path path) throws IOException {
        List<double[]> vertices = new ArrayList<>(), referenced = new ArrayList<>();
        for (String line : Files.readAllLines(path)) {
            if (line.startsWith("v ")) {
                String[] p = line.split("\\s+");
                double[] v = {Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])};
                for (double value : v) assertTrue(Double.isFinite(value));
                vertices.add(v);
            } else if (line.startsWith("f ")) {
                String[] tokens = line.split("\\s+");
                for (int i = 1; i < tokens.length; i++) {
                    int index = Integer.parseInt(tokens[i].split("/")[0]) - 1;
                    assertTrue(index >= 0 && index < vertices.size(), path + " vertex index");
                    referenced.add(vertices.get(index).clone());
                }
            }
        }
        return referenced;
    }

    private static List<JsonObject> layers(JsonObject model) {
        if (!model.has("children")) return List.of(model);
        return model.getAsJsonObject("children").entrySet().stream().map(e -> e.getValue().getAsJsonObject()).toList();
    }
    private static Path modelPath(String id) { return ASSETS.resolve("models/" + id.split(":")[1] + ".json"); }
    private static Path objectPath(JsonObject model) { return ASSETS.resolve(model.get("model").getAsString().split(":")[1]); }
    private static JsonObject json(Path path) throws IOException { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
}
