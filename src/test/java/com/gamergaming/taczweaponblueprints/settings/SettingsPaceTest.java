package com.gamergaming.taczweaponblueprints.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.loot.ScrapSupplyLootModifier;
import com.gamergaming.taczweaponblueprints.progression.ResearchTierPricing;
import com.gamergaming.taczweaponblueprints.settings.SettingsCatalog.QuickControl;
import com.gamergaming.taczweaponblueprints.settings.SettingsPace.Pace;

class SettingsPaceTest {
    private static SettingsEditSession draft(BlueprintConfig config) {
        return new SettingsEditSession(new SettingsSnapshot(UUID.randomUUID(), "pace", true,
                FzzySettingsAdapter.capture(config),
                SettingsPresentation.Profile.unavailable(config.activeResearchProfile.getUnconditional())));
    }

    private static SettingsPresentation.Summary summary(SettingsEditSession draft) {
        return SettingsPresentation.describe(draft.baseline(), draft.preview());
    }

    @Test
    void aNewServerIsOnTheNormalPace() {
        var draft = draft(new BlueprintConfig());
        assertEquals(Pace.NORMAL, SettingsPace.matching(draft.preview()));
        assertEquals(SettingsValues.token(Pace.NORMAL), QuickSetupSettings.selected(QuickControl.PACE, summary(draft)));
        SettingsPace.apply(draft, Pace.NORMAL);
        assertFalse(draft.dirty(), "choosing the pace you already have changes nothing");
    }

    @Test
    void choosingAPaceEditsOnlyItsFiveSettingsAndTheyTakeEffect() {
        var draft = draft(new BlueprintConfig());
        QuickSetupSettings.select(draft, QuickControl.PACE, SettingsValues.token(Pace.HARD));
        assertEquals(java.util.Set.copyOf(SettingsPace.FIELDS), draft.proposedChanges().keySet());
        assertEquals(Pace.HARD, QuickSetupSettings.pace(summary(draft)));

        var applied = FzzySettingsAdapter.fromValues(draft.preview());
        applied.initializeLoadedSnapshots();
        assertEquals(new ResearchTierPricing(150, 250, 400), applied.progressionSnapshot().tierPricing());
        assertEquals(75, applied.scrapLootPercent.getUnconditional());

        QuickSetupSettings.select(draft, QuickControl.PACE, SettingsValues.token(Pace.RELAXED));
        var relaxed = FzzySettingsAdapter.fromValues(draft.preview());
        relaxed.initializeLoadedSnapshots();
        assertEquals(new ResearchTierPricing(75, 150, 200), relaxed.progressionSnapshot().tierPricing());
        assertEquals(150, relaxed.scrapLootPercent.getUnconditional());
    }

    @Test
    void anyOtherMixOfValuesIsCustomAndCustomCannotBeChosen() {
        Map<String, String> values = new HashMap<>(draft(new BlueprintConfig()).preview());
        values.put("researchCostPercent", SettingsValues.canonical("value = 110"));
        assertEquals(Pace.CUSTOM, SettingsPace.matching(values));
        assertFalse(SettingsPace.choices().contains(Pace.CUSTOM));
        assertThrows(IllegalArgumentException.class, () -> SettingsPace.targets(Pace.CUSTOM));
        assertThrows(IllegalArgumentException.class, () -> QuickSetupSettings.select(draft(new BlueprintConfig()),
                QuickControl.PACE, SettingsValues.token(Pace.CUSTOM)));
    }

    @Test
    void aServerThatKeptItsOldPricesShowsAsCustomUntilItPicksAPace() throws Exception {
        var migrated = SettingsBaselineTest.load("custom-current");
        assertEquals(Pace.CUSTOM, SettingsPace.matching(FzzySettingsAdapter.capture(migrated)));
    }

    @Test
    void scrapLootScalesWholeAmountsAndRollsForTheRemainder() {
        assertEquals(12, ScrapSupplyLootModifier.scaled(12, 100, 0.99F));
        assertEquals(18, ScrapSupplyLootModifier.scaled(12, 150, 0.99F));
        assertEquals(9, ScrapSupplyLootModifier.scaled(12, 75, 0.0F));
        // One Scrap at 75%: three rolls in four keep it, so the average is right for small drops.
        assertEquals(1, ScrapSupplyLootModifier.scaled(1, 75, 0.74F));
        assertEquals(0, ScrapSupplyLootModifier.scaled(1, 75, 0.75F));
        assertEquals(1, ScrapSupplyLootModifier.scaled(1, 150, 0.5F));
        assertEquals(2, ScrapSupplyLootModifier.scaled(1, 150, 0.49F));
        assertTrue(ScrapSupplyLootModifier.scaled(64, ScrapSupplyLootModifier.MAX_PERCENT, 0.0F) > 64,
                "the per-roll limit, not this, caps large amounts");
    }
}
