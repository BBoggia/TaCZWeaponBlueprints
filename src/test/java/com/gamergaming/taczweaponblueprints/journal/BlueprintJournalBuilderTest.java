package com.gamergaming.taczweaponblueprints.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class BlueprintJournalBuilderTest {
    private static final ResourceLocation PROFILE = id("test:profile");

    @Test
    void buildsDeterministicDisclosureFilteredEntriesAndUnavailableHistory() {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(id("test:unknown"), data("test:unknown"));
        catalog.put(id("test:discovered"), data("test:discovered"));
        catalog.put(id("test:learned"), data("test:learned"));
        PlayerRecipeData player = new PlayerRecipeData();
        player.discoverBlueprint("test:discovered");
        player.addBlueprint("test:learned");
        player.discoverBlueprint("removed:history");
        player.addBlueprint("removed:learned_history");
        player.setResearchPoints(12);

        BlueprintJournalSnapshot snapshot = BlueprintJournalBuilder.build(
                catalog,
                researchSnapshot(),
                config(JournalVisibility.FULL),
                player,
                ignored -> false);

        assertEquals(3, snapshot.entries().size());
        BlueprintJournalEntry discovered = snapshot.entries().get(0);
        BlueprintJournalEntry learned = snapshot.entries().get(1);
        BlueprintJournalEntry unknown = snapshot.entries().get(2);
        assertEquals(id("test:discovered"), discovered.blueprintId().orElseThrow());
        assertEquals(JournalVisibility.PREVIEW, discovered.visibility());
        assertFalse(discovered.discovered());
        assertFalse(discovered.researchable());
        assertEquals(8, discovered.researchPointCost());
        assertEquals(0, discovered.recyclingValue());
        assertEquals(id("test:learned"), learned.blueprintId().orElseThrow());
        assertEquals(JournalVisibility.FULL, learned.visibility());
        assertTrue(learned.learned());
        assertEquals(1, learned.recyclingValue());
        assertEquals(JournalVisibility.SILHOUETTE, unknown.visibility());
        assertTrue(unknown.blueprintId().isEmpty());
        assertTrue(unknown.nameKey().isEmpty());
        assertEquals(1, snapshot.learnedCount());
        assertEquals(2, snapshot.discoveredCount());
        assertEquals(1, snapshot.researchableCount());
        assertEquals(12, snapshot.researchPoints());
        assertEquals(
                List.of(id("removed:history"), id("removed:learned_history")),
                snapshot.unavailableHistory().stream()
                        .map(BlueprintJournalSnapshot.HistoryEntry::blueprintId)
                        .toList());
        assertFalse(snapshot.unavailableHistory().get(0).learned());
        assertTrue(snapshot.unavailableHistory().get(1).learned());
    }

    @Test
    void visibilityCeilingPreventsIdentityAndPolicyLeaks() {
        PlayerRecipeData player = new PlayerRecipeData();
        player.discoverBlueprint("test:discovered");
        BlueprintJournalSnapshot snapshot = BlueprintJournalBuilder.build(
                Map.of(id("test:discovered"), data("test:discovered")),
                researchSnapshot(),
                config(JournalVisibility.NAME),
                player,
                ignored -> false);

        BlueprintJournalEntry entry = snapshot.entries().get(0);
        assertEquals(JournalVisibility.NAME, entry.visibility());
        assertTrue(entry.nameKey().isPresent());
        assertTrue(entry.blueprintId().isEmpty());
        assertFalse(entry.discovered());
        assertEquals(0, entry.researchPointCost());
    }

    @Test
    void disabledJournalAndUnavailablePlayerDataPublishEmptySnapshots() {
        Map<ResourceLocation, BlueprintData> catalog =
                Map.of(id("test:unknown"), data("test:unknown"));
        BlueprintProgressionConfigSnapshot disabled = new BlueprintProgressionConfigSnapshot(
                true, true, false, JournalVisibility.FULL, true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING, false, 100, false, PROFILE);
        assertEquals(
                BlueprintJournalSnapshot.EMPTY,
                BlueprintJournalBuilder.build(catalog, researchSnapshot(), disabled, new PlayerRecipeData(), null));
        assertEquals(
                BlueprintJournalSnapshot.EMPTY,
                BlueprintJournalBuilder.build(catalog, researchSnapshot(), config(JournalVisibility.FULL), null, null));
    }

    @Test
    void entryConstructorRejectsMetadataAtRestrictedVisibilityTiers() {
        assertThrows(IllegalArgumentException.class, () -> new BlueprintJournalEntry(
                0,
                JournalVisibility.SILHOUETTE,
                Optional.of(id("test:leak")),
                Optional.empty(), Optional.empty(), Optional.empty(),
                false, false, false, false, false, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintJournalEntry(
                0,
                JournalVisibility.NAME,
                Optional.empty(),
                Optional.of("test.name"), Optional.empty(), Optional.empty(),
                false, true, false, false, false, 0, 0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintJournalEntry(
                0,
                JournalVisibility.PREVIEW,
                Optional.of(id("test:target")),
                Optional.of("test.name"), Optional.of("rifle"), Optional.of(id("test:slot/target")),
                false, false, true, false, true, 8, 0, 0, 0));
    }

    private static BlueprintResearchSnapshot researchSnapshot() {
        BlueprintResearchProfile profile = new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.SILHOUETTE,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                true,
                false);
        return BlueprintResearchSnapshot.create(Map.of(), Map.of(PROFILE, profile), Map.of());
    }

    private static BlueprintProgressionConfigSnapshot config(JournalVisibility maximum) {
        return new BlueprintProgressionConfigSnapshot(
                true, true, true, maximum, true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING, false, 100, false, PROFILE);
    }

    private static BlueprintData data(String value) {
        ResourceLocation blueprintId = id(value);
        return new BlueprintData(
                value,
                "name." + blueprintId.getPath(),
                "tooltip." + blueprintId.getPath(),
                id("test:recipe/" + blueprintId.getPath()),
                null,
                "rifle",
                id("test:slot/" + blueprintId.getPath()));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
