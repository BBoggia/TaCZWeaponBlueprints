package com.gamergaming.taczweaponblueprints.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class SettingsRecipesTest {
    SettingsEditSession draft(BlueprintConfig config) {
        return new SettingsEditSession(new SettingsSnapshot(UUID.randomUUID(), "recipes", true, FzzySettingsAdapter.capture(config),
                SettingsPresentation.Profile.unavailable(config.activeResearchProfile.getUnconditional())));
    }
    @Test void recipesHaveExhaustiveIndependentAllowlists() {
        assertEquals(Set.of("enableBlueprints", "enableResearch", "researchAccess", "analyzerSetup", "progressionPreset", "ammoResearch", "attachmentResearch", "balancePreset", "researchCostMode", "enableGunsmith", "enableBallisticsResearcher"), SettingsRecipes.targets(SettingsRecipes.Recipe.RECOMMENDED).keySet());
        assertEquals(Set.of("researchAccess", "analyzerSetup"), SettingsRecipes.targets(SettingsRecipes.Recipe.SIMPLER_STATIONS).keySet());
    }
    @ParameterizedTest @ValueSource(strings = {"defaults", "custom-current", "custom-currency", "banked-rp"})
    void previewCancelAndRepeatedQueuePreserveEveryUnrelatedValue(String fixture) throws Exception {
        var config = SettingsBaselineTest.load(fixture); var before = FzzySettingsAdapter.capture(config);
        for (var recipe : SettingsRecipes.Recipe.values()) {
            var draft = draft(config); var preview = SettingsRecipes.preview(draft, recipe);
            assertEquals(before, draft.preview()); assertFalse(draft.dirty());
            SettingsRecipes.queue(draft, preview);
            var expected = new LinkedHashMap<>(before); expected.putAll(SettingsRecipes.targets(recipe));
            assertEquals(expected, draft.preview()); assertEquals(before, FzzySettingsAdapter.capture(config));
            var queued = draft.proposedChanges(); var again = SettingsRecipes.preview(draft, recipe);
            assertTrue(again.changes().isEmpty()); SettingsRecipes.queue(draft, again); assertEquals(queued, draft.proposedChanges());
            var prepared = FzzySettingsAdapter.prepare(config, queued);
            assertEquals(expected, FzzySettingsAdapter.capture(prepared));
        }
    }
    @Test void simplerStationsKeepsResearchOffCraftingRulesAndDataRewardTier() {
        var config = new BlueprintConfig(); config.enableResearch.validateAndSet(false);
        config.singleAnalyzerDataTier.validateAndSet(com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier.TIER_3);
        var draft = draft(config); SettingsRecipes.queue(draft, SettingsRecipes.preview(draft, SettingsRecipes.Recipe.SIMPLER_STATIONS));
        assertEquals(Set.of("researchAccess", "analyzerSetup"), draft.proposedChanges().keySet());
        var after = FzzySettingsAdapter.fromValues(draft.preview());
        assertFalse(after.enableResearch.getUnconditional());
        assertEquals(config.progressionPreset.getUnconditional(), after.progressionPreset.getUnconditional());
        assertEquals(config.singleAnalyzerDataTier.get(), after.singleAnalyzerDataTier.get());
    }
    @Test void overlappingPendingChangesAreExplicitAndUnrelatedPendingChangesSurvive() {
        var draft = draft(new BlueprintConfig());
        draft.setChoice("researchAccess", StationSetup.ResearchAccess.PORTABLE); draft.setBoolean("enableJournal", false);
        var proposal = SettingsRecipes.preview(draft, SettingsRecipes.Recipe.RECOMMENDED);
        assertEquals(1, proposal.changes().size()); assertTrue(proposal.changes().getFirst().replacesPending());
        SettingsRecipes.queue(draft, proposal);
        assertEquals(Map.of("enableJournal", SettingsValues.bool(false)), draft.proposedChanges());
    }
    @Test void aPreviewCannotBeQueuedAfterTheDraftChangesOrAgainstAnotherSession() {
        var draft = draft(new BlueprintConfig()); var proposal = SettingsRecipes.preview(draft, SettingsRecipes.Recipe.SIMPLER_STATIONS);
        draft.setBoolean("enableJournal", false); var changed = draft.proposedChanges();
        assertThrows(IllegalStateException.class, () -> SettingsRecipes.queue(draft, proposal)); assertEquals(changed, draft.proposedChanges());
        assertThrows(IllegalStateException.class, () -> SettingsRecipes.queue(draft(new BlueprintConfig()), proposal));
    }
    @Test void frozenAndReadOnlyDraftsCannotQueueRecipes() {
        var draft = draft(new BlueprintConfig()); var proposal = SettingsRecipes.preview(draft, SettingsRecipes.Recipe.SIMPLER_STATIONS); draft.freeze();
        assertThrows(IllegalStateException.class, () -> SettingsRecipes.queue(draft, proposal)); assertFalse(draft.dirty());
        var old = draft.baseline(); var readOnly = new SettingsEditSession(new SettingsSnapshot(old.session(), old.revision(), false, old.values(), old.profile()));
        assertThrows(IllegalStateException.class, () -> SettingsRecipes.queue(readOnly, SettingsRecipes.preview(readOnly, SettingsRecipes.Recipe.SIMPLER_STATIONS)));
        assertFalse(readOnly.dirty());
    }
    @Test void invalidBatchDoesNotPartiallyQueueEarlierFields() {
        var draft = draft(new BlueprintConfig()); Map<String, String> proposal = new LinkedHashMap<>();
        proposal.put("enableResearch", SettingsValues.bool(false)); proposal.put("madeUpField", SettingsValues.bool(true));
        assertThrows(IllegalArgumentException.class, () -> draft.setAll(proposal)); assertFalse(draft.dirty());
    }
    @Test void setupMatchIsDerivedAndDoesNotClaimUnrelatedSettingsAreDefault() {
        var draft = draft(new BlueprintConfig()); draft.setBoolean("enableJournal", false);
        assertEquals(Optional.of(SettingsRecipes.Recipe.RECOMMENDED), SettingsRecipes.matching(draft.preview()));
        draft.setChoice("researchAccess", StationSetup.ResearchAccess.PORTABLE);
        assertTrue(SettingsRecipes.matching(draft.preview()).isEmpty());
        SettingsRecipes.queue(draft, SettingsRecipes.preview(draft, SettingsRecipes.Recipe.SIMPLER_STATIONS));
        assertEquals(Optional.of(SettingsRecipes.Recipe.SIMPLER_STATIONS), SettingsRecipes.matching(draft.preview()));
        assertFalse(SettingsValues.booleanValue(draft.preview().get("enableJournal")));
    }
}
