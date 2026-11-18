package com.gamergaming.taczweaponblueprints.menu;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.ResearchRouteFingerprint;

/** Compares the client's quoted route with a freshly rebuilt authoritative preview. */
final class ResearchPreviewCommitGuard {
    private ResearchPreviewCommitGuard() {
    }

    static boolean accepts(
            boolean directPathResearch,
            ResearchSelectionPreview current,
            Optional<ResearchRouteFingerprint> requested) {
        if (current == null || current.blueprintId().isEmpty()
                || current.pathPlanningState() != ResearchSelectionPreview.PathPlanningState.NONE) {
            return false;
        }
        // Both modes carry an opaque quote identity. Its server-side digest includes the result
        // mode, so a live switch between printing and learning also requires a fresh confirmation.
        if (requested == null || requested.isEmpty()) {
            return false;
        }
        return current.routeFingerprint().filter(requested.orElseThrow()::equals).isPresent();
    }
}
