package com.gamergaming.taczweaponblueprints.mixin;

import com.gamergaming.taczweaponblueprints.villager.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;

@Mixin(MerchantOffer.class)
public abstract class VillagerMerchantOfferMixin implements OwnedVillagerOffer {
    @Shadow @Final private net.minecraft.world.item.trading.ItemCost baseCostA;
    @Shadow @Final @Mutable private static com.mojang.serialization.Codec<MerchantOffer> CODEC;
    @Shadow private int demand;
    @Shadow private float priceMultiplier;
    @Shadow private int specialPriceDiff;
    @Unique private com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding taczweaponblueprints$binding;
    @Override public com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding taczweaponblueprints$binding() { return taczweaponblueprints$binding; }
    @Override public void taczweaponblueprints$binding(com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding binding) { taczweaponblueprints$binding = binding; }
    @Unique private VillagerOfferTerms taczweaponblueprints$terms;
    @Unique private final java.util.Map<String,CompoundTag> taczweaponblueprints$dormantStacks=new java.util.HashMap<>();
    @Override public VillagerOfferTerms taczweaponblueprints$terms() { return taczweaponblueprints$terms; }
    @Override public void taczweaponblueprints$terms(VillagerOfferTerms terms) {
        if (terms != null && ((baseCostA.itemStack().isEmpty() && terms.available()) || terms.minimumPrimary() > baseCostA.itemStack().getMaxStackSize()))
            throw new IllegalArgumentException("Invalid minimum for currency");
        taczweaponblueprints$terms = terms;
    }
    @Inject(method = "<clinit>", at = @At("RETURN"))
    private static void wrapOwnershipCodec(CallbackInfo ci) {
        CODEC = OwnedOfferSerialization.withMetadata(CODEC,
                (offer, tag) -> ((OwnedVillagerOffer) offer).taczweaponblueprints$readMetadata(tag),
                (offer, tag) -> ((OwnedVillagerOffer) offer).taczweaponblueprints$writeMetadata(tag),
                OwnedOfferSerialization::preserveMissingItems);
    }
    @Inject(method = "copy", at = @At("RETURN"))
    private void copyOwnership(CallbackInfoReturnable<MerchantOffer> cir) {
        var tag = new CompoundTag();
        taczweaponblueprints$writeMetadata(tag);
        ((OwnedVillagerOffer) cir.getReturnValue()).taczweaponblueprints$readMetadata(tag);
    }
    @Override public void taczweaponblueprints$readMetadata(CompoundTag tag) {
        if (!tag.contains(VillagerOfferTerms.TAG) && !tag.contains(com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding.TAG)) return;
        try {
            for(var name:java.util.List.of("buy","buyB","sell")) {
                var raw=tag.getCompound(name);
                if(OwnedOfferSerialization.isMissingItem(raw))
                    taczweaponblueprints$dormantStacks.put(name,raw.copy());
            }
            if ((baseCostA.itemStack().isEmpty() && !taczweaponblueprints$dormantStacks.containsKey("buy")) || baseCostA.count() > baseCostA.itemStack().getMaxStackSize()
                    || !Float.isFinite(priceMultiplier) || priceMultiplier < 0) throw new IllegalArgumentException("Invalid saved price");
            taczweaponblueprints$terms = VillagerOfferTerms.read(tag.getCompound(VillagerOfferTerms.TAG), baseCostA.itemStack().isEmpty()?64:baseCostA.itemStack().getMaxStackSize());
            if(!taczweaponblueprints$dormantStacks.isEmpty())taczweaponblueprints$terms=new VillagerOfferTerms(taczweaponblueprints$terms.slot(),taczweaponblueprints$terms.minimumPrimary(),false,taczweaponblueprints$terms.revision());
            if (tag.contains(com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding.TAG)) {
                taczweaponblueprints$binding = com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding.read(tag.getCompound(com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding.TAG));
                if (!taczweaponblueprints$binding.entry().slot().equals(taczweaponblueprints$terms.slot())) throw new IllegalArgumentException("Stock binding differs from owned slot");
            }
        }
        catch (IllegalArgumentException invalid) {
            taczweaponblueprints$binding = null;
            // Preserve the ownership marker and block malformed saved stock, never turn it into an unrestricted offer.
            taczweaponblueprints$terms = new VillagerOfferTerms(
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("taczweaponblueprints", "invalid_saved_offer"), 1, false, 0);
        }
    }
    @Override public void taczweaponblueprints$writeMetadata(CompoundTag tag) {
        if (taczweaponblueprints$terms != null) tag.put(VillagerOfferTerms.TAG, taczweaponblueprints$terms.save());
        if(taczweaponblueprints$binding!=null)taczweaponblueprints$dormantStacks.forEach((name,value)->tag.put(name,value.copy()));
        if (taczweaponblueprints$binding != null) tag.put(com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding.TAG, taczweaponblueprints$binding.save());
    }
    @Unique private int taczweaponblueprints$price() {
        if (!Float.isFinite(priceMultiplier) || priceMultiplier < 0 || baseCostA.count() < 1)
            return baseCostA.itemStack().getMaxStackSize();
        return VillagerTradePricing.cost(baseCostA.count(), demand, priceMultiplier, specialPriceDiff,
                taczweaponblueprints$terms.minimumPrimary(), baseCostA.itemStack().getMaxStackSize());
    }
    // Both the displayed stack and payment validation use this method in 1.21.
    // Applying the floor only to getCostA lets an underpaid trade pass satisfiedBy.
    @Inject(method = "getModifiedCostCount", at = @At("HEAD"), cancellable = true)
    private void minimumPrice(net.minecraft.world.item.trading.ItemCost cost, CallbackInfoReturnable<Integer> cir) {
        if (taczweaponblueprints$terms != null && !baseCostA.itemStack().isEmpty())
            cir.setReturnValue(taczweaponblueprints$price());
    }
    @Inject(method = "getSpecialPriceDiff", at = @At("RETURN"), cancellable = true)
    private void displayedPrice(CallbackInfoReturnable<Integer> cir) {
        if (taczweaponblueprints$terms == null || baseCostA.itemStack().isEmpty()) return;
        // Vanilla serializes this accessor. Send the effective adjustment without mutating
        // the customer's original adjustment, so the vanilla client displays the same cost.
        int nativeBonus = Math.max(0, Mth.floor((float) (baseCostA.count() * demand) * priceMultiplier));
        cir.setReturnValue(taczweaponblueprints$price() - baseCostA.count() - nativeBonus);
    }
    @Inject(method = "isOutOfStock", at = @At("HEAD"), cancellable = true)
    private void suspendedStock(CallbackInfoReturnable<Boolean> cir) {
        if (taczweaponblueprints$terms != null && !taczweaponblueprints$terms.available()) cir.setReturnValue(true);
    }
    @Inject(method = "satisfiedBy", at = @At("HEAD"), cancellable = true)
    private void suspendedPayment(ItemStack first, ItemStack second, CallbackInfoReturnable<Boolean> cir) {
        if (taczweaponblueprints$terms != null && !taczweaponblueprints$terms.available()) cir.setReturnValue(false);
    }
}
