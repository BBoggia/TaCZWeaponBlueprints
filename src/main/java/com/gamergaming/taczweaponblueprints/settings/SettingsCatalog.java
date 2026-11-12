package com.gamergaming.taczweaponblueprints.settings;

import java.util.*;

/** Stable presentation identities. The fields still belong to the original flat configs. */
public final class SettingsCatalog {
    public enum Category { DISCOVERY, RESEARCH, ECONOMY, ANALYZER, VILLAGERS, STARTING_ACCESS, INTEGRATIONS, PERSONAL, LEGACY }
    /** The crafting switch is one control with a coordinated write, not three independent quick widgets. */
    public enum QuickControl {
        BLUEPRINTS("enableBlueprints"), RESEARCH("enableResearch"), AMMO_RESEARCH("ammoResearch"),
        ATTACHMENT_RESEARCH("attachmentResearch"), RESEARCH_ACCESS("researchAccess"),
        CRAFTING_TIERS("progressionPreset", "customEnforceResearchTiers", "customEnforceCraftingTiers"),
        DISCOVERY("balancePreset"), COSTS("researchCostMode"), GUNSMITH("enableGunsmith"), BALLISTICS_RESEARCHER("enableBallisticsResearcher");
        private final List<String> fields;
        QuickControl(String... fields) { this.fields = List.of(fields); }
        public List<String> fields() { return fields; }
        public String presentationField() { return fields.getFirst(); }
    }
    public record Entry(String field, Category category, SettingsCondition condition, boolean quick, boolean supported) {
        public String labelKey() { return "config.taczweaponblueprints.blueprint." + field; }
        public int permissionLevel() { return 2; }
    }
    private static final Set<String> QUICK = Arrays.stream(QuickControl.values()).map(QuickControl::presentationField).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private static final Map<String, Entry> ENTRIES = build();
    private SettingsCatalog() {}
    public static Map<String, Entry> entries() { return ENTRIES; }
    public static Entry entry(String id) {
        Entry result = ENTRIES.get(id);
        if (result == null) throw new IllegalArgumentException("Unknown settings field: " + id);
        return result;
    }
    private static Map<String, Entry> build() {
        Map<String, Entry> entries = new LinkedHashMap<>();
        add(entries, Category.RESEARCH, "researchAccess enableBlueprints enableDiscoveryTracking enableJournal enableResearch researchCostMode treeResearchResultMode creativeBypassesResearchCost ammoResearch attachmentResearch progressionPreset ammoCraftingStrategy attachmentCraftingStrategy");
        add(entries, Category.DISCOVERY, "balancePreset maximumUndiscoveredVisibility blueprintSpawnChance minBlueprints maxBlueprints");
        add(entries, Category.ECONOMY, "researchCurrencyKind researchCurrencyItem enableScrapLoot scrapLootBudget researchPointCap enableResearchPointAwards enableCombatResearchPointAwards researchDataBehavior researchDataAllowLearned researchDataAllowUndiscovered enableResearchDataLoot");
        add(entries, Category.ANALYZER, "fragmentPreset duplicateBlueprintPolicy allowUnlearnedRecycling foundWeaponRecoveryMode exactFragmentThresholds customFragmentMode tierOneFragmentThreshold tierTwoFragmentThreshold tierThreeFragmentThreshold fragmentRetainedProgressCap fragmentDiscountType fragmentDiscountValue learnedTargetFragmentResearchPoints fragmentLootReplacementPercent");
        add(entries, Category.STARTING_ACCESS, "startingBlueprints progressionExemptBlueprints progressionExemptKinds progressionExemptItemTypes");
        add(entries, Category.INTEGRATIONS, "activeResearchProfile gunBlacklist ammoBlacklist attachmentBlacklist unknownWorkstationFallbackTier unknownExternalWorkstationsUnrestricted externalWorkstationTiers magazineProgressionMode magazineKeepBasicCrafting magazineBasicWorkbenchTier magazineExtendedWorkbenchTier");
        add(entries, Category.RESEARCH, "customEnforceResearchTiers customEnforceCraftingTiers tierOneUpperPercent tierTwoUpperPercent creativeBypassesWorkbenchTiers creativeBypassesProgressionGates linkedAmmoFallbackTier craftingUnrestrictedKinds craftingUnrestrictedItemTypes craftingDisabledKinds craftingDisabledItemTypes exactCraftingOverrides");
        add(entries, Category.VILLAGERS, "enableGunsmith enableBallisticsResearcher villagerMaterialPurchases villagerGunSales villagerAmmoSales villagerAttachmentSales villagerDataSales villagerFragmentSales villagerBlueprintSales villagerMagazineSales villagerExtendedMagazines villagerCurrencyMode villagerFixedCurrency villagerPricePercent villagerStockPercent villagerMinimumPricePercent villagerMaximumGunTier");
        add(entries, Category.ANALYZER, "analyzerSetup singleAnalyzerDataTier");
        return Collections.unmodifiableMap(entries);
    }
    private static void add(Map<String, Entry> entries, Category category, String fields) {
        for (String field : fields.split(" ")) {
            boolean supported = !field.startsWith("magazine") && !Set.of("villagerMagazineSales", "villagerExtendedMagazines").contains(field);
            if (entries.put(field, new Entry(field, category, SettingsCondition.forField(field), QUICK.contains(field), supported)) != null)
                throw new IllegalStateException("Duplicate settings identity: " + field);
        }
    }
}
