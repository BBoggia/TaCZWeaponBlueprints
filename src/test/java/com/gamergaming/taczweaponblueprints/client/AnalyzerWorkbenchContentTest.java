package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.Action;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import com.gamergaming.taczweaponblueprints.progression.fragment.*;
import com.gamergaming.taczweaponblueprints.resource.research.AnalyzerPolicyQuote;
import net.minecraft.resources.ResourceLocation;

class AnalyzerWorkbenchContentTest {
    private static final ResourceLocation TARGET = new ResourceLocation("tacz:ak47");
    private static final Optional<Action> ANALYZE = Optional.of(Action.REVERSE_ENGINEER);
    private static final Optional<Action> RECOVER = Optional.of(Action.RECOVER_POINTS);

    @Test void selectedActionCannotSurviveAnAuthoritativeChangeThatRemovesIt() {
        var p = weapon(new AnalyzerOutcomeTable(30, 70, 0, 1, 2));
        var choice = BlueprintRecyclerScreenModel.from(p, false);
        assertEquals(RECOVER, AnalyzerWorkbenchContent.selected(choice, RECOVER));
        assertEquals(ANALYZE, AnalyzerWorkbenchContent.selected(choice, Optional.of(Action.REDEEM_STACK)));
        assertTrue(AnalyzerWorkbenchContent.selected(BlueprintRecyclerScreenModel.from(BlueprintRecyclerPreview.EMPTY, false), RECOVER).isEmpty());
        assertEquals(RECOVER, AnalyzerWorkbenchContent.selected(BlueprintRecyclerScreenModel.from(p, true), RECOVER));
    }

    @Test void SwitchingToRecoveryChangesBothTheCostAndPredictedReward() {
        var p = weapon(new AnalyzerOutcomeTable(30, 70, 0, 1, 2));
        assertEquals(3, AnalyzerWorkbenchContent.cost(p, ANALYZE).currency());
        assertEquals(7, AnalyzerWorkbenchContent.cost(p, RECOVER).currency());
        assertEquals(1, AnalyzerWorkbenchContent.cost(p, ANALYZE).input());
        assertEquals(2, AnalyzerWorkbenchContent.cost(p, RECOVER).input());
        assertEquals(List.of(AnalyzerWorkbenchContent.Icon.BLUEPRINT, AnalyzerWorkbenchContent.Icon.FRAGMENT), icons(p, ANALYZE));
        assertEquals(List.of(AnalyzerWorkbenchContent.Icon.CURRENCY), icons(p, RECOVER));
    }

    @Test void WeaponOverviewOmitsImpossibleOutcomesButPreservesNoResultChance() {
        assertEquals(List.of(AnalyzerWorkbenchContent.Icon.BLUEPRINT), icons(weapon(new AnalyzerOutcomeTable(100, 0, 0, 0, 0)), ANALYZE));
        var p = weapon(new AnalyzerOutcomeTable(0, 3, 1, 2, 2));
        var overview = AnalyzerWorkbenchContent.overview(p, ANALYZE);
        assertEquals(List.of(AnalyzerWorkbenchContent.Icon.FRAGMENT, AnalyzerWorkbenchContent.Icon.NONE), icons(p, ANALYZE));
        assertEquals("75%", overview.rows().get(0).value().getString());
        assertEquals("25%", overview.rows().get(1).value().getString());
    }

    @Test void ResearchDataShowsAggregateOddsWithoutInventingTargetProgress() {
        var d = new ResearchDataPresentation(1, 3, true, true, false, AnalysisRewardClass.PROTECTED_PREREQUISITES, List.of(
                new ResearchDataPresentation.Station(1, 3, .02, true, true, 1, 2),
                new ResearchDataPresentation.Station(2, 3, .04, true, true, 2, 3),
                new ResearchDataPresentation.Station(3, 3, .06, true, true, 3, 4)));
        var p = data(d);
        var view = AnalyzerWorkbenchContent.overview(p, Optional.of(Action.ANALYZE_DATA));
        assertTrue(view.progress().isEmpty());
        assertEquals("≈4%", view.rows().get(0).value().getString());
        assertEquals("≈96%", view.rows().get(1).value().getString());
        assertEquals(1, AnalyzerWorkbenchContent.cost(p, Optional.of(Action.ANALYZE_DATA)).input());
    }

    @Test void UnavailableDataPoolDoesNotShowFabricatedOutcomes() {
        var d = new ResearchDataPresentation(1, 3, false, false, false, AnalysisRewardClass.PROTECTED_PREREQUISITES, List.of(
                new ResearchDataPresentation.Station(1, 0, 0, false, false, 0, 0),
                new ResearchDataPresentation.Station(2, 0, 0, false, false, 0, 0),
                new ResearchDataPresentation.Station(3, 0, 0, false, false, 0, 0)));
        assertEquals(List.of(AnalyzerWorkbenchContent.Icon.NONE), icons(data(d), Optional.empty()));
    }

