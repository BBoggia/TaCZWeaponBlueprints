package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.research.designer.DesignerCaptureTransfer;
import com.gamergaming.taczweaponblueprints.research.designer.DesignerCaptureTransfer.*;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

public record DesignerCaptureReplyPacket(Reply reply) implements CustomPacketPayload {
    public static final Type<DesignerCaptureReplyPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("designer_capture_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, DesignerCaptureReplyPacket> STREAM_CODEC =
            StreamCodec.ofMember(DesignerCaptureReplyPacket::toBytes, DesignerCaptureReplyPacket::new);

    @Override
    public Type<DesignerCaptureReplyPacket> type() { return TYPE; }

    public DesignerCaptureReplyPacket(FriendlyByteBuf b){this(new Reply(b.readUUID(),b.readEnum(Status.class),b.readVarInt(),b.readVarInt(),b.readUtf(64),b.readByteArray(DesignerCaptureTransfer.CHUNK_BYTES)));}
    public void toBytes(FriendlyByteBuf b){b.writeUUID(reply.id());b.writeEnum(reply.status());b.writeVarInt(reply.index());b.writeVarInt(reply.totalBytes());b.writeUtf(reply.checksum(),64);b.writeByteArray(reply.payload());}
    public void handle(IPayloadContext context) {
        new ClientHandler().handle(context);
    }

    /** Loaded only when this clientbound payload is handled, never during server registration. */
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext supplier) {
            var context=supplier;context.enqueueWork(()-> com.gamergaming.taczweaponblueprints.client.designer.ClientDesignerCapture.accept(reply));
        }
    }
}
