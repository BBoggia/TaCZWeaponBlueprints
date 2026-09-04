package com.gamergaming.taczweaponblueprints.menu;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.DisclosedCraftingAccess;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

/** Disclosure-safe progression details for one authoritative Research Bench selection. */
public record ResearchSelectionProgressionPreview(
        Optional<ResearchWorkbenchTier> currentTier,
        Optional<ResearchWorkbenchTier> requiredTier,
        Optional<FragmentProgress> fragments,
        Optional<DisclosedCraftingAccess> craftingAccess) {
    public static final ResearchSelectionProgressionPreview EMPTY =
            new ResearchSelectionProgressionPreview(
                    Optional.empty(), Optional.empty(), Optional.empty());

    public ResearchSelectionProgressionPreview {
        currentTier = currentTier == null ? Optional.empty() : currentTier;
        requiredTier = requiredTier == null ? Optional.empty() : requiredTier;
        fragments = fragments == null ? Optional.empty() : fragments;
        craftingAccess = craftingAccess == null ? Optional.empty() : craftingAccess;
        if (currentTier.isPresent() != requiredTier.isPresent()) {
            throw new IllegalArgumentException(
                    "research selection tier context must be complete or absent");
        }
    }

    /** Compatibility constructor for previews without disclosed crafting access. */
    public ResearchSelectionProgressionPreview(
            Optional<ResearchWorkbenchTier> currentTier,
            Optional<ResearchWorkbenchTier> requiredTier,
            Optional<FragmentProgress> fragments) {
        this(currentTier, requiredTier, fragments, Optional.empty());
    }

    public record FragmentProgress(
            int archived,
            int threshold,
            BlueprintFragmentPolicy.CompletionMode completionMode,
            boolean discountApplied) {
        public FragmentProgress {
            if (archived < 0
                    || archived > BlueprintFragmentPolicy.MAX_ARCHIVED_FRAGMENTS
                    || threshold < 1
                    || threshold > BlueprintFragmentPolicy.MAX_THRESHOLD
                    || completionMode == null
                    || completionMode == BlueprintFragmentPolicy.CompletionMode.DISABLED
                    || discountApplied
                            && (completionMode
                                            != BlueprintFragmentPolicy.CompletionMode
                                                    .TARGETED_RESEARCH_BOOST
                                    || archived < threshold)) {
                throw new IllegalArgumentException(
                        "invalid fragment progress in research selection");
            }
        }

        public int displayedArchived() {
            return Math.min(archived, threshold);
        }

        public boolean complete() {
            return archived >= threshold;
        }
    }
}
