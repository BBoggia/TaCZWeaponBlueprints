package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class BlueprintJournalQueryTest {
    @Test
    void searchesOnlyDisclosedFields() {
        BlueprintJournalEntry silhouette = entry(0, JournalVisibility.SILHOUETTE, null, null, false, false, false);
        BlueprintJournalEntry named = entry(1, JournalVisibility.NAME, null, "secret.weapon", false, false, false);
        BlueprintJournalEntry preview = entry(2, JournalVisibility.PREVIEW, "pack:alpha", "Alpha Rifle", false, true, true);

        assertEquals(0, query(List.of(silhouette), "server-only-name").totalMatches());
        assertEquals(1, query(List.of(named), "secret").totalMatches());
        assertEquals(1, query(List.of(preview), "pack:alpha").totalMatches());
        assertEquals(1, query(List.of(preview), "rifle").totalMatches());
    }

    @Test
    void anonymousEntriesNeverReachTheNameResolver() {
        BlueprintJournalEntry silhouette = entry(
                0, JournalVisibility.SILHOUETTE, null, null, false, false, false);
        AtomicInteger resolverCalls = new AtomicInteger();
        BlueprintJournalQuery.Result result = BlueprintJournalQuery.query(
                List.of(silhouette), "", BlueprintJournalQuery.StatusFilter.ALL, "",
                BlueprintJournalQuery.SortOrder.NAME, 0, 8, ignored -> {
                    resolverCalls.incrementAndGet();
                    return "server-only-name";
                });
        assertEquals(List.of(silhouette), result.entries());
        assertEquals(0, resolverCalls.get());
    }

    @Test
    void filtersSortsAndClampsPagesDeterministically() {
        BlueprintJournalEntry learned = entry(0, JournalVisibility.FULL, "pack:zulu", "Zulu", true, true, false);
        BlueprintJournalEntry researchable = entry(1, JournalVisibility.FULL, "pack:bravo", "Bravo", false, true, true);
        BlueprintJournalEntry unknown = entry(2, JournalVisibility.SILHOUETTE, null, null, false, false, false);
        List<BlueprintJournalEntry> entries = List.of(learned, researchable, unknown);

        BlueprintJournalQuery.Result filtered = BlueprintJournalQuery.query(
                entries, "", BlueprintJournalQuery.StatusFilter.RESEARCHABLE, "rifle",
                BlueprintJournalQuery.SortOrder.NAME, 0, 8, BlueprintJournalQueryTest::name);
        assertEquals(List.of(researchable), filtered.entries());

        BlueprintJournalQuery.Result paged = BlueprintJournalQuery.query(
                entries, "", BlueprintJournalQuery.StatusFilter.ALL, "",
                BlueprintJournalQuery.SortOrder.PROGRESS, 99, 2, BlueprintJournalQueryTest::name);
        assertEquals(2, paged.pageCount());
        assertEquals(1, paged.page());
        assertEquals(List.of(unknown), paged.entries());
    }

    @Test
    void categoriesComeOnlyFromEntriesThatDiscloseThem() {
        List<BlueprintJournalEntry> entries = List.of(
                entry(0, JournalVisibility.SILHOUETTE, null, null, false, false, false),
                entry(1, JournalVisibility.PREVIEW, "pack:a", "A", false, true, false),
                fullEntry(2, "pack:b", "B", "ammo", false, true, false));
        assertEquals(List.of("ammo", "rifle"), BlueprintJournalQuery.categories(entries));
    }

    @Test
    void historySearchAndOrderingRemainBounded() {
        List<BlueprintJournalSnapshot.HistoryEntry> history = List.of(
                new BlueprintJournalSnapshot.HistoryEntry(id("pack:zulu"), false),
                new BlueprintJournalSnapshot.HistoryEntry(id("pack:alpha"), true));
        BlueprintJournalQuery.HistoryResult result =
                BlueprintJournalQuery.queryHistory(history, "pack:", 4, 1);
        assertEquals(2, result.pageCount());
        assertEquals(1, result.page());
        assertEquals(id("pack:zulu"), result.entries().get(0).blueprintId());
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintJournalQuery.queryHistory(history, "", 0, 0));
    }

    @Test
    void recentSearchUsesNamesAndMembersAndOrdersNewestFirst() {
        List<BlueprintJournalSnapshot.RecentUnlockBatch> recent = List.of(
                new BlueprintJournalSnapshot.RecentUnlockBatch(
                        1L,
                        com.gamergaming.taczweaponblueprints.capabilities
                                .RecentBlueprintUnlockBatch.Source.PHYSICAL_BLUEPRINT,
                        id("pack:alpha"),
                        List.of(id("pack:alpha")),
                        1),
                new BlueprintJournalSnapshot.RecentUnlockBatch(
                        2L,
                        com.gamergaming.taczweaponblueprints.capabilities
                                .RecentBlueprintUnlockBatch.Source.TREE_RESEARCH,
                        id("pack:target"),
                        List.of(id("pack:route_member"), id("pack:target")),
                        2));

        BlueprintJournalQuery.RecentResult result = BlueprintJournalQuery.queryRecent(
                recent,
                "route member",
                0,
                4,
                blueprintId -> blueprintId.equals(id("pack:route_member"))
                        ? "Route Member"
                        : "");

        assertEquals(1, result.totalMatches());
        assertEquals(2L, result.entries().get(0).sequence());
        assertThrows(IllegalArgumentException.class,
                () -> BlueprintJournalQuery.queryRecent(recent, "", 0, 0, ignored -> ""));
    }

    private static BlueprintJournalQuery.Result query(List<BlueprintJournalEntry> entries, String search) {
        return BlueprintJournalQuery.query(
                entries, search, BlueprintJournalQuery.StatusFilter.ALL, "",
                BlueprintJournalQuery.SortOrder.CATALOG, 0, 8, BlueprintJournalQueryTest::name);
    }

    private static String name(BlueprintJournalEntry entry) {
        return entry.nameKey().orElse("");
    }

    private static BlueprintJournalEntry entry(
            int ordinal,
            JournalVisibility visibility,
            String id,
            String name,
            boolean learned,
            boolean discovered,
            boolean researchable) {
        if (visibility == JournalVisibility.SILHOUETTE || visibility == JournalVisibility.NAME) {
            return new BlueprintJournalEntry(
                    ordinal, visibility, Optional.empty(), Optional.ofNullable(name), Optional.empty(), Optional.empty(),
                    false, false, false, false, false, 0, 0, 0, 0);
        }
        if (visibility == JournalVisibility.PREVIEW) {
            return new BlueprintJournalEntry(
                    ordinal, visibility, Optional.of(id(id)), Optional.of(name),
                    Optional.of("rifle"), Optional.of(id("pack:slot/" + ordinal)),
                    false, false, false, false, false, 5, 1, 0, 0);
        }
        return fullEntry(ordinal, id, name, "rifle", learned, discovered, researchable);
    }

    private static BlueprintJournalEntry fullEntry(
            int ordinal,
            String id,
            String name,
            String category,
            boolean learned,
            boolean discovered,
            boolean researchable) {
        return new BlueprintJournalEntry(
                ordinal, JournalVisibility.FULL, Optional.of(id(id)), Optional.of(name),
                Optional.of(category), Optional.of(id("pack:slot/" + ordinal)),
                learned, discovered, researchable, false, researchable, 5, 1, 0, 2);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
