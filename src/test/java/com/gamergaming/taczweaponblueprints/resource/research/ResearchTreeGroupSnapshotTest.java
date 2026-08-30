package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeGroupSnapshotTest {
    private static final ResourceLocation PROFILE = id("test:profile");

    @Test
    void compilesGroupsByAuthoredOrderAndIndexesEveryPlacement() {
        ResearchTreeGroupDefinition later = group(
                "Later",
                20,
                List.of(List.of(id("test:c"))));
        ResearchTreeGroupDefinition earlier = group(
                "Earlier",
                10,
                List.of(List.of(id("test:a")), List.of(id("test:b"))));
        BlueprintResearchSnapshot snapshot = snapshot(
                Map.of(),
                Map.of(id("test:later"), later, id("test:earlier"), earlier));

        assertEquals(
                List.of(id("test:earlier"), id("test:later")),
                snapshot.groupsForProfile(PROFILE).stream()
                        .map(BlueprintResearchSnapshot.GroupBinding::groupId)
                        .toList());
        assertEquals(
                new ResearchTreeGroupPlacement(id("test:earlier"), 1, 0),
                snapshot.placementFor(PROFILE, id("test:b")).orElseThrow());
        assertTrue(snapshot.placementFor(PROFILE, id("test:missing")).isEmpty());
    }

    @Test
    void oldGroupLessSnapshotsRemainValidAndUseNoAuthoredPlacements() {
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                Map.of());

        assertTrue(snapshot.groups().isEmpty());
        assertTrue(snapshot.groupsForProfile(PROFILE).isEmpty());
        assertTrue(snapshot.placementFor(PROFILE, id("test:anything")).isEmpty());
    }

    @Test
    void rejectsMissingProfilesAndDuplicateMembershipWithinAProfile() {
        ResearchTreeGroupDefinition missingProfile = new ResearchTreeGroupDefinition(
                1,
                id("test:missing_profile"),
                "Missing",
                Optional.empty(),
                id("test:a"),
                10,
                List.of(List.of(id("test:a"))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                Map.of(),
                Map.of(id("test:missing"), missingProfile)));

        ResearchTreeGroupDefinition first = group("First", 10, List.of(List.of(id("test:a"))));
        ResearchTreeGroupDefinition second = group("Second", 20, List.of(List.of(id("test:a"))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                Map.of(),
                Map.of(id("test:first"), first, id("test:second"), second)));
    }

    @Test
    void rejectsAuthoredRanksThatContradictEffectivePrerequisites() {
        BlueprintResearchRule root = rule(id("test:a"), List.of(), 100);
        BlueprintResearchRule dependent = rule(id("test:b"), List.of(id("test:a")), 100);
        ResearchTreeGroupDefinition misleading = group(
                "Misleading",
                10,
                List.of(List.of(id("test:a"), id("test:b"))));
        assertThrows(IllegalArgumentException.class, () -> snapshot(
                Map.of(id("test:root"), root, id("test:dependent"), dependent),
                Map.of(id("test:misleading"), misleading)));

        ResearchTreeGroupDefinition valid = group(
                "Valid",
                10,
                List.of(List.of(id("test:a")), List.of(id("test:b"))));
        BlueprintResearchSnapshot snapshot = snapshot(
                Map.of(id("test:root"), root, id("test:dependent"), dependent),
                Map.of(id("test:valid"), valid));
        assertEquals(1, snapshot.placementFor(PROFILE, id("test:b")).orElseThrow().rank());
    }

    @Test
    void diagnosticsAndExportReportCoverageFallbackAndAbsentMembers() {
        ResearchTreeGroupDefinition group = group(
                "Pistols",
                10,
                List.of(List.of(id("test:present")), List.of(id("test:absent"))));
        BlueprintResearchSnapshot snapshot = snapshot(Map.of(), Map.of(id("test:pistols"), group));
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        catalog.put(id("test:present"), data(id("test:present")));
        catalog.put(id("test:fallback"), data(id("test:fallback")));
        catalog.put(id("test:scope"), data(
                id("test:scope"), BlueprintKind.ATTACHMENT, "scope"));
        catalog.put(id("test:ammo"), data(
                id("test:ammo"), BlueprintKind.AMMO, "ammo"));

        BlueprintResearchDiagnostics.GroupAudit audit = BlueprintResearchDiagnostics.auditGroups(
                snapshot,
                catalog,
                PROFILE);
        assertEquals(1, audit.authoredGroupCount());
        assertEquals(2, audit.catalogSize());
        assertEquals(2, audit.authoredMemberCount());
        assertEquals(1, audit.groupedCatalogCount());
        assertEquals(List.of(id("test:fallback")), audit.fallbackBlueprintIds());
        assertEquals(List.of(id("test:absent")), audit.missingMemberIds());
        assertTrue(audit.hasProblems());

        String exported = BlueprintResearchCatalogExporter.export(snapshot, catalog, PROFILE);
        assertTrue(exported.contains("\"format\": 12"));
        assertTrue(exported.contains("\"research_group\": \"test:pistols\""));
        assertTrue(exported.contains("\"research_rank\": 0"));
        assertTrue(exported.contains("\"presentation_source\": \"automatic_fallback\""));
        assertTrue(exported.contains("\"presentation_source\": \"tech_tree_only\""));
        assertTrue(exported.contains("\"include_in_overview\": true"));
        assertTrue(exported.contains("\"research_group_included_in_overview\": false"));
        assertTrue(exported.contains("\"missing_members\""));

        assertFalse(BlueprintResearchDiagnostics.auditGroups(
                BlueprintResearchSnapshot.EMPTY,
                Map.of(),
                PROFILE).hasProblems());
    }

    private static BlueprintResearchSnapshot snapshot(
            Map<ResourceLocation, BlueprintResearchRule> rules,
            Map<ResourceLocation, ResearchTreeGroupDefinition> groups) {
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE, profile()),
                rules,
                groups);
    }

    private static BlueprintResearchProfile profile() {
        return new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.SILHOUETTE,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of()),
                false,
                false);
    }

    private static BlueprintResearchRule rule(
            ResourceLocation target,
            List<ResourceLocation> prerequisites,
            int priority) {
        return new BlueprintResearchRule(
                1,
                PROFILE,
                priority,
                new BlueprintResearchTarget(List.of(target), List.of(), Optional.empty()),
                Optional.of(JournalVisibility.FULL),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                prerequisites.isEmpty() ? Optional.empty() : Optional.of(prerequisites),
                Optional.empty());
    }

    private static ResearchTreeGroupDefinition group(
            String title,
            int order,
            List<List<ResourceLocation>> ranks) {
        return new ResearchTreeGroupDefinition(
                1,
                PROFILE,
                title,
                Optional.empty(),
                ranks.get(0).get(0),
                order,
                ranks);
    }

    private static BlueprintData data(ResourceLocation id) {
        return data(id, BlueprintKind.GUN, "gun");
    }

    private static BlueprintData data(
            ResourceLocation id,
            BlueprintKind kind,
            String itemType) {
        return new BlueprintData(
                id.toString(),
                "item." + id.getNamespace() + "." + id.getPath(),
                "tooltip." + id.getNamespace() + "." + id.getPath(),
                id,
                null,
                itemType,
                null,
                kind);
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
