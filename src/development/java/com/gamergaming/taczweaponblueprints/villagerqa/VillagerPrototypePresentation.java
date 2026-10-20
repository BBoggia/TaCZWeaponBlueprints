package com.gamergaming.taczweaponblueprints.villagerqa;

import java.util.*;
import java.util.function.Supplier;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.villager.OwnedVillagerOffer;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.MerchantMenu;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.bus.api.SubscribeEvent;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.network.*;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Presentation experiment only. The production channel/packet contract is designed in Stage 5. */
@EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID,bus=EventBusSubscriber.Bus.MOD)
public final class VillagerPrototypePresentation {
    private static boolean enabled;
    @SubscribeEvent public static void setup(RegisterPayloadHandlersEvent event) {
        enabled = VillagerPrototypeStock.root()!=null && !Boolean.getBoolean("tacz.villagerQA.lifecycle");
        event.registrar("1").optional().playToClient(Status.TYPE, Status.CODEC,
                (packet, context) -> VillagerPrototypeClient.accept(packet));
    }
    public record Status(int containerId,List<Integer> unavailable) implements CustomPacketPayload {
        public static final Type<Status> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(TaCZWeaponBlueprints.MODID, "villager_prototype"));
        public static final StreamCodec<RegistryFriendlyByteBuf, Status> CODEC = StreamCodec.ofMember(Status::encode, Status::decode);
        @Override public Type<Status> type() { return TYPE; }
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
    }
    @EventBusSubscriber(modid=TaCZWeaponBlueprints.MODID)
    public static final class ServerEvents {
        @SubscribeEvent public static void opened(PlayerContainerEvent.Open event){
            if(!enabled || !(event.getEntity() instanceof ServerPlayer player) || !(event.getContainer() instanceof MerchantMenu menu))return;
            var unavailable=new ArrayList<Integer>();var offers=menu.getOffers();if(offers.size()>256)return;
            for(int i=0;i<offers.size();i++){
                var terms=((OwnedVillagerOffer)offers.get(i)).taczweaponblueprints$terms();
                if(terms!=null && !terms.available())unavailable.add(i);
            }
            PacketDistributor.sendToPlayer(player,new Status(menu.containerId,unavailable));
        }
    }
}
