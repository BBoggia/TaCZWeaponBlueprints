package com.gamergaming.taczweaponblueprints.progression;

import java.util.Objects;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger;

/** Pure bounded accounting for one server-authoritative RP credit. */
public final class ResearchPointTransactionService {
    private ResearchPointTransactionService() {
    }

    public static Evaluation evaluate(
            IPlayerRecipeData playerData,
            int requestedPoints,
            int pointCap,
            OverflowPolicy overflowPolicy) {
        Objects.requireNonNull(overflowPolicy, "RP overflow policy cannot be null");
        int currentBalance = boundedBalance(playerData == null ? 0 : playerData.getResearchPoints());
        if (playerData == null) {
            return Evaluation.failure(
                    Status.DATA_UNAVAILABLE, overflowPolicy, requestedPoints, currentBalance, pointCap);
        }
        if (requestedPoints <= 0
                || requestedPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            return Evaluation.failure(
                    Status.INVALID_AMOUNT, overflowPolicy, requestedPoints, currentBalance, pointCap);
        }
        if (pointCap < 0 || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            return Evaluation.failure(
                    Status.INVALID_CAP, overflowPolicy, requestedPoints, currentBalance, pointCap);
        }
        if (playerData.getResearchPoints() < 0
                || playerData.getResearchPoints() > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            return Evaluation.failure(
                    Status.INVALID_BALANCE, overflowPolicy, requestedPoints, currentBalance, pointCap);
        }

        int remaining = pointCap - playerData.getResearchPoints();
        if (remaining <= 0) {
            return Evaluation.failure(
                    Status.POINT_CAP_REACHED,
                    overflowPolicy,
                    requestedPoints,
                    currentBalance,
                    pointCap);
        }
        if (overflowPolicy == OverflowPolicy.REQUIRE_FULL && requestedPoints > remaining) {
            return Evaluation.failure(
                    Status.POINT_CAP_REACHED,
                    overflowPolicy,
                    requestedPoints,
                    currentBalance,
                    pointCap);
        }

        int awarded = Math.min(requestedPoints, remaining);
        Status status = awarded == requestedPoints ? Status.AWARDED : Status.PARTIALLY_AWARDED;
        return new Evaluation(
                status,
                overflowPolicy,
                requestedPoints,
                awarded,
                currentBalance,
                currentBalance + awarded,
                pointCap);
    }

    public static Result credit(
            IPlayerRecipeData playerData,
            int requestedPoints,
            int pointCap,
            OverflowPolicy overflowPolicy) {
        return credit(
                playerData,
                requestedPoints,
                pointCap,
                overflowPolicy,
                ResearchPointAwardLedger.Mutation.empty());
    }

    /**
     * Credits the evaluated amount and applies the supplied protective ledger
     * mutation through one capability operation.
     */
    public static Result credit(
            IPlayerRecipeData playerData,
            int requestedPoints,
            int pointCap,
            OverflowPolicy overflowPolicy,
            ResearchPointAwardLedger.Mutation ledgerMutation) {
        Evaluation evaluation = evaluate(playerData, requestedPoints, pointCap, overflowPolicy);
        if (!evaluation.successful()) {
            if (evaluation.status() == Status.POINT_CAP_REACHED
                    && evaluation.overflowPolicy() == OverflowPolicy.CLAMP
                    && ledgerMutation != null
                    && !ledgerMutation.isEmpty()) {
                return playerData.applyResearchPointTransaction(0, pointCap, ledgerMutation)
                        ? Result.ledgerOnly(evaluation)
                        : Result.failure(Status.COMMIT_REJECTED, evaluation);
            }
            return Result.from(evaluation);
        }
        if (ledgerMutation == null
                || !playerData.applyResearchPointTransaction(
                        evaluation.awardedPoints(), pointCap, ledgerMutation)) {
            return Result.failure(Status.COMMIT_REJECTED, evaluation);
        }
        return new Result(
                evaluation.status(),
                evaluation.overflowPolicy(),
                evaluation.requestedPoints(),
                evaluation.awardedPoints(),
                evaluation.previousBalance(),
                playerData.getResearchPoints(),
                evaluation.pointCap());
    }

