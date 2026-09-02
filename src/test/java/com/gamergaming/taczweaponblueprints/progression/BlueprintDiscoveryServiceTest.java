package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintDiscoveryService.DiscoveryResult;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class BlueprintDiscoveryServiceTest {
    @AfterEach
    void clearCatalog() {
        BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of());
    }

    @Test
    void discoversAValidInventoryBlueprintExactlyOnce() {
        ResourceLocation blueprintId = new ResourceLocation("test", "ak47");
        BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of(blueprintId, blueprint(blueprintId)));
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(25);

        assertEquals(
                DiscoveryResult.DISCOVERED,
                BlueprintDiscoveryService.discover(
                        data,
                        blueprintId.toString(),
                        BlueprintDataManager.SERVER,
                        true));
        assertEquals(
                DiscoveryResult.ALREADY_DISCOVERED,
                BlueprintDiscoveryService.discover(
                        data,
                        blueprintId.toString(),
                        BlueprintDataManager.SERVER,
                        true));

        assertEquals(Set.of(blueprintId.toString()), data.getDiscoveredBlueprints());
        assertTrue(data.getLearnedBlueprints().isEmpty());
        assertEquals(25, data.getResearchPoints());
    }

    @Test
    void trackingToggleSuppressesInventoryDiscoveryWithoutDeletingState() {
        ResourceLocation existingId = new ResourceLocation("test", "existing");
        ResourceLocation newId = new ResourceLocation("test", "new_blueprint");
        BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of(newId, blueprint(newId)));
        PlayerRecipeData data = new PlayerRecipeData();
        data.discoverBlueprint(existingId.toString());

        assertEquals(
                DiscoveryResult.TRACKING_DISABLED,
                BlueprintDiscoveryService.discover(
                        data,
                        newId.toString(),
                        BlueprintDataManager.SERVER,
                        false));
        assertEquals(Set.of(existingId.toString()), data.getDiscoveredBlueprints());
    }

    @Test
    void rejectsInvalidAndUnavailableBlueprintIds() {
        PlayerRecipeData data = new PlayerRecipeData();

        assertEquals(
                DiscoveryResult.INVALID_BLUEPRINT,
                BlueprintDiscoveryService.discover(
                        data,
                        null,
                        BlueprintDataManager.SERVER,
                        true));
        assertEquals(
                DiscoveryResult.INVALID_BLUEPRINT,
                BlueprintDiscoveryService.discover(
                        data,
                        "removed:missing",
                        BlueprintDataManager.SERVER,
                        true));
        assertTrue(data.getDiscoveredBlueprints().isEmpty());
    }

    @Test
    void retainsUnavailableDiscoveryHistoryWithoutRevalidatingIt() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.discoverBlueprint("removed:historical");

        assertEquals(
                DiscoveryResult.ALREADY_DISCOVERED,
                BlueprintDiscoveryService.discover(
                        data,
                        "removed:historical",
                        BlueprintDataManager.SERVER,
                        true));
        assertEquals(Set.of("removed:historical"), data.getDiscoveredBlueprints());
    }

    @Test
    void capacityFailureDoesNotMutateProgression() {
        ResourceLocation overflowId = new ResourceLocation("test", "overflow");
        BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of(overflowId, blueprint(overflowId)));
        PlayerRecipeData data = new PlayerRecipeData();
        for (int index = 0; index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION; index++) {
            assertTrue(data.discoverBlueprint("history:blueprint_" + index));
        }

        assertEquals(
                DiscoveryResult.CAPACITY_REACHED,
                BlueprintDiscoveryService.discover(
                        data,
                        overflowId.toString(),
                        BlueprintDataManager.SERVER,
                        true));
        assertFalse(data.hasDiscoveredBlueprint(overflowId.toString()));
        assertEquals(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION, data.getDiscoveredBlueprints().size());
    }

    @Test
    void missingCapabilityDataFailsClosed() {
        ResourceLocation blueprintId = new ResourceLocation("test", "ak47");
        BlueprintDataManager.SERVER.setBlueprintDataMap(Map.of(blueprintId, blueprint(blueprintId)));

        assertEquals(
                DiscoveryResult.DATA_UNAVAILABLE,
                BlueprintDiscoveryService.discover(
                        null,
                        blueprintId.toString(),
                        BlueprintDataManager.SERVER,
                        true));
    }

    private static BlueprintData blueprint(ResourceLocation blueprintId) {
        return new BlueprintData(
                blueprintId.toString(),
                "item.test.name",
                "item.test.tooltip",
                new ResourceLocation("test", "recipe/" + blueprintId.getPath()),
                null,
                "rifle",
                new ResourceLocation("test", "display/rifle"));
    }
}
