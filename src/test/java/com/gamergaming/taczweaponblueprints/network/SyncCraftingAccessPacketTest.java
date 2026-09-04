package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService.AccessIdentity;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

class SyncCraftingAccessPacketTest {
    private static final AccessIdentity IDENTITY = identity(7L);

    @Test
    void readySnapshotRoundTripsInStableOrder() {
        var snapshot = new CraftingEligibilityService.Snapshot(
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:bravo", "test:alpha"),
                Optional.of(IDENTITY));
        SyncCraftingAccessPacket source =
                SyncCraftingAccessPacket.split(7, 11L, 3L, snapshot).get(0);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            source.toBytes(buffer);
            SyncCraftingAccessPacket decoded = new SyncCraftingAccessPacket(buffer);

            assertEquals(7, decoded.containerId());
            assertEquals(11L, decoded.requestId());
            assertEquals(3L, decoded.snapshotId());
            assertEquals(Optional.of(IDENTITY), decoded.accessIdentity());
            assertEquals(0, decoded.chunkIndex());
            assertEquals(1, decoded.chunkCount());
            assertEquals(CraftingEligibilityService.Status.ALLOWED, decoded.status());
            assertEquals(Set.of("test:alpha", "test:bravo"), decoded.entries());
        } finally {
            buffer.release();
        }
    }

    @Test
    void unavailableSnapshotUsesOneEmptyChunk() {
        var snapshot = new CraftingEligibilityService.Snapshot(
                CraftingEligibilityService.Status.POLICY_UNAVAILABLE,
                Set.of(),
                Optional.empty());
        List<SyncCraftingAccessPacket> packets =
                SyncCraftingAccessPacket.split(1, 2L, 1L, snapshot);

        assertEquals(1, packets.size());
        assertTrue(packets.get(0).entries().isEmpty());
        assertEquals(CraftingEligibilityService.Status.POLICY_UNAVAILABLE,
                packets.get(0).status());
    }

    @Test
    void distinctPolicyDenialsRoundTripWithoutRecipeDisclosure() {
        for (CraftingEligibilityService.Status status : List.of(
                CraftingEligibilityService.Status.CRAFTING_POLICY_MISSING,
                CraftingEligibilityService.Status.CRAFTING_DISABLED,
                CraftingEligibilityService.Status.WORKBENCH_TIER_REQUIRED,
                CraftingEligibilityService.Status.PROGRESSION_GATE_REQUIRED)) {
            SyncCraftingAccessPacket source = SyncCraftingAccessPacket.split(
                    2,
                    3L,
                    4L,
                    new CraftingEligibilityService.Snapshot(
                            status, Set.of(), Optional.of(IDENTITY))).get(0);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                source.toBytes(buffer);
                SyncCraftingAccessPacket decoded = new SyncCraftingAccessPacket(buffer);

                assertEquals(status, decoded.status());
                assertTrue(decoded.entries().isEmpty());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void maximumSupportedCollectionIsChunkedAndAssembledAtomically() {
        TreeSet<String> ids = new TreeSet<>();
        String padding = "a".repeat(235);
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
                index++) {
            ids.add("test:" + padding + String.format("%04x", index));
        }
        List<SyncCraftingAccessPacket> packets = SyncCraftingAccessPacket.split(
                9,
                15L,
                4L,
                new CraftingEligibilityService.Snapshot(
                        CraftingEligibilityService.Status.ALLOWED,
                        ids,
                        Optional.of(IDENTITY)));

        assertTrue(packets.size() > 1);
        SyncCraftingAccessPacket.ClientAccumulator accumulator =
                new SyncCraftingAccessPacket.ClientAccumulator();
        Optional<SyncCraftingAccessPacket.Snapshot> completed = Optional.empty();
        for (int index = packets.size() - 1; index >= 0; index--) {
            SyncCraftingAccessPacket packet = packets.get(index);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.toBytes(buffer);
                assertTrue(buffer.readableBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES);
            } finally {
                buffer.release();
            }
            Optional<SyncCraftingAccessPacket.Snapshot> accepted = accumulator
                    .accept(packet).completedSnapshot();
            if (accepted.isPresent()) {
                completed = accepted;
            }
        }

        assertTrue(completed.isPresent());
        assertEquals(ids, completed.orElseThrow().allowedRecipeIds());
    }

    @Test
    void accumulatorRejectsAnOlderMenuRequestAfterANewerOne() {
        var snapshot = new CraftingEligibilityService.Snapshot(
                CraftingEligibilityService.Status.ALLOWED,
                Set.of("test:alpha"),
                Optional.of(IDENTITY));
        SyncCraftingAccessPacket older =
                SyncCraftingAccessPacket.split(4, 20L, 1L, snapshot).get(0);
        SyncCraftingAccessPacket newer =
                SyncCraftingAccessPacket.split(4, 21L, 1L, snapshot).get(0);
        SyncCraftingAccessPacket.ClientAccumulator accumulator =
                new SyncCraftingAccessPacket.ClientAccumulator();

        assertTrue(accumulator.accept(newer).completedSnapshot().isPresent());
        var stale = accumulator.accept(older);
        assertTrue(stale.completedSnapshot().isEmpty());
        assertFalse(stale.startedSnapshot());
    }

    @Test
    void newerSnapshotForTheSameMenuRequestReplacesCompletedAccess() {
        SyncCraftingAccessPacket first = SyncCraftingAccessPacket.split(
                4,
                20L,
                1L,
                new CraftingEligibilityService.Snapshot(
                        CraftingEligibilityService.Status.ALLOWED,
                        Set.of("test:alpha"),
                        Optional.of(IDENTITY))).get(0);
        SyncCraftingAccessPacket refreshed = SyncCraftingAccessPacket.split(
                4,
                20L,
                2L,
                new CraftingEligibilityService.Snapshot(
                        CraftingEligibilityService.Status.ALLOWED,
                        Set.of("test:bravo"),
                        Optional.of(identity(8L)))).get(0);
        SyncCraftingAccessPacket.ClientAccumulator accumulator =
                new SyncCraftingAccessPacket.ClientAccumulator();

        assertEquals(Set.of("test:alpha"),
                accumulator.accept(first).completedSnapshot().orElseThrow()
                        .allowedRecipeIds());
        assertEquals(Set.of("test:bravo"),
                accumulator.accept(refreshed).completedSnapshot().orElseThrow()
                        .allowedRecipeIds());
        assertTrue(accumulator.accept(first).completedSnapshot().isEmpty());
    }

    @Test
    void accumulatorRejectsMixedIdentityAndConflictingCompletedDuplicates() {
        TreeSet<String> ids = maximumIds();
        List<SyncCraftingAccessPacket> firstIdentity = SyncCraftingAccessPacket.split(
                6,
                30L,
                2L,
                new CraftingEligibilityService.Snapshot(
                        CraftingEligibilityService.Status.ALLOWED,
                        ids,
                        Optional.of(IDENTITY)));
        List<SyncCraftingAccessPacket> secondIdentity = SyncCraftingAccessPacket.split(
                6,
                30L,
                2L,
                new CraftingEligibilityService.Snapshot(
                        CraftingEligibilityService.Status.ALLOWED,
                        ids,
                        Optional.of(identity(9L))));
        assertTrue(firstIdentity.size() > 1);
        SyncCraftingAccessPacket.ClientAccumulator mixed =
                new SyncCraftingAccessPacket.ClientAccumulator();
        mixed.accept(firstIdentity.get(0));
        assertThrows(IllegalArgumentException.class,
                () -> mixed.accept(secondIdentity.get(1)));

        SyncCraftingAccessPacket alpha = SyncCraftingAccessPacket.split(
                6,
                31L,
                1L,
                new CraftingEligibilityService.Snapshot(
                        CraftingEligibilityService.Status.ALLOWED,
                        Set.of("test:alpha"),
                        Optional.of(IDENTITY))).get(0);
        SyncCraftingAccessPacket bravo = SyncCraftingAccessPacket.split(
                6,
                31L,
                1L,
                new CraftingEligibilityService.Snapshot(
                        CraftingEligibilityService.Status.ALLOWED,
                        Set.of("test:bravo"),
                        Optional.of(IDENTITY))).get(0);
        SyncCraftingAccessPacket.ClientAccumulator conflicting =
                new SyncCraftingAccessPacket.ClientAccumulator();
        assertTrue(conflicting.accept(alpha).completedSnapshot().isPresent());
        assertThrows(IllegalArgumentException.class, () -> conflicting.accept(bravo));
    }

    @Test
    void decoderRejectsDuplicateAndMalformedIds() {
        FriendlyByteBuf duplicate = raw(
                CraftingEligibilityService.Status.ALLOWED, 2);
        try {
            duplicate.writeUtf("test:same");
            duplicate.writeUtf("test:same");
            assertThrows(IllegalArgumentException.class,
                    () -> new SyncCraftingAccessPacket(duplicate));
        } finally {
            duplicate.release();
        }

        FriendlyByteBuf malformed = raw(
                CraftingEligibilityService.Status.ALLOWED, 1);
        try {
            malformed.writeUtf("not an id");
            assertThrows(IllegalArgumentException.class,
                    () -> new SyncCraftingAccessPacket(malformed));
        } finally {
            malformed.release();
        }
    }

    private static FriendlyByteBuf raw(
            CraftingEligibilityService.Status status,
            int size) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeVarInt(1);
        buffer.writeLong(1L);
        buffer.writeLong(1L);
        writeIdentity(buffer, IDENTITY);
        buffer.writeVarInt(0);
        buffer.writeVarInt(1);
        buffer.writeEnum(status);
        buffer.writeVarInt(size);
        return buffer;
    }

    private static void writeIdentity(FriendlyByteBuf buffer, AccessIdentity identity) {
        buffer.writeBoolean(true);
        buffer.writeLong(identity.catalogRevision());
        buffer.writeLong(identity.researchRevision());
        buffer.writeLong(identity.automaticRevision());
        buffer.writeLong(identity.evidenceRevision());
        buffer.writeLong(identity.ammoAssociationRevision());
        buffer.writeLong(identity.policyPublicationRevision());
        buffer.writeUtf(identity.profileId().toString());
        buffer.writeUtf(identity.workstationId().toString());
        buffer.writeEnum(identity.workstationTier());
        buffer.writeBoolean(identity.unrestrictedWorkbench());
        buffer.writeBoolean(identity.enforceCraftingTiers());
        buffer.writeBoolean(identity.bypassTier());
        buffer.writeBoolean(identity.bypassGates());
        buffer.writeBoolean(identity.blueprintsEnabled());
    }

    private static TreeSet<String> maximumIds() {
        TreeSet<String> ids = new TreeSet<>();
        String padding = "a".repeat(235);
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
                index++) {
            ids.add("test:" + padding + String.format("%04x", index));
        }
        return ids;
    }

    private static AccessIdentity identity(long policyRevision) {
        return new AccessIdentity(
                2L,
                3L,
                4L,
                5L,
                6L,
                policyRevision,
                new ResourceLocation("test:profile"),
                new ResourceLocation("test:workbench"),
                ResearchWorkbenchTier.TIER_2,
                false,
                true,
                false,
                false,
                true);
    }
}
