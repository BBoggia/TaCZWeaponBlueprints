package com.gamergaming.taczweaponblueprints.settings;

import java.util.Arrays;
import java.util.Locale;
import java.util.function.Function;

/** Search accepts player terms, translated labels, and the original flat setting names. */
public final class SettingsSearch {
    private SettingsSearch() {}
    public static boolean matches(SettingsCatalog.Entry entry,String query,Function<String,String> translate) {
        if(!entry.supported())return false;
        String aliases=switch(entry.category()) {
            case DISCOVERY -> "accessible balanced generous normal discovery loot preset";
            case ECONOMY -> "scrap rp research points currency money rewards";
            case RESEARCH -> "workbench bench levels tiers research crafting progression preset enforce advanced tier settings";
            case ANALYZER -> "analyzer recycler recycling fragments ordinary loot analysis";
            case VILLAGERS -> "villager trader gunsmith ballistics researcher";
            default -> "";
        };
        String words=(entry.field()+" "+translate.apply(entry.labelKey())+" "+translate.apply(entry.labelKey()+".desc")+" "+translate.apply("settings.taczweaponblueprints.ui.category."+entry.category().name().toLowerCase(Locale.ROOT))+" "+aliases).toLowerCase(Locale.ROOT);
        return Arrays.stream(query.strip().toLowerCase(Locale.ROOT).split("\\s+")).allMatch(words::contains);
    }
}
