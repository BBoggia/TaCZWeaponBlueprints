package com.gamergaming.taczweaponblueprints.menu;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Pure validation contract for every client-requested Research Bench action. */
public final class ResearchBenchActionValidator {
    private ResearchBenchActionValidator() {
    }

    public static boolean accepts(
            ResearchBenchMenu.Mode mode,
            ResearchBenchMenu.Action action,
            Optional<ResourceLocation> selectedBlueprint,
            Optional<ResourceLocation> requestedBlueprint,
            Optional<ResourceLocation> physicalRecyclingBlueprint) {
        if (mode == null || action == null) {
            return false;
        }
        Optional<ResourceLocation> selected = safe(selectedBlueprint);
        Optional<ResourceLocation> requested = safe(requestedBlueprint);
        Optional<ResourceLocation> physical = safe(physicalRecyclingBlueprint);
        return switch (action) {
            case SELECT -> mode == ResearchBenchMenu.Mode.BROWSE;
            case RESEARCH -> mode == ResearchBenchMenu.Mode.BROWSE
                    && requested.isPresent()
                    && requested.equals(selected);
            case RECYCLE -> mode == ResearchBenchMenu.Mode.RECYCLE
                    && requested.isPresent()
                    && requested.equals(physical);
            case SHOW_BROWSE, SHOW_RECYCLE -> requested.isEmpty();
        };
    }

    private static Optional<ResourceLocation> safe(Optional<ResourceLocation> value) {
        return value == null ? Optional.empty() : value;
    }
}
