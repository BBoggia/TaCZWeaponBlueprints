package com.gamergaming.taczweaponblueprints.villager.trade;

import java.util.Set;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import net.minecraft.resources.ResourceLocation;

/** Immutable server configuration; registry lookup happens after configuration loading. */
public record TradeSettings(boolean gunsmith, boolean researcher, CurrencyMode currencyMode,
        ResourceLocation fixedCurrency, int priceBasisPoints, int stockBasisPoints, int minimumBasisPoints,
        boolean materialPurchases, int maximumGunTier, Set<TradeDefinition.Kind> categories,
        boolean extendedMagazines) {
    public enum CurrencyMode implements me.fzzyhmstrs.fzzy_config.util.EnumTranslatable {
        FOLLOW_RESEARCH_ITEM, FIXED_ITEM;
        @Override public String prefix() { return "config.taczweaponblueprints.blueprint.villagerCurrencyMode"; }
    }
    public TradeSettings {
        java.util.Objects.requireNonNull(currencyMode); java.util.Objects.requireNonNull(fixedCurrency);
        categories = Set.copyOf(categories);
        TradeDefinition.range(priceBasisPoints, 1, 100_000, "price multiplier");
        TradeDefinition.range(stockBasisPoints, 1, 100_000, "stock multiplier");
        TradeDefinition.range(minimumBasisPoints, 0, 10_000, "minimum price");
        TradeDefinition.range(maximumGunTier, 1, 3, "maximum gun tier");
    }
    public static TradeSettings from(BlueprintConfig c) {
        var kinds = java.util.EnumSet.noneOf(TradeDefinition.Kind.class);
        if (c.villagerGunSales.get()) kinds.add(TradeDefinition.Kind.GUN);
        if (c.villagerAmmoSales.get()) kinds.add(TradeDefinition.Kind.AMMO);
        if (c.villagerAttachmentSales.get()) kinds.add(TradeDefinition.Kind.ATTACHMENT);
        if (c.villagerDataSales.get()) kinds.add(TradeDefinition.Kind.RESEARCH_DATA);
        if (c.villagerFragmentSales.get()) kinds.add(TradeDefinition.Kind.ANALYSIS_FRAGMENTS);
        if (c.villagerBlueprintSales.get()) kinds.add(TradeDefinition.Kind.BLUEPRINT);
        if (c.villagerMagazineSales.get()) kinds.add(TradeDefinition.Kind.MAGAZINE);
        kinds.add(TradeDefinition.Kind.CURRENCY);
        return new TradeSettings(c.enableGunsmith.get(), c.enableBallisticsResearcher.get(), c.villagerCurrencyMode.get(),
                TradeDefinition.parseId(c.villagerFixedCurrency.get()), c.villagerPricePercent.get() * 100,
                c.villagerStockPercent.get() * 100, c.villagerMinimumPricePercent.get() * 100,
                c.villagerMaterialPurchases.get(), c.villagerMaximumGunTier.get(), kinds, c.villagerExtendedMagazines.get());
    }
    public ResourceLocation currency(ResearchCurrencyPolicy policy) {
        return currencyMode == CurrencyMode.FIXED_ITEM ? fixedCurrency
                : policy.item().orElse(new ResourceLocation("minecraft:emerald"));
    }
    public boolean enabled(TradeDefinition.Profession profession) {
        return profession == TradeDefinition.Profession.GUNSMITH ? gunsmith : researcher;
    }
}
