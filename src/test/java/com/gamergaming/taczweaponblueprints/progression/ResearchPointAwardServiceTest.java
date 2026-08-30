package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.CombatFacts;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.CreditType;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.Difficulty;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.DispatchMode;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.SpawnProvenance;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDefinition;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchPointAwardServiceTest {
    private static final ResourceLocation PROFILE = id("test:profile");

    @Test
    void finiteTargetClaimCreditsAtomicallyAndCannotReplay() {
        ResearchPointAwardDefinition definition = definition(
                "once_per_target",
                "\"claim_id\": \"test:discover\"",
                "\"points\": 3, \"overflow\": \"clamp\"",
                "");
        ResearchPointAwardSnapshot snapshot = ResearchPointAwardSnapshot.create(
                Map.of(id("test:award"), definition));
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                PROFILE,
                id("test:pistol"));
        ResearchPointAwardResolver.Resolution resolved =
                ResearchPointAwardResolver.resolve(snapshot, context);
        PlayerRecipeData data = new PlayerRecipeData();
        ResearchPointAwardConfigSnapshot config = config(100);

        var first = ResearchPointAwardService.awardResolved(data, resolved, context, config, 20L);
        var duplicate = ResearchPointAwardService.awardResolved(data, resolved, context, config, 21L);

        assertEquals(3, first.awardedPoints());
        assertTrue(first.changed());
        assertEquals(3, data.getResearchPoints());
        assertEquals(1, data.getResearchPointAwardLedger().claimCount());
        assertEquals(0, duplicate.awardedPoints());
        assertEquals(ResearchPointAwardService.Status.ALREADY_CLAIMED,
                duplicate.awards().get(0).status());
    }

    @Test
    void finiteClampAtCapRecordsHistoryButRepeatableZeroDoesNotConsumeRateState() {
        ResearchPointAwardDefinition finite = definition(
                "once_per_target",
                "\"claim_id\": \"test:finite\"",
                "\"points\": 2, \"overflow\": \"clamp\"",
                "");
        PlayerRecipeData data = new PlayerRecipeData();
        data.setResearchPoints(10);
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                PROFILE,
                id("test:pistol"));
        var resolved = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(id("test:finite_award"), finite)), context);

        var result = ResearchPointAwardService.awardResolved(data, resolved, context, config(10), 20L);

        assertEquals(ResearchPointAwardService.Status.LEDGER_RECORDED_AT_CAP,
                result.awards().get(0).status());
        assertEquals(1, data.getResearchPointAwardLedger().claimCount());
        assertEquals(0, data.getResearchPointAwardLedger().rateStateCount());
    }

    @Test
    void localWindowAndSharedBudgetRejectBeforeMutation() {
        ResearchPointAwardDefinition windowed = definition(
                "windowed",
                "\"window_ticks\": 100, \"max_awards\": 3, \"max_points\": 6",
                "\"points\": 2, \"overflow\": \"clamp\"",
                ", \"budget\": {\"id\": \"test:shared\", \"max_awards\": 2, "
                        + "\"max_points\": 3, \"window_ticks\": 100}");
        PlayerRecipeData data = new PlayerRecipeData();
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                PROFILE,
                id("test:pistol"));
        var resolved = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(id("test:windowed"), windowed)), context);

        var first = ResearchPointAwardService.awardResolved(data, resolved, context, config(100), 10L);
        var second = ResearchPointAwardService.awardResolved(data, resolved, context, config(100), 11L);

        assertEquals(2, first.awardedPoints());
        assertEquals(ResearchPointAwardService.Status.RATE_LIMITED,
                second.awards().get(0).status());
        assertEquals(2, data.getResearchPoints());
        assertEquals(2, data.getResearchPointAwardLedger().windowEntryCount());
    }

    @Test
    void cooldownUsesPersistedServerGameTimeAndRecoversAtTheBoundary() {
        ResearchPointAwardDefinition cooldown = definition(
                "cooldown",
                "\"cooldown_ticks\": 20",
                "\"points\": 1, \"overflow\": \"clamp\"",
                "");
        PlayerRecipeData data = new PlayerRecipeData();
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                PROFILE,
                id("test:pistol"));
        var resolved = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(id("test:cooldown"), cooldown)), context);

        assertEquals(1, ResearchPointAwardService.awardResolved(
                data, resolved, context, config(100), 10L).awardedPoints());
        assertEquals(ResearchPointAwardService.Status.COOLDOWN_ACTIVE,
                ResearchPointAwardService.awardResolved(
                        data, resolved, context, config(100), 29L).awards().get(0).status());
        assertEquals(1, ResearchPointAwardService.awardResolved(
                data, resolved, context, config(100), 30L).awardedPoints());
        assertEquals(2, data.getResearchPoints());
    }

    @Test
    void localWindowExpiresWithoutEvictingUnexpiredProtection() {
        ResearchPointAwardDefinition windowed = definition(
                "windowed",
                "\"window_ticks\": 100, \"max_awards\": 2, \"max_points\": 4",
                "\"points\": 2, \"overflow\": \"clamp\"",
                "");
        PlayerRecipeData data = new PlayerRecipeData();
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                PROFILE,
                id("test:pistol"));
        var resolved = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(id("test:window"), windowed)), context);

        assertEquals(2, ResearchPointAwardService.awardResolved(
                data, resolved, context, config(100), 10L).awardedPoints());
        assertEquals(2, ResearchPointAwardService.awardResolved(
                data, resolved, context, config(100), 20L).awardedPoints());
        assertEquals(ResearchPointAwardService.Status.RATE_LIMITED,
                ResearchPointAwardService.awardResolved(
                        data, resolved, context, config(100), 109L).awards().get(0).status());
        assertEquals(2, ResearchPointAwardService.awardResolved(
                data, resolved, context, config(100), 110L).awardedPoints());
        assertEquals(6, data.getResearchPoints());
        assertEquals(2, data.getResearchPointAwardLedger().windowEntryCount());
    }

    @Test
    void sequentialPreviewDoesNotPruneOrOtherwiseMutateTheLiveLedger() {
        ResearchPointAwardDefinition windowed = definition(
                "windowed",
                "\"window_ticks\": 100, \"max_awards\": 2, \"max_points\": 4",
                "\"points\": 2, \"overflow\": \"clamp\"",
                "");
        PlayerRecipeData data = new PlayerRecipeData();
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                PROFILE,
                id("test:pistol"));
        var resolved = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(id("test:window"), windowed)), context);
        ResearchPointAwardConfigSnapshot config = config(100);
        ResearchPointAwardService.awardResolved(data, resolved, context, config, 10L);

        var preview = ResearchPointAwardService.evaluateResolved(
                data, resolved, context, config, 200L);

        assertTrue(preview.eligible());
        assertEquals(2, preview.awardablePoints());
        assertEquals(1, data.getResearchPointAwardLedger().windowEntryCount());
        assertEquals(2, data.getResearchPoints());
    }

    @Test
    void killSwitchesAndStaleProfileFailClosed() {
        ResearchPointAwardDefinition definition = definition(
                "once_per_target",
                "\"claim_id\": \"test:discover\"",
                "\"points\": 3, \"overflow\": \"clamp\"",
                "");
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                PROFILE,
                id("test:pistol"));
        var resolved = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(id("test:award"), definition)), context);
        PlayerRecipeData data = new PlayerRecipeData();

        var disabled = ResearchPointAwardService.awardResolved(
                data,
                resolved,
                context,
                new ResearchPointAwardConfigSnapshot(false, false, 100, PROFILE),
                1L);
        var stale = ResearchPointAwardService.awardResolved(
                data,
                resolved,
                context,
                new ResearchPointAwardConfigSnapshot(true, false, 100, id("test:other")),
                1L);

        assertEquals(ResearchPointAwardService.Status.DISABLED, disabled.awards().get(0).status());
        assertEquals(ResearchPointAwardService.Status.STALE_RESOLUTION, stale.awards().get(0).status());
        assertFalse(disabled.changed());
        assertEquals(0, data.getResearchPoints());
    }

    @Test
    void combatKillSwitchRejectsBeforeMutatingRateOrPointState() {
        String json = """
                {
                  "format": 1,
                  "profiles": ["test:profile"],
                  "award_group": "test:combat",
                  "trigger": {
                    "type": "entity_killed",
                    "target": {"ids": ["minecraft:zombie"]}
                  },
                  "reward": {"points": 2, "overflow": "clamp"},
                  "repeat": {"type": "cooldown", "cooldown_ticks": 20},
                  "presentation": {"visibility": "public", "name": "test.combat"}
                }
                """;
        ResearchPointAwardDefinition definition = ResearchPointAwardDefinition.CODEC
                .parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow();
        ResearchPointAwardContext context = new ResearchPointAwardContext(
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type.ENTITY_KILLED,
                PROFILE,
                DispatchMode.LIVE,
                Optional.of(id("minecraft:zombie")),
                Set.of(),
                Optional.empty(), Optional.empty(), Optional.empty(),
                0, 0,
                Optional.of(new CombatFacts(
                        CreditType.DIRECT, false, false, false, false, false, false,
                        Optional.of(SpawnProvenance.NATURAL), 100L,
                        id("minecraft:overworld"), Difficulty.NORMAL, false)));
        var resolved = ResearchPointAwardResolver.resolve(
                ResearchPointAwardSnapshot.create(Map.of(id("test:combat"), definition)), context);
        PlayerRecipeData data = new PlayerRecipeData();

        var disabled = ResearchPointAwardService.awardResolved(
                data, resolved,
                context, new ResearchPointAwardConfigSnapshot(true, false, 100, PROFILE), 1L);
        var enabled = ResearchPointAwardService.awardResolved(
                data, resolved,
                context, new ResearchPointAwardConfigSnapshot(true, true, 100, PROFILE), 1L);

        assertEquals(ResearchPointAwardService.Status.COMBAT_DISABLED,
                disabled.awards().get(0).status());
        assertFalse(disabled.changed());
        assertEquals(2, enabled.awardedPoints());
        assertEquals(2, data.getResearchPoints());
        assertEquals(1, data.getResearchPointAwardLedger().rateStateCount());
    }

    private static ResearchPointAwardDefinition definition(
            String repeatType,
            String repeatFields,
            String reward,
            String extraTopLevel) {
        String json = """
                {
                  "format": 1,
                  "profiles": ["test:profile"],
                  "award_group": "test:group",
                  "trigger": {"type": "blueprint_discovered"},
                  "reward": {%s},
                  "repeat": {"type": "%s", %s},
                  "presentation": {"visibility": "public", "name": "test.award"}%s
                }
                """.formatted(reward, repeatType, repeatFields, extraTopLevel);
        return ResearchPointAwardDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .result().orElseThrow();
    }

    private static ResearchPointAwardConfigSnapshot config(int cap) {
        return new ResearchPointAwardConfigSnapshot(true, false, cap, PROFILE);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
