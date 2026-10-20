package com.gamergaming.taczweaponblueprints.villager;

/** Implemented on MerchantOffer so native save/load and curing preserve ownership. */
public interface OwnedVillagerOffer {
    void taczweaponblueprints$readMetadata(net.minecraft.nbt.CompoundTag tag);
    void taczweaponblueprints$writeMetadata(net.minecraft.nbt.CompoundTag tag);
    com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding taczweaponblueprints$binding();
    void taczweaponblueprints$binding(com.gamergaming.taczweaponblueprints.villager.stock.StockOfferBinding binding);
    VillagerOfferTerms taczweaponblueprints$terms();
    void taczweaponblueprints$terms(VillagerOfferTerms terms);
}
