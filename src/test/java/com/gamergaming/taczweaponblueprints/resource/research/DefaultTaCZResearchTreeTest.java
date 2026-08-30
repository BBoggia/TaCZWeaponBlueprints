package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeLayout;
import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeLayoutCatalog;
import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeLayoutEngine;
import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeLayoutPolicy;
import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeProjectionBuilder;
import com.gamergaming.taczweaponblueprints.client.ResearchTechTreeProjectionCatalog;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot;
import com.gamergaming.taczweaponblueprints.progression.BlueprintLearningService;
import com.gamergaming.taczweaponblueprints.progression.BlueprintUnlockOrigin;
import com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.AutomaticPlacementMode;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.PlacementOrigin;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Tier;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeTopologyAudit;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeEconomyAudit;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardEconomyProjection;
import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardTrigger;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBuilder;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.AutomaticWeaponPlacementPolicy.ReviewHandling;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class DefaultTaCZResearchTreeTest {
    private static final ResourceLocation PROFILE_ID = id("taczweaponblueprints:duplicate_recovery");
    private static final ResourceLocation TECH_TREE_ID = id("taczweaponblueprints:default");
    private static final ResourceLocation ROOT_BLUEPRINT = id("tacz:glock_17");
    private static final Set<ResourceLocation> BRANCH_ROOTS = ids(
            "uzi", "db_long", "m16a4", "springfield1873", "rpk", "m320");
    private static final Set<Integer> DEFAULT_POINT_TIERS = Set.of(2, 4, 6, 8, 10, 12);
    private static final Map<Tier, Integer> DEFAULT_POINTS_BY_TECH_TIER = Map.of(
            Tier.STARTER, 2,
            Tier.BASIC, 4,
            Tier.ESTABLISHED, 6,
            Tier.ADVANCED, 8,
            Tier.ELITE, 10,
            Tier.APEX, 12);
    private static final Set<ResourceLocation> OFFICIAL_GUNS = ids(
            "aa12", "ai_awp", "ak47", "aug", "b93r", "cz75", "db_long", "db_short", "deagle",
            "deagle_golden", "fn_evolys", "fn_fal", "g36k", "glock_17", "hk416d", "hk_g3",
            "hk_mk23", "hk_mp5a5", "kar98", "lonetrail", "m1014", "m107", "m16a1", "m16a4",
            "m1911", "m249", "m320", "m4a1", "m700", "m870", "m95", "m9a4", "minigun",
            "mk14", "p320", "p90", "qbz_191", "qbz_95", "rhino357", "rpg7", "rpk", "scar_h",
            "scar_l", "sks_tactical", "spas_12", "spr15hb", "springfield1873", "taurus500",
            "timeless50", "type_81", "ump45", "uzi", "vector45");
    private static final Map<ResourceLocation, String> EXPECTED_GROUP_RANKS = Map.of(
            id("taczweaponblueprints:pistols"),
            "glock_17|m9a4|m1911,cz75|p320,rhino357,hk_mk23,lonetrail,b93r|"
                    + "deagle|deagle_golden,timeless50|taurus500",
            id("taczweaponblueprints:smgs"),
            "|uzi|hk_mp5a5|ump45|p90|vector45",
            id("taczweaponblueprints:shotguns"),
            "|db_long|db_short,m870|m1014|aa12|spas_12",
            id("taczweaponblueprints:rifles"),
            "|m16a4|m4a1,type_81|aug,hk_g3,m16a1,ak47|qbz_95,g36k,scar_l,sks_tactical|"
                    + "fn_fal,hk416d|qbz_191|mk14|spr15hb|scar_h",
            id("taczweaponblueprints:snipers"),
            "|springfield1873|kar98,m700|ai_awp|m95|m107",
            id("taczweaponblueprints:machine_guns"),
            "|rpk|m249|fn_evolys||minigun",
            id("taczweaponblueprints:special_weapons"),
            "|m320|||rpg7");
    private static final Map<ResourceLocation, List<ResourceLocation>> EXPECTED_PISTOL_PREREQUISITES =
            Map.ofEntries(
                    Map.entry(id("tacz:glock_17"), List.of()),
                    Map.entry(id("tacz:m9a4"), List.of(id("tacz:glock_17"))),
                    Map.entry(id("tacz:m1911"), List.of(id("tacz:glock_17"))),
                    Map.entry(id("tacz:cz75"), List.of(id("tacz:glock_17"))),
                    Map.entry(id("tacz:p320"), List.of(id("tacz:m1911"), id("tacz:cz75"))),
                    Map.entry(id("tacz:hk_mk23"), List.of(id("tacz:glock_17"))),
                    Map.entry(id("tacz:rhino357"), List.of(id("tacz:m9a4"), id("tacz:m1911"))),
                    Map.entry(id("tacz:lonetrail"), List.of(id("tacz:m1911"))),
                    Map.entry(id("tacz:b93r"), List.of(id("tacz:cz75"))),
                    Map.entry(id("tacz:deagle"), List.of(id("tacz:p320"), id("tacz:rhino357"))),
                    Map.entry(id("tacz:deagle_golden"), List.of(id("tacz:p320"))),
                    Map.entry(id("tacz:timeless50"), List.of(id("tacz:p320"))),
                    Map.entry(id("tacz:taurus500"),
                            List.of(id("tacz:rhino357"), id("tacz:timeless50"))));

    @Test
    void packagedRulesCoverEveryPinnedTaCZ118RecipeExactlyOnce() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Set<ResourceLocation> guns = pinnedRecipeIds("fixtures/tacz-1.1.8-gun-recipes.txt");
        Set<ResourceLocation> attachments = pinnedRecipeIds(
                "fixtures/tacz-1.1.8-attachment-recipes.txt");
        Set<ResourceLocation> ammo = pinnedRecipeIds("fixtures/tacz-1.1.8-ammo-recipes.txt");
        Set<ResourceLocation> expected = new LinkedHashSet<>();
        expected.addAll(guns);
        expected.addAll(attachments);
        expected.addAll(ammo);
        Set<ResourceLocation> targets = new LinkedHashSet<>();
        for (BlueprintResearchRule rule : snapshot.rules().values()) {
            assertEquals(PROFILE_ID, rule.profile());
            if (!rule.target().exactOnly()) {
                continue;
            }
            assertEquals(100, rule.priority());
            assertEquals(JournalVisibility.FULL, rule.visibility().orElseThrow());
            assertTrue(rule.treeEnabled().orElse(false));
            for (ResourceLocation target : rule.target().blueprints()) {
                assertTrue(expected.contains(target), () -> "unknown pinned target " + target);
                assertTrue(targets.add(target), () -> "duplicate default-tree target " + target);
            }
            rule.prerequisites().orElse(List.of()).forEach(prerequisite ->
                    assertTrue(expected.contains(prerequisite),
                            () -> "unknown default-tree prerequisite " + prerequisite));
        }
        assertEquals(expected, targets);
        assertEquals(OFFICIAL_GUNS, pinnedTaCZ118RecipeGuns());
        assertEquals(172, targets.size());

        BlueprintResearchRule gunFallback = snapshot.rules().get(
                id("taczweaponblueprints:fallback_guns"));
        assertEquals(JournalVisibility.PREVIEW, gunFallback.visibility().orElseThrow());
        assertTrue(gunFallback.treeEnabled().orElse(false));
        assertEquals(List.of(BlueprintKind.GUN),
                gunFallback.target().selector().orElseThrow().blueprintKinds());
        assertEquals(
                List.of(BlueprintKind.ATTACHMENT),
                snapshot.rules().get(id("taczweaponblueprints:fallback_attachments"))
                        .target().selector().orElseThrow().blueprintKinds());
        assertEquals(
                List.of(BlueprintKind.AMMO),
                snapshot.rules().get(id("taczweaponblueprints:fallback_ammo"))
                        .target().selector().orElseThrow().blueprintKinds());
    }

    @Test
    void packagedTreeIsOneConnectedProgressionWithIncreasingCosts() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = catalog(OFFICIAL_GUNS);
        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                snapshot, catalog, PROFILE_ID);

        assertEquals(OFFICIAL_GUNS.size(), audit.assignedBlueprintCount());
        assertEquals(OFFICIAL_GUNS.size(), audit.treeVisibleBlueprintCount());
        assertEquals(1, audit.rootCount());
        assertEquals(1, audit.componentCount());
        assertTrue(audit.unassignedBlueprintIds().isEmpty());
        assertTrue(audit.independentBlueprintIds().isEmpty());
        assertFalse(audit.hasStructuralProblems());

        Set<ResourceLocation> reachable = new LinkedHashSet<>();
        reachable.add(ROOT_BLUEPRINT);
        boolean changed;
        do {
            changed = false;
            for (ResourceLocation target : OFFICIAL_GUNS) {
                BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                        snapshot, catalog, PROFILE_ID, target);
                if (!reachable.contains(target)
                        && reachable.containsAll(definition.prerequisites())) {
                    changed |= reachable.add(target);
                }
            }
        } while (changed);
        assertEquals(OFFICIAL_GUNS, reachable);

        for (ResourceLocation target : OFFICIAL_GUNS) {
            BlueprintResearchPolicyDefinition targetDefinition = BlueprintResearchPolicyResolver.definitionFor(
                    snapshot, catalog, PROFILE_ID, target);
            assertTrue(targetDefinition.researchEnabled());
            assertTrue(targetDefinition.visibility().ordinal() >= JournalVisibility.PREVIEW.ordinal());
            for (ResourceLocation prerequisite : targetDefinition.prerequisites()) {
                BlueprintResearchPolicyDefinition prerequisiteDefinition =
                        BlueprintResearchPolicyResolver.definitionFor(
                                snapshot, catalog, PROFILE_ID, prerequisite);
                assertTrue(
                        targetDefinition.researchCost().points()
                                >= prerequisiteDefinition.researchCost().points(),
                        () -> target + " must not cost less than prerequisite " + prerequisite);
            }
        }
    }

    @Test
    void packagedGroupsCoverEveryOfficialWeaponOnceInStableSidebarOrder() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        assertEquals(7, snapshot.groups().size());
        assertEquals(
                List.of(10, 20, 30, 40, 50, 60, 70),
                snapshot.groupsForProfile(PROFILE_ID).stream()
                        .map(binding -> binding.definition().order())
                        .toList());

        Set<ResourceLocation> grouped = new LinkedHashSet<>();
        for (BlueprintResearchSnapshot.GroupBinding binding : snapshot.groupsForProfile(PROFILE_ID)) {
            ResearchTreeGroupDefinition group = binding.definition();
            for (ResourceLocation member : group.members()) {
                assertTrue(grouped.add(member), () -> "duplicate default group member " + member);
                assertEquals(
                        binding.groupId(),
                        snapshot.placementFor(PROFILE_ID, member).orElseThrow().groupId());
            }
            assertTrue(group.members().contains(group.icon()));
        }
        assertEquals(OFFICIAL_GUNS, grouped);

        BlueprintResearchDiagnostics.GroupAudit audit = BlueprintResearchDiagnostics.auditGroups(
                snapshot,
                catalog(OFFICIAL_GUNS),
                PROFILE_ID);
        assertEquals(7, audit.authoredGroupCount());
        assertEquals(OFFICIAL_GUNS.size(), audit.groupedCatalogCount());
        assertTrue(audit.fallbackBlueprintIds().isEmpty());
        assertTrue(audit.missingMemberIds().isEmpty());
        assertFalse(audit.hasProblems());
    }

    @Test
    void packagedRanksAndPrerequisitesPreserveTheAuthoredBalanceContract() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = catalog(OFFICIAL_GUNS);
        Set<Integer> observedPointTiers = new LinkedHashSet<>();

        for (BlueprintResearchSnapshot.GroupBinding binding : snapshot.groupsForProfile(PROFILE_ID)) {
            ResearchTreeGroupDefinition group = binding.definition();
            assertEquals(
                    ranks(EXPECTED_GROUP_RANKS.get(binding.groupId())),
                    group.ranks(),
                    () -> "unexpected authored ranks for " + binding.groupId());
            ResourceLocation firstMember = group.ranks().stream()
                    .filter(rank -> !rank.isEmpty())
                    .findFirst()
                    .orElseThrow()
                    .get(0);
            assertEquals(firstMember, group.icon());

            for (ResourceLocation member : group.members()) {
                ResearchTreeGroupPlacement placement = snapshot.placementFor(PROFILE_ID, member).orElseThrow();
                BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                        snapshot,
                        catalog,
                        PROFILE_ID,
                        member);
                observedPointTiers.add(definition.researchCost().points());
                assertTrue(DEFAULT_POINT_TIERS.contains(definition.researchCost().points()));

                if (member.equals(ROOT_BLUEPRINT)) {
                    assertTrue(definition.prerequisites().isEmpty(), () -> "root has prerequisites: " + member);
                    continue;
                }

                assertFalse(definition.prerequisites().isEmpty(), () -> "non-root has no prerequisite: " + member);
                if (BRANCH_ROOTS.contains(member)) {
                    assertEquals(List.of(ROOT_BLUEPRINT), definition.prerequisites(),
                            () -> "branch root must connect directly to the shared entry: " + member);
                }
                for (ResourceLocation prerequisite : definition.prerequisites()) {
                    ResearchTreeGroupPlacement prerequisitePlacement = snapshot
                            .placementFor(PROFILE_ID, prerequisite)
                            .orElseThrow();
                    assertTrue(
                            prerequisitePlacement.rank() < placement.rank(),
                            () -> member + " does not rise above prerequisite " + prerequisite);
                }
            }
        }

        assertEquals(DEFAULT_POINT_TIERS, observedPointTiers);
    }

    @Test
    void packagedPistolPrerequisitesMatchTheLayoutMigrationFixture() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = catalog(OFFICIAL_GUNS);

        ResearchTreeGroupDefinition pistols = snapshot.groupsForProfile(PROFILE_ID).stream()
                .filter(binding -> binding.groupId().equals(id("taczweaponblueprints:pistols")))
                .findFirst()
                .orElseThrow()
                .definition();
        assertEquals(EXPECTED_PISTOL_PREREQUISITES.keySet(),
                new LinkedHashSet<>(pistols.members()));
        for (ResourceLocation pistol : pistols.members()) {
            BlueprintResearchPolicyDefinition definition =
                    BlueprintResearchPolicyResolver.definitionFor(
                            snapshot, catalog, PROFILE_ID, pistol);
            assertEquals(
                    EXPECTED_PISTOL_PREREQUISITES.get(pistol),
                    definition.prerequisites(),
                    () -> "unexpected packaged pistol prerequisite for " + pistol);
        }
    }

    @Test
    void addonGunsAreSelectableIndependentFallbacksAndRemainExportable() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>(catalog(OFFICIAL_GUNS));
        ResourceLocation addon = id("example_pack:laser_rifle");
        catalog.put(addon, data(addon));

        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                snapshot, catalog, PROFILE_ID);
        assertTrue(audit.unassignedBlueprintIds().isEmpty());
        assertEquals(OFFICIAL_GUNS.size() + 1, audit.treeVisibleBlueprintCount());
        assertEquals(2, audit.rootCount());
        assertEquals(2, audit.componentCount());
        assertEquals(List.of(addon), audit.independentBlueprintIds());
        assertFalse(audit.hasStructuralProblems());

        BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog, PROFILE_ID, addon);
        assertEquals(id("taczweaponblueprints:fallback_guns"), definition.ruleId().orElseThrow());
        assertEquals(JournalVisibility.PREVIEW, definition.visibility());
        assertTrue(definition.treeEnabled());
        assertTrue(definition.prerequisites().isEmpty());

        String exported = BlueprintResearchCatalogExporter.export(snapshot, catalog, PROFILE_ID);
        assertTrue(exported.contains("\"blueprint\": \"example_pack:laser_rifle\""));
        assertTrue(exported.contains("\"catalog_size\": 54"));
        var exportRoot = com.google.gson.JsonParser.parseString(exported).getAsJsonObject();
        assertEquals(12, exportRoot.get("format").getAsInt());
        var presentation = exportRoot.getAsJsonObject("tech_tree_presentation");
        assertEquals("dynamic", presentation.get("band_mode").getAsString());
        assertEquals(3, presentation.get("ranks_per_band").getAsInt());
    }

    @Test
    void missingPreferredEntryPointRebasesTheTreeOntoTheNextAvailablePistol() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>(catalog(OFFICIAL_GUNS));
        catalog.remove(ROOT_BLUEPRINT);
        ResourceLocation fallback = id("tacz:m9a4");

        BlueprintResearchPolicyResolver.EntryPointResolution resolution =
                BlueprintResearchPolicyResolver.entryPointResolution(snapshot, catalog, PROFILE_ID);
        assertTrue(resolution.usesFallback());
        assertEquals(ROOT_BLUEPRINT, resolution.preferred().orElseThrow());
        assertEquals(fallback, resolution.resolved().orElseThrow());
        assertTrue(BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog, PROFILE_ID, fallback).prerequisites().isEmpty());
        for (ResourceLocation branchRoot : BRANCH_ROOTS) {
            assertEquals(
                    List.of(fallback),
                    BlueprintResearchPolicyResolver.definitionFor(
                            snapshot, catalog, PROFILE_ID, branchRoot).prerequisites());
        }

        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                snapshot, catalog, PROFILE_ID);
        assertEquals(1, audit.rootCount());
        assertEquals(1, audit.componentCount());
        assertTrue(audit.missingPrerequisiteIds().isEmpty());
        assertFalse(audit.hasStructuralProblems());
    }

    @Test
    void blockedPreferredEntryPointAlsoUsesTheNextSelectablePistol() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = catalog(OFFICIAL_GUNS);
        ResourceLocation fallback = id("tacz:m9a4");

        assertEquals(
                fallback,
                BlueprintResearchPolicyResolver.entryPointResolution(
                        snapshot,
                        catalog,
                        PROFILE_ID,
                        ROOT_BLUEPRINT.toString()::equals)
                        .resolved()
                        .orElseThrow());

        BlueprintResearchPolicy fallbackPolicy = BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                PROFILE_ID,
                fallback,
                new PlayerRecipeData(),
                ROOT_BLUEPRINT.toString()::equals);
        assertTrue(fallbackPolicy.prerequisites().isEmpty());

        BlueprintResearchPolicy branchPolicy = BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                PROFILE_ID,
                id("tacz:uzi"),
                new PlayerRecipeData(),
                ROOT_BLUEPRINT.toString()::equals);
        assertEquals(List.of(fallback), branchPolicy.prerequisites());
    }

    @Test
    void disabledTechDomainEntriesRemainDormantWhenPreferredContentIsMissing() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = completePinnedCatalog();
        ResourceLocation attachmentRoot = id("tacz:grip_rk6");
        ResourceLocation attachmentFallback = id("tacz:sight_acro_pistol");
        ResourceLocation ammoRoot = id("tacz:9mm");
        ResourceLocation ammoFallback = id("tacz:22wmr");
        catalog.remove(attachmentRoot);
        catalog.remove(ammoRoot);

        List<BlueprintResearchPolicyResolver.EntryPointResolution> resolutions =
                BlueprintResearchPolicyResolver.entryPointResolutions(
                        snapshot, catalog, PROFILE_ID);
        assertEquals(3, resolutions.size());
        assertEquals(ROOT_BLUEPRINT, resolutions.get(0).resolved().orElseThrow());
        assertEquals(attachmentRoot, resolutions.get(1).preferred().orElseThrow());
        assertTrue(resolutions.get(1).unavailable());
        assertEquals(ammoRoot, resolutions.get(2).preferred().orElseThrow());
        assertTrue(resolutions.get(2).unavailable());
        assertFalse(BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog, PROFILE_ID, attachmentFallback).treeEnabled());
        assertFalse(BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog, PROFILE_ID, ammoFallback).researchEnabled());

        BlueprintResearchDiagnostics.Audit graphAudit = BlueprintResearchDiagnostics.audit(
                snapshot, catalog, PROFILE_ID);
        assertEquals(1, graphAudit.rootCount());
        assertEquals(1, graphAudit.componentCount());

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                catalog, snapshot, config(), new PlayerRecipeData(), ignored -> false);
        ResearchTechTreeTopologyAudit.Audit topology = ResearchTechTreeTopologyAudit.audit(
                publication.graph(), publication.techTree());
        assertTrue(topology.allDomainsUnified());
        assertTrue(topology.domain(Domain.ATTACHMENTS).isEmpty());
        assertTrue(topology.domain(Domain.AMMO).isEmpty());
    }

    @Test
    void disabledTechDomainEntriesRemainUnavailableWhenPreferredContentIsBlocked() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = completePinnedCatalog();
        Set<String> blocked = Set.of("tacz:grip_rk6", "tacz:9mm");

        List<BlueprintResearchPolicyResolver.EntryPointResolution> resolutions =
                BlueprintResearchPolicyResolver.entryPointResolutions(
                        snapshot, catalog, PROFILE_ID, blocked::contains);
        assertTrue(resolutions.get(1).unavailable());
        assertTrue(resolutions.get(2).unavailable());

        BlueprintResearchPolicy attachmentFallback = BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                PROFILE_ID,
                id("tacz:sight_acro_pistol"),
                new PlayerRecipeData(),
                blocked::contains);
        assertFalse(attachmentFallback.treeEnabled());
        assertFalse(attachmentFallback.researchEnabled());
        BlueprintResearchPolicy ammoDependent = BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                PROFILE_ID,
                id("tacz:45acp"),
                new PlayerRecipeData(),
                blocked::contains);
        assertFalse(ammoDependent.treeEnabled());
        assertFalse(ammoDependent.researchEnabled());
    }

    @Test
    void addonAmmoAndAttachmentsKeepAuthoredFallbacksButAreDormantByDefault() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        ResourceLocation ammo = id("example_pack:test_ammo");
        ResourceLocation attachment = id("example_pack:test_scope");
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>(catalog(OFFICIAL_GUNS));
        catalog.put(ammo, data(ammo, BlueprintKind.AMMO, "ammo"));
        catalog.put(attachment, data(attachment, BlueprintKind.ATTACHMENT, "scope"));

        for (ResourceLocation id : List.of(ammo, attachment)) {
            BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                    snapshot, catalog, PROFILE_ID, id);
            assertTrue(definition.journalEnabled());
            assertFalse(definition.treeEnabled());
            assertFalse(definition.researchEnabled());
            assertEquals(JournalVisibility.PREVIEW, definition.visibility());
            assertTrue(definition.prerequisites().isEmpty());
            assertEquals(4, definition.researchCost().points());
            assertEquals(2, definition.researchCost().ingredients().size());
        }
        assertEquals(
                id("taczweaponblueprints:fallback_ammo"),
                BlueprintResearchPolicyResolver.definitionFor(
                        snapshot, catalog, PROFILE_ID, ammo).ruleId().orElseThrow());
        assertEquals(
                id("taczweaponblueprints:fallback_attachments"),
                BlueprintResearchPolicyResolver.definitionFor(
                        snapshot, catalog, PROFILE_ID, attachment).ruleId().orElseThrow());
        assertEquals(
                id("taczweaponblueprints:ammo/general"),
                ResearchTechTreePlacementResolver.resolve(
                                snapshot, TECH_TREE_ID, ammo, catalog.get(ammo))
                        .placement()
                        .orElseThrow()
                        .lane());
        assertEquals(
                id("taczweaponblueprints:attachments/general"),
                ResearchTechTreePlacementResolver.resolve(
                                snapshot, TECH_TREE_ID, attachment, catalog.get(attachment))
                        .placement()
                        .orElseThrow()
                        .lane());
        assertEquals(OFFICIAL_GUNS.size(),
                BlueprintResearchDiagnostics.audit(
                        snapshot, catalog, PROFILE_ID).treeVisibleBlueprintCount());

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                catalog,
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false);
        assertEquals(OFFICIAL_GUNS.size(), publication.graph().nodes().size());
        assertEquals(OFFICIAL_GUNS.size(), publication.legacyGraph().nodes().size());
        assertTrue(publication.presentation().membership(ammo).isEmpty());
        assertTrue(publication.presentation().membership(attachment).isEmpty());
        assertEquals(
                List.of(Domain.WEAPONS),
                publication.techTree().domains().stream()
                        .map(ResearchTechTreePresentation.DomainView::domain)
                        .toList());
    }

    @Test
    void disabledAmmoResearchStillAllowsPhysicalBlueprintLearning() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        ResourceLocation ammo = id("tacz:45acp");
        Map<ResourceLocation, BlueprintData> catalog = completePinnedCatalog();
        PlayerRecipeData playerData = new PlayerRecipeData();
        BlueprintResearchPolicy policy = BlueprintResearchPolicyResolver.resolve(
                snapshot,
                catalog,
                PROFILE_ID,
                ammo,
                playerData,
                ignored -> false);

        assertFalse(policy.treeEnabled());
        assertFalse(policy.researchEnabled());
        BlueprintLearningService.Result result = BlueprintLearningService.learn(
                new BlueprintLearningService.Request(
                        BlueprintUnlockOrigin.PHYSICAL_BLUEPRINT,
                        ammo,
                        true,
                        snapshot.profiles().get(PROFILE_ID).reverseEngineering()
                                .physicalBlueprintLearningMode(),
                        false),
                playerData,
                ignored -> new BlueprintLearningService.LearningTarget(
                        ammo, catalog.get(ammo).getRecipeId()),
                ignored -> policy);

        assertTrue(result.successful());
        assertTrue(result.prerequisitesBypassed());
        assertTrue(playerData.hasBlueprint(ammo.toString()));
    }

    @Test
    void pinnedAmmoAndAttachmentPoliciesRemainAuthoredButDormantByDefault() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Set<ResourceLocation> attachments = pinnedRecipeIds(
                "fixtures/tacz-1.1.8-attachment-recipes.txt");
        Set<ResourceLocation> ammo = pinnedRecipeIds("fixtures/tacz-1.1.8-ammo-recipes.txt");
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        attachments.forEach(value -> catalog.put(
                value, data(value, BlueprintKind.ATTACHMENT, "attachment")));
        ammo.forEach(value -> catalog.put(value, data(value, BlueprintKind.AMMO, "ammo")));
        Map<Domain, Map<Tier, Long>> observed = new LinkedHashMap<>();
        Map<Domain, Set<ResourceLocation>> roots = new LinkedHashMap<>();

        for (Map.Entry<ResourceLocation, BlueprintData> entry : catalog.entrySet()) {
            ResourceLocation blueprintId = entry.getKey();
            BlueprintResearchPolicyDefinition definition =
                    BlueprintResearchPolicyResolver.definitionFor(
                            snapshot, catalog, PROFILE_ID, blueprintId);
            ResearchTechTreePlacementResolver.Placement placement =
                    ResearchTechTreePlacementResolver.resolve(
                                    snapshot, TECH_TREE_ID, blueprintId, entry.getValue())
                            .placement()
                            .orElseThrow();
            assertFalse(definition.treeEnabled());
            assertFalse(definition.researchEnabled());
            assertEquals(JournalVisibility.FULL, definition.visibility());
            assertFalse(definition.requiresDiscovery());
            if (definition.prerequisites().isEmpty()) {
                roots.computeIfAbsent(placement.domain(), ignored -> new LinkedHashSet<>())
                        .add(blueprintId);
            } else {
                assertEquals(1, definition.prerequisites().size());
                ResourceLocation prerequisiteId = definition.prerequisites().get(0);
                BlueprintData prerequisiteData = catalog.get(prerequisiteId);
                assertTrue(prerequisiteData != null,
                        () -> "missing authored prerequisite " + prerequisiteId);
                ResearchTechTreePlacementResolver.Placement prerequisitePlacement =
                        ResearchTechTreePlacementResolver.resolve(
                                        snapshot, TECH_TREE_ID, prerequisiteId, prerequisiteData)
                                .placement()
                                .orElseThrow();
                assertEquals(placement.domain(), prerequisitePlacement.domain());
                assertTrue(ResearchTechTreeContract.tierTransitionAllowed(
                        prerequisitePlacement.tier(), placement.tier()));
                if (prerequisitePlacement.tier() == placement.tier()) {
                    assertTrue(prerequisitePlacement.order() < placement.order());
                }
            }
            assertEquals(DEFAULT_POINTS_BY_TECH_TIER.get(placement.tier()),
                    definition.researchCost().points());
            assertEquals(MatchSpecificity.EXACT, definition.specificity());
            observed.computeIfAbsent(placement.domain(), ignored -> new LinkedHashMap<>())
                    .merge(placement.tier(), 1L, Long::sum);
        }

        assertEquals(
                Map.of(
                        Tier.STARTER, 13L,
                        Tier.BASIC, 17L,
                        Tier.ESTABLISHED, 17L,
                        Tier.ADVANCED, 17L,
                        Tier.ELITE, 19L,
                        Tier.APEX, 12L),
                observed.get(Domain.ATTACHMENTS));
        assertEquals(
                Map.of(
                        Tier.STARTER, 3L,
                        Tier.BASIC, 5L,
                        Tier.ESTABLISHED, 5L,
                        Tier.ADVANCED, 4L,
                        Tier.ELITE, 5L,
                        Tier.APEX, 2L),
                observed.get(Domain.AMMO));
        assertEquals(
                Map.of(
                        Domain.ATTACHMENTS, Set.of(id("tacz:grip_rk6")),
                        Domain.AMMO, Set.of(id("tacz:9mm"))),
                roots);
    }

    @Test
    void packagedDefaultPublishesOneConnectedWeaponProgression() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = completePinnedCatalog();
        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                snapshot, catalog, PROFILE_ID);

        assertEquals(172, audit.catalogSize());
        assertEquals(172, audit.assignedBlueprintCount());
        assertEquals(53, audit.treeVisibleBlueprintCount());
        assertEquals(1, audit.rootCount());
        assertEquals(1, audit.componentCount());
        assertTrue(audit.independentBlueprintIds().isEmpty());
        assertTrue(audit.unassignedBlueprintIds().isEmpty());
        assertTrue(audit.missingPrerequisiteIds().isEmpty());
        assertTrue(audit.hiddenPrerequisiteTargetIds().isEmpty());
        assertTrue(audit.unselectableResearchTargetIds().isEmpty());
        assertTrue(audit.competitions().isEmpty());
        assertFalse(audit.hasStructuralProblems());

        ResearchTreePublication publication = ResearchTreeBuilder.buildPublication(
                catalog, snapshot, config(), new PlayerRecipeData(), ignored -> false);
        ResearchTechTreeTopologyAudit.Audit topology = ResearchTechTreeTopologyAudit.audit(
                publication.graph(), publication.techTree());
        assertTrue(topology.allDomainsUnified());
        ResearchTechTreeTopologyAudit.DomainAudit weapons =
                topology.domain(Domain.WEAPONS).orElseThrow();
        assertEquals(ResearchTechTreeContract.DEFAULT_WEAPON_COUNT, weapons.nodeCount());
        assertTrue(weapons.internalEdgeCount() >= weapons.nodeCount() - 1);
        assertEquals(Set.of(ROOT_BLUEPRINT), weapons.rootIds());
        assertEquals(1, weapons.componentCount());
        assertEquals(weapons.nodeCount(), weapons.reachableNodeCount());
        assertEquals(0, weapons.boundaryPrerequisiteCount());
        assertEquals(0, weapons.unplacedPrerequisiteCount());
        assertEquals(1, weapons.rootIds().size());
        assertEquals(2, weapons.maximumPrerequisiteCount());
        assertTrue(weapons.maximumDependentCount() >= 2);
        assertTrue(weapons.maximumDepth() > 0);
        assertEquals(8, weapons.maximumRankPopulation());
        assertEquals(0, weapons.emptyRankCount());
        assertEquals(16, weapons.mergeCount());
        assertTrue(weapons.crossBranchMergeCount() > 0);
        assertEquals(53, weapons.manualNodeCount());
        assertEquals(0, weapons.automaticNodeCount());
        assertFalse(topology.parentRetention().available());

        ResearchTechTreeEconomyAudit.DomainEconomy economy =
                ResearchTechTreeEconomyAudit.audit(
                                publication.graph(),
                                publication.techTree(),
                                new ResearchPointAwardEconomyProjection.Projection(
                                        12,
                                        3,
                                        218,
                                        Map.of(ResearchPointAwardTrigger.Type.INTEGRATION, 218)))
                        .domain(Domain.WEAPONS).orElseThrow();
        assertEquals(418, economy.fullTreeCost());
        assertEquals(1, economy.foundationCount());
        assertEquals(2, economy.foundationCost());
        assertEquals(15, economy.leafCount());
        assertEquals(10, economy.minimumLeafSinglePathCost());
        assertEquals(54, economy.maximumLeafSinglePathCost());
        assertEquals(10, economy.minimumLeafUnlockClosureCost());
        assertEquals(88, economy.maximumLeafUnlockClosureCost());
        assertEquals(16, economy.andMergeCount());
        assertEquals(16, economy.additionalMergePrerequisiteCount());
        assertTrue(economy.minimumLeafSinglePathCost()
                <= economy.maximumLeafSinglePathCost());
        assertTrue(economy.maximumLeafSinglePathCost()
                <= economy.maximumLeafUnlockClosureCost());
        assertEquals(5_215, economy.finiteIncomeCoverageBasisPoints());
        assertTrue(topology.domain(Domain.ATTACHMENTS).isEmpty());
        assertTrue(topology.domain(Domain.AMMO).isEmpty());
    }

    @Test
    void packagedTechTreeDefinesDynamicBandsAndDormantOptInDomains() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        BlueprintResearchProfile profile = snapshot.profiles().get(PROFILE_ID);
        assertEquals(2, profile.format());
        assertEquals(BlueprintResearchProfile.DomainPolicy.ENABLED,
                profile.domainPolicy(Domain.WEAPONS));
        assertEquals(new BlueprintResearchProfile.DomainPolicy(false, false),
                profile.domainPolicy(Domain.ATTACHMENTS));
        assertEquals(new BlueprintResearchProfile.DomainPolicy(false, false),
                profile.domainPolicy(Domain.AMMO));
        assertEquals(TECH_TREE_ID, profile.techTree().orElseThrow());
        assertEquals(id("tacz:grip_rk6"),
                profile.techEntryPointCandidates().get(Domain.ATTACHMENTS).get(0));
        assertEquals(id("tacz:9mm"),
                profile.techEntryPointCandidates().get(Domain.AMMO).get(0));
        assertEquals(Set.of(TECH_TREE_ID), snapshot.techTrees().keySet());
        assertEquals(4, snapshot.techTreeEntryBundles().size());
        ResearchAutomaticPlacementProfile automatic = parseAutomaticPlacementProfile(Path.of(
                "src/main/resources/data/taczweaponblueprints/taczweaponblueprints/"
                        + "research_automatic_placement_profiles/default.json"));
        assertEquals(AutomaticPlacementMode.CONNECTED, automatic.mode());
        assertEquals(2, automatic.format());
        assertEquals(3, automatic.levelsPerTier());
        assertEquals(ReviewHandling.PLACE_CONNECTED, automatic.reviewHandling());
        assertTrue(automatic.placementPolicy().usesDynamicLayers());
        assertEquals(9, automatic.maxNodesPerRank());
        assertEquals(2, automatic.foundationCount());
        assertTrue(automatic.progressionBands().isEmpty());

        ResearchTechTreeDefinition tree = snapshot.techTrees().get(TECH_TREE_ID);
        assertEquals(2, tree.format());
        assertEquals(ResearchTechTreeDefinition.WidthMode.DYNAMIC,
                tree.layout().widthMode());
        assertEquals(9, tree.layout().minNodesPerLayer());
        assertEquals(20, tree.layout().maxNodesPerLayer());
        assertEquals(ResearchTechTreeDefinition.BandMode.DYNAMIC,
                tree.bandPolicy().mode());
        assertEquals(3, tree.bandPolicy().ranksPerBand());
        assertTrue(tree.tiers().isEmpty());
        assertEquals(List.of(Domain.WEAPONS, Domain.ATTACHMENTS, Domain.AMMO), tree.domains().stream()
                .map(ResearchTechTreeDefinition.DomainDefinition::domain)
                .toList());
        for (ResearchTechTreeDefinition.DomainDefinition domain : tree.domains()) {
            assertEquals(Tier.BASIC, domain.fallbackTier());
            assertTrue(domain.lanes().stream().anyMatch(lane -> lane.id().equals(domain.fallbackLane())));
            assertTrue(domain.lanes().stream().map(ResearchTechTreeDefinition.LaneDefinition::order)
                    .distinct().count() == domain.lanes().size());
        }
    }

    @Test
    void packagedTechTreeCoversEveryPinnedTaCZ118RecipeExactlyOnce() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Set<ResourceLocation> guns = pinnedRecipeIds("fixtures/tacz-1.1.8-gun-recipes.txt");
        Set<ResourceLocation> attachments = pinnedRecipeIds("fixtures/tacz-1.1.8-attachment-recipes.txt");
        Set<ResourceLocation> ammo = pinnedRecipeIds("fixtures/tacz-1.1.8-ammo-recipes.txt");
        Map<ResourceLocation, Domain> expectedDomains = new LinkedHashMap<>();
        guns.forEach(value -> expectedDomains.put(value, Domain.WEAPONS));
        attachments.forEach(value -> expectedDomains.put(value, Domain.ATTACHMENTS));
        ammo.forEach(value -> expectedDomains.put(value, Domain.AMMO));
        assertEquals(172, expectedDomains.size());

        Set<ResourceLocation> exactTargets = new LinkedHashSet<>();
        int selectorFallbacks = 0;
        for (BlueprintResearchSnapshot.TechTreeEntryBinding binding
                : snapshot.techTreeEntriesFor(TECH_TREE_ID)) {
            ResearchTechTreeEntryBundle.Entry entry = binding.entry();
            if (!entry.target().exactOnly()) {
                selectorFallbacks++;
                assertEquals(0, binding.bundle().priority());
                assertEquals(ResearchTechTreeEntryBundle.LEGACY_FORMAT,
                        binding.bundle().format());
                assertTrue(entry.target().selector().isPresent());
                assertTrue(entry.fallback());
                assertTrue(entry.rating().isEmpty());
                continue;
            }
            assertFalse(entry.fallback());
            assertEquals(1, entry.target().blueprints().size());
            ResourceLocation target = entry.target().blueprints().get(0);
            assertTrue(exactTargets.add(target), () -> "duplicate exact Tech Tree placement " + target);
            assertEquals(expectedDomains.get(target), entry.domain(),
                    () -> "wrong Tech Tree domain for " + target);
            assertEquals(100, binding.bundle().priority());
            if (entry.domain() == Domain.WEAPONS) {
                assertEquals(ResearchTechTreeEntryBundle.CURRENT_FORMAT,
                        binding.bundle().format());
                assertTrue(entry.rank().isPresent(),
                        () -> "missing explicit weapon rank for " + target);
                assertTrue(entry.rating().isPresent(), () -> "missing weapon rating for " + target);
            } else {
                assertEquals(ResearchTechTreeEntryBundle.LEGACY_FORMAT,
                        binding.bundle().format());
                assertTrue(entry.rank().isEmpty(),
                        () -> "dormant legacy placement unexpectedly has a rank for " + target);
                assertTrue(entry.rating().isEmpty(), () -> "non-weapon rating for " + target);
            }
        }
        assertEquals(expectedDomains.keySet(), exactTargets);
        assertEquals(3, selectorFallbacks);
    }

    @Test
    void packagedTaCZ118ProgressionHasAStableSemanticFingerprint() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = completePinnedCatalog();
        StringBuilder manifest = new StringBuilder();
        catalog.keySet().stream().sorted().forEach(blueprintId -> {
            ResearchTechTreePlacementResolver.Placement placement =
                    ResearchTechTreePlacementResolver.resolve(
                                    snapshot,
                                    TECH_TREE_ID,
                                    blueprintId,
                                    catalog.get(blueprintId))
                            .placement()
                            .orElseThrow();
            BlueprintResearchPolicyDefinition policy =
                    BlueprintResearchPolicyResolver.definitionFor(
                            snapshot, catalog, PROFILE_ID, blueprintId);
            manifest.append(blueprintId)
                    .append('|').append(placement.domain())
                    .append('|').append(placement.lane())
                    .append('|').append(placement.tier())
                    .append('|').append(placement.level())
                    .append('|').append(placement.order())
                    .append('|').append(policy.researchCost().points())
                    .append('|').append(policy.prerequisites().stream()
                            .map(ResourceLocation::toString)
                            .collect(java.util.stream.Collectors.joining(",")))
                    .append('\n');
        });
        String fingerprint = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        manifest.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(
                "54e17c6f4cfb5e7044615e48309f9e35559bb6b76bddc5609bc16ab40e707d3c",
                fingerprint);
    }

    @Test
    void packagedFormat2WeaponTopologyHasAStableFingerprint() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = completePinnedCatalog();
        StringBuilder manifest = new StringBuilder();
        OFFICIAL_GUNS.stream().sorted().forEach(blueprintId -> {
            BlueprintResearchPolicyDefinition policy =
                    BlueprintResearchPolicyResolver.definitionFor(
                            snapshot, catalog, PROFILE_ID, blueprintId);
            manifest.append(blueprintId)
                    .append('|').append(snapshot.techTreeProgressionFor(
                            PROFILE_ID, blueprintId).orElseThrow().rank())
                    .append('|').append(policy.prerequisites().stream()
                            .map(ResourceLocation::toString)
                            .sorted()
                            .collect(java.util.stream.Collectors.joining(",")))
                    .append('\n');
        });
        String fingerprint = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(
                        manifest.toString().getBytes(StandardCharsets.UTF_8)));
        assertEquals(
                ResearchTechTreeContract.DEFAULT_WEAPON_TOPOLOGY_FINGERPRINT,
                fingerprint);
    }

    @Test
    void packagedTechTreePublishesOnlyWeaponsWhileDormantDataRemainsAuthored()
            throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        ResearchTreePublication publication = packagedPublication(snapshot);

        assertEquals(OFFICIAL_GUNS.size(), publication.graph().nodes().size());
        assertEquals(OFFICIAL_GUNS.size(), publication.legacyGraph().nodes().size());
        assertEquals(OFFICIAL_GUNS.size(), publication.presentation().groups().stream()
                .flatMap(group -> group.members().stream())
                .count());
        assertTrue(publication.techTree().available());
        assertEquals(TECH_TREE_ID, publication.techTree().treeId().orElseThrow());
        assertEquals(OFFICIAL_GUNS.size(), publication.techTree().memberCount());
        assertEquals(List.of(Domain.WEAPONS),
                publication.techTree().domains().stream()
                        .map(value -> value.domain())
                        .toList());
        assertEquals(
                Map.of(Domain.WEAPONS, 53L),
                publication.techTree().domains().stream().collect(
                        java.util.stream.Collectors.toMap(
                                ResearchTechTreePresentation.DomainView::domain,
                                domain -> domain.lanes().stream()
                                        .mapToLong(lane -> lane.members().size())
                                        .sum())));
        assertEquals(OFFICIAL_GUNS,
                publication.techTree().domains().get(0).lanes().stream()
                        .flatMap(lane -> lane.members().stream())
                        .map(member -> member.nodeId())
                        .collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void packagedTechTreeLayoutFitsTheEnabledWeaponDomain() throws Exception {
        ResearchTreePublication publication = packagedPublication(packagedSnapshot());
        ResearchTechTreeProjectionCatalog projections =
                ResearchTechTreeProjectionBuilder.build(publication);
        assertTrue(projections.relationships().isEmpty(),
                "independently researchable default domains must not publish fake cross-domain links");
        ResearchTechTreeLayoutCatalog layouts = ResearchTechTreeLayoutEngine.layoutCatalog(
                projections, ResearchTechTreeLayoutPolicy.DEFAULT);

        assertEquals(List.of(Domain.WEAPONS), layouts.domains());
        ResearchTechTreeLayout weapons = layouts.layout(Domain.WEAPONS).orElseThrow();
        assertEquals(OFFICIAL_GUNS.size(), weapons.graphLayout().nodes().size());
        assertTrue(layouts.layout(Domain.ATTACHMENTS).isEmpty());
        assertTrue(layouts.layout(Domain.AMMO).isEmpty());
        assertEquals(7, projections.projection(Domain.WEAPONS).orElseThrow()
                .presentation().lanes().size());
        assertTrue(weapons.graphLayout().groupRegions().isEmpty());
        assertTrue(weapons.tiers().isEmpty());
        assertFalse(weapons.bands().isEmpty());
        assertTrue(weapons.portals().isEmpty());
        for (Domain domain : layouts.domains()) {
            ResearchTechTreeLayout layout = layouts.layout(domain).orElseThrow();
            Set<ResourceLocation> members = publication.techTree().domain(domain).orElseThrow()
                    .lanes().stream()
                    .flatMap(lane -> lane.members().stream())
                    .map(ResearchTechTreePresentation.Member::nodeId)
                    .collect(java.util.stream.Collectors.toSet());
            for (var edge : publication.graph().edges()) {
                if (!members.contains(edge.prerequisiteId())
                        || !members.contains(edge.dependentId())) {
                    continue;
                }
                assertTrue(
                        layout.graphLayout().position(edge.prerequisiteId()).orElseThrow().y()
                                > layout.graphLayout().position(edge.dependentId()).orElseThrow().y(),
                        () -> "packaged Tech Tree edge must rise from "
                                + edge.prerequisiteId() + " to " + edge.dependentId());
            }
        }
    }

    @Test
    void exactPlacementsBeatConservativeAddonFallbacks() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        ResourceLocation official = id("tacz:glock_17");
        ResearchTechTreePlacementResolver.Placement officialPlacement =
                ResearchTechTreePlacementResolver.resolve(
                                snapshot, TECH_TREE_ID, official, data(official))
                        .placement()
                        .orElseThrow();
        assertEquals(MatchSpecificity.EXACT, officialPlacement.specificity());
        assertEquals(PlacementOrigin.EXACT, officialPlacement.origin());
        assertEquals(id("taczweaponblueprints:weapons/handguns"), officialPlacement.lane());
        assertEquals(Tier.STARTER, officialPlacement.tier());
        assertEquals(100, officialPlacement.priority());

        List<BlueprintData> addons = List.of(
                data(id("example_pack:laser_rifle"), BlueprintKind.GUN, "gun"),
                data(id("example_pack:test_scope"), BlueprintKind.ATTACHMENT, "scope"),
                data(id("example_pack:test_ammo"), BlueprintKind.AMMO, "ammo"));
        List<Domain> domains = List.of(Domain.WEAPONS, Domain.ATTACHMENTS, Domain.AMMO);
        List<ResourceLocation> fallbackLanes = List.of(
                id("taczweaponblueprints:weapons/general"),
                id("taczweaponblueprints:attachments/general"),
                id("taczweaponblueprints:ammo/general"));
        for (int index = 0; index < addons.size(); index++) {
            BlueprintData addon = addons.get(index);
            ResearchTechTreePlacementResolver.Placement placement =
                    ResearchTechTreePlacementResolver.resolve(
                                    snapshot, TECH_TREE_ID, addon.getRecipeId(), addon)
                            .placement()
                            .orElseThrow();
            assertEquals(MatchSpecificity.SELECTOR, placement.specificity());
            assertEquals(PlacementOrigin.LEGACY_FALLBACK, placement.origin());
            assertEquals(domains.get(index), placement.domain());
            assertEquals(fallbackLanes.get(index), placement.lane());
            assertEquals(Tier.BASIC, placement.tier());
            assertEquals(0, placement.priority());
        }
    }

    @Test
    void reviewedWeaponEvidenceMatchesTheAuthoredPlacements() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        var appealDocument = JsonParser.parseString(Files.readString(
                Path.of("src/authoring/resources/tacz-1.1.8-appeal-ratings.json"),
                StandardCharsets.UTF_8)).getAsJsonObject();
        var appealRatings = appealDocument.getAsJsonObject("ratings");
        assertEquals(OFFICIAL_GUNS.size(), appealRatings.size());

        Map<ResourceLocation, ResearchTechTreePlacementResolver.Placement> placements =
                new LinkedHashMap<>();
        for (ResourceLocation gun : OFFICIAL_GUNS) {
            ResearchTechTreePlacementResolver.Placement placement =
                    ResearchTechTreePlacementResolver.resolve(
                                    snapshot, TECH_TREE_ID, gun, data(gun))
                            .placement()
                            .orElseThrow();
            placements.put(gun, placement);
            var reviewed = appealRatings.getAsJsonObject(gun.toString());
            assertTrue(reviewed != null, () -> "missing reviewed appeal rating for " + gun);
            assertFalse(reviewed.get("reason").getAsString().isBlank());
            assertEquals(
                    reviewed.get("score").getAsInt(),
                    placement.rating().orElseThrow().appeal(),
                    () -> "appeal evidence drift for " + gun);
        }
        assertEquals(Set.of(Tier.values()), placements.values().stream()
                .map(ResearchTechTreePlacementResolver.Placement::tier)
                .collect(java.util.stream.Collectors.toSet()));
        assertEquals(
                Map.of(
                        Tier.STARTER, 1L,
                        Tier.BASIC, 6L,
                        Tier.ESTABLISHED, 11L,
                        Tier.ADVANCED, 17L,
                        Tier.ELITE, 13L,
                        Tier.APEX, 5L),
                placements.values().stream().collect(java.util.stream.Collectors.groupingBy(
                        ResearchTechTreePlacementResolver.Placement::tier,
                        java.util.stream.Collectors.counting())));
        assertEquals(39, placements.values().stream()
                .filter(placement -> placement.tierOverrideReason().isPresent())
                .count());
        assertEquals(
                Set.of(
                        id("tacz:taurus500"),
                        id("tacz:minigun"),
                        id("tacz:spr15hb"),
                        id("tacz:scar_h"),
                        id("tacz:m107")),
                placements.entrySet().stream()
                        .filter(entry -> entry.getValue().tier() == Tier.APEX)
                        .map(Map.Entry::getKey)
                        .collect(java.util.stream.Collectors.toSet()));

        Map<ResourceLocation, BlueprintData> catalog = catalog(OFFICIAL_GUNS);
        for (ResourceLocation gun : OFFICIAL_GUNS) {
            ResearchTechTreePlacementResolver.Placement dependent = placements.get(gun);
            BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                    snapshot, catalog, PROFILE_ID, gun);
            for (ResourceLocation prerequisiteId : definition.prerequisites()) {
                ResearchTechTreePlacementResolver.Placement prerequisite = placements.get(prerequisiteId);
                assertTrue(prerequisite.tier().ordinal() < dependent.tier().ordinal()
                                || prerequisite.tier() == dependent.tier()
                                        && prerequisite.order() < dependent.order(),
                        () -> gun + " must appear after prerequisite " + prerequisiteId);
            }
        }
    }

    @Test
    void exampleDatapackUsesTheProductionCodecs() throws Exception {
        Path root = Path.of("examples/research-tree-datapack/data/example/taczweaponblueprints");
        BlueprintResearchProfile profile = BlueprintResearchProfile.CODEC.parse(
                        JsonOps.INSTANCE,
                        JsonParser.parseString(Files.readString(
                                root.resolve("research_profiles/custom_progression.json"),
                                StandardCharsets.UTF_8)))
                .result()
                .orElseThrow();
        Map<ResourceLocation, BlueprintResearchRule> rules = new LinkedHashMap<>();
        Path rulesRoot = root.resolve("research_rules");
        try (Stream<Path> paths = Files.walk(rulesRoot)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> rules.put(
                            id("example:" + rulesRoot.relativize(path).toString()
                                    .replace('\\', '/')
                                    .replace(".json", "")),
                            parseRule(path)));
        }
        Map<ResourceLocation, ResearchTreeGroupDefinition> groups = new LinkedHashMap<>();
        Path groupsRoot = root.resolve("research_tree_groups");
        try (Stream<Path> paths = Files.walk(groupsRoot)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> groups.put(
                            id("example:" + groupsRoot.relativize(path).toString()
                                    .replace('\\', '/')
                                    .replace(".json", "")),
                            parseGroup(path)));
        }
        ResearchTechTreeDefinition techTree = parseTechTree(
                root.resolve("research_tech_trees/dynamic_weapons.json"));
        Map<ResourceLocation, ResearchTechTreeEntryBundle> entries = Map.of(
                id("example:dynamic_weapons"),
                parseTechTreeEntryBundle(root.resolve(
                        "research_tech_tree_entries/dynamic_weapons.json")),
                id("example:fallback_guns"),
                parseTechTreeEntryBundle(root.resolve(
                        "research_tech_tree_entries/fallback_guns.json")));
        ResearchAutomaticPlacementProfile automatic = parseAutomaticPlacementProfile(
                root.resolve("research_automatic_placement_profiles/dynamic_weapons.json"));
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(id("example:custom_progression"), profile),
                rules,
                groups,
                Map.of(id("example:dynamic_weapons"), techTree),
                entries,
                Map.of(id("example:dynamic_weapons"), automatic));
        assertEquals(2, profile.format());
        assertEquals(BlueprintResearchProfile.DomainPolicy.ENABLED,
                profile.domainPolicy(Domain.WEAPONS));
        assertEquals(new BlueprintResearchProfile.DomainPolicy(false, false),
                profile.domainPolicy(Domain.ATTACHMENTS));
        assertEquals(new BlueprintResearchProfile.DomainPolicy(false, false),
                profile.domainPolicy(Domain.AMMO));
        assertEquals(3, snapshot.rules().size());
        assertEquals(1, snapshot.groups().size());
        assertEquals(2, snapshot.techTreeEntryBundles().size());
        assertEquals(2, techTree.format());
        assertEquals(9, techTree.layout().maxNodesPerLayer());
        assertEquals(2, automatic.format());
        assertTrue(automatic.placementPolicy().usesDynamicLayers());
        assertEquals(0, snapshot.techTreeProgressionFor(
                id("example:custom_progression"),
                id("example_guns:starter_pistol")).orElseThrow().rank());
        assertEquals(1, snapshot.techTreeProgressionFor(
                id("example:custom_progression"),
                id("example_guns:advanced_pistol")).orElseThrow().rank());
        assertEquals(
                id("example:pistols"),
                snapshot.placementFor(
                        id("example:custom_progression"),
                        id("example_guns:advanced_pistol"))
                        .orElseThrow()
                        .groupId());
    }

    private static ResearchTreePublication packagedPublication(
            BlueprintResearchSnapshot snapshot) throws IOException {
        return ResearchTreeBuilder.buildPublication(
                completePinnedCatalog(),
                snapshot,
                config(),
                new PlayerRecipeData(),
                ignored -> false);
    }

    private static BlueprintProgressionConfigSnapshot config() {
        return new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                false,
                100,
                false,
                PROFILE_ID);
    }

    private static BlueprintResearchSnapshot packagedSnapshot() throws Exception {
        BlueprintResearchProfile profile;
        try (var stream = DefaultTaCZResearchTreeTest.class.getClassLoader().getResourceAsStream(
                "data/taczweaponblueprints/taczweaponblueprints/research_profiles/duplicate_recovery.json")) {
            assert stream != null;
            profile = BlueprintResearchProfile.CODEC.parse(
                            JsonOps.INSTANCE,
                            JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
                    .result()
                    .orElseThrow();
        }
        Map<ResourceLocation, BlueprintResearchRule> rules = new LinkedHashMap<>();
        Path root = Path.of(
                "src/main/resources/data/taczweaponblueprints/taczweaponblueprints/research_rules");
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> rules.put(ruleId(root, path), parseRule(path)));
        }
        Map<ResourceLocation, ResearchTreeGroupDefinition> groups = new LinkedHashMap<>();
        Path groupsRoot = Path.of(
                "src/main/resources/data/taczweaponblueprints/taczweaponblueprints/research_tree_groups");
        try (Stream<Path> paths = Files.walk(groupsRoot)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> groups.put(groupId(groupsRoot, path), parseGroup(path)));
        }
        Map<ResourceLocation, ResearchTechTreeDefinition> techTrees = new LinkedHashMap<>();
        Path techTreesRoot = Path.of(
                "src/main/resources/data/taczweaponblueprints/taczweaponblueprints/research_tech_trees");
        try (Stream<Path> paths = Files.walk(techTreesRoot)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> techTrees.put(resourceId(techTreesRoot, path), parseTechTree(path)));
        }
        Map<ResourceLocation, ResearchTechTreeEntryBundle> techTreeEntryBundles = new LinkedHashMap<>();
        Path entriesRoot = Path.of(
                "src/main/resources/data/taczweaponblueprints/taczweaponblueprints/research_tech_tree_entries");
        try (Stream<Path> paths = Files.walk(entriesRoot)) {
            paths.filter(path -> path.toString().endsWith(".json"))
                    .sorted()
                    .forEach(path -> techTreeEntryBundles.put(
                            resourceId(entriesRoot, path),
                            parseTechTreeEntryBundle(path)));
        }
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE_ID, profile),
                rules,
                groups,
                techTrees,
                techTreeEntryBundles);
    }

    private static BlueprintResearchRule parseRule(Path path) {
        try {
            return BlueprintResearchRule.CODEC.parse(
                            JsonOps.INSTANCE,
                            JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)))
                    .result()
                    .orElseThrow();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static ResearchTreeGroupDefinition parseGroup(Path path) {
        try {
            return ResearchTreeGroupDefinition.CODEC.parse(
                            JsonOps.INSTANCE,
                            JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)))
                    .result()
                    .orElseThrow();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static ResearchTechTreeDefinition parseTechTree(Path path) {
        try {
            return ResearchTechTreeDefinition.CODEC.parse(
                            JsonOps.INSTANCE,
                            JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)))
                    .result()
                    .orElseThrow();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static ResearchTechTreeEntryBundle parseTechTreeEntryBundle(Path path) {
        try {
            return ResearchTechTreeEntryBundle.CODEC.parse(
                            JsonOps.INSTANCE,
                            JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)))
                    .result()
                    .orElseThrow();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static ResearchAutomaticPlacementProfile parseAutomaticPlacementProfile(Path path) {
        try {
            return ResearchAutomaticPlacementProfile.CODEC.parse(
                            JsonOps.INSTANCE,
                            JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)))
                    .result()
                    .orElseThrow();
        } catch (IOException exception) {
            throw new IllegalStateException("Could not read " + path, exception);
        }
    }

    private static ResourceLocation ruleId(Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        return id("taczweaponblueprints:"
                + relative.substring(0, relative.length() - ".json".length()));
    }

    private static ResourceLocation groupId(Path root, Path path) {
        return resourceId(root, path);
    }

    private static ResourceLocation resourceId(Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        return id("taczweaponblueprints:"
                + relative.substring(0, relative.length() - ".json".length()));
    }

    private static Map<ResourceLocation, BlueprintData> catalog(Set<ResourceLocation> ids) {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        ids.stream().sorted().forEach(id -> catalog.put(id, data(id)));
        return catalog;
    }

    private static Map<ResourceLocation, BlueprintData> completePinnedCatalog() throws IOException {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>(catalog(OFFICIAL_GUNS));
        pinnedRecipeIds("fixtures/tacz-1.1.8-attachment-recipes.txt").forEach(value ->
                catalog.put(value, data(value, BlueprintKind.ATTACHMENT, "attachment")));
        pinnedRecipeIds("fixtures/tacz-1.1.8-ammo-recipes.txt").forEach(value ->
                catalog.put(value, data(value, BlueprintKind.AMMO, "ammo")));
        return catalog;
    }

    private static BlueprintData data(ResourceLocation id) {
        return data(id, BlueprintKind.GUN, "gun");
    }

    private static BlueprintData data(ResourceLocation id, BlueprintKind kind, String itemType) {
        return new BlueprintData(
                id.toString(),
                "item." + id.getNamespace() + "." + id.getPath(),
                "tooltip." + id.getNamespace() + "." + id.getPath(),
                id,
                null,
                itemType,
                id,
                kind);
    }

    private static Set<ResourceLocation> pinnedTaCZ118RecipeGuns() throws IOException {
        return pinnedRecipeIds("fixtures/tacz-1.1.8-gun-recipes.txt");
    }

    private static Set<ResourceLocation> pinnedRecipeIds(String resource) throws IOException {
        try (var stream = DefaultTaCZResearchTreeTest.class.getClassLoader().getResourceAsStream(
                resource)) {
            assert stream != null;
            return new LinkedHashSet<>(new java.io.BufferedReader(
                    new InputStreamReader(stream, StandardCharsets.UTF_8))
                    .lines()
                    .filter(line -> !line.isBlank() && !line.startsWith("#"))
                    .map(DefaultTaCZResearchTreeTest::id)
                    .toList());
        }
    }

    private static Set<ResourceLocation> ids(String... paths) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        Stream.of(paths).map(path -> id("tacz:" + path)).forEach(ids::add);
        return Set.copyOf(ids);
    }

    private static List<List<ResourceLocation>> ranks(String encodedRanks) {
        return Stream.of(encodedRanks.split("\\|", -1))
                .map(rank -> rank.isEmpty()
                        ? List.<ResourceLocation>of()
                        : Stream.of(rank.split(","))
                                .map(path -> id("tacz:" + path))
                                .toList())
                .toList();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
