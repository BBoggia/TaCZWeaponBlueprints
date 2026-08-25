package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchPreview;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

class ResearchBenchPacketTest {
    private static final ResourceLocation BLUEPRINT = new ResourceLocation("test:rifle");

    @Test
    void actionRoundTripsOnlyContainerActionAndBoundedBlueprintId() {
        ResearchBenchActionPacket packet = new ResearchBenchActionPacket(
                17, ResearchBenchMenu.Action.RESEARCH, Optional.of(BLUEPRINT));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            ResearchBenchActionPacket decoded = new ResearchBenchActionPacket(buffer);
            assertEquals(17, decoded.containerId());
            assertEquals(ResearchBenchMenu.Action.RESEARCH, decoded.action());
            assertEquals(Optional.of(BLUEPRINT), decoded.blueprintId());
        } finally {
            buffer.release();
        }
    }

    @Test
    void everyResearchBenchActionRoundTrips() {
        for (ResearchBenchMenu.Action action : ResearchBenchMenu.Action.values()) {
            ResearchBenchActionPacket packet = new ResearchBenchActionPacket(
                    23, action, Optional.empty());
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                packet.toBytes(buffer);
                ResearchBenchActionPacket decoded = new ResearchBenchActionPacket(buffer);
                assertEquals(23, decoded.containerId());
                assertEquals(action, decoded.action());
                assertEquals(Optional.empty(), decoded.blueprintId());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void actionRejectsUnknownOrdinalsAndOversizedIdsBeforeHandling() {
        FriendlyByteBuf ordinal = new FriendlyByteBuf(Unpooled.buffer());
        try {
            ordinal.writeVarInt(1);
            ordinal.writeVarInt(ResearchBenchMenu.Action.values().length);
            ordinal.writeBoolean(false);
            assertThrows(IllegalArgumentException.class, () -> new ResearchBenchActionPacket(ordinal));
        } finally {
            ordinal.release();
        }

        assertThrows(IllegalArgumentException.class, () -> new ResearchBenchActionPacket(
                1,
                ResearchBenchMenu.Action.SELECT,
                Optional.of(new ResourceLocation("test", "x".repeat(252)))));
    }

    @Test
    void exactIngredientPreviewRoundTripsWithStrictBounds() {
        ResearchBenchPreview preview = new ResearchBenchPreview(
                Optional.of(BLUEPRINT),
                8,
                12,
                true,
                false,
                true,
                false,
                false,
                List.of(
                        new ResearchBenchPreview.IngredientPreview(
                                List.of(new ResourceLocation("minecraft:paper")),
                                Optional.empty(),
                                4,
                                4),
                        new ResearchBenchPreview.IngredientPreview(
                                List.of(new ResourceLocation("minecraft:iron_ingot")),
                                Optional.of(new ResourceLocation("forge:ingots/iron")),
                                2,
                                1)),
                new ResearchBenchPreview.RecyclingPreview(
                        Optional.of(BLUEPRINT),
                        BlueprintRecyclingService.Status.SUCCESS,
                        2,
                        12,
                        20));
        SyncResearchBenchPreviewPacket packet = new SyncResearchBenchPreviewPacket(4, preview);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            SyncResearchBenchPreviewPacket decoded = new SyncResearchBenchPreviewPacket(buffer);
            assertEquals(4, decoded.containerId());
            assertEquals(preview, decoded.preview());
        } finally {
            buffer.release();
        }
    }

    @Test
    void previewRejectsExcessIngredientTypesDuringDecode() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);
            buffer.writeBoolean(false);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeVarInt(7);
            assertThrows(IllegalArgumentException.class, () -> new SyncResearchBenchPreviewPacket(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void previewRejectsUnknownRecyclingStatusesDuringDecode() {
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            buffer.writeVarInt(1);
            buffer.writeBoolean(false);
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeBoolean(false);
            buffer.writeVarInt(0);
            buffer.writeBoolean(false);
            buffer.writeVarInt(BlueprintRecyclingService.Status.values().length);
            assertThrows(IllegalArgumentException.class, () -> new SyncResearchBenchPreviewPacket(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void previewRejectsOverstatedOrMisleadingMaterialSummaries() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchBenchPreview.IngredientPreview(
                List.of(new ResourceLocation("minecraft:paper")),
                Optional.empty(),
                4,
                5));

        assertThrows(IllegalArgumentException.class, () -> new ResearchBenchPreview(
                Optional.of(BLUEPRINT),
                0,
                0,
                true,
                true,
                true,
                false,
                false,
                List.of(new ResearchBenchPreview.IngredientPreview(
                        List.of(new ResourceLocation("minecraft:paper")),
                        Optional.empty(),
                        1,
                        0)),
                ResearchBenchPreview.RecyclingPreview.EMPTY));
    }
}
