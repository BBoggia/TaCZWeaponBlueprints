package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.tacz.guns.resource.pojo.data.gun.Bolt;

class PhysicalItemBlueprintResolverTest {
    @Test
    void emptyOpenBoltGunIgnoresTaCZsNonUsableBarrelFlag() {
        assertFalse(PhysicalItemBlueprintResolver.isLoadedGun(
                0,
                true,
                Bolt.OPEN_BOLT));
    }

    @Test
    void openBoltGunWithMagazineAmmoRemainsLoaded() {
        assertTrue(PhysicalItemBlueprintResolver.isLoadedGun(
                1,
                true,
                Bolt.OPEN_BOLT));
    }

    @Test
    void chamberedRoundStillLoadsClosedBoltAndManualActionGuns() {
        assertTrue(PhysicalItemBlueprintResolver.isLoadedGun(
                0,
                true,
                Bolt.CLOSED_BOLT));
        assertTrue(PhysicalItemBlueprintResolver.isLoadedGun(
                0,
                true,
                Bolt.MANUAL_ACTION));
    }

    @Test
    void unresolvedGunDataFailsConservativelyForReportedChamberedRound() {
        assertTrue(PhysicalItemBlueprintResolver.isLoadedGun(0, true, null));
        assertFalse(PhysicalItemBlueprintResolver.isLoadedGun(0, false, null));
    }
}
