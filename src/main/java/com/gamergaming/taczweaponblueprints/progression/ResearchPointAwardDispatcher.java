package com.gamergaming.taczweaponblueprints.progression;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardBlueprintFacts;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.CombatFacts;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.DispatchMode;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardMilestoneResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.MilestoneState;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger.Type;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Normalizes trusted finite gameplay transitions into the shared award service. */
public final class ResearchPointAwardDispatcher {
    private ResearchPointAwardDispatcher() {
    }

    public static DispatchResult advancementCompleted(
            ServerPlayer player,
            ResourceLocation advancementId) {
        try {
            return dispatchAdvancementCompleted(player, advancementId);
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Failed closed while dispatching advancement RP award for {}",
                    advancementId,
                    exception);
            return DispatchResult.EMPTY;
        }
    }

    /** Dispatches combat facts captured from one authoritative, non-cancelled death. */
    public static DispatchResult entityKilled(
            ServerPlayer player,
            ResourceLocation entityTypeId,
            Set<ResourceLocation> entityTags,
            CombatFacts combatFacts) {
        try {
            return dispatchEntityKilled(player, entityTypeId, entityTags, combatFacts);
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Failed closed while dispatching combat RP award for {}",
                    entityTypeId,
                    exception);
            return DispatchResult.EMPTY;
        }
    }

    private static DispatchResult dispatchEntityKilled(
            ServerPlayer player,
            ResourceLocation entityTypeId,
            Set<ResourceLocation> entityTags,
            CombatFacts combatFacts) {
        if (player == null || entityTypeId == null || combatFacts == null) {
            return DispatchResult.EMPTY;
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        ResearchPointAwardConfigSnapshot config = ModConfigs.BLUEPRINT.awardSnapshot();
        if (data == null || config == null || !config.awardsEnabled()
                || !config.combatAwardsEnabled()) {
            return DispatchResult.EMPTY;
        }
        ResearchPointAwardContext context = new ResearchPointAwardContext(
                Type.ENTITY_KILLED,
                config.activeProfileId(),
                DispatchMode.LIVE,
                Optional.of(entityTypeId),
                entityTags == null ? Set.of() : entityTags,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                0,
                Optional.of(combatFacts));
        ResearchPointAwardResolver.Resolution resolution =
                ResearchPointAwardDataManager.INSTANCE.resolve(context);
        ResearchPointAwardService.BatchResult result = ResearchPointAwardService.awardResolved(
                player, data, resolution, context, config, gameTime(player));
        scheduleRetryIfNeeded(player, resolution, result);
        if (result.pointsChanged()) {
            NetworkHandler.syncPlayerPointBalance(player);
        }
        if (result.changed()) {
            ResearchPointPresentationService.syncHelp(player);
        }
        ResearchPointPresentationService.sendFeedback(player, resolution, result, context);
        return new DispatchResult(result.awardedPoints(), result.changed());
    }

    private static DispatchResult dispatchAdvancementCompleted(
            ServerPlayer player,
            ResourceLocation advancementId) {
        if (player == null || advancementId == null) {
            return DispatchResult.EMPTY;
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        ResearchPointAwardConfigSnapshot config = ModConfigs.BLUEPRINT.awardSnapshot();
        if (data == null || config == null || !config.awardsEnabled()) {
            return DispatchResult.EMPTY;
        }
        ResearchPointAwardContext context = new ResearchPointAwardContext(
                Type.ADVANCEMENT_COMPLETED,
                config.activeProfileId(),
                DispatchMode.LIVE,
                Optional.of(advancementId),
                Set.of(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                0,
                0,
                Optional.empty());
        ResearchPointAwardResolver.Resolution resolution =
                ResearchPointAwardDataManager.INSTANCE.resolve(context);
        ResearchPointAwardService.BatchResult result = ResearchPointAwardService.awardResolved(
                player, data, resolution, context, config, gameTime(player));
        scheduleRetryIfNeeded(player, resolution, result);
        if (result.pointsChanged()) {
            NetworkHandler.syncPlayerPointBalance(player);
        }
        if (result.changed()) {
            ResearchPointPresentationService.syncHelp(player);
        }
        ResearchPointPresentationService.sendFeedback(player, resolution, result, context);
        return new DispatchResult(result.awardedPoints(), result.changed());
    }

    /**
     * Dispatches only state transitions that the caller has just committed.
     * The caller owns the required full progression synchronization.
     */
    public static DispatchResult blueprintTransitions(
            ServerPlayer player,
            IPlayerRecipeData data,
            ResourceLocation blueprintId,
            boolean discoveredChanged,
            boolean learnedChanged) {
        try {
            DispatchResult result = dispatchBlueprintTransitions(
                    player, data, blueprintId, discoveredChanged, learnedChanged);
            if (player != null && (discoveredChanged || learnedChanged)) {
                ResearchPointPresentationService.syncHelp(player);
            }
            return result;
        } catch (RuntimeException exception) {
            TaCZWeaponBlueprints.LOGGER.error(
                    "Failed closed while dispatching blueprint RP award for {}",
                    blueprintId,
                    exception);
            return DispatchResult.EMPTY;
        }
    }

    private static DispatchResult dispatchBlueprintTransitions(
            ServerPlayer player,
            IPlayerRecipeData data,
            ResourceLocation blueprintId,
            boolean discoveredChanged,
            boolean learnedChanged) {
        if (player == null || data == null || blueprintId == null
                || (!discoveredChanged && !learnedChanged)) {
            return DispatchResult.EMPTY;
        }
        ResearchPointAwardConfigSnapshot config = ModConfigs.BLUEPRINT.awardSnapshot();
        if (config == null || !config.awardsEnabled()) {
            return DispatchResult.EMPTY;
        }
        var awardSnapshot = ResearchPointAwardDataManager.INSTANCE.snapshot();
        Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts =
                ResearchPointAwardBlueprintFacts.currentPublication().facts();
        ResearchPointAwardBlueprintFacts changedFacts = facts.get(blueprintId);
        if (changedFacts == null) {
            return DispatchResult.EMPTY;
        }

        int points = 0;
        boolean changed = false;
        long gameTime = gameTime(player);
        if (discoveredChanged) {
            DispatchResult direct = blueprintEvent(
                    player, data, changedFacts, Type.BLUEPRINT_DISCOVERED,
                    awardSnapshot, config, gameTime);
            DispatchResult milestone = milestoneEvent(
                    player, data,
                    blueprintId,
                    parseIds(data.getDiscoveredBlueprints()),
                    facts,
                    MilestoneState.DISCOVERED,
                    awardSnapshot,
                    config,
                    gameTime);
            points += direct.awardedPoints() + milestone.awardedPoints();
            changed |= direct.changed() || milestone.changed();
        }
        if (learnedChanged) {
            DispatchResult direct = blueprintEvent(
                    player, data, changedFacts, Type.BLUEPRINT_LEARNED,
                    awardSnapshot, config, gameTime);
            DispatchResult milestone = milestoneEvent(
                    player, data,
                    blueprintId,
                    parseIds(data.getLearnedBlueprints()),
                    facts,
                    MilestoneState.LEARNED,
                    awardSnapshot,
                    config,
                    gameTime);
            points += direct.awardedPoints() + milestone.awardedPoints();
            changed |= direct.changed() || milestone.changed();
        }
        return new DispatchResult(points, changed);
    }

    private static DispatchResult blueprintEvent(
            ServerPlayer player,
            IPlayerRecipeData data,
            ResearchPointAwardBlueprintFacts facts,
            Type type,
            com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot snapshot,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        ResearchPointAwardContext context = facts.context(
                type, config.activeProfileId(), DispatchMode.LIVE);
        ResearchPointAwardResolver.Resolution resolution =
                ResearchPointAwardResolver.resolve(snapshot, context);
        ResearchPointAwardService.BatchResult result = ResearchPointAwardService.awardResolved(
                player, data, resolution, context, config, gameTime);
        scheduleRetryIfNeeded(player, resolution, result);
        ResearchPointPresentationService.sendFeedback(player, resolution, result, context);
        return new DispatchResult(result.awardedPoints(), result.changed());
    }

    private static DispatchResult milestoneEvent(
            ServerPlayer player,
            IPlayerRecipeData data,
            ResourceLocation changedId,
            Set<ResourceLocation> currentIds,
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts,
            MilestoneState state,
            com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardSnapshot snapshot,
            ResearchPointAwardConfigSnapshot config,
            long gameTime) {
        ResearchPointAwardMilestoneResolver.Resolution resolution =
                ResearchPointAwardMilestoneResolver.resolve(
                        snapshot,
                        config.activeProfileId(),
                        DispatchMode.LIVE,
                        state,
                        currentIds,
                        Optional.of(changedId),
                        facts);
        int points = 0;
        boolean changed = false;
        boolean retryScheduled = false;
        if (resolution.successful()) {
            for (ResearchPointAwardMilestoneResolver.ResolvedMilestoneAward award
                    : resolution.awards()) {
                ResearchPointAwardService.AwardResult result = ResearchPointAwardService.awardOne(
                        player, data, award.award(), award.context(), config, gameTime);
                if (!retryScheduled && retryRequired(award.award(), result)) {
                    ResearchPointAwardReconciliationScheduler.schedule(player);
                    sendRetryScheduledFeedback(player);
                    retryScheduled = true;
                }
                ResearchPointPresentationService.sendFeedback(
                        player, award.award(), result, award.context());
                points += result.awardedPoints();
                changed |= result.committed();
            }
        }
        return new DispatchResult(points, changed);
    }

    private static void scheduleRetryIfNeeded(
            ServerPlayer player,
            ResearchPointAwardResolver.Resolution resolution,
            ResearchPointAwardService.BatchResult result) {
        if (player == null || resolution == null || result == null || !resolution.successful()) {
            return;
        }
        int count = Math.min(resolution.awards().size(), result.awards().size());
        for (int index = 0; index < count; index++) {
            if (retryRequired(resolution.awards().get(index), result.awards().get(index))) {
                ResearchPointAwardReconciliationScheduler.schedule(player);
                sendRetryScheduledFeedback(player);
                return;
            }
        }
    }

    static boolean retryRequired(
            ResearchPointAwardResolver.ResolvedAward award,
            ResearchPointAwardService.AwardResult result) {
        return award != null
                && result != null
                && result.status() == ResearchPointAwardService.Status.POINT_CAP_REACHED
                && award.binding().definition().trigger().retroactive()
                && award.binding().definition().repeat().finite();
    }

    private static void sendRetryScheduledFeedback(ServerPlayer player) {
        player.displayClientMessage(Component.translatable(
                "message.taczweaponblueprints.research_points.retry_scheduled"), true);
    }

    private static Set<ResourceLocation> parseIds(Set<String> values) {
        Set<ResourceLocation> parsed = new LinkedHashSet<>();
        if (values != null) {
            values.stream().sorted().map(ResourceLocation::tryParse)
                    .filter(java.util.Objects::nonNull).forEach(parsed::add);
        }
        return Set.copyOf(parsed);
    }

    private static long gameTime(ServerPlayer player) {
        return Math.max(0L, player.server.overworld().getGameTime());
    }

    public record DispatchResult(int awardedPoints, boolean changed) {
        private static final DispatchResult EMPTY = new DispatchResult(0, false);

        public DispatchResult {
            if (awardedPoints < 0) {
                throw new IllegalArgumentException("invalid Research Point dispatch result");
            }
        }
    }
}
