package com.gamergaming.taczweaponblueprints.menu;

import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Pure validation for the permanent research-only menu contract. */
public final class ResearchBenchResearchActionValidator {
    private ResearchBenchResearchActionValidator() {
    }

    public static boolean accepts(
            ResearchBenchResearchAction action,
            Optional<ResourceLocation> selectedBlueprint,
            Optional<ResourceLocation> requestedBlueprint) {
        if (action == null) {
            return false;
        }
        Optional<ResourceLocation> selected = safe(selectedBlueprint);
        Optional<ResourceLocation> requested = safe(requestedBlueprint);
        return switch (action) {
            case SELECT -> true;
            case RESEARCH -> requested.isPresent() && requested.equals(selected);
        };
    }

    private static Optional<ResourceLocation> safe(Optional<ResourceLocation> value) {
        return value == null ? Optional.empty() : value;
    }
}
