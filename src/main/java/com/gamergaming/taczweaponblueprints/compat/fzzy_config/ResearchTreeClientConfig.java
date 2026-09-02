package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import java.util.function.Supplier;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeDisplayPolicy;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeLayoutPreset;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeMinimapMode;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;

import me.fzzyhmstrs.fzzy_config.annotations.ConfigDeprecated;
import me.fzzyhmstrs.fzzy_config.annotations.RootConfig;
import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.annotations.Version;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.config.ConfigAction;
import me.fzzyhmstrs.fzzy_config.config.ConfigGroup;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedEnum;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;
import net.minecraft.network.chat.Component;

/** Client-owned visual policy for the Research Tree layout kernel. */
@RootConfig
@Version(version = 3)
@Translation(prefix = ModConfigs.BASE_KEY + "research_tree_client")
public final class ResearchTreeClientConfig extends Config {
    public static final int DEFAULT_HOLD_DURATION_MILLIS = 700;
    public static final int MIN_HOLD_DURATION_MILLIS = 400;
    public static final int MAX_HOLD_DURATION_MILLIS = 2_000;

    private static final ResearchTreeLayoutPolicy DEFAULTS =
            ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;
    private static final String TRANSLATION_PREFIX =
            ModConfigs.BASE_KEY + "research_tree_client.";

    public ConfigGroup interaction = new ConfigGroup("interaction");
    public ValidatedBoolean holdToResearch = new ValidatedBoolean(true);
    public ValidatedCondition<Integer> holdDurationMillis = uiCondition(
            new ValidatedInt(
                    DEFAULT_HOLD_DURATION_MILLIS,
                    MAX_HOLD_DURATION_MILLIS,
                    MIN_HOLD_DURATION_MILLIS),
            holdToResearch::get,
            TRANSLATION_PREFIX + "condition.holdToResearch");
    @ConfigGroup.Pop
    public ValidatedBoolean showResearchPointNotifications = new ValidatedBoolean(true);

    public ConfigGroup treeAppearance = new ConfigGroup("treeAppearance");
    public ValidatedBoolean reduceMotion = new ValidatedBoolean(
            ResearchTreeDisplayPolicy.DEFAULT.reduceMotion());
    public ValidatedBoolean showBackgroundGrid = new ValidatedBoolean(
            ResearchTreeDisplayPolicy.DEFAULT.showBackgroundGrid());
    public ValidatedEnum<ResearchTreeLayoutPreset> layoutPreset = new ValidatedEnum<>(
            ResearchTreeLayoutPreset.BALANCED,
            ValidatedEnum.WidgetType.CYCLING);
    public ValidatedEnum<ResearchTreeMinimapMode> minimap = new ValidatedEnum<>(
            ResearchTreeMinimapMode.AUTOMATIC,
            ValidatedEnum.WidgetType.CYCLING);
    @ConfigGroup.Pop
    public ConfigAction resetTreeAppearance = new ConfigAction.Builder()
            .title(Component.translatable(TRANSLATION_PREFIX + "resetTreeAppearance"))
            .desc(Component.translatable(TRANSLATION_PREFIX + "resetTreeAppearance.desc"))
            .build(this::restoreTreeAppearanceDefaults);

    public ConfigGroup advancedLayout = new ConfigGroup("advancedLayout", true);
    public ValidatedCondition<Integer> canvasPadding = customSpacing(DEFAULTS.canvasPadding());
    public ValidatedCondition<Integer> nodeGap = customSpacing(DEFAULTS.nodeGap());
    public ValidatedCondition<Integer> tierGap = customSpacing(DEFAULTS.tierGap());
    @ConfigDeprecated("No longer used by the visible Tech Tree")
    public ValidatedCondition<Integer> componentGap = customSpacing(DEFAULTS.componentGap());
    @ConfigDeprecated("No longer used by the visible Tech Tree")
    public ValidatedCondition<Integer> intraGroupGap = customSpacing(DEFAULTS.intraGroupGap());
    @ConfigDeprecated("No longer used by the visible Tech Tree")
    public ValidatedCondition<Integer> interGroupGap = customSpacing(DEFAULTS.interGroupGap());
    @ConfigDeprecated("No longer used by the visible Tech Tree")
    public ValidatedCondition<Integer> groupPadding = customSpacing(DEFAULTS.groupPadding());
    @ConfigDeprecated("No longer used by the visible Tech Tree")
    public ValidatedCondition<Integer> groupHeaderHeight = customSpacing(DEFAULTS.groupHeaderHeight());
    public ValidatedCondition<Integer> portalPadding = customSpacing(DEFAULTS.portalPadding());
    @ConfigDeprecated("No longer used by the visible Tech Tree")
    public ValidatedCondition<Integer> maxRankBlockWidth = customSetting(new ValidatedInt(
            DEFAULTS.maxRankBlockWidth(),
            ResearchTreeLayout.MAX_DIMENSION,
            ResearchTreeLayout.NODE_WIDTH));
    public ValidatedCondition<Integer> orderingSweeps = customSetting(new ValidatedInt(
            DEFAULTS.orderingSweeps(), ResearchTreeLayoutPolicy.MAX_SWEEPS, 0));
    @ConfigGroup.Pop
    public ValidatedCondition<Integer> compactionSweeps = customSetting(new ValidatedInt(
            DEFAULTS.compactionSweeps(), ResearchTreeLayoutPolicy.MAX_SWEEPS, 0));