    @Test void FragmentOverviewKeepsDepositRetainedInputAndSavedRemainderDistinct() {
        var p = fragments(8, 2, 3, true);
        var view = AnalyzerWorkbenchContent.overview(p, Optional.of(Action.ARCHIVE_FRAGMENTS));
        assertEquals(new AnalyzerWorkbenchContent.Progress(8, 2, 10), view.progress().orElseThrow());
        assertEquals(2, AnalyzerWorkbenchContent.cost(p, Optional.empty()).input());
        assertEquals(3, view.rows().size()); // blueprint, saved after reconstruction, retained input
        assertEquals(AnalyzerWorkbenchContent.Icon.BLUEPRINT, view.rows().get(0).icon());
        assertEquals(0, p.fragmentEvaluation().orElseThrow().archivedAfterAction());
        assertEquals(3, p.fragmentEvaluation().orElseThrow().rejected());
    }

    @Test void CompleteStoredSetDoesNotClaimToConsumeAnyOfferedFragments() {
        var p = fragments(10, 0, 3, true);
        assertEquals(0, AnalyzerWorkbenchContent.cost(p, Optional.of(Action.ARCHIVE_FRAGMENTS)).input());
        assertEquals(3, AnalyzerWorkbenchContent.overview(p, Optional.empty()).rows().size());
    }

    @Test void PartialSetDoesNotPromiseABlueprint() {
        var p = fragments(2, 3, 0, false);
        assertFalse(icons(p, Optional.empty()).contains(AnalyzerWorkbenchContent.Icon.BLUEPRINT));
        assertEquals(1, AnalyzerWorkbenchContent.overview(p, Optional.empty()).rows().size());
    }

    @Test void LegacyStackUsesWideArithmeticForTheMaximumPossibleReward() {
        var p = new BlueprintRecyclerPreview(BlueprintRecyclerPreview.InputKind.RESEARCH_DATA, Optional.of(TARGET), 64,
                1_000_000_000, 0, 1_000_000_000, Optional.empty(), Optional.of(ResearchDataRedemptionService.Status.SUCCESS));
        var selected = Optional.of(Action.REDEEM_STACK);
        assertEquals(64, AnalyzerWorkbenchContent.cost(p, selected).input());
        var label = AnalyzerWorkbenchContent.overview(p, selected).rows().get(0).label();
        assertEquals(64_000_000_000L, ((net.minecraft.network.chat.contents.TranslatableContents) label.getContents()).getArgs()[0]);
    }

    private static List<AnalyzerWorkbenchContent.Icon> icons(BlueprintRecyclerPreview p, Optional<Action> action) {
        return AnalyzerWorkbenchContent.overview(p, action).rows().stream().map(AnalyzerWorkbenchContent.Row::icon).toList();
    }
    private static BlueprintRecyclerPreview weapon(AnalyzerOutcomeTable outcomes) {
        var presentation = new AnalyzerPresentation(1, Optional.of(new AnalyzerPresentation.Weapon(2, AnalyzerPolicyQuote.Status.CONFIGURED,
                Optional.of(outcomes), Optional.of(AnalysisRewardClass.PROTECTED_PREREQUISITES), 0, 0, 10, 1000)), Optional.empty(),
                Optional.of(new AnalyzerPresentation.RecoveryCost(2, 7, List.of(), BlueprintReverseEngineeringService.Status.READY)));
        return new BlueprintRecyclerPreview(BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM, Optional.of(TARGET), 2, 0, 20, 100,
                Optional.empty(), Optional.empty(), 5L, Optional.of(TARGET), 1, 3, true, true, false, false,
                Optional.of(BlueprintReverseEngineeringService.Status.READY), List.of(), BlueprintRecyclerPreview.WeaponOrigin.LOOT_GENERATED,
                12, Optional.of(FoundWeaponRecoveryService.Status.READY), Optional.empty(), presentation);
    }
    private static BlueprintRecyclerPreview data(ResearchDataPresentation d) {
        return new BlueprintRecyclerPreview(BlueprintRecyclerPreview.InputKind.DATA_ANALYSIS, Optional.of(TARGET), 3, 0, 20, 100,
                Optional.empty(), Optional.of(ResearchDataRedemptionService.Status.SUCCESS), 5L, Optional.empty(), 1, 0, true, true,
                false, false, Optional.empty(), List.of(), BlueprintRecyclerPreview.WeaponOrigin.UNKNOWN, 0, Optional.empty(), Optional.empty(),
                AnalyzerPresentation.EMPTY.withStation(2).withData(d));
    }
    private static BlueprintRecyclerPreview fragments(int saved, int incoming, int retained, boolean complete) {
        return BlueprintRecyclerPreview.fragment(new BlueprintFragmentAnalysisService.Evaluation(BlueprintFragmentAnalysisService.Status.READY,
                Optional.of(TARGET), BlueprintFragmentPolicy.CompletionMode.RECONSTRUCT_BLUEPRINT, incoming + retained, incoming, retained,
                saved, saved + incoming, saved + incoming - (complete ? 10 : 0), 10, saved / 10, (saved + incoming) / 10,
                complete, false, 0, 20, 100, complete, 5L));
    }
}
