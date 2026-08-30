package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDefinition;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardPresentation;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardRepeat;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver.ResolvedAward;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardReward;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot.Binding;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTarget.Specificity;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;

import net.minecraft.resources.ResourceLocation;

class ResearchPointPresentationServiceTest {
    @Test
    void visibilityControlsNamesButNeverSuppressesTheTruthfulPointTotal() {
        var publicFeedback = feedback(ResearchPointAwardPresentation.Visibility.PUBLIC, true);
        var conditionalHidden = feedback(
                ResearchPointAwardPresentation.Visibility.CONDITIONAL, false);
        var hidden = feedback(ResearchPointAwardPresentation.Visibility.HIDDEN, true);

        assertEquals(List.of("award.test.name"), publicFeedback.namedAwards());
        assertEquals(0, publicFeedback.genericAwardCount());
        assertEquals(4, conditionalHidden.awardedPoints());
        assertEquals(1, conditionalHidden.genericAwardCount());
        assertTrue(conditionalHidden.namedAwards().isEmpty());
        assertEquals(1, hidden.genericAwardCount());
        assertTrue(hidden.namedAwards().isEmpty());
    }

    @Test
    void capClaimsRemainPresentWithoutPretendingPointsWereAdded() {
        ResolvedAward award = resolved(ResearchPointAwardPresentation.Visibility.HIDDEN);
        var result = new ResearchPointAwardService.AwardResult(
                new ResourceLocation("test:award"),
                ResearchPointAwardService.Status.LEDGER_RECORDED_AT_CAP,
                4,
                0);

        var feedback = ResearchPointPresentationService.feedback(award, result, false);

        assertTrue(feedback.present());
        assertTrue(feedback.claimedAtCap());
        assertEquals(0, feedback.awardedPoints());
    }

    @Test
    void combiningFeedbackIsBoundedAndIgnoresEmptyResults() {
        var combined = ResearchPointPresentationService.combine(List.of(
                ResearchPointPresentationService.Feedback.EMPTY,
                new ResearchPointPresentationService.Feedback(
                        2, 1, false, List.of("award.test.one")),
                new ResearchPointPresentationService.Feedback(
                        3, 0, true, List.of("award.test.one", "award.test.two"))));

        assertEquals(5, combined.awardedPoints());
        assertEquals(List.of("award.test.one", "award.test.two"), combined.namedAwards());
        assertEquals(1, combined.genericAwardCount());
        assertTrue(combined.claimedAtCap());
        assertFalse(ResearchPointPresentationService.combine(List.of()).present());
    }

    @Test
    void claimedFiniteAwardIsNoLongerPresentedAsAvailableHelp() {
        ResolvedAward award = finiteResolved();
        ResourceLocation definitionId = award.binding().definitionId();
        var definition = award.binding().definition();
        var data = new com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData();
        ResourceLocation profile = new ResourceLocation("test:profile");
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                ResearchPointAwardTrigger.Type.ADVANCEMENT_COMPLETED,
                profile,
                new ResourceLocation("minecraft:story/mine_iron"));

        assertFalse(ResearchPointPresentationService.finiteAwardExhausted(
                data, definitionId, definition));
        ResearchPointAwardService.AwardResult result = ResearchPointAwardService.awardOne(
                data,
                award,
                context,
                new ResearchPointAwardConfigSnapshot(true, false, 100, profile),
                10L);

        assertTrue(result.committed());
        assertTrue(ResearchPointPresentationService.finiteAwardExhausted(
                data, definitionId, definition));
    }

    private static ResearchPointPresentationService.Feedback feedback(
            ResearchPointAwardPresentation.Visibility visibility,
            boolean conditionalVisible) {
        ResolvedAward award = resolved(visibility);
        var result = new ResearchPointAwardService.AwardResult(
                new ResourceLocation("test:award"),
                ResearchPointAwardService.Status.AWARDED,
                4,
                4);
        return ResearchPointPresentationService.feedback(award, result, conditionalVisible);
    }

    private static ResolvedAward resolved(ResearchPointAwardPresentation.Visibility visibility) {
        ResearchPointAwardDefinition definition = new ResearchPointAwardDefinition(
                1,
                true,
                List.of(),
                new ResourceLocation("test:group"),
                0,
                new ResearchPointAwardTrigger(
                        ResearchPointAwardTrigger.Type.ADVANCEMENT_COMPLETED,
                        Optional.empty(),
                        false,
                        Optional.empty(),
                        Optional.empty()),
                new ResearchPointAwardReward(4, ResearchPointAwardReward.Overflow.CLAMP),
                new ResearchPointAwardRepeat(
                        ResearchPointAwardRepeat.Type.UNLIMITED,
                        Optional.empty(),
                        ResearchPointAwardRepeat.Scope.DEFINITION,
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty(),
                new ResearchPointAwardPresentation(
                        visibility,
                        visibility == ResearchPointAwardPresentation.Visibility.HIDDEN
                                ? Optional.empty()
                                : Optional.of("award.test.name")));
        return new ResolvedAward(
                new Binding(new ResourceLocation("test:award"), definition),
                Specificity.GENERIC);
    }

    private static ResolvedAward finiteResolved() {
        ResearchPointAwardDefinition definition = new ResearchPointAwardDefinition(
                1,
                true,
                List.of(),
                new ResourceLocation("test:finite_group"),
                0,
                new ResearchPointAwardTrigger(
                        ResearchPointAwardTrigger.Type.ADVANCEMENT_COMPLETED,
                        Optional.empty(),
                        false,
                        Optional.empty(),
                        Optional.empty()),
                new ResearchPointAwardReward(4, ResearchPointAwardReward.Overflow.CLAMP),
                new ResearchPointAwardRepeat(
                        ResearchPointAwardRepeat.Type.ONCE,
                        Optional.of(new ResourceLocation("test:finite_claim")),
                        ResearchPointAwardRepeat.Scope.DEFINITION,
                        Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()),
                Optional.empty(),
                new ResearchPointAwardPresentation(
                        ResearchPointAwardPresentation.Visibility.PUBLIC,
                        Optional.of("award.test.finite")));
        return new ResolvedAward(
                new Binding(new ResourceLocation("test:finite_award"), definition),
                Specificity.GENERIC);
    }
}
