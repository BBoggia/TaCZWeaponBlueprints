package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Pure one-shot hold gesture used only for an already-ready fullscreen selection. */
final class ResearchTreeHoldActivationController {
    private Snapshot snapshot = Snapshot.IDLE;

    void begin(ResourceLocation blueprintId, long nowMillis, int durationMillis) {
        if (blueprintId == null || nowMillis < 0L || durationMillis < 1) {
            throw new IllegalArgumentException("invalid Research Tree hold activation");
        }
        snapshot = new Snapshot(
                Status.HOLDING,
                Optional.of(blueprintId),
                nowMillis,
                durationMillis);
    }

    Outcome advance(long nowMillis) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("Research Tree hold time cannot be negative");
        }
        if (snapshot.status() != Status.HOLDING
                || nowMillis - snapshot.startedAtMillis() < snapshot.durationMillis()) {
            return Outcome.NONE;
        }
        snapshot = new Snapshot(
                Status.ACTIVATED,
                snapshot.blueprintId(),
                snapshot.startedAtMillis(),
                snapshot.durationMillis());
        return Outcome.ACTIVATE;
    }

    boolean release() {
        boolean activated = snapshot.status() == Status.ACTIVATED;
        snapshot = Snapshot.IDLE;
        return activated;
    }

    void cancel() {
        snapshot = Snapshot.IDLE;
    }

    Snapshot snapshot(long nowMillis) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("Research Tree hold time cannot be negative");
        }
        return snapshot.withProgress(nowMillis);
    }

    enum Status {
        IDLE,
        HOLDING,
        ACTIVATED
    }

    enum Outcome {
        NONE,
        ACTIVATE
    }

    record Snapshot(
            Status status,
            Optional<ResourceLocation> blueprintId,
            long startedAtMillis,
            int durationMillis,
            double progress) {
        private static final Snapshot IDLE = new Snapshot(
                Status.IDLE, Optional.empty(), 0L, 0, 0.0D);

        private Snapshot(
                Status status,
                Optional<ResourceLocation> blueprintId,
                long startedAtMillis,
                int durationMillis) {
            this(status, blueprintId, startedAtMillis, durationMillis, 0.0D);
        }

        Snapshot {
            blueprintId = blueprintId == null ? Optional.empty() : blueprintId;
            if (status == null || startedAtMillis < 0L || durationMillis < 0
                    || !Double.isFinite(progress) || progress < 0.0D || progress > 1.0D
                    || status == Status.IDLE
                            && (blueprintId.isPresent() || startedAtMillis != 0L
                                    || durationMillis != 0 || progress != 0.0D)
                    || status != Status.IDLE
                            && (blueprintId.isEmpty() || durationMillis < 1)) {
                throw new IllegalArgumentException("invalid Research Tree hold snapshot");
            }
        }

        private Snapshot withProgress(long nowMillis) {
            if (status == Status.IDLE) {
                return this;
            }
            double nextProgress = status == Status.ACTIVATED
                    ? 1.0D
                    : Math.max(0.0D, Math.min(
                            1.0D,
                            (double) (nowMillis - startedAtMillis) / durationMillis));
            return new Snapshot(
                    status, blueprintId, startedAtMillis, durationMillis, nextProgress);
        }
    }
}
