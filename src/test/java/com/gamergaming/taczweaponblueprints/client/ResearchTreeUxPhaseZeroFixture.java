package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

/** Disclosure-safe UI states used by the player-experience redesign tests. */
final class ResearchTreeUxPhaseZeroFixture {
    private static final ResourceLocation DISPLAY_SLOT = id("minecraft:paper");
    private static final ResourceLocation IRON_INGOT = id("minecraft:iron_ingot");

    private ResearchTreeUxPhaseZeroFixture() {
    }

    static ResearchTreeGraph everyAvailability() {
        List<ResearchTreeGraph.Node> nodes = new ArrayList<>();
        for (ResearchTreeGraph.Availability availability
                : ResearchTreeGraph.Availability.values()) {
            nodes.add(node(nodes.size(), availability));
        }
        return new ResearchTreeGraph(nodes, List.of());
    }

    static ResearchSelectionPreview readyPreview() {
        return preview("ready", 6, 10, true, true, true, true, 4);
    }

    static ResearchSelectionPreview insufficientPointsPreview() {
        return preview("points", 10, 3, true, true, true, false, 4);
    }

    static ResearchSelectionPreview missingMaterialsPreview() {
        return preview("materials", 6, 10, true, false, true, false, 2);
    }

    static ResearchSelectionPreview outputFullPreview() {
        return preview("output_full", 6, 10, true, true, false, false, 4);
    }

    static ResearchSelectionPreview lockedPolicyPreview() {
        return preview("policy_locked", 6, 10, false, true, true, false, 4);
    }

    static ResearchSelectionPreview creativeBypassPreview() {
        ResearchSelectionPreview.IngredientPreview ingredient =
                new ResearchSelectionPreview.IngredientPreview(
                        List.of(IRON_INGOT), Optional.empty(), 4, 0);
        return new ResearchSelectionPreview(
                Optional.of(id("phase_zero:creative")),
                6,
                0,
                true,
                true,
                true,
                true,
                true,
                List.of(ingredient));
    }

    private static ResearchTreeGraph.Node node(
            int ordinal,
            ResearchTreeGraph.Availability availability) {
        if (availability == ResearchTreeGraph.Availability.REDACTED) {
            return new ResearchTreeGraph.Node(
                    ordinal,
                    ResearchTreeGraph.redactedNodeId(ordinal),
                    ResearchTreeGraph.REDACTED_NAME_KEY,
                    ResearchTreeGraph.REDACTED_ITEM_TYPE,
                    ResearchTreeGraph.REDACTED_DISPLAY_SLOT,
                    JournalVisibility.SILHOUETTE,
                    false,
                    false,
                    false,
                    0,
                    0,
                    0,
                    0,
                    availability);
        }
        boolean learned = availability == ResearchTreeGraph.Availability.LEARNED;
        boolean eligible = availability == ResearchTreeGraph.Availability.AVAILABLE;
        JournalVisibility visibility = availability == ResearchTreeGraph.Availability.PREVIEW
                ? JournalVisibility.PREVIEW
                : JournalVisibility.FULL;
        return new ResearchTreeGraph.Node(
                ordinal,
                id("phase_zero:" + availability.name().toLowerCase(java.util.Locale.ROOT)),
                "fixture.phase_zero." + availability.name().toLowerCase(java.util.Locale.ROOT),
                "rifle",
                DISPLAY_SLOT,
                visibility,
                learned,
                false,
                eligible,
                availability == ResearchTreeGraph.Availability.RESEARCH_DISABLED ? 0 : 6,
                0,
                0,
                0,
                availability);
    }

    private static ResearchSelectionPreview preview(
            String path,
            int pointCost,
            int pointBalance,
            boolean policyEligible,
            boolean ingredientsSatisfied,
            boolean outputSpace,
            boolean researchable,
            int inventoryAvailable) {
        ResearchSelectionPreview.IngredientPreview ingredient =
                new ResearchSelectionPreview.IngredientPreview(
                        List.of(IRON_INGOT), Optional.empty(), 4, inventoryAvailable);
        return new ResearchSelectionPreview(
                Optional.of(id("phase_zero:" + path)),
                pointCost,
                pointBalance,
                policyEligible,
                ingredientsSatisfied,
                outputSpace,
                researchable,
                false,
                List.of(ingredient));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
