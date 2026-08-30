package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TaCZGunPackExtractorTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void extractsOnlyRecipeBackedGunsAndAcceptsTaCZComments() throws IOException {
        write("data/test/recipes/gun/sample.json", """
                {
                  // TaCZ gun packs permit comments.
                  "result": {"type": "gun", "id": "test:sample"}
                }
                """);
        write("data/test/index/guns/sample.json", """
                {"type":"rifle","data":"test:sample_data"}
                """);
        write("data/test/data/guns/sample_data.json", """
                {
                  "rpm": 600,
                  "ammo_amount": 30,
                  "bullet": {
                    "damage": 8,
                    "speed": 250,
                    "life": 0.8,
                    "extra_damage": {
                      "armor_ignore": 0.25,
                      "head_shot_multiplier": 1.5,
                      "damage_adjust": [
                        {"distance":30,"damage":9},
                        {"distance":60,"damage":7}
                      ]
                    },
                    "pierce": 1
                  },
                  "reload": {"type":"magazine","cooldown":{"empty":2.6,"tactical":2.0}},
                  "aim_time": 0.2,
                  "draw_time": 0.35,
                  "weight": 3.5,
                  "movement_speed": {"aim":-0.2},
                  "fire_mode": ["auto","semi"],
                  "allow_attachment_types": ["scope","muzzle"],
                  "recoil": {
                    "pitch":[{"value":[1.2,1.2]}],
                    "yaw":[{"value":[-0.4,0.5]}]
                  },
                  "inaccuracy":{"aim":0.3}
                }
                """);
        write("data/test/index/guns/unrecipe.json", """
                {"type":"pistol","data":"test:unrecipe_data"}
                """);
        write("data/test/data/guns/unrecipe_data.json", "{}");

        var extracted = new TaCZGunPackExtractor().extract(temporaryDirectory);

        assertEquals(1, extracted.size());
        TaCZGunStats sample = extracted.get(0);
        assertEquals("test:sample", sample.blueprintId());
        assertEquals("rifle", sample.gunType());
        assertEquals(9.0, sample.baseDamage());
        assertEquals(60.0, sample.effectiveRange());
        assertEquals(2.6, sample.reloadSeconds());
        assertEquals(1.2, sample.recoilMagnitude());
        assertEquals(2, sample.fireModeCount());
        assertEquals(2, sample.attachmentTypeCount());
        assertFalse(sample.sourceHash().isBlank());
        assertEquals(0, sample.missingFields().size());
    }

    @Test
    void rejectsResourceIdsThatCouldEscapeThePack() throws IOException {
        write("data/test/recipes/gun/bad.json", """
                {"result":{"type":"gun","id":"test:../../bad"}}
                """);

        assertThrows(IOException.class,
                () -> new TaCZGunPackExtractor().extract(temporaryDirectory));
    }

    @Test
    void expandsKnownTubeReloadScriptsAndFlagsScriptedBehavior() throws IOException {
        write("data/test/recipes/gun/tube.json", """
                {"result":{"type":"gun","id":"test:tube"}}
                """);
        write("data/test/index/guns/tube.json", """
                {"type":"shotgun","data":"test:tube_data"}
                """);
        write("data/test/data/guns/tube_data.json", """
                {
                  "script":"tacz:m870_gun_logic",
                  "script_param":{"bolt_time":0.55},
                  "rpm":180,
                  "ammo_amount":5,
                  "bullet":{"damage":36,"speed":150,"life":0.6},
                  "reload":{
                    "type":"magazine",
                    "feed":{"empty":2.13,"tactical":0.67},
                    "cooldown":{"empty":0.67,"tactical":0.23}
                  },
                  "aim_time":0.15,
                  "draw_time":0.25,
                  "weight":3.2,
                  "movement_speed":{"aim":-0.2},
                  "fire_mode":["semi"],
                  "recoil":{"pitch":[{"value":[5.5,5.5]}]},
                  "inaccuracy":{"aim":0.2}
                }
                """);

        TaCZGunStats tube = new TaCZGunPackExtractor().extract(temporaryDirectory).get(0);

        assertEquals(5.71, tube.reloadSeconds(), 0.0001);
        assertEquals(0.55, tube.boltActionSeconds());
        assertEquals("tacz:m870_gun_logic", tube.scriptId());
        assertEquals(true, tube.missingFields().stream()
                .anyMatch(value -> value.startsWith("scripted_behavior_requires_review:")));
    }

    @Test
    void expandsM1014PairLoadingTheSameWayAsRuntimeEvidence() throws IOException {
        write("data/test/recipes/gun/pair_tube.json", """
                {"result":{"type":"gun","id":"test:pair_tube"}}
                """);
        write("data/test/index/guns/pair_tube.json", """
                {"type":"shotgun","data":"test:pair_tube_data"}
                """);
        write("data/test/data/guns/pair_tube_data.json", """
                {
                  "script":"tacz:m1014_gun_logic",
                  "script_param":{
                    "intro_empty":0.55,"loop":0.75,"loop_2":0.8,"ending":1.21
                  },
                  "rpm":200,
                  "ammo_amount":6,
                  "bullet":{"damage":40,"speed":150,"life":1.0},
                  "reload":{
                    "type":"magazine",
                    "feed":{"empty":0.55,"tactical":0.5},
                    "cooldown":{"empty":0.75,"tactical":1.21}
                  },
                  "aim_time":0.15,
                  "draw_time":0.25,
                  "weight":3.2,
                  "movement_speed":{"aim":-0.2},
                  "fire_mode":["semi"],
                  "recoil":{"pitch":[{"value":[5.5,5.5]}]},
                  "inaccuracy":{"aim":0.2}
                }
                """);

        TaCZGunStats tube = new TaCZGunPackExtractor().extract(temporaryDirectory).get(0);

        assertEquals(4.16, tube.reloadSeconds(), 0.0001);
    }

    private void write(String relativePath, String content) throws IOException {
        Path path = temporaryDirectory.resolve(relativePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }
}
