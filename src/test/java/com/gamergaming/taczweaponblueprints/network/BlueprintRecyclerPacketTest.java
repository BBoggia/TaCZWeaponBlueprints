package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;
import com.gamergaming.taczweaponblueprints.progression.BlueprintRecyclingService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryService;
import com.gamergaming.taczweaponblueprints.progression.ResearchDataRedemptionService;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentAnalysisService;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

class BlueprintRecyclerPacketTest {
    private static final ResourceLocation BLUEPRINT = new ResourceLocation("test:rifle");
    private static final ResourceLocation RESEARCH_NOTE =
            new ResourceLocation("taczweaponblueprints:research_note");

    @Test
    void everyActionRoundTripsWithItsExpectedPhysicalInput() {
        for (BlueprintRecyclerActionContract.Action action
                : BlueprintRecyclerActionContract.Action.values()) {
            BlueprintRecyclerActionPacket packet =
                    new BlueprintRecyclerActionPacket(7, 42, action, BLUEPRINT, 3, 77L);
            FriendlyByteBuf buffer = buffer();
            try {
                packet.toBytes(buffer);
                BlueprintRecyclerActionPacket decoded =
                        new BlueprintRecyclerActionPacket(buffer);
                assertEquals(7, decoded.containerId());
                assertEquals(42, decoded.requestId());
                assertEquals(action, decoded.action());
                assertEquals(BLUEPRINT, decoded.expectedInputId());
                assertEquals(3, decoded.expectedInputCount());
                assertEquals(77L, decoded.expectedStateToken());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void everyAnalyzerPreviewRoundTripsWithoutClientInference() {
        BlueprintRecyclerPreview[] previews = {
                new BlueprintRecyclerPreview(
                        BlueprintRecyclerPreview.InputKind.BLUEPRINT,
                        Optional.of(BLUEPRINT),
                        1,
                        3,
                        4,
                        20,
                        Optional.of(BlueprintRecyclingService.Status.SUCCESS),
                        Optional.empty()),
                new BlueprintRecyclerPreview(
                        BlueprintRecyclerPreview.InputKind.RESEARCH_DATA,
                        Optional.of(RESEARCH_NOTE),
                        6,
                        2,
                        4,
                        20,
                        Optional.empty(),
                        Optional.of(ResearchDataRedemptionService.Status.SUCCESS)),
                new BlueprintRecyclerPreview(
                        BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM,
                        Optional.of(BLUEPRINT),
                        2,
                        0,
                        9,
                        20,
                        Optional.empty(),
                        Optional.empty(),
                        91L,
                        Optional.of(BLUEPRINT),
                        1,
                        4,
                        true,
                        true,
                        true,
                        true,
                        Optional.of(BlueprintReverseEngineeringService.Status.READY),
                        List.of(new BlueprintRecyclerPreview.IngredientPreview(
                                List.of(new ResourceLocation("minecraft:iron_ingot")),
                                Optional.empty(),
                                2,
                                3)),
                        BlueprintRecyclerPreview.WeaponOrigin.LOOT_GENERATED,
                        3,
                        Optional.of(FoundWeaponRecoveryService.Status.READY)),
                BlueprintRecyclerPreview.fragment(
                        new BlueprintFragmentAnalysisService.Evaluation(
                                BlueprintFragmentAnalysisService.Status.READY,
                                Optional.of(BLUEPRINT),
                                BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                                3,
                                3,
                                0,
                                4,
                                7,
                                7,
                                5,
                                0,
                                1,
                                false,
                                false,
                                0,
                                4,
                                20,
                                false,
                                9L))
        };

        for (BlueprintRecyclerPreview preview : previews) {
            SyncBlueprintRecyclerPreviewPacket packet =
                    new SyncBlueprintRecyclerPreviewPacket(9, preview);
            FriendlyByteBuf buffer = buffer();
            try {
                packet.toBytes(buffer);
                SyncBlueprintRecyclerPreviewPacket decoded =
                        new SyncBlueprintRecyclerPreviewPacket(buffer);
                assertEquals(9, decoded.containerId());
                assertEquals(preview, decoded.preview());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void correlatedResultRoundTrips() {
        BlueprintRecyclerActionContract.ActionResult result =
                new BlueprintRecyclerActionContract.ActionResult(
                        BlueprintRecyclerActionContract.Action.RECYCLE,
                        Optional.of(BLUEPRINT),
                        BlueprintRecyclerActionContract.ResultCode.DUPLICATE_REQUIRED);
        BlueprintRecyclerActionResultPacket packet =
                new BlueprintRecyclerActionResultPacket(5, 88, result);
        FriendlyByteBuf buffer = buffer();
        try {
            packet.toBytes(buffer);
            BlueprintRecyclerActionResultPacket decoded =
                    new BlueprintRecyclerActionResultPacket(buffer);
            assertEquals(5, decoded.containerId());
            assertEquals(88, decoded.requestId());
            assertEquals(result, decoded.result());
        } finally {
            buffer.release();
        }
    }

    @Test
    void codecsRejectInvalidCorrelationAndEnumOrdinals() {
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintRecyclerActionPacket(
                        1,
                        0,
                        BlueprintRecyclerActionContract.Action.RECYCLE,
                        BLUEPRINT,
                        1));
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintRecyclerActionPacket(
                        1,
                        1,
                        BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,
                        BLUEPRINT,
                        1,
                        0L));

        FriendlyByteBuf action = buffer();
        try {
            action.writeVarInt(1);
            action.writeVarInt(1);
            action.writeVarInt(BlueprintRecyclerActionContract.Action.values().length);
            assertThrows(IllegalArgumentException.class, () ->
                    new BlueprintRecyclerActionPacket(action));
        } finally {
            action.release();
        }

        FriendlyByteBuf preview = buffer();
        try {
            preview.writeVarInt(1);
            preview.writeVarInt(BlueprintRecyclerPreview.InputKind.values().length);
            assertThrows(IllegalArgumentException.class, () ->
                    new SyncBlueprintRecyclerPreviewPacket(preview));
        } finally {
            preview.release();
        }
    }

    @Test
    void actionRejectsOversizedPhysicalInputIdsBeforeSending() {
        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintRecyclerActionPacket(
                        1,
                        1,
                        BlueprintRecyclerActionContract.Action.RECYCLE,
                        new ResourceLocation("test", "x".repeat(252)),
                        1));

        assertThrows(IllegalArgumentException.class, () ->
                new BlueprintRecyclerActionPacket(
                        1,
                        1,
                        BlueprintRecyclerActionContract.Action.REDEEM_STACK,
                        RESEARCH_NOTE,
                        65));
    }

    private static FriendlyByteBuf buffer() {
        return new FriendlyByteBuf(Unpooled.buffer());
    }
}
