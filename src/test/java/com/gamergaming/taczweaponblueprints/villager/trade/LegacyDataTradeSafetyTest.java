package com.gamergaming.taczweaponblueprints.villager.trade;
import org.junit.jupiter.api.Test;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import static org.junit.jupiter.api.Assertions.*;
class LegacyDataTradeSafetyTest {
    final ResourceLocation scrap=ResourceLocation.parse("taczweaponblueprints:scrap"),emerald=ResourceLocation.parse("minecraft:emerald");
    @Test void comparisonsRequireExplicitConsentAndSamePhysicalUnits(){
        var physical=ResearchCurrencyPolicy.physical(scrap,1);
        assertFalse(LegacyDataTradeSafety.compare(false,physical,scrap,3,1,1).isEmpty());
        assertFalse(LegacyDataTradeSafety.compare(true,ResearchCurrencyPolicy.banked(100,1),emerald,100,1,1).isEmpty());
        assertFalse(LegacyDataTradeSafety.compare(true,physical,emerald,100,1,1).isEmpty());
        assertEquals("",LegacyDataTradeSafety.compare(true,physical,scrap,3,1,1));
        assertEquals("",LegacyDataTradeSafety.compare(true,physical,scrap,17,2,3));
        assertEquals("",LegacyDataTradeSafety.compare(true,physical,scrap,18,1,6));
        assertFalse(LegacyDataTradeSafety.compare(true,physical,scrap,4,4,1).isEmpty());
        assertFalse(LegacyDataTradeSafety.compare(true,physical,scrap,3,4,1).isEmpty());
        assertFalse(LegacyDataTradeSafety.compare(true,physical,scrap,64,64,Long.MAX_VALUE).isEmpty());
    }
    @Test void legacyFlagIsStrictAndLimitedToData()throws Exception{
        var json=com.google.gson.JsonParser.parseString(TradeFormatTest.JSON).getAsJsonObject();
        json.getAsJsonObject("output").addProperty("allow_legacy_exchange",true);
        assertThrows(IllegalArgumentException.class,()->TradeFormatTest.read("test:d",json.toString()));
        json.getAsJsonObject("output").addProperty("kind","research_data");
        assertTrue(TradeFormatTest.read("test:d",json.toString()).output().allowLegacyExchange());
        json.getAsJsonObject("output").addProperty("allow_legacy_exchange","true");
        assertThrows(IllegalArgumentException.class,()->TradeFormatTest.read("test:d",json.toString()));
    }
}
