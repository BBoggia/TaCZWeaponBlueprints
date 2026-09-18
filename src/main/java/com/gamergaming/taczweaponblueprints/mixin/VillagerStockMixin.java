package com.gamergaming.taczweaponblueprints.mixin;

import com.gamergaming.taczweaponblueprints.villager.stock.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Villager.class)
public abstract class VillagerStockMixin extends AbstractVillager implements StockVillagerBridge {
    protected VillagerStockMixin(EntityType<? extends AbstractVillager> type,Level level){super(type,level);}
    @Unique private StockState taczweaponblueprints$state;
    @Unique private boolean taczweaponblueprints$generating;
    @Override public StockState taczweaponblueprints$stock(){return taczweaponblueprints$state;}
    @Override public void taczweaponblueprints$stock(StockState state){taczweaponblueprints$state=state;}
    @Override public MerchantOffers taczweaponblueprints$rawOffers(){return offers;}
    @Override public boolean taczweaponblueprints$generating(){return taczweaponblueprints$generating;}
    @Inject(method="updateTrades",at=@At("HEAD")) private void startingTrades(CallbackInfo ci){taczweaponblueprints$generating=true;}
    @Inject(method="updateTrades",at=@At("RETURN")) private void finishedTrades(CallbackInfo ci){
        taczweaponblueprints$generating=false;if(offers!=null)StockService.refresh((Villager)(Object)this,offers,false);
    }
    @Inject(method="restock",at=@At("HEAD")) private void restocking(CallbackInfo ci){if(offers!=null)StockService.refresh((Villager)(Object)this,offers,true);}
    @Inject(method="setVillagerData",at=@At("HEAD")) private void professionChange(VillagerData data,CallbackInfo ci){
        if(((Villager)(Object)this).getVillagerData().getProfession()!=data.getProfession())taczweaponblueprints$state=null;
    }
    @Inject(method="setOffers",at=@At("RETURN")) private void convertedOffers(MerchantOffers value,CallbackInfo ci){
        var saved=((StockOffersBridge)value).taczweaponblueprints$stock();if(saved!=null)taczweaponblueprints$state=saved;
    }
    @Inject(method="addAdditionalSaveData",at=@At("RETURN")) private void savePlan(CompoundTag tag,CallbackInfo ci){
        if(taczweaponblueprints$state!=null)tag.put(StockState.TAG,taczweaponblueprints$state.save());
    }
    @Inject(method="readAdditionalSaveData",at=@At("RETURN")) private void loadPlan(CompoundTag tag,CallbackInfo ci){
        if(!tag.contains(StockState.TAG))return;
        try{taczweaponblueprints$state=StockState.read(tag.getCompound(StockState.TAG));}
        catch(RuntimeException failure){taczweaponblueprints$state=new StockState(StockService.profession((Villager)(Object)this),0);taczweaponblueprints$state.invalid=true;}
        if(offers!=null)((StockOffersBridge)offers).taczweaponblueprints$stock(taczweaponblueprints$state);
    }
}
