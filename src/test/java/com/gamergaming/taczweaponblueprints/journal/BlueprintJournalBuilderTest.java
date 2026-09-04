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
import com.gamergaming.taczweaponblueprints.capabilities.RecentBlueprintUnlockBatch;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentDiscount;
import com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateRequirements;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintCraftingPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingDisposition;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingPolicySource;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;

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
        player.recordRecentUnlockBatch(
                RecentBlueprintUnlockBatch.Source.PHYSICAL_BLUEPRINT,
                "test:learned",
                List.of("test:learned"));

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
        assertEquals(1, snapshot.recentUnlocks().size());
        assertEquals(id("test:learned"),
                snapshot.recentUnlocks().get(0).targetBlueprintId());
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
    void publishesFragmentProgressOnlyWhenIdentityIsDisclosed() {
        PlayerRecipeData player = new PlayerRecipeData();
        player.discoverBlueprint("test:discovered");
        assertTrue(player.replaceSupplementalProgression(
                Map.of("test:discovered", 3), Map.of()));
        ResolvedBlueprintProgressionPolicy progression = fragmentPolicy(
                id("test:discovered"));

        BlueprintJournalSnapshot disclosed = BlueprintJournalBuilder.build(
                Map.of(id("test:discovered"), data("test:discovered")),
                researchSnapshot(),
                config(JournalVisibility.FULL),
                player,
                ignored -> false,
                ignored -> false,
                null,
                Map.of(id("test:discovered"), progression));
        BlueprintJournalEntry.FragmentProgress progress = disclosed.entries().get(0)
                .fragmentProgress().orElseThrow();
        assertEquals(3, progress.archived());
        assertEquals(5, progress.threshold());

        BlueprintJournalSnapshot hidden = BlueprintJournalBuilder.build(
                Map.of(id("test:discovered"), data("test:discovered")),
                researchSnapshot(),
                config(JournalVisibility.NAME),
                player,
                ignored -> false,
                ignored -> false,
                null,
                Map.of(id("test:discovered"), progression));
        assertTrue(hidden.entries().get(0).fragmentProgress().isEmpty());
    }

    @Test
    void publishesCraftingAccessOnlyForFullJournalDetails() {
        ResourceLocation learnedId = id("test:learned");
        ResourceLocation previewId = id("test:preview");
        PlayerRecipeData player = new PlayerRecipeData();
        player.addBlueprint(learnedId.toString());
        player.discoverBlueprint(previewId.toString());

        BlueprintJournalSnapshot snapshot = BlueprintJournalBuilder.build(
                Map.of(learnedId, data(learnedId.toString()),
                        previewId, data(previewId.toString())),
                researchSnapshot(),
                config(JournalVisibility.FULL),
                player,
                ignored -> false,
                ignored -> false,
                null,
                Map.of(),
                Map.of(
                        learnedId, craftingPolicy(learnedId, ResearchWorkbenchTier.TIER_3),
                        previewId, craftingPolicy(previewId, ResearchWorkbenchTier.TIER_2)));

        BlueprintJournalEntry learned = snapshot.entries().stream()
                .filter(entry -> entry.blueprintId().filter(learnedId::equals).isPresent())
                .findFirst().orElseThrow();
        BlueprintJournalEntry preview = snapshot.entries().stream()
                .filter(entry -> entry.blueprintId().filter(previewId::equals).isPresent())
                .findFirst().orElseThrow();
        assertEquals(
                ResearchWorkbenchTier.TIER_3,
                learned.craftingAccess().orElseThrow()
                        .requiredWorkbenchTier().orElseThrow());
        assertTrue(preview.craftingAccess().isEmpty());
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

    private static ResolvedBlueprintProgressionPolicy fragmentPolicy(
            ResourceLocation blueprintId) {
        return new ResolvedBlueprintProgressionPolicy(
                PROFILE,
                blueprintId,
                ResearchWorkbenchTier.TIER_1,
                new BlueprintFragmentPolicy(
                        BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST,
                        5,
                        100,
                        BlueprintFragmentDiscount.fixed(2),
                        1),
                ProgressionGateRequirements.EMPTY,
                ResolvedBlueprintProgressionPolicy.TierSource.FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                false);
    }

    private static ResolvedBlueprintCraftingPolicy craftingPolicy(
            ResourceLocation blueprintId,
            ResearchWorkbenchTier tier) {
        return new ResolvedBlueprintCraftingPolicy(
                PROFILE,
                blueprintId,
                BlueprintCraftingDisposition.TIERED,
                Optional.of(tier),
                ProgressionGateRequirements.EMPTY,
                BlueprintCraftingPolicySource.PROFILE_FALLBACK,
                Optional.empty(),
                MatchSpecificity.NONE,
                Optional.empty(),
                Optional.empty(),
                false,
                "journal_test",
                java.util.Set.of());
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
