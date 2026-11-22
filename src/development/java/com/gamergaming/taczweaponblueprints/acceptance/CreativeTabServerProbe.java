package com.gamergaming.taczweaponblueprints.acceptance;

import com.gamergaming.taczweaponblueprints.init.ModCreativeTabs;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;

/** Exercises the normal tab enumeration against the packaged mod on a physical server. */
@Mod.EventBusSubscriber(modid = "taczblueprintsqa")
public final class CreativeTabServerProbe {
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void started(ServerStartedEvent event) {
        if (!Boolean.getBoolean("wrb.verifyCreativeTabs")) return;
        var server = event.getServer();
        CreativeModeTabs.tryRebuildTabContents(server.getWorldData().enabledFeatures(), true, server.registryAccess());
        var ammo = ModCreativeTabs.AMMO_BLUEPRINT_TAB.get().getDisplayItems();
        if (ammo.stream().noneMatch(stack -> BlueprintItem.getBlueprintId(stack).isPresent())) {
            throw new IllegalStateException("Dedicated server did not populate the ammo blueprint tab");
        }
        TaCZWeaponBlueprints.LOGGER.info("WRB_CREATIVE_SERVER passed with {} ammo blueprints", ammo.size());
    }
}
