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
        if (!directPathResearch) {
            // CREATE_BLUEPRINT has no route fingerprint. Reject a fingerprint
            // from a DIRECT_LEARN preview so a live result-mode change cannot
            // silently commit a different transaction shape.
            return requested == null || requested.isEmpty();
        }
        if (current == null || requested == null || requested.isEmpty()) {
            return false;
        }
        return current.routeFingerprint().filter(requested.orElseThrow()::equals).isPresent();
    }
}