    private static int boundedBalance(int balance) {
        return Math.max(0, Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, balance));
    }

    private static int boundedRequestedPoints(int points) {
        return Math.max(0, Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, points));
    }

    private static int boundedPointCap(int pointCap) {
        return Math.max(0, Math.min(PlayerProgressionLimits.MAX_RESEARCH_POINTS, pointCap));
    }

    public enum OverflowPolicy {
        CLAMP,
        REQUIRE_FULL
    }

    public enum Status {
        AWARDED,
        PARTIALLY_AWARDED,
        LEDGER_RECORDED_AT_CAP,
        POINT_CAP_REACHED,
        DATA_UNAVAILABLE,
        INVALID_AMOUNT,
        INVALID_CAP,
        INVALID_BALANCE,
        COMMIT_REJECTED;

        private boolean awardsPoints() {
            return this == AWARDED || this == PARTIALLY_AWARDED;
        }

        private boolean committed() {
            return awardsPoints() || this == LEDGER_RECORDED_AT_CAP;
        }
    }

    /** Immutable non-mutating decision for a proposed credit. */
    public record Evaluation(
            Status status,
            OverflowPolicy overflowPolicy,
            int requestedPoints,
            int awardedPoints,
            int previousBalance,
            int projectedBalance,
            int pointCap) {
        public Evaluation {
            validate(status, overflowPolicy, requestedPoints, awardedPoints,
                    previousBalance, projectedBalance, pointCap, "evaluation");
        }

        public boolean successful() {
            return status.awardsPoints();
        }

        private static Evaluation failure(
                Status status,
                OverflowPolicy overflowPolicy,
                int requestedPoints,
                int currentBalance,
                int pointCap) {
            int boundedBalance = boundedBalance(currentBalance);
            return new Evaluation(
                    status,
                    overflowPolicy,
                    boundedRequestedPoints(requestedPoints),
                    0,
                    boundedBalance,
                    boundedBalance,
                    boundedPointCap(pointCap));
        }
    }

    /** Immutable result after the capability commit was attempted. */
    public record Result(
            Status status,
            OverflowPolicy overflowPolicy,
            int requestedPoints,
            int awardedPoints,
            int previousBalance,
            int newBalance,
            int pointCap) {
        public Result {
            validate(status, overflowPolicy, requestedPoints, awardedPoints,
                    previousBalance, newBalance, pointCap, "result");
        }

        public boolean successful() {
            return status.committed();
        }

        private static Result from(Evaluation evaluation) {
            return new Result(
                    evaluation.status(),
                    evaluation.overflowPolicy(),
                    evaluation.requestedPoints(),
                    0,
                    evaluation.previousBalance(),
                    evaluation.previousBalance(),
                    evaluation.pointCap());
        }

        private static Result failure(Status status, Evaluation evaluation) {
            return new Result(
                    status,
                    evaluation.overflowPolicy(),
                    evaluation.requestedPoints(),
                    0,
                    evaluation.previousBalance(),
                    evaluation.previousBalance(),
                    evaluation.pointCap());
        }

        private static Result ledgerOnly(Evaluation evaluation) {
            return new Result(
                    Status.LEDGER_RECORDED_AT_CAP,
                    evaluation.overflowPolicy(),
                    evaluation.requestedPoints(),
                    0,
                    evaluation.previousBalance(),
                    evaluation.previousBalance(),
                    evaluation.pointCap());
        }
    }

    private static void validate(
            Status status,
            OverflowPolicy overflowPolicy,
            int requestedPoints,
            int awardedPoints,
            int previousBalance,
            int resultingBalance,
            int pointCap,
            String description) {
        if (status == null || overflowPolicy == null
                || requestedPoints < 0
                || requestedPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || awardedPoints < 0
                || awardedPoints > requestedPoints
                || previousBalance < 0
                || previousBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || resultingBalance < 0
                || resultingBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointCap < 0
                || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("invalid Research Point transaction " + description);
        }
        if (status.awardsPoints()) {
            if (requestedPoints <= 0 || awardedPoints <= 0
                    || resultingBalance != previousBalance + awardedPoints
                    || resultingBalance > pointCap
                    || (status == Status.AWARDED && awardedPoints != requestedPoints)
                    || (status == Status.PARTIALLY_AWARDED && awardedPoints >= requestedPoints)) {
                throw new IllegalArgumentException("invalid successful Research Point transaction " + description);
            }
        } else if (status == Status.LEDGER_RECORDED_AT_CAP) {
            if (!"result".equals(description)
                    || overflowPolicy != OverflowPolicy.CLAMP
                    || requestedPoints <= 0
                    || awardedPoints != 0
                    || resultingBalance != previousBalance
                    || previousBalance < pointCap) {
                throw new IllegalArgumentException(
                        "invalid ledger-only Research Point transaction " + description);
            }
        } else if (awardedPoints != 0 || resultingBalance != previousBalance) {
            throw new IllegalArgumentException("failed Research Point transaction changed state");
        }
    }
}
