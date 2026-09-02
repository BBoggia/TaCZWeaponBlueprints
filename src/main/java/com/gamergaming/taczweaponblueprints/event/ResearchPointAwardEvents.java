package com.gamergaming.taczweaponblueprints.event;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointAwardDispatcher;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointCombatTracker;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Authoritative Forge entry points for finite Research Point awards. */
@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ResearchPointAwardEvents {
    private ResearchPointAwardEvents() {
    }

    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            ResearchPointAwardDispatcher.advancementCompleted(
                    player, event.getAdvancement().getId());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onMobFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
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
