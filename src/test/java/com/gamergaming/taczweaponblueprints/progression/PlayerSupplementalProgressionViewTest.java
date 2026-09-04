package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressValueMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateGroup;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateScope;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;

import net.minecraft.resources.ResourceLocation;

class PlayerSupplementalProgressionViewTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation VISIBLE = id("test:visible");
    private static final ResourceLocation OTHER = id("test:other");

    @Test
    void viewIncludesOnlyDisclosedFragmentsAndPublicReferencedCriteria() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.commit(VISIBLE.toString(), 0, 8));
        data.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.commit(OTHER.toString(), 0, 11));
        data.applyProgressionCriterionMutation(
                PlayerProgressValueMutation.Request.commit("test:public_trial", 0, 4));
        data.applyProgressionCriterionMutation(
                PlayerProgressValueMutation.Request.commit("test:hidden_trial", 0, 9));
        data.applyProgressionCriterionMutation(
                PlayerProgressValueMutation.Request.commit("test:unreferenced", 0, 7));

        PlayerSupplementalProgressionView view = PlayerSupplementalProgressionView.create(
                data,
                List.of(VISIBLE),
                Map.of(VISIBLE, policy(VISIBLE), OTHER, policy(OTHER)));

        assertEquals(Map.of(VISIBLE.toString(), 8), view.archivedFragments());
        assertEquals(Map.of("test:public_trial", 4), view.publicCriteria());
        assertFalse(view.publicCriteria().containsKey("test:hidden_trial"));
        assertFalse(view.publicCriteria().containsKey("test:unreferenced"));
        assertThrows(UnsupportedOperationException.class,
                () -> view.archivedFragments().put("test:leak", 1));
    }

    @Test
    void missingOrDisabledPoliciesFailClosedWithoutLeakingSavedState() {
        PlayerRecipeData data = new PlayerRecipeData();
        data.applyArchivedFragmentMutation(
                PlayerProgressValueMutation.Request.commit(VISIBLE.toString(), 0, 8));
        data.applyProgressionCriterionMutation(
                PlayerProgressValueMutation.Request.commit("test:public_trial", 0, 4));

        assertEquals(PlayerSupplementalProgressionView.EMPTY,
                PlayerSupplementalProgressionView.create(data, List.of(VISIBLE), Map.of()));
        assertThrows(IllegalArgumentException.class,
                () -> PlayerSupplementalProgressionView.create(
                        data, java.util.Arrays.asList(VISIBLE, null), Map.of()));
    }

    @Test
    void disclosedIdsIncludeVisibleTreeNodesWhenJournalIsDisabled() {
        ResearchTreeGraph graph = new ResearchTreeGraph(
                List.of(new ResearchTreeGraph.Node(
                        0,
                        VISIBLE,
                        "item.test.visible",
                        "rifle",
                        id("test:slot/visible"),
                        JournalVisibility.PREVIEW,
                        false,
                        false,
                        false,
                        8,
                        0,
                        0,
                        0,
                        ResearchTreeGraph.Availability.PREVIEW),
                        new ResearchTreeGraph.Node(
                                1,
                                1,
                                ResearchTreeGraph.redactedNodeId(1),
                                ResearchTreeGraph.REDACTED_NAME_KEY,
                                ResearchTreeGraph.REDACTED_ITEM_TYPE,
                                ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                                JournalVisibility.SILHOUETTE,
                                false,
                                false,
                                false,
                                0,
                                0,
                                0,
                                0,
                                ResearchTreeGraph.Availability.REDACTED)),
                List.of());
        ResearchTreePublication tree = new ResearchTreePublication(
                graph,
                ResearchTreePresentation.EMPTY,
                ResearchTechTreePresentation.EMPTY);

        assertEquals(
                java.util.Set.of(VISIBLE),
                PlayerSupplementalProgressionView.disclosedBlueprintIds(
                        BlueprintJournalSnapshot.EMPTY,
                        tree));
    }

    private static ResolvedBlueprintProgressionPolicy policy(ResourceLocation blueprintId) {
        ProgressionGateRequirements gates = new ProgressionGateRequirements(List.of(
                new ProgressionGateGroup(List.of(
                        ProgressionGateCondition.Criterion.of(
                                "test:public_trial",
                                5,
                                ProgressionGateScope.BOTH,
                                "gate.test.public",
                                ProgressionGateCondition.Disclosure.PUBLIC),
                        ProgressionGateCondition.Criterion.of(
                                "test:hidden_trial",
                                10,
                                ProgressionGateScope.BOTH,
                                "gate.test.hidden",
                                ProgressionGateCondition.Disclosure.HIDDEN)))));
        return new ResolvedBlueprintProgressionPolicy(
                PROFILE,
                blueprintId,
                ResearchWorkbenchTier.TIER_1,
                new BlueprintFragmentPolicy(
                        BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                        5,
                        100,
                        BlueprintFragmentDiscount.percentage(2_500),
                        1),
                gates,
                ResolvedBlueprintProgressionPolicy.TierSource.FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                false);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
