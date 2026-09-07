package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.research.designer.DesignerCaptureTransfer;
import com.gamergaming.taczweaponblueprints.research.designer.DesignerCaptureTransfer.*;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

public record DesignerCaptureReplyPacket(Reply reply) {
    public DesignerCaptureReplyPacket(FriendlyByteBuf b){this(new Reply(b.readUUID(),b.readEnum(Status.class),b.readVarInt(),b.readVarInt(),b.readUtf(64),b.readByteArray(DesignerCaptureTransfer.CHUNK_BYTES)));}
    public void toBytes(FriendlyByteBuf b){b.writeUUID(reply.id());b.writeEnum(reply.status());b.writeVarInt(reply.index());b.writeVarInt(reply.totalBytes());b.writeUtf(reply.checksum(),64);b.writeByteArray(reply.payload());}
    public void handle(java.util.function.Supplier<NetworkEvent.Context> supplier){
        var context=supplier.get();context.enqueueWork(()-> net.minecraftforge.fml.DistExecutor.unsafeRunWhenOn(net.minecraftforge.api.distmarker.Dist.CLIENT,
                () -> () -> com.gamergaming.taczweaponblueprints.client.designer.ClientDesignerCapture.accept(reply)));
        context.setPacketHandled(true);
    }
}
