package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.BlueprintLearningMutation;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class StartingBlueprintGrantServiceTest {
    private static final ResourceLocation GRANTED = id("test:granted");
    private static final ResourceLocation KNOWN = id("test:known");
    private static final ResourceLocation EXEMPT = id("test:exempt");
    private static final ResourceLocation BLOCKED = id("test:blocked");
    private static final ResourceLocation MISSING = id("test:missing");

    @Test
    void configuredGrantsAreExactIdempotentAndKeepExemptionsSeparate() {
        PlayerRecipeData data = new PlayerRecipeData();
        assertTrue(data.applyBlueprintLearning(BlueprintLearningMutation.Request.commit(
                KNOWN.toString(), recipe(KNOWN).toString())).committed());
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                GRANTED, blueprint(GRANTED),
                KNOWN, blueprint(KNOWN),
                EXEMPT, blueprint(EXEMPT),
                BLOCKED, blueprint(BLOCKED));
        BlueprintAccessConfigSnapshot access = new BlueprintAccessConfigSnapshot(
                Set.of(EXEMPT), Set.of(), Set.of(),
                Set.of(GRANTED, KNOWN, EXEMPT, BLOCKED, MISSING));

        StartingBlueprintGrantService.Result result = StartingBlueprintGrantService.apply(
                data,
                access.startingBlueprints(),
                true,
                access,
                catalog,
                id -> policy(data, id, id.equals(BLOCKED)));

        assertEquals(1, result.granted());
        assertEquals(1, result.alreadyKnown());
        assertEquals(1, result.progressionExempt());
        assertEquals(1, result.blocked());
        assertEquals(1, result.unavailable());
        assertTrue(result.changed());
        assertTrue(data.hasBlueprint(GRANTED.toString()));
        assertTrue(data.hasRecipe(recipe(GRANTED).toString()));
        assertFalse(data.hasBlueprint(EXEMPT.toString()));
        assertTrue(data.getResearchPointAwardLedger().isEmpty());

        StartingBlueprintGrantService.Result repeated = StartingBlueprintGrantService.apply(
                data,
                access.startingBlueprints(),
                true,
                access,
                catalog,
                id -> policy(data, id, id.equals(BLOCKED)));
        assertEquals(0, repeated.granted());
        assertEquals(2, repeated.alreadyKnown());
        assertFalse(repeated.changed());
    }

    private static BlueprintResearchPolicy policy(
            PlayerRecipeData data,
            ResourceLocation id,
            boolean blocked) {
        return new BlueprintResearchPolicy(
                id,
                id("test:profile"),
                true,
                blocked,
                true,
                data.hasBlueprint(id.toString()),
                data.hasDiscoveredBlueprint(id.toString()),
                data.getResearchPoints(),
                100,
                false,
                true,
                true,
                JournalVisibility.FULL,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(0, List.of()),
                false,
                List.of(id("test:unlearned_prerequisite")),
                false,
                Optional.empty(),
                MatchSpecificity.NONE);
    }

    private static BlueprintData blueprint(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "item.test.name",
                "item.test.tooltip",
                recipe(id),
                null,
                "rifle",
                id("test:display"));
    }

    private static ResourceLocation recipe(ResourceLocation id) {
        return new ResourceLocation("test", "recipe/" + id.getPath());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
