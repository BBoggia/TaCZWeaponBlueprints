package com.gamergaming.taczweaponblueprints.mixin;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
@Mixin(MerchantOffers.class)
public abstract class MerchantOffersStockMixin implements StockOffersBridge {
    @Unique private StockState taczweaponblueprints$state;
    @Override public StockState taczweaponblueprints$stock(){return taczweaponblueprints$state;}
    @Override public void taczweaponblueprints$stock(StockState state){taczweaponblueprints$state=state;}
    @Shadow @Final @Mutable private static com.mojang.serialization.Codec<MerchantOffers> CODEC;
    @Inject(method="<clinit>",at=@At("RETURN")) private static void wrapStockCodec(CallbackInfo ci){
        CODEC=com.gamergaming.taczweaponblueprints.villager.OwnedOfferSerialization.withMetadata(CODEC,(offers,tag)->{
            if(!tag.contains(StockState.TAG))return;
            StockState state;
            try{state=StockState.read(tag.getCompound(StockState.TAG));}
            catch(RuntimeException failure){state=new StockState(TradeDefinition.Profession.GUNSMITH,0);state.invalid=true;}
            ((StockOffersBridge)offers).taczweaponblueprints$stock(state);
        },(offers,tag)->{
            var state=((StockOffersBridge)offers).taczweaponblueprints$stock();
            if(state!=null)tag.put(StockState.TAG,state.save());
        });
    }
    @Inject(method="copy",at=@At("RETURN")) private void copyStock(CallbackInfoReturnable<MerchantOffers> ci){
        if(taczweaponblueprints$state!=null)((StockOffersBridge)ci.getReturnValue()).taczweaponblueprints$stock(StockState.read(taczweaponblueprints$state.save()));
    }
}
