package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeDisplayPolicy;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayoutPolicy;

import me.fzzyhmstrs.fzzy_config.annotations.Translation;
import me.fzzyhmstrs.fzzy_config.config.Config;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedBoolean;
import me.fzzyhmstrs.fzzy_config.validation.number.ValidatedInt;

/** Client-owned visual policy for the Research Tree layout kernel. */
@Translation(prefix = ModConfigs.BASE_KEY + "research_tree_client")
public final class ResearchTreeClientConfig extends Config {
    public static final int DEFAULT_HOLD_DURATION_MILLIS = 700;
    public static final int MIN_HOLD_DURATION_MILLIS = 400;
    public static final int MAX_HOLD_DURATION_MILLIS = 2_000;

    private static final ResearchTreeLayoutPolicy DEFAULTS =
            ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW;

    public ValidatedBoolean holdToResearch = new ValidatedBoolean(true);
    public ValidatedInt holdDurationMillis = new ValidatedInt(
            DEFAULT_HOLD_DURATION_MILLIS,
            MAX_HOLD_DURATION_MILLIS,
            MIN_HOLD_DURATION_MILLIS);
    public ValidatedBoolean reduceMotion = new ValidatedBoolean(
            ResearchTreeDisplayPolicy.DEFAULT.reduceMotion());
    public ValidatedBoolean showBackgroundGrid = new ValidatedBoolean(
            ResearchTreeDisplayPolicy.DEFAULT.showBackgroundGrid());
    public ValidatedBoolean showResearchPointNotifications = new ValidatedBoolean(true);
    public ValidatedInt canvasPadding = spacing(DEFAULTS.canvasPadding());
    public ValidatedInt nodeGap = spacing(DEFAULTS.nodeGap());
    public ValidatedInt tierGap = spacing(DEFAULTS.tierGap());
    public ValidatedInt componentGap = spacing(DEFAULTS.componentGap());
    public ValidatedInt intraGroupGap = spacing(DEFAULTS.intraGroupGap());
    public ValidatedInt interGroupGap = spacing(DEFAULTS.interGroupGap());
    public ValidatedInt groupPadding = spacing(DEFAULTS.groupPadding());
    public ValidatedInt groupHeaderHeight = spacing(DEFAULTS.groupHeaderHeight());
    public ValidatedInt portalPadding = spacing(DEFAULTS.portalPadding());
    public ValidatedInt maxRankBlockWidth = new ValidatedInt(
            DEFAULTS.maxRankBlockWidth(),
            ResearchTreeLayout.MAX_DIMENSION,
            ResearchTreeLayout.NODE_WIDTH);
    public ValidatedInt orderingSweeps = new ValidatedInt(
            DEFAULTS.orderingSweeps(), ResearchTreeLayoutPolicy.MAX_SWEEPS, 0);
    public ValidatedInt compactionSweeps = new ValidatedInt(
            DEFAULTS.compactionSweeps(), ResearchTreeLayoutPolicy.MAX_SWEEPS, 0);

    private transient volatile ResearchTreeLayoutPolicy layoutPolicy = DEFAULTS;
    private transient volatile ResearchTreeDisplayPolicy displayPolicy =
            ResearchTreeDisplayPolicy.DEFAULT;

    public ResearchTreeClientConfig() {
        super(TaCZWeaponBlueprints.loc("research_tree_client"));
        publishPolicies();
    }

    @Override
    public void update(int deserializedVersion) {
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
        return holdDurationMillis.get();
    }

    public ResearchTreeDisplayPolicy displayPolicy() {
        return displayPolicy;
    }

    private void normalizeAndPublish() {
        if (interGroupGap.get() < intraGroupGap.get()) {
            interGroupGap.setAndUpdate(intraGroupGap.get());
        }
        publishPolicies();
    }

    private void publishPolicies() {
        layoutPolicy = new ResearchTreeLayoutPolicy(
                canvasPadding.get(),
                nodeGap.get(),
                tierGap.get(),
                componentGap.get(),
                intraGroupGap.get(),
                interGroupGap.get(),
                groupPadding.get(),
                groupHeaderHeight.get(),
                portalPadding.get(),
                maxRankBlockWidth.get(),
                orderingSweeps.get(),
                compactionSweeps.get());
        displayPolicy = new ResearchTreeDisplayPolicy(
                reduceMotion.get(),
                showBackgroundGrid.get());
    }

    private static ValidatedInt spacing(int defaultValue) {
        return new ValidatedInt(defaultValue, ResearchTreeLayoutPolicy.MAX_SPACING, 0);
    }
}
