package com.gamergaming.taczweaponblueprints.mixin;

import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchTierResolver;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchInteractionMode;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.inventory.GunSmithTableMenu;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Captures the physical source of TaCZ and gun-pack crafting menus. */
@Mixin(GunSmithTableBlockEntity.class)
public abstract class GunSmithTableBlockEntityMixin {
    // This Minecraft interface override is not resolved by the Mixin annotation
    // processor through TaCZ's interface hierarchy. Pin both supported names;
    // exactly one must match in either the development or production namespace.
    @Inject(method = {
            "createMenu(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            "m_7208_(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/entity/player/Player;)Lnet/minecraft/world/inventory/AbstractContainerMenu;"
    }, at = @At("RETURN"), remap = false, require = 1, allow = 1)
    private void attachPhysicalWorkbench(
            int containerId,
            Inventory inventory,
            Player player,
            CallbackInfoReturnable<AbstractContainerMenu> cir) {
        GunSmithTableBlockEntity self = (GunSmithTableBlockEntity) (Object) this;
        Level level = self.getLevel();
        AbstractContainerMenu returned = cir.getReturnValue();
        if (!(player instanceof ServerPlayer)
                || level == null || level.isClientSide()
                || !(returned instanceof GunSmithTableMenu menu)
                || !(menu instanceof TaCZWorkbenchMenuBridge bridge)) {
            return;
        }
        BlockPos position = self.getBlockPos();
        BlockState state = self.getBlockState();
        if (state.getBlock() instanceof AbstractGunSmithTableBlock table) {
            position = table.getRootPos(position, state);
        }
        ResourceLocation workstationId = self.getId() == null
                ? DefaultAssets.DEFAULT_BLOCK_ID
                : self.getId();
        var resolution = CraftingWorkbenchTierResolver.resolve(
                workstationId, ModConfigs.BLUEPRINT.researchFeatureSnapshot());
        bridge.taczweaponblueprints$attachWorkbenchContext(new ResearchWorkbenchContext(
                position,
                level.dimension().location(),
                workstationId,
                resolution.tier(),
                ResearchInteractionMode.CRAFTING,
                (long) containerId + 1L));
    }
}
