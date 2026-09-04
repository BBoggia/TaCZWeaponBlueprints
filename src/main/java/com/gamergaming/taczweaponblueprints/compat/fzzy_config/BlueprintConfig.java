package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.BlueprintAccessConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalanceSettings;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.BlueprintFragmentDiscountType;
import com.gamergaming.taczweaponblueprints.progression.BlueprintFragmentPreset;
import com.gamergaming.taczweaponblueprints.progression.AmmoCraftingStrategy;
import com.gamergaming.taczweaponblueprints.progression.AttachmentCraftingStrategy;
import com.gamergaming.taczweaponblueprints.progression.CraftingAccessOverride;
import com.gamergaming.taczweaponblueprints.progression.ResearchFeatureConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchProgressionPreset;
import com.gamergaming.taczweaponblueprints.progression.StartingBlueprintGrantService;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardReconciliationScheduler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.progression.FoundWeaponRecoveryMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.TreeResearchResultMode;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchTierResolver;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.tacz.guns.inventory.GunSmithTableMenu;

import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.annotations.WithPerms;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigGroup;
import me.fzzyhmstrs.fzzy_config.event.api.ServerUpdateContext;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedChoiceList;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedSet;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedStringMap;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedDouble;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraftforge.fml.loading.FMLPaths;

@Version(version = 4)
@Translation(prefix = ModConfigs.BASE_KEY + "blueprint")
public class BlueprintConfig extends Config {
    public static final int MAX_BLUEPRINTS_PER_LOOT_CONTAINER = 64;
    /** Practical operator-facing limit; persisted player balances retain the larger protocol bound. */
    public static final int MAX_CONFIGURED_RESEARCH_POINT_CAP = 100_000;
    public static final int MAX_CONFIGURED_FRAGMENT_THRESHOLD = 10_000;
    public static final int MAX_CONFIGURED_FRAGMENT_PROGRESS = 100_000;
    public static final net.minecraft.resources.ResourceLocation DEFAULT_RESEARCH_PROFILE =
            TaCZWeaponBlueprints.loc("duplicate_recovery");
    private static final String RESOURCE_LOCATION_PATTERN =
            "(?=.{1," + PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH + "}$)"
                    + "[a-z0-9_.-]+:[a-z0-9/._-]+";
    private static final Pattern PERSISTED_BALANCE_PRESET = Pattern.compile(
            "(?m)^\\s*balancePreset\\s*=\\s*\"([A-Z_]+)\"\\s*(?:#.*)?$");
    private static final String TRANSLATION_PREFIX = ModConfigs.BASE_KEY + "blueprint.";
    private static final String ITEM_TYPE_PATTERN = "[a-z0-9_.-]{1,256}";
    private static final List<String> BLUEPRINT_KINDS = List.of("gun", "ammo", "attachment");
    private static final List<String> STANDARD_BLUEPRINT_ITEM_TYPES = List.of(
            "rifle",
            "pistol",
            "sniper",
            "shotgun",
            "smg",
            "rpg",
            "mg",
            "ammo",
            "scope",
            "muzzle",
            "stock",
            "grip",
            "laser",
            "extended_mag");

    public ConfigGroup generalProgression = new ConfigGroup("generalProgression");
    @WithPerms(opLevel = 2)
    public ValidatedBoolean enableBlueprints = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Boolean> enableDiscoveryTracking = blueprintsEnabled(
            new ValidatedBoolean(true));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Boolean> enableJournal = blueprintsEnabled(
            new ValidatedBoolean(true));

    @ConfigGroup.Pop
    @WithPerms(opLevel = 2)
    public ValidatedCondition<Boolean> enableResearch = blueprintsEnabled(
            new ValidatedBoolean(true));

    public ConfigGroup discoveryAndLoot = new ConfigGroup("discoveryAndLoot");
    @WithPerms(opLevel = 2)
    public ValidatedEnum<BlueprintBalancePreset> balancePreset =
            new ValidatedEnum<>(BlueprintBalancePreset.BALANCED, ValidatedEnum.WidgetType.CYCLING);

    @WithPerms(opLevel = 2)
    public ValidatedCondition<JournalVisibility> maximumUndiscoveredVisibility = customDiscovery(
            new ValidatedEnum<>(JournalVisibility.FULL));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Double> blueprintSpawnChance = customDiscovery(
            new ValidatedDouble(0.2, 1.0, 0.0));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Integer> minBlueprints = customDiscovery(
            new ValidatedInt(1, MAX_BLUEPRINTS_PER_LOOT_CONTAINER, 0));

