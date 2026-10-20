package com.gamergaming.taczweaponblueprints.villager;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.MerchantOffer;

/** Builds an owned native offer without modifying its output item. */
public final class VillagerTradeOffers {
    private VillagerTradeOffers() {}
    public static MerchantOffer create(ResourceLocation slot, ItemStack primary, ItemStack secondary,
            ItemStack output, int uses, int xp, float multiplier) {
        return create(slot, primary, secondary, output, uses, xp, multiplier, VillagerTradePricing.DEFAULT_MINIMUM_BASIS_POINTS);
    }
    public static MerchantOffer create(ResourceLocation slot, ItemStack primary, ItemStack secondary,
            ItemStack output, int uses, int xp, float multiplier, int minimumBasisPoints) {
        if (primary.isEmpty() || output.isEmpty() || output.getCount() > output.getMaxStackSize()
                || (!secondary.isEmpty() && secondary.getCount() > secondary.getMaxStackSize())
                || uses < 1 || uses > com.gamergaming.taczweaponblueprints.villager.trade.TradeLimits.USES
                || xp < 0 || xp > com.gamergaming.taczweaponblueprints.villager.trade.TradeLimits.XP
                || !Float.isFinite(multiplier) || multiplier < 0 || multiplier > 1)
            throw new IllegalArgumentException("Invalid offer contents");
        var counts = VillagerTradePricing.normalize(primary.getCount(), secondary.getCount(),
                !secondary.isEmpty() && ItemStack.isSameItemSameComponents(primary, secondary), primary.getMaxStackSize(),
                minimumBasisPoints);
        var offer = new MerchantOffer(OwnedOfferSerialization.cost(primary.copyWithCount(counts.primary())),
                counts.secondary() == 0 ? java.util.Optional.empty() : java.util.Optional.of(OwnedOfferSerialization.cost(secondary.copyWithCount(counts.secondary()))),
                output.copy(), uses, xp, multiplier);
        ((OwnedVillagerOffer) offer).taczweaponblueprints$terms(new VillagerOfferTerms(slot, counts.minimumPrimary(), true, 0));
        return offer;
    }
}
