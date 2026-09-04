package com.gamergaming.taczweaponblueprints.progression.eligibility;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Canonical blocker ordering shared by previews, diagnostics, and later transactions. */
public record ResearchEligibilityBlockers(List<ResearchEligibilityBlocker> all) {
    public static final int MAX_BLOCKERS = 64;
    private static final Comparator<ResearchEligibilityBlocker> ORDER = Comparator
            .comparingInt((ResearchEligibilityBlocker blocker) -> blocker.kind().priority())
            .thenComparing(blocker -> blocker.subjectId().toString())
            .thenComparing(ResearchEligibilityBlocker::stableKey);
    public static final ResearchEligibilityBlockers NONE = new ResearchEligibilityBlockers(
            List.of());

    public ResearchEligibilityBlockers {
        if (all == null
                || all.size() > MAX_BLOCKERS
                || all.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("eligibility blocker collection is invalid");
        }
        Set<String> identities = new LinkedHashSet<>();
        if (all.stream().anyMatch(blocker -> !identities.add(identity(blocker)))) {
            throw new IllegalArgumentException("eligibility blocker collection contains duplicates");
        }
        all = all.stream().sorted(ORDER).toList();
    }

    public boolean eligible() {
        return all.isEmpty();
    }

    public Optional<ResearchEligibilityBlocker> primary() {
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    public List<ResearchEligibilityBlocker> secondary() {
        return all.size() < 2 ? List.of() : all.subList(1, all.size());
    }

    private static String identity(ResearchEligibilityBlocker blocker) {
        return blocker.kind() + "\u0000" + blocker.subjectId() + "\u0000" + blocker.stableKey();
    }
}