    @ConfigGroup.Pop
    @WithPerms(opLevel = 2)
    public ValidatedCondition<Integer> maxBlueprints = customDiscovery(
            new ValidatedInt(2, MAX_BLUEPRINTS_PER_LOOT_CONTAINER, 0));

    public ConfigGroup researchAndPoints = new ConfigGroup("researchAndPoints");
    @WithPerms(opLevel = 2)
    public ValidatedCondition<ResearchCostMode> researchCostMode = researchEnabled(
            new ValidatedEnum<>(ResearchCostMode.POINTS_AND_ITEMS));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<TreeResearchResultMode> treeResearchResultMode = researchEnabled(
            new ValidatedEnum<>(TreeResearchResultMode.DIRECT_LEARN));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Integer> researchPointCap = blueprintsEnabled(new ValidatedInt(
            BlueprintProgressionConfigSnapshot.DEFAULT_POINT_CAP,
            MAX_CONFIGURED_RESEARCH_POINT_CAP,
            0));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Boolean> enableResearchPointAwards = blueprintsEnabled(
            new ValidatedBoolean(true));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Boolean> enableCombatResearchPointAwards = uiCondition(
            new ValidatedBoolean(false),
            () -> enableBlueprints.get() && enableResearchPointAwards.get(),
            TRANSLATION_PREFIX + "condition.researchPointAwards");

    @ConfigGroup.Pop
    @WithPerms(opLevel = 2)
    public ValidatedCondition<Boolean> creativeBypassesResearchCost = researchEnabled(
            new ValidatedBoolean(false));

    public ConfigGroup tieredProgression = new ConfigGroup("tieredProgression");
    @WithPerms(opLevel = 2)
    public ValidatedCondition<ResearchProgressionPreset> progressionPreset = blueprintsEnabled(
            new ValidatedEnum<>(ResearchProgressionPreset.TIERED_RESEARCH_AND_CRAFTING));

    @ConfigGroup.Pop
    @WithPerms(opLevel = 2)
    public ValidatedCondition<BlueprintFragmentPreset> fragmentPreset = blueprintsEnabled(
            new ValidatedEnum<>(BlueprintFragmentPreset.TARGETED_RESEARCH_BOOST));

    public ConfigGroup craftingAccess = new ConfigGroup("craftingAccess");
    @WithPerms(opLevel = 2)
    public ValidatedEnum<AmmoCraftingStrategy> ammoCraftingStrategy =
            new ValidatedEnum<>(AmmoCraftingStrategy.PROFILE, ValidatedEnum.WidgetType.CYCLING);

    @ConfigGroup.Pop
    @WithPerms(opLevel = 2)
    public ValidatedEnum<AttachmentCraftingStrategy> attachmentCraftingStrategy =
            new ValidatedEnum<>(AttachmentCraftingStrategy.PROFILE,
                    ValidatedEnum.WidgetType.CYCLING);

    public ConfigGroup analyzer = new ConfigGroup("analyzer");
    @WithPerms(opLevel = 2)
    public ValidatedCondition<DuplicateBlueprintPolicy> duplicateBlueprintPolicy = blueprintsEnabled(
            new ValidatedEnum<>(DuplicateBlueprintPolicy.MANUAL_RECYCLING));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Boolean> allowUnlearnedRecycling = uiCondition(
            new ValidatedBoolean(false),
            () -> enableBlueprints.get()
                    && duplicateBlueprintPolicy.get() == DuplicateBlueprintPolicy.MANUAL_RECYCLING,
            TRANSLATION_PREFIX + "condition.recyclingEnabled");

    @ConfigGroup.Pop
    @WithPerms(opLevel = 2)
    public ValidatedCondition<FoundWeaponRecoveryMode> foundWeaponRecoveryMode = blueprintsEnabled(
            new ValidatedEnum<>(FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY));

    public ConfigGroup startingAccess = new ConfigGroup("startingAccess");
    @WithPerms(opLevel = 2)
    public ValidatedSet<String> startingBlueprints = new ValidatedSet<>(
            new HashSet<>(),
            blueprintIdAutocomplete());

    @WithPerms(opLevel = 2)
    public ValidatedSet<String> progressionExemptBlueprints = new ValidatedSet<>(
            new HashSet<>(),
            blueprintIdAutocomplete());

