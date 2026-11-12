package com.gamergaming.taczweaponblueprints.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.network.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.DomainPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class SettingsCompatibilityMatrixTest {
    private SettingsSnapshot snapshot(BlueprintConfig config, boolean available, boolean allowed) {
        return new SettingsSnapshot(UUID.randomUUID(), "matrix", true, FzzySettingsAdapter.capture(config),
                new SettingsPresentation.Profile(config.activeResearchProfile.getUnconditional(), available,
                        new DomainPolicy(allowed, allowed), new DomainPolicy(allowed, allowed), new DomainPolicy(allowed, allowed)));
    }
    @ParameterizedTest @EnumSource(ResearchDomainMode.class)
    void emptyAndMissingProfilesAndDisabledResearchAreNotGuessed(ResearchDomainMode mode) {
        var config = new BlueprintConfig(); config.ammoResearch.validateAndSet(mode);
        var missing = snapshot(config, false, false);
        assertTrue(SettingsPresentation.describe(missing, missing.values()).controls().get("ammoResearch").effective().isEmpty());
        var empty = snapshot(config, true, false);
        assertEquals(SettingsValues.bool(mode == ResearchDomainMode.ENABLED), SettingsPresentation.describe(empty, empty.values()).controls().get("ammoResearch").effective().orElseThrow());
        config.enableResearch.validateAndSet(false); var disabled = snapshot(config, true, true);
        assertEquals(SettingsValues.bool(false), SettingsPresentation.describe(disabled, disabled.values()).controls().get("ammoResearch").effective().orElseThrow());
        assertEquals(SettingsPresentation.Availability.INACTIVE, SettingsPresentation.describe(disabled, disabled.values()).controls().get("ammoResearch").availability());
    }
    @ParameterizedTest @ValueSource(strings = {"defaults", "custom-current", "banked-rp", "custom-currency", "legacy-zero", "legacy-two", "legacy-three"})
    void quickRecipesPreserveTheExistingMigrationResult(String fixture) throws Exception {
        var config = SettingsBaselineTest.load(fixture);
        for (var recipe : SettingsRecipes.Recipe.values()) {
            var before = FzzySettingsAdapter.capture(config); var edit = new SettingsEditSession(snapshot(config, false, false));
            SettingsRecipes.queue(edit, SettingsRecipes.preview(edit, recipe));
            var saved = FzzySettingsAdapter.prepare(config, edit.proposedChanges());
            var restored = me.fzzyhmstrs.fzzy_config.api.ConfigApi.deserializeConfig(new BlueprintConfig(), FzzySettingsAdapter.serialize(saved)).get();
            restored.initializeLoadedSnapshots();
            var expected = new LinkedHashMap<>(before); expected.putAll(SettingsRecipes.targets(recipe));
            assertEquals(expected, FzzySettingsAdapter.capture(restored));
            assertEquals(before, FzzySettingsAdapter.capture(config));
        }
    }
    @Test void largeUnknownSelectorsSurviveSmallEditsWithoutBeingResentInThePatch() {
        var config = new BlueprintConfig();
        var selectors = java.util.stream.IntStream.range(0, 1000).mapToObj(i -> "missing_pack:weapon_" + i).toList();
        config.startingBlueprints.validateAndSet(new java.util.LinkedHashSet<>(selectors));
        var before = snapshot(config, true, true); var draft = new SettingsEditSession(before);
        draft.setBoolean("enableGunsmith", false);
        assertEquals(1, draft.plan().changes().size());
        var packet = new SettingsRequestPacket(UUID.randomUUID(), SettingsRequestPacket.Action.APPLY, draft.plan());
        assertNotNull(packet);
        assertTrue(new SettingsReplyPacket(UUID.randomUUID(), SettingsTransaction.Status.READY, before).withinLimit());
        var saved = FzzySettingsAdapter.prepare(config, draft.proposedChanges());
        assertEquals(new java.util.LinkedHashSet<>(selectors), saved.startingBlueprints.get());
        assertEquals(FzzySettingsAdapter.capture(saved).get("startingBlueprints"), FzzySettingsAdapter.captureField(saved, "startingBlueprints"));
        assertEquals(SettingsValues.bool(false), FzzySettingsAdapter.captureField(saved, "enableGunsmith"));
    }
    @Test void exactRuleConflictsRemainVisibleAndUnmodifiedByGeneralControls() throws Exception {
        var config = SettingsBaselineTest.load("custom-current");
        var snapshot = snapshot(config, true, true); var draft = new SettingsEditSession(snapshot);
        var before = SettingsPresentation.describe(snapshot, snapshot.values()); assertTrue(before.specialCraftingRules());
        draft.setCraftingTiers(!before.craftingTiers());
        assertEquals(snapshot.values().get("exactCraftingOverrides"), draft.preview().get("exactCraftingOverrides"));
        assertTrue(SettingsPresentation.describe(snapshot, draft.preview()).specialCraftingRules());
    }
}
