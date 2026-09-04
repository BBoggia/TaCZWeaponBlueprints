package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.RecentBlueprintUnlockBatch;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.DisclosedCraftingAccess;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingDisposition;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

class SyncBlueprintJournalPacketTest {
    @Test
    void fragmentProgressRoundTripsWithTheJournalEntry() {
        var id = new net.minecraft.resources.ResourceLocation("test:rifle");
        BlueprintJournalEntry entry = new BlueprintJournalEntry(
                0,
                JournalVisibility.FULL,
                Optional.of(id),
                Optional.of("item.test.rifle"),
                Optional.of("rifle"),
                Optional.of(new net.minecraft.resources.ResourceLocation("test:slot/rifle")),
                false, true, true, true, true,
                10, 1, 1, 2,
                Optional.of(new BlueprintJournalEntry.FragmentProgress(
                        4,
                        5,
                        BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST)),
                Optional.of(new DisclosedCraftingAccess(
                        BlueprintCraftingDisposition.TIERED,
                        Optional.of(ResearchWorkbenchTier.TIER_2))));
        BlueprintJournalSnapshot snapshot = new BlueprintJournalSnapshot(
                List.of(entry), List.of(), 20, 100, 0, 1, 1);

        SyncBlueprintJournalPacket packet = SyncBlueprintJournalPacket
                .split(snapshot, 3L).get(0);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            SyncBlueprintJournalPacket decoded = new SyncBlueprintJournalPacket(buffer);
            SyncBlueprintJournalPacket.ClientAccumulator accumulator =
                    new SyncBlueprintJournalPacket.ClientAccumulator();
            assertEquals(snapshot, accumulator.accept(decoded).orElseThrow());
        } finally {
            buffer.release();
        }
    }

    @Test
    void chunksRoundTripAndAccumulateAtomicallyOutOfOrder() {
        List<BlueprintJournalEntry> entries = new ArrayList<>();
        String maximumName = "x".repeat(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH);
        for (int index = 0; index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION; index++) {
            entries.add(new BlueprintJournalEntry(
                    index,
                    JournalVisibility.NAME,
                    Optional.empty(),
                    Optional.of(maximumName),
                    Optional.empty(),
                    Optional.empty(),
                    false, false, false, false, false, 0, 0, 0, 0));
        }
        BlueprintJournalSnapshot snapshot = new BlueprintJournalSnapshot(
                entries,
                List.of(),
                List.of(new BlueprintJournalSnapshot.RecentUnlockBatch(
                        4L,
                        RecentBlueprintUnlockBatch.Source.TREE_RESEARCH,
                        new net.minecraft.resources.ResourceLocation("test:target"),
                        List.of(
                                new net.minecraft.resources.ResourceLocation("test:root"),
                                new net.minecraft.resources.ResourceLocation("test:target")),
                        2)),
                25, 1_000_000, 0, 0, 0);
        List<SyncBlueprintJournalPacket> packets = SyncBlueprintJournalPacket.split(snapshot, 55L);
        assertTrue(packets.size() > 1);

        List<SyncBlueprintJournalPacket> decoded = packets.stream().map(packet -> {
            FriendlyByteBuf encoded = new FriendlyByteBuf(Unpooled.buffer());
            packet.toBytes(encoded);
            assertTrue(encoded.readableBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES);
            return new SyncBlueprintJournalPacket(encoded);
        }).toList();
        SyncBlueprintJournalPacket.ClientAccumulator accumulator =
                new SyncBlueprintJournalPacket.ClientAccumulator();
        Optional<BlueprintJournalSnapshot> completed = Optional.empty();
        for (int index = decoded.size() - 1; index >= 0; index--) {
            completed = accumulator.accept(decoded.get(index));
        }
        assertTrue(completed.isPresent());
        assertEquals(snapshot, completed.orElseThrow());
    }

    @Test
    void newerSnapshotReplacesIncompleteAccumulationAndDuplicatesDoNotCompleteIt() {
        BlueprintJournalSnapshot first = snapshot(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION);
        BlueprintJournalSnapshot replacement = snapshot(PlayerProgressionLimits.MAX_IDS_PER_COLLECTION);
        List<SyncBlueprintJournalPacket> firstPackets = SyncBlueprintJournalPacket.split(first, 1L);
        List<SyncBlueprintJournalPacket> replacementPackets = SyncBlueprintJournalPacket.split(replacement, 2L);
        SyncBlueprintJournalPacket.ClientAccumulator accumulator =
                new SyncBlueprintJournalPacket.ClientAccumulator();

        assertFalse(accumulator.accept(firstPackets.get(0)).isPresent());
        assertFalse(accumulator.accept(replacementPackets.get(0)).isPresent());
        assertFalse(accumulator.accept(replacementPackets.get(0)).isPresent());
        Optional<BlueprintJournalSnapshot> completed = Optional.empty();
        for (int index = 1; index < replacementPackets.size(); index++) {
            completed = accumulator.accept(replacementPackets.get(index));
        }
        assertEquals(replacement, completed.orElseThrow());

        for (SyncBlueprintJournalPacket stale : firstPackets) {
            assertTrue(accumulator.accept(stale).isEmpty());
        }
        assertTrue(accumulator.accept(replacementPackets.get(0)).isEmpty());
    }

    @Test
    void conflictingDuplicateChunkFailsClosed() {
        BlueprintJournalSnapshot first = snapshot(
                PlayerProgressionLimits.MAX_IDS_PER_COLLECTION,
                "a".repeat(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH));
        BlueprintJournalSnapshot conflicting = snapshot(
                PlayerProgressionLimits.MAX_IDS_PER_COLLECTION,
                "b".repeat(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH));
        List<SyncBlueprintJournalPacket> firstPackets = SyncBlueprintJournalPacket.split(first, 7L);
        List<SyncBlueprintJournalPacket> conflictingPackets = SyncBlueprintJournalPacket.split(conflicting, 7L);
        SyncBlueprintJournalPacket.ClientAccumulator accumulator =
                new SyncBlueprintJournalPacket.ClientAccumulator();

        assertTrue(accumulator.accept(firstPackets.get(0)).isEmpty());
        assertThrows(
                IllegalArgumentException.class,
                () -> accumulator.accept(conflictingPackets.get(0)));
    }

    private static BlueprintJournalSnapshot snapshot(int count) {
        return snapshot(count, "n".repeat(BlueprintSyncLimits.MAX_TRANSLATION_KEY_LENGTH));
    }

    private static BlueprintJournalSnapshot snapshot(int count, String name) {
        List<BlueprintJournalEntry> entries = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            entries.add(new BlueprintJournalEntry(
                    index, JournalVisibility.NAME, Optional.empty(), Optional.of(name),
                    Optional.empty(), Optional.empty(),
                    false, false, false, false, false, 0, 0, 0, 0));
        }
        return new BlueprintJournalSnapshot(entries, List.of(), 0, 100, 0, 0, 0);
    }
}
