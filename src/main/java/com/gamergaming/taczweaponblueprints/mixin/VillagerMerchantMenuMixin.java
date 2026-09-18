package com.gamergaming.taczweaponblueprints.mixin;

import com.gamergaming.taczweaponblueprints.villager.*;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MerchantMenu.class)
public abstract class VillagerMerchantMenuMixin extends AbstractContainerMenu implements VillagerMerchantMenuBridge {
    @Shadow @Final private Merchant trader;
    @Shadow @Final private MerchantContainer tradeContainer;
    @Unique private VillagerMerchantSession taczweaponblueprints$session;
    protected VillagerMerchantMenuMixin(MenuType<?> type, int id) { super(type, id); }
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/trading/Merchant;)V", at = @At("RETURN"))
    private void captureQuote(int id, Inventory inventory, Merchant merchant, CallbackInfo ci) {
        taczweaponblueprints$session = VillagerMerchantSession.capture(merchant);
    }
    @Override public boolean taczweaponblueprints$validateQuote(Player player) {
        return taczweaponblueprints$session == null || taczweaponblueprints$session.validate(player, (MerchantMenu) (Object) this);
    }
    @Unique private boolean taczweaponblueprints$canTake(Player player) {
        if (!taczweaponblueprints$validateQuote(player)) return false;
        if (taczweaponblueprints$session == null) return true;
        var offer = tradeContainer.getActiveOffer();
        if (offer == null || !trader.getOffers().contains(offer) || offer.isOutOfStock() || !ItemStack.matches(tradeContainer.getItem(2), offer.getResult())) return false;
        var a = tradeContainer.getItem(0); var b = tradeContainer.getItem(1);
        return offer.satisfiedBy(a, b) || offer.satisfiedBy(b, a);
    }
    // MerchantMenu inherits clicked. Override only this subclass so validation precedes
    // vanilla pickup, number-key, dropping, and the quick-move dispatch alike.
    @Override public void clicked(int slot, int button, ClickType type, Player player) {
        if (!taczweaponblueprints$validateQuote(player)) return;
        boolean takesOutput = type == ClickType.PICKUP || type == ClickType.QUICK_MOVE
                || type == ClickType.SWAP || type == ClickType.THROW;
        if (slot == 2 && takesOutput && !taczweaponblueprints$canTake(player)) return;
        super.clicked(slot, button, type, player);
    }
    @Inject(method = "quickMoveStack", at = @At("HEAD"), cancellable = true)
    private void beforeQuickMove(Player player, int slot, CallbackInfoReturnable<ItemStack> cir) {
        if (!taczweaponblueprints$validateQuote(player) || (slot == 2 && (!taczweaponblueprints$canTake(player)
                || !taczweaponblueprints$wholeOutputFits())))
            cir.setReturnValue(ItemStack.EMPTY);
    }
    @Redirect(method="removed",at=@At(value="INVOKE",target="Lnet/minecraft/world/item/trading/Merchant;setTradingPlayer(Lnet/minecraft/world/entity/player/Player;)V"))
    private void closeOnlyOwnCustomer(Merchant merchant,Player ignored,Player closingPlayer) {
        if(taczweaponblueprints$session==null || merchant.getTradingPlayer()==closingPlayer)merchant.setTradingPlayer(null);
    }
    @Unique private boolean taczweaponblueprints$wholeOutputFits() {
        var offer = tradeContainer.getActiveOffer();
        if (taczweaponblueprints$session == null || offer == null || ((OwnedVillagerOffer) offer).taczweaponblueprints$terms() == null) return true;
        var output = tradeContainer.getItem(2);
        int remaining = output.getCount();
        // Vanilla quickMoveStack accepts a partial insertion, then charges the entire
        // offer. Require room for the whole owned output before it moves any items.
        for (int i = 3; i < 39 && remaining > 0; i++) {
            var target = slots.get(i); var existing = target.getItem();
            if (!target.mayPlace(output) || (!existing.isEmpty() && !ItemStack.isSameItemSameTags(existing, output))) continue;
            int limit = Math.min(target.getMaxStackSize(output), output.getMaxStackSize());
            remaining -= Math.max(0, limit - existing.getCount());
        }
        return remaining <= 0;
    }
    @Inject(method = "tryMoveItems", at = @At("HEAD"), cancellable = true)
    private void beforeAutofill(int selection, CallbackInfo ci) {
        var player = trader.getTradingPlayer();
        if (player != null && !taczweaponblueprints$validateQuote(player)) ci.cancel();
    }
}
