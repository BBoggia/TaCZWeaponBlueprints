package com.gamergaming.taczweaponblueprints.network;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.resource.research.AnalyzerPolicyQuote;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;

class AnalyzerPresentationPacketTest {
    private static final ResourceLocation ID = new ResourceLocation("test:rifle");
    private static BlueprintRecyclerPreview physical() {
        return new BlueprintRecyclerPreview(BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM, Optional.of(ID), 1, 0, 10, 100,
                Optional.empty(), Optional.empty(), 1L, Optional.of(ID), 1, 3, true, true, false, false,
                Optional.of(BlueprintReverseEngineeringService.Status.READY), List.of());
    }
    @Test void allTierPairsRoundTripWeightsProgressAndIndependentRecoveryCosts() {
        for (int station=1; station<=3; station++) for (int weapon=1; weapon<=3; weapon++) {
            var details = new AnalyzerPresentation(station, Optional.of(new AnalyzerPresentation.Weapon(weapon,
                    AnalyzerPolicyQuote.Status.CONFIGURED, Optional.of(new AnalyzerOutcomeTable(1,1_000_000_000,1_000_000_000,1,64)),
                    Optional.of(AnalysisRewardClass.PROTECTED_PREREQUISITES), 9, 11, 5, 10)), Optional.empty(),
                    Optional.of(new AnalyzerPresentation.RecoveryCost(2, 7, List.of(new BlueprintRecyclerPreview.IngredientPreview(
                            List.of(new ResourceLocation("minecraft:iron_ingot")), Optional.empty(), 3, 2)), BlueprintReverseEngineeringService.Status.INGREDIENTS_REQUIRED)));
            var p=physical().withPresentation(details).withStateToken(89);
            var b=new FriendlyByteBuf(Unpooled.buffer());
            try { new SyncBlueprintRecyclerPreviewPacket(7,p).toBytes(b); assertEquals(p,new SyncBlueprintRecyclerPreviewPacket(b).preview()); assertEquals(0,b.readableBytes()); }
            finally { b.release(); }
        }
    }
    @Test void everyCommittedOutcomeAndFragmentResultRoundTrips() {
        for (var o : List.of(new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.BLUEPRINT,1),
                new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.FRAGMENTS,64), new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.NO_RESULT,0))) {
            roundTrip(new BlueprintRecyclerActionContract.ActionResult(BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER, Optional.of(ID), BlueprintRecyclerActionContract.ResultCode.SUCCESS,Optional.of(o)));
        }
        roundTrip(new BlueprintRecyclerActionContract.ActionResult(BlueprintRecyclerActionContract.Action.ARCHIVE_FRAGMENTS, Optional.of(ID), BlueprintRecyclerActionContract.ResultCode.SUCCESS,
                Optional.empty(),Optional.of(new BlueprintRecyclerActionContract.FragmentResult(0,3,5,true))));
    }
    private void roundTrip(BlueprintRecyclerActionContract.ActionResult result) {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try { new BlueprintRecyclerActionResultPacket(7,9,result).toBytes(b); assertEquals(result,new BlueprintRecyclerActionResultPacket(b).result()); assertEquals(0,b.readableBytes()); }
        finally { b.release(); }
    }
    @Test void resultStateTokensRoundTripAndRejectNegativeValues() {
        var result = new BlueprintRecyclerActionContract.ActionResult(BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,
                Optional.of(ID), BlueprintRecyclerActionContract.ResultCode.STALE_INPUT);
        roundTrip(result.withStateToken(1));
        roundTrip(result.withStateToken(Long.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> result.withStateToken(-1));
    }
    @Test void fullBlueprintWithoutLearningIsExplicitButNoResultAndUnavailablePoliciesAreNotMislabelled() {
        var full = new AnalyzerOutcomeTable(1, 0, 0, 0, 0);
        var disabled = new AnalyzerPresentation.Weapon(1, AnalyzerPolicyQuote.Status.CONFIGURED,
                Optional.of(full), Optional.empty(), 0, 0, 0, 0);
        assertTrue(disabled.blueprintLearningDisabled());
        assertFalse(new AnalyzerPresentation.Weapon(1, AnalyzerPolicyQuote.Status.CONFIGURED,
                Optional.of(full), Optional.of(AnalysisRewardClass.PROTECTED_PREREQUISITES), 0, 0, 0, 0).blueprintLearningDisabled());
        assertFalse(new AnalyzerPresentation.Weapon(1, AnalyzerPolicyQuote.Status.CONFIGURED,
                Optional.of(new AnalyzerOutcomeTable(0, 0, 1, 0, 0)), Optional.empty(), 0, 0, 0, 0).blueprintLearningDisabled());
        assertFalse(new AnalyzerPresentation.Weapon(0, AnalyzerPolicyQuote.Status.TIER_UNAVAILABLE,
                Optional.empty(), Optional.empty(), 0, 0, 0, 0).blueprintLearningDisabled());
        var b = new FriendlyByteBuf(Unpooled.buffer());
        try {
            new SyncBlueprintRecyclerPreviewPacket(7, physical().withPresentation(new AnalyzerPresentation(
                    1, Optional.of(disabled), Optional.empty(), Optional.empty()))).toBytes(b);
            assertTrue(new SyncBlueprintRecyclerPreviewPacket(b).preview().presentation().weapon().orElseThrow().blueprintLearningDisabled());
        } finally { b.release(); }
    }
    @Test void rejectsImpossiblePresentationAndFailureOutcome() {
        assertThrows(IllegalArgumentException.class,()-> new AnalyzerPresentation(4,Optional.empty(),Optional.empty(),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()-> new AnalyzerPresentation.Weapon(0,AnalyzerPolicyQuote.Status.CONFIGURED,Optional.empty(),Optional.empty(),0,0,0,0));
        assertThrows(IllegalArgumentException.class,()-> new AnalyzerPresentation.Fragments(AnalysisRewardClass.PROTECTED_PREREQUISITES,Optional.empty(),-1,10));
        assertThrows(IllegalArgumentException.class,()-> BlueprintRecyclerPreview.EMPTY.withPresentation(new AnalyzerPresentation(1,Optional.of(
                new AnalyzerPresentation.Weapon(1,AnalyzerPolicyQuote.Status.DISABLED,Optional.empty(),Optional.empty(),0,0,0,0)),Optional.empty(),Optional.empty())));
        assertThrows(IllegalArgumentException.class,()-> new BlueprintRecyclerActionContract.ActionResult(BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,Optional.of(ID),
                BlueprintRecyclerActionContract.ResultCode.TRANSACTION_FAILED,Optional.of(new AnalyzerOutcomeTable.Result(AnalyzerOutcomeTable.Kind.BLUEPRINT,1))));
    }
    @Test void rejectsUnboundedRecoveryListBeforeAllocationAndUnknownOutcome() {
        var b=new FriendlyByteBuf(Unpooled.buffer());
        try {
            b.writeVarInt(1); b.writeBoolean(false); b.writeBoolean(false); b.writeBoolean(true);
            b.writeVarInt(1); b.writeVarInt(0); b.writeVarInt(0); b.writeVarInt(Integer.MAX_VALUE);
            assertThrows(IllegalArgumentException.class,()->AnalyzerPresentationCodec.read(b));
            b.clear();b.writeBoolean(true);b.writeVarInt(99);b.writeVarInt(0);
            assertThrows(IllegalArgumentException.class,()->AnalyzerPresentationCodec.outcome(b));
        } finally { b.release(); }
    }
}
