package com.gamergaming.taczweaponblueprints.event;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeDataProvider;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber
public class CapabilityEventHandler {

    private static final ResourceLocation PLAYER_RECIPE_DATA_ID = new ResourceLocation(TaCZWeaponBlueprints.MODID, "player_recipe_data");

    @SubscribeEvent
    public static void attachCapabilities(final AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(PLAYER_RECIPE_DATA_ID, new PlayerRecipeDataProvider());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        event.getOriginal().getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(oldData -> {
            event.getEntity().getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(newData -> {
                newData.deserializeNBT(oldData.serializeNBT()); // Copy data from old player to new player
            });
        });
    }
}