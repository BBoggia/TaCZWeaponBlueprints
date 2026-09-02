package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;

class ResearchTechTreeRatingSuggesterTest {
    @Test
    void suggestionsAreStableAcrossInputOrderAndKeepAppealExplicit() {
        TaCZGunStats weak = gun("test:weak", 4.0, 300.0, 8, 3.0, 0.7, 5.0);
        TaCZGunStats middle = gun("test:middle", 8.0, 600.0, 20, 2.0, 0.3, 3.0);
        TaCZGunStats strong = gun("test:strong", 14.0, 900.0, 40, 1.2, 0.1, 2.0);
        ResearchTechTreeRatingSuggester suggester = new ResearchTechTreeRatingSuggester();

        var first = suggester.suggest(
                List.of(strong, weak, middle),
                Map.of("test:strong", new AppealRating(95, "Iconic end-game weapon")));
        var second = suggester.suggest(
                List.of(middle, strong, weak),
                Map.of("test:strong", new AppealRating(95, "Iconic end-game weapon")));

        assertEquals(first, second);
        WeaponRatingSuggestion reviewed = byId(first, "test:strong");
        WeaponRatingSuggestion unreviewed = byId(first, "test:weak");
        assertTrue(reviewed.appealReviewed());
        assertEquals(95, reviewed.appealScore());
        assertFalse(unreviewed.appealReviewed());
        assertEquals(ResearchTechTreeRatingSuggester.DEFAULT_UNREVIEWED_APPEAL,
                unreviewed.appealScore());
        assertTrue(unreviewed.warnings().contains("appeal_unreviewed"));

        int mechanicalTier = ResearchTechTreeContract.Tier
                .forScore(reviewed.mechanicalScore()).ordinal();
        assertTrue(Math.abs(reviewed.suggestedTier().ordinal() - mechanicalTier)
                <= ResearchTechTreeContract.MAX_APPEAL_TIER_SHIFT);
    }

    @Test
    void sustainedDamageIncludesReloadAndManualActionCycleTime() {
        TaCZGunStats manual = new TaCZGunStats(
                "test:manual", "sniper", "test:manual_data",
                20.0, 0.0, 600.0, 2, 2.0, 200.0, 80.0,
                0.0, 1.5, 1, 0.2, 0.4, 4.0, 0.1, 1.0, -0.2,
                1, 2, 1.0, null, "magazine", false, "hash", List.of());

        WeaponRatingSuggestion suggestion = new ResearchTechTreeRatingSuggester()
                .suggest(List.of(manual), Map.of()).get(0);

        // Two shots: one one-second action interval, then a two-second reload.
        assertEquals(40.0 / 3.0, suggestion.sustainedDamagePerSecond(), 0.0001);
    }

    @Test
    void missingMetricsReceiveNeutralEvidenceAndRemainVisible() {
        TaCZGunStats incomplete = new TaCZGunStats(
                "test:incomplete", "special", "test:incomplete_data",
                5.0, 0.0, 300.0, null, null, 80.0, 40.0,
                null, null, null,
                null, null, null, null, null, null,
                1, 0, null, null, "inventory", false, "hash", List.of("ammo_amount"));

        WeaponRatingSuggestion suggestion = new ResearchTechTreeRatingSuggester()
                .suggest(List.of(incomplete), Map.of()).get(0);

        assertEquals(50, suggestion.metricPercentiles().get("magazine_capacity"));
        assertTrue(suggestion.warnings().contains("ammo_amount"));
        assertTrue(suggestion.warnings().contains("neutral_percentile:magazine_capacity"));
    }

    @Test
    void rejectsAppealRatingsForContentOutsideExtractedEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchTechTreeRatingSuggester()
                .suggest(
                        List.of(gun("test:known", 5.0, 400.0, 10, 2.0, 0.5, 3.0)),
                        Map.of("test:unknown", new AppealRating(50, "Not in this pack"))));
    }

    private static WeaponRatingSuggestion byId(List<WeaponRatingSuggestion> suggestions, String id) {
        return suggestions.stream()
                .filter(suggestion -> suggestion.stats().blueprintId().equals(id))
                .findFirst()
                .orElseThrow();
    }

    private static TaCZGunStats gun(
            String id,
            double damage,
            double rpm,
            int capacity,
            double reload,
            double inaccuracy,
            double weight) {
        return new TaCZGunStats(
                id, "rifle", id + "_data",
                damage, 0.0, rpm, capacity, reload, 200.0, 80.0,
                0.1, 1.5, 1, 0.2, 0.4, weight, inaccuracy, inaccuracy, -0.2,
                2, 4, null, null, "magazine", false,
                id.substring(id.indexOf(':') + 1), List.of());
    }
}
