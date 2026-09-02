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
import com.gamergaming.taczweaponblueprints.research.tree.automatic.CapabilityMetric;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponCapabilityMetrics;
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
        assertEquals(1, evidence.projectileCount());
        assertEquals(1.0, evidence.damageRetention());
        assertEquals(2.0, evidence.tacticalReloadSeconds());
        assertEquals(0.0, evidence.chargeSeconds());
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
    void explosiveProjectileCapabilitiesPreserveTaCZUnits() {
        GunData gun = gun("""
                {
                  "ammo": "tacz:40mm",
                  "ammo_amount": 1,
                  "rpm": 150,
                  "bullet": {
                    "bullet_amount": 1,
                    "damage": 10.0,
                    "speed": 60.0,
                    "life": 5.0,
                    "gravity": 0.15,
                    "pierce": 0,
                    "ignite": false,
                    "explosion": {
                      "explode": true,
                      "damage": 50.0,
                      "radius": 6.0,
                      "knockback": true,
                      "delay": 30
                    }
                  },
                  "reload": {
                    "type": "magazine",
                    "cooldown": {"empty": 3.0}
                  },
                  "aim_time": 0.1,
                  "draw_time": 1.18,
                  "weight": 3.6,
                  "fire_mode": ["semi"],
                  "inaccuracy": {"aim": 0.2},
                  "movement_speed": {"aim": -0.1},
                  "recoil": {"pitch": [{"time": 0.0, "value": [2.5]}]}
                }
                """);

        var evidence = new TaCZRuntimeWeaponEvidenceAdapter().normalize(
                "addon:launcher", new RuntimeGun("rpg", gun));

        assertTrue(evidence.explosive());
        assertEquals(50.0, evidence.explosionDamage());
        assertEquals(6.0, evidence.explosionRadius());
        assertEquals(30.0, evidence.explosionDelaySeconds());
        assertTrue(evidence.explosionKnockback());
        assertEquals(0.15, evidence.projectileGravity(), 0.0001);
        assertFalse(evidence.projectileIgnitesEntities());
    }

    @Test
    void modeAdjustmentsAndBurstTriggerIntervalDriveStrongestSustainedMode() {
        GunData gun = gun("""
                {
                  "ammo_amount": 50,
                  "rpm": 810,
                  "bullet": {
                    "damage": 9,
                    "speed": 100,
                    "extra_damage": {
                      "armor_ignore": 0.2,
                      "head_shot_multiplier": 1.5
                    }
                  },
                  "reload": {"type":"inventory"},
                  "fire_mode": ["auto", "burst"],
                  "burst_data": {"count":5,"bpm":1200,"min_interval":0.6},
                  "fire_mode_adjust": {
                    "burst": {"damage":0.5,"head_shot_multiplier":-0.25,
                              "aim_inaccuracy":0.08}
                  },
                  "inaccuracy": {"aim":0.2},
                  "recoil": {"pitch":[{"value":[1]}]}
                }
                """);

        var evidence = new TaCZRuntimeWeaponEvidenceAdapter().normalize(
                "addon:p90", new RuntimeGun("smg", gun));
        var modes = evidence.fireModes();
        var burst = modes.stream().filter(mode -> mode.mode().equals("burst"))
                .findFirst().orElseThrow();

        assertEquals(2, modes.size());
        assertEquals(5, burst.shotsPerTrigger());
        assertEquals(0.6, burst.triggerIntervalSeconds());
        assertEquals(9.5, burst.impactDamage());
        assertEquals(1.25, burst.headshotMultiplier());
        assertEquals(121.5, WeaponCapabilityMetrics.derive(evidence)
                .value(CapabilityMetric.SUSTAINED_DPS).orElseThrow(), 0.0001);
    }

    @Test
    void chargeAndHeatRecoveryReduceLongRunCadence() {
        GunData heated = gun("""
                {
                  "rpm":1200,
                  "bullet":{"damage":10,"speed":100},
                  "reload":{"type":"inventory"},
                  "fire_mode":["semi"],
                  "charging":{"semi":{
                    "type":"hold","increase_per_tick":0.1,
                    "decrease_on_fire":0.6,"max_charge":1,
                    "fire_threshold":0.6,"charge_during_cooldown":false
                  }},
                  "heat":{"max":20,"per_shot":2,"cooling_multiplier":5,
                          "over_heat_time":1000,"min_rpm_mod":0.5,"max_rpm_mod":1,
                          "min_inaccuracy":1,"max_inaccuracy":2},
                  "inaccuracy":{"aim":0.2},
                  "recoil":{"pitch":[{"value":[1]}]}
                }
                """);
        GunData unheated = gun("""
                {
                  "rpm":1200,
                  "bullet":{"damage":10,"speed":100},
                  "reload":{"type":"inventory"},
                  "fire_mode":["semi"],
                  "inaccuracy":{"aim":0.2},
                  "recoil":{"pitch":[{"value":[1]}]}
                }
                """);

        var heatedEvidence = new TaCZRuntimeWeaponEvidenceAdapter().normalize(
                "addon:heated", new RuntimeGun("special", heated));
        var unheatedEvidence = new TaCZRuntimeWeaponEvidenceAdapter().normalize(
                "addon:plain", new RuntimeGun("special", unheated));
        double heatedDps = WeaponCapabilityMetrics.derive(heatedEvidence)
                .value(CapabilityMetric.SUSTAINED_DPS).orElseThrow();
        double plainDps = WeaponCapabilityMetrics.derive(unheatedEvidence)
                .value(CapabilityMetric.SUSTAINED_DPS).orElseThrow();
        double heatedInaccuracy = WeaponCapabilityMetrics.derive(heatedEvidence)
                .value(CapabilityMetric.AIMED_INACCURACY).orElseThrow();
        double plainInaccuracy = WeaponCapabilityMetrics.derive(unheatedEvidence)
                .value(CapabilityMetric.AIMED_INACCURACY).orElseThrow();

        assertEquals(0.3, heatedEvidence.chargeSeconds(), 0.0001);
        assertEquals(3.0, heatedEvidence.heatRecoverySeconds(), 0.0001);
        assertTrue(heatedDps < plainDps);
        assertTrue(heatedInaccuracy > plainInaccuracy);
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
