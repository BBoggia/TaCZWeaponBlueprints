package com.gamergaming.taczweaponblueprints.villager.stock;

import net.minecraft.nbt.CompoundTag;

/** Original pre-merge charges accompany the chosen product; vanilla persists uses and demand. */
public record StockOfferBinding(StockPlan.Entry entry,int primary,int protectedSecondary) {
    public static final String TAG="taczweaponblueprints:stock_offer";
    public StockOfferBinding {if(entry==null||primary<1||primary>64||protectedSecondary<0||protectedSecondary>64)throw new IllegalArgumentException("Invalid stock price metadata");}
    public CompoundTag save(){var tag=StockState.saveEntry(entry);tag.putInt("version",1);tag.putInt("original_primary",primary);tag.putInt("protected_secondary",protectedSecondary);return tag;}
    public static StockOfferBinding read(CompoundTag tag){if(!tag.contains("version",net.minecraft.nbt.Tag.TAG_INT)||tag.getInt("version")!=1||!tag.contains("original_primary",net.minecraft.nbt.Tag.TAG_INT)||!tag.contains("protected_secondary",net.minecraft.nbt.Tag.TAG_INT))throw new IllegalArgumentException("Invalid stock offer format");return new StockOfferBinding(StockState.readEntry(tag),tag.getInt("original_primary"),tag.getInt("protected_secondary"));}
}
