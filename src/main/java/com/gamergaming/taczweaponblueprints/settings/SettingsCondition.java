package com.gamergaming.taczweaponblueprints.settings;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchDataPolicy;
import com.gamergaming.taczweaponblueprints.compat.magazines.MagazineProgressionSettings;

/** Presentation conditions mirror the existing public controls; they never alter stored values. */
public enum SettingsCondition {
    ALWAYS(""), BLUEPRINTS("condition.blueprintsEnabled"), RESEARCH("condition.researchEnabled"),
    DISCOVERY("condition.customDiscovery"), PHYSICAL_CURRENCY("condition.physicalCurrency"),
    PROGRESSION("condition.customProgression"), FRAGMENTS("condition.customFragments"),
    SINGLE_ANALYZER("condition.singleAnalyzer"), CURRENCY_LOOT("condition.currencyLoot"),
    BANKED("researchPointCap.banked_only"), AWARDS("condition.researchPointAwards"), DATA("condition.dataAnalysis"),
    RECYCLING("condition.recyclingEnabled"), LINKED_AMMO("condition.linkedAmmo"), LINKED_MAGAZINES("condition.linkedMagazines");
    private final String key;
    SettingsCondition(String suffix) { key = suffix.isEmpty() ? "" : "config.taczweaponblueprints.blueprint." + suffix; }
    public String reasonKey() { return key; }
    public boolean active(BlueprintConfig c) {
        return switch (this) {
            case ALWAYS -> true;
            case BLUEPRINTS -> c.enableBlueprints.get();
            case RESEARCH -> c.enableBlueprints.get() && c.enableResearch.getUnconditional();
            case DISCOVERY -> c.enableBlueprints.get() && c.balancePreset.get() == BlueprintBalancePreset.CUSTOM;
            case PHYSICAL_CURRENCY -> c.researchCurrencyKind.get() == ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM;
            case PROGRESSION -> c.enableBlueprints.get() && c.progressionPreset.getUnconditional() == ResearchProgressionPreset.CUSTOM;
            case FRAGMENTS -> c.enableBlueprints.get() && c.fragmentPreset.getUnconditional() == BlueprintFragmentPreset.CUSTOM;
            case SINGLE_ANALYZER -> c.analyzerSetup.get() == StationSetup.AnalyzerMode.SINGLE;
            case CURRENCY_LOOT -> PHYSICAL_CURRENCY.active(c) && c.enableScrapLoot.getUnconditional();
            case BANKED -> c.enableBlueprints.get() && c.researchCurrencyKind.get() == ResearchCurrencyPolicy.Kind.BANKED_RP;
            case AWARDS -> c.enableBlueprints.get() && c.enableResearchPointAwards.getUnconditional();
            case DATA -> c.researchDataBehavior.get() == ResearchDataPolicy.Behavior.ANALYSIS;
            case RECYCLING -> c.enableBlueprints.get() && c.duplicateBlueprintPolicy.getUnconditional() == DuplicateBlueprintPolicy.MANUAL_RECYCLING;
            case LINKED_AMMO -> c.ammoCraftingStrategy.get() == AmmoCraftingStrategy.LINKED_WEAPON;
            case LINKED_MAGAZINES -> c.magazineProgressionMode.get() == MagazineProgressionSettings.Mode.LINKED_WEAPON;
        };
    }
    public static SettingsCondition forField(String field) {
        return switch (field) {
            case "singleAnalyzerDataTier" -> SINGLE_ANALYZER;
            case "enableDiscoveryTracking", "enableJournal", "enableResearch", "enableResearchPointAwards", "progressionPreset", "fragmentPreset", "duplicateBlueprintPolicy", "foundWeaponRecoveryMode", "activeResearchProfile" -> BLUEPRINTS;
            case "maximumUndiscoveredVisibility", "blueprintSpawnChance", "minBlueprints", "maxBlueprints" -> DISCOVERY;
            case "researchCostMode", "researchCostPercent", "tierTwoResearchCostPercent", "tierThreeResearchCostPercent", "treeResearchResultMode", "creativeBypassesResearchCost" -> RESEARCH;
            case "researchCurrencyItem", "enableScrapLoot" -> PHYSICAL_CURRENCY;
            case "scrapLootBudget", "scrapLootPercent" -> CURRENCY_LOOT;
            case "researchPointCap" -> BANKED;
            case "enableCombatResearchPointAwards" -> AWARDS;
            case "researchDataAllowLearned", "researchDataAllowUndiscovered" -> DATA;
            case "allowUnlearnedRecycling" -> RECYCLING;
            case "customEnforceResearchTiers", "customEnforceCraftingTiers" -> PROGRESSION;
            case "linkedAmmoFallbackTier" -> LINKED_AMMO;
            case "magazineKeepBasicCrafting" -> LINKED_MAGAZINES;
            case "customFragmentMode", "tierOneFragmentThreshold", "tierTwoFragmentThreshold", "tierThreeFragmentThreshold", "fragmentRetainedProgressCap", "fragmentDiscountType", "fragmentDiscountValue", "learnedTargetFragmentResearchPoints" -> FRAGMENTS;
            default -> ALWAYS;
        };
    }
}
