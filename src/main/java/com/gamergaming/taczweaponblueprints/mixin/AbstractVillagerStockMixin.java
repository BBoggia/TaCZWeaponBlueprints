package com.gamergaming.taczweaponblueprints.mixin;
import com.gamergaming.taczweaponblueprints.villager.stock.StockService;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
@Mixin(AbstractVillager.class)
public abstract class AbstractVillagerStockMixin {
    @Inject(method="getOffers",at=@At("RETURN")) private void currentStock(CallbackInfoReturnable<MerchantOffers> ci){
        if((Object)this instanceof Villager v)StockService.refresh(v,ci.getReturnValue(),false);
    }
}
