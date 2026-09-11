package com.gamergaming.taczweaponblueprints.client;

import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

/**
 * Client-thread scene boundary. Current presentation is always taken from the bound publication;
 * only validated geometry may survive a state update. Retains at most one scene per domain.
 */
public final class ResearchTreeSceneCache {
    public enum Status { VALID, FALLBACK, UNAVAILABLE }

    public record Result(Object scope, long generation, ResearchTreePublication publication, Domain domain,
            Optional<ResearchTechTreeProjection> presentation, Status status,
            Optional<ResearchTreeScene> geometry, List<String> diagnostics, boolean reused) {
        public Result {
            java.util.Objects.requireNonNull(scope);
            java.util.Objects.requireNonNull(publication);
            java.util.Objects.requireNonNull(domain);
            java.util.Objects.requireNonNull(presentation);
            java.util.Objects.requireNonNull(status);
            java.util.Objects.requireNonNull(geometry);
            diagnostics = List.copyOf(diagnostics);
            if ((status == Status.UNAVAILABLE) == geometry.isPresent()) {
                throw new IllegalArgumentException("scene status/geometry mismatch");
            }
        }
    }

    private record Entry(String requestedRevision, String fallbackRevision, boolean groupedRouting, ResearchTreeScene scene,
            Status status, List<String> diagnostics) { }

    private final Function<ResearchTechTreeProjection, ResearchTreeSceneEngine> primaryFactory;
    private final ResearchTreeSceneEngine fallback;
    private final EnumMap<Domain, Entry> entries = new EnumMap<>(Domain.class);
    private final EnumMap<Domain, Object> scopes = new EnumMap<>(Domain.class);
    private ResearchTreePublication publication = ResearchTreePublication.EMPTY;
    private ResearchTechTreeProjectionCatalog catalog = ResearchTechTreeProjectionCatalog.EMPTY;
    private long generation;
    private long layoutAttempts;

    long layoutAttempts() { return layoutAttempts; }

    public ResearchTreeSceneCache() {
        this(ResearchTreeCompatibilitySceneEngine::new, new ResearchTreeSimpleSceneEngine());
    }

    ResearchTreeSceneCache(Function<ResearchTechTreeProjection, ResearchTreeSceneEngine> primaryFactory,
            ResearchTreeSceneEngine fallback) {
        this.primaryFactory = java.util.Objects.requireNonNull(primaryFactory);
        this.fallback = java.util.Objects.requireNonNull(fallback);
    }

    /** Derives the typed projection from the supplied public data, never from a server catalog. */
    public void bind(ResearchTreePublication next) {
        bind(next, ResearchTechTreeProjectionBuilder.build(next));
    }

    // The projection cache has already built this exact catalog in its atomic update.
    void bind(ResearchTreePublication next, ResearchTechTreeProjectionCatalog nextCatalog) {
        java.util.Objects.requireNonNull(next);
        java.util.Objects.requireNonNull(nextCatalog);
        var retained = new EnumMap<Domain, Entry>(Domain.class);
        for (var entry : entries.entrySet()) {
            var projection = nextCatalog.projection(entry.getKey());
            var old = entry.getValue().scene().key();
            try {
                if (projection.isPresent() && ResearchTreeSceneInput.from(projection.orElseThrow(), old.policy())
                        .topology().equals(old.topology())) retained.put(entry.getKey(), entry.getValue());
            } catch (IllegalArgumentException unsupportedGeometry) {
                // A typed projection can still fail the stricter scene input contract.
                // Bind its current presentation; scene() will report unavailable geometry.
            }
        }
        entries.clear();
        entries.putAll(retained);
        publication = next;
        catalog = nextCatalog;
        generation++;
    }

