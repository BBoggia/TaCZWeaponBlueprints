package com.gamergaming.taczweaponblueprints.capabilities;

/** Typed request and result boundary for one atomic blueprint-learning change. */
public final class BlueprintLearningMutation {
    private BlueprintLearningMutation() {
    }

    public enum Operation {
        PREFLIGHT,
        COMMIT
    }

    public enum Status {
        READY,
        APPLIED,
        ALREADY_LEARNED,
        INVALID_IDENTITY,
        CAPACITY_REACHED
    }

    public record Request(
            Operation operation,
            String blueprintId,
            String legacyRecipeId) {
        public Request {
            if (operation == null) {
                throw new IllegalArgumentException(
                        "blueprint learning operation cannot be null");
            }
        }

        public static Request preflight(String blueprintId, String legacyRecipeId) {
            return new Request(Operation.PREFLIGHT, blueprintId, legacyRecipeId);
        }

        public static Request commit(String blueprintId, String legacyRecipeId) {
            return new Request(Operation.COMMIT, blueprintId, legacyRecipeId);
        }
    }

    /**
     * Transition flags describe prospective changes for {@link Status#READY}
     * and committed changes for {@link Status#APPLIED}.
     */
    public record Result(
            Status status,
            Operation operation,
            boolean learnedChanged,
            boolean discoveredChanged,
            boolean legacyRecipeChanged) {
        public Result {
            if (status == null || operation == null) {
                throw new IllegalArgumentException(
                        "blueprint learning result contains null required state");
            }
            boolean changed = learnedChanged
                    || discoveredChanged
                    || legacyRecipeChanged;
            if ((status == Status.READY || status == Status.APPLIED) != changed) {
                throw new IllegalArgumentException(
                        "blueprint learning result has inconsistent transitions");
            }
            if (status == Status.READY && operation != Operation.PREFLIGHT) {
                throw new IllegalArgumentException(
                        "only a preflight operation may be ready");
            }
            if (status == Status.APPLIED && operation != Operation.COMMIT) {
                throw new IllegalArgumentException(
                        "only a commit operation may apply changes");
            }
        }

        public static Result ready(
                boolean learnedChanged,
                boolean discoveredChanged,
                boolean legacyRecipeChanged) {
            return new Result(
                    Status.READY,
                    Operation.PREFLIGHT,
                    learnedChanged,
                    discoveredChanged,
                    legacyRecipeChanged);
        }

        public static Result applied(
                boolean learnedChanged,
                boolean discoveredChanged,
                boolean legacyRecipeChanged) {
            return new Result(
                    Status.APPLIED,
                    Operation.COMMIT,
                    learnedChanged,
                    discoveredChanged,
                    legacyRecipeChanged);
        }

        public static Result unchanged(Status status, Operation operation) {
            if (status == Status.READY || status == Status.APPLIED) {
                throw new IllegalArgumentException(
                        "a changed status cannot be unchanged");
            }
            return new Result(status, operation, false, false, false);
        }

        public boolean ready() {
            return status == Status.READY;
        }

        public boolean committed() {
            return status == Status.APPLIED;
        }
    }
}
