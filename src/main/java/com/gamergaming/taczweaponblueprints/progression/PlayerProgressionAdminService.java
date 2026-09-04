package com.gamergaming.taczweaponblueprints.progression;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

/** Pure invariant-preserving operations behind the permission-gated commands. */
public final class PlayerProgressionAdminService {
    private PlayerProgressionAdminService() {
    }

    public static Snapshot inspect(IPlayerRecipeData data) {
        if (data == null) {
            throw new IllegalArgumentException("player progression data cannot be null");
        }
        var fragments = data.getArchivedBlueprintFragments();
        var criteria = data.getProgressionCriteria();
        if (fragments == null || criteria == null) {
            throw new IllegalArgumentException("player supplemental progression cannot be null");
        }
        return new Snapshot(
                data.getLearnedBlueprints().size(),
                data.getDiscoveredBlueprints().size(),
                data.getLearnedRecipes().size(),
                data.getResearchPoints(),
                fragments.size(),
                fragments.values().stream()
                        .mapToLong(Integer::longValue).sum(),
                criteria.size());
    }

    public static boolean reset(IPlayerRecipeData data, ResetState state) {
        if (data == null || state == null) {
            return false;
        }
        Set<String> learned = Set.copyOf(data.getLearnedBlueprints());
        Set<String> discovered = Set.copyOf(data.getDiscoveredBlueprints());
        int points = data.getResearchPoints();
        boolean reset = switch (state) {
            case LEARNED -> {
                boolean replaced = data.replaceProgression(List.of(), discovered, points);
                if (replaced) {
                    data.replaceRecipes(List.of());
                }
                yield replaced;
            }
            case DISCOVERED -> data.replaceProgression(learned, learned, points);
            case POINTS -> data.setResearchPoints(0);
            case AWARDS -> {
                data.clearResearchPointAwardLedger();
                yield true;
            }
            case FRAGMENTS -> {
                data.clearArchivedBlueprintFragments();
                yield true;
            }
            case CRITERIA -> {
                data.clearProgressionCriteria();
                yield true;
            }
            case ALL -> {
                boolean replaced = data.replaceProgression(List.of(), List.of(), 0);
                if (replaced) {
                    data.replaceRecipes(List.of());
                    data.clearResearchPointAwardLedger();
                    data.clearArchivedBlueprintFragments();
                    data.clearProgressionCriteria();
                }
                yield replaced;
            }
        };
        if (reset) {
            data.clearRecentUnlockHistory();
        }
        return reset;
    }

    /** Adds operator-granted RP without bypassing configured or persisted bounds. */
    public static boolean giveResearchPoints(
            IPlayerRecipeData data,
            int amount,
            int pointCap) {
        if (data == null
                || amount <= 0
                || pointCap < 0
                || pointCap > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            return false;
        }
        return ResearchPointTransactionService.credit(
                data,
                amount,
                pointCap,
                ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL)
                .successful();
    }

    public enum ResetState {
        LEARNED,
        DISCOVERED,
        POINTS,
        AWARDS,
        FRAGMENTS,
        CRITERIA,
        ALL;

        public String serializedName() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Optional<ResetState> parse(String value) {
            if (value == null) {
                return Optional.empty();
            }
            try {
                return Optional.of(valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return Optional.empty();
            }
        }
    }

    public record Snapshot(
            int learnedBlueprints,
            int discoveredBlueprints,
            int legacyRecipes,
            int researchPoints,
            int fragmentTargets,
            long archivedFragments,
            int progressionCriteria) {
        public Snapshot {
            if (learnedBlueprints < 0 || discoveredBlueprints < learnedBlueprints
                    || legacyRecipes < 0 || researchPoints < 0
                    || fragmentTargets < 0
                    || fragmentTargets > PlayerProgressionLimits.MAX_FRAGMENT_TARGETS
                    || archivedFragments < 0L
                    || archivedFragments > (long) PlayerProgressionLimits.MAX_FRAGMENT_TARGETS
                            * PlayerProgressionLimits.MAX_PROGRESS_VALUE
                    || progressionCriteria < 0
                    || progressionCriteria > PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA) {
                throw new IllegalArgumentException("invalid player progression inspection snapshot");
            }
        }
    }
}
