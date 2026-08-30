package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.ResearchBenchScreen;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchResearchAction;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkEvent;

/** Correlates a server-authoritative selection or research result with its client request. */
public final class ResearchBenchActionResultPacket {
    private final int containerId;
    private final int requestId;
    private final ResearchBenchMenu.ActionResult result;

    public ResearchBenchActionResultPacket(
            int containerId,
            int requestId,
            ResearchBenchMenu.ActionResult result) {
        if (containerId < 0 || requestId < 1 || result == null) {
            throw new IllegalArgumentException("invalid Research Bench action result packet");
        }
        this.containerId = containerId;
        this.requestId = requestId;
        this.result = result;
    }

    public ResearchBenchActionResultPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readVarInt(),
                new ResearchBenchMenu.ActionResult(
                        readAction(buffer),
                        readOptionalId(buffer),
                        readResultCode(buffer)));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(result.action().ordinal());
        writeOptionalId(buffer, result.blueprintId());
        buffer.writeVarInt(result.code().ordinal());
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> {
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.screen instanceof ResearchBenchScreen screen
                    && minecraft.player != null
                    && minecraft.player.containerMenu.containerId == containerId) {
                screen.acceptActionResult(requestId, result);
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

    ResearchBenchMenu.ActionResult result() {
        return result;
    }

    private static ResearchBenchResearchAction readAction(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        ResearchBenchResearchAction[] actions = ResearchBenchResearchAction.values();
        if (ordinal < 0 || ordinal >= actions.length) {
            throw new IllegalArgumentException("invalid Research Bench result action");
        }
        return actions[ordinal];
    }

    private static ResearchBenchMenu.ActionResultCode readResultCode(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        ResearchBenchMenu.ActionResultCode[] codes = ResearchBenchMenu.ActionResultCode.values();
        if (ordinal < 0 || ordinal >= codes.length) {
            throw new IllegalArgumentException("invalid Research Bench result code");
        }
        return codes[ordinal];
    }

    private static Optional<ResourceLocation> readOptionalId(FriendlyByteBuf buffer) {
        if (!buffer.readBoolean()) {
            return Optional.empty();
        }
        String raw = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("invalid Research Bench result blueprint ID");
        }
        return Optional.of(id);
    }

    private static void writeOptionalId(
            FriendlyByteBuf buffer,
            Optional<ResourceLocation> blueprintId) {
        buffer.writeBoolean(blueprintId.isPresent());
        blueprintId.ifPresent(id -> buffer.writeUtf(
                id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
    }
}
