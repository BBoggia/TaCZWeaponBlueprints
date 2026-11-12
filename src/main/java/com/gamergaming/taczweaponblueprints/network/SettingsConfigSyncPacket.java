package com.gamergaming.taczweaponblueprints.network;

import java.util.Map;
import com.gamergaming.taczweaponblueprints.settings.*;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Full config publication for remote LAN guests, never replayed into the shared host config. */
public record SettingsConfigSyncPacket(Map<String, String> values) implements CustomPacketPayload {
    public static final Type<SettingsConfigSyncPacket> TYPE = new Type<>(com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("settings_config_sync"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SettingsConfigSyncPacket> STREAM_CODEC = StreamCodec.ofMember(SettingsConfigSyncPacket::write, SettingsConfigSyncPacket::read);
    public SettingsConfigSyncPacket {
        values = Map.copyOf(values);
        if (!values.keySet().equals(SettingsCatalog.entries().keySet()) || !SettingsRequestPacket.mapFits(values, SettingsReplyPacket.MAX_BYTES))
            throw new IllegalArgumentException("Incomplete or oversized settings sync");
    }
    @Override public Type<SettingsConfigSyncPacket> type() { return TYPE; }
    private void write(RegistryFriendlyByteBuf buffer) { SettingsRequestPacket.writeMap(buffer, values); }
    private static SettingsConfigSyncPacket read(RegistryFriendlyByteBuf buffer) {
        if (buffer.readableBytes() > SettingsReplyPacket.MAX_BYTES) throw new IllegalArgumentException("Settings sync too large");
        return new SettingsConfigSyncPacket(SettingsRequestPacket.readMap(buffer, SettingsReplyPacket.MAX_BYTES));
    }
    public void handle(IPayloadContext context) { new ClientHandler().handle(context); }
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext context) { context.enqueueWork(() -> {
            // Defense in depth: local hosts already share the server's authoritative wrappers.
            var minecraft = net.minecraft.client.Minecraft.getInstance();
            var connection = minecraft.getConnection();
            if (minecraft.hasSingleplayerServer() || connection == null || connection.getConnection() != context.connection()) return;
            var candidate = FzzySettingsAdapter.fromValues(values);
            var config = com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT;
            FzzySettingsAdapter.install(config, candidate, values.keySet());
            config.onSyncClient();
            config.save();
        }); }
    }
}
