package com.gamergaming.taczweaponblueprints.mixin;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.tacz.TaCZWorkbenchMenuBridge;
import com.gamergaming.taczweaponblueprints.compat.magazines.MagazineCatalog;
import com.gamergaming.taczweaponblueprints.compat.magazines.MagazineCraftingAccess;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance;
import com.gamergaming.taczweaponblueprints.progression.CraftingEligibilityService;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchAuthority;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchTierResolver;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import com.tacz.guns.inventory.GunSmithTableMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Dynamic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GunSmithTableMenu.class)
public abstract class GunSmithTableMenuMixin implements TaCZWorkbenchMenuBridge {

    @Dynamic("TaCZ 1.1.8 getRecipe tab-membership callback")
    @SuppressWarnings("target")
    @Inject(method = "lambda$getRecipe$2(Lcom/tacz/guns/crafting/GunSmithTableRecipe;Lcom/tacz/guns/resource/index/CommonBlockIndex;)Ljava/lang/Boolean;",
            at = @At("RETURN"), cancellable = true, require = 1, allow = 1, remap = false)
    private static void taczweaponblueprints$acceptMagazineTab(GunSmithTableRecipe recipe,
            com.tacz.guns.resource.index.CommonBlockIndex index,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        // This callback only tests tab membership. Native recipe filters remain intact,
        // and the doCraft HEAD authority checks the exact physical supported Workbench.
        if (Boolean.TRUE.equals(cir.getReturnValue())
                && MagazineCraftingAccess.hasMagazineTab(MagazineCatalog.INSTANCE.publication().catalog(), recipe)) {
            cir.setReturnValue(false);
        }
    }

    @Unique
    private ResearchWorkbenchContext taczweaponblueprints$workbenchContext;

    @Unique
    private long taczweaponblueprints$craftingAccessRequestId;

    @Unique
    private boolean taczweaponblueprints$craftingAccessRetryAccepted;

    @Unique
    private long taczweaponblueprints$craftingAccessSnapshotId;

    @Override
    public void taczweaponblueprints$attachWorkbenchContext(
            ResearchWorkbenchContext context) {
        if (context == null) {
            throw new IllegalArgumentException("TaCZ workbench context cannot be null");
        }
        if (taczweaponblueprints$workbenchContext != null
                && !taczweaponblueprints$workbenchContext.equals(context)) {
            throw new IllegalStateException("TaCZ workbench context is already attached");
        }
        taczweaponblueprints$workbenchContext = context;
    }

    @Override
    public Optional<ResearchWorkbenchContext> taczweaponblueprints$workbenchContext() {
        return Optional.ofNullable(taczweaponblueprints$workbenchContext);
    }

    @Override
    public boolean taczweaponblueprints$acceptCraftingAccessRequest(long requestId) {
        if (requestId < 1L) {
            return false;
        }
        if (taczweaponblueprints$craftingAccessRequestId == 0L) {
            taczweaponblueprints$craftingAccessRequestId = requestId;
            return true;
        }
        // Permit one recovery resend of the exact original request. Later
        // policy, knowledge, and game-mode refreshes reuse the stored request
        // ID without passing through this admission path. Capping the client
        // to one retry prevents request-ID escalation from amplifying the
        // bounded but potentially large access response.
        if (requestId == taczweaponblueprints$craftingAccessRequestId
                && !taczweaponblueprints$craftingAccessRetryAccepted) {
            taczweaponblueprints$craftingAccessRetryAccepted = true;
            return true;
        }
        return false;
    }

    @Override
    public long taczweaponblueprints$craftingAccessRequestId() {
        return taczweaponblueprints$craftingAccessRequestId;
    }

    @Override
    public long taczweaponblueprints$nextCraftingAccessSnapshotId() {
        if (taczweaponblueprints$craftingAccessSnapshotId == Long.MAX_VALUE) {
            throw new IllegalStateException("TaCZ crafting access snapshot ID exhausted");
        }
        taczweaponblueprints$craftingAccessSnapshotId++;
        return taczweaponblueprints$craftingAccessSnapshotId;
    }

    @Unique
    private ResourceLocation taczweaponblueprints$activeCraftRecipe;

    // The 1.21 recipe value no longer owns its ID. Scope the requested ID to
    // this synchronous craft, restoring it even if another mod throws or reenters.
    @com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod(method = "doCraft", remap = false)
    private void taczweaponblueprints$withCraftIdentity(ResourceLocation recipeId, Player player,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        ResourceLocation previous = taczweaponblueprints$activeCraftRecipe;
        taczweaponblueprints$activeCraftRecipe = recipeId;
        try {
            original.call(recipeId, player);
        } finally {
            taczweaponblueprints$activeCraftRecipe = previous;
        }
    }

    @Dynamic("TaCZ compiler-generated crafting lambda, pinned by GunSmithTableMenuMixinContractTest")
    @SuppressWarnings("target")
    @Redirect(
            method = "lambda$doCraft$3(Lnet/minecraft/world/entity/player/Player;"
                    + "Lcom/tacz/guns/crafting/GunSmithTableRecipe;"
                    + "Lnet/neoforged/neoforge/items/IItemHandler;)V",
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
        // mutated. The enclosing craft supplies its canonical recipe ID; the
        // lambda supplies the player and recipe output.
        ItemStack crafted = output.copy();
        if (player != null && !player.level().isClientSide()
                && !player.isCreative() && recipe != null) {
            ResourceLocation recipeId = taczweaponblueprints$activeCraftRecipe;
            if (recipeId != null && recipeId.toString().length()
                    <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH) {
                PhysicalWeaponProvenance.stampCrafted(crafted, recipeId);
            }
        }
        return crafted;
    }

    @Inject(method = "doCraft", at = @At("HEAD"), cancellable = true, remap = false)
    private void requireLearnedRecipe(ResourceLocation recipeId, Player player, CallbackInfo ci) {
        if (player.level().isClientSide()) {
            return;
        }
        CraftingEligibilityService.Evaluation result = player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                ? CraftingEligibilityService.evaluate(
                        serverPlayer, (GunSmithTableMenu) (Object) this, recipeId)
                : CraftingEligibilityService.Evaluation.blocked(
                        CraftingEligibilityService.Status.INVALID_REQUEST);
        if (!result.allowed()) {
            TaCZWeaponBlueprints.LOGGER.debug(
                    "Denied TaCZ recipe {} requested by {}: {}",
                    recipeId,
                    player.getGameProfile().getName(),
                    result.status());
            player.displayClientMessage(
                    Component.translatable(result.status().translationKey()), true);
            ci.cancel();
        }
    }

    @Inject(method = "stillValid", at = @At("HEAD"), cancellable = true)
    private void validatePhysicalWorkbench(
            Player player,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<Boolean> cir) {
        boolean nativeWorkbench = CraftingWorkbenchTierResolver
                .isNativeCraftingWorkbench(((GunSmithTableMenu) (Object) this).getBlockId());
        if (!player.level().isClientSide()
                && (ModConfigs.BLUEPRINT.enableBlueprints.get() || nativeWorkbench)) {
            cir.setReturnValue(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer
                    && CraftingWorkbenchAuthority.valid(
                            serverPlayer, (GunSmithTableMenu) (Object) this));
        }
    }
}
