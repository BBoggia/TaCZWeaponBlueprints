package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenuBridge;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Bounded smart-slot request; the open Recycler menu remains authoritative. */
public final class BlueprintRecyclerActionPacket implements CustomPacketPayload {
    public static final Type<BlueprintRecyclerActionPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("blueprint_recycler_action"));
    public static final StreamCodec<RegistryFriendlyByteBuf, BlueprintRecyclerActionPacket> STREAM_CODEC =
            StreamCodec.ofMember(BlueprintRecyclerActionPacket::toBytes, BlueprintRecyclerActionPacket::new);

    @Override
    public Type<BlueprintRecyclerActionPacket> type() { return TYPE; }

    private final int containerId;
    private final int requestId;
    private final BlueprintRecyclerActionContract.Action action;
    private final ResourceLocation expectedInputId;
    private final int expectedInputCount;
    private final long expectedStateToken;

    public BlueprintRecyclerActionPacket(
            int containerId,
            int requestId,
            BlueprintRecyclerActionContract.Action action,
            ResourceLocation expectedInputId,
            int expectedInputCount) {
        this(containerId, requestId, action, expectedInputId, expectedInputCount, 1L);
    }

    public BlueprintRecyclerActionPacket(
            int containerId,
            int requestId,
            BlueprintRecyclerActionContract.Action action,
            ResourceLocation expectedInputId,
            int expectedInputCount,
            long expectedStateToken) {
        if (containerId < 0 || requestId < 1 || action == null
                || expectedInputCount < 1
                || expectedInputCount
                        > PlayerProgressionLimits.MAX_RESEARCH_DATA_REDEMPTIONS_PER_ACTION
                || expectedStateToken < 1L) {
            throw new IllegalArgumentException("invalid Blueprint Recycler action");
        }
        validateId(expectedInputId);
        this.containerId = containerId;
        this.requestId = requestId;
        this.action = action;
        this.expectedInputId = expectedInputId;
        this.expectedInputCount = expectedInputCount;
        this.expectedStateToken = expectedStateToken;
    }

    public BlueprintRecyclerActionPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readVarInt(),
                readAction(buffer),
                readId(buffer),
                buffer.readVarInt(),
                buffer.readVarLong());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(action.ordinal());
        writeId(buffer, expectedInputId);
        buffer.writeVarInt(expectedInputCount);
        buffer.writeVarLong(expectedStateToken);
    }

    public void handle(IPayloadContext contextSupplier) {
        IPayloadContext context = contextSupplier;
        ServerPlayer sender = NetworkHandler.sender(context);
        context.enqueueWork(() -> {
            if (sender != null
                    && sender.containerMenu.containerId == containerId
                    && sender.containerMenu instanceof BlueprintRecyclerMenuBridge menu
                    && menu.isRecyclerMenuValid(sender)) {
                BlueprintRecyclerActionContract.ActionResult result =
                        menu.handleRecyclerRequest(
                                sender, requestId,
                                action,
                                expectedInputId,
                                expectedInputCount,
                                expectedStateToken);
                if (result == null) {
                    result = new BlueprintRecyclerActionContract.ActionResult(
                            action,
                            Optional.of(expectedInputId),
                            BlueprintRecyclerActionContract.ResultCode.TRANSACTION_FAILED);
                }
                NetworkHandler.sendBlueprintRecyclerActionResult(
                        sender, containerId, requestId, result);
            }
        });
    }

    int containerId() {
        return containerId;
    }

    int requestId() {
        return requestId;
    }

    BlueprintRecyclerActionContract.Action action() {
        return action;
    }

    ResourceLocation expectedInputId() {
        return expectedInputId;
    }

    int expectedInputCount() {
        return expectedInputCount;
    }

    long expectedStateToken() {
        return expectedStateToken;
    }

    private static BlueprintRecyclerActionContract.Action readAction(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        BlueprintRecyclerActionContract.Action[] actions =
                BlueprintRecyclerActionContract.Action.values();
        if (ordinal < 0 || ordinal >= actions.length) {
            throw new IllegalArgumentException("unknown Blueprint Recycler action");
        }
        return actions[ordinal];
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        String raw = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("invalid Blueprint Recycler input ID");
        }
        return id;
    }

    private static void writeId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
    }

    private static void validateId(ResourceLocation id) {
        if (id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("invalid Blueprint Recycler input ID");
        }
    }
}
