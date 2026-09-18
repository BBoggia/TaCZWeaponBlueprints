package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/** Semantic detail content; only an explicitly disclosed target can be activated. */
record ResearchTreeInspectorRow(Kind kind, Component text, int color, Optional<ResourceLocation> target,
        Optional<CostCells> costCells, int materialPageDirection) {
    enum Kind { SECTION, EXPLANATION, MATERIAL, GROUP, PREREQUISITE, EXTERNAL_LINK, COST_HEADER, COST, MATERIAL_PAGE }
    record CostCells(Component have, Component required, int haveColor) {
        CostCells {
            if (have == null || required == null) throw new IllegalArgumentException("missing cost cells");
        }
    }
    ResearchTreeInspectorRow(Kind kind, Component text, int color, Optional<ResourceLocation> target) {
        this(kind, text, color, target, Optional.empty(), 0);
    }
    ResearchTreeInspectorRow {
        if (kind == null || text == null || target == null || costCells == null
                || (materialPageDirection != 0) != (kind == Kind.MATERIAL_PAGE)
                || Math.abs((long) materialPageDirection) > 1
                || costCells.isPresent() != (kind == Kind.COST_HEADER || kind == Kind.COST)
                || target.isPresent() && kind != Kind.PREREQUISITE && kind != Kind.EXTERNAL_LINK)
            throw new IllegalArgumentException("invalid inspector row");
    }
    static ResearchTreeInspectorRow text(Kind kind, Component text, int color) {
        return new ResearchTreeInspectorRow(kind, text, color, Optional.empty());
    }
    static ResearchTreeInspectorRow cost(Kind kind, Component name, Component have, Component required,
            int color, int haveColor) {
        return new ResearchTreeInspectorRow(kind, name, color, Optional.empty(), Optional.of(new CostCells(have, required, haveColor)), 0);
    }
    static ResearchTreeInspectorRow materialPage(Component label, int direction, int color) {
        return new ResearchTreeInspectorRow(Kind.MATERIAL_PAGE, label, color, Optional.empty(), Optional.empty(), direction);
    }
    boolean interactive() { return target.isPresent() || materialPageDirection != 0; }
    Component narration() {
        return costCells.<Component>map(c -> Component.translatable(
                "gui.taczweaponblueprints.research_bench.tree.cost_table.narration", text, c.have(), c.required())).orElse(text);
    }
}
