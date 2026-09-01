package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Correlated client-only selection and research feedback with bounded pending lifetime. */
final class ResearchTreeFeedbackState {
    private Snapshot snapshot = Snapshot.IDLE;

    Snapshot snapshot() {
        return snapshot;
    }

    void pending(ResourceLocation blueprintId) {
        pending(blueprintId, 1, 0L);
    }

    void pending(ResourceLocation blueprintId, int requestId, long nowMillis) {
        snapshot = new Snapshot(
                Status.PENDING, requiredId(blueprintId), Optional.empty(), requestId, nowMillis);
    }

    void succeeded(ResourceLocation blueprintId) {
        snapshot = new Snapshot(
                Status.SUCCESS, requiredId(blueprintId), Optional.empty(), 1, 0L);
    }

    boolean succeeded(ResourceLocation blueprintId, int requestId, long nowMillis) {
        if (!acceptsResult(blueprintId, requestId)) {
            return false;
        }
        snapshot = new Snapshot(
                Status.SUCCESS, requiredId(blueprintId), Optional.empty(), requestId, nowMillis);
        return true;
    }

    void failed(ResourceLocation blueprintId, String resultKey) {
        if (resultKey == null || resultKey.isBlank()) {
            throw new IllegalArgumentException("Research Tree feedback result cannot be blank");
        }
        snapshot = new Snapshot(
                Status.FAILURE,
                requiredId(blueprintId),
                Optional.of(resultKey),
                1,
                0L);
    }

    boolean failed(
            ResourceLocation blueprintId,
            int requestId,
            String resultKey,
            long nowMillis) {
        if (!acceptsResult(blueprintId, requestId)) {
            return false;
        }
        if (resultKey == null || resultKey.isBlank()) {
            throw new IllegalArgumentException("Research Tree feedback result cannot be blank");
        }
        snapshot = new Snapshot(
                Status.FAILURE,
                requiredId(blueprintId),
                Optional.of(resultKey),
                requestId,
                nowMillis);
        return true;
    }

    boolean matchesPending(ResourceLocation blueprintId, int requestId) {
        return snapshot.status() == Status.PENDING
                && snapshot.requestId() == requestId
                && snapshot.blueprintId().filter(blueprintId::equals).isPresent();
    }

    boolean acceptsResult(ResourceLocation blueprintId, int requestId) {
        return snapshot.requestId() == requestId
                && snapshot.blueprintId().filter(blueprintId::equals).isPresent()
                && (snapshot.status() == Status.PENDING
                        || snapshot.status() == Status.FAILURE
                                && snapshot.resultKey()
                                        .filter(key -> key.endsWith("_timeout"))
                                        .isPresent());
    }

    boolean pendingFor(ResourceLocation blueprintId) {
        return snapshot.status() == Status.PENDING
                && snapshot.blueprintId().filter(blueprintId::equals).isPresent();
    }

    boolean pending() {
        return snapshot.status() == Status.PENDING;
    }

    boolean expirePending(long nowMillis, long timeoutMillis, String resultKey) {
        if (timeoutMillis < 1L || snapshot.status() != Status.PENDING
                || nowMillis - snapshot.updatedAtMillis() < timeoutMillis) {
            return false;
        }
        failed(
                snapshot.blueprintId().orElseThrow(),
                snapshot.requestId(),
                resultKey,
                nowMillis);
        return true;
    }

    void clear() {
        snapshot = Snapshot.IDLE;
    }

    private static Optional<ResourceLocation> requiredId(ResourceLocation blueprintId) {
        if (blueprintId == null) {
            throw new IllegalArgumentException("Research Tree feedback node cannot be null");
        }
        return Optional.of(blueprintId);
    }

    enum Status {
        IDLE,
        PENDING,
        SUCCESS,
        FAILURE
    }

    record Snapshot(
            Status status,
            Optional<ResourceLocation> blueprintId,
            Optional<String> resultKey,
            int requestId,
            long updatedAtMillis) {
        private static final Snapshot IDLE =
                new Snapshot(Status.IDLE, Optional.empty(), Optional.empty(), 0, 0L);

        Snapshot {
            if (status == null || blueprintId == null || resultKey == null
                    || requestId < 0 || updatedAtMillis < 0L
                    || status == Status.IDLE
                            && (blueprintId.isPresent() || resultKey.isPresent()
                                    || requestId != 0 || updatedAtMillis != 0L)
                    || status != Status.IDLE && blueprintId.isEmpty()
                    || status != Status.IDLE && requestId == 0
                    || status != Status.FAILURE && resultKey.isPresent()
                    || status == Status.FAILURE && resultKey.isEmpty()) {
                throw new IllegalArgumentException("invalid Research Tree feedback snapshot");
            }
        }
    }
}
