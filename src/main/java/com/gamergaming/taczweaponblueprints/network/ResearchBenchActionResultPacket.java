package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.ResearchBenchScreen;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchResearchAction;

import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Correlates a server-authoritative selection or research result with its client request. */
public final class ResearchBenchActionResultPacket implements CustomPacketPayload {
    public static final Type<ResearchBenchActionResultPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("research_bench_action_result"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchBenchActionResultPacket> STREAM_CODEC =
            StreamCodec.ofMember(ResearchBenchActionResultPacket::toBytes, ResearchBenchActionResultPacket::new);

    @Override
    public Type<ResearchBenchActionResultPacket> type() { return TYPE; }

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

    public void handle(IPayloadContext context) {
        new ClientHandler().handle(context);
    }

    /** Loaded only when this clientbound payload is handled, never during server registration. */
    @net.neoforged.api.distmarker.OnlyIn(net.neoforged.api.distmarker.Dist.CLIENT)
    private final class ClientHandler {
        void handle(IPayloadContext contextSupplier) {
            IPayloadContext context = contextSupplier;
            context.enqueueWork(() -> {
                Minecraft minecraft = Minecraft.getInstance();
                if (minecraft.screen instanceof ResearchBenchScreen screen
                        && minecraft.player != null
                        && minecraft.player.containerMenu.containerId == containerId) {
                    screen.acceptActionResult(requestId, result);
                }
            });
        }
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