    @WithPerms(opLevel = 2)
    public ValidatedChoiceList<String> progressionExemptKinds = new ValidatedChoiceList<>(
            List.of(),
            BLUEPRINT_KINDS,
            new ValidatedString("gun", ITEM_TYPE_PATTERN),
            BlueprintConfig::blueprintKindName,
            BlueprintConfig::blueprintKindDescription,
            ValidatedChoiceList.WidgetType.INLINE);

    @ConfigGroup.Pop
    @WithPerms(opLevel = 2)
    public PreservingStringChoiceList progressionExemptItemTypes = new PreservingStringChoiceList(
            List.of(),
            STANDARD_BLUEPRINT_ITEM_TYPES,
            new ValidatedString("pistol", ITEM_TYPE_PATTERN),
            BlueprintConfig::loadedBlueprintItemTypes,
            value -> value != null && value.matches(ITEM_TYPE_PATTERN),
            BlueprintConfig::blueprintItemTypeName,
            BlueprintConfig::blueprintItemTypeDescription,
            ValidatedChoiceList.WidgetType.SCROLLABLE);

    public ConfigGroup advanced = new ConfigGroup("advanced", true);
    @WithPerms(opLevel = 2)
    public ValidatedCondition<String> activeResearchProfile = blueprintsEnabled(new ValidatedString(
            DEFAULT_RESEARCH_PROFILE.toString(),
            RESOURCE_LOCATION_PATTERN));

    @WithPerms(opLevel = 2)
    public ValidatedSet<String> gunBlacklist = new ValidatedSet<>(
            new HashSet<>(),
            blueprintIdAutocomplete("tacz:ak47", BlueprintKind.GUN));

    @WithPerms(opLevel = 2)
    public ValidatedSet<String> ammoBlacklist = new ValidatedSet<>(
            new HashSet<>(),
            blueprintIdAutocomplete("oldgun:pf60_ammo", BlueprintKind.AMMO));

    @WithPerms(opLevel = 2)
    public ValidatedSet<String> attachmentBlacklist = new ValidatedSet<>(
            new HashSet<>(),
            blueprintIdAutocomplete("tacz:extended_mag_1", BlueprintKind.ATTACHMENT));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Boolean> customEnforceResearchTiers = customProgression(
            new ValidatedBoolean(true));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Boolean> customEnforceCraftingTiers = customProgression(
            new ValidatedBoolean(true));

    @WithPerms(opLevel = 2)
    public ValidatedInt tierOneUpperPercent = new ValidatedInt(35, 98, 1);

    @WithPerms(opLevel = 2)
    public ValidatedInt tierTwoUpperPercent = new ValidatedInt(75, 99, 2);

    @WithPerms(opLevel = 2)
    public ValidatedEnum<ResearchWorkbenchTier> unknownWorkstationFallbackTier =
            new ValidatedEnum<>(ResearchWorkbenchTier.TIER_1);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean unknownExternalWorkstationsUnrestricted = new ValidatedBoolean(false);

    @WithPerms(opLevel = 2)
    public ValidatedStringMap<ResearchWorkbenchTier> externalWorkstationTiers =
            new ValidatedStringMap<>(
                    Map.of(),
                    new ValidatedString("tacz:gun_smith_table", RESOURCE_LOCATION_PATTERN),
                    new ValidatedEnum<>(ResearchWorkbenchTier.TIER_1));

    @WithPerms(opLevel = 2)
    public ValidatedBoolean creativeBypassesWorkbenchTiers = new ValidatedBoolean(true);

    @WithPerms(opLevel = 2)
    public ValidatedBoolean creativeBypassesProgressionGates = new ValidatedBoolean(false);

    @WithPerms(opLevel = 2)
    public ValidatedCondition<ResearchWorkbenchTier> linkedAmmoFallbackTier = uiCondition(
            new ValidatedEnum<>(ResearchWorkbenchTier.TIER_1),
            () -> ammoCraftingStrategy.get() == AmmoCraftingStrategy.LINKED_WEAPON,
            TRANSLATION_PREFIX + "condition.linkedAmmo");

    @WithPerms(opLevel = 2)
    public ValidatedChoiceList<String> craftingUnrestrictedKinds = new ValidatedChoiceList<>(
            List.of(),
            BLUEPRINT_KINDS,
            new ValidatedString("gun", ITEM_TYPE_PATTERN),
            BlueprintConfig::craftingKindName,
            BlueprintConfig::craftingUnrestrictedKindDescription,
            ValidatedChoiceList.WidgetType.INLINE);

