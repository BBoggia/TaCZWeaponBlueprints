package com.gamergaming.taczweaponblueprints.client;

import net.minecraft.network.chat.Component;

/** One purchase scope shared by the inspector, action tooltip and narration. */
final class ResearchTreePurchaseText {
    private static final String PREFIX = "gui.taczweaponblueprints.research_bench.";
    private ResearchTreePurchaseText() { }

    static Component action(ResearchTreeSelectedNodePresenter.Presentation details) {
        return Component.translatable(PREFIX + (details.pathPurchase() ? "research_path" : "research"), details.unlockCount());
    }

    static Component compactAction(ResearchTreeSelectedNodePresenter.Presentation details) {
        return Component.translatable(PREFIX + (details.pathPurchase() ? "research_path.compact" : "research.compact"), details.unlockCount());
    }

    static Component scope(ResearchTreeSelectedNodePresenter.Presentation details) {
        if (!details.hasPurchaseQuote()) return Component.empty();
        return Component.translatable(PREFIX + "tree.purchase." + (details.pathPurchase() ? "path" : "single"), details.unlockCount());
    }

    static Component cost(ResearchTreeSelectedNodePresenter.Presentation details) {
        if (details.pathPlanningFailed()) return Component.translatable(PREFIX + "tree.path.cost_unavailable");
        if (details.costBypassed()) return Component.translatable(PREFIX + "tree.cost_bypassed");
        String key = PREFIX + (details.hasPurchaseQuote() ? "tree.purchase.total" : "tree.cost");
        if (details.pointsEnabled()) {
            if (details.materialsEnabled() && details.ingredientTypeCount() > 0) key += ".points_and_items";
            return ClientResearchCurrency.text(key, details.pointCost());
        }
        return Component.translatable(key + (details.materialsEnabled() && details.ingredientTypeCount() > 0
                ? ".items_only" : ".free"));
    }
}
