package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Browses the current selection only; the server prepares a new complete quote. */
public record ResearchMaterialPageRequestPacket(int containerId, long generation,
        ResourceLocation targetId, int fromOffset, int toOffset) implements CustomPacketPayload {
    public static final Type<ResearchMaterialPageRequestPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("research_material_page_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchMaterialPageRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(ResearchMaterialPageRequestPacket::toBytes, ResearchMaterialPageRequestPacket::new);

    @Override
    public Type<ResearchMaterialPageRequestPacket> type() { return TYPE; }

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
    public void handle(IPayloadContext supplier) {
        var context = supplier;
        context.enqueueWork(() -> {
            var player = NetworkHandler.sender(context);
            if (player != null && NetworkHandler.matchesResearchGeneration(player, generation)
                    && player.containerMenu.containerId == containerId
                    && player.containerMenu instanceof ResearchBenchMenu menu) {
                menu.requestMaterialPage(player, targetId, fromOffset, toOffset);
            }
        });
    }
}
