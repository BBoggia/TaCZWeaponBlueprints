package com.gamergaming.taczweaponblueprints.network;


import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Requests the disclosure-safe recipe allow-list for one exact native menu. */
public final class CraftingAccessRequestPacket implements CustomPacketPayload {
    public static final Type<CraftingAccessRequestPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("crafting_access_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, CraftingAccessRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(CraftingAccessRequestPacket::toBytes, CraftingAccessRequestPacket::new);

    @Override
    public Type<CraftingAccessRequestPacket> type() { return TYPE; }

    private final int containerId;
    private final long requestId;

    public CraftingAccessRequestPacket(int containerId, long requestId) {
        if (containerId < 0 || requestId < 1L) {
            throw new IllegalArgumentException("invalid crafting access container ID");
        }
        this.containerId = containerId;
        this.requestId = requestId;
    }

    public CraftingAccessRequestPacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readLong());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeLong(requestId);
    }

    public void handle(IPayloadContext contextSupplier) {
        IPayloadContext context = contextSupplier;
        ServerPlayer sender = NetworkHandler.sender(context);
        context.enqueueWork(() -> {
            if (sender != null
                    && sender.containerMenu.containerId == containerId
                    && sender.containerMenu instanceof GunSmithTableMenu menu
                    && menu instanceof TaCZWorkbenchMenuBridge bridge
                    && bridge.taczweaponblueprints$acceptCraftingAccessRequest(requestId)) {
                NetworkHandler.sendCraftingAccess(sender, menu);
            }
        });
    }
}
