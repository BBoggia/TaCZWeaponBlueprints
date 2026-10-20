package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import com.gamergaming.taczweaponblueprints.client.ResearchBenchScreen;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Bounded guidance payload paired with the exact selected quote request. */
public record ResearchRouteReviewResultPacket(ResearchRouteReviewRequestPacket request,
        ResearchBenchMenu.GuidanceResult result) implements CustomPacketPayload {
    public static final Type<ResearchRouteReviewResultPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("research_route_review_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchRouteReviewResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(ResearchRouteReviewResultPacket::toBytes, ResearchRouteReviewResultPacket::new);

    @Override
    public Type<ResearchRouteReviewResultPacket> type() { return TYPE; }

    public ResearchRouteReviewResultPacket {
        if (request == null || result == null || result.snapshot().filter(s -> !s.targetId().equals(request.targetId())).isPresent())
            throw new IllegalArgumentException("invalid route review response");
    }
    public ResearchRouteReviewResultPacket(FriendlyByteBuf b) { this(new ResearchRouteReviewRequestPacket(b), readResult(b)); }
    private static ResearchBenchMenu.GuidanceResult readResult(FriendlyByteBuf b) {
        int ordinal = b.readVarInt(); var codes = ResearchBenchMenu.GuidanceResultCode.values();
        if (ordinal < 0 || ordinal >= codes.length) throw new IllegalArgumentException("invalid review result code");
        var code = codes[ordinal];
        return new ResearchBenchMenu.GuidanceResult(code, code == ResearchBenchMenu.GuidanceResultCode.SUCCESS
                ? Optional.of(ResearchGuidanceResultPacket.readSnapshot(b)) : Optional.empty());
    }
    public void toBytes(FriendlyByteBuf b) {
        request.toBytes(b); b.writeVarInt(result.code().ordinal());
        result.snapshot().ifPresent(s -> ResearchGuidanceResultPacket.writeSnapshot(b,s));
    }
    public void handle(IPayloadContext context) {
        new ClientHandler().handle(context);
    }

    /** Loaded only when this clientbound payload is handled, never during server registration. */
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext supplier) {
            var context = supplier; context.enqueueWork(() -> {
                var mc = Minecraft.getInstance();
                if (mc.player != null && mc.player.containerMenu instanceof ResearchBenchMenu
                        && mc.player.containerMenu.containerId == request.containerId()
                        && mc.screen instanceof ResearchBenchScreen screen) screen.acceptRouteReview(ResearchRouteReviewResultPacket.this);
            });
        }
    }
}
