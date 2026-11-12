package com.gamergaming.taczweaponblueprints.settings;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
class SettingsSearchTest {
    @Test void findsCanonicalWordsAndPlayerAliasesWithoutExactPhraseOrder(){
        assertTrue(SettingsSearch.matches(SettingsCatalog.entry("villagerGunSales"),"gun sales",key->""));
        assertTrue(SettingsSearch.matches(SettingsCatalog.entry("researchCurrencyKind"),"RP money",key->""));
        assertTrue(SettingsSearch.matches(SettingsCatalog.entry("enableResearch"),"levels workbench",key->""));
        assertFalse(SettingsSearch.matches(SettingsCatalog.entry("enableResearch"),"zzzz",key->""));
    }
    @Test void supportsTranslatedLabelsAndHidesUnsupportedEditors(){
        assertTrue(SettingsSearch.matches(SettingsCatalog.entry("enableGunsmith"),"WAFFEN",key->"Waffenschmied"));
        assertFalse(SettingsSearch.matches(SettingsCatalog.entry("magazineProgressionMode"),"magazine",key->key));
    }
}
