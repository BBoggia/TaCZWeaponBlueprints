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
    @Shadow @Final private ItemStack baseCostA;
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
        if (terms != null && ((baseCostA.isEmpty() && terms.available()) || terms.minimumPrimary() > baseCostA.getMaxStackSize()))
            throw new IllegalArgumentException("Invalid minimum for currency");
        taczweaponblueprints$terms = terms;
    }
    @Inject(method = "<init>(Lnet/minecraft/nbt/CompoundTag;)V", at = @At("RETURN"))
    private void loadTerms(CompoundTag tag, CallbackInfo ci) {
        if (!tag.contains(VillagerOfferTerms.TAG) && !tag.contains(com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding.TAG)) return;
        try {
            for(var name:java.util.List.of("buy","buyB","sell")) {
                var raw=tag.getCompound(name);var id=net.minecraft.resources.ResourceLocation.tryParse(raw.getString("id"));
                if(id!=null&&!id.equals(new net.minecraft.resources.ResourceLocation("minecraft:air"))&&raw.getByte("Count")>0&&raw.getByte("Count")<=64
                        && (!net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id)||net.minecraftforge.registries.ForgeRegistries.ITEMS.getValue(id)==net.minecraft.world.item.Items.AIR))
                    taczweaponblueprints$dormantStacks.put(name,raw.copy());
            }
            if ((baseCostA.isEmpty() && !taczweaponblueprints$dormantStacks.containsKey("buy")) || baseCostA.getCount() > baseCostA.getMaxStackSize()
                    || !Float.isFinite(priceMultiplier) || priceMultiplier < 0) throw new IllegalArgumentException("Invalid saved price");
            taczweaponblueprints$terms = VillagerOfferTerms.read(tag.getCompound(VillagerOfferTerms.TAG), baseCostA.isEmpty()?64:baseCostA.getMaxStackSize());
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
                    new net.minecraft.resources.ResourceLocation("taczweaponblueprints", "invalid_saved_offer"), 1, false, 0);
        }
    }
    @Inject(method = "createTag", at = @At("RETURN"))
    private void saveTerms(CallbackInfoReturnable<CompoundTag> cir) {
        if (taczweaponblueprints$terms != null) cir.getReturnValue().put(VillagerOfferTerms.TAG, taczweaponblueprints$terms.save());
        if(taczweaponblueprints$binding!=null)taczweaponblueprints$dormantStacks.forEach((name,value)->cir.getReturnValue().put(name,value.copy()));
        if (taczweaponblueprints$binding != null) cir.getReturnValue().put(com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding.TAG, taczweaponblueprints$binding.save());
    }
    @Unique private int taczweaponblueprints$price() {
        if (!Float.isFinite(priceMultiplier) || priceMultiplier < 0 || baseCostA.getCount() < 1)
            return baseCostA.getMaxStackSize();
        return VillagerTradePricing.cost(baseCostA.getCount(), demand, priceMultiplier, specialPriceDiff,
                taczweaponblueprints$terms.minimumPrimary(), baseCostA.getMaxStackSize());
    }
    @Inject(method = "getCostA", at = @At("HEAD"), cancellable = true)
    private void minimumPrice(CallbackInfoReturnable<ItemStack> cir) {
        if (taczweaponblueprints$terms != null && !baseCostA.isEmpty())
            cir.setReturnValue(baseCostA.copyWithCount(taczweaponblueprints$price()));
    }
    @Inject(method = "getSpecialPriceDiff", at = @At("RETURN"), cancellable = true)
    private void displayedPrice(CallbackInfoReturnable<Integer> cir) {
        if (taczweaponblueprints$terms == null || baseCostA.isEmpty()) return;
        // Vanilla serializes this accessor. Send the effective adjustment without mutating
        // the customer's original adjustment, so the vanilla client displays the same cost.
        int nativeBonus = Math.max(0, Mth.floor((float) (baseCostA.getCount() * demand) * priceMultiplier));
        cir.setReturnValue(taczweaponblueprints$price() - baseCostA.getCount() - nativeBonus);
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