    public Result scene(ResearchTreePublication expectedPublication, Domain domain, ResearchTreeScenePolicy policy) {
        if (expectedPublication != publication) throw new IllegalArgumentException("scene publication is no longer current");
        java.util.Objects.requireNonNull(domain);
        java.util.Objects.requireNonNull(policy);
        var projection = catalog.projection(domain);
        ResearchTreeSceneInput input;
        ResearchTreeSceneEngine primary;
        String primaryRevision, fallbackRevision;
        boolean groupedRouting;
        try {
            input = projection.map(p -> ResearchTreeSceneInput.from(p, policy))
                    .orElseGet(() -> ResearchTreeSceneInput.empty(domain, policy));
            // No legacy engine call for absent domains: a valid empty scene is explicit.
            primary = projection.isPresent() ? java.util.Objects.requireNonNull(primaryFactory.apply(projection.orElseThrow()))
                    : new ResearchTreeSimpleSceneEngine();
            primaryRevision = java.util.Objects.requireNonNull(primary.revision());
            fallbackRevision = java.util.Objects.requireNonNull(fallback.revision());
            groupedRouting = primary.requiresGroupedRouting();
        } catch (RuntimeException preparationFailure) {
            invalidate(domain);
            // Without a prepared input/engine contract, a fallback cannot be validated safely.
            return result(domain, projection, Status.UNAVAILABLE, Optional.empty(), List.of(diagnostic(preparationFailure)), false);
        }
        var previous = entries.get(domain);
        if (previous != null && previous.requestedRevision().equals(primaryRevision)
                && previous.fallbackRevision().equals(fallbackRevision) && previous.groupedRouting() == groupedRouting
                && previous.scene().key().equals(input.key(previous.scene().key().engineRevision()))) {
            return result(domain, projection, previous.status(), Optional.of(previous.scene()), previous.diagnostics(), true);
        }
        // Incompatible geometry must never escape if both engines fail.
        invalidate(domain);
        try {
            layoutAttempts++;
            var scene = ResearchTreeSceneValidator.validate(input, primaryRevision, primary.layout(input));
            requireRouting(groupedRouting, scene);
            entries.put(domain, new Entry(primaryRevision, fallbackRevision, groupedRouting, scene, Status.VALID, List.of()));
            return result(domain, projection, Status.VALID, Optional.of(scene), List.of(), false);
        } catch (RuntimeException primaryFailure) {
            // The final simple engine never emits grouped routing. Do not build and
            // validate a potentially large scene that cannot meet this contract.
            if (groupedRouting && fallback instanceof ResearchTreeSimpleSceneEngine) {
                return result(domain, projection, Status.UNAVAILABLE, Optional.empty(),
                        List.of(diagnostic(primaryFailure), "simple fallback cannot provide required grouped routing"), false);
            }
            try {
                layoutAttempts++;
                var scene = ResearchTreeSceneValidator.validate(input, fallbackRevision, fallback.layout(input));
                requireRouting(groupedRouting, scene);
                var diagnostics = List.of(diagnostic(primaryFailure));
                entries.put(domain, new Entry(primaryRevision, fallbackRevision, groupedRouting, scene, Status.FALLBACK, diagnostics));
                return result(domain, projection, Status.FALLBACK, Optional.of(scene), diagnostics, false);
            } catch (RuntimeException fallbackFailure) {
                return result(domain, projection, Status.UNAVAILABLE, Optional.empty(),
                        List.of(diagnostic(primaryFailure), diagnostic(fallbackFailure)), false);
            }
        }
    }

    private void invalidate(Domain domain) {
        entries.remove(domain);
        scopes.put(domain, new Object());
    }

    private static void requireRouting(boolean groupedRouting, ResearchTreeScene scene) {
        if (groupedRouting && scene.routing().isEmpty()) {
            throw new IllegalArgumentException("fallback does not provide validated grouped routing");
        }
    }

    /** Consumers retaining a result must check this before drawing or acting on its presentation. */
    public boolean isCurrent(Result result) {
        if (result == null || result.scope() != scopes.get(result.domain()) || result.generation() != generation
                || result.publication() != publication) return false;
        var entry = entries.get(result.domain());
        return result.geometry().isPresent()
                ? entry != null && entry.scene() == result.geometry().orElseThrow()
                : entry == null;
    }

    public void clear() {
        entries.clear();
        scopes.clear();
        publication = ResearchTreePublication.EMPTY;
        catalog = ResearchTechTreeProjectionCatalog.EMPTY;
        generation++;
    }

    private Result result(Domain domain, Optional<ResearchTechTreeProjection> projection, Status status,
            Optional<ResearchTreeScene> geometry, List<String> diagnostics, boolean reused) {
        return new Result(scopes.get(domain), generation, publication, domain, projection, status, geometry, diagnostics, reused);
    }

    private static String diagnostic(RuntimeException failure) {
        String text = failure.getClass().getSimpleName() + ": " + String.valueOf(failure.getMessage());
        return text.substring(0, Math.min(240, text.length()));
    }
}
