package com.gamergaming.taczweaponblueprints.client;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/** Focus, search, and independent viewport state shared by tree presentations. */
public final class ResearchTreeViewState {
    private final Map<ResearchTreeScreenLayout.ViewMode, ResearchTreeViewport> viewports =
            new EnumMap<>(ResearchTreeScreenLayout.ViewMode.class);
    private ResourceLocation focusedId;
    private Set<ResourceLocation> searchMatches = Set.of();

    public ResearchTreeViewState() {
        for (ResearchTreeScreenLayout.ViewMode mode : ResearchTreeScreenLayout.ViewMode.values()) {
            viewports.put(mode, new ResearchTreeViewport());
        }
    }

    public ResearchTreeViewport viewport(ResearchTreeScreenLayout.ViewMode mode) {
        if (mode == null) {
            throw new IllegalArgumentException("Research Tree view mode cannot be null");
        }
        return viewports.get(mode);
    }

    public Optional<ResourceLocation> focusedId() {
        return Optional.ofNullable(focusedId);
    }

    public void focus(ResourceLocation blueprintId) {
        focusedId = blueprintId;
    }

    public Set<ResourceLocation> searchMatches() {
        return searchMatches;
    }

    public void setSearchMatches(Set<ResourceLocation> matches) {
        if (matches == null || matches.isEmpty()) {
            searchMatches = Set.of();
            return;
        }
        if (matches.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("Research Tree search matches cannot contain null IDs");
        }
        searchMatches = Collections.unmodifiableSet(new LinkedHashSet<>(matches));
    }

    /** Retains only focus/search state that exists in the newly published graph. */
    public void retainVisibleNodes(ResearchTreeGraph graph, ResourceLocation preferredFocus) {
        if (graph == null) {
            throw new IllegalArgumentException("Research Tree graph cannot be null");
        }
        if (focusedId == null || graph.node(focusedId).isEmpty()) {
            focusedId = graph.node(preferredFocus).isPresent()
                    ? preferredFocus
                    : graph.nodes().isEmpty() ? null : graph.nodes().get(0).blueprintId();
        }
        if (!searchMatches.isEmpty()) {
            LinkedHashSet<ResourceLocation> retained = new LinkedHashSet<>();
            for (ResourceLocation match : searchMatches) {
                if (graph.node(match).isPresent()) {
                    retained.add(match);
                }
            }
            setSearchMatches(retained);
        }
    }
}
