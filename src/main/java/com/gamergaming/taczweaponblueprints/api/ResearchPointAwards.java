package com.gamergaming.taczweaponblueprints.api;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardService;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointPresentationService;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardResolver;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Stable server-only integration entry point for datapack-authored RP awards. */
public final class ResearchPointAwards {
    private static final Set<ResourceLocation> REGISTERED_SOURCES = new LinkedHashSet<>();

    private ResearchPointAwards() {
    }

    /**
     * Registers a stable integration event ID. Registration is idempotent and
     * bounded for the lifetime of the game process.
     */
    public static synchronized boolean registerSource(ResourceLocation sourceId) {
        validateSource(sourceId);
        if (REGISTERED_SOURCES.contains(sourceId)) {
            return false;
        }
        if (REGISTERED_SOURCES.size()
                >= PlayerProgressionLimits.MAX_RESEARCH_POINT_INTEGRATION_SOURCES) {
            throw new IllegalStateException("Research Point integration source limit reached");
        }
        REGISTERED_SOURCES.add(sourceId);
        return true;
    }

    public static synchronized boolean isRegistered(ResourceLocation sourceId) {
        return sourceId != null && REGISTERED_SOURCES.contains(sourceId);
    }

    public static synchronized List<ResourceLocation> registeredSources() {
        return REGISTERED_SOURCES.stream()
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    /** Triggers all winning award groups matching one registered integration ID. */
    public static Result trigger(ServerPlayer player, ResourceLocation sourceId) {
        return triggerInternal(player, sourceId, isRegistered(sourceId));
    }

    /** Permission-checked bridge used by command functions and operators. */
    public static Result triggerFromCommand(
            CommandSourceStack commandSource,
            ServerPlayer player,
            ResourceLocation sourceId) {
        boolean trusted = commandSource != null
                && commandSource.hasPermission(2)
                && player != null
                && commandSource.getServer() == player.server;
        return triggerInternal(player, sourceId, trusted);
    }

    private static Result triggerInternal(
            ServerPlayer player,
            ResourceLocation sourceId,
            boolean trustedSource) {
        if (player == null || player.server == null || player.level().isClientSide) {
            return Result.failure(Status.INVALID_PLAYER);
        }
        if (!player.server.isSameThread()) {
            return Result.failure(Status.WRONG_THREAD);
        }
        if (!trustedSource) {
            return Result.failure(Status.UNREGISTERED_SOURCE);
        }
        try {
            validateSource(sourceId);
        } catch (IllegalArgumentException exception) {
            return Result.failure(Status.INVALID_SOURCE);
        }
        ResearchPointAwardConfigSnapshot config = ModConfigs.BLUEPRINT.awardSnapshot();
        if (config == null || !config.awardsEnabled()) {
            return Result.failure(Status.DISABLED);
        }
        var data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElse(null);
        if (data == null) {
            return Result.failure(Status.PLAYER_DATA_UNAVAILABLE);
        }
        ResearchPointAwardContext context = ResearchPointAwardContext.simple(
                ResearchPointAwardTrigger.Type.INTEGRATION,
                config.activeProfileId(),
                sourceId);
        ResearchPointAwardResolver.Resolution resolution =
                ResearchPointAwardDataManager.INSTANCE.resolve(context);
        if (!resolution.successful() || resolution.awards().isEmpty()) {
            return Result.failure(Status.NO_MATCH);
        }
        ResearchPointAwardService.BatchResult result = ResearchPointAwardService.awardResolved(
                player,
                data,
                resolution,
                context,
                config,
                Math.max(0L, player.server.overworld().getGameTime()));
        if (result.pointsChanged()) {
            NetworkHandler.syncPlayerPointBalance(player);
        }
        if (result.changed()) {
            ResearchPointPresentationService.syncHelp(player);
        }
        ResearchPointPresentationService.sendFeedback(player, resolution, result, context);
        int committed = (int) result.awards().stream()
                .filter(ResearchPointAwardService.AwardResult::committed)
                .count();
        return new Result(
                committed > 0 ? Status.TRIGGERED : Status.NO_ELIGIBLE_AWARD,
                result.awardedPoints(),
                committed);
    }

    private static void validateSource(ResourceLocation sourceId) {
        if (sourceId == null || sourceId.toString().length()
                > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
            throw new IllegalArgumentException("invalid Research Point integration source ID");
        }
    }

    public enum Status {
        TRIGGERED,
        NO_ELIGIBLE_AWARD,
        NO_MATCH,
        DISABLED,
        PLAYER_DATA_UNAVAILABLE,
        UNREGISTERED_SOURCE,
        INVALID_SOURCE,
        INVALID_PLAYER,
        WRONG_THREAD;

        public String serializedName() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    public record Result(Status status, int awardedPoints, int committedAwards) {
        public Result {
            if (status == null || awardedPoints < 0 || committedAwards < 0
                    || committedAwards > PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_GROUPS_PER_EVENT) {
                throw new IllegalArgumentException("invalid Research Point integration result");
            }
        }

        public boolean successful() {
            return status == Status.TRIGGERED;
        }

        private static Result failure(Status status) {
            return new Result(status, 0, 0);
        }
    }
}
