package com.gamergaming.taczweaponblueprints.network;

import java.util.UUID;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.DomainPolicy;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record SettingsReplyPacket(UUID request, SettingsTransaction.Status status, SettingsSnapshot snapshot) implements CustomPacketPayload {
    public static final int MAX_BYTES = 196_608;
    public static final Type<SettingsReplyPacket> TYPE = new Type<>(com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("settings_reply"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsReplyPacket> STREAM_CODEC = StreamCodec.ofMember(SettingsReplyPacket::write, SettingsReplyPacket::read);
    @Override public Type<SettingsReplyPacket> type() { return TYPE; }
    public boolean withinLimit() { return snapshot == null || SettingsRequestPacket.mapFits(snapshot.values(), MAX_BYTES - 1024); }
    private static SettingsReplyPacket read(FriendlyByteBuf b) {
        if (b.readableBytes() > MAX_BYTES) throw new IllegalArgumentException("Settings snapshot too large");
        UUID request = b.readUUID(); var status = b.readEnum(SettingsTransaction.Status.class);
        SettingsSnapshot snapshot = null;
        if (b.readBoolean()) {
            UUID session = b.readUUID(); String revision = b.readUtf(64); boolean editable = b.readBoolean();
            var profile = new SettingsPresentation.Profile(b.readUtf(256), b.readBoolean(), domain(b), domain(b), domain(b));
            snapshot = new SettingsSnapshot(session, revision, editable, SettingsRequestPacket.readMap(b, MAX_BYTES), profile);
        }
        return new SettingsReplyPacket(request, status, snapshot);
    }
    private void write(FriendlyByteBuf b) {
        if (!withinLimit()) throw new IllegalArgumentException("Settings snapshot too large");
        b.writeUUID(request); b.writeEnum(status); b.writeBoolean(snapshot != null);
        if (snapshot == null) return;
        b.writeUUID(snapshot.session()); b.writeUtf(snapshot.revision(), 64); b.writeBoolean(snapshot.editable());
        var profile = snapshot.profile(); b.writeUtf(profile.id(), 256); b.writeBoolean(profile.available());
        domain(b, profile.weapons()); domain(b, profile.ammo()); domain(b, profile.attachments());
        SettingsRequestPacket.writeMap(b, snapshot.values());
    }
    private static DomainPolicy domain(FriendlyByteBuf b) { return new DomainPolicy(b.readBoolean(), b.readBoolean()); }
    private static void domain(FriendlyByteBuf b, DomainPolicy value) { b.writeBoolean(value.treeEnabled()); b.writeBoolean(value.researchEnabled()); }
    public void handle(IPayloadContext context) { new ClientHandler().handle(context); }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext context) { context.enqueueWork(() -> com.gamergaming.taczweaponblueprints.client.settings.ClientSettingsAccess.accept(SettingsReplyPacket.this)); }
    }
}
