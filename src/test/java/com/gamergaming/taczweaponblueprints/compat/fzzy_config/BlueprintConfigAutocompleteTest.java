package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;

import net.minecraft.resources.ResourceLocation;

class BlueprintConfigAutocompleteTest {
    private static final Map<ResourceLocation, BlueprintData> CATALOG = Map.of(
            id("tacz:m4a1"), blueprint("tacz:m4a1", "rifle", BlueprintKind.GUN),
            id("lradd:bam4"), blueprint("lradd:bam4", "rifle", BlueprintKind.GUN),
            id("cib:m4"), blueprint("cib:m4", "rifle", BlueprintKind.GUN),
            id("oldgun:pf60_ammo"), blueprint("oldgun:pf60_ammo", "ammo", BlueprintKind.AMMO),
            id("tacz:stock_m4ss"), blueprint("tacz:stock_m4ss", "stock", BlueprintKind.ATTACHMENT));

    @Test
    void gunSuggestionsIncludeEveryNameOrIdContainingM4() {
        List<BlueprintIdAutocompleteString.BlueprintIdSuggestion> guns =
                BlueprintConfig.blueprintIdSuggestions(CATALOG, BlueprintKind.GUN);
        BlueprintIdAutocompleteString entry = new BlueprintIdAutocompleteString(
                "tacz:ak47",
                value -> value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"),
                () -> guns);

        assertEquals(
                List.of("cib:m4", "tacz:m4a1", "lradd:bam4"),
                entry.suggestionsForTesting("m4", 2, ignored -> true).join().getList().stream()
                        .map(com.mojang.brigadier.suggestion.Suggestion::getText)
                        .toList());
    }

    @Test
    void blacklistSuggestionCatalogsAreRestrictedToTheirOwnKinds() {
        assertEquals(
                List.of("cib:m4", "lradd:bam4", "tacz:m4a1"),
                ids(BlueprintConfig.blueprintIdSuggestions(CATALOG, BlueprintKind.GUN)));
        assertEquals(
                List.of("oldgun:pf60_ammo"),
                ids(BlueprintConfig.blueprintIdSuggestions(CATALOG, BlueprintKind.AMMO)));
        assertEquals(
                List.of("tacz:stock_m4ss"),
                ids(BlueprintConfig.blueprintIdSuggestions(CATALOG, BlueprintKind.ATTACHMENT)));
    }

    private static List<String> ids(
            List<BlueprintIdAutocompleteString.BlueprintIdSuggestion> suggestions) {
        return suggestions.stream()
                .map(BlueprintIdAutocompleteString.BlueprintIdSuggestion::id)
                .sorted()
                .toList();
    }

    private static BlueprintData blueprint(String id, String itemType, BlueprintKind kind) {
        return new BlueprintData(
                id,
                "",
                "",
                id(id),
                null,
                itemType,
                id(id),
                kind,
                1);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
