package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.PlayerSupplementalProgressionView;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

class SyncPlayerSupplementalProgressionPacketTest {
    @Test
    void analysisClassesRoundTripAcrossReorderedChunksWithoutPartialPublication() {
        var analysis = new java.util.TreeMap<String, com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts>();
        var counts = new com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts(1,2,3,4);
        for (int i=0; i<PlayerProgressionLimits.MAX_FRAGMENT_TARGETS; i++) analysis.put("test:" + "x".repeat(240) + i, counts);
        var view = new PlayerSupplementalProgressionView(Map.of(), Map.of("test:criterion",5), analysis);
        var packets = SyncPlayerSupplementalProgressionPacket.split(view, 70L);
        assertTrue(packets.size() > 1);
        var accumulator = new SyncPlayerSupplementalProgressionPacket.ClientAccumulator();
        for (int i=packets.size()-1; i>=0; i--) {
            var buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packets.get(i).toBytes(buffer);
                assertTrue(buffer.readableBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES);
                assertEquals(buffer.readableBytes(), packets.get(i).estimatedPayloadBytes());
                var completed = accumulator.accept(new SyncPlayerSupplementalProgressionPacket(buffer));
                assertEquals(0, buffer.readableBytes());
                if (i>0) assertTrue(completed.isEmpty());
                else {
                    var snapshot = completed.orElseThrow();
                    assertEquals(analysis, snapshot.analysisFragments());
                    var client = new com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData();
                    assertTrue(client.replaceSupplementalProgression(snapshot.archivedFragments(), snapshot.publicCriteria(), snapshot.analysisFragments()));
                    assertEquals(analysis, client.getArchivedAnalysisFragments());
                }
            } finally { buffer.release(); }
        }
        assertTrue(accumulator.accept(packets.get(0)).isEmpty());
        accumulator.clear();
        var empty = SyncPlayerSupplementalProgressionPacket.split(PlayerSupplementalProgressionView.EMPTY, 1L).get(0);
        assertTrue(accumulator.accept(empty).orElseThrow().analysisFragments().isEmpty());
    }

    @Test
    void malformedAnalysisValuesAndCrossChunkCombinedOverflowAreRejected() {
        for (int value : new int[]{-1, Integer.MAX_VALUE, 0}) {
            var buffer = rawPacketHeader(80L,0,1);
            try {
                buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeVarInt(1);
                buffer.writeUtf("test:rifle"); buffer.writeVarInt(value);
                buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeVarInt(0);
                assertThrows(IllegalArgumentException.class, () -> new SyncPlayerSupplementalProgressionPacket(buffer));
            } finally { buffer.release(); }
        }
        var accumulator = new SyncPlayerSupplementalProgressionPacket.ClientAccumulator();
        var first = decodedPacket(90L,0,2,Map.of("test:rifle",PlayerProgressionLimits.MAX_PROGRESS_VALUE),Map.of());
        assertTrue(accumulator.accept(first).isEmpty());
        var buffer = rawPacketHeader(90L,1,2);
        try {
            buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeVarInt(1);
            buffer.writeUtf("test:rifle"); buffer.writeVarInt(1); buffer.writeVarInt(0); buffer.writeVarInt(0); buffer.writeVarInt(0);
            var second = new SyncPlayerSupplementalProgressionPacket(buffer);
            assertThrows(IllegalArgumentException.class, () -> accumulator.accept(second));
        } finally { buffer.release(); }
        assertTrue(accumulator.accept(decodedPacket(91L,0,1,Map.of(),Map.of())).isPresent());
    }

    @Test
    void packetRoundTripsStableMapsAndAccumulatesAtomically() {
        PlayerSupplementalProgressionView view = new PlayerSupplementalProgressionView(
                Map.of("test:bravo", 2, "test:alpha", 1),
                Map.of("test:trial", 7));
        List<SyncPlayerSupplementalProgressionPacket> packets =
                SyncPlayerSupplementalProgressionPacket.split(view, 9L);
        assertEquals(1, packets.size());

        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packets.get(0).toBytes(buffer);
            SyncPlayerSupplementalProgressionPacket decoded =
                    new SyncPlayerSupplementalProgressionPacket(buffer);
            assertEquals(Map.of("test:alpha", 1, "test:bravo", 2),
                    decoded.archivedFragments());
            assertEquals(Map.of("test:trial", 7), decoded.publicCriteria());
            var completed = new SyncPlayerSupplementalProgressionPacket.ClientAccumulator()
                    .accept(decoded).orElseThrow();
            assertEquals(view.archivedFragments(), completed.archivedFragments());
            assertEquals(view.publicCriteria(), completed.publicCriteria());
        } finally {
            buffer.release();
        }
    }

    @Test
    void decoderRejectsDuplicateInvalidAndOversizedEntries() {
        FriendlyByteBuf duplicate = rawPacketHeader(1L, 0, 1);
        try {
            duplicate.writeVarInt(2);
            writeEntry(duplicate, "test:same", 1);
            writeEntry(duplicate, "test:same", 2);
            duplicate.writeVarInt(0);
            assertThrows(IllegalArgumentException.class,
                    () -> new SyncPlayerSupplementalProgressionPacket(duplicate));
        } finally {
            duplicate.release();
        }

        FriendlyByteBuf zero = rawPacketHeader(2L, 0, 1);
        try {
            zero.writeVarInt(1);
            writeEntry(zero, "test:zero", 0);
            zero.writeVarInt(0);
            assertThrows(IllegalArgumentException.class,
                    () -> new SyncPlayerSupplementalProgressionPacket(zero));
        } finally {
            zero.release();
        }

        FriendlyByteBuf excessive = rawPacketHeader(3L, 0, 1);
        try {
            excessive.writeVarInt(PlayerProgressionLimits.MAX_FRAGMENT_TARGETS + 1);
            assertThrows(IllegalArgumentException.class,
                    () -> new SyncPlayerSupplementalProgressionPacket(excessive));
        } finally {
            excessive.release();
        }
    }

    @Test
    void accumulatorRejectsStaleConflictingAndCumulativeOverflowSnapshots() {
        var accumulator = new SyncPlayerSupplementalProgressionPacket.ClientAccumulator();
        var newer = decodedPacket(20L, 0, 1, Map.of("test:new", 1), Map.of());
        var stale = decodedPacket(19L, 0, 1, Map.of("test:old", 1), Map.of());
        assertTrue(accumulator.accept(newer).isPresent());
        assertTrue(accumulator.accept(stale).isEmpty());

        accumulator.clear();
        var first = decodedPacket(30L, 0, 2, Map.of("test:first", 1), Map.of());
        var conflict = decodedPacket(30L, 0, 2, Map.of("test:conflict", 1), Map.of());
        assertTrue(accumulator.accept(first).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(conflict));

        accumulator.clear();
        var manyFirst = decodedPacket(40L, 0, 2, progressMap("a", 2_050), Map.of());
        var manySecond = decodedPacket(40L, 1, 2, progressMap("b", 2_050), Map.of());
        assertTrue(accumulator.accept(manyFirst).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(manySecond));
    }

    @Test
    void accumulatorRecoversAfterDuplicateIdsAcrossCompletedChunks() {
        var accumulator = new SyncPlayerSupplementalProgressionPacket.ClientAccumulator();
        var first = decodedPacket(50L, 0, 2, Map.of("test:same", 1), Map.of());
        var duplicate = decodedPacket(50L, 1, 2, Map.of("test:same", 2), Map.of());

        assertTrue(accumulator.accept(first).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> accumulator.accept(duplicate));

        var replacement = decodedPacket(
                50L, 0, 1, Map.of("test:recovered", 3), Map.of("test:trial", 1));
        var completed = accumulator.accept(replacement).orElseThrow();
        assertEquals(Map.of("test:recovered", 3), completed.archivedFragments());
        assertEquals(Map.of("test:trial", 1), completed.publicCriteria());
    }

    @Test
    void maximumMapsSplitWithinTheWireBudget() {
        Map<String, Integer> fragments = maximumLengthProgressMap("frag");
        Map<String, Integer> criteria = maximumLengthProgressMap("gate");
        var packets = SyncPlayerSupplementalProgressionPacket.split(
                new PlayerSupplementalProgressionView(fragments, criteria),
                100L);

        assertTrue(packets.size() > 1);
        assertTrue(packets.size() <= BlueprintSyncLimits.MAX_CHUNKS_PER_SNAPSHOT);
        assertTrue(packets.stream().allMatch(packet ->
                packet.estimatedPayloadBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES));
        var accumulator = new SyncPlayerSupplementalProgressionPacket.ClientAccumulator();
        SyncPlayerSupplementalProgressionPacket.Snapshot completed = null;
        for (SyncPlayerSupplementalProgressionPacket packet : packets) {
            var result = accumulator.accept(packet);
            if (result.isPresent()) {
                completed = result.orElseThrow();
            }
        }
        assertEquals(fragments, completed.archivedFragments());
        assertEquals(criteria, completed.publicCriteria());
    }

    private static FriendlyByteBuf rawPacketHeader(
            long syncId,
            int chunkIndex,
            int chunkCount) {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        buffer.writeLong(syncId);
        buffer.writeVarInt(chunkIndex);
        buffer.writeVarInt(chunkCount);
        return buffer;
    }

    private static SyncPlayerSupplementalProgressionPacket decodedPacket(
            long syncId,
            int chunkIndex,
            int chunkCount,
            Map<String, Integer> fragments,
            Map<String, Integer> criteria) {
        FriendlyByteBuf buffer = rawPacketHeader(syncId, chunkIndex, chunkCount);
        try {
            writeMap(buffer, fragments);
            writeMap(buffer, criteria);
            buffer.writeVarInt(0); // Protocol 57 analysis archive.
            return new SyncPlayerSupplementalProgressionPacket(buffer);
        } finally {
            buffer.release();
        }
    }

    private static void writeMap(FriendlyByteBuf buffer, Map<String, Integer> values) {
        buffer.writeVarInt(values.size());
        values.forEach((id, value) -> writeEntry(buffer, id, value));
    }

    private static void writeEntry(FriendlyByteBuf buffer, String id, int value) {
        buffer.writeUtf(id, PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH);
        buffer.writeVarInt(value);
    }

    private static Map<String, Integer> progressMap(String namespace, int count) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int index = 0; index < count; index++) {
            values.put(namespace + ":entry_" + index, index + 1);
        }
        return values;
    }

    private static Map<String, Integer> maximumLengthProgressMap(String prefix) {
        Map<String, Integer> values = new LinkedHashMap<>();
        for (int index = 0; index < PlayerProgressionLimits.MAX_IDS_PER_COLLECTION; index++) {
            String suffix = Integer.toString(index);
            values.put(
                    "test:" + prefix + "_" + "x".repeat(
                            250 - prefix.length() - suffix.length()) + suffix,
                    PlayerProgressionLimits.MAX_PROGRESS_VALUE);
        }
        return values;
    }
}