    @WithPerms(opLevel = 2)
    public PreservingStringChoiceList craftingUnrestrictedItemTypes =
            new PreservingStringChoiceList(
                    List.of(),
                    STANDARD_BLUEPRINT_ITEM_TYPES,
                    new ValidatedString("pistol", ITEM_TYPE_PATTERN),
                    BlueprintConfig::loadedBlueprintItemTypes,
                    value -> value != null && value.matches(ITEM_TYPE_PATTERN),
                    BlueprintConfig::craftingItemTypeName,
                    BlueprintConfig::craftingUnrestrictedItemTypeDescription,
                    ValidatedChoiceList.WidgetType.SCROLLABLE);

    @WithPerms(opLevel = 2)
    public ValidatedChoiceList<String> craftingDisabledKinds = new ValidatedChoiceList<>(
            List.of(),
            BLUEPRINT_KINDS,
            new ValidatedString("gun", ITEM_TYPE_PATTERN),
            BlueprintConfig::craftingKindName,
            BlueprintConfig::craftingDisabledKindDescription,
            ValidatedChoiceList.WidgetType.INLINE);

    @WithPerms(opLevel = 2)
    public PreservingStringChoiceList craftingDisabledItemTypes =
            new PreservingStringChoiceList(
                    List.of(),
                    STANDARD_BLUEPRINT_ITEM_TYPES,
                    new ValidatedString("pistol", ITEM_TYPE_PATTERN),
                    BlueprintConfig::loadedBlueprintItemTypes,
                    value -> value != null && value.matches(ITEM_TYPE_PATTERN),
                    BlueprintConfig::craftingItemTypeName,
                    BlueprintConfig::craftingDisabledItemTypeDescription,
                    ValidatedChoiceList.WidgetType.SCROLLABLE);

    @WithPerms(opLevel = 2)
    public ValidatedStringMap<CraftingAccessOverride> exactCraftingOverrides =
            new ValidatedStringMap<>(
                    Map.of(),
                    blueprintIdAutocomplete(),
                    new ValidatedEnum<>(CraftingAccessOverride.TIER_1));

