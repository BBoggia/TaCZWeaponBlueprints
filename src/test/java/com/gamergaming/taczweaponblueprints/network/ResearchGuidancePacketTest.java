package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchPathUnlockPlanner;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

class ResearchGuidancePacketTest {
    private static final ResourceLocation ROOT = id("test:root");
    private static final ResourceLocation TARGET = id("test:target");

    @Test
    void requestRoundTripsItsContainerCorrelationPublicationAndTarget() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ResearchGuidanceRequestPacket(7, 19, 42L, TARGET).toBytes(buffer);
            ResearchGuidanceRequestPacket decoded =
                    new ResearchGuidanceRequestPacket(buffer);
            assertEquals(7, decoded.containerId());
            assertEquals(19, decoded.requestId());
            assertEquals(42L, decoded.publicationGeneration());
            assertEquals(TARGET, decoded.targetId());
        } finally {
            buffer.release();
        }
    }

    @Test
    void successfulResultRoundTripsExactRouteAndMaterialEvidence() {
        ResearchBenchMenu.GuidanceResult result = new ResearchBenchMenu.GuidanceResult(
                ResearchBenchMenu.GuidanceResultCode.SUCCESS,
                Optional.of(snapshot()));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new ResearchGuidanceResultPacket(7, 19, 42L, result).toBytes(buffer);
            ResearchGuidanceResultPacket decoded =
                    new ResearchGuidanceResultPacket(buffer);
            assertEquals(7, decoded.containerId());
            assertEquals(19, decoded.requestId());
            assertEquals(42L, decoded.publicationGeneration());
            assertEquals(result, decoded.result());
        } finally {
            buffer.release();
        }
    }

    @Test
    void rejectedAndThrottledResultsCarryNoSnapshot() {
        for (ResearchBenchMenu.GuidanceResultCode code : List.of(
                ResearchBenchMenu.GuidanceResultCode.REJECTED,
                ResearchBenchMenu.GuidanceResultCode.THROTTLED)) {
            ResearchBenchMenu.GuidanceResult result =
                    new ResearchBenchMenu.GuidanceResult(code, Optional.empty());
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new ResearchGuidanceResultPacket(2, 3, 4L, result).toBytes(buffer);
                assertEquals(result, new ResearchGuidanceResultPacket(buffer).result());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void constructorsAndDecoderRejectUnboundedOrUncorrelatedInput() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchGuidanceRequestPacket(1, 0, 2L, TARGET));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchGuidanceResultPacket(
                        1,
                        1,
                        Long.MIN_VALUE,
                        new ResearchBenchMenu.GuidanceResult(
                                ResearchBenchMenu.GuidanceResultCode.REJECTED,
                                Optional.empty())));

        FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
        try {
            invalid.writeVarInt(1);
            invalid.writeVarInt(2);
            invalid.writeLong(3L);
            invalid.writeVarInt(ResearchBenchMenu.GuidanceResultCode.SUCCESS.ordinal());
            invalid.writeUtf(TARGET.toString());
            invalid.writeVarInt(ResearchGuidanceSnapshot.State.AFFORDABLE.ordinal());
            invalid.writeVarInt(1);
            invalid.writeVarInt(1);
            invalid.writeVarInt(ResearchCostMode.POINTS_AND_ITEMS.ordinal());
            invalid.writeBoolean(false);
            invalid.writeBoolean(true);
            invalid.writeVarInt(1);
            invalid.writeVarInt(1);
            invalid.writeVarInt(1);
            invalid.writeVarInt(0);
            invalid.writeVarInt(ResearchGuidanceSnapshot.MAX_MATERIAL_PROGRESS + 1);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new ResearchGuidanceResultPacket(invalid));
        } finally {
            invalid.release();
        }
    }

    private static ResearchGuidanceSnapshot snapshot() {
        return new ResearchGuidanceSnapshot(
                TARGET,
                ResearchGuidanceSnapshot.State.AFFORDABLE,
                5,
                8,
                ResearchCostMode.POINTS_AND_ITEMS,
                false,
                true,
                1,
                List.of(new ResearchGuidanceSnapshot.MaterialProgress(
                        List.of(id("minecraft:paper")), Optional.empty(), 2, 2)),
                List.of(ROOT, TARGET),
                List.of(ROOT, TARGET),
                List.of(new ResearchPathUnlockPlanner.SelectedRequirement(
                        TARGET, 0, ROOT)),
                Optional.of(ROOT));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
