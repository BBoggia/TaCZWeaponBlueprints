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
        // reviveCaps only reopens the entity dispatcher; it cannot revive the
        // provider's invalidated LazyOptional. Serialization still owns the
        // backend and remains available after invalidation. Read only our tag,
        // without reopening another mod's capabilities on the removed player.
        var saved = event.getOriginal().serializeNBT();
        event.getEntity().getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .ifPresent(newData -> copySerializedRecipeData(saved, newData));
    }

    static void copySerializedRecipeData(net.minecraft.nbt.CompoundTag player, IPlayerRecipeData clone) {
        var caps = player.getCompound("ForgeCaps");
        if (caps.contains(PLAYER_RECIPE_DATA_ID.toString(), net.minecraft.nbt.Tag.TAG_COMPOUND))
            clone.deserializeNBT(caps.getCompound(PLAYER_RECIPE_DATA_ID.toString()).copy());
    }
}