    @WithPerms(opLevel = 2)
    public ValidatedStringMap<Integer> exactFragmentThresholds = new ValidatedStringMap<>(
            Map.of(),
            blueprintIdAutocomplete(),
            new ValidatedInt(10, MAX_CONFIGURED_FRAGMENT_THRESHOLD, 1));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<BlueprintFragmentPolicy.CompletionMode> customFragmentMode = customFragments(
            new ValidatedEnum<>(BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Integer> tierOneFragmentThreshold = customFragments(
            new ValidatedInt(5, MAX_CONFIGURED_FRAGMENT_THRESHOLD, 1));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Integer> tierTwoFragmentThreshold = customFragments(
            new ValidatedInt(10, MAX_CONFIGURED_FRAGMENT_THRESHOLD, 1));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Integer> tierThreeFragmentThreshold = customFragments(
            new ValidatedInt(15, MAX_CONFIGURED_FRAGMENT_THRESHOLD, 1));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Integer> fragmentRetainedProgressCap = customFragments(
            new ValidatedInt(1_000, MAX_CONFIGURED_FRAGMENT_PROGRESS, 1));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<BlueprintFragmentDiscountType> fragmentDiscountType = customFragments(
            new ValidatedEnum<>(BlueprintFragmentDiscountType.PERCENTAGE));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Integer> fragmentDiscountValue = customFragments(
            new ValidatedInt(2_500, 10_000, 1));

    @WithPerms(opLevel = 2)
    public ValidatedCondition<Integer> learnedTargetFragmentResearchPoints = customFragments(
            new ValidatedInt(1, MAX_CONFIGURED_RESEARCH_POINT_CAP, 0));

    @ConfigGroup.Pop
    @WithPerms(opLevel = 2)
    public ValidatedInt fragmentLootReplacementPercent = new ValidatedInt(20, 100, 0);

    private transient volatile BlueprintProgressionConfigSnapshot progressionSnapshot;
    private transient volatile BlueprintAccessConfigSnapshot accessSnapshot;
    private transient volatile ResearchPointAwardConfigSnapshot awardSnapshot;
    private transient volatile ResearchFeatureConfigSnapshot researchFeatureSnapshot;
    
    public BlueprintConfig() {
        super(TaCZWeaponBlueprints.loc("blueprint"));
        publishProgressionSnapshot();
    }

    @Override
    public void update(int deserializedVersion) {
        if (deserializedVersion < 1) {
            // Version-zero files exposed these four discovery settings
            // directly. Preserve their established behavior by migrating the
            // existing server to Custom rather than applying a new overlay.
            balancePreset.validateAndSet(BlueprintBalancePreset.CUSTOM);
        }
        if (deserializedVersion < 3) {
            // Tiered workstations did not exist in version 2. Preserve the
            // established world's access and loot rules until its operator
            // opts into the new workstation and Blueprint Fragment systems.
            progressionPreset.validateAndSet(ResearchProgressionPreset.CLASSIC);
            fragmentPreset.validateAndSet(BlueprintFragmentPreset.DISABLED);
            TaCZWeaponBlueprints.LOGGER.info(
                    "Migrated the blueprint configuration from version {} to Classic Research "
                            + "progression with Blueprint Fragments disabled. Choose a tiered "
                            + "progression and fragment preset to opt into the new systems.",
                    deserializedVersion);
        } else if (deserializedVersion < 4) {
            TaCZWeaponBlueprints.LOGGER.info(
                    "Migrated the blueprint configuration from version 3 with research-profile "
                            + "crafting behavior preserved. New crafting category and override "
                            + "controls remain set to their compatibility defaults.");
        }
        normalizeAndPublish();
    }

    @Override
    public void onUpdateClient() {
        normalizeAndPublish();
    }

    @Override
    public void onUpdateServer(ServerUpdateContext context) {
        ResearchFeatureConfigSnapshot previousResearchFeatures = researchFeatureSnapshot;
        normalizeAndPublish();
        ResearchFeatureConfigSnapshot currentResearchFeatures = researchFeatureSnapshot;
        boolean policyShapeChanged = previousResearchFeatures == null
                || !previousResearchFeatures.policyShape().equals(
                        currentResearchFeatures.policyShape());
        if (policyShapeChanged || ProgressionPolicyAccessService.acquire(
                ProgressionPolicyAccessService.Mode.CURRENT_ONLY).isEmpty()) {
            BlueprintDataManager.SERVER.rebuildProgressionPolicy();
        }
        closeChangedExternalWorkbenchMenus(
                context.getServer(), previousResearchFeatures, currentResearchFeatures);
        // Fzzy Config 0.7.4+ can issue server updates without an originating
        // player (including integrated-server and file-watcher updates). The
        // context always retains the server needed to republish every open
        // workstation even in those cases.
        refreshOnlinePlayers(context.getServer());
    }

    private static void closeChangedExternalWorkbenchMenus(
            MinecraftServer server,
            ResearchFeatureConfigSnapshot previous,
            ResearchFeatureConfigSnapshot current) {
        if (server == null || previous == null || current == null
                || previous.hasSameExternalWorkbenchResolution(current)) {
            return;
        }
        for (var player : server.getPlayerList().getPlayers()) {
            if (!(player.containerMenu instanceof GunSmithTableMenu menu)
                    || !(menu instanceof TaCZWorkbenchMenuBridge bridge)) {
                continue;
            }
            var workbench = bridge.taczweaponblueprints$workbenchContext().orElse(null);
            if (workbench == null || CraftingWorkbenchTierResolver.isNativeCraftingWorkbench(
                    workbench.workstationId())) {
                continue;
            }
            var before = CraftingWorkbenchTierResolver.resolve(
                    workbench.workstationId(), previous);
            var after = CraftingWorkbenchTierResolver.resolve(
                    workbench.workstationId(), current);
            if (before.tier() != after.tier()
                    || before.unrestricted() != after.unrestricted()) {
                player.closeContainer();
                player.displayClientMessage(Component.translatable(
                        "message.taczweaponblueprints.external_workbench_config_changed"), false);
            }
        }
    }

    @Override
    public void onSyncClient() {
        publishProgressionSnapshot();
    }

    @Override
    public void onSyncServer() {
        publishProgressionSnapshot();
    }

    public BlueprintProgressionConfigSnapshot progressionSnapshot() {
        return progressionSnapshot;
    }

    public ResearchPointAwardConfigSnapshot awardSnapshot() {
        return awardSnapshot;
    }

    public BlueprintAccessConfigSnapshot accessSnapshot() {
        return accessSnapshot;
    }

    public ResearchFeatureConfigSnapshot researchFeatureSnapshot() {
        return researchFeatureSnapshot;
    }

    public BlueprintBalanceSettings balanceSettings() {
        return BlueprintBalanceSettings.resolve(
                balancePreset.get(),
                maximumUndiscoveredVisibility.get(),
                blueprintSpawnChance.get(),
                minBlueprints.get(),
                maxBlueprints.get());
    }

    /** Applies only the reversible preset selector and republishes live policy. */
    public synchronized BalancePresetApplication applyBalancePreset(
            BlueprintBalancePreset preset,
            MinecraftServer server) {
        if (preset == null) {
            throw new IllegalArgumentException("balance preset cannot be null");
        }
        boolean changed = balancePreset.get() != preset;
        if (changed) {
            // Do not normalize the dormant custom fields here: switching a
            // reversible overlay must not rewrite the values it is preserving.
            balancePreset.validateAndSet(preset);
            publishProgressionSnapshot();
        }
        // An explicit confirmed apply also repairs a missing/stale file when the
        // in-memory selector already has the requested value.
        save();
        boolean persisted = isBalancePresetPersisted(preset);
        int synchronizedPlayers = server == null ? 0 : refreshOnlinePlayers(server);
        return new BalancePresetApplication(changed, persisted, synchronizedPlayers);
    }

    /** Applies an authoritative server publication on a remote client without marking a GUI edit. */
    public synchronized void acceptSynchronizedBalancePreset(BlueprintBalancePreset preset) {
        if (preset == null) {
            throw new IllegalArgumentException("synchronized balance preset cannot be null");
        }
        balancePreset.validateAndSet(preset);
        publishProgressionSnapshot();
    }

    private void normalizeAndPublish() {
        int minValue = minBlueprints.get();
        int maxValue = maxBlueprints.get();
        if (maxValue < minValue) {
            maxBlueprints.setAndUpdate(minValue);
        }
        int tierOne = tierOneUpperPercent.get();
        if (tierTwoUpperPercent.get() <= tierOne) {
            tierTwoUpperPercent.setAndUpdate(Math.min(99, tierOne + 1));
        }
        int maximumThreshold = Math.max(
                tierOneFragmentThreshold.get(),
                Math.max(tierTwoFragmentThreshold.get(), tierThreeFragmentThreshold.get()));
        if (fragmentRetainedProgressCap.get() < maximumThreshold) {
            fragmentRetainedProgressCap.setAndUpdate(maximumThreshold);
        }
        publishProgressionSnapshot();
    }

    private int refreshOnlinePlayers(MinecraftServer server) {
        server.getPlayerList().getPlayers().forEach(onlinePlayer -> {
            NetworkHandler.syncBalancePreset(onlinePlayer, balancePreset.get());
            StartingBlueprintGrantService.applyConfiguredGrants(onlinePlayer);
            NetworkHandler.syncPlayerRecipeData(onlinePlayer);
            ResearchPointPresentationService.syncHelp(onlinePlayer);
            ResearchPointAwardReconciliationScheduler.schedule(onlinePlayer);
        });
        return server.getPlayerList().getPlayerCount();
    }

    private boolean isBalancePresetPersisted(BlueprintBalancePreset expected) {
        Path file = configFile();
        try {
            boolean matches = serializedBalancePresetMatches(Files.readString(file), expected);
            if (!matches) {
                TaCZWeaponBlueprints.LOGGER.error(
                        "Balance preset {} is active in memory but was not confirmed in {}",
                        expected,
                        file);
            }
            return matches;
        } catch (IOException | RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Balance preset {} is active in memory but persistence verification failed for {}",
                    expected,
                    file,
                    exception);
            return false;
        }
    }

    static boolean serializedBalancePresetMatches(
            String serialized,
            BlueprintBalancePreset expected) {
        if (serialized == null || expected == null) {
            return false;
        }
        Matcher matcher = PERSISTED_BALANCE_PRESET.matcher(serialized);
        return matcher.find() && expected.name().equals(matcher.group(1));
    }

    private Path configFile() {
        Path path = FMLPaths.CONFIGDIR.get();
        if (!getFolder().isBlank()) {
            path = path.resolve(getFolder());
        }
        if (!getSubfolder().isBlank()) {
            path = path.resolve(getSubfolder());
        }
        return path.resolve(getName() + ".toml");
    }

    private void publishProgressionSnapshot() {
        progressionSnapshot = BlueprintProgressionConfigSnapshot.from(this);
        accessSnapshot = BlueprintAccessConfigSnapshot.from(this);
        awardSnapshot = ResearchPointAwardConfigSnapshot.from(this, progressionSnapshot);
        researchFeatureSnapshot = ResearchFeatureConfigSnapshot.from(this);
    }

    public record BalancePresetApplication(
            boolean changed,
            boolean persisted,
            int synchronizedPlayers) {
        public BalancePresetApplication {
            if (synchronizedPlayers < 0) {
                throw new IllegalArgumentException("synchronized player count cannot be negative");
            }
        }
    }

    public boolean isItemBlacklisted(String itemId) {
        return gunBlacklist.contains(itemId) || ammoBlacklist.contains(itemId) || attachmentBlacklist.contains(itemId);
    }

    private <T> ValidatedCondition<T> blueprintsEnabled(ValidatedField<T> field) {
        return uiCondition(
                field,
                enableBlueprints::get,
                TRANSLATION_PREFIX + "condition.blueprintsEnabled");
    }

    private <T> ValidatedCondition<T> researchEnabled(ValidatedField<T> field) {
        return uiCondition(
                field,
                () -> enableBlueprints.get() && enableResearch.get(),
                TRANSLATION_PREFIX + "condition.researchEnabled");
    }

    private <T> ValidatedCondition<T> customProgression(ValidatedField<T> field) {
        return uiCondition(
                field,
                () -> enableBlueprints.get()
                        && progressionPreset.get() == ResearchProgressionPreset.CUSTOM,
                TRANSLATION_PREFIX + "condition.customProgression");
    }

    private <T> ValidatedCondition<T> customFragments(ValidatedField<T> field) {
        return uiCondition(
                field,
                () -> enableBlueprints.get()
                        && fragmentPreset.get() == BlueprintFragmentPreset.CUSTOM,
                TRANSLATION_PREFIX + "condition.customFragments");
    }

    private <T> ValidatedCondition<T> customDiscovery(ValidatedField<T> field) {
        return uiCondition(
                field,
                () -> enableBlueprints.get()
                        && balancePreset.get() == BlueprintBalancePreset.CUSTOM,
                TRANSLATION_PREFIX + "condition.customDiscovery");
    }

    private static <T> ValidatedCondition<T> uiCondition(
            ValidatedField<T> field,
            Supplier<Boolean> condition,
            String failureTranslationKey) {
        return field.toCondition(
                condition,
                Component.translatable(failureTranslationKey),
                field::get);
    }

    private static Collection<String> loadedBlueprintItemTypes() {
        return BlueprintDataManager.presentationCatalog().getAllBlueprints().stream()
                .map(data -> data.getItemType() == null
                        ? ""
                        : data.getItemType().toLowerCase(Locale.ROOT))
                .filter(value -> value.matches(ITEM_TYPE_PATTERN))
                .distinct()
                .sorted()
                .toList();
    }

    private static BlueprintIdAutocompleteString blueprintIdAutocomplete() {
        return blueprintIdAutocomplete("tacz:ak47", null);
    }

    private static BlueprintIdAutocompleteString blueprintIdAutocomplete(
            String defaultValue,
            BlueprintKind requiredKind) {
        return new BlueprintIdAutocompleteString(
                defaultValue,
                value -> value != null && value.matches(RESOURCE_LOCATION_PATTERN),
                () -> loadedBlueprintIdSuggestions(requiredKind));
    }

    private static List<BlueprintIdAutocompleteString.BlueprintIdSuggestion> loadedBlueprintIdSuggestions(
            BlueprintKind requiredKind) {
        return blueprintIdSuggestions(
                BlueprintDataManager.presentationCatalog().catalogPublication().blueprints(),
                requiredKind);
    }

    static List<BlueprintIdAutocompleteString.BlueprintIdSuggestion> blueprintIdSuggestions(
            Map<net.minecraft.resources.ResourceLocation, BlueprintData> blueprints,
            BlueprintKind requiredKind) {
        if (blueprints == null || blueprints.isEmpty()) {
            return List.of();
        }
        return blueprints.entrySet().stream()
                .filter(entry -> entry.getKey() != null && entry.getValue() != null)
                .filter(entry -> requiredKind == null || entry.getValue().getKind() == requiredKind)
                .map(entry -> new BlueprintIdAutocompleteString.BlueprintIdSuggestion(
                        entry.getKey().toString(),
                        blueprintDisplayName(entry.getValue(), entry.getKey().getPath())))
                .sorted(java.util.Comparator
                        .comparing(
                                BlueprintIdAutocompleteString.BlueprintIdSuggestion::displayName,
                                String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(BlueprintIdAutocompleteString.BlueprintIdSuggestion::id))
                .toList();
    }

    private static String blueprintDisplayName(BlueprintData data, String fallbackPath) {
        if (data == null || data.getNameKey() == null || data.getNameKey().isBlank()) {
            return humanizeChoice(fallbackPath);
        }
        String key = data.getNameKey();
        String translated = Component.translatable(key).getString().strip();
        if (translated.equals(key.strip()) && key.endsWith(".name")) {
            String alternateKey = key.substring(0, key.length() - ".name".length());
            String alternate = Component.translatable(alternateKey).getString().strip();
            if (!alternate.equals(alternateKey)) {
                translated = alternate;
            }
        }
        return translated.isBlank() || translated.equals(key.strip())
                ? humanizeChoice(fallbackPath)
                : translated;
    }

    private static net.minecraft.network.chat.MutableComponent blueprintKindName(
            String value,
            String ignoredTranslationKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "progressionExemptKinds.option." + value,
                switch (value) {
                    case "gun" -> "Guns";
                    case "ammo" -> "Ammunition";
                    case "attachment" -> "Attachments";
                    default -> humanizeChoice(value);
                });
    }

    private static Component blueprintKindDescription(String value, String ignoredDescriptionKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "progressionExemptKinds.option." + value + ".desc",
                "Make every recipe in this category available without a blueprint.");
    }

