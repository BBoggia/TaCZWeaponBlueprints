package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.google.gson.JsonParser;

class ResearchTechTreeRatingReportTest {
    @TempDir
    java.nio.file.Path temporaryDirectory;

    @Test
    void reportIsDeterministicAndClearlyNonAuthoritative() {
        TaCZGunStats gun = new TaCZGunStats(
                "test:gun", "rifle", "test:gun_data",
                8.0, 0.0, 600.0, 30, 2.0, 200.0, 80.0,
                0.1, 1.5, 1, 0.2, 0.4, 3.0, 0.2, 0.5, -0.2,
                2, 4, null, null, "magazine", false, "source-hash", List.of());
        var suggestions = new ResearchTechTreeRatingSuggester().suggest(
                List.of(gun),
                Map.of("test:gun", new AppealRating(75, "Well-known platform")));

        String first = ResearchTechTreeRatingReport.toJson(suggestions, "1.1.8", "fixture");
        String second = ResearchTechTreeRatingReport.toJson(suggestions, "1.1.8", "fixture");
        var root = JsonParser.parseString(first).getAsJsonObject();

        assertEquals(first, second);
        assertFalse(root.get("authoritative").getAsBoolean());
        assertEquals("authoring_suggestions_only", root.get("purpose").getAsString());
        assertEquals(1, root.getAsJsonObject("source").get("recipe_backed_guns").getAsInt());
        assertEquals(75, root.getAsJsonArray("recommendations").get(0).getAsJsonObject()
                .getAsJsonObject("scores").get("appeal").getAsInt());
        assertFalse(first.contains("generated_at"));
    }

    @Test
    void appealInputRejectsUnknownFields() throws Exception {
        var path = temporaryDirectory.resolve("appeal.json");
        Files.writeString(path, """
                {"format":1,"ratings":{"test:gun":{"score":50,"reason":"review","extra":true}}}
                """);

        assertThrows(java.io.IOException.class, () -> AppealRatings.load(path));
    }

    @Test
    void appealInputLoadsSortedReviewedRatings() throws Exception {
        var path = temporaryDirectory.resolve("appeal.json");
        Files.writeString(path, """
                {
                  "format":1,
                  "ratings":{
                    "test:z":{"score":70,"reason":"Z review"},
                    "test:a":{"score":30,"reason":"A review"}
                  }
                }
                """);

        Map<String, AppealRating> ratings = AppealRatings.load(path);

        assertEquals(2, ratings.size());
        assertEquals(30, ratings.get("test:a").score());
        assertTrue(ratings.get("test:z").reason().contains("review"));
    }
}
