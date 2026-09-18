package com.gamergaming.taczweaponblueprints.villager;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID)
public final class VillagerMerchantEvents {
    private VillagerMerchantEvents() {}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if(event.phase!=TickEvent.Phase.END)return;
        com.gamergaming.taczweaponblueprints.villager.stock.StockPublicationCoordinator.settle();
        for(var player:event.getServer().getPlayerList().getPlayers())
            if(player.containerMenu instanceof MerchantMenu menu)
                ((VillagerMerchantMenuBridge)menu).taczweaponblueprints$validateQuote(player);
    }
}