    private transient volatile ResearchTreeLayoutPolicy layoutPolicy = DEFAULTS;
    private transient volatile ResearchTreeDisplayPolicy displayPolicy =
            ResearchTreeDisplayPolicy.DEFAULT;

    public ResearchTreeClientConfig() {
        super(TaCZWeaponBlueprints.loc("research_tree_client"));
        publishPolicies();
    }

    @Override
    public void update(int deserializedVersion) {
        if (deserializedVersion < 1) {
            // Version-zero files already contain individually tuned layout
            // values. Keep those values authoritative instead of silently
            // placing a preset over them during migration.
            layoutPreset.validateAndSet(ResearchTreeLayoutPreset.CUSTOM);
        }
        normalizeAndPublish();
    }

    @Override
    public void onUpdateClient() {
        normalizeAndPublish();
    }

    @Override
    public void onSyncClient() {
        normalizeAndPublish();
    }

    /** Returns one immutable snapshot; callers never observe half-applied fields. */
    public ResearchTreeLayoutPolicy layoutPolicy() {
        return layoutPolicy;
    }

    public boolean holdToResearchEnabled() {
        return holdToResearch.get();
    }

    public int holdDurationMillis() {
        return holdDurationMillis.getUnconditional();
    }

    public ResearchTreeDisplayPolicy displayPolicy() {
        return displayPolicy;
    }

    public ResearchTreeMinimapMode minimapMode() {
        return minimap.get();
    }

    private void normalizeAndPublish() {
        if (interGroupGap.getUnconditional() < intraGroupGap.getUnconditional()) {
            interGroupGap.setAndUpdate(intraGroupGap.getUnconditional());
        }
        publishPolicies();
    }

    private void publishPolicies() {
        ResearchTreeLayoutPolicy customPolicy = new ResearchTreeLayoutPolicy(
                canvasPadding.getUnconditional(),
                nodeGap.getUnconditional(),
                tierGap.getUnconditional(),
                componentGap.getUnconditional(),
                intraGroupGap.getUnconditional(),
                interGroupGap.getUnconditional(),
                groupPadding.getUnconditional(),
                groupHeaderHeight.getUnconditional(),
                portalPadding.getUnconditional(),
                maxRankBlockWidth.getUnconditional(),
                orderingSweeps.getUnconditional(),
                compactionSweeps.getUnconditional());
        layoutPolicy = layoutPreset.get().resolve(customPolicy);
        displayPolicy = new ResearchTreeDisplayPolicy(
                reduceMotion.get(),
                showBackgroundGrid.get());
    }

    void restoreTreeAppearanceDefaults() {
        canvasPadding.setAndUpdate(DEFAULTS.canvasPadding());
        nodeGap.setAndUpdate(DEFAULTS.nodeGap());
        tierGap.setAndUpdate(DEFAULTS.tierGap());
        componentGap.setAndUpdate(DEFAULTS.componentGap());
        intraGroupGap.setAndUpdate(DEFAULTS.intraGroupGap());
        interGroupGap.setAndUpdate(DEFAULTS.interGroupGap());
        groupPadding.setAndUpdate(DEFAULTS.groupPadding());
        groupHeaderHeight.setAndUpdate(DEFAULTS.groupHeaderHeight());
        portalPadding.setAndUpdate(DEFAULTS.portalPadding());
        maxRankBlockWidth.setAndUpdate(DEFAULTS.maxRankBlockWidth());
        orderingSweeps.setAndUpdate(DEFAULTS.orderingSweeps());
        compactionSweeps.setAndUpdate(DEFAULTS.compactionSweeps());
        layoutPreset.setAndUpdate(ResearchTreeLayoutPreset.BALANCED);
        minimap.setAndUpdate(ResearchTreeMinimapMode.AUTOMATIC);
        publishPolicies();
    }

    private ValidatedCondition<Integer> customSpacing(int defaultValue) {
        return customSetting(spacing(defaultValue));
    }

    private <T> ValidatedCondition<T> customSetting(ValidatedField<T> field) {
        return uiCondition(
                field,
                () -> layoutPreset.get() == ResearchTreeLayoutPreset.CUSTOM,
                TRANSLATION_PREFIX + "condition.customLayout");
    }

    private static ValidatedInt spacing(int defaultValue) {
        return new ValidatedInt(defaultValue, ResearchTreeLayoutPolicy.MAX_SPACING, 0);
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
}
