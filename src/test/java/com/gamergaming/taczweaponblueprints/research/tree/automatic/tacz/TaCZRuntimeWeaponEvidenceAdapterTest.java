package com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.TaCZRuntimeWeaponEvidenceAdapter.RuntimeGun;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.resources.ResourceLocation;

class TaCZRuntimeWeaponEvidenceAdapterTest {
    @Test
    void captureUsesOnlyRecipeBackedGunsAndIsolatesMissingIndexes() {
        ResourceLocation accepted = new ResourceLocation("addon", "accepted");
        ResourceLocation missing = new ResourceLocation("addon", "missing");
        ResourceLocation ammo = new ResourceLocation("addon", "ammo");
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(missing, blueprint(missing, BlueprintKind.GUN));
        catalog.put(ammo, blueprint(ammo, BlueprintKind.AMMO));
        catalog.put(accepted, blueprint(accepted, BlueprintKind.GUN));

        TaCZRuntimeWeaponEvidenceAdapter.Capture capture =
                new TaCZRuntimeWeaponEvidenceAdapter().capture(
                        catalog,
                        id -> id.equals(accepted)
                                ? new RuntimeGun("rifle", standardGun())
                                : null);

        assertEquals(2, capture.candidateCount());
        assertEquals(java.util.List.of("addon:accepted"),
                capture.evidenceByBlueprint().keySet().stream().toList());
        assertEquals(Map.of("addon:missing", "missing_tacz_gun_index"),
                capture.rejectedBlueprints());
        assertFalse(capture.evidenceByBlueprint().containsKey("addon:ammo"));
        var evidence = capture.evidenceByBlueprint().get("addon:accepted");
        assertEquals(8.0, evidence.baseDamage());
        assertEquals(2.5, evidence.reloadSeconds());
        assertEquals(1000.0, evidence.effectiveRange());
        assertTrue(evidence.warnings().stream().anyMatch(value ->
                value.contains("speed * life fallback")));
    }

    @Test
    void tubeReloadAndCustomScriptRemainExplicit() {
        GunData gun = gun("""
                {
                  "ammo": "tacz:12g",
                  "ammo_amount": 6,
                  "rpm": 200,
                  "bullet": {
                    "damage": 40.0,
                    "speed": 150.0,
                    "life": 1.0,
                    "pierce": 1,
                    "extra_damage": {
                      "armor_ignore": 0.25,
                      "head_shot_multiplier": 1.33,
                      "damage_adjust": [{"distance": 28.0, "damage": 42.0}]
                    }
                  },
                  "reload": {
                    "type": "magazine",
                    "feed": {"empty": 0.55, "tactical": 0.5},
                    "cooldown": {"empty": 0.75, "tactical": 1.21}
                  },
                  "script": "tacz:m1014_gun_logic",
                  "script_param": {"intro_empty": 0.55, "loop": 0.75, "loop_2": 0.8, "ending": 1.21},
                  "fire_mode": ["semi"],
                  "allow_attachment_types": ["scope", "muzzle", "stock"],
                  "inaccuracy": {"aim": 3.9},
                  "movement_speed": {"aim": -0.2},
                  "recoil": {
                    "pitch": [{"time": 0.0, "value": [3.0, 4.8]}],
                    "yaw": [{"time": 0.0, "value": [-1.0, 1.0]}]
                  }
                }
                """);

        var evidence = new TaCZRuntimeWeaponEvidenceAdapter().normalize(
                "addon:m1014", new RuntimeGun("shotgun", gun));

        assertEquals(42.0, evidence.baseDamage());
        assertEquals(4.16, evidence.reloadSeconds(), 0.0001);
        assertEquals(28.0, evidence.effectiveRange());
        assertEquals(4.8, evidence.recoilMagnitude(), 0.0001);
        assertTrue(evidence.scriptControlled());
        assertTrue(evidence.warnings().contains(
                "scripted_behavior_requires_review:tacz:m1014_gun_logic"));
    }

    @Test
    void oneMalformedGunDoesNotDiscardOtherRuntimeEvidence() {
        ResourceLocation valid = new ResourceLocation("addon", "valid");
        ResourceLocation invalid = new ResourceLocation("addon", "invalid");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                valid, blueprint(valid, BlueprintKind.GUN),
                invalid, blueprint(invalid, BlueprintKind.GUN));
        GunData invalidGun = gun("""
                {
                  "ammo": "tacz:9mm",
                  "ammo_amount": 10,
                  "rpm": -1,
                  "bullet": {"damage": 5.0},
                  "fire_mode": ["semi"]
                }
                """);

        TaCZRuntimeWeaponEvidenceAdapter.Capture capture =
                new TaCZRuntimeWeaponEvidenceAdapter().capture(
                        catalog,
                        id -> new RuntimeGun(
                                "pistol", id.equals(valid) ? standardGun() : invalidGun));

        assertEquals(1, capture.evidenceByBlueprint().size());
        assertEquals(1, capture.rejectedBlueprints().size());
        assertTrue(capture.rejectedBlueprints().get("addon:invalid")
                .startsWith("invalid_tacz_runtime_evidence:"));
    }

    private static BlueprintData blueprint(ResourceLocation id, BlueprintKind kind) {
        return new BlueprintData(
                id.toString(),
                "name." + id.getPath(),
                "tooltip",
                new ResourceLocation(id.getNamespace(), "recipe/" + id.getPath()),
                null,
                kind == BlueprintKind.GUN ? "rifle" : "ammo",
                new ResourceLocation(id.getNamespace(), "display/" + id.getPath()),
                kind);
    }

    private static GunData standardGun() {
        return gun("""
                {
                  "ammo": "tacz:9mm",
                  "ammo_amount": 20,
                  "rpm": 600,
                  "bullet": {"damage": 8.0, "speed": 100.0, "life": 10.0, "pierce": 1},
                  "reload": {
                    "type": "magazine",
                    "cooldown": {"empty": 2.5, "tactical": 2.0}
                  },
                  "aim_time": 0.2,
                  "draw_time": 0.4,
                  "weight": 3.0,
                  "fire_mode": ["semi", "auto"],
                  "allow_attachment_types": ["scope", "muzzle"],
                  "inaccuracy": {"aim": 0.2},
                  "movement_speed": {"aim": -0.2},
                  "recoil": {"pitch": [{"time": 0.0, "value": [0.2, 0.4]}]}
                }
                """);
    }

    private static GunData gun(String json) {
        return CommonAssetsManager.GSON.fromJson(json, GunData.class);
    }
}
