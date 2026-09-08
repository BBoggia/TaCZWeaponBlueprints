package com.gamergaming.taczweaponblueprints.network;

import java.util.function.Supplier;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.StationSetup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkHooks;

/** Requests access only; the server chooses the session and checks the active mode. */
public final class OpenPortableResearchPacket {
    public OpenPortableResearchPacket() { }
    public OpenPortableResearchPacket(FriendlyByteBuf buffer) { }
    public void toBytes(FriendlyByteBuf buffer) { }
    public void handle(Supplier<NetworkEvent.Context> supplier) {
        var context = supplier.get();
        context.enqueueWork(() -> {
            var player = context.getSender();
            var config = ModConfigs.BLUEPRINT;
            if (player == null || !player.isAlive() || player.isSpectator()
                    || player.containerMenu != player.inventoryMenu
                    || config.stationSetup().research() != StationSetup.ResearchAccess.PORTABLE
                    || !config.progressionSnapshot().blueprintsEnabled()
                    || !config.progressionSnapshot().researchEnabled()) return;
            NetworkHooks.openScreen(player, new SimpleMenuProvider(
                    (id, inventory, ignored) -> ResearchBenchMenu.portable(id, inventory),
                    Component.translatable("gui.taczweaponblueprints.portable_research")), buffer -> {
                        buffer.writeBlockPos(player.blockPosition()); buffer.writeVarInt(3);
                        buffer.writeBoolean(true); buffer.writeEnum(StationSetup.ResearchAccess.PORTABLE);
                    });
        });
        context.setPacketHandled(true);
    }
}
