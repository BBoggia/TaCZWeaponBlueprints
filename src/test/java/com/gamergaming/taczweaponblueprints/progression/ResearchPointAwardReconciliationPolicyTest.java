package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDefinition;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

class ResearchPointAwardReconciliationPolicyTest {
    @Test
    void cappedWorkSleepsUntilTheBalanceActuallyDecreases() {
        var queue = new ResearchPointAwardReconciliationScheduler.RetryableWorkQueue<String, String>();
        queue.defer("first", "award-one", 100);
        queue.defer("second", "award-two", 80);

        assertTrue(queue.wakeAfterBalanceDecrease(100).isEmpty());
        assertTrue(queue.wakeAfterBalanceDecrease(101).isEmpty());
        assertEquals(List.of("award-one", "award-two"), queue.wakeAfterBalanceDecrease(99));
        assertTrue(queue.isEmpty());
    }

    @Test
    void duplicateClaimDoesNotCreateDuplicateRetryWork() {
        var queue = new ResearchPointAwardReconciliationScheduler.RetryableWorkQueue<String, String>();
        queue.defer("claim", "original", 50);
        queue.defer("claim", "replacement", 50);

        assertEquals(1, queue.size());
        assertEquals(List.of("original"), queue.wakeAfterBalanceDecrease(49));
    }

    @Test
    void liveRetroactiveFiniteCapFailureRequestsReconciliation() {
        String json = """
                {
                  "format": 1,
                  "profiles": ["test:profile"],
                  "award_group": "test:discover",
                  "trigger": {"type": "blueprint_discovered", "retroactive": true},
                  "reward": {"points": 2, "overflow": "require_full"},
                  "repeat": {"type": "once_per_target", "claim_id": "test:discover"},
                  "presentation": {"visibility": "hidden"}
                }
                """;
        ResearchPointAwardDefinition definition = ResearchPointAwardDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
        ResourceLocation profile = new ResourceLocation("test:profile");
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                profile,
                new ResourceLocation("test:pistol"));
        ResearchPointAwardResolver.Resolution resolution = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(
                        new ResourceLocation("test:award"), definition)),
                context);
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(10);
        ResearchPointAwardService.BatchResult result = ResearchPointAwardService.awardResolved(
                data,
                resolution,
                context,
                new ResearchPointAwardConfigSnapshot(true, false, 10, profile),
                10L);

        assertEquals(ResearchPointAwardService.Status.POINT_CAP_REACHED,
                result.awards().get(0).status());
        assertTrue(ResearchPointAwardDispatcher.retryRequired(
                resolution.awards().get(0), result.awards().get(0)));
    }
}
