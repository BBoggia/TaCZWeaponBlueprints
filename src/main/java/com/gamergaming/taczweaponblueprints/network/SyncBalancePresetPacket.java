package com.gamergaming.taczweaponblueprints.network;


import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset;

import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Keeps command-driven server preset changes visible in the synced client config. */
public final class SyncBalancePresetPacket implements CustomPacketPayload {
    public static final Type<SyncBalancePresetPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("sync_balance_preset"));
    public static final StreamCodec<RegistryFriendlyByteBuf, SyncBalancePresetPacket> STREAM_CODEC =
            StreamCodec.ofMember(SyncBalancePresetPacket::toBytes, SyncBalancePresetPacket::new);

    @Override
    public Type<SyncBalancePresetPacket> type() { return TYPE; }

    private final BlueprintBalancePreset preset;

    public SyncBalancePresetPacket(BlueprintBalancePreset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("balance preset packet cannot be null");
        }
        this.preset = preset;
    }

    public SyncBalancePresetPacket(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        BlueprintBalancePreset[] values = BlueprintBalancePreset.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid synchronized balance preset");
        }
        this.preset = values[ordinal];
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(preset.ordinal());
    }

    public void handle(IPayloadContext context) {
        new ClientHandler().handle(context);
    }

    /** Loaded only when this clientbound payload is handled, never during server registration. */
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext contextSupplier) {
            IPayloadContext context = contextSupplier;
            context.enqueueWork(() ->
                    ModConfigs.BLUEPRINT.acceptSynchronizedBalancePreset(preset));
        }
    }

    BlueprintBalancePreset preset() {
        return preset;
    }
}
