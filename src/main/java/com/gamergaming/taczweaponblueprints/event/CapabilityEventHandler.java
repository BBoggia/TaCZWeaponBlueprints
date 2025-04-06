package com.gamergaming.taczweaponblueprints.event;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeDataProvider;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.network.SyncPlayerRecipeDataPacket;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.*;

@Mod.EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class CapabilityEventHandler {

    private static final ResourceLocation PLAYER_RECIPE_DATA_ID =
            new ResourceLocation("taczweaponblueprints", "player_recipe_data");

    private static final Map<UUID, Set<String>> tempRecipeData = new HashMap<>();

    @SubscribeEvent
    public static void attachCapabilities(final AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(PLAYER_RECIPE_DATA_ID, new PlayerRecipeDataProvider());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;

        event.getOriginal().getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(oldData -> {
            event.getEntity().getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(newData -> {
                CompoundTag nbt = oldData.serializeNBT();
                newData.deserializeNBT(nbt);
            });
        });
    }

    @SubscribeEvent
    public static void onPlayerDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof Player player) {
            player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(oldData -> {
                tempRecipeData.put(player.getUUID(), new HashSet<>(oldData.getLearnedRecipes()));
            });
        }
    }

    @SubscribeEvent
    public static void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (tempRecipeData.containsKey(event.getEntity().getUUID())) {
            event.getEntity().getCapability(ModCapabilities.PLAYER_RECIPE_DATA).ifPresent(newData -> {
                newData.getLearnedRecipes().clear();
                newData.getLearnedRecipes().addAll(tempRecipeData.get(event.getEntity().getUUID()));

                if (!event.getEntity().level().isClientSide() && event.getEntity() instanceof ServerPlayer serverPlayer) {
                    NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> serverPlayer),
                            new SyncPlayerRecipeDataPacket(newData.getLearnedRecipes()));
                }
            });
            tempRecipeData.remove(event.getEntity().getUUID());
        }
    }
}