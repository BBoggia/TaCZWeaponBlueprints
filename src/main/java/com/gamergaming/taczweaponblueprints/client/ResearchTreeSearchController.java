package com.gamergaming.taczweaponblueprints.client;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/** Pure ordered search state for one disclosure-safe Research Tree publication. */
final class ResearchTreeSearchController {
    private int windowStart;
    private boolean followActive = true;
    private int windowSize = 1;
    private final ResearchTreeScrollAccumulator wheel = new ResearchTreeScrollAccumulator();
    void scroll(double delta, int visible) {
        windowSize = Math.max(1, visible);
        followActive = false;
        windowStart = Math.max(0, Math.min(Math.max(0, matches.size() - windowSize),
                windowStart + wheel.consume(-delta)));
    }
    void page(int direction, int visible) {
        if (direction == 0 || matches.isEmpty()) return;
        var ordered = List.copyOf(matches);
        window(visible);
        int index = ordered.indexOf(activeMatch);
        int row = Math.max(0, Math.min(windowSize - 1, index - windowStart));
        windowStart = Math.max(0, Math.min(ordered.size() - windowSize,
                windowStart + Integer.signum(direction) * windowSize));
        activeMatch = ordered.get(windowStart + row);
        followActive = false;
        wheel.reset();
    }
    private void revealActive() {
        followActive = true;
        if (activeMatch == null) { windowStart = 0; return; }
        int index = List.copyOf(matches).indexOf(activeMatch);
        if (index < windowStart) windowStart = Math.max(0, index);
        else if (index >= windowStart + windowSize) windowStart = index - windowSize + 1;
    }
    private String query = "";
    private Set<ResourceLocation> matches = Set.of();
    private ResourceLocation activeMatch;

    void update(
            String rawQuery,
            List<ResearchTreeGraph.Node> nodes,
            Function<ResearchTreeGraph.Node, String> searchableText) {
        if (rawQuery == null || nodes == null || searchableText == null
                || nodes.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("invalid Research Tree search input");
        }
        String nextQuery = rawQuery.strip().toLowerCase(Locale.ROOT);
        boolean queryChanged = !query.equals(nextQuery);
        if (queryChanged) { windowStart = 0; wheel.reset(); }
        query = nextQuery;
        if (query.isEmpty()) {
            matches = Set.of();
            activeMatch = null;
            return;
        }
        LinkedHashSet<ResourceLocation> next = new LinkedHashSet<>();
        for (ResearchTreeGraph.Node node : nodes) {
            String searchable = searchableText.apply(node);
            if (searchable == null) {
                throw new IllegalArgumentException("Research Tree searchable text cannot be null");
            }
            if (searchable.toLowerCase(Locale.ROOT).contains(query)) {
                next.add(node.blueprintId());
            }
        }
        matches = Collections.unmodifiableSet(next);
        if (activeMatch == null || !matches.contains(activeMatch)) {
            activeMatch = matches.isEmpty() ? null : matches.iterator().next();
            followActive = true;
        }
        if (queryChanged) revealActive();
    }

    String query() {
        return query;
    }

    Set<ResourceLocation> matches() {
        return matches;
    }

    Optional<ResourceLocation> activeMatch() {
        return Optional.ofNullable(activeMatch);
    }

    Set<ResourceLocation> visibleMatches(ResearchTreeGraph graph) {
        if (graph == null) {
            throw new IllegalArgumentException("Research Tree search graph cannot be null");
        }
        LinkedHashSet<ResourceLocation> visible = new LinkedHashSet<>();
        for (ResourceLocation match : matches) {
            if (graph.node(match).isPresent()) {
                visible.add(match);
            }
        }
        return Collections.unmodifiableSet(visible);
    }

    Optional<ResourceLocation> select(ResourceLocation blueprintId) {
        if (blueprintId == null || !matches.contains(blueprintId)) {
            return Optional.empty();
        }
        activeMatch = blueprintId;
        revealActive();
        return Optional.of(activeMatch);
    }

    Optional<ResourceLocation> selectNext(int delta) {
        if (matches.isEmpty()) {
            activeMatch = null;
            return Optional.empty();
        }
        List<ResourceLocation> ordered = List.copyOf(matches);
        int currentIndex = activeMatch == null ? -1 : ordered.indexOf(activeMatch);
        activeMatch = ordered.get(Math.floorMod(currentIndex + delta, ordered.size()));
        revealActive();
        return Optional.of(activeMatch);
    }

    Optional<ResourceLocation> commit() {
        if (activeMatch == null) return Optional.empty();
        // Wheel browsing may leave the cursor behind, but Enter must never
        // navigate to a result that is no longer visible in the dropdown.
        if (followActive) window(windowSize);
        int index = List.copyOf(matches).indexOf(activeMatch);
        return index >= windowStart && index < windowStart + windowSize
                ? Optional.ofNullable(activeMatch) : Optional.empty();
    }

    List<Result> window(int maximumVisible) {
        if (maximumVisible <= 0) {
            throw new IllegalArgumentException("Research Tree search window must be positive");
        }
        List<ResourceLocation> ordered = List.copyOf(matches);
        if (ordered.isEmpty()) {
            return List.of();
        }
        windowSize = Math.min(maximumVisible, ordered.size());
        if (followActive) { windowStart = Math.max(0, ordered.indexOf(activeMatch) - windowSize / 2); followActive = false; }
        windowStart = Math.max(0, Math.min(windowStart, ordered.size() - windowSize));
        int start = windowStart;
        java.util.ArrayList<Result> results = new java.util.ArrayList<>(windowSize);
        for (int index = start; index < start + windowSize; index++) {
            ResourceLocation blueprintId = ordered.get(index);
            results.add(new Result(
                    blueprintId,
                    index,
                    ordered.size(),
                    blueprintId.equals(activeMatch)));
        }
        return List.copyOf(results);
    }

    record Result(
            ResourceLocation blueprintId,
            int index,
            int total,
            boolean active) {
        Result {
            if (blueprintId == null || index < 0 || total <= 0 || index >= total) {
                throw new IllegalArgumentException("invalid Research Tree search result");
            }
        }
    }
}
