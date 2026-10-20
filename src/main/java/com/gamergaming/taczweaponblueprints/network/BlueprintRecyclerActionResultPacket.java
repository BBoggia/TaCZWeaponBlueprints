package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.BlueprintRecyclerActionResultListener;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Correlates a Recycler response with the exact client request and input. */
public final class BlueprintRecyclerActionResultPacket implements CustomPacketPayload {
    public static final Type<BlueprintRecyclerActionResultPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("blueprint_recycler_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintRecyclerActionResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(BlueprintRecyclerActionResultPacket::toBytes, BlueprintRecyclerActionResultPacket::new);

    @Override
    public Type<BlueprintRecyclerActionResultPacket> type() { return TYPE; }

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
                                "result code"), AnalyzerPresentationCodec.outcome(buffer), AnalyzerPresentationCodec.fragments(buffer), buffer.readVarLong()));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(result.action().ordinal());
        buffer.writeUtf(
                result.inputId().orElseThrow().toString(),
                PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeVarInt(result.code().ordinal());
        AnalyzerPresentationCodec.outcome(buffer, result.outcome());
        AnalyzerPresentationCodec.fragments(buffer, result.fragments());
        buffer.writeVarLong(result.stateToken());
    }

    public void handle(IPayloadContext context) {
        new ClientHandler().handle(context);
    }

    /** Loaded only when this clientbound payload is handled, never during server registration. */
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext contextSupplier) {
            IPayloadContext context = contextSupplier;
            context.enqueueWork(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.screen instanceof BlueprintRecyclerActionResultListener listener
                        && minecraft.player != null
                        && minecraft.player.containerMenu.containerId == containerId) {
                    listener.acceptRecyclerActionResult(requestId, result);
                }
            });
        }
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
