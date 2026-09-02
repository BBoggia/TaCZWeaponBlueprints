package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDefinition;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import net.minecraft.resources.ResourceLocation;

class ResearchDataRedemptionServiceTest {
    private static final ResourceLocation PROFILE = id("test:profile");
    private static final ResourceLocation NOTE = id("test:note");
    private static final ResourceLocation DATA_TAG = id("test:research_data");

    @Test
    void exactItemWinsOverTagWithinOneAwardGroup() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:tag"), definition("test:turn_in", 100, 2, "tags", DATA_TAG),
                id("test:exact"), definition("test:turn_in", 0, 5, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();
        MutableEntry entry = new MutableEntry(NOTE, Set.of(DATA_TAG), 3);

        ResearchDataRedemptionService.Evaluation evaluation =
                ResearchDataRedemptionService.evaluate(
                        List.of(entry), data, snapshot, config(100), 10L);

        assertTrue(evaluation.redeemable());
        assertEquals(NOTE, evaluation.itemId().orElseThrow());
        assertEquals(5, evaluation.pointValue());
        assertEquals(3, evaluation.availableItems());
        assertTrue(evaluation.matchedInput());
        assertEquals(0, data.getResearchPoints());
    }

    @Test
    void independentAwardGroupsStackForOneConsumedUnit() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:base"), definition("test:base", 0, 2, "tags", DATA_TAG),
                id("test:bonus"), definition("test:bonus", 0, 3, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();
        MutableEntry entry = new MutableEntry(NOTE, Set.of(DATA_TAG), 1);

        ResearchDataRedemptionService.Result result = ResearchDataRedemptionService.redeem(
                List.of(entry), data, snapshot, config(100), 10L, 1);

        assertTrue(result.successful());
        assertEquals(5, result.awardedPoints());
        assertEquals(1, result.consumedItems());
        assertEquals(0, entry.count());
        assertEquals(5, data.getResearchPoints());
    }

    @Test
    void stackedPreviewSimulatesCapEffectsInCommitOrder() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:base"), definition("test:base", 0, 3, "tags", DATA_TAG),
                id("test:bonus"), definition("test:bonus", 0, 6, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(4);
        MutableEntry entry = new MutableEntry(NOTE, Set.of(DATA_TAG), 1);

        ResearchDataRedemptionService.Evaluation evaluation =
                ResearchDataRedemptionService.evaluate(
                        List.of(entry), data, snapshot, config(10), 10L);
        ResearchDataRedemptionService.Result result = ResearchDataRedemptionService.redeem(
                List.of(entry), data, snapshot, config(10), 10L, 1);

        assertEquals(3, evaluation.pointValue());
        assertTrue(result.successful());
        assertEquals(3, result.awardedPoints());
        assertEquals(7, data.getResearchPoints());
        assertEquals(0, entry.count());
    }

    @Test
    void validLargeStackingValuesRemainBoundedInsteadOfOverflowingPreview() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:base"), definition("test:base", 0, 600_000_000, "tags", DATA_TAG),
                id("test:bonus"), definition("test:bonus", 0, 600_000_000, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();

        ResearchDataRedemptionService.Evaluation evaluation =
                ResearchDataRedemptionService.evaluate(
                        List.of(new MutableEntry(NOTE, Set.of(DATA_TAG), 1)),
                        data,
                        snapshot,
                        config(1_000_000_000),
                        10L);

        assertTrue(evaluation.redeemable());
        assertEquals(600_000_000, evaluation.pointValue());
        assertEquals(0, data.getResearchPoints());
    }

    @Test
    void bulkRedemptionIsBoundedByLiveInventoryAndConsumesExactlyOncePerAward() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:note"), definition("test:turn_in", 0, 3, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();
        MutableEntry entry = new MutableEntry(NOTE, Set.of(), 3);

        ResearchDataRedemptionService.Result result = ResearchDataRedemptionService.redeem(
                List.of(entry), data, snapshot, config(100), 10L, 64);

