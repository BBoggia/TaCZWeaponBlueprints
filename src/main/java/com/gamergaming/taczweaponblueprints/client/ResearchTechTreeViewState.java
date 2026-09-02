package com.gamergaming.taczweaponblueprints.client;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

import net.minecraft.resources.ResourceLocation;

/**
 * Client-owned navigation state for the live Tech Tree browse view.
 *
 * <p>Each published domain retains its own focus, query, pin and camera. The
 * remembered preferred domain is deliberately distinct from the active
 * fallback so a temporarily unavailable choice can return after a reload.
 */
public final class ResearchTechTreeViewState {
    public static final int MAX_SEARCH_LENGTH = 64;

    private final EnumMap<Domain, MutableDomainState> states =
            new EnumMap<>(Domain.class);
    private Domain preferredDomain = ResearchTechTreeContract.DEFAULT_DOMAIN;
    private Domain selectedDomain;

    public Domain preferredDomain() {
        return preferredDomain;
    }

    public Optional<Domain> selectedDomain() {
        return Optional.ofNullable(selectedDomain);
    }

    /** Reconciles remembered state with one newly committed public catalog. */
    public void retain(
            ResearchTechTreeProjectionCatalog catalog,
            ResourceLocation preferredNodeId) {
        retain(
                catalog,
                ResearchTechTreeLayoutEngine.layoutCatalog(
                        catalog, ResearchTechTreeLayoutPolicy.DEFAULT),
                preferredNodeId);
    }

    /** Reconciles state and invalidates only cameras whose geometry changed. */
    public void retain(
            ResearchTechTreeProjectionCatalog catalog,
            ResearchTechTreeLayoutCatalog layouts,
            ResourceLocation preferredNodeId) {
        requireCatalog(catalog);
        if (layouts == null || !layouts.matches(catalog)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree layout catalog does not match its projection");
        }
        LinkedHashSet<Domain> available = new LinkedHashSet<>(catalog.domains());
        states.keySet().removeIf(domain -> !available.contains(domain));
        selectedDomain = ResearchTechTreeContract.fallbackDomain(
                available, preferredDomain).orElse(null);

        for (ResearchTechTreeProjection projection : catalog.projections()) {
            MutableDomainState state = states.computeIfAbsent(
                    projection.domain(), ignored -> new MutableDomainState());
            state.retain(projection, layouts.layout(projection.domain()).orElseThrow());
        }
        if (selectedDomain == null) {
            return;
        }
        MutableDomainState selected = states.get(selectedDomain);
        if (selected.focusedNodeId == null
                && belongsToSelectedDomain(preferredNodeId, catalog)) {
            selected.focusedNodeId = preferredNodeId;
        }
        if (selected.focusedNodeId == null) {
            selected.focusedNodeId = firstNode(catalog, selectedDomain).orElse(null);
        }
    }

    public boolean selectDomain(
            Domain domain,
            ResearchTechTreeProjectionCatalog catalog) {
        requireCatalog(catalog);
        if (domain == null || catalog.projection(domain).isEmpty()) {
            return false;
        }
        boolean changed = selectedDomain != domain;
        preferredDomain = domain;
        selectedDomain = domain;
        MutableDomainState state = states.computeIfAbsent(
                domain, ignored -> new MutableDomainState());
        if (state.focusedNodeId == null) {
            state.focusedNodeId = firstNode(catalog, domain).orElse(null);
        }
        return changed;
    }

    /** Selects and focuses the public domain that owns this node. */
    public Optional<Domain> selectNode(
            ResourceLocation nodeId,
            ResearchTechTreeProjectionCatalog catalog) {
        requireCatalog(catalog);
        Optional<Domain> domain = catalog.domainOf(nodeId);
        if (domain.isEmpty()) {
            return Optional.empty();
        }
        selectDomain(domain.orElseThrow(), catalog);
        states.get(domain.orElseThrow()).focusedNodeId = nodeId;
        return domain;
    }

