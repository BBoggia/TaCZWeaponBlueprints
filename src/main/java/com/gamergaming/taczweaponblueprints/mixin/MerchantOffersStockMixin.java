package com.gamergaming.taczweaponblueprints.mixin;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(MerchantOffers.class)
public abstract class MerchantOffersStockMixin implements StockOffersBridge {
    @Unique private StockState taczweaponblueprints$state;
    @Override public StockState taczweaponblueprints$stock(){return taczweaponblueprints$state;}
    @Override public void taczweaponblueprints$stock(StockState state){taczweaponblueprints$state=state;}
    @Inject(method="<init>(Lnet/minecraft/nbt/CompoundTag;)V",at=@At("RETURN")) private void readPlan(CompoundTag tag,CallbackInfo ci){
        if(!tag.contains(StockState.TAG))return;
        try{taczweaponblueprints$state=StockState.read(tag.getCompound(StockState.TAG));}
        catch(RuntimeException failure){taczweaponblueprints$state=new StockState(TradeDefinition.Profession.GUNSMITH,0);taczweaponblueprints$state.invalid=true;}
    }
    @Inject(method="createTag",at=@At("RETURN")) private void savePlan(CallbackInfoReturnable<CompoundTag> ci){
        if(taczweaponblueprints$state!=null)ci.getReturnValue().put(StockState.TAG,taczweaponblueprints$state.save());
    }
}
