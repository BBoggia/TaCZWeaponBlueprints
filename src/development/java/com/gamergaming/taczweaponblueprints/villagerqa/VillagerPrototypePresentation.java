package com.gamergaming.taczweaponblueprints.villagerqa;

import java.util.*;
import java.util.function.Supplier;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.villager.OwnedVillagerOffer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.PlayerContainerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.network.*;
import net.minecraftforge.network.simple.SimpleChannel;

/** Presentation experiment only. The production channel/packet contract is designed in Stage 5. */
@Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID,bus=Mod.EventBusSubscriber.Bus.MOD)
public final class VillagerPrototypePresentation {
    private static SimpleChannel channel;
    @SubscribeEvent public static void setup(FMLCommonSetupEvent event) {
        if(VillagerPrototypeStock.root()==null||Boolean.getBoolean("tacz.villagerQA.lifecycle"))return;
        channel=NetworkRegistry.newSimpleChannel(new ResourceLocation(TaCZWeaponBlueprints.MODID,"villager_prototype"),()->"1","1"::equals,"1"::equals);
        channel.registerMessage(0,Status.class,Status::encode,Status::decode,Status::handle,Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }
    public record Status(int containerId,List<Integer> unavailable) {
        public Status {
            unavailable=List.copyOf(unavailable);
            if(containerId<0 || containerId>255 || unavailable.size()>256 || new HashSet<>(unavailable).size()!=unavailable.size()
                    || unavailable.stream().anyMatch(i->i<0 || i>=256))throw new IllegalArgumentException("Invalid prototype preview");
        }
        void encode(FriendlyByteBuf buffer){buffer.writeVarInt(containerId);buffer.writeVarInt(unavailable.size());unavailable.forEach(buffer::writeVarInt);}
        static Status decode(FriendlyByteBuf buffer){
            int id=buffer.readVarInt(),count=buffer.readVarInt();if(count<0 || count>256)throw new IllegalArgumentException("Oversized prototype preview");
            var values=new ArrayList<Integer>(count);for(int i=0;i<count;i++)values.add(buffer.readVarInt());return new Status(id,values);
        }
        void handle(Supplier<NetworkEvent.Context> supplier){
            var context=supplier.get();context.enqueueWork(()->DistExecutor.unsafeRunWhenOn(Dist.CLIENT,()->()->VillagerPrototypeClient.accept(this)));context.setPacketHandled(true);
        }
    }
    @Mod.EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
    public static final class ServerEvents {
        @SubscribeEvent public static void opened(PlayerContainerEvent.Open event){
            if(channel==null || !(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof MerchantMenu menu))return;
            var unavailable=new ArrayList<Integer>();var offers=menu.getOffers();if(offers.size()>256)return;
            for(int i=0;i<offers.size();i++){
                var terms=((OwnedVillagerOffer)offers.get(i)).taczweaponblueprints$terms();
                if(terms!=null && !terms.available())unavailable.add(i);
            }
            channel.send(PacketDistributor.PLAYER.with(()->player),new Status(menu.containerId,unavailable));
        }
    }
}
