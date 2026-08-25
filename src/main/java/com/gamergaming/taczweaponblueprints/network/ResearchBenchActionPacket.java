package com.gamergaming.taczweaponblueprints.network;

import java.util.Optional;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** A bounded request; the open server menu remains the sole authority. */
public final class ResearchBenchActionPacket {
    private final int containerId;
    private final ResearchBenchMenu.Action action;
    private final Optional<ResourceLocation> blueprintId;

    public ResearchBenchActionPacket(
            int containerId,
            ResearchBenchMenu.Action action,
            Optional<ResourceLocation> blueprintId) {
        if (containerId < 0 || action == null) {
            throw new IllegalArgumentException("invalid Research Bench action");
        }
        this.containerId = containerId;
        this.action = action;
        this.blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
        this.blueprintId.ifPresent(ResearchBenchActionPacket::validateId);
    }

    public ResearchBenchActionPacket(FriendlyByteBuf buffer) {
        this(
                buffer.readVarInt(),
                readAction(buffer),
                buffer.readBoolean()
                        ? Optional.of(readId(buffer))
                        : Optional.empty());
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeVarInt(action.ordinal());
        buffer.writeBoolean(blueprintId.isPresent());
        blueprintId.ifPresent(id -> buffer.writeUtf(
                id.toString(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH));
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
        context.enqueueWork(() -> {
            if (sender != null
                    && sender.containerMenu.containerId == containerId
                    && sender.containerMenu instanceof ResearchBenchMenu menu
                    && menu.stillValid(sender)) {
                menu.handleAction(sender, action, blueprintId);
            }
        });
        context.setPacketHandled(true);
    }

    int containerId() {
        return containerId;
    }

    ResearchBenchMenu.Action action() {
        return action;
    }

    Optional<ResourceLocation> blueprintId() {
        return blueprintId;
    }

    private static ResearchBenchMenu.Action readAction(FriendlyByteBuf buffer) {
        int ordinal = buffer.readVarInt();
        ResearchBenchMenu.Action[] values = ResearchBenchMenu.Action.values();
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
