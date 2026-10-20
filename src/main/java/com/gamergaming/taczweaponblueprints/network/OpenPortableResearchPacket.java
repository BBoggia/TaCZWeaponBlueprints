package com.gamergaming.taczweaponblueprints.network;

import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.StationSetup;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleMenuProvider;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

/** Requests access only; the server chooses the session and checks the active mode. */
public final class OpenPortableResearchPacket implements CustomPacketPayload {
    public static final Type<OpenPortableResearchPacket> TYPE = new Type<>(
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.loc("open_portable_research"));
    public static final StreamCodec<RegistryFriendlyByteBuf, OpenPortableResearchPacket> STREAM_CODEC =
            StreamCodec.ofMember(OpenPortableResearchPacket::toBytes, OpenPortableResearchPacket::new);

    @Override
    public Type<OpenPortableResearchPacket> type() { return TYPE; }

    public OpenPortableResearchPacket() { }
    public OpenPortableResearchPacket(FriendlyByteBuf buffer) { }
    public void toBytes(FriendlyByteBuf buffer) { }
    public void handle(IPayloadContext supplier) {
        var context = supplier;
        context.enqueueWork(() -> {
            var player = NetworkHandler.sender(context);
            var config = ModConfigs.BLUEPRINT;
            if (player == null || !player.isAlive() || player.isSpectator()
                    || player.containerMenu != player.inventoryMenu
                    || config.stationSetup().research() != StationSetup.ResearchAccess.PORTABLE
                    || !config.progressionSnapshot().blueprintsEnabled()
                    || !config.progressionSnapshot().researchEnabled()) return;
            player.openMenu(new SimpleMenuProvider(
                    (id, inventory, ignored) -> ResearchBenchMenu.portable(id, inventory),
                    Component.translatable("gui.taczweaponblueprints.portable_research")), buffer -> {
                        buffer.writeBlockPos(player.blockPosition()); buffer.writeVarInt(3);
                        buffer.writeBoolean(true); buffer.writeEnum(StationSetup.ResearchAccess.PORTABLE);
                    });
        });
    }
}
