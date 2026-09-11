package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.function.ToIntFunction;
import net.minecraft.network.chat.Component;

/** Shared column geometry for every cost row; labels wrap without displacing amounts. */
record ResearchTreeCostColumns(int resourceWidth, int haveX, int haveWidth, int requiredX, int requiredWidth) {
    static ResearchTreeCostColumns measure(int widgetWidth, List<ResearchTreeInspectorRow> rows,
            ToIntFunction<Component> measure) {
        int end = Math.max(3, widgetWidth - 10);
        int cap = Math.max(1, (end - 2 - 48 - 16) / 2);
        int have = 1, required = 1;
        for (var row : rows) if (row.costCells().isPresent()) {
            var cells = row.costCells().orElseThrow();
            have = Math.max(have, measure.applyAsInt(cells.have()));
            required = Math.max(required, measure.applyAsInt(cells.required()));
        }
        have = Math.min(cap, have); required = Math.min(cap, required);
        int requiredX = end - required, haveX = requiredX - 8 - have;
        return new ResearchTreeCostColumns(Math.max(1, haveX - 8 - 2), haveX, have, requiredX, required);
    }
}
