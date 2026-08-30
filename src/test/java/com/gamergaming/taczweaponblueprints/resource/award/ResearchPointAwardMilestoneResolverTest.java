package com.gamergaming.taczweaponblueprints.resource.award;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.DispatchMode;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.MilestoneState;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchPointAwardMilestoneResolverTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation PISTOL_ONE = id("test:pistol_one");
    private static final ResourceLocation PISTOL_TWO = id("test:pistol_two");
    private static final ResourceLocation RIFLE = id("test:rifle");

    @Test
    void liveThresholdUsesTheDefinitionsFilteredCount() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:pistol_two"), milestone(2, "test:pistol_two")));

        var result = ResearchPointAwardMilestoneResolver.resolve(
                snapshot,
                PROFILE,
                DispatchMode.LIVE,
                MilestoneState.DISCOVERED,
                Set.of(PISTOL_ONE, PISTOL_TWO, RIFLE),
                Optional.of(PISTOL_TWO),
                facts());

        assertTrue(result.successful());
        assertEquals(1, result.awards().size());
        assertEquals(1, result.awards().get(0).context().previousCount());
        assertEquals(2, result.awards().get(0).context().currentCount());
    }

    @Test
    void retroactiveSimulationPreservesSeparateHistoricalThresholdEvents() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:first"), milestone(1, "test:first_claim"),
                id("test:second"), milestone(2, "test:second_claim")));

        var result = ResearchPointAwardMilestoneResolver.resolve(
                snapshot,
                PROFILE,
                DispatchMode.RETROACTIVE,
                MilestoneState.DISCOVERED,
                Set.of(PISTOL_ONE, PISTOL_TWO),
                Optional.empty(),
                facts());

        assertTrue(result.successful());
        assertEquals(2, result.awards().size());
        assertEquals(Set.of(1, 2), result.awards().stream()
                .map(value -> value.context().currentCount()).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void retroactivePlanConsumesOnlyOneDefinitionPerStep() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:first"), milestone(1, "test:first_claim"),
                id("test:second"), milestone(2, "test:second_claim")));
        var plan = ResearchPointAwardMilestoneResolver.retroactivePlan(
                snapshot,
                PROFILE,
                MilestoneState.DISCOVERED,
                Set.of(PISTOL_ONE, PISTOL_TWO),
                facts());

        assertFalse(plan.complete());
        assertTrue(plan.step());
        assertFalse(plan.complete());
        assertTrue(plan.step());
        assertTrue(plan.complete());
        assertFalse(plan.step());
        assertEquals(2, plan.finish().awards().size());
    }

    @Test
    void queuedMilestoneCanBeRevalidatedAgainstCurrentProgression() {
        ResearchPointAwardDefinition definition = milestone(2, "test:claim");

        assertTrue(ResearchPointAwardMilestoneResolver.currentlySatisfied(
                definition,
                PROFILE,
                DispatchMode.RETROACTIVE,
                Set.of(PISTOL_ONE, PISTOL_TWO),
                facts()));
        assertFalse(ResearchPointAwardMilestoneResolver.currentlySatisfied(
                definition,
                PROFILE,
                DispatchMode.RETROACTIVE,
                Set.of(PISTOL_ONE),
                facts()));
    }

    private static Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts() {
        return Map.of(
                PISTOL_ONE, new ResearchPointAwardBlueprintFacts(
                        PISTOL_ONE, Set.of(id("test:sidearms")), "pistol", BlueprintKind.GUN),
                PISTOL_TWO, new ResearchPointAwardBlueprintFacts(
                        PISTOL_TWO, Set.of(id("test:sidearms")), "pistol", BlueprintKind.GUN),
                RIFLE, new ResearchPointAwardBlueprintFacts(
                        RIFLE, Set.of(), "rifle", BlueprintKind.GUN));
    }

    private static ResearchPointAwardDefinition milestone(int threshold, String claimId) {
        String json = """
                {
                  "format": 1,
                  "profiles": ["test:profile"],
                  "award_group": "test:milestones",
                  "trigger": {
                    "type": "blueprint_milestone",
                    "retroactive": true,
                    "target": {"catalog_selector": {"category": "pistol"}},
                    "milestone": {"state": "discovered", "threshold": %d}
                  },
                  "reward": {"points": 2, "overflow": "clamp"},
                  "repeat": {"type": "once", "claim_id": "%s"},
                  "presentation": {"visibility": "public", "name": "test.milestone"}
                }
                """.formatted(threshold, claimId);
        return ResearchPointAwardDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result().orElseThrow();
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
