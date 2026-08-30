package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchTreeV1RedesignBaselineTest;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;

/** Freezes the format-1 redesign publication across the current protocol boundary. */
class SyncResearchTreeV1RedesignBaselineTest {
    @Test
    void legacyPublicationRoundTripsAtomicallyThroughChunkedPackets() throws Exception {
        ResearchTreePublication publication =
                ResearchTreeV1RedesignBaselineTest.publicationFixture();
        List<SyncResearchTreePacket> packets = SyncResearchTreePacket.split(publication, 91L);
        SyncResearchTreePacket.ClientAccumulator accumulator =
                new SyncResearchTreePacket.ClientAccumulator();
        Optional<ResearchTreePublication> decoded = Optional.empty();

        for (int index = packets.size() - 1; index >= 0; index--) {
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packets.get(index).toBytes(buffer);
                assertTrue(buffer.readableBytes() <= BlueprintSyncLimits.MAX_CHUNK_BYTES);
                decoded = accumulator.accept(new SyncResearchTreePacket(buffer));
            } finally {
                buffer.release();
            }
        }

        assertEquals(publication, decoded.orElseThrow());
    }
}
