package com.gamergaming.taczweaponblueprints.client;

import net.neoforged.fml.common.EventBusSubscriber;

import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.network.SyncVillagerTradeStatusPacket;
import com.gamergaming.taczweaponblueprints.villager.VillagerTradeStatus;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.bus.api.SubscribeEvent;

/** Data is bound to the actual menu instance and never participates in a trade. */
@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID,value=Dist.CLIENT)
public final class ClientVillagerTradeStatus {
    private static final com.gamergaming.taczweaponblueprints.villager.VillagerStatusInbox INBOX=new com.gamergaming.taczweaponblueprints.villager.VillagerStatusInbox();
    private ClientVillagerTradeStatus(){}
    public static void accept(SyncVillagerTradeStatusPacket packet){
        var player=Minecraft.getInstance().player;
        if(player!=null&&player.containerMenu instanceof MerchantMenu open)INBOX.accept(open,open.containerId,packet);
    }
    public static Optional<VillagerTradeStatus.Row> row(MerchantMenu open,int index){
        if(index<0||index>=open.getOffers().size())return Optional.empty();
        return INBOX.current(open).stream().flatMap(p->p.rows().stream()).filter(r->r.index()==index&&r.fingerprint().equals(VillagerTradeStatus.fingerprint(open.getOffers().get(index),net.minecraft.client.Minecraft.getInstance().player.registryAccess()))).findFirst();
    }
    public static void render(GuiGraphics graphics,MerchantMenu open,Font font,int left,int top,int scroll,int selected,int mouseX,int mouseY){
        if(open.getOffers().isEmpty()&&INBOX.current(open).isPresent()) {
            var lines=font.split(Component.translatable("merchant.taczweaponblueprints.empty"),82);
            for(int line=0;line<Math.min(lines.size(),8);line++)
                graphics.drawString(font,lines.get(line),left+9,top+22+line*10,0x594e43,false);
        }
        int index=-1;
        // Native row arrow: items keep vanilla tooltips, prices and interaction.
        if(mouseX>=left+57&&mouseX<left+73&&mouseY>=top+18&&mouseY<top+158)index=scroll+(mouseY-top-18)/20;
        else if(mouseX>=left+186&&mouseX<left+208&&mouseY>=top+35&&mouseY<top+56)index=selected;
        var status=row(open,index);if(status.isEmpty())return;var r=status.orElseThrow();var text=new ArrayList<Component>();
        if(r.reason()!=VillagerTradeStatus.Reason.AVAILABLE)text.add(Component.translatable("merchant.taczweaponblueprints.unavailable."+r.reason().name().toLowerCase(Locale.ROOT)).withStyle(net.minecraft.ChatFormatting.GOLD));
        else if(open.getOffers().get(index).isOutOfStock())text.add(Component.translatable("merchant.deprecated"));
        if((r.flags()&1)!=0)text.add(Component.translatable("tooltip.taczweaponblueprints.traded_weapon"));
        if((r.flags()&2)!=0)text.add(Component.translatable("merchant.taczweaponblueprints.protected_research"));
        if((r.flags()&4)!=0){
            text.add(Component.translatable("merchant.taczweaponblueprints.empty_magazine"));
            if(r.family().isPresent()){
                text.add(Component.translatable("merchant.taczweaponblueprints.magazine_family",open.getOffers().get(index).getResult().getHoverName()));
                text.add(Component.translatable("merchant.taczweaponblueprints.magazine_capacity",com.tacz.guns.api.item.builder.AmmoItemBuilder.create().setId(r.ammo().orElseThrow()).setCount(1).build().getHoverName(),r.capacity()));
            }
        }
        if(!text.isEmpty()) {
            int width=Math.max(80,Math.min(260,Minecraft.getInstance().getWindow().getGuiScaledWidth()-24));
            graphics.renderTooltip(font,text.stream().flatMap(line->font.split(line,width).stream()).toList(),mouseX,mouseY);
        }
    }
    @SubscribeEvent public static void closing(ScreenEvent.Closing event){INBOX.close();}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){INBOX.disconnect();}
}
