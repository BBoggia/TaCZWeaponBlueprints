package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary;

class ResearchMaterialPagePacketTest {
    private static final ResourceLocation TARGET = new ResourceLocation("test:target");
    @Test void requestAndPartialFinalPageRoundTripWithoutRelaxingMaterialBounds() {
        var request = new ResearchMaterialPageRequestPacket(2, 19, TARGET, 0, 6);
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            request.toBytes(b); assertEquals(request, new ResearchMaterialPageRequestPacket(b));
            var row = new ResearchSelectionPreview.IngredientPreview(List.of(new ResourceLocation("minecraft:paper")), Optional.empty(), 3, 1);
            var preview = new ResearchSelectionPreview(Optional.of(TARGET), 2, 0, true, false, true, false, false,
                    List.of(row), 2, 7, ResearchSelectionPreview.PathPlanningState.NONE, ResearchCostMode.POINTS_AND_ITEMS,
                    Optional.empty(), ResearchAccessSummary.NONE, ResearchSelectionProgressionPreview.EMPTY, 6);
            new SyncResearchBenchPreviewPacket(2, preview).toBytes(b);
            assertEquals(preview, new SyncResearchBenchPreviewPacket(b).preview());
            assertEquals(0, b.readableBytes());
        } finally { b.release(); }
    }
    @Test void invalidPageRequestsAreRejectedBeforeWorkIsScheduled() {
        for (int[] offsets : new int[][]{{-6,0},{0,-6},{1,7},{0,12},{6,6},{24570,24576},{0,Integer.MAX_VALUE}}) {
            assertThrows(IllegalArgumentException.class, () -> new ResearchMaterialPageRequestPacket(2, 19, TARGET, offsets[0], offsets[1]));
        }
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            b.writeVarInt(2); b.writeLong(19); b.writeUtf(TARGET.toString()); b.writeVarInt(0); b.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class, () -> new ResearchMaterialPageRequestPacket(b));
        } finally { b.release(); }
    }
    @Test void malformedPreviewOffsetCannotExposeAnOutOfRangePage() {
        var row = new ResearchSelectionPreview.IngredientPreview(List.of(new ResourceLocation("minecraft:paper")), Optional.empty(), 3, 1);
        var preview = new ResearchSelectionPreview(Optional.of(TARGET), 2, 0, true, false, true, false, false,
                List.of(row), 2, 7, ResearchSelectionPreview.PathPlanningState.NONE, ResearchCostMode.POINTS_AND_ITEMS,
                Optional.empty(), ResearchAccessSummary.NONE, ResearchSelectionProgressionPreview.EMPTY, 6);
        for (int offset : new int[]{1, 7, 12, 120}) {
            var b = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new SyncResearchBenchPreviewPacket(2, preview).toBytes(b);
                b.setByte(b.writerIndex() - 1, offset);
                assertThrows(IllegalArgumentException.class, () -> new SyncResearchBenchPreviewPacket(b));
            } finally { b.release(); }
        }
    }
}
