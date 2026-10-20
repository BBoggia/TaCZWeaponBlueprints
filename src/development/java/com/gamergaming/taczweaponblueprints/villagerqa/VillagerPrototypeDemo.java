package com.gamergaming.taczweaponblueprints.villagerqa;

import java.util.UUID;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass;
import com.gamergaming.taczweaponblueprints.villager.*;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.*;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

/** Creates a controlled native screen for manual inspection in the opt-in QA world. */
@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
public final class VillagerPrototypeDemo {
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){
        if(VillagerPrototypeStock.root()==null || !Boolean.getBoolean("tacz.villagerQA.demo") || !(event.getEntity() instanceof ServerPlayer player))return;
        player.server.execute(()->{
            var world=player.serverLevel();for(int x=-4;x<5;x++)for(int z=-4;z<5;z++)world.setBlockAndUpdate(new BlockPos(x,79,z),Blocks.STONE.defaultBlockState());
            player.teleportTo(world,.5,80,.5,0,0);
            var v=new Villager(EntityType.VILLAGER,world);v.setNoAi(true);v.moveTo(2,80,2);v.setVillagerData(v.getVillagerData().setProfession(ModVillagers.BALLISTICS_RESEARCHER.get()).setLevel(2));world.addFreshEntity(v);
            var fragment=BlueprintFragmentItem.createAnalysis(ResourceLocation.parse("tacz:glock_17"),AnalysisRewardClass.PROTECTED_PREREQUISITES);
            var suspended=VillagerPrototypeStock.offer("unavailable",new ItemStack(ModItems.SCRAP.get(),8),ItemStack.EMPTY,fragment,3,5);
            var terms=((OwnedVillagerOffer)suspended).taczweaponblueprints$terms();
            ((OwnedVillagerOffer)suspended).taczweaponblueprints$terms(new VillagerOfferTerms(terms.slot(),terms.minimumPrimary(),false,1));
            var sold=VillagerPrototypeStock.offer("sold_out",new ItemStack(ModItems.SCRAP.get(),4),ItemStack.EMPTY,new ItemStack(ModItems.RESEARCH_NOTE.get()),4,1);sold.setToOutOfStock();
            var offers=new MerchantOffers();offers.add(suspended);offers.add(sold);v.setOffers(offers);v.setTradingPlayer(player);v.openTradingScreen(player,v.getDisplayName(),2);
        });
    }
}
