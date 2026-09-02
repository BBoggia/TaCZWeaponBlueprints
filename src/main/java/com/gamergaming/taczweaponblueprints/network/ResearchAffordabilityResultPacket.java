package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.ClientResearchAffordabilityState;
import com.gamergaming.taczweaponblueprints.client.ClientResearchState;
import com.gamergaming.taczweaponblueprints.client.ResearchBenchScreen;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.ResearchAffordabilitySnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;

import net.minecraft.client.Minecraft;
import net.minecraft.Util;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Correlated compact result for one progressive Affordable Now batch. */
public final class ResearchAffordabilityResultPacket {
    private final int containerId;
    private final int requestId;
    private final long publicationGeneration;
    private final ResearchBenchMenu.AffordabilityResult result;

    public ResearchAffordabilityResultPacket(
            int containerId,
            int requestId,
            long publicationGeneration,
            ResearchBenchMenu.AffordabilityResult result) {
        if (containerId < 0 || requestId < 1 || publicationGeneration == Long.MIN_VALUE
                || result == null) {
            throw new IllegalArgumentException("invalid research affordability result packet");
        }
        this.containerId = containerId;
        this.requestId = requestId;
        this.publicationGeneration = publicationGeneration;
        this.result = result;
    }

    public ResearchAffordabilityResultPacket(FriendlyByteBuf buffer) {
        this.containerId = buffer.readVarInt();
        this.requestId = buffer.readVarInt();
        this.publicationGeneration = buffer.readLong();
        ResearchBenchMenu.AffordabilityResultCode code = readEnum(
                buffer,
                ResearchBenchMenu.AffordabilityResultCode.values(),
                "affordability result");
        Optional<ResearchAffordabilitySnapshot> snapshot = code
                == ResearchBenchMenu.AffordabilityResultCode.SUCCESS
                        ? Optional.of(readSnapshot(buffer))
                        : Optional.empty();
        this.result = new ResearchBenchMenu.AffordabilityResult(code, snapshot);
        if (containerId < 0 || requestId < 1 || publicationGeneration == Long.MIN_VALUE) {
            throw new IllegalArgumentException("invalid research affordability result packet");
        }
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeLong(publicationGeneration);
        buffer.writeVarInt(result.code().ordinal());
        result.snapshot().ifPresent(snapshot -> writeSnapshot(buffer, snapshot));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null
                    || minecraft.player.containerMenu.containerId != containerId
                    || !(minecraft.player.containerMenu instanceof ResearchBenchMenu)) {
                return;
            }
            ClientResearchAffordabilityState.ResponseOutcome outcome;
            if (result.code() == ResearchBenchMenu.AffordabilityResultCode.SUCCESS) {
                outcome = ClientResearchAffordabilityState.accept(
                        requestId,
                        publicationGeneration,
                        result.snapshot().orElseThrow(),
                        ClientResearchState.publication());
            } else if (result.code() == ResearchBenchMenu.AffordabilityResultCode.QUEUED) {
                outcome = ClientResearchAffordabilityState.acknowledge(
                        requestId, publicationGeneration, Util.getMillis());
            } else if (result.code() == ResearchBenchMenu.AffordabilityResultCode.THROTTLED) {
                outcome = ClientResearchAffordabilityState.throttle(
                        requestId, publicationGeneration);
            } else {
                outcome = ClientResearchAffordabilityState.reject(
                        requestId, publicationGeneration);
            }
            if (minecraft.screen instanceof ResearchBenchScreen screen) {
                switch (outcome) {
                    case ACKNOWLEDGED, ACCEPTED, ADVANCED_AFTER_FAILURE ->
                            screen.refreshAuthoritativeAffordability();
                    case RETRY -> screen.scheduleAffordabilityRetry();
                    case IGNORED -> {
                    }
                }
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

    ResearchBenchMenu.AffordabilityResult result() {
        return result;
    }

    private static ResearchAffordabilitySnapshot readSnapshot(FriendlyByteBuf buffer) {
        int count = buffer.readVarInt();
        if (count < 1 || count > ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH) {
            throw new IllegalArgumentException("invalid research affordability result count");
        }
        List<ResearchAffordabilitySnapshot.Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            entries.add(new ResearchAffordabilitySnapshot.Entry(
                    readId(buffer),
                    readEnum(buffer, ResearchGuidanceSnapshot.State.values(), "guidance state"),
                    buffer.readBoolean()));
        }
        return new ResearchAffordabilitySnapshot(entries);
    }

    private static void writeSnapshot(
            FriendlyByteBuf buffer,
            ResearchAffordabilitySnapshot snapshot) {
        buffer.writeVarInt(snapshot.entries().size());
        for (ResearchAffordabilitySnapshot.Entry entry : snapshot.entries()) {
            buffer.writeUtf(
                    entry.targetId().toString(),
                    PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
            buffer.writeVarInt(entry.state().ordinal());
            buffer.writeBoolean(entry.transactionCapacityAvailable());
        }
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.tryParse(
                buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
        if (id == null) {
            throw new IllegalArgumentException("invalid research affordability resource ID");
        }
        return id;
    }

    private static <T> T readEnum(FriendlyByteBuf buffer, T[] values, String field) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid research " + field);
        }
        return values[ordinal];
    }
}
