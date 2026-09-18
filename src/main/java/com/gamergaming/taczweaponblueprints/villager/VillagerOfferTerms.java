package com.gamergaming.taczweaponblueprints.villager;

import com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition;
import com.gamergaming.taczweaponblueprints.villager.trade.TradeLimits;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;

/** Offer metadata is separate from merchandise NBT, including protected fragments. */
public record VillagerOfferTerms(ResourceLocation slot, int minimumPrimary, boolean available, long revision) {
    public static final String TAG = "taczweaponblueprints:trade";
    public VillagerOfferTerms {
        if (slot == null || slot.getPath().isBlank() || slot.toString().length() > TradeLimits.ID_LENGTH
                || minimumPrimary < 1 || minimumPrimary > 64 || revision < 0) throw new IllegalArgumentException("Invalid owned offer terms");
    }
    public CompoundTag save() {
        var tag = new CompoundTag(); tag.putInt("version", 1); tag.putString("slot", slot.toString());
        tag.putInt("minimum", minimumPrimary); tag.putBoolean("available", available); tag.putLong("revision", revision);
        return tag;
    }
    public static VillagerOfferTerms read(CompoundTag tag, int stackLimit) {
        if (tag.getInt("version") != 1 || !tag.contains("available", Tag.TAG_BYTE)
                || !tag.contains("revision", Tag.TAG_LONG)) throw new IllegalArgumentException("Unknown owned offer format");
        var terms = new VillagerOfferTerms(TradeDefinition.parseId(tag.getString("slot")), tag.getInt("minimum"),
                tag.getBoolean("available"), tag.getLong("revision"));
        if (terms.minimumPrimary > stackLimit) throw new IllegalArgumentException("Minimum exceeds currency stack size");
        return terms;
    }
}
