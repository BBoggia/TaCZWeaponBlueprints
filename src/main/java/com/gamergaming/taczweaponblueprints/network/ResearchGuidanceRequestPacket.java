package com.gamergaming.taczweaponblueprints.network;


import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Requests authoritative guidance for one public target in the open Research Bench. */
public final class ResearchGuidanceRequestPacket implements CustomPacketPayload {
    public static final Type<ResearchGuidanceRequestPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("research_guidance_request"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchGuidanceRequestPacket> STREAM_CODEC =
            StreamCodec.ofMember(ResearchGuidanceRequestPacket::toBytes, ResearchGuidanceRequestPacket::new);

    @Override
    public Type<ResearchGuidanceRequestPacket> type() { return TYPE; }

    private final int containerId;
    private final int requestId;
    private final long publicationGeneration;
    private final ResourceLocation targetId;

    public ResearchGuidanceRequestPacket(
            int containerId,
            int requestId,
            long publicationGeneration,
            ResourceLocation targetId) {
        if (containerId < 0 || requestId < 1 || publicationGeneration == Long.MIN_VALUE
                || !validId(targetId)) {
            throw new IllegalArgumentException("invalid research guidance request packet");
        }
        this.containerId = containerId;
        this.requestId = requestId;
        this.publicationGeneration = publicationGeneration;
        this.targetId = targetId;
    }

    public ResearchGuidanceRequestPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readVarInt(),
                buffer.readLong(),
                readId(buffer));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeLong(publicationGeneration);
        writeId(buffer, targetId);
    }

    public void handle(IPayloadContext contextSupplier) {
        IPayloadContext context = contextSupplier;
        ServerPlayer sender = NetworkHandler.sender(context);
        context.enqueueWork(() -> {
            ResearchBenchMenu.GuidanceResult result = sender != null
                    && NetworkHandler.matchesResearchGeneration(
                            sender, publicationGeneration)
                    && sender.containerMenu.containerId == containerId
                    && sender.containerMenu instanceof ResearchBenchMenu menu
                    && menu.stillValid(sender)
                            ? menu.handleGuidanceRequest(sender, targetId)
                            : new ResearchBenchMenu.GuidanceResult(
                                    ResearchBenchMenu.GuidanceResultCode.REJECTED,
                                    java.util.Optional.empty());
            if (sender != null) {
                NetworkHandler.sendResearchGuidanceResult(
                        sender,
                        containerId,
                        requestId,
                        publicationGeneration,
                        result);
            }
        });
    }

    int containerId() {
        return containerId;
    }

    int requestId() {
        return requestId;
    }

    long publicationGeneration() {
        return publicationGeneration;
    }

    ResourceLocation targetId() {
        return targetId;
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.tryParse(
                buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
        if (!validId(id)) {
            throw new IllegalArgumentException("invalid research guidance target ID");
        }
        return id;
    }

    private static void writeId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
    }

    private static boolean validId(ResourceLocation id) {
        return id != null
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }
}
