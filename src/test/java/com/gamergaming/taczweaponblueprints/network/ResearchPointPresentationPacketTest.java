package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.Feedback;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpEntry;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpSnapshot;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

class ResearchPointPresentationPacketTest {
    @Test
    void feedbackRoundTripsWithoutDefinitionOrTargetIds() {
        Feedback value = new Feedback(9, 1, true, List.of("award.test.zombie"));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ResearchPointFeedbackPacket(value).toBytes(buffer);
            assertEquals(value, new ResearchPointFeedbackPacket(buffer).feedback());
        } finally {
            buffer.release();
        }
    }

    @Test
    void feedbackRejectsOverBoundNameCountsBeforeAllocating() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);
            buffer.writeVarInt(0);
            buffer.writeBoolean(false);
            buffer.writeVarInt(PlayerProgressionLimits.MAX_RESEARCH_POINT_FEEDBACK_NAMES + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> new ResearchPointFeedbackPacket(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void filteredHelpRoundTripsWithBoundedTypedEntries() {
        HelpSnapshot value = new HelpSnapshot(17L, List.of(
                new HelpEntry(
                        "award.test.discovery",
                        ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                        3),
                new HelpEntry(
                        "award.test.combat",
                        ResearchPointAwardTrigger.Type.ENTITY_KILLED,
                        2)));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SyncResearchPointHelpPacket(value).toBytes(buffer);
            assertEquals(value, new SyncResearchPointHelpPacket(buffer).snapshot());
        } finally {
            buffer.release();
        }
    }

    @Test
    void helpRejectsOverBoundCountsAndUnknownTriggerTypes() {
        FriendlyByteBuf tooMany = new FriendlyByteBuf(Unpooled.buffer());
        try {
            tooMany.writeVarLong(1L);
            tooMany.writeVarInt(PlayerProgressionLimits.MAX_RESEARCH_POINT_HELP_ENTRIES + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> new SyncResearchPointHelpPacket(tooMany));
        } finally {
            tooMany.release();
        }

        FriendlyByteBuf badTrigger = new FriendlyByteBuf(Unpooled.buffer());
        try {
            badTrigger.writeVarLong(1L);
            badTrigger.writeVarInt(1);
            badTrigger.writeUtf("award.test.invalid");
            badTrigger.writeVarInt(ResearchPointAwardTrigger.Type.values().length);
            assertThrows(IllegalArgumentException.class,
                    () -> new SyncResearchPointHelpPacket(badTrigger));
        } finally {
            badTrigger.release();
        }
    }
}
