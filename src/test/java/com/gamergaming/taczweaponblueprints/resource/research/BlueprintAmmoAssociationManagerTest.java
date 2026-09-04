package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZAmmoAssociationAdapter;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;

import net.minecraft.resources.ResourceLocation;

class BlueprintAmmoAssociationManagerTest {
    @Test
    void publicationIsCanonicalRevisionedAndAtomicallyInvalidated() {
        ResourceLocation gun = id("test:gun");
        ResourceLocation rejectedGun = id("test:rejected");
        ResourceLocation ammo = id("test:ammo");
        Map<ResourceLocation, BlueprintData> catalog = catalog(
                Set.of(gun, rejectedGun), Set.of(ammo));
        var capture = new TaCZAmmoAssociationAdapter.Capture(
                2,
                Map.of(gun, Set.of(ammo)),
                Map.of(rejectedGun, "missing_tacz_ammo_id"));
        BlueprintAmmoAssociationManager manager = new BlueprintAmmoAssociationManager();

        assertTrue(manager.publish(capture, catalog, 8L));

        var publication = manager.publication();
        var snapshot = publication.snapshot();
        assertEquals(1L, publication.revision());
        assertEquals(8L, publication.catalogRevision());
        assertEquals(BlueprintAmmoAssociationManager.PublicationState.READY,
                publication.state());
        assertEquals(Map.of(gun, ammo), snapshot.ammoByGun());
        assertEquals(Set.of(gun), snapshot.gunsForAmmo(ammo));
        assertEquals("missing_tacz_ammo_id",
                snapshot.rejectedGunLinks().get(rejectedGun));
        assertEquals(snapshot,
                manager.snapshotForCatalogRevision(8L).orElseThrow());
        assertTrue(manager.snapshotForCatalogRevision(9L).isEmpty());

        manager.invalidateForCatalogRevision(9L);
        assertEquals(2L, manager.publication().revision());
        assertEquals(BlueprintAmmoAssociationManager.PublicationState.INVALIDATED,
                manager.publication().state());
        assertTrue(manager.snapshotForCatalogRevision(9L).isEmpty());

        manager.clear();
        assertSame(BlueprintAmmoAssociationManager.Publication.EMPTY,
                manager.publication());
    }

    @Test
    void ambiguousCandidateLinksAreVisibleButNeverPublishedAsTrusted() {
        ResourceLocation gun = id("test:gun");
        ResourceLocation ammoA = id("test:ammo_a");
        ResourceLocation ammoB = id("test:ammo_b");
        Map<ResourceLocation, BlueprintData> catalog = catalog(
                Set.of(gun), Set.of(ammoA, ammoB));
        var capture = new TaCZAmmoAssociationAdapter.Capture(
                1,
                Map.of(gun, Set.of(ammoA, ammoB)),
                Map.of());
        BlueprintAmmoAssociationManager manager = new BlueprintAmmoAssociationManager();

        assertTrue(manager.publish(capture, catalog, 2L));
        BlueprintAmmoAssociationSnapshot snapshot = manager.publication().snapshot();

        assertTrue(snapshot.ammoByGun().isEmpty());
        assertEquals(Set.of(gun), snapshot.ambiguousGunBlueprintIds());
        assertEquals(Set.of(gun), snapshot.ambiguousGunsForAmmo(ammoA));
        assertEquals(Set.of(gun), snapshot.ambiguousGunsForAmmo(ammoB));
    }

    @Test
    void nonRecipeBackedAmmoBecomesARejectedLink() {
        ResourceLocation gun = id("test:gun");
        ResourceLocation absentAmmo = id("test:absent_ammo");
        Map<ResourceLocation, BlueprintData> catalog = catalog(Set.of(gun), Set.of());
        var capture = new TaCZAmmoAssociationAdapter.Capture(
                1,
                Map.of(gun, Set.of(absentAmmo)),
                Map.of());
        BlueprintAmmoAssociationManager manager = new BlueprintAmmoAssociationManager();

        assertTrue(manager.publish(capture, catalog, 1L));
        BlueprintAmmoAssociationSnapshot snapshot = manager.publication().snapshot();
        assertTrue(snapshot.ammoByGun().isEmpty());
        assertEquals("ammo_not_recipe_backed:test:absent_ammo",
                snapshot.rejectedGunLinks().get(gun));
    }

    @Test
    void invalidReplacementCannotPartiallyReplaceAPriorPublication() {
        ResourceLocation gun = id("test:gun");
        ResourceLocation ammo = id("test:ammo");
        Map<ResourceLocation, BlueprintData> catalog = catalog(
                Set.of(gun), Set.of(ammo));
        BlueprintAmmoAssociationManager manager = new BlueprintAmmoAssociationManager();
        assertTrue(manager.publish(
                new TaCZAmmoAssociationAdapter.Capture(
                        1, Map.of(gun, Set.of(ammo)), Map.of()),
                catalog,
                1L));
        var previous = manager.publication();

        assertThrows(IllegalArgumentException.class, () -> manager.publish(
                new TaCZAmmoAssociationAdapter.Capture(
                        1, Map.of(id("test:other"), Set.of(ammo)), Map.of()),
                catalog,
                2L));

        assertSame(previous, manager.publication());
        assertFalse(manager.publish(null, catalog, 2L));
        assertSame(previous, manager.publication());
    }

    @Test
    void snapshotRejectsMismatchedReverseAndOverlappingPartitions() {
        ResourceLocation gun = id("test:gun");
        ResourceLocation ammo = id("test:ammo");
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintAmmoAssociationSnapshot(
                        1L,
                        1L,
                        Set.of(gun),
                        Set.of(ammo),
                        Map.of(gun, ammo),
                        Map.of(),
                        Set.of(),
                        Map.of(),
                        Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintAmmoAssociationSnapshot(
                        1L,
                        1L,
                        Set.of(gun),
                        Set.of(ammo),
                        Map.of(gun, ammo),
                        Map.of(ammo, Set.of(gun)),
                        Set.of(gun),
                        Map.of(ammo, Set.of(gun)),
                        Map.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintAmmoAssociationSnapshot(
                        1L,
                        1L,
                        Set.of(gun),
                        Set.of(gun),
                        Map.of(),
                        Map.of(),
                        Set.of(),
                        Map.of(),
                        Map.of(gun, "missing_tacz_ammo_id")));
    }

    private static Map<ResourceLocation, BlueprintData> catalog(
            Set<ResourceLocation> guns,
            Set<ResourceLocation> ammo) {
        Map<ResourceLocation, BlueprintData> result = new LinkedHashMap<>();
        guns.forEach(id -> result.put(id, blueprint(id, BlueprintKind.GUN)));
        ammo.forEach(id -> result.put(id, blueprint(id, BlueprintKind.AMMO)));
        return result;
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

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
