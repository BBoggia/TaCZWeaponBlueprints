package com.gamergaming.taczweaponblueprints.network;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.client.ClientResearchPointPresentationState;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpEntry;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpSnapshot;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** Complete bounded disclosure-filtered RP earning-help publication. */
public final class SyncResearchPointHelpPacket {
    private final HelpSnapshot snapshot;

    public SyncResearchPointHelpPacket(HelpSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Research Point help snapshot cannot be null");
        }
        this.snapshot = snapshot;
    }

    public SyncResearchPointHelpPacket(FriendlyByteBuf buffer) {
        long revision = buffer.readVarLong();
        int count = buffer.readVarInt();
        if (count < 0 || count > PlayerProgressionLimits.MAX_RESEARCH_POINT_HELP_ENTRIES) {
            throw new IllegalArgumentException("invalid Research Point help entry count");
        }
        List<HelpEntry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            String name = buffer.readUtf(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
            int triggerOrdinal = buffer.readVarInt();
            ResearchPointAwardTrigger.Type[] triggers = ResearchPointAwardTrigger.Type.values();
            if (triggerOrdinal < 0 || triggerOrdinal >= triggers.length) {
                throw new IllegalArgumentException("invalid Research Point help trigger");
            }
            entries.add(new HelpEntry(name, triggers[triggerOrdinal], buffer.readVarInt()));
        }
        snapshot = new HelpSnapshot(revision, entries);
    }

    public void toBytes(FriendlyByteBuf buffer) {
        buffer.writeVarLong(snapshot.revision());
        buffer.writeVarInt(snapshot.entries().size());
        for (HelpEntry entry : snapshot.entries()) {
            buffer.writeUtf(entry.nameKey(), PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
            buffer.writeVarInt(entry.triggerType().ordinal());
            buffer.writeVarInt(entry.points());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> contextSupplier) {
        NetworkEvent.Context context = contextSupplier.get();
        context.enqueueWork(() -> ClientResearchPointPresentationState.acceptHelp(snapshot));
        context.setPacketHandled(true);
    }

    HelpSnapshot snapshot() {
        return snapshot;
    }
}
