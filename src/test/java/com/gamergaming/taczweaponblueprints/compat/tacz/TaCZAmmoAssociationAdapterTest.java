package com.gamergaming.taczweaponblueprints.compat.tacz;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.tacz.guns.resource.CommonAssetsManager;
import com.tacz.guns.resource.pojo.data.gun.GunData;

import net.minecraft.resources.ResourceLocation;

class TaCZAmmoAssociationAdapterTest {
    @Test
    void captureReadsCanonicalAmmoIdsOnlyForRecipeBackedGuns() {
        ResourceLocation linked = id("addon:linked");
        ResourceLocation missing = id("addon:missing");
        ResourceLocation ammo = id("addon:ammo");
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(ammo, blueprint(ammo, BlueprintKind.AMMO));
        catalog.put(missing, blueprint(missing, BlueprintKind.GUN));
        catalog.put(linked, blueprint(linked, BlueprintKind.GUN));

        TaCZAmmoAssociationAdapter.Capture capture =
                new TaCZAmmoAssociationAdapter().capture(
                        catalog,
                        gunId -> gunId.equals(linked)
                                ? gun("{\"ammo\":\"addon:ammo\"}")
                                : null);

        assertEquals(2, capture.candidateGunCount());
        assertEquals(Map.of(linked, Set.of(ammo)), capture.candidateAmmoByGun());
        assertEquals(Map.of(missing, "missing_tacz_gun_index"),
                capture.rejectedGunLinks());
        assertFalse(capture.candidateAmmoByGun().containsKey(ammo));
    }

    @Test
    void missingAndExceptionalAmmoDataAreIsolatedPerGun() {
        ResourceLocation missingAmmo = id("addon:missing_ammo");
        ResourceLocation broken = id("addon:broken");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                missingAmmo, blueprint(missingAmmo, BlueprintKind.GUN),
                broken, blueprint(broken, BlueprintKind.GUN));

        TaCZAmmoAssociationAdapter.Capture capture =
                new TaCZAmmoAssociationAdapter().capture(
                        catalog,
                        gunId -> {
                            if (gunId.equals(broken)) {
                                throw new IllegalStateException("bad pack data");
                            }
                            return gun("{}");
                        });

        assertEquals("missing_tacz_ammo_id",
                capture.rejectedGunLinks().get(missingAmmo));
        assertEquals("invalid_tacz_ammo_link:IllegalStateException",
                capture.rejectedGunLinks().get(broken));
    }

    @Test
    void binaryLinkageFailuresAbortTheWholeCapture() {
        ResourceLocation gun = id("addon:incompatible");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                gun, blueprint(gun, BlueprintKind.GUN));

        assertThrows(NoSuchMethodError.class, () ->
                new TaCZAmmoAssociationAdapter().capture(
                        catalog,
                        ignored -> {
                            throw new NoSuchMethodError("changed TaCZ API");
                        }));
    }

    @Test
    void rawCaptureRetainsMultipleCandidatesForFailClosedPublication() {
        ResourceLocation gun = id("test:gun");
        var capture = new TaCZAmmoAssociationAdapter.Capture(
                1,
                Map.of(gun, Set.of(id("test:a"), id("test:b"))),
                Map.of());
        assertEquals(2, capture.candidateAmmoByGun().get(gun).size());
        assertThrows(IllegalArgumentException.class, () ->
                new TaCZAmmoAssociationAdapter.Capture(
                        1,
                        Map.of(gun, Set.of(id("test:a"))),
                        Map.of(gun, "overlap")));
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

    private static GunData gun(String json) {
        return CommonAssetsManager.GSON.fromJson(json, GunData.class);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
