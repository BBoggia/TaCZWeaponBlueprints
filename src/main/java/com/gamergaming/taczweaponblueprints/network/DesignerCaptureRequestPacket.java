package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.research.designer.DesignerCaptureTransfer.*;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record DesignerCaptureRequestPacket(Request request) implements CustomPacketPayload {
    public static final Type<DesignerCaptureRequestPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("designer_capture_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DesignerCaptureRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(DesignerCaptureRequestPacket::toBytes, DesignerCaptureRequestPacket::new);

    @Override
    public Type<DesignerCaptureRequestPacket> type() { return TYPE; }

    public DesignerCaptureRequestPacket(FriendlyByteBuf b){this(new Request(b.readUUID(),b.readEnum(Action.class),b.readVarInt()));}
    public void toBytes(FriendlyByteBuf b){b.writeUUID(request.id());b.writeEnum(request.action());b.writeVarInt(request.index());}
    public void handle(IPayloadContext supplier){
        var context=supplier;context.enqueueWork(()->{
            var player=NetworkHandler.sender(context);if(player!=null) com.gamergaming.taczweaponblueprints.research.designer.server.DesignerCaptureRequests.handle(player,request);
        });
    }
}
