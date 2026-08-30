package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalBuilder;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBuilder;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class BlueprintProgressionExemptionPublicationTest {
    @Test
    void exemptTargetsDisappearFromResearchSurfacesButRemainUndiscovered() {
        ResourceLocation exempt = id("test:exempt");
        ResourceLocation researchable = id("test:researchable");
        ResourceLocation profileId = id("test:profile");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                exempt, blueprint(exempt, "pistol"),
                researchable, blueprint(researchable, "rifle"));
        BlueprintResearchSnapshot research = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId, new BlueprintResearchProfile(
                        1,
                        true,
                        JournalVisibility.FULL,
                        true,
                        true,
                        false,
                        1,
                        new BlueprintResearchCost(2, List.of()),
                        false,
                        false)),
                Map.of());
        BlueprintProgressionConfigSnapshot config = new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                false,
                100,
                false,
                profileId);
        PlayerRecipeData data = new PlayerRecipeData();

        var journal = BlueprintJournalBuilder.build(
                catalog,
                research,
                config,
                data,
                ignored -> false,
                exempt::equals,
                null);
        var tree = ResearchTreeBuilder.buildPublication(
                catalog,
                research,
                config,
                data,
                ignored -> false,
                exempt::equals,
                null,
                null);

        assertEquals(1, journal.entries().size());
        assertEquals(researchable, journal.entries().get(0).blueprintId().orElseThrow());
        assertEquals(1, tree.graph().nodes().size());
        assertEquals(researchable, tree.graph().nodes().get(0).blueprintId());
        assertFalse(data.hasDiscoveredBlueprint(exempt.toString()));
        assertFalse(data.hasBlueprint(exempt.toString()));
    }

    private static BlueprintData blueprint(ResourceLocation id, String type) {
        return new BlueprintData(
                id.toString(),
                "item.test.name",
                "item.test.tooltip",
                new ResourceLocation("test", "recipe/" + id.getPath()),
                null,
                type,
                id("test:display"));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
