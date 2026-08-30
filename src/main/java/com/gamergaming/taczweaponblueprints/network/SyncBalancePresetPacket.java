package com.gamergaming.taczweaponblueprints.network;

import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Keeps command-driven server preset changes visible in the synced client config. */
public final class SyncBalancePresetPacket {
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

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() ->
                ModConfigs.BLUEPRINT.acceptSynchronizedBalancePreset(preset));
        context.setPacketHandled(true);
    }

    BlueprintBalancePreset preset() {
        return preset;
    }
}
