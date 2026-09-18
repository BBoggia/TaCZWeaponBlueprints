package com.gamergaming.taczweaponblueprints.network;

import java.util.function.Supplier;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Browses the current selection only; the server prepares a new complete quote. */
public record ResearchMaterialPageRequestPacket(int containerId, long generation,
        ResourceLocation targetId, int fromOffset, int toOffset) {
    public ResearchMaterialPageRequestPacket {
        int size = BlueprintResearchCost.MAX_INGREDIENT_TYPES;
        int limit = PlayerProgressionLimits.MAX_IDS_PER_COLLECTION * size;
        if (containerId < 0 || generation == Long.MIN_VALUE || targetId == null
                || targetId.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                || fromOffset < 0 || fromOffset >= limit || fromOffset % size != 0
                || toOffset < 0 || toOffset >= limit || toOffset % size != 0
                || Math.abs((long) fromOffset - toOffset) != size)
            throw new IllegalArgumentException("invalid material page request");
    }
    public ResearchMaterialPageRequestPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readLong(),
                ResourceLocation.tryParse(b.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)),
                b.readVarInt(), b.readVarInt());
    }
    public void toBytes(FriendlyByteBuf b) {
        b.writeVarInt(containerId); b.writeLong(generation);
        b.writeUtf(targetId.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        b.writeVarInt(fromOffset); b.writeVarInt(toOffset);
    }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player != null && NetworkHandler.matchesResearchGeneration(player, generation)
                    && player.containerMenu.containerId == containerId
                    && player.containerMenu instanceof ResearchBenchMenu menu) {
                menu.requestMaterialPage(player, targetId, fromOffset, toOffset);
            }
        });
        context.setPacketHandled(true);
    }
}