    /** Cycles through the stable publication order and wraps at both ends. */
    public Optional<Domain> cycleDomain(
            int delta,
            ResearchTechTreeProjectionCatalog catalog) {
        requireCatalog(catalog);
        if (delta == 0 || catalog.domains().isEmpty()) {
            return selectedDomain();
        }
        int current = catalog.domains().indexOf(selectedDomain);
        int base = current < 0 ? (delta > 0 ? -1 : 0) : current;
        int target = Math.floorMod(base + delta, catalog.domains().size());
        Domain domain = catalog.domains().get(target);
        selectDomain(domain, catalog);
        return Optional.of(domain);
    }

    public Optional<ResourceLocation> focusedNode() {
        return selectedState().map(state -> state.focusedNodeId);
    }

    public boolean focus(
            ResourceLocation nodeId,
            ResearchTechTreeProjectionCatalog catalog) {
        requireCatalog(catalog);
        if (!belongsToSelectedDomain(nodeId, catalog)) {
            return false;
        }
        states.get(selectedDomain).focusedNodeId = nodeId;
        return true;
    }

    public String searchQuery() {
        return selectedState().map(state -> state.searchQuery).orElse("");
    }

    public Optional<ResourceLocation> activeSearchMatch() {
        return selectedState().map(state -> state.activeSearchMatch);
    }

    public void setSearch(
            String query,
            ResourceLocation activeMatch,
            ResearchTechTreeProjectionCatalog catalog) {
        requireCatalog(catalog);
        validateSearch(query);
        MutableDomainState state = requireSelectedState();
        if (activeMatch != null && !belongsToSelectedDomain(activeMatch, catalog)) {
            throw new IllegalArgumentException(
                    "Research Tech Tree search match is outside the selected domain");
        }
        state.searchQuery = query;
        state.activeSearchMatch = activeMatch;
    }

    public Optional<ResourceLocation> pinnedNode() {
        return selectedState().map(state -> state.pinnedNodeId);
    }

    public boolean pin(
            ResourceLocation nodeId,
            ResearchTechTreeProjectionCatalog catalog) {
        requireCatalog(catalog);
        if (!belongsToSelectedDomain(nodeId, catalog)) {
            return false;
        }
        states.get(selectedDomain).pinnedNodeId = nodeId;
        return true;
    }

    public void clearPin() {
        selectedState().ifPresent(state -> state.pinnedNodeId = null);
    }

    public void saveCamera(Surface surface, ResearchTreeViewport.Snapshot camera) {
        if (surface == null || camera == null) {
            throw new IllegalArgumentException("Research Tech Tree camera fields cannot be null");
        }
        MutableDomainState state = requireSelectedState();
        state.cameras.put(surface, camera);
        state.cameraLayouts.remove(surface);
    }

    /** Saves a camera together with the responsive geometry it describes. */
    public void saveCamera(
            Surface surface,
            ResearchTechTreeLayout layout,
            ResearchTreeViewport.Snapshot camera) {
        if (surface == null || layout == null || camera == null
                || layout.domain() != selectedDomain) {
            throw new IllegalArgumentException(
                    "Research Tech Tree camera layout fields are invalid");
        }
        MutableDomainState state = requireSelectedState();
        state.cameras.put(surface, camera);
        state.cameraLayouts.put(surface, layout);
    }

    public Optional<ResearchTreeViewport.Snapshot> camera(Surface surface) {
        if (surface == null) {
            throw new IllegalArgumentException("Research Tech Tree camera surface cannot be null");
        }
        return selectedState().map(state -> state.cameras.get(surface));
    }

    /** Rejects a saved camera whose responsive row geometry no longer matches. */
    public Optional<ResearchTreeViewport.Snapshot> camera(
            Surface surface,
            ResearchTechTreeLayout layout) {
        if (surface == null || layout == null || layout.domain() != selectedDomain) {
            throw new IllegalArgumentException(
                    "Research Tech Tree camera layout lookup is invalid");
        }
        return selectedState().flatMap(state -> layout.equals(
                        state.cameraLayouts.get(surface))
                ? Optional.ofNullable(state.cameras.get(surface))
                : Optional.empty());
    }

    public Optional<DomainSnapshot> snapshot(Domain domain) {
        MutableDomainState state = domain == null ? null : states.get(domain);
        return Optional.ofNullable(state == null ? null : state.snapshot());
    }

