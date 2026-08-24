package com.gamergaming.taczweaponblueprints.mixin;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunSmithTableMenu.class)
public abstract class GunSmithTableMenuMixin {

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
                && player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                        .map(recipeData -> recipeData.hasBlueprint(blueprintId.toString())
                                || recipeData.hasRecipe(recipeId.toString()))
                        .orElse(false);
        if (!learned) {
            TaCZWeaponBlueprints.LOGGER.debug(
                    "Denied locked TaCZ recipe {} requested by {}",
                    recipeId,
                    player.getGameProfile().getName());
            ci.cancel();
        }
    }
}
