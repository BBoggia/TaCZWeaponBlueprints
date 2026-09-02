package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.ClientResearchGuidanceState;
import com.gamergaming.taczweaponblueprints.client.ClientResearchState;
import com.gamergaming.taczweaponblueprints.client.ResearchBenchScreen;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchPathUnlockPlanner;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Correlated authoritative research-guidance response for one tree publication. */
public final class ResearchGuidanceResultPacket {
    private final int containerId;
    private final int requestId;
    private final long publicationGeneration;
    private final ResearchBenchMenu.GuidanceResult result;

    public ResearchGuidanceResultPacket(
            int containerId,
            int requestId,
            long publicationGeneration,
            ResearchBenchMenu.GuidanceResult result) {
        if (containerId < 0 || requestId < 1 || publicationGeneration == Long.MIN_VALUE
                || result == null) {
            throw new IllegalArgumentException("invalid research guidance result packet");
        }
        this.containerId = containerId;
        this.requestId = requestId;
        this.publicationGeneration = publicationGeneration;
        this.result = result;
    }

    public ResearchGuidanceResultPacket(FriendlyByteBuf buffer) {
        this.containerId = buffer.readVarInt();
        this.requestId = buffer.readVarInt();
        this.publicationGeneration = buffer.readLong();
        ResearchBenchMenu.GuidanceResultCode code = readEnum(
                buffer, ResearchBenchMenu.GuidanceResultCode.values(), "guidance result");
        Optional<ResearchGuidanceSnapshot> snapshot = code
                == ResearchBenchMenu.GuidanceResultCode.SUCCESS
                        ? Optional.of(readSnapshot(buffer))
                        : Optional.empty();
        this.result = new ResearchBenchMenu.GuidanceResult(code, snapshot);
        if (containerId < 0 || requestId < 1 || publicationGeneration == Long.MIN_VALUE) {
            throw new IllegalArgumentException("invalid research guidance result packet");
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
            if (result.code() == ResearchBenchMenu.GuidanceResultCode.SUCCESS) {
                boolean accepted = ClientResearchGuidanceState.accept(
                        requestId,
                        publicationGeneration,
                        result.snapshot().orElseThrow(),
                        ClientResearchState.publication());
                if ((accepted || ClientResearchGuidanceState.unavailable())
                        && minecraft.screen instanceof ResearchBenchScreen screen) {
                    screen.refreshAuthoritativeGuidance();
                }
            } else {
                boolean rejected = ClientResearchGuidanceState.reject(
                        requestId,
                        publicationGeneration,
                        result.code() == ResearchBenchMenu.GuidanceResultCode.REJECTED);
                if (rejected && minecraft.screen instanceof ResearchBenchScreen screen) {
                    if (result.code() == ResearchBenchMenu.GuidanceResultCode.THROTTLED) {
                        screen.scheduleAuthoritativeGuidanceRetry();
                    } else {
                        screen.refreshAuthoritativeGuidance();
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

    ResearchBenchMenu.GuidanceResult result() {
        return result;
    }

    private static ResearchGuidanceSnapshot readSnapshot(FriendlyByteBuf buffer) {
        ResourceLocation targetId = readId(buffer);
        ResearchGuidanceSnapshot.State state = readEnum(
                buffer, ResearchGuidanceSnapshot.State.values(), "guidance state");
        int pointCost = buffer.readVarInt();
        int pointBalance = buffer.readVarInt();
        ResearchCostMode costMode = readEnum(
                buffer, ResearchCostMode.values(), "guidance cost mode");
        boolean costBypassed = buffer.readBoolean();
        boolean capacity = buffer.readBoolean();
        int totalMaterialTypes = buffer.readVarInt();
        int totalMaterialUnits = buffer.readVarInt();
        int allocatedMaterialUnits = buffer.readVarInt();
        int missingMaterialTypes = buffer.readVarInt();
        int materialCount = readCount(
                buffer, ResearchGuidanceSnapshot.MAX_MATERIAL_PROGRESS, "material progress");
        List<ResearchGuidanceSnapshot.MaterialProgress> materials =
                new ArrayList<>(materialCount);
        for (int index = 0; index < materialCount; index++) {
            int itemCount = readCount(
                    buffer, BlueprintResearchIngredient.MAX_ITEMS, "material alternatives");
            List<ResourceLocation> items = new ArrayList<>(itemCount);
            for (int itemIndex = 0; itemIndex < itemCount; itemIndex++) {
                items.add(readId(buffer));
            }
            Optional<ResourceLocation> tag = readOptionalId(buffer);
            materials.add(new ResearchGuidanceSnapshot.MaterialProgress(
                    items, tag, buffer.readVarInt(), buffer.readVarInt()));
        }
        List<ResourceLocation> supportIds = readIds(
                buffer, ResearchGuidanceSnapshot.MAX_SUPPORT_IDS, "support IDs");
        List<ResourceLocation> purchaseIds = readIds(
                buffer, ResearchGuidanceSnapshot.MAX_PURCHASE_IDS, "purchase IDs");
        int selectedCount = readCount(
                buffer,
                ResearchGuidanceSnapshot.MAX_SELECTED_REQUIREMENTS,
                "selected requirements");
        List<ResearchPathUnlockPlanner.SelectedRequirement> selected =
                new ArrayList<>(selectedCount);
        for (int index = 0; index < selectedCount; index++) {
            selected.add(new ResearchPathUnlockPlanner.SelectedRequirement(
                    readId(buffer), buffer.readVarInt(), readId(buffer)));
        }
        return new ResearchGuidanceSnapshot(
                targetId,
                state,
                pointCost,
                pointBalance,
                costMode,
                costBypassed,
                capacity,
                totalMaterialTypes,
                totalMaterialUnits,
                allocatedMaterialUnits,
                missingMaterialTypes,
                materials,
                supportIds,
                purchaseIds,
                selected,
                readOptionalId(buffer));
    }

    private static void writeSnapshot(
            FriendlyByteBuf buffer,
            ResearchGuidanceSnapshot snapshot) {
        writeId(buffer, snapshot.targetId());
        buffer.writeVarInt(snapshot.state().ordinal());
        buffer.writeVarInt(snapshot.pointCost());
        buffer.writeVarInt(snapshot.pointBalance());
        buffer.writeVarInt(snapshot.costMode().ordinal());
        buffer.writeBoolean(snapshot.costBypassed());
        buffer.writeBoolean(snapshot.transactionCapacityAvailable());
        buffer.writeVarInt(snapshot.totalMaterialTypes());
        buffer.writeVarInt(snapshot.totalMaterialUnits());
        buffer.writeVarInt(snapshot.allocatedMaterialUnits());
        buffer.writeVarInt(snapshot.missingMaterialTypes());
        buffer.writeVarInt(snapshot.materials().size());
        for (ResearchGuidanceSnapshot.MaterialProgress material : snapshot.materials()) {
            buffer.writeVarInt(material.items().size());
            material.items().forEach(id -> writeId(buffer, id));
            writeOptionalId(buffer, material.tag());
            buffer.writeVarInt(material.required());
            buffer.writeVarInt(material.allocated());
        }
        writeIds(buffer, snapshot.supportIds());
        writeIds(buffer, snapshot.purchaseIds());
        buffer.writeVarInt(snapshot.selectedRequirements().size());
        for (ResearchPathUnlockPlanner.SelectedRequirement selected
                : snapshot.selectedRequirements()) {
            writeId(buffer, selected.dependentId());
            buffer.writeVarInt(selected.groupOrdinal());
            writeId(buffer, selected.prerequisiteId());
        }
        writeOptionalId(buffer, snapshot.nextStepId());
    }

    private static List<ResourceLocation> readIds(
            FriendlyByteBuf buffer,
            int maximum,
            String field) {
        int count = readCount(buffer, maximum, field);
        List<ResourceLocation> ids = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            ids.add(readId(buffer));
        }
        return ids;
    }

    private static void writeIds(FriendlyByteBuf buffer, List<ResourceLocation> ids) {
        buffer.writeVarInt(ids.size());
        ids.forEach(id -> writeId(buffer, id));
    }

    private static int readCount(FriendlyByteBuf buffer, int maximum, String field) {
        int count = buffer.readVarInt();
        if (count < 0 || count > maximum) {
            throw new IllegalArgumentException("invalid research guidance " + field + " count");
        }
        return count;
    }

    private static <T> T readEnum(FriendlyByteBuf buffer, T[] values, String field) {
        int ordinal = buffer.readVarInt();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("invalid research " + field);
        }
        return values[ordinal];
    }

    private static Optional<ResourceLocation> readOptionalId(FriendlyByteBuf buffer) {
        return buffer.readBoolean() ? Optional.of(readId(buffer)) : Optional.empty();
    }

    private static void writeOptionalId(
            FriendlyByteBuf buffer,
            Optional<ResourceLocation> id) {
        buffer.writeBoolean(id.isPresent());
        id.ifPresent(value -> writeId(buffer, value));
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        ResourceLocation id = ResourceLocation.tryParse(
                buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
        if (id == null) {
            throw new IllegalArgumentException("invalid research guidance resource ID");
        }
        return id;
    }

    private static void writeId(FriendlyByteBuf buffer, ResourceLocation id) {
        buffer.writeUtf(id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
    }
}