    private static net.minecraft.network.chat.MutableComponent blueprintItemTypeName(
            String value,
            String ignoredTranslationKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "progressionExemptItemTypes.option." + value,
                itemTypeDisplayName(value));
    }

    private static net.minecraft.network.chat.MutableComponent craftingKindName(
            String value,
            String ignoredTranslationKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "craftingKinds.option." + value,
                switch (value) {
                    case "gun" -> "Guns";
                    case "ammo" -> "Ammunition";
                    case "attachment" -> "Attachments";
                    default -> humanizeChoice(value);
                });
    }

    private static Component craftingUnrestrictedKindDescription(
            String value,
            String ignoredDescriptionKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "craftingUnrestrictedKinds.option.desc",
                "Let this category ignore Crafting Workbench levels.");
    }

    private static Component craftingDisabledKindDescription(
            String value,
            String ignoredDescriptionKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "craftingDisabledKinds.option.desc",
                "Disable Workbench crafting for this category.");
    }

    private static net.minecraft.network.chat.MutableComponent craftingItemTypeName(
            String value,
            String ignoredTranslationKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "craftingItemTypes.option." + value,
                itemTypeDisplayName(value));
    }

    private static Component craftingUnrestrictedItemTypeDescription(
            String value,
            String ignoredDescriptionKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "craftingUnrestrictedItemTypes.option.desc",
                "Let this TaCZ subgroup ignore Crafting Workbench levels.");
    }

    private static Component craftingDisabledItemTypeDescription(
            String value,
            String ignoredDescriptionKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "craftingDisabledItemTypes.option.desc",
                "Disable Workbench crafting for this TaCZ subgroup.");
    }

    private static Component blueprintItemTypeDescription(String value, String ignoredDescriptionKey) {
        return Component.translatableWithFallback(
                TRANSLATION_PREFIX + "progressionExemptItemTypes.option.desc",
                "Make every recipe in this TaCZ subgroup available without a blueprint.");
    }

    private static String itemTypeDisplayName(String value) {
        return switch (value) {
            case "rifle" -> "Rifles";
            case "pistol" -> "Pistols";
            case "sniper" -> "Sniper Rifles";
            case "shotgun" -> "Shotguns";
            case "smg" -> "Submachine Guns";
            case "rpg" -> "Rocket Launchers";
            case "mg" -> "Machine Guns";
            case "ammo" -> "Ammunition";
            case "scope" -> "Scopes";
            case "muzzle" -> "Muzzle Attachments";
            case "stock" -> "Stocks";
            case "grip" -> "Grips";
            case "laser" -> "Lasers";
            case "extended_mag" -> "Extended Magazines";
            default -> humanizeChoice(value);
        };
    }

    private static String humanizeChoice(String value) {
        if (value == null || value.isBlank()) {
            return "Unknown";
        }
        String[] words = value.replace('_', ' ').replace('-', ' ').replace('.', ' ').split("\\s+");
        StringBuilder label = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!label.isEmpty()) {
                label.append(' ');
            }
            label.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) {
                label.append(word.substring(1));
            }
        }
        return label.toString();
    }
    
}
