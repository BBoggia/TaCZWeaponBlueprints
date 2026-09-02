package com.gamergaming.taczweaponblueprints.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.ResearchAffordabilitySnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;

/** Correlates one progressive, connection-local Affordable Now sweep. */
public final class ClientResearchAffordabilityState {
    static final int MAX_BATCH_FAILURES = 3;
    static final int MAX_TIMEOUT_RETRIES = 1;
    private static boolean enabled;
    private static long publicationGeneration = Long.MIN_VALUE;
    /** Identity of the immutable publication used to derive {@link #targets}. */
    private static ResearchTreeGraph publicationGraph;
    private static List<ResourceLocation> targets = List.of();
    private static int nextTargetIndex;
    private static int nextRequestId = 1;
    private static Pending pending;
    private static Map<ResourceLocation, ResearchAffordabilitySnapshot.Entry> results = Map.of();
    private static int batchFailures;
    private static int timeoutRetries;
    private static Snapshot cachedSnapshot = new Snapshot(
            false, Long.MIN_VALUE, Map.of(), 0, 0, false);

    private ClientResearchAffordabilityState() {
    }

    public static synchronized void setEnabled(
            boolean nextEnabled,
            ResearchTreeGraph graph,
            long generation) {
        if (!nextEnabled) {
            enabled = false;
            resetSweep(null, Long.MIN_VALUE, List.of());
            return;
        }
        boolean wasEnabled = enabled;
        enabled = true;
        if (wasEnabled && publicationGraph == graph && publicationGeneration == generation) {
            return;
        }
        List<ResourceLocation> nextTargets = publicTargets(graph);
        if (!wasEnabled
                || publicationGeneration != generation
                || !targets.equals(nextTargets)) {
            resetSweep(graph, generation, nextTargets);
        } else {
            publicationGraph = graph;
        }
    }

    public static synchronized void retain(
            ResearchTreeGraph graph,
            long generation) {
        if (!enabled) {
            return;
        }
        if (publicationGraph == graph && publicationGeneration == generation) {
            return;
        }
        List<ResourceLocation> nextTargets = publicTargets(graph);
        if (publicationGeneration != generation || !targets.equals(nextTargets)) {
            resetSweep(graph, generation, nextTargets);
        } else {
            publicationGraph = graph;
        }
    }

    /** Restarts live resource classification without changing the filter toggle. */
    public static synchronized void invalidateResources(
            ResearchTreeGraph graph,
            long generation) {
        if (enabled) {
            List<ResourceLocation> nextTargets = publicationGraph == graph
                            && publicationGeneration == generation
                    ? targets
                    : publicTargets(graph);
            resetSweep(graph, generation, nextTargets);
        }
    }

    public static synchronized Optional<Request> beginNext(
            ResearchTreeGraph graph,
            long generation) {
        return beginNext(graph, generation, Util.getMillis());
    }

    static synchronized Optional<Request> beginNext(
            ResearchTreeGraph graph,
            long generation,
            long nowMillis) {
        retain(graph, generation);
        if (nowMillis < 0L) {
            throw new IllegalArgumentException("research affordability request time is invalid");
        }
        if (!enabled || pending != null || nextTargetIndex >= targets.size()) {
            return Optional.empty();
        }
        int toIndex = Math.min(
                targets.size(),
                nextTargetIndex + ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH);
        List<ResourceLocation> batch = List.copyOf(targets.subList(nextTargetIndex, toIndex));
        int requestId = nextRequestId;
        nextRequestId = requestId == Integer.MAX_VALUE ? 1 : requestId + 1;
        Request request = new Request(requestId, generation, batch);
        pending = new Pending(request, nowMillis, timeoutRetries);
        refreshSnapshot();
        return Optional.of(request);
    }

