package com.gamergaming.taczweaponblueprints.network;


import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchMenuTransitions;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Requests a server-validated mode transition for the currently open Bench. */
public final class ResearchWorkbenchModePacket implements CustomPacketPayload {
    public static final Type<ResearchWorkbenchModePacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("research_workbench_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ResearchWorkbenchModePacket> STREAM_CODEC =
            StreamCodec.ofMember(ResearchWorkbenchModePacket::toBytes, ResearchWorkbenchModePacket::new);

    @Override
    public Type<ResearchWorkbenchModePacket> type() { return TYPE; }

    private final int containerId;
    private final Mode mode;

    public ResearchWorkbenchModePacket(int containerId, Mode mode) {
        if (containerId < 0 || mode == null) {
            throw new IllegalArgumentException("invalid Research Bench mode request");
        }
        this.containerId = containerId;
        this.mode = mode;
    }

    public ResearchWorkbenchModePacket(FriendlyByteBuf buffer) {
        this(buffer.readVarInt(), buffer.readEnum(Mode.class));
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarInt(containerId);
        buffer.writeEnum(mode);
    }

    public void handle(IPayloadContext contextSupplier) {
        IPayloadContext context = contextSupplier;
        ServerPlayer sender = NetworkHandler.sender(context);
        context.enqueueWork(() -> {
            if (sender == null || sender.containerMenu.containerId != containerId) {
                return;
            }
            if (mode == Mode.CRAFTING && sender.containerMenu instanceof ResearchBenchMenu menu) {
                ResearchWorkbenchMenuTransitions.toCrafting(sender, menu);
            } else if (mode == Mode.RESEARCH
                    && sender.containerMenu instanceof GunSmithTableMenu menu) {
                ResearchWorkbenchMenuTransitions.toResearch(sender, menu);
            }
        });
    }

    public enum Mode {
        RESEARCH,
        CRAFTING
    }
}
