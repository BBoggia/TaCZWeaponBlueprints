package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchResearchAction;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFingerprint;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** A bounded request; the open server menu remains the sole authority. */
public final class ResearchBenchActionPacket {
    private final int containerId;
    private final int requestId;
    private final ResearchBenchResearchAction action;
    private final Optional<ResourceLocation> blueprintId;
    private final Optional<ResearchRouteFingerprint> routeFingerprint;

    public ResearchBenchActionPacket(
            int containerId,
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> blueprintId) {
        this(containerId, 0, action, blueprintId, Optional.empty());
    }

    public ResearchBenchActionPacket(
            int containerId,
            int requestId,
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> blueprintId) {
        this(containerId, requestId, action, blueprintId, Optional.empty());
    }

    public ResearchBenchActionPacket(
            int containerId,
            int requestId,
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> blueprintId,
            Optional<ResearchRouteFingerprint> routeFingerprint) {
        if (containerId < 0 || requestId < 0 || action == null) {
            throw new IllegalArgumentException("invalid Research Bench action");
        }
        this.containerId = containerId;
        this.requestId = requestId;
        this.action = action;
        this.blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
        this.routeFingerprint = routeFingerprint == null
                ? Optional.empty()
                : routeFingerprint;
        this.blueprintId.ifPresent(ResearchBenchActionPacket::validateId);
        if (this.routeFingerprint.filter(fingerprint -> !fingerprint.present()).isPresent()
                || action == ResearchBenchResearchAction.SELECT
                        && this.routeFingerprint.isPresent()) {
            throw new IllegalArgumentException("invalid Research Bench route fingerprint");
        }
    }

    public ResearchBenchActionPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                buffer.readVarInt(),
                readAction(buffer),
                buffer.readBoolean()
                        ? Optional.of(readId(buffer))
                        : Optional.empty(),
                buffer.readBoolean()
                        ? Optional.of(new ResearchRouteFingerprint(
                                buffer.readLong(), buffer.readLong()))
                        : Optional.empty());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(requestId);
        buffer.writeVarInt(action.ordinal());
        buffer.writeBoolean(blueprintId.isPresent());
        blueprintId.ifPresent(id -> buffer.writeUtf(
                id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
        buffer.writeBoolean(routeFingerprint.isPresent());
        routeFingerprint.ifPresent(fingerprint -> {
            buffer.writeLong(fingerprint.high());
            buffer.writeLong(fingerprint.low());
        });
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null
                    && sender.containerMenu.containerId == containerId
                    && sender.containerMenu instanceof ResearchBenchMenu menu
                    && menu.stillValid(sender)) {
                menu.handleAction(
                        sender, action, blueprintId, routeFingerprint).ifPresent(result -> {
                    if (requestId > 0) {
                        NetworkHandler.sendResearchBenchActionResult(
                                sender, containerId, requestId, result);
                    }
                });
            }
        });
        context.setPacketHandled(true);
    }

    int containerId() {
        return containerId;
    }

    ResearchBenchResearchAction action() {
        return action;
    }

    int requestId() {
        return requestId;
    }

    Optional<ResourceLocation> blueprintId() {
        return blueprintId;
    }

    Optional<ResearchRouteFingerprint> routeFingerprint() {
        return routeFingerprint;
    }

    private static ResearchBenchResearchAction readAction(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        ResearchBenchResearchAction[] values = ResearchBenchResearchAction.values();
        if (ordinal < 0 || ordinal >= values.length) {
            throw new IllegalArgumentException("unknown Research Bench action");
        }
        return values[ordinal];
    }

    private static ResourceLocation readId(FriendlyByteBuf buffer) {
        String raw = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            throw new IllegalArgumentException("invalid Research Bench blueprint ID");
        }
        return id;
    }

    private static void validateId(ResourceLocation id) {
        if (id == null || id.toString().length() > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("invalid Research Bench blueprint ID");
        }
    }
}
