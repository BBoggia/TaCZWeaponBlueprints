package com.gamergaming.taczweaponblueprints.event;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeDataProvider;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CapabilityEventHandler {

    private static final ResourceLocation PLAYER_RECIPE_DATA_ID =
            new ResourceLocation("taczweaponblueprints", "player_recipe_data");

    @SubscribeEvent
    public static void attachCapabilities(final AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            PlayerRecipeDataProvider provider = new PlayerRecipeDataProvider();
            event.addCapability(PLAYER_RECIPE_DATA_ID, provider);
            event.addListener(provider::invalidate);
        }
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // Forge also clones players when they leave the End. Copy on every clone,
        // not only death clones, so learned recipes survive that transition.
        event.getOriginal().reviveCaps();
        try {
            event.getOriginal().getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(oldData -> {
                event.getEntity().getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(newData -> {
                    copyRecipeData(oldData, newData);
                });
            });
        } finally {
            event.getOriginal().invalidateCaps();
        }
    }

    static void copyRecipeData(IPlayerRecipeData original, IPlayerRecipeData clone) {
        clone.deserializeNBT(original.serializeNBT());
    }
}
