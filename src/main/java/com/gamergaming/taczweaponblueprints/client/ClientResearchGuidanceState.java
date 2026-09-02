package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

/** Correlates one authoritative guidance request with one research publication. */
public final class ClientResearchGuidanceState {
    static final int MAX_TIMEOUT_RETRIES = 1;
    private static int nextRequestId = 1;
    private static Pending pending;
    private static TimedOutRetry timedOutRetry;
    private static Accepted accepted;
    private static boolean resourcesCurrent;
    private static Rejected rejected;

    private ClientResearchGuidanceState() {
    }

    public static synchronized Optional<Request> begin(
            ResearchTreeGraph graph,
            ResourceLocation targetId,
            long publicationGeneration) {
        return begin(graph, targetId, publicationGeneration, Util.getMillis());
    }

    static synchronized Optional<Request> begin(
            ResearchTreeGraph graph,
            ResourceLocation targetId,
            long publicationGeneration,
            long nowMillis) {
        if (!publicTarget(graph, targetId) || publicationGeneration == Long.MIN_VALUE) {
            return Optional.empty();
        }
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("research guidance request time is invalid");
        }
        if (rejected != null
                && rejected.publicationGeneration() == publicationGeneration
                && rejected.targetId().equals(targetId)) {
            return Optional.empty();
        }
        if (accepted != null
                && accepted.publicationGeneration() == publicationGeneration
                && accepted.snapshot().targetId().equals(targetId)
                && resourcesCurrent) {
            return Optional.empty();
        }
        if (pending != null
                && pending.request().publicationGeneration() == publicationGeneration
                && pending.request().targetId().equals(targetId)) {
            return Optional.empty();
        }
        int requestId = nextRequestId;
        nextRequestId = requestId == Integer.MAX_VALUE ? 1 : requestId + 1;
        Request request = new Request(requestId, publicationGeneration, targetId);
        int timeoutRetries = timedOutRetry != null
                        && timedOutRetry.publicationGeneration() == publicationGeneration
                        && timedOutRetry.targetId().equals(targetId)
                ? timedOutRetry.timeoutRetries()
                : 0;
        pending = new Pending(request, nowMillis, timeoutRetries);
        timedOutRetry = null;
        if (accepted != null
                && (accepted.publicationGeneration() != publicationGeneration
                        || !accepted.snapshot().targetId().equals(targetId))) {
            accepted = null;
        }
        resourcesCurrent = false;
        rejected = null;
        return Optional.of(request);
    }

    public static synchronized boolean accept(
            int requestId,
            long publicationGeneration,
            ResearchGuidanceSnapshot snapshot,
            ClientResearchState.Publication publication) {
        if (pending == null
                || pending.request().requestId() != requestId
                || pending.request().publicationGeneration() != publicationGeneration) {
            return false;
        }
        if (publication != null
                && publication.generation() != publicationGeneration) {
            pending = null;
            retain(publication.graph(), publication.generation());
            resourcesCurrent = false;
            rejected = null;
            return false;
        }
        if (snapshot == null || publication == null) {
            pending = null;
            resourcesCurrent = false;
            rejected = null;
            return false;
        }
        if (!pending.request().targetId().equals(snapshot.targetId())
                || ClientResearchPlannerState.targetId()
                        .filter(snapshot.targetId()::equals).isEmpty()
                || !validAgainst(snapshot, publication.graph())) {
            ResourceLocation targetId = pending.request().targetId();
            pending = null;
            rejected = new Rejected(publicationGeneration, targetId);
            resourcesCurrent = false;
            return false;
        }
        accepted = new Accepted(publicationGeneration, snapshot);
        pending = null;
        timedOutRetry = null;
        resourcesCurrent = true;
        rejected = null;
        return true;
    }

    public static synchronized boolean reject(
            int requestId,
            long publicationGeneration,
            boolean terminal) {
        if (pending == null
                || pending.request().requestId() != requestId
                || pending.request().publicationGeneration() != publicationGeneration) {
            return false;
        }
        ResourceLocation targetId = pending.request().targetId();
        pending = null;
        timedOutRetry = null;
        resourcesCurrent = false;
        rejected = terminal ? new Rejected(publicationGeneration, targetId) : null;
        return true;
    }

    /** Lets a later Bench retry a request whose response was ignored after close. */
    public static synchronized void abandonPending() {
        pending = null;
        timedOutRetry = null;
    }

    /** Expires one lost response, permits one retry, then fails closed. */
    public static synchronized TimeoutOutcome expirePending(
            long nowMillis,
            long timeoutMillis) {
        if (nowMillis < 0L || timeoutMillis <= 0L) {
            throw new IllegalArgumentException("research guidance timeout is invalid");
        }
        if (pending == null
                || nowMillis < pending.startedAtMillis()
                || nowMillis - pending.startedAtMillis() < timeoutMillis) {
            return TimeoutOutcome.NONE;
        }
        Pending expired = pending;
        pending = null;
        resourcesCurrent = false;
        int nextRetry = expired.timeoutRetries() + 1;
        if (nextRetry <= MAX_TIMEOUT_RETRIES) {
            timedOutRetry = new TimedOutRetry(
                    expired.request().publicationGeneration(),
                    expired.request().targetId(),
                    nextRetry);
            rejected = null;
            return TimeoutOutcome.RETRY;
        }
        timedOutRetry = null;
        rejected = new Rejected(
                expired.request().publicationGeneration(),
                expired.request().targetId());
        return TimeoutOutcome.UNAVAILABLE;
    }

    public static synchronized boolean unavailable() {
        return rejected != null;
    }

    public static synchronized Optional<ResearchGuidanceSnapshot> snapshot() {
        return accepted == null ? Optional.empty() : Optional.of(accepted.snapshot());
    }

    /** Returns guidance only when its live RP and inventory allocation are current. */
    public static synchronized Optional<ResearchGuidanceSnapshot> currentSnapshot() {
        return resourcesCurrent ? snapshot() : Optional.empty();
    }

    /** Invalidates live economy data while retaining the exact structural route. */
    public static synchronized void invalidateResources() {
        pending = null;
        timedOutRetry = null;
        resourcesCurrent = false;
        rejected = null;
    }

    public static synchronized boolean pending() {
        return pending != null;
    }

    public static synchronized void retain(
            ResearchTreeGraph graph,
            long publicationGeneration) {
        if (graph == null) {
            throw new IllegalArgumentException("research guidance graph cannot be null");
        }
        if (pending != null && (pending.request().publicationGeneration()
                        != publicationGeneration
                || !publicTarget(graph, pending.request().targetId()))) {
            pending = null;
        }
        if (timedOutRetry != null
                && (timedOutRetry.publicationGeneration() != publicationGeneration
                        || !publicTarget(graph, timedOutRetry.targetId()))) {
            timedOutRetry = null;
        }
        if (rejected != null && (rejected.publicationGeneration() != publicationGeneration
                || !publicTarget(graph, rejected.targetId()))) {
            rejected = null;
        }
        if (accepted != null && (accepted.publicationGeneration() != publicationGeneration
                || !validAgainst(accepted.snapshot(), graph))) {
            accepted = null;
            resourcesCurrent = false;
        }
    }

    public static synchronized void clear() {
        pending = null;
        timedOutRetry = null;
        accepted = null;
        resourcesCurrent = false;
        rejected = null;
        nextRequestId = 1;
    }

    private static boolean validAgainst(
            ResearchGuidanceSnapshot snapshot,
            ResearchTreeGraph graph) {
        if (!publicTarget(graph, snapshot.targetId())
                || snapshot.supportIds().stream().anyMatch(id -> !publicTarget(graph, id))) {
            return false;
        }
        if (!snapshot.routeAvailable()) {
            return snapshot.supportIds().equals(List.of(snapshot.targetId()));
        }
        java.util.Set<ResourceLocation> purchases = java.util.Set.copyOf(
                snapshot.purchaseIds());
        if (snapshot.supportIds().stream().anyMatch(id -> graph.node(id).orElseThrow().learned()
                        == purchases.contains(id))) {
            return false;
        }
        for (var selected : snapshot.selectedRequirements()) {
            Optional<ResearchTreeGraph.RequirementGroup> group =
                    graph.requirementGroupsOf(selected.dependentId()).stream()
                            .filter(candidate -> candidate.ordinal()
                                    == selected.groupOrdinal())
                            .findFirst();
            if (group.isEmpty()
                    || !group.orElseThrow().visibleAlternativeIds()
                            .contains(selected.prerequisiteId())) {
                return false;
            }
        }
        return true;
    }

    private static boolean publicTarget(
            ResearchTreeGraph graph,
            ResourceLocation targetId) {
        return graph != null && targetId != null && graph.node(targetId)
                .filter(node -> node.visibility().revealsIdentity()).isPresent();
    }

    public record Request(
            int requestId,
            long publicationGeneration,
            ResourceLocation targetId) {
        public Request {
            if (requestId < 1 || publicationGeneration == Long.MIN_VALUE || targetId == null) {
                throw new IllegalArgumentException("research guidance request is invalid");
            }
        }
    }

    public enum TimeoutOutcome {
        NONE,
        RETRY,
        UNAVAILABLE
    }

    private record Pending(Request request, long startedAtMillis, int timeoutRetries) {
        private Pending {
            if (request == null || startedAtMillis < 0L
                    || timeoutRetries < 0 || timeoutRetries > MAX_TIMEOUT_RETRIES) {
                throw new IllegalArgumentException("pending research guidance is invalid");
            }
        }
    }

    private record TimedOutRetry(
            long publicationGeneration,
            ResourceLocation targetId,
            int timeoutRetries) {
        private TimedOutRetry {
            if (publicationGeneration == Long.MIN_VALUE || targetId == null
                    || timeoutRetries < 1 || timeoutRetries > MAX_TIMEOUT_RETRIES) {
                throw new IllegalArgumentException("research guidance retry is invalid");
            }
        }
    }

    private record Accepted(
            long publicationGeneration,
            ResearchGuidanceSnapshot snapshot) {
    }

    private record Rejected(long publicationGeneration, ResourceLocation targetId) {
    }
}
