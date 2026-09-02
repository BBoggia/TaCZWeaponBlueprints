package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import me.fzzyhmstrs.fzzy_config.entry.EntryValidator;

class BlueprintIdAutocompleteStringTest {
    private static final List<BlueprintIdAutocompleteString.BlueprintIdSuggestion> SUGGESTIONS = List.of(
            new BlueprintIdAutocompleteString.BlueprintIdSuggestion(
                    "tacz:ak47", "AK-47 Assault Rifle"),
            new BlueprintIdAutocompleteString.BlueprintIdSuggestion(
                    "tacz:m1911", "M1911 Classic Pistol"),
            new BlueprintIdAutocompleteString.BlueprintIdSuggestion(
                    "pack:service_rifle", "Service Rifle"));

    @Test
    void translatedNameSearchInsertsOnlyTheStableId() {
        BlueprintIdAutocompleteString entry = entry();

        var suggestions = entry.suggestionsForTesting("classic pistol", 14, ignored -> true).join();

        assertEquals(1, suggestions.getList().size());
        assertEquals("tacz:m1911", suggestions.getList().get(0).getText());
        assertEquals("tacz:m1911", suggestions.getList().get(0).apply("classic pistol"));
        assertTrue(suggestions.getList().get(0).getTooltip().getString().contains("M1911 Classic Pistol"));
    }

    @Test
    void idPathSearchAndChoiceFilteringStillWork() {
        BlueprintIdAutocompleteString entry = entry();

        var suggestions = entry.suggestionsForTesting(
                "service_ri",
                "service_ri".length(),
                id -> !id.equals("tacz:ak47"))
                .join();

        assertEquals(List.of("pack:service_rifle"), suggestions.getList().stream()
                .map(com.mojang.brigadier.suggestion.Suggestion::getText)
                .toList());
    }

    @Test
    void validUnavailablePackIdsRemainAccepted() {
        BlueprintIdAutocompleteString entry = entry();

        assertTrue(entry.validateEntry(
                "futurepack:future_weapon",
                EntryValidator.ValidationType.STRONG).isValid());
        assertFalse(entry.validateEntry(
                "Future Weapon",
                EntryValidator.ValidationType.STRONG).isValid());
    }

    private static BlueprintIdAutocompleteString entry() {
        return new BlueprintIdAutocompleteString(
                "tacz:ak47",
                value -> value.matches("[a-z0-9_.-]+:[a-z0-9/._-]+"),
                () -> SUGGESTIONS);
    }
}