    public static synchronized ResponseOutcome accept(
            int requestId,
            long generation,
            ResearchAffordabilitySnapshot snapshot,
            ClientResearchState.Publication publication) {
        if (!matchesPending(requestId, generation)) {
            return ResponseOutcome.IGNORED;
        }
        if (snapshot == null || publication == null) {
            return failPendingBatch();
        }
        if (publication.generation() != generation) {
            retain(publication.graph(), publication.generation());
            return ResponseOutcome.IGNORED;
        }
        List<ResourceLocation> responseTargets = snapshot.entries().stream()
                .map(ResearchAffordabilitySnapshot.Entry::targetId)
                .toList();
        if (!pending.request().targetIds().equals(responseTargets)
                || snapshot.entries().stream().anyMatch(entry -> publication.graph()
                        .node(entry.targetId())
                        .filter(node -> node.visibility().revealsIdentity())
                        .isEmpty())) {
            return failPendingBatch();
        }
        LinkedHashMap<ResourceLocation, ResearchAffordabilitySnapshot.Entry> accepted =
                new LinkedHashMap<>(results);
        snapshot.entries().forEach(entry -> accepted.put(entry.targetId(), entry));
        results = Collections.unmodifiableMap(accepted);
        nextTargetIndex = Math.addExact(nextTargetIndex, snapshot.entries().size());
        pending = null;
        batchFailures = 0;
        timeoutRetries = 0;
        refreshSnapshot();
        return ResponseOutcome.ACCEPTED;
    }

    /** Renews the lease for a server-accepted batch without advancing it. */
    public static synchronized ResponseOutcome acknowledge(
            int requestId,
            long generation,
            long nowMillis) {
        if (nowMillis < 0L) {
            throw new IllegalArgumentException(
                    "research affordability acknowledgement time is invalid");
        }
        if (!matchesPending(requestId, generation)) {
            return ResponseOutcome.IGNORED;
        }
        pending = new Pending(pending.request(), nowMillis, pending.timeoutRetries());
        return ResponseOutcome.ACKNOWLEDGED;
    }

    public static synchronized ResponseOutcome reject(int requestId, long generation) {
        if (!matchesPending(requestId, generation)) {
            return ResponseOutcome.IGNORED;
        }
        timeoutRetries = 0;
        return failPendingBatch();
    }

    public static synchronized ResponseOutcome throttle(int requestId, long generation) {
        if (!matchesPending(requestId, generation)) {
            return ResponseOutcome.IGNORED;
        }
        timeoutRetries = pending.timeoutRetries();
        pending = null;
        refreshSnapshot();
        return ResponseOutcome.RETRY;
    }

    /** Expires one lost batch response, retries once, then advances safely. */
    public static synchronized ResponseOutcome expirePending(
            long nowMillis,
            long timeoutMillis) {
        if (nowMillis < 0L || timeoutMillis <= 0L) {
            throw new IllegalArgumentException("research affordability timeout is invalid");
        }
        if (pending == null
                || nowMillis < pending.startedAtMillis()
                || nowMillis - pending.startedAtMillis() < timeoutMillis) {
            return ResponseOutcome.IGNORED;
        }
        List<ResourceLocation> failedTargets = pending.request().targetIds();
        int nextRetry = pending.timeoutRetries() + 1;
        pending = null;
        if (nextRetry <= MAX_TIMEOUT_RETRIES) {
            timeoutRetries = nextRetry;
            refreshSnapshot();
            return ResponseOutcome.RETRY;
        }
        timeoutRetries = 0;
        advanceFailedTargets(failedTargets);
        return ResponseOutcome.ADVANCED_AFTER_FAILURE;
    }

    /** Lets a later Bench retry an unfinished batch after this screen closes. */
    public static synchronized void abandonPending() {
        pending = null;
        timeoutRetries = 0;
        refreshSnapshot();
    }

    public static synchronized Snapshot snapshot() {
        return cachedSnapshot;
    }

    public static synchronized void clear() {
        enabled = false;
        nextRequestId = 1;
        resetSweep(null, Long.MIN_VALUE, List.of());
    }

    private static void resetSweep(
            ResearchTreeGraph graph,
            long generation,
            List<ResourceLocation> nextTargets) {
        publicationGraph = graph;
        publicationGeneration = generation;
        targets = List.copyOf(nextTargets);
        nextTargetIndex = 0;
        pending = null;
        results = Map.of();
        batchFailures = 0;
        timeoutRetries = 0;
        refreshSnapshot();
    }

