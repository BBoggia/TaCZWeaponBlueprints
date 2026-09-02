package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.BlueprintRecyclerActionResultListener;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Correlates a Recycler response with the exact client request and input. */
public final class BlueprintRecyclerActionResultPacket {
    private final int containerId;
    private final int requestId;
    private final BlueprintRecyclerActionContract.ActionResult result;

    public BlueprintRecyclerActionResultPacket(
            int containerId,
            int requestId,
            BlueprintRecyclerActionContract.ActionResult result) {
        if (containerId < 0 || requestId < 1 || result == null) {
            throw new IllegalArgumentException("invalid Blueprint Recycler result packet");
        }
        this.containerId = containerId;
        this.requestId = requestId;
        this.result = result;
    }

    public BlueprintRecyclerActionResultPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readVarInt(),
                new BlueprintRecyclerActionContract.ActionResult(
                        readEnum(
                                buffer,
                                BlueprintRecyclerActionContract.Action.values(),
                                "result action"),
                        Optional.of(readId(buffer)),
                        readEnum(
                                buffer,
                                BlueprintRecyclerActionContract.ResultCode.values(),
                                "result code")));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(result.action().ordinal());
        buffer.writeUtf(
                result.inputId().orElseThrow().toString(),
                PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeVarInt(result.code().ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof BlueprintRecyclerActionResultListener listener
                    && minecraft.player != null
                    && minecraft.player.containerMenu.containerId == containerId) {
                listener.acceptRecyclerActionResult(requestId, result);
            }
        });
        context.setPacketHandled(true);
    }

    int containerId() {
        return containerId;
    }

    int requestId() {
        return requestId;
    }

    BlueprintRecyclerActionContract.ActionResult result() {
        return result;
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        String raw = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("invalid Blueprint Recycler result input ID");
        }
        return id;
    }

    private static <T extends Enum<T>> T readEnum(
            FriendlyByteBuf buffer,
            T[] values,
            String description) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid Blueprint Recycler " + description);
        }
        return values[ordinal];
    }
}
