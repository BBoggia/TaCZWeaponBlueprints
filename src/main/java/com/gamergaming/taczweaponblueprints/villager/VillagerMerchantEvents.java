package com.gamergaming.taczweaponblueprints.villager;

import net.neoforged.fml.common.EventBusSubscriber;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class VillagerMerchantEvents {
    private VillagerMerchantEvents() {}
    @SubscribeEvent public static void tick(ServerTickEvent.Post event) {

        com.gamergaming.taczweaponblueprints.villager.stock.StockPublicationCoordinator.settle();
        for(var player:event.getServer().getPlayerList().getPlayers())
            if(player.containerMenu instanceof MerchantMenu menu)
                ((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(player);
    }
}
