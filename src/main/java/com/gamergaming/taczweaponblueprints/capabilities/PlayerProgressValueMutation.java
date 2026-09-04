package com.gamergaming.taczweaponblueprints.capabilities;

/**
 * Compare-and-set boundary for one bounded fragment or criterion progress
 * value. The same transition can be preflighted, committed, and reversed
 * without granting a rollback permission to unrelated state.
 */
public final class PlayerProgressValueMutation {
    private PlayerProgressValueMutation() {
    }

    public enum Operation {
        PREFLIGHT,
        COMMIT,
        ROLLBACK
    }

    public enum Status {
        READY,
        APPLIED,
        ROLLED_BACK,
        UNCHANGED,
        STALE,
        INVALID_IDENTITY,
        CAPACITY_REACHED,
        UNSUPPORTED
    }

    public record Request(
            Operation operation,
            String progressId,
            int expectedValue,
            int resultingValue) {
        public Request {
            if (operation == null) {
                throw new IllegalArgumentException("progress mutation operation cannot be null");
            }
            if (expectedValue < 0
                    || expectedValue > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || resultingValue < 0
                    || resultingValue > PlayerProgressionLimits.MAX_PROGRESS_VALUE) {
                throw new IllegalArgumentException("progress mutation value is out of bounds");
            }
        }

        public static Request preflight(String progressId, int expectedValue, int resultingValue) {
            return new Request(Operation.PREFLIGHT, progressId, expectedValue, resultingValue);
        }

        public static Request commit(String progressId, int expectedValue, int resultingValue) {
            return new Request(Operation.COMMIT, progressId, expectedValue, resultingValue);
        }

        public static Request rollback(String progressId, int committedValue, int previousValue) {
            return new Request(Operation.ROLLBACK, progressId, committedValue, previousValue);
        }
    }

    public record Result(
            Status status,
            Operation operation,
            int previousValue,
            int resultingValue) {
        public Result {
            if (status == null || operation == null
                    || previousValue < 0
                    || previousValue > PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || resultingValue < 0
                    || resultingValue > PlayerProgressionLimits.MAX_PROGRESS_VALUE) {
                throw new IllegalArgumentException("progress mutation result is invalid");
            }
            if (status == Status.READY && operation != Operation.PREFLIGHT) {
                throw new IllegalArgumentException("only a preflight mutation may be ready");
            }
            if (status == Status.APPLIED && operation != Operation.COMMIT) {
                throw new IllegalArgumentException("only a commit mutation may be applied");
            }
            if (status == Status.ROLLED_BACK && operation != Operation.ROLLBACK) {
                throw new IllegalArgumentException("only a rollback mutation may be rolled back");
            }
            if ((status == Status.APPLIED || status == Status.ROLLED_BACK)
                    && previousValue == resultingValue) {
                throw new IllegalArgumentException("changed progress result contains no transition");
            }
            if (status == Status.READY && previousValue == resultingValue) {
                throw new IllegalArgumentException("ready progress result contains no transition");
            }
            if (status == Status.UNCHANGED && previousValue != resultingValue) {
                throw new IllegalArgumentException("unchanged progress result contains a transition");
            }
        }

        public static Result ready(Request request, int previousValue) {
            return new Result(Status.READY, request.operation(), previousValue, request.resultingValue());
        }

        public static Result changed(Request request, int previousValue) {
            Status status = request.operation() == Operation.ROLLBACK
                    ? Status.ROLLED_BACK
                    : Status.APPLIED;
            return new Result(status, request.operation(), previousValue, request.resultingValue());
        }

        public static Result unchanged(Request request, int value) {
            return new Result(Status.UNCHANGED, request.operation(), value, value);
        }

        public static Result rejected(Status status, Request request, int actualValue) {
            if (status == Status.READY || status == Status.APPLIED
                    || status == Status.ROLLED_BACK || status == Status.UNCHANGED) {
                throw new IllegalArgumentException("successful status cannot reject a mutation");
            }
            return new Result(status, request.operation(), actualValue, actualValue);
        }

        public boolean successful() {
            return status == Status.READY
                    || status == Status.APPLIED
                    || status == Status.ROLLED_BACK
                    || status == Status.UNCHANGED;
        }

        public boolean changed() {
            return status == Status.APPLIED || status == Status.ROLLED_BACK;
        }
    }
}
