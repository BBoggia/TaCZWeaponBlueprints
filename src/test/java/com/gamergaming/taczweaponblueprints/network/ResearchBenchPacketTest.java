package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchResearchAction;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionProgressionPreview;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFingerprint;
import com.gamergaming.taczweaponblueprints.progression.DisclosedCraftingAccess;
import com.gamergaming.taczweaponblueprints.progression.eligibility.ResearchAccessSummary;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingDisposition;

import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

class ResearchBenchPacketTest {
    private static final ResourceLocation BLUEPRINT = new ResourceLocation("test:rifle");

    @Test
    void actionRoundTripsOnlyContainerActionAndBoundedBlueprintId() {
        ResearchRouteFingerprint fingerprint = new ResearchRouteFingerprint(12L, 34L);
        ResearchBenchActionPacket packet = new ResearchBenchActionPacket(
                17,
                42,
                ResearchBenchResearchAction.RESEARCH,
                Optional.of(BLUEPRINT),
                Optional.of(fingerprint));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            ResearchBenchActionPacket decoded = new ResearchBenchActionPacket(buffer);
            assertEquals(17, decoded.containerId());
            assertEquals(42, decoded.requestId());
            assertEquals(ResearchBenchResearchAction.RESEARCH, decoded.action());
            assertEquals(Optional.of(BLUEPRINT), decoded.blueprintId());
            assertEquals(Optional.of(fingerprint), decoded.routeFingerprint());
        } finally {
            buffer.release();
        }
    }

    @Test
    void everyResearchBenchActionRoundTrips() {
        for (ResearchBenchResearchAction action : ResearchBenchResearchAction.values()) {
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
            ordinal.writeVarInt(0);
            ordinal.writeVarInt(ResearchBenchResearchAction.values().length);
            ordinal.writeBoolean(false);
            assertThrows(IllegalArgumentException.class, () -> new ResearchBenchActionPacket(ordinal));
        } finally {
            ordinal.release();
        }

        assertThrows(IllegalArgumentException.class, () -> new ResearchBenchActionPacket(
                1,
                ResearchBenchResearchAction.SELECT,
                Optional.of(new ResourceLocation("test", "x".repeat(252)))));
        assertThrows(IllegalArgumentException.class, () -> new ResearchBenchActionPacket(
                1,
                1,
                ResearchBenchResearchAction.SELECT,
                Optional.of(BLUEPRINT),
                Optional.of(new ResearchRouteFingerprint(1L, 2L))));
    }

    @Test
    void correlatedActionResultsRoundTripWithoutFreeFormClientMessages() {
        ResearchBenchMenu.ActionResult result = new ResearchBenchMenu.ActionResult(
                ResearchBenchResearchAction.RESEARCH,
                Optional.of(BLUEPRINT),
                ResearchBenchMenu.ActionResultCode.INGREDIENTS_REQUIRED);
        ResearchBenchActionResultPacket packet =
                new ResearchBenchActionResultPacket(7, 93, result);
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            packet.toBytes(buffer);
            ResearchBenchActionResultPacket decoded =
                    new ResearchBenchActionResultPacket(buffer);
            assertEquals(7, decoded.containerId());
            assertEquals(93, decoded.requestId());
            assertEquals(result, decoded.result());
        } finally {
            buffer.release();
        }
    }

    @Test
    void boundedPathPlanningFailuresRoundTripAsAppendOnlyActionResults() {
        for (ResearchBenchMenu.ActionResultCode code : List.of(
                ResearchBenchMenu.ActionResultCode.PATH_TOO_LARGE,
                ResearchBenchMenu.ActionResultCode.ROUTE_TOO_COMPLEX,
                ResearchBenchMenu.ActionResultCode.TECH_TREE_UNAVAILABLE,
                ResearchBenchMenu.ActionResultCode.UNSATISFIABLE,
                ResearchBenchMenu.ActionResultCode.STALE_PREVIEW,
                ResearchBenchMenu.ActionResultCode.WORKBENCH_TIER_REQUIRED,
                ResearchBenchMenu.ActionResultCode.PROGRESSION_GATE_REQUIRED)) {
            ResearchBenchMenu.ActionResult result = new ResearchBenchMenu.ActionResult(
                    ResearchBenchResearchAction.RESEARCH,
                    Optional.of(BLUEPRINT),
                    code);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new ResearchBenchActionResultPacket(7, 94, result).toBytes(buffer);
                assertEquals(result, new ResearchBenchActionResultPacket(buffer).result());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void exactTierAndGateBlockersRoundTripWithoutHiddenPolicyDetails() {
        for (ResearchAccessSummary access : List.of(
                ResearchAccessSummary.workbench(
                        ResearchWorkbenchTier.TIER_1,
                        ResearchWorkbenchTier.TIER_3),
                ResearchAccessSummary.gate("gate.example.complete_trial"),
                ResearchAccessSummary.POLICY_UNAVAILABLE)) {
            ResearchSelectionPreview preview = new ResearchSelectionPreview(
                    Optional.of(BLUEPRINT),
                    42,
                    80,
                    false,
                    true,
                    true,
                    false,
                    false,
                    List.of(),
                    2,
                    0,
                    ResearchSelectionPreview.PathPlanningState.NONE,
                    ResearchCostMode.POINTS_AND_ITEMS,
                    Optional.empty(),
                    access);
            FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new SyncResearchBenchPreviewPacket(9, preview).toBytes(buffer);
                assertEquals(
                        preview,
                        new SyncResearchBenchPreviewPacket(buffer).preview());
            } finally {
                buffer.release();
            }
        }
    }

    @Test
    void actionResultsRejectInvalidCorrelationAndSemanticCombinations() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchBenchActionResultPacket(
                        1,
                        0,
                        new ResearchBenchMenu.ActionResult(
                                ResearchBenchResearchAction.SELECT,
                                Optional.of(BLUEPRINT),
                                ResearchBenchMenu.ActionResultCode.ACCEPTED)));
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchBenchMenu.ActionResult(
                        ResearchBenchResearchAction.SELECT,
                        Optional.of(BLUEPRINT),
                        ResearchBenchMenu.ActionResultCode.SUCCESS));
        assertEquals(
                ResearchBenchMenu.ActionResultCode.REQUEST_THROTTLED,
                new ResearchBenchMenu.ActionResult(
                        ResearchBenchResearchAction.SELECT,
                        Optional.of(BLUEPRINT),
                        ResearchBenchMenu.ActionResultCode.REQUEST_THROTTLED).code());
    }

    @Test
    void exactIngredientPreviewRoundTripsWithStrictBounds() {
        ResearchSelectionPreview preview = new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                8,
                12,
                true,
                false,
                true,
                false,
                false,
                List.of(
                        new ResearchSelectionPreview.IngredientPreview(
                                List.of(new ResourceLocation("minecraft:paper")),
                                Optional.empty(),
                                4,
                                4),
                        new ResearchSelectionPreview.IngredientPreview(
                                List.of(new ResourceLocation("minecraft:iron_ingot")),
                                Optional.of(new ResourceLocation("forge:ingots/iron")),
                                2,
                                1)));
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
    void pathPreviewRoundTripsAggregateCountsAndTruncatedMaterials() {
        ResearchRouteFingerprint fingerprint = new ResearchRouteFingerprint(56L, 78L);
        ResearchSelectionPreview preview = new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                42,
                80,
                true,
                false,
                true,
                false,
                false,
                List.of(new ResearchSelectionPreview.IngredientPreview(
                        List.of(new ResourceLocation("minecraft:paper")),
                        Optional.empty(),
                        12,
                        12)),
                5,
                3,
                ResearchSelectionPreview.PathPlanningState.NONE,
                ResearchCostMode.POINTS_AND_ITEMS,
                Optional.of(fingerprint),
                ResearchAccessSummary.NONE,
                new ResearchSelectionProgressionPreview(
                        Optional.of(ResearchWorkbenchTier.TIER_2),
                        Optional.of(ResearchWorkbenchTier.TIER_3),
                        Optional.of(new ResearchSelectionProgressionPreview.FragmentProgress(
                                5,
                                5,
                                BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                                true)),
                        Optional.of(new DisclosedCraftingAccess(
                                BlueprintCraftingDisposition.TIERED,
                                Optional.of(ResearchWorkbenchTier.TIER_3)))));
        FriendlyByteBuf buffer = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SyncResearchBenchPreviewPacket(9, preview).toBytes(buffer);
            ResearchSelectionPreview decoded =
                    new SyncResearchBenchPreviewPacket(buffer).preview();
            assertEquals(preview, decoded);
            assertEquals(5, decoded.unlockCount());
            assertEquals(2, decoded.additionalIngredientTypes());
            assertEquals(Optional.of(fingerprint), decoded.routeFingerprint());
            assertEquals(ResearchWorkbenchTier.TIER_3,
                    decoded.progression().requiredTier().orElseThrow());
            assertTrue(decoded.progression().fragments().orElseThrow().discountApplied());
            assertEquals(
                    ResearchWorkbenchTier.TIER_3,
                    decoded.progression().craftingAccess().orElseThrow()
                            .requiredWorkbenchTier().orElseThrow());
        } finally {
            buffer.release();
        }
    }

    @Test
    void effectiveResearchCostModeRoundTripsAndRejectsUnknownValues() {
        ResearchSelectionPreview preview = new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                0,
                80,
                true,
                true,
                true,
                true,
                false,
                List.of(new ResearchSelectionPreview.IngredientPreview(
                        List.of(new ResourceLocation("minecraft:paper")),
                        Optional.empty(),
                        3,
                        3)),
                1,
                1,
                ResearchSelectionPreview.PathPlanningState.NONE,
                ResearchCostMode.ITEMS_ONLY);
        FriendlyByteBuf roundTrip = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SyncResearchBenchPreviewPacket(9, preview).toBytes(roundTrip);
            assertEquals(preview, new SyncResearchBenchPreviewPacket(roundTrip).preview());
        } finally {
            roundTrip.release();
        }

        FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SyncResearchBenchPreviewPacket(9, preview).toBytes(invalid);
            // The empty route, access summary, progression tier/fragment fields,
            // and disclosed crafting-access marker occupy nine trailing bytes.
            invalid.setByte(invalid.writerIndex() - 10, ResearchCostMode.values().length);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SyncResearchBenchPreviewPacket(invalid));
        } finally {
            invalid.release();
        }
    }

    @Test
    void boundedPathPlanningStateRoundTripsAndRejectsUnknownOrdinals() {
        for (ResearchSelectionPreview.PathPlanningState state : List.of(
                ResearchSelectionPreview.PathPlanningState.PATH_TOO_LARGE,
                ResearchSelectionPreview.PathPlanningState.ROUTE_TOO_COMPLEX,
                ResearchSelectionPreview.PathPlanningState.TECH_TREE_UNAVAILABLE,
                ResearchSelectionPreview.PathPlanningState.UNSATISFIABLE)) {
            ResearchSelectionPreview preview = new ResearchSelectionPreview(
                    Optional.of(BLUEPRINT),
                    0,
                    80,
                    false,
                    true,
                    true,
                    false,
                    false,
                    List.of(),
                    1,
                    0,
                    state);
            FriendlyByteBuf roundTrip = new FriendlyByteBuf(Unpooled.buffer());
            try {
                new SyncResearchBenchPreviewPacket(9, preview).toBytes(roundTrip);
                assertEquals(preview, new SyncResearchBenchPreviewPacket(roundTrip).preview());
            } finally {
                roundTrip.release();
            }
        }

        FriendlyByteBuf invalid = new FriendlyByteBuf(Unpooled.buffer());
        try {
            invalid.writeVarInt(1);
            invalid.writeBoolean(false);
            invalid.writeVarInt(0);
            invalid.writeVarInt(0);
            invalid.writeBoolean(false);
            invalid.writeBoolean(false);
            invalid.writeBoolean(false);
            invalid.writeBoolean(false);
            invalid.writeBoolean(false);
            invalid.writeVarInt(0);
            invalid.writeVarInt(0);
            invalid.writeVarInt(ResearchSelectionPreview.PathPlanningState.values().length);
            assertThrows(
                    IllegalArgumentException.class,
                    () -> new SyncResearchBenchPreviewPacket(invalid));
        } finally {
            invalid.release();
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
            buffer.writeVarInt(0);
            buffer.writeVarInt(0);
            buffer.writeVarInt(ResearchSelectionPreview.PathPlanningState.NONE.ordinal());
            buffer.writeVarInt(7);
            assertThrows(IllegalArgumentException.class, () -> new SyncResearchBenchPreviewPacket(buffer));
        } finally {
            buffer.release();
        }
    }

    @Test
    void previewRejectsOverstatedOrMisleadingMaterialSummaries() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchSelectionPreview.IngredientPreview(
                List.of(new ResourceLocation("minecraft:paper")),
                Optional.empty(),
                4,
                5));

        assertThrows(IllegalArgumentException.class, () -> new ResearchSelectionPreview(
                Optional.of(BLUEPRINT),
                0,
                0,
                true,
                true,
                true,
                false,
                false,
                List.of(new ResearchSelectionPreview.IngredientPreview(
                        List.of(new ResourceLocation("minecraft:paper")),
                        Optional.empty(),
                        1,
                        0))));
    }
}