    private Optional<MutableDomainState> selectedState() {
        return Optional.ofNullable(selectedDomain == null ? null : states.get(selectedDomain));
    }

    private MutableDomainState requireSelectedState() {
        return selectedState().orElseThrow(() ->
                new IllegalStateException("Research Tech Tree has no selected domain"));
    }

    private boolean belongsToSelectedDomain(
            ResourceLocation nodeId,
            ResearchTechTreeProjectionCatalog catalog) {
        return nodeId != null && selectedDomain != null
                && catalog.domainOf(nodeId).filter(selectedDomain::equals).isPresent();
    }

    private static Optional<ResourceLocation> firstNode(
            ResearchTechTreeProjectionCatalog catalog,
            Domain domain) {
        return catalog.projection(domain)
                .flatMap(projection -> projection.graph().nodes().stream()
                        .findFirst()
                        .map(ResearchTreeGraph.Node::blueprintId));
    }

    private static void requireCatalog(ResearchTechTreeProjectionCatalog catalog) {
        if (catalog == null) {
            throw new IllegalArgumentException("Research Tech Tree catalog cannot be null");
        }
    }

    private static void validateSearch(String query) {
        if (query == null || query.length() > MAX_SEARCH_LENGTH
                || query.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("Research Tech Tree search query is invalid");
        }
    }

    public enum Surface {
        COMPACT,
        FULLSCREEN
    }

    public record DomainSnapshot(
            Optional<ResourceLocation> focusedNodeId,
            String searchQuery,
            Optional<ResourceLocation> activeSearchMatch,
            Optional<ResourceLocation> pinnedNodeId,
            Map<Surface, ResearchTreeViewport.Snapshot> cameras) {
        public DomainSnapshot {
            focusedNodeId = focusedNodeId == null ? Optional.empty() : focusedNodeId;
            activeSearchMatch = activeSearchMatch == null
                    ? Optional.empty() : activeSearchMatch;
            pinnedNodeId = pinnedNodeId == null ? Optional.empty() : pinnedNodeId;
            validateSearch(searchQuery);
            EnumMap<Surface, ResearchTreeViewport.Snapshot> copy =
                    new EnumMap<>(Surface.class);
            if (cameras != null) {
                copy.putAll(cameras);
            }
            if (copy.entrySet().stream().anyMatch(entry ->
                    entry.getKey() == null || entry.getValue() == null)) {
                throw new IllegalArgumentException(
                        "Research Tech Tree camera snapshot cannot contain nulls");
            }
            cameras = Collections.unmodifiableMap(copy);
        }
    }

    private static final class MutableDomainState {
        private ResourceLocation focusedNodeId;
        private String searchQuery = "";
        private ResourceLocation activeSearchMatch;
        private ResourceLocation pinnedNodeId;
        private final EnumMap<Surface, ResearchTreeViewport.Snapshot> cameras =
                new EnumMap<>(Surface.class);
        private final EnumMap<Surface, ResearchTechTreeLayout> cameraLayouts =
                new EnumMap<>(Surface.class);
        private ResearchTechTreeLayout layoutIdentity;

        private void retain(
                ResearchTechTreeProjection projection,
                ResearchTechTreeLayout layout) {
            if (layoutIdentity != null && !layoutIdentity.equals(layout)) {
                cameras.clear();
                cameraLayouts.clear();
            }
            layoutIdentity = layout;
            if (projection.graph().node(focusedNodeId).isEmpty()) {
                focusedNodeId = null;
            }
            if (projection.graph().node(activeSearchMatch).isEmpty()) {
                activeSearchMatch = null;
            }
            if (projection.graph().node(pinnedNodeId).isEmpty()) {
                pinnedNodeId = null;
            }
        }

        private DomainSnapshot snapshot() {
            return new DomainSnapshot(
                    Optional.ofNullable(focusedNodeId),
                    searchQuery,
                    Optional.ofNullable(activeSearchMatch),
                    Optional.ofNullable(pinnedNodeId),
                    cameras);
        }
    }
}