        assertEquals(ResearchDataRedemptionService.Status.SUCCESS, result.status());
        assertEquals(3, result.consumedItems());
        assertEquals(9, result.awardedPoints());
        assertEquals(0, entry.count());
        assertEquals(9, data.getResearchPoints());
    }

    @Test
    void fullAwardRequirementLeavesNearCapInventoryUntouched() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:note"), definition("test:turn_in", 0, 5, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(4);
        MutableEntry entry = new MutableEntry(NOTE, Set.of(), 2);

        ResearchDataRedemptionService.Result result = ResearchDataRedemptionService.redeem(
                List.of(entry), data, snapshot, config(7), 10L, 64);

        assertFalse(result.successful());
        assertEquals(ResearchDataRedemptionService.Status.POINT_CAP_REACHED, result.status());
        assertEquals(2, entry.count());
        assertEquals(4, data.getResearchPoints());
    }

    @Test
    void configuredButBlockedInputKeepsItsIdentityForTheSmartSlotPreview() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:note"), definition("test:turn_in", 0, 5, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(7);
        MutableEntry entry = new MutableEntry(NOTE, Set.of(), 2);

        ResearchDataRedemptionService.Evaluation evaluation =
                ResearchDataRedemptionService.evaluateInput(
                        List.of(entry), data, snapshot, config(7), 10L);

        assertEquals(ResearchDataRedemptionService.Status.POINT_CAP_REACHED,
                evaluation.status());
        assertEquals(NOTE, evaluation.itemId().orElseThrow());
        assertEquals(2, evaluation.availableItems());
        assertTrue(evaluation.matchedInput());
        assertFalse(evaluation.redeemable());
    }

    @Test
    void unconfiguredInputIsNeverClassifiedAsResearchData() {
        PlayerRecipeData data = new PlayerRecipeData();
        MutableEntry entry = new MutableEntry(NOTE, Set.of(), 2);

        ResearchDataRedemptionService.Evaluation evaluation =
                ResearchDataRedemptionService.evaluateInput(
                        List.of(entry), data, ResearchPointAwardSnapshot.EMPTY,
                        config(100), 10L);

        assertEquals(ResearchDataRedemptionService.Status.NO_MATCH, evaluation.status());
        assertFalse(evaluation.matchedInput());
        assertTrue(evaluation.itemId().isEmpty());
        assertEquals(0, evaluation.availableItems());
    }

    @Test
    void unavailableServerStateCanPreserveIdentityWithoutClaimingAMatch() {
        ResearchDataRedemptionService.Evaluation evaluation =
                new ResearchDataRedemptionService.Evaluation(
                        ResearchDataRedemptionService.Status.PLAYER_DATA_UNAVAILABLE,
                        Optional.of(NOTE), 2, 0, 0, 0);

        assertFalse(evaluation.matchedInput());
        assertEquals(Optional.of(NOTE), evaluation.itemId());
    }

    @Test
    void stalePreviewIsReevaluatedAgainstCurrentBalanceBeforeConsumption() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:note"), definition("test:turn_in", 0, 5, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();
        MutableEntry entry = new MutableEntry(NOTE, Set.of(), 1);
        assertTrue(ResearchDataRedemptionService.evaluate(
                List.of(entry), data, snapshot, config(5), 10L).redeemable());

        data.setResearchPoints(5);
        ResearchDataRedemptionService.Result result = ResearchDataRedemptionService.redeem(
                List.of(entry), data, snapshot, config(5), 11L, 1);

        assertEquals(ResearchDataRedemptionService.Status.POINT_CAP_REACHED, result.status());
        assertEquals(1, entry.count());
        assertEquals(5, data.getResearchPoints());
    }

    @Test
    void lastMomentInventoryMismatchFailsBeforePointCommit() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:note"), definition("test:turn_in", 0, 5, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();
        MutableEntry entry = new MutableEntry(NOTE, Set.of(), 1) {
            @Override
            public boolean matches(
                    ResourceLocation expectedId,
                    Set<ResourceLocation> expectedTags) {
                return false;
            }
        };

        ResearchDataRedemptionService.Result result = ResearchDataRedemptionService.redeem(
                List.of(entry), data, snapshot, config(100), 10L, 1);

        assertEquals(ResearchDataRedemptionService.Status.STALE_INVENTORY, result.status());
        assertEquals(1, entry.count());
        assertEquals(0, data.getResearchPoints());
    }

    @Test
    void inventoryChangedAfterInitialSelectionFailsBeforePointCommit() {
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(Map.of(
                id("test:note"), definition("test:turn_in", 0, 5, "ids", NOTE)));
        PlayerRecipeData data = new PlayerRecipeData();
        MutableEntry entry = new MutableEntry(NOTE, Set.of(), 1) {
            private int validations;

            @Override
            public boolean matches(
                    ResourceLocation expectedId,
                    Set<ResourceLocation> expectedTags) {
                return validations++ == 0;
            }
        };

        ResearchDataRedemptionService.Result result = ResearchDataRedemptionService.redeem(
                List.of(entry), data, snapshot, config(100), 10L, 1);

        assertEquals(ResearchDataRedemptionService.Status.STALE_INVENTORY, result.status());
        assertEquals(1, entry.count());
        assertEquals(0, data.getResearchPoints());
    }

    private static ResearchPointAwardDefinition definition(
            String group,
            int priority,
            int points,
            String selector,
            ResourceLocation target) {
        String json = """
                {
                  "format": 1,
                  "profiles": ["test:profile"],
                  "award_group": "%s",
                  "priority": %s,
                  "trigger": {
                    "type": "inventory_turn_in",
                    "target": {"%s": ["%s"]}
                  },
                  "reward": {"points": %s, "overflow": "require_full"},
                  "repeat": {"type": "unlimited"},
                  "presentation": {"visibility": "public", "name": "test.turn_in"}
                }
                """.formatted(group, priority, selector, target, points);
        return ResearchPointAwardDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result().orElseThrow();
    }

    private static ResearchPointAwardConfigSnapshot config(int cap) {
        return new ResearchPointAwardConfigSnapshot(true, false, cap, PROFILE);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }

    private static class MutableEntry implements ResearchDataRedemptionService.InventoryEntry {
        private final ResourceLocation id;
        private final Set<ResourceLocation> tags;
        private int count;

        private MutableEntry(ResourceLocation id, Set<ResourceLocation> tags, int count) {
            this.id = id;
            this.tags = Set.copyOf(tags);
            this.count = count;
        }

        @Override
        public ResourceLocation itemId() {
            return id;
        }

        @Override
        public Set<ResourceLocation> tags() {
            return tags;
        }

        @Override
        public int count() {
            return count;
        }

        @Override
        public void consumeOne() {
            count--;
        }
    }
}
