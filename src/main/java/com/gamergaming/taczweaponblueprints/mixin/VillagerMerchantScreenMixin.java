package com.gamergaming.taczweaponblueprints.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MerchantScreen.class)
public abstract class VillagerMerchantScreenMixin extends AbstractContainerScreen<MerchantMenu> {
    @Shadow private int shopItem;
    @Shadow int scrollOff;
    protected VillagerMerchantScreenMixin(MerchantMenu menu,Inventory inventory,Component title){super(menu,inventory,title);}
    @Inject(method="render",at=@At("TAIL"))
    private void tradeInformation(GuiGraphics graphics,int mouseX,int mouseY,float partialTick,CallbackInfo ci){
        com.gamergaming.taczweaponblueprints.client.ClientVillagerTradeStatus.render(graphics,menu,font,leftPos,topPos,scrollOff,shopItem,mouseX,mouseY);
    }
}
