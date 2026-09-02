package com.gamergaming.taczweaponblueprints.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import com.gamergaming.taczweaponblueprints.client.BlueprintRecyclerScreen;

import net.minecraft.client.Minecraft;

/**
 * Treats the Analyzer as an unhandled screen so EMI does not add sidebars,
 * search, buttons, tooltips, or input handling around its focused workflow.
 *
 * <p>The pseudo target keeps EMI optional and avoids linking this mod against
 * EMI implementation classes at runtime when EMI is absent.</p>
 */
@Pseudo
@Mixin(targets = "dev.emi.emi.screen.EmiScreenBase", remap = false)
public abstract class EmiScreenBaseMixin {
    @Inject(
            method = "isEmpty",
            at = @At("HEAD"),
            cancellable = true,
            remap = false,
            require = 0)
    private void taczweaponblueprints$disableForAnalyzer(
            CallbackInfoReturnable<Boolean> callback) {
        if (Minecraft.getInstance().screen instanceof BlueprintRecyclerScreen) {
            callback.setReturnValue(true);
        }
    }
}
