package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import net.minecraft.network.chat.Component;
import static com.gamergaming.taczweaponblueprints.client.ResearchTreeInspectorRow.Kind.*;

/** Uses the matching server quote, including allocated materials, without another inventory scan. */
final class ResearchTreeInspectorCosts {
    private static final String PREFIX = "gui.taczweaponblueprints.research_bench.tree.cost_table.";
    static List<ResearchTreeInspectorRow> rows(ResearchTreeSelectedNodePresenter.Presentation details,
            Component currency, Function<ResearchSelectionPreview.IngredientPreview, Component> ingredientName,
            int text, int muted, int good, int bad) {
        return rows(details, null, currency, ingredientName, text, muted, good, bad);
    }
    static List<ResearchTreeInspectorRow> rows(ResearchTreeSelectedNodePresenter.Presentation details,
            ResearchSelectionPreview preview, Component currency,
            Function<ResearchSelectionPreview.IngredientPreview, Component> ingredientName,
            int text, int muted, int good, int bad) {
        if (!details.hasPurchaseQuote()) return List.of();
        var rows = new ArrayList<ResearchTreeInspectorRow>();
        rows.add(ResearchTreeInspectorRow.text(SECTION, Component.translatable(PREFIX
                + (details.pathPurchase() ? "path" : "single"), details.unlockCount()), text));
        if (preview != null && details.additionalIngredientTypes() > 0) {
            rows.add(ResearchTreeInspectorRow.text(EXPLANATION, Component.translatable(PREFIX + "page",
                    preview.ingredientOffset() + 1, preview.ingredientOffset() + preview.ingredients().size(),
                    preview.ingredientTypeCount()), muted));
            if (preview.ingredientOffset() > 0) rows.add(ResearchTreeInspectorRow.materialPage(
                    Component.translatable(PREFIX + "previous"), -1, text));
            if (preview.ingredientOffset() + preview.ingredients().size() < preview.ingredientTypeCount())
                rows.add(ResearchTreeInspectorRow.materialPage(Component.translatable(PREFIX + "next"), 1, text));
        }
        if (details.costBypassed()) rows.add(ResearchTreeInspectorRow.text(EXPLANATION,
                Component.translatable("gui.taczweaponblueprints.research_bench.tree.cost_bypassed"), muted));
        boolean hasResources = details.pointsEnabled() || !details.ingredients().isEmpty();
        if (hasResources) {
            rows.add(ResearchTreeInspectorRow.cost(COST_HEADER, Component.translatable(PREFIX + "resource"),
                    Component.translatable(PREFIX + "have"), Component.translatable(PREFIX + "required"), muted, muted));
            if (details.pointsEnabled()) rows.add(amount(currency, details.pointBalance(), details.pointCost(),
                    details.costBypassed(), text, muted, good, bad));
            for (var ingredient : details.ingredients()) rows.add(amount(ingredientName.apply(ingredient),
                    ingredient.inventoryAvailable(), ingredient.required(), details.costBypassed(), text, muted, good, bad));
        } else if (details.additionalIngredientTypes() == 0) {
            rows.add(ResearchTreeInspectorRow.text(EXPLANATION, Component.translatable(PREFIX + "free"), good));
        }
        if (preview == null && details.additionalIngredientTypes() > 0) rows.add(ResearchTreeInspectorRow.text(EXPLANATION,
                Component.translatable("gui.taczweaponblueprints.research_bench.tree.path.more_materials",
                        details.additionalIngredientTypes()), muted));
        return List.copyOf(rows);
    }
    private static ResearchTreeInspectorRow amount(Component name, int have, int required, boolean bypass,
            int text, int muted, int good, int bad) {
        return ResearchTreeInspectorRow.cost(COST, name, Component.literal(Integer.toString(have)),
                Component.literal(Integer.toString(required)), text, bypass ? muted : have >= required ? good : bad);
    }
    private ResearchTreeInspectorCosts() { }
}
