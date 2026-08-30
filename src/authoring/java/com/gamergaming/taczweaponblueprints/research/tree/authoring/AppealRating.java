package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

/** Explicit human review input; popularity is never inferred from mechanical stats. */
public record AppealRating(int score, String reason) {
    public AppealRating {
        if (score < 0 || score > ResearchTechTreeContract.SCORE_MAX) {
            throw new IllegalArgumentException("Appeal score must be between 0 and 100");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Reviewed appeal ratings require a reason");
        }
        reason = reason.trim();
    }
}
