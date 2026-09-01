package com.gamergaming.taczweaponblueprints.mixin;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionAccess;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunSmithTableMenu.class)
public abstract class GunSmithTableMenuMixin {

    @Dynamic("TaCZ compiler-generated crafting lambda, pinned by GunSmithTableMenuMixinContractTest")
    @SuppressWarnings("target")
    @Redirect(
            method = "lambda$doCraft$3(Lnet/minecraft/world/entity/player/Player;"
                    + "Lcom/tacz/guns/crafting/GunSmithTableRecipe;"
                    + "Lnet/minecraftforge/items/IItemHandler;)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/item/ItemStack;copy()Lnet/minecraft/world/item/ItemStack;",
                    remap = true),
            require = 1,
            allow = 1,
            remap = false)
    private ItemStack markSurvivalCraftedOutput(
            ItemStack output,
            Player player,
            GunSmithTableRecipe recipe,
            IItemHandler ignoredHandler) {
        // TaCZ constructs the dropped result inside this captured lambda. Copy
        // first, exactly as TaCZ does, so the cached recipe output is never
        // mutated. Capturing the lambda arguments gives us both the player and
        // canonical recipe identity without any thread-local transaction state.
        ItemStack crafted = output.copy();
        if (player != null && !player.level().isClientSide()
                && !player.isCreative() && recipe != null) {
            ResourceLocation recipeId = recipe.getId();
            if (recipeId != null && recipeId.toString().length()
                    <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                PhysicalWeaponProvenance.stampCrafted(crafted, recipeId);
            }
        }
        return crafted;
    }

    @Inject(method = "doCraft", at = @At("HEAD"), cancellable = true, remap = false)
    private void requireLearnedRecipe(ResourceLocation recipeId, Player player, CallbackInfo ci) {
        if (player.level().isClientSide() || !ModConfigs.BLUEPRINT.enableBlueprints.get()) {
            return;
        }

        ResourceLocation canonicalRecipe = BlueprintDataManager.SERVER.getCanonicalRecipeId(recipeId);
        ResourceLocation blueprintId = BlueprintDataManager.SERVER.getBlueprintIdForRecipe(recipeId);
        boolean learned = recipeId != null
                && recipeId.equals(canonicalRecipe)
                && blueprintId != null
                && (BlueprintProgressionAccess.isProgressionExempt(blueprintId)
                    || player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                        .map(recipeData -> recipeData.hasBlueprint(blueprintId.toString())
                                || recipeData.hasRecipe(recipeId.toString()))
                        .orElse(false));
        if (!learned) {
            TaCZWeaponBlueprints.LOGGER.debug(
                    "Denied locked TaCZ recipe {} requested by {}",
                    recipeId,
                    player.getGameProfile().getName());
            ci.cancel();
        }
    }
}
