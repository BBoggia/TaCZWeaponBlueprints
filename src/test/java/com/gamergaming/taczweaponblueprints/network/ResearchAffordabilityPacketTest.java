package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.ResearchAffordabilitySnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

class ResearchAffordabilityPacketTest {
    private static final ResourceLocation A = id("test:a");
    private static final ResourceLocation B = id("test:b");

    @Test
    void requestRoundTripsItsBoundedOrderedTargetBatch() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ResearchAffordabilityRequestPacket(7, 19, 42L, List.of(A, B))
                    .toBytes(buffer);
            ResearchAffordabilityRequestPacket decoded =
                    new ResearchAffordabilityRequestPacket(buffer);
            assertEquals(7, decoded.containerId());
            assertEquals(19, decoded.requestId());
            assertEquals(42L, decoded.publicationGeneration());
            assertEquals(List.of(A, B), decoded.targetIds());
        } finally {
            buffer.release();
        }
    }

    @Test
    void resultRoundTripsAffordabilityAndIndependentCapacityEvidence() {
        ResearchBenchMenu.AffordabilityResult result =
                new ResearchBenchMenu.AffordabilityResult(
                        ResearchBenchMenu.AffordabilityResultCode.SUCCESS,
                        Optional.of(new ResearchAffordabilitySnapshot(List.of(
                                new ResearchAffordabilitySnapshot.Entry(
                                        A,
                                        ResearchGuidanceSnapshot.State.AFFORDABLE,
                                        false),
                                new ResearchAffordabilitySnapshot.Entry(
                                        B,
                                        ResearchGuidanceSnapshot.State.MISSING_MATERIALS,
                                        true)))));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ResearchAffordabilityResultPacket(7, 19, 42L, result).toBytes(buffer);
            ResearchAffordabilityResultPacket decoded =
                    new ResearchAffordabilityResultPacket(buffer);
            assertEquals(7, decoded.containerId());
            assertEquals(19, decoded.requestId());
            assertEquals(42L, decoded.publicationGeneration());
            assertEquals(result, decoded.result());
            ResearchAffordabilitySnapshot.Entry affordable = decoded.result().snapshot()
                    .orElseThrow().entries().get(0);
            assertFalse(affordable.transactionCapacityAvailable());
            assertTrue(affordable.affordableNow());
        } finally {
            buffer.release();
        }
    }

    @Test
    void queuedAcknowledgementRoundTripsWithoutInventingResults() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ResearchAffordabilityResultPacket(
                    7,
                    19,
                    42L,
                    ResearchBenchMenu.AffordabilityResult.queued())
                    .toBytes(buffer);

            ResearchAffordabilityResultPacket decoded =
                    new ResearchAffordabilityResultPacket(buffer);

            assertEquals(
                    ResearchBenchMenu.AffordabilityResultCode.QUEUED,
                    decoded.result().code());
            assertTrue(decoded.result().snapshot().isEmpty());
        } finally {
            buffer.release();
        }
    }

    @Test
    void packetBoundariesRejectEmptyDuplicateAndOversizedBatches() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchAffordabilityRequestPacket(1, 1, 1L, List.of()));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchAffordabilityRequestPacket(1, 1, 1L, List.of(A, A)));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchAffordabilitySnapshot(java.util.stream.IntStream.rangeClosed(
                                0,
                                ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH)
                        .mapToObj(index -> new ResearchAffordabilitySnapshot.Entry(
                                id("test:oversized_" + index),
                                ResearchGuidanceSnapshot.State.AFFORDABLE,
                                true))
                        .toList()));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchAffordabilitySnapshot.Entry(
                        A, ResearchGuidanceSnapshot.State.CHECKING, true));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
