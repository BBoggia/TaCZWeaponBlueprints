package com.gamergaming.taczweaponblueprints.villager.stock;

import net.neoforged.fml.common.EventBusSubscriber;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModVillagers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class StockTradeEvents {
    private StockTradeEvents(){}
    @SubscribeEvent public static void trades(VillagerTradesEvent event){
        if(!ModVillagers.owns(event.getType()))return;
        for(int level=1;level<=5;level++){var list=event.getTrades().get(level);list.add(new Factory(level,0));list.add(new Factory(level,1));}
    }
    public record Factory(int level,int index) implements VillagerTrades.ItemListing {
        @Override public MerchantOffer getOffer(Entity entity,RandomSource random){return entity instanceof Villager v?StockService.factory(v,level,index):null;}
    }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){StockCatalog.clear();}
}
