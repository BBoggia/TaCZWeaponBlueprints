package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** Applies permanent blueprint discovery using server-authoritative state. */
public final class BlueprintDiscoveryService {
    private BlueprintDiscoveryService() {
    }

    public static DiscoveryResult discoverInventoryBlueprint(ServerPlayer player, ItemStack stack) {
        if (player == null) {
            return DiscoveryResult.DATA_UNAVAILABLE;
        }
        if (stack == null || stack.isEmpty() || !(stack.getItem() instanceof BlueprintItem)) {
            return DiscoveryResult.NOT_BLUEPRINT_ITEM;
        }
        var config = ModConfigs.BLUEPRINT.progressionSnapshot();
        if (!config.blueprintsEnabled() || !config.discoveryTrackingEnabled()) {
            return DiscoveryResult.TRACKING_DISABLED;
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve()
                .orElse(null);
        DiscoveryResult result = discover(
                data,
                BlueprintItem.getBpId(stack),
                BlueprintDataManager.SERVER,
                true);
        if (result == DiscoveryResult.DISCOVERED) {
            ResourceLocation blueprintId = ResourceLocation.tryParse(BlueprintItem.getBpId(stack));
            if (blueprintId != null) {
                ResearchPointAwardDispatcher.blueprintTransitions(
                        player, data, blueprintId, true, false);
            }
            BlueprintProgressionSyncScheduler.markDirty(player);
        }
        return result;
    }

    static DiscoveryResult discover(
            IPlayerRecipeData data,
            String blueprintId,
            BlueprintDataManager catalog,
            boolean trackingEnabled) {
        if (!trackingEnabled) {
            return DiscoveryResult.TRACKING_DISABLED;
        }
        if (data == null || catalog == null) {
            return DiscoveryResult.DATA_UNAVAILABLE;
        }

        if (data.hasDiscoveredBlueprint(blueprintId)) {
            return DiscoveryResult.ALREADY_DISCOVERED;
        }
        if (data.getDiscoveredBlueprints().size() >= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            return DiscoveryResult.CAPACITY_REACHED;
        }
        if (catalog.getBlueprintData(blueprintId) == null) {
            return DiscoveryResult.INVALID_BLUEPRINT;
        }
        if (data.discoverBlueprint(blueprintId)) {
            return DiscoveryResult.DISCOVERED;
        }
        return data.hasDiscoveredBlueprint(blueprintId)
                ? DiscoveryResult.ALREADY_DISCOVERED
                : DiscoveryResult.CAPACITY_REACHED;
    }

    public enum DiscoveryResult {
        DISCOVERED,
        ALREADY_DISCOVERED,
        TRACKING_DISABLED,
        NOT_BLUEPRINT_ITEM,
        INVALID_BLUEPRINT,
        CAPACITY_REACHED,
        DATA_UNAVAILABLE
    }
}
