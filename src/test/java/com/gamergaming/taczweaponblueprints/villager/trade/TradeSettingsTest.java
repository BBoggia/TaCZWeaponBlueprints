package com.gamergaming.taczweaponblueprints.villager.trade;

import org.junit.jupiter.api.Test;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeFormatTest.id;

class TradeSettingsTest {
    @Test void currentVersionFilesPublishLoadedValuesAndRoundTripWithoutSerializingSnapshots() {
        var loaded=ConfigApi.deserializeConfig(new BlueprintConfig(),"""
            version = 5
            enableGunsmith = false
            villagerCurrencyMode = "FIXED_ITEM"
            villagerFixedCurrency = "minecraft:iron_ingot"
            villagerPricePercent = 125
            villagerStockPercent = 50
            villagerMinimumPricePercent = 90
            villagerMaximumGunTier = 3
            villagerBlueprintSales = true
            villagerMagazineSales = false
            """).get();
        loaded.initializeLoadedSnapshots(); var s=loaded.villagerSettings();
        assertFalse(s.gunsmith());assertEquals(12500,s.priceBasisPoints());assertEquals(5000,s.stockBasisPoints());
        assertEquals(9000,s.minimumBasisPoints());assertEquals(3,s.maximumGunTier());
        assertTrue(s.categories().contains(TradeDefinition.Kind.BLUEPRINT));assertFalse(s.categories().contains(TradeDefinition.Kind.MAGAZINE));
        assertEquals(id("minecraft:iron_ingot"),s.currency(ResearchCurrencyPolicy.banked(100,1)));
        String saved=ConfigApi.serializeConfig(loaded).get(); assertFalse(saved.contains("villagerSettings"));
        var restored=ConfigApi.deserializeConfig(new BlueprintConfig(),saved).get();restored.initializeLoadedSnapshots();assertEquals(s,restored.villagerSettings());
    }
    @Test void migratedFilesAndBankedFallbackUseTheSameSnapshotPath() {
        var c=ConfigApi.deserializeConfig(new BlueprintConfig(),"version = 4\nvillagerMaterialPurchases = false\n").get();c.initializeLoadedSnapshots();
        assertFalse(c.villagerSettings().materialPurchases());
        assertEquals(id("minecraft:emerald"),c.villagerSettings().currency(ResearchCurrencyPolicy.banked(100,1)));
        assertEquals(id("minecraft:diamond"),c.villagerSettings().currency(ResearchCurrencyPolicy.physical(id("minecraft:diamond"),1)));
    }
}
