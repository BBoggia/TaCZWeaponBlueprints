package com.gamergaming.taczweaponblueprints.network;

import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Requests the disclosure-safe recipe allow-list for one exact native menu. */
public final class CraftingAccessRequestPacket {
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

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null
                    && sender.containerMenu.containerId == containerId
                    && sender.containerMenu instanceof GunSmithTableMenu menu
                    && menu instanceof TaCZWorkbenchMenuBridge bridge
                    && bridge.taczweaponblueprints$acceptCraftingAccessRequest(requestId)) {
                NetworkHandler.sendCraftingAccess(sender, menu);
            }
        });
        context.setPacketHandled(true);
    }
}
