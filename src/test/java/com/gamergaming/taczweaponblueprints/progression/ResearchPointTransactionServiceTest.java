package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ClaimKey;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.Mutation;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointTransactionService.OverflowPolicy;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointTransactionService.Status;

import net.minecraft.resources.ResourceLocation;

class ResearchPointTransactionServiceTest {
    @Test
    void evaluationIsPureAndFullCreditCommitsExactlyOnce() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(7);

        var evaluation = ResearchPointTransactionService.evaluate(
                data, 3, 10, OverflowPolicy.REQUIRE_FULL);
        assertEquals(Status.AWARDED, evaluation.status());
        assertEquals(3, evaluation.awardedPoints());
        assertEquals(7, evaluation.previousBalance());
        assertEquals(10, evaluation.projectedBalance());
        assertEquals(7, data.getResearchPoints());

        var result = ResearchPointTransactionService.credit(
                data, 3, 10, OverflowPolicy.REQUIRE_FULL);
        assertTrue(result.successful());
        assertEquals(Status.AWARDED, result.status());
        assertEquals(3, result.awardedPoints());
        assertEquals(10, result.newBalance());
        assertEquals(10, data.getResearchPoints());
    }

    @Test
    void clampCanPartiallyCreditButRequireFullPreservesTheBalance() {
        PlayerRecipeData clamped = new PlayerRecipeData();
        clamped.setResearchPoints(8);
        var partial = ResearchPointTransactionService.credit(
                clamped, 5, 10, OverflowPolicy.CLAMP);
        assertEquals(Status.PARTIALLY_AWARDED, partial.status());
        assertEquals(2, partial.awardedPoints());
        assertEquals(10, partial.newBalance());

        PlayerRecipeData required = new PlayerRecipeData();
        required.setResearchPoints(8);
        var rejected = ResearchPointTransactionService.credit(
                required, 5, 10, OverflowPolicy.REQUIRE_FULL);
        assertEquals(Status.POINT_CAP_REACHED, rejected.status());
        assertFalse(rejected.successful());
        assertEquals(0, rejected.awardedPoints());
        assertEquals(8, rejected.newBalance());
        assertEquals(8, required.getResearchPoints());
    }

    @Test
    void fullClampCanExplicitlyRecordFiniteHistoryWithoutAwardingPoints() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(10);
        ClaimKey claim = ClaimKey.once(id("test:full_balance_milestone"));

        var recorded = ResearchPointTransactionService.credit(
                data,
                4,
                10,
                OverflowPolicy.CLAMP,
                Mutation.claim(claim));

        assertTrue(recorded.successful());
        assertEquals(Status.LEDGER_RECORDED_AT_CAP, recorded.status());
        assertEquals(0, recorded.awardedPoints());
        assertEquals(10, recorded.newBalance());
        assertTrue(data.getResearchPointAwardLedger().hasClaim(claim));

        var duplicate = ResearchPointTransactionService.credit(
                data,
                4,
                10,
                OverflowPolicy.CLAMP,
                Mutation.claim(claim));
        assertEquals(Status.COMMIT_REJECTED, duplicate.status());
        assertFalse(duplicate.successful());
        assertEquals(10, data.getResearchPoints());

        PlayerRecipeData aboveLoweredCap = new PlayerRecipeData();
        aboveLoweredCap.setResearchPoints(12);
        ClaimKey loweredCapClaim = ClaimKey.once(id("test:lowered_cap_milestone"));
        var loweredCap = ResearchPointTransactionService.credit(
                aboveLoweredCap,
                1,
                10,
                OverflowPolicy.CLAMP,
                Mutation.claim(loweredCapClaim));
        assertEquals(Status.LEDGER_RECORDED_AT_CAP, loweredCap.status());
        assertEquals(12, loweredCap.newBalance());
        assertTrue(aboveLoweredCap.getResearchPointAwardLedger().hasClaim(loweredCapClaim));
    }

    @Test
    void fullClampWithoutHistoryAndRequireFullNeverAdvanceTheLedger() {
        PlayerRecipeData clamped = new PlayerRecipeData();
        clamped.setResearchPoints(10);
        assertEquals(Status.POINT_CAP_REACHED, ResearchPointTransactionService.credit(
                clamped, 1, 10, OverflowPolicy.CLAMP).status());
        assertTrue(clamped.getResearchPointAwardLedger().isEmpty());

        PlayerRecipeData required = new PlayerRecipeData();
        required.setResearchPoints(10);
        ClaimKey rejectedClaim = ClaimKey.once(id("test:require_full_rejected"));
        assertEquals(Status.POINT_CAP_REACHED, ResearchPointTransactionService.credit(
                required,
                1,
                10,
                OverflowPolicy.REQUIRE_FULL,
                Mutation.claim(rejectedClaim)).status());
        assertFalse(required.getResearchPointAwardLedger().hasClaim(rejectedClaim));
    }

    @Test
    void validatesDataAmountCapAndLoweredCapWithoutMutation() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(20);

        assertEquals(Status.DATA_UNAVAILABLE, ResearchPointTransactionService.credit(
                null, 1, 100, OverflowPolicy.REQUIRE_FULL).status());
        assertEquals(Status.INVALID_AMOUNT, ResearchPointTransactionService.credit(
                data, 0, 100, OverflowPolicy.REQUIRE_FULL).status());
        assertEquals(Status.INVALID_AMOUNT, ResearchPointTransactionService.credit(
                data, -1, 100, OverflowPolicy.REQUIRE_FULL).status());
        assertEquals(Status.INVALID_AMOUNT, ResearchPointTransactionService.credit(
                data,
                PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1,
                PlayerProgressionLimits.MAX_RESEARCH_POINTS,
                OverflowPolicy.REQUIRE_FULL).status());
        assertEquals(Status.INVALID_CAP, ResearchPointTransactionService.credit(
                data,
                1,
                PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1,
                OverflowPolicy.REQUIRE_FULL).status());
        assertEquals(Status.POINT_CAP_REACHED, ResearchPointTransactionService.credit(
                data, 1, 10, OverflowPolicy.CLAMP).status());
        PlayerRecipeData invalidBalance = new PlayerRecipeData() {
            @Override
            public int getResearchPoints() {
                return -1;
            }
        };
        assertEquals(Status.INVALID_BALANCE, ResearchPointTransactionService.credit(
                invalidBalance, 1, 10, OverflowPolicy.CLAMP).status());
        assertEquals(20, data.getResearchPoints());
        assertThrows(NullPointerException.class,
                () -> ResearchPointTransactionService.credit(data, 1, 100, null));
    }

    @Test
    void pointAndLedgerMutationCommitTogetherAndDuplicateClaimFailsClosed() {
        PlayerRecipeData data = new PlayerRecipeData();
        ClaimKey claim = ClaimKey.once(id("test:first_award"));
        Mutation mutation = Mutation.claim(claim);

        var first = ResearchPointTransactionService.credit(
                data, 4, 10, OverflowPolicy.REQUIRE_FULL, mutation);
        assertTrue(first.successful());
        assertEquals(4, data.getResearchPoints());
        assertTrue(data.getResearchPointAwardLedger().hasClaim(claim));

        var duplicate = ResearchPointTransactionService.credit(
                data, 4, 10, OverflowPolicy.REQUIRE_FULL, mutation);
        assertEquals(Status.COMMIT_REJECTED, duplicate.status());
        assertEquals(0, duplicate.awardedPoints());
        assertEquals(4, duplicate.newBalance());
        assertEquals(4, data.getResearchPoints());
        assertEquals(1, data.getResearchPointAwardLedger().claimCount());
    }

    @Test
    void ledgerCapacityRejectionCannotCreditPointsOrEvictClaims() {
        PlayerRecipeData data = new PlayerRecipeData();
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_CLAIMS;
                index++) {
            assertTrue(data.getResearchPointAwardLedger().apply(
                    Mutation.claim(ClaimKey.once(id("test:claim_" + index)))));
        }
        ClaimKey overflow = ClaimKey.once(id("test:overflow"));

        var rejected = ResearchPointTransactionService.credit(
                data,
                2,
                100,
                OverflowPolicy.REQUIRE_FULL,
                Mutation.claim(overflow));

        assertEquals(Status.COMMIT_REJECTED, rejected.status());
        assertEquals(0, data.getResearchPoints());
        assertFalse(data.getResearchPointAwardLedger().hasClaim(overflow));
        assertEquals(PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_CLAIMS,
                data.getResearchPointAwardLedger().claimCount());
    }

    @Test
    void immutableResultShapesRejectContradictoryState() {
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchPointTransactionService.Result(
                        Status.AWARDED, OverflowPolicy.REQUIRE_FULL,
                        2, 1, 0, 1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchPointTransactionService.Result(
                        Status.POINT_CAP_REACHED, OverflowPolicy.REQUIRE_FULL,
                        2, 1, 0, 1, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchPointTransactionService.Evaluation(
                        Status.LEDGER_RECORDED_AT_CAP, OverflowPolicy.CLAMP,
                        2, 0, 10, 10, 10));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchPointTransactionService.Result(
                        Status.LEDGER_RECORDED_AT_CAP, OverflowPolicy.REQUIRE_FULL,
                        2, 0, 10, 10, 10));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
