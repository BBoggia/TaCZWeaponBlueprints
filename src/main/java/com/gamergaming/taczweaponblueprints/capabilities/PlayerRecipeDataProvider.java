package com.gamergaming.taczweaponblueprints.capabilities;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import org.jetbrains.annotations.NotNull;

import com.gamergaming.taczweaponblueprints.init.ModCapabilities;

import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.*;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;

public class PlayerRecipeDataProvider implements ICapabilitySerializable<CompoundTag> {
    private final IPlayerRecipeData backend = new PlayerRecipeData();
    private final LazyOptional<IPlayerRecipeData> optionalData = LazyOptional.of(() -> backend);

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        return cap == ModCapabilities.PLAYER_RECIPE_DATA ? optionalData.cast() : LazyOptional.empty();
    }

    @Override
    public CompoundTag serializeNBT() {
        return backend.serializeNBT();
    }

    @Override
    public void deserializeNBT(CompoundTag nbt) {
        backend.deserializeNBT(nbt);
    }
}