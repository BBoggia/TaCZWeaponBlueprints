package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.research.designer.DesignerCaptureTransfer.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record DesignerCaptureRequestPacket(Request request) {
    public DesignerCaptureRequestPacket(FriendlyByteBuf b){this(new Request(b.readUUID(),b.readEnum(Action.class),b.readVarInt()));}
    public void toBytes(FriendlyByteBuf b){b.writeUUID(request.id());b.writeEnum(request.action());b.writeVarInt(request.index());}
    public void handle(java.util.function.Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()->{
            var player=context.getSender();if(player!=null) com.gamergaming.taczweaponblueprints.research.designer.server.DesignerCaptureRequests.handle(player,request);
        });context.setPacketHandled(true);
    }
}
