package com.gamergaming.taczweaponblueprints.settings;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.util.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.DomainPolicy;
import com.gamergaming.taczweaponblueprints.settings.SettingsCatalog.QuickControl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;

class QuickSetupSettingsTest {
    private SettingsEditSession draft(BlueprintConfig config) {
        var yes = new DomainPolicy(true, true); var no = new DomainPolicy(false, false);
        return new SettingsEditSession(new SettingsSnapshot(UUID.randomUUID(), "quick-fixture", true,
                FzzySettingsAdapter.capture(config), new SettingsPresentation.Profile(config.activeResearchProfile.getUnconditional(), true, yes, no, no)));
    }
    private SettingsPresentation.Summary summary(SettingsEditSession draft) { return SettingsPresentation.describe(draft.baseline(), draft.preview()); }
    @Test void enablingCategoryResearchDoesNotTouchAnyCraftingRules() {
        var draft = draft(new BlueprintConfig());
        QuickSetupSettings.select(draft, QuickControl.AMMO_RESEARCH, SettingsValues.token(ResearchDomainMode.ENABLED));
        QuickSetupSettings.select(draft, QuickControl.ATTACHMENT_RESEARCH, SettingsValues.token(ResearchDomainMode.ENABLED));
        assertEquals(Set.of("ammoResearch", "attachmentResearch"), draft.proposedChanges().keySet());
        for (String field : draft.proposedChanges().keySet()) assertEquals(SettingsValues.bool(true), summary(draft).controls().get(field).effective().orElseThrow());
        assertEquals(SettingsValues.bool(false), SettingsPresentation.describe(draft.baseline(), draft.baseline().values()).controls().get("ammoResearch").effective().orElseThrow());
    }
    @ParameterizedTest @EnumSource(ResearchProgressionPreset.class)
    void independentBenchAndCraftingChoicesKeepTheInheritedResearchRule(ResearchProgressionPreset preset) {
        var config = new BlueprintConfig();
        config.progressionPreset.validateAndSet(preset); config.customEnforceResearchTiers.validateAndSet(false);
        config.researchAccess.validateAndSet(StationSetup.ResearchAccess.PRESET);
        var draft = draft(config);
        assertEquals(SettingsValues.token(StationSetup.ResearchAccess.PRESET), QuickSetupSettings.selected(QuickControl.RESEARCH_ACCESS, summary(draft)));
        boolean crafting = summary(draft).craftingTiers();
        QuickSetupSettings.select(draft, QuickControl.RESEARCH_ACCESS, SettingsValues.token(StationSetup.ResearchAccess.SINGLE));
        assertEquals(Set.of("researchAccess"), draft.proposedChanges().keySet());
        assertEquals(crafting, summary(draft).craftingTiers());
        QuickSetupSettings.select(draft, QuickControl.CRAFTING_TIERS, SettingsValues.bool(!crafting));
        assertEquals(preset.enforcesResearch(false), SettingsValues.booleanValue(draft.preview().get("customEnforceResearchTiers")));
        assertEquals(SettingsValues.token(StationSetup.ResearchAccess.SINGLE), draft.preview().get("researchAccess"));
        // Going back to inherited access proves that the underlying research component survived.
        QuickSetupSettings.select(draft, QuickControl.RESEARCH_ACCESS, SettingsValues.token(StationSetup.ResearchAccess.PRESET));
        assertEquals(preset.enforcesResearch(false), summary(draft).researchTiers());
    }
    @ParameterizedTest @ValueSource(strings = {"defaults", "banked-rp", "custom-currency"})
    void costChoicesRetainCurrencyIdentityAndAllUnrelatedValues(String fixture) throws Exception {
        var config = SettingsBaselineTest.load(fixture); var draft = draft(config);
        for (var mode : ResearchCostMode.values()) {
            QuickSetupSettings.select(draft, QuickControl.COSTS, SettingsValues.token(mode));
            var expected = new LinkedHashMap<>(draft.baseline().values()); expected.put("researchCostMode", SettingsValues.token(mode));
            assertEquals(expected, draft.preview());
            assertEquals(config.researchCurrencyKind.get().name(), summary(draft).currencyKind());
            assertEquals(config.researchCurrencyItem.getUnconditional(), summary(draft).currencyItem());
        }
    }
    @Test void discoveryRoundTripRestoresTheExactCustomBehavior() {
        var config = new BlueprintConfig(); config.balancePreset.validateAndSet(BlueprintBalancePreset.CUSTOM);
        config.blueprintSpawnChance.validateAndSet(.731); config.minBlueprints.validateAndSet(2); config.maxBlueprints.validateAndSet(7);
        var draft = draft(config); var before = summary(draft).discovery();
        for (var preset : BlueprintBalancePreset.values()) QuickSetupSettings.select(draft, QuickControl.DISCOVERY, SettingsValues.token(preset));
        QuickSetupSettings.select(draft, QuickControl.DISCOVERY, SettingsValues.token(BlueprintBalancePreset.CUSTOM));
        assertFalse(draft.dirty()); assertEquals(before, summary(draft).discovery());
    }
    @Test void unavailableProfilesInactiveParentsAndReadOnlySnapshotsCannotBeEdited() {
        var config = new BlueprintConfig(); var draft = draft(config);
        draft.set("activeResearchProfile", "value = \"pack:missing\"");
        assertTrue(summary(draft).controls().get("ammoResearch").effective().isEmpty());
        var missing = draft;
        assertThrows(IllegalStateException.class, () -> QuickSetupSettings.select(missing, QuickControl.AMMO_RESEARCH, SettingsValues.token(ResearchDomainMode.ENABLED)));
        config.enableResearch.validateAndSet(false); draft = draft(config); var inactive = draft;
        assertThrows(IllegalStateException.class, () -> QuickSetupSettings.select(inactive, QuickControl.COSTS, SettingsValues.token(ResearchCostMode.ITEMS_ONLY)));
        var snapshot = draft.baseline();
        var readOnly = new SettingsEditSession(new SettingsSnapshot(snapshot.session(), snapshot.revision(), false, snapshot.values(), snapshot.profile()));
        assertThrows(IllegalStateException.class, () -> QuickSetupSettings.select(readOnly, QuickControl.GUNSMITH, SettingsValues.bool(false)));
        assertFalse(readOnly.dirty());
    }
    @ParameterizedTest @ValueSource(strings = {"defaults", "custom-current", "banked-rp", "custom-currency"})
    void viewingEveryChoicePreservesComplexConfigsAndDisplaysAValidStoredSelection(String fixture) throws Exception {
        var config = SettingsBaselineTest.load(fixture); var before = FzzySettingsAdapter.capture(config); var draft = draft(config);
        for (var control : QuickControl.values()) assertTrue(QuickSetupSettings.choices(control).contains(QuickSetupSettings.selected(control, summary(draft))), control.name());
        assertFalse(draft.dirty()); assertEquals(before, draft.preview()); assertEquals(before, FzzySettingsAdapter.capture(config));
    }
    @Test void everyQuickLabelAndChoiceHasAnEnglishTranslation() throws Exception {
        var json = com.google.gson.JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/taczweaponblueprints/lang/en_us.json"))).getAsJsonObject();
        for (var control : QuickControl.values()) {
            assertTrue(json.has(QuickSetupSettings.labelKey(control)));
            for (String choice : QuickSetupSettings.choices(control)) assertTrue(json.has(QuickSetupSettings.choiceKey(control, choice)), control + choice);
        }
    }
}
