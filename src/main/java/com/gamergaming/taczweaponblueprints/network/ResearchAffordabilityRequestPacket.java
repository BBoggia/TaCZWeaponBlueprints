package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.ResearchAffordabilitySnapshot;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Requests one bounded progressive Affordable Now batch from the open Bench. */
public final class ResearchAffordabilityRequestPacket {
    private final int containerId;
    private final int requestId;
    private final long publicationGeneration;
    private final List<ResourceLocation> targetIds;

    public ResearchAffordabilityRequestPacket(
            int containerId,
            int requestId,
            long publicationGeneration,
            List<ResourceLocation> targetIds) {
        if (containerId < 0 || requestId < 1 || publicationGeneration == Long.MIN_VALUE
                || targetIds == null || targetIds.isEmpty()
                || targetIds.size() > ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH
                || targetIds.stream().anyMatch(id -> !validId(id))
                || targetIds.stream().distinct().count() != targetIds.size()) {
            throw new IllegalArgumentException("invalid research affordability request packet");
        }
        this.containerId = containerId;
        this.requestId = requestId;
        this.publicationGeneration = publicationGeneration;
        this.targetIds = List.copyOf(targetIds);
    }

    public ResearchAffordabilityRequestPacket(FriendlyByteBuf buffer) {
        this.containerId = buffer.readVarInt();
        this.requestId = buffer.readVarInt();
        this.publicationGeneration = buffer.readLong();
        int count = buffer.readVarInt();
        if (count < 1 || count > ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH) {
            throw new IllegalArgumentException("invalid research affordability target count");
        }
        ArrayList<ResourceLocation> targets = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            targets.add(readId(buffer));
        }
        this.targetIds = List.copyOf(targets);
        if (containerId < 0 || requestId < 1 || publicationGeneration == Long.MIN_VALUE
                || targetIds.stream().distinct().count() != targetIds.size()) {
            throw new IllegalArgumentException("invalid research affordability request packet");
        }
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeLong(publicationGeneration);
        buffer.writeVarInt(targetIds.size());
        targetIds.forEach(id -> buffer.writeUtf(
                id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            Optional<ResearchBenchMenu.AffordabilityResult> immediate = sender != null
                    && NetworkHandler.matchesResearchGeneration(
                            sender, publicationGeneration)
                    && sender.containerMenu.containerId == containerId
                    && sender.containerMenu instanceof ResearchBenchMenu menu
                    && menu.stillValid(sender)
                            ? menu.beginAffordabilityRequest(
                                    sender,
                                    requestId,
                                    publicationGeneration,
                                    targetIds)
                            : Optional.of(ResearchBenchMenu.AffordabilityResult.rejected());
            if (sender != null) {
                ResearchBenchMenu.AffordabilityResult result = immediate.orElseGet(
                        ResearchBenchMenu.AffordabilityResult::queued);
                NetworkHandler.sendResearchAffordabilityResult(
                        sender,
                        containerId,
                        requestId,
                        publicationGeneration,
                        result);
            }
        });
        context.setPacketHandled(true);
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

    List<ResourceLocation> targetIds() {
        return targetIds;
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.tryParse(
                buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
        if (!validId(id)) {
            throw new IllegalArgumentException("invalid research affordability target ID");
        }
        return id;
    }

    private static boolean validId(ResourceLocation id) {
        return id != null
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }
}
