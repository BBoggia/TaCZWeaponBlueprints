package com.gamergaming.taczweaponblueprints.progression;

import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Applies exact configured starting knowledge without awards or revocation. */
public final class StartingBlueprintGrantService {
    private StartingBlueprintGrantService() {
    }

    public static Result applyConfiguredGrants(ServerPlayer player) {
        if (player == null) {
            return Result.EMPTY;
        }
        IPlayerRecipeData data = player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .resolve().orElse(null);
        if (data == null) {
            return Result.unavailablePlayerData();
        }
        var access = ModConfigs.BLUEPRINT.accessSnapshot();
        Result result = apply(
                data,
                access.startingBlueprints(),
                ModConfigs.BLUEPRINT.progressionSnapshot().blueprintsEnabled(),
                access,
                BlueprintDataManager.SERVER.getBlueprintDataMap(),
                id -> BlueprintResearchDataManager.INSTANCE.policyFor(id, data));
        if (result.capacityReached() || result.rejected() > 0) {
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Starting-blueprint grants for {} left {} entries rejected (capacity reached={})",
                    player.getGameProfile().getName(),
                    result.rejected(),
                    result.capacityReached());
        }
        return result;
    }

    static Result apply(
            IPlayerRecipeData playerData,
            Set<ResourceLocation> configuredIds,
            boolean blueprintsEnabled,
            BlueprintAccessConfigSnapshot access,
            Map<ResourceLocation, BlueprintData> catalog,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver) {
        if (playerData == null) {
            return Result.unavailablePlayerData();
        }
        if (configuredIds == null || configuredIds.isEmpty()) {
            return Result.EMPTY;
        }
        int granted = 0;
        int alreadyKnown = 0;
        int exempt = 0;
        int unavailable = 0;
        int blocked = 0;
        int rejected = 0;
        boolean capacityReached = false;
        for (ResourceLocation id : configuredIds.stream().sorted().toList()) {
            BlueprintData blueprint = catalog == null ? null : catalog.get(id);
            if (blueprint == null || blueprint.getRecipeId() == null) {
                unavailable++;
                continue;
            }
            if (BlueprintProgressionAccess.isProgressionExempt(access, id, blueprint)) {
                exempt++;
                continue;
            }
            BlueprintLearningService.Result result = BlueprintLearningService.learn(
                    new BlueprintLearningService.Request(
                            BlueprintUnlockOrigin.STARTING_GRANT,
                            id,
                            blueprintsEnabled,
                            PhysicalBlueprintLearningMode.DISABLED,
                            false),
                    playerData,
                    target -> BlueprintLearningService.targetFromCatalogMap(catalog, target),
                    policyResolver);
            switch (result.status()) {
                case SUCCESS -> granted++;
                case ALREADY_LEARNED -> alreadyKnown++;
                case BLOCKED -> blocked++;
                case CONTENT_UNAVAILABLE, INVALID_IDENTITY -> unavailable++;
                case PROGRESSION_CAPACITY_EXHAUSTED -> {
                    capacityReached = true;
                    rejected++;
                }
                default -> rejected++;
            }
        }
        return new Result(
                configuredIds.size(),
                granted,
                alreadyKnown,
                exempt,
                unavailable,
                blocked,
                rejected,
                capacityReached,
                false);
    }

    public record Result(
            int configured,
            int granted,
            int alreadyKnown,
            int progressionExempt,
            int unavailable,
            int blocked,
            int rejected,
            boolean capacityReached,
            boolean playerDataUnavailable) {
        private static final Result EMPTY = new Result(0, 0, 0, 0, 0, 0, 0, false, false);

        public Result {
            if (configured < 0 || granted < 0 || alreadyKnown < 0
                    || progressionExempt < 0 || unavailable < 0 || blocked < 0
                    || rejected < 0) {
                throw new IllegalArgumentException("invalid starting-grant result");
            }
        }

        public boolean changed() {
            return granted > 0;
        }

        private static Result unavailablePlayerData() {
            return new Result(0, 0, 0, 0, 0, 0, 0, false, true);
        }
    }
}
