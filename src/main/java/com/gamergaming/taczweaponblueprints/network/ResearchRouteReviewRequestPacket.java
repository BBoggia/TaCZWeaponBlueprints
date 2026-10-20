package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFingerprint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.network.PacketDistributor;

/** Explicit quote-bound review identity, independent of tracked-goal guidance. */
public record ResearchRouteReviewRequestPacket(int containerId, int requestId, long publicationGeneration,
        ResourceLocation targetId, ResearchRouteFingerprint fingerprint) implements CustomPacketPayload {
    public static final Type<ResearchRouteReviewRequestPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("research_route_review_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchRouteReviewRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(ResearchRouteReviewRequestPacket::toBytes, ResearchRouteReviewRequestPacket::new);

    @Override
    public Type<ResearchRouteReviewRequestPacket> type() { return TYPE; }

    public ResearchRouteReviewRequestPacket {
        if (containerId < 0 || requestId < 1 || publicationGeneration == Long.MIN_VALUE || targetId == null
                || targetId.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH
                || fingerprint == null || fingerprint.equals(ResearchRouteFingerprint.NONE))
            throw new IllegalArgumentException("invalid route review request");
    }
    public ResearchRouteReviewRequestPacket(FriendlyByteBuf b) {
        this(b.readVarInt(), b.readVarInt(), b.readLong(),
                ResourceLocation.tryParse(b.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)),
                new ResearchRouteFingerprint(b.readLong(), b.readLong()));
    }
    public void toBytes(FriendlyByteBuf b) {
        b.writeVarInt(containerId); b.writeVarInt(requestId); b.writeLong(publicationGeneration);
        b.writeUtf(targetId.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        b.writeLong(fingerprint.high()); b.writeLong(fingerprint.low());
    }
    public void handle(IPayloadContext supplier) {
        var context = supplier;
        context.enqueueWork(() -> {
            var player = NetworkHandler.sender(context);
            if (player == null) return;
            var result = NetworkHandler.matchesResearchGeneration(player, publicationGeneration)
                    && player.containerMenu.containerId == containerId
                    && player.containerMenu instanceof ResearchBenchMenu menu
                    ? menu.handleReviewRequest(player, targetId, fingerprint)
                    : new ResearchBenchMenu.GuidanceResult(ResearchBenchMenu.GuidanceResultCode.REJECTED, Optional.empty());
            net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(player, new ResearchRouteReviewResultPacket(this, result));
        });
    }
}
