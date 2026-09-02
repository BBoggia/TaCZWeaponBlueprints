package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpEntry;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService.HelpSnapshot;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class BlueprintOnboardingPlanTest {
    @Test
    void freshProgressionPointsToDisclosureSafeEarningHelp() {
        HelpEntry first = help("first");
        BlueprintOnboardingPlan plan = BlueprintOnboardingPlan.from(
                BlueprintJournalSnapshot.EMPTY,
                new HelpSnapshot(1L, List.of(first, help("second"), help("third"), help("fourth"))));

        assertEquals(BlueprintOnboardingPlan.State.COMPLETE, plan.steps().get(0).state());
        assertEquals(BlueprintOnboardingPlan.State.CURRENT, plan.steps().get(1).state());
        assertEquals(BlueprintOnboardingPlan.State.LATER, plan.steps().get(2).state());
        assertEquals(3, plan.earningHelp().size());
        assertEquals(first, plan.earningHelp().get(0));
    }

    @Test
    void learnedKnowledgeCompletesTheCorePathWithoutChangingOptionalAnalyzer() {
        BlueprintJournalEntry learned = new BlueprintJournalEntry(
                0,
                JournalVisibility.FULL,
                Optional.of(new ResourceLocation("test:learned")),
                Optional.of("item.test.learned"),
                Optional.of("rifle"),
                Optional.of(new ResourceLocation("test:display")),
                true, true, false, false, false,
                0, 0, 0, 0);
        BlueprintJournalSnapshot journal = new BlueprintJournalSnapshot(
                List.of(learned), List.of(), 0, 100, 1, 1, 0);
        BlueprintOnboardingPlan plan = BlueprintOnboardingPlan.from(journal, HelpSnapshot.EMPTY);

        assertEquals(BlueprintOnboardingPlan.State.COMPLETE, plan.steps().get(1).state());
        assertEquals(BlueprintOnboardingPlan.State.COMPLETE, plan.steps().get(2).state());
        assertEquals(BlueprintOnboardingPlan.State.COMPLETE, plan.steps().get(3).state());
        assertEquals(BlueprintOnboardingPlan.State.OPTIONAL, plan.steps().get(4).state());
    }

    private static HelpEntry help(String name) {
        return new HelpEntry(
                "guide." + name,
                ResearchPointAwardTrigger.Type.ADVANCEMENT_COMPLETED,
                1);
    }
}
