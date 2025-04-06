package com.gamergaming.taczweaponblueprints.event;

import java.util.Set;

import com.gamergaming.taczweaponblueprints.network.SyncBlueprintDataPacket;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import org.slf4j.Logger;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.network.SyncPlayerRecipeDataPacket;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ModEventHandler {
    private static final Logger LOGGER = TaCZWeaponBlueprints.LOGGER;

    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            BlueprintDataManager.INSTANCE.initialize(serverPlayer.getServer());

            // Sends blueprint data to client
            NetworkHandler.INSTANCE.send(
                    PacketDistributor.PLAYER.with(() -> serverPlayer),
                    new SyncBlueprintDataPacket(BlueprintDataManager.INSTANCE.getBlueprintDataMap())
            );

            LazyOptional<IPlayerRecipeData> playerLearnedRecipes = serverPlayer.getCapability(ModCapabilities.PLAYER_RECIPE_DATA);
            playerLearnedRecipes.ifPresent(recipeData -> {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new SyncPlayerRecipeDataPacket(recipeData.getLearnedRecipes()));
            });
            
            // Check if playerLearnedRecipes is not present or if the player is joining for the first time
            // LOGGER.info("\n\n\nChecking if player has learned recipes\n\n\n");
            // if (!playerLearnedRecipes.isPresent() || playerLearnedRecipes.map(data -> data.getLearnedRecipes().isEmpty()).orElse(true)) {
            //     NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
            //         new SyncPlayerRecipeDataPacket((Set<String>) ModConfigs.BLUEPRINT.startingBlueprints.get()));
            //     LOGGER.info("INSIDE IF STATEMENT: " + ModConfigs.BLUEPRINT.startingBlueprints.get().toString());
            // }
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            serverPlayer.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(recipeData -> {
                NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer), new SyncPlayerRecipeDataPacket(recipeData.getLearnedRecipes()));
            });
        }
    }
}