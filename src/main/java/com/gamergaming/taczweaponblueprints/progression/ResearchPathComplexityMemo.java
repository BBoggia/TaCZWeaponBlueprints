package com.gamergaming.taczweaponblueprints.progression;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

/** Brief bounded memo for exact general routes that exhausted deterministic budgets. */
final class ResearchPathComplexityMemo {
    static final long RETENTION_NANOS = 2_000_000_000L;
    static final int MAX_ENTRIES = 64;
    private static final Map<Key, Long> FAILURES = new LinkedHashMap<>(16, 0.75f, true) {
        private static final long serialVersionUID = 1L;

        @Override
        protected boolean removeEldestEntry(Map.Entry<Key, Long> eldest) {
            return size() > MAX_ENTRIES;
        }
    };

    private ResearchPathComplexityMemo() {
    }

    static Key key(
            ResolvedResearchPathGraph.Graph graph,
            boolean creativePlayer,
            ResearchPathUnlockPlanner.PlanningBudget budget) {
        if (graph == null
                || graph.shape() != ResolvedResearchPathGraph.GraphShape.GENERAL_AND_OR_DAG
                || budget == null) {
            throw new IllegalArgumentException("complexity memo requires a general AND/OR graph");
        }
        budget.countCanonicalWork(6L);
        MessageDigest digest = sha256();
        update(digest, "taczweaponblueprints:general_route_failure:v1");
        update(digest, creativePlayer ? 1L : 0L);
        update(digest, ResearchPathUnlockPlanner.routeSelectionPolicy().name());
        update(digest, graph.targetId().toString());
        update(digest, "nodes");
        update(digest, graph.nodes().size());
        for (ResolvedResearchPathGraph.Node node : graph.nodes()) {
            budget.countCanonicalWork(9L);
            update(digest, "node");
            update(digest, node.blueprintId().toString());
            update(digest, node.state().name());
            update(digest, node.routeViable() ? 1L : 0L);
            update(digest, node.connected() ? 1L : 0L);
            update(digest, node.rootProvenance().isPresent() ? 1L : 0L);
            node.rootProvenance().ifPresent(value -> update(digest, value.name()));
            update(digest, node.policy().isPresent() ? 1L : 0L);
            node.policy().ifPresent(policy -> {
                update(digest, policy.researchCost().points());
                update(digest, policy.creativeBypassesCost() ? 1L : 0L);
                update(digest, policy.researchCost().ingredients().size());
                for (BlueprintResearchIngredient ingredient
                        : policy.researchCost().ingredients()) {
                    budget.countCanonicalWork(4L + ingredient.items().size());
                    update(digest, "ingredient");
                    update(digest, ingredient.items().size());
                    ingredient.items().stream()
                            .sorted(java.util.Comparator.comparing(Object::toString))
                            .forEach(id -> update(digest, id.toString()));
                    update(digest, ingredient.tag().map(Object::toString).orElse(""));
                    update(digest, ingredient.count());
                }
            });
            update(digest, node.groups().size());
            for (ResolvedResearchPathGraph.RequirementGroup group : node.groups()) {
                budget.countCanonicalWork(3L + group.alternatives().size());
                update(digest, "group");
                update(digest, group.state().name());
                update(digest, group.alternatives().size());
                for (ResolvedResearchPathGraph.Alternative alternative : group.alternatives()) {
                    update(digest, alternative.nodeIndex());
                    update(digest, alternative.state().name());
                    update(digest, alternative.usable() ? 1L : 0L);
                }
            }
        }
        byte[] hash = digest.digest();
        return new Key(readLong(hash, 0), readLong(hash, Long.BYTES));
    }

    static synchronized boolean contains(Key key, long nowNanos) {
        if (key == null) {
            return false;
        }
        Long recorded = FAILURES.get(key);
        if (recorded == null) {
            return false;
        }
        if (nowNanos - recorded >= RETENTION_NANOS) {
            FAILURES.remove(key);
            return false;
        }
        return true;
    }

    static synchronized void remember(Key key, long nowNanos) {
        if (key == null) {
            return;
        }
        FAILURES.put(key, nowNanos);
    }

    static synchronized void forget(Key key) {
        if (key != null) {
            FAILURES.remove(key);
        }
    }

    static synchronized void clear() {
        FAILURES.clear();
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        update(digest, bytes.length);
        digest.update(bytes);
    }

    private static void update(MessageDigest digest, long value) {
        for (int shift = Long.SIZE - Byte.SIZE; shift >= 0; shift -= Byte.SIZE) {
            digest.update((byte) (value >>> shift));
        }
    }

    private static long readLong(byte[] bytes, int offset) {
        long value = 0L;
        for (int index = 0; index < Long.BYTES; index++) {
            value = value << Byte.SIZE | bytes[offset + index] & 0xffL;
        }
        return value;
    }

    record Key(long high, long low) {
    }
}
