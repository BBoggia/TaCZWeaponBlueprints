package com.gamergaming.taczweaponblueprints.init;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.attachment.IAttachmentHolder;
import net.neoforged.neoforge.attachment.IAttachmentSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/** Persistent player knowledge. NeoForge copies it on death and dimension-return cloning. */
public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, TaCZWeaponBlueprints.MODID);
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<IPlayerRecipeData>> PLAYER_RECIPE_DATA =
            TYPES.register("player_recipe_data", () -> AttachmentType.<IPlayerRecipeData>builder(PlayerRecipeData::new)
                    .serialize(new IAttachmentSerializer<CompoundTag, IPlayerRecipeData>() {
                        @Override
                        public IPlayerRecipeData read(IAttachmentHolder holder, CompoundTag tag, HolderLookup.Provider registries) {
                            var data = new PlayerRecipeData();
                            data.deserializeNBT(tag);
                            return data;
                        }

                        @Override
                        public CompoundTag write(IPlayerRecipeData data, HolderLookup.Provider registries) {
                            return data.serializeNBT();
                        }
                    }).copyOnDeath().build());

    private ModAttachments() {}

    public static void register(IEventBus bus) { TYPES.register(bus); }

    public static Optional<IPlayerRecipeData> recipeData(Player player) {
        return player == null ? Optional.empty() : Optional.of(player.getData(PLAYER_RECIPE_DATA));
    }
}
