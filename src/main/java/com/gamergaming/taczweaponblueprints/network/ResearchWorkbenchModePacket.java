package com.gamergaming.taczweaponblueprints.network;

import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchMenuTransitions;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

/** Requests a server-validated mode transition for the currently open Bench. */
public final class ResearchWorkbenchModePacket {
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

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        ServerPlayer sender = context.getSender();
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
        context.setPacketHandled(true);
    }

    public enum Mode {
        RESEARCH,
        CRAFTING
    }
}
