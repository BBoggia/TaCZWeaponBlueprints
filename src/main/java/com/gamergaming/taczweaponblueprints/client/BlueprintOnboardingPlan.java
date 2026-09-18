package com.gamergaming.taczweaponblueprints.client;

import java.util.List;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpEntry;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpSnapshot;

/** Pure disclosure-safe first-hour guidance derived only from synchronized state. */
public record BlueprintOnboardingPlan(List<Step> steps, List<HelpEntry> earningHelp) {
    public static final int MAX_EARNING_HINTS = 3;

    public BlueprintOnboardingPlan {
        steps = steps == null ? List.of() : List.copyOf(steps);
        earningHelp = earningHelp == null
                ? List.of()
                : List.copyOf(earningHelp.stream().limit(MAX_EARNING_HINTS).toList());
    }

    public static BlueprintOnboardingPlan from(
            BlueprintJournalSnapshot journal,
            HelpSnapshot help) {
        return from(journal,help,com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.LEGACY_CURRENCY);
    }
    public static BlueprintOnboardingPlan from(BlueprintJournalSnapshot journal, HelpSnapshot help,
            com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior mode) {
        BlueprintJournalSnapshot stableJournal = journal == null
                ? BlueprintJournalSnapshot.EMPTY
                : journal;
        HelpSnapshot stableHelp = help == null ? HelpSnapshot.EMPTY : help;
        boolean hasPoints = stableJournal.researchPoints() > 0;
        boolean hasKnowledge = stableJournal.learnedCount() > 0;
        return new BlueprintOnboardingPlan(
                List.of(
                        new Step("journal", State.COMPLETE),
                        new Step("points", hasPoints || hasKnowledge
                                ? State.COMPLETE
                                : State.CURRENT),
                        new Step("bench", hasKnowledge
                                ? State.COMPLETE
                                : hasPoints ? State.CURRENT : State.LATER),
                        new Step("research", hasKnowledge
                                ? State.COMPLETE
                                : State.LATER),
                        new Step(mode == com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.ANALYSIS ? "analyzer_analysis"
                                : mode == com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy.Behavior.DISABLED ? "analyzer_disabled" : "analyzer", State.OPTIONAL),
                        new Step("villagers", State.OPTIONAL)),
                stableHelp.entries());
    }

    public record Step(String key, State state) {
        public Step {
            if (key == null || key.isBlank() || state == null) {
                throw new IllegalArgumentException("invalid onboarding step");
            }
        }
    }

    public enum State {
        COMPLETE,
        CURRENT,
        LATER,
        OPTIONAL
    }
}
