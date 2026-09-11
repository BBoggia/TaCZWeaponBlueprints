package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import java.util.function.Supplier;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFingerprint;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** Explicit quote-bound review identity, independent of tracked-goal guidance. */
public record ResearchRouteReviewRequestPacket(int containerId, int requestId, long publicationGeneration,
        ResourceLocation targetId, ResearchRouteFingerprint fingerprint) {
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
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            if (player == null) return;
            var result = NetworkHandler.matchesResearchGeneration(player, publicationGeneration)
                    && player.containerMenu.containerId == containerId
                    && player.containerMenu instanceof ResearchBenchMenu menu
                    ? menu.handleReviewRequest(player, targetId, fingerprint)
                    : new ResearchBenchMenu.GuidanceResult(ResearchBenchMenu.GuidanceResultCode.REJECTED, Optional.empty());
            NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player),
                    new ResearchRouteReviewResultPacket(this, result));
        });
        context.setPacketHandled(true);
    }
}