    private static ResponseOutcome failPendingBatch() {
        List<ResourceLocation> failedTargets = pending.request().targetIds();
        pending = null;
        timeoutRetries = 0;
        batchFailures++;
        if (batchFailures < MAX_BATCH_FAILURES) {
            refreshSnapshot();
            return ResponseOutcome.RETRY;
        }
        advanceFailedTargets(failedTargets);
        return ResponseOutcome.ADVANCED_AFTER_FAILURE;
    }

    private static void advanceFailedTargets(List<ResourceLocation> failedTargets) {
        LinkedHashMap<ResourceLocation, ResearchAffordabilitySnapshot.Entry> accepted =
                new LinkedHashMap<>(results);
        failedTargets.forEach(targetId -> accepted.put(
                targetId,
                new ResearchAffordabilitySnapshot.Entry(
                        targetId, ResearchGuidanceSnapshot.State.ROUTE_UNAVAILABLE, true)));
        results = Collections.unmodifiableMap(accepted);
        nextTargetIndex = Math.addExact(nextTargetIndex, failedTargets.size());
        batchFailures = 0;
        refreshSnapshot();
    }

    private static void refreshSnapshot() {
        cachedSnapshot = new Snapshot(
                enabled,
                publicationGeneration,
                results,
                nextTargetIndex,
                targets.size(),
                pending != null);
    }

    private static boolean matchesPending(int requestId, long generation) {
        return enabled
                && pending != null
                && pending.request().requestId() == requestId
                && pending.request().publicationGeneration() == generation;
    }

    private static List<ResourceLocation> publicTargets(ResearchTreeGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("research affordability graph cannot be null");
        }
        return graph.nodes().stream()
                .filter(node -> node.visibility().revealsIdentity())
                .filter(node -> !node.learned())
                .sorted(java.util.Comparator
                        .comparingInt((ResearchTreeGraph.Node node) ->
                                node.availability() == ResearchTreeGraph.Availability.AVAILABLE
                                        ? 0
                                        : 1)
                        .thenComparingInt(ResearchTreeGraph.Node::ordinal))
                .map(ResearchTreeGraph.Node::blueprintId)
                .toList();
    }

    public record Request(
            int requestId,
            long publicationGeneration,
            List<ResourceLocation> targetIds) {
        public Request {
            targetIds = targetIds == null ? List.of() : List.copyOf(targetIds);
            if (requestId < 1
                    || publicationGeneration == Long.MIN_VALUE
                    || targetIds.isEmpty()
                    || targetIds.size() > ResearchAffordabilitySnapshot.MAX_TARGETS_PER_BATCH
                    || targetIds.stream().anyMatch(java.util.Objects::isNull)
                    || targetIds.stream().distinct().count() != targetIds.size()) {
                throw new IllegalArgumentException("invalid research affordability request");
            }
        }
    }

    public enum ResponseOutcome {
        IGNORED,
        ACKNOWLEDGED,
        ACCEPTED,
        RETRY,
        ADVANCED_AFTER_FAILURE
    }

    public record Snapshot(
            boolean enabled,
            long publicationGeneration,
            Map<ResourceLocation, ResearchAffordabilitySnapshot.Entry> results,
            int checkedTargets,
            int totalTargets,
            boolean pending) {
        public Snapshot {
            results = results == null ? Map.of() : Map.copyOf(results);
            if (checkedTargets < 0
                    || checkedTargets > totalTargets
                    || results.size() != checkedTargets
                    || totalTargets < 0
                    || !enabled && (publicationGeneration != Long.MIN_VALUE
                            || !results.isEmpty() || checkedTargets != 0
                            || totalTargets != 0 || pending)) {
                throw new IllegalArgumentException("invalid research affordability client state");
            }
        }

        public int affordableTargets() {
            return Math.toIntExact(results.values().stream()
                    .filter(ResearchAffordabilitySnapshot.Entry::affordableNow)
                    .count());
        }

        public boolean complete() {
            return enabled && checkedTargets == totalTargets && !pending;
        }
    }

    private record Pending(Request request, long startedAtMillis, int timeoutRetries) {
        private Pending {
            if (request == null || startedAtMillis < 0L
                    || timeoutRetries < 0 || timeoutRetries > MAX_TIMEOUT_RETRIES) {
                throw new IllegalArgumentException("pending affordability request is invalid");
            }
        }
    }
}
