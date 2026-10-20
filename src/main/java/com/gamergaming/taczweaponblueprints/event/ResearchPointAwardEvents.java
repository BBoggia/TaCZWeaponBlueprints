package com.gamergaming.taczweaponblueprints.event;

import net.neoforged.fml.common.EventBusSubscriber;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardDispatcher;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointCombatTracker;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionSyncScheduler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;

/** Authoritative Forge entry points for finite Research Point awards. */
@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ResearchPointAwardEvents {
    private ResearchPointAwardEvents() {
    }

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResearchPointAwardDispatcher.advancementCompleted(
                    player, event.getAdvancement().id());
        }
    }

    @SubscribeEvent
    public static void onAdvancementProgressed(
            AdvancementEvent.AdvancementProgressEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Both grants and administrative revocations can change a live
            // advancement gate. Coalescing keeps multiple criterion changes to
            // one player publication and one open-Bench preview refresh per tick.
            BlueprintProgressionSyncScheduler.markDirty(player);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobFinalizeSpawn(net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent event) {
        if (!event.isSpawnCancelled() && event.getEntity().level() instanceof ServerLevel level) {
            ResearchPointCombatTracker.recordSpawn(
                    event.getEntity(), event.getSpawnType(), gameTime(level));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!event.isCanceled() && event.getEntity() instanceof LivingEntity living
                && event.getLevel() instanceof ServerLevel level) {
            ResearchPointCombatTracker.recordFirstServerJoin(living, gameTime(level));
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(LivingDeathEvent event) {
        if (!event.isCanceled() && event.getEntity().level() instanceof ServerLevel level) {
            ResearchPointCombatTracker.capture(
                    event.getEntity(), event.getSource(), gameTime(level))
                    .ifPresent(combat -> ResearchPointAwardDispatcher.entityKilled(
                            combat.player(),
                            combat.targetId(),
                            combat.targetTags(),
                            combat.facts()));
        }
    }

    private static long gameTime(ServerLevel level) {
        return Math.max(0L, level.getServer().overworld().getGameTime());
    }
}
