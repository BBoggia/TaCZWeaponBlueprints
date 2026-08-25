package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class DefaultTaCZResearchTreeTest {
    private static final ResourceLocation PROFILE_ID = id("taczweaponblueprints:duplicate_recovery");
    private static final Set<Integer> DEFAULT_POINT_TIERS = Set.of(4, 6, 8, 10, 12);
    private static final Set<ResourceLocation> OFFICIAL_GUNS = ids(
            "aa12", "ai_awp", "ak47", "aug", "b93r", "cz75", "db_long", "db_short", "deagle",
            "deagle_golden", "fn_evolys", "fn_fal", "g36k", "glock_17", "hk416d", "hk_g3",
            "hk_mk23", "hk_mp5a5", "kar98", "lonetrail", "m1014", "m107", "m16a1", "m16a4",
            "m1911", "m249", "m320", "m4a1", "m700", "m870", "m95", "m9a4", "minigun",
            "mk14", "p320", "p90", "qbz_191", "qbz_95", "rhino357", "rpg7", "rpk", "scar_h",
            "scar_l", "sks_tactical", "spas_12", "spr15hb", "springfield1873", "taurus500",
            "taurus943", "timeless50", "type_81", "ump45", "uzi", "vector45");
    private static final Map<ResourceLocation, String> EXPECTED_GROUP_RANKS = Map.of(
            id("taczweaponblueprints:pistols"),
            "taurus943|glock_17,m9a4|m1911,cz75,p320,hk_mk23,rhino357|"
                    + "lonetrail,b93r,deagle,deagle_golden,timeless50|taurus500",
            id("taczweaponblueprints:smgs"),
            "uzi|hk_mp5a5|ump45,p90|vector45",
            id("taczweaponblueprints:shotguns"),
            "db_long|db_short,m870|m1014|aa12,spas_12",
            id("taczweaponblueprints:rifles"),
            "m16a4|m4a1,type_81,aug,hk_g3|m16a1,ak47,qbz_95,g36k,scar_l,fn_fal,sks_tactical|"
                    + "hk416d,qbz_191,mk14|spr15hb,scar_h",
            id("taczweaponblueprints:snipers"),
            "springfield1873|kar98,m700|ai_awp|m95|m107",
            id("taczweaponblueprints:machine_guns"),
            "rpk|m249|fn_evolys|minigun",
            id("taczweaponblueprints:special_weapons"),
            "m320|rpg7");

    @Test
    void packagedRulesCoverTheExactTaCZ118GunCatalogOnce() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Set<ResourceLocation> targets = new LinkedHashSet<>();
        for (BlueprintResearchRule rule : snapshot.rules().values()) {
            assertEquals(PROFILE_ID, rule.profile());
            assertEquals(100, rule.priority());
            assertEquals(JournalVisibility.FULL, rule.visibility().orElseThrow());
            assertTrue(rule.target().exactOnly());
            for (ResourceLocation target : rule.target().blueprints()) {
                assertTrue(targets.add(target), () -> "duplicate default-tree target " + target);
            }
            rule.prerequisites().orElse(List.of()).forEach(prerequisite ->
                    assertTrue(OFFICIAL_GUNS.contains(prerequisite),
                            () -> "unknown default-tree prerequisite " + prerequisite));
        }
        assertEquals(OFFICIAL_GUNS, targets);
    }

    @Test
    void packagedTreeHasSevenHealthyBranchesAndIncreasingCosts() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = catalog(OFFICIAL_GUNS);
        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                snapshot, catalog, PROFILE_ID);

        assertEquals(OFFICIAL_GUNS.size(), audit.assignedBlueprintCount());
        assertEquals(OFFICIAL_GUNS.size(), audit.treeVisibleBlueprintCount());
        assertEquals(7, audit.rootCount());
        assertEquals(7, audit.componentCount());
        assertTrue(audit.unassignedBlueprintIds().isEmpty());
        assertTrue(audit.independentBlueprintIds().isEmpty());
        assertFalse(audit.hasStructuralProblems());

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
                                > prerequisiteDefinition.researchCost().points(),
                        () -> target + " must cost more than prerequisite " + prerequisite);
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
            assertEquals(1, group.ranks().get(0).size(), () -> "branch must have one root: " + binding.groupId());
            assertEquals(group.ranks().get(0).get(0), group.icon());

            for (ResourceLocation member : group.members()) {
                ResearchTreeGroupPlacement placement = snapshot.placementFor(PROFILE_ID, member).orElseThrow();
                BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                        snapshot,
                        catalog,
                        PROFILE_ID,
                        member);
                observedPointTiers.add(definition.researchCost().points());
                assertTrue(DEFAULT_POINT_TIERS.contains(definition.researchCost().points()));

                if (placement.rank() == 0) {
                    assertTrue(definition.prerequisites().isEmpty(), () -> "root has prerequisites: " + member);
                    continue;
                }

                assertFalse(definition.prerequisites().isEmpty(), () -> "non-root has no prerequisite: " + member);
                for (ResourceLocation prerequisite : definition.prerequisites()) {
                    ResearchTreeGroupPlacement prerequisitePlacement = snapshot
                            .placementFor(PROFILE_ID, prerequisite)
                            .orElseThrow();
                    assertEquals(
                            placement.groupId(),
                            prerequisitePlacement.groupId(),
                            () -> member + " crosses into another default branch through " + prerequisite);
                    assertTrue(
                            prerequisitePlacement.rank() < placement.rank(),
                            () -> member + " does not rise above prerequisite " + prerequisite);
                }
            }
        }

        assertEquals(DEFAULT_POINT_TIERS, observedPointTiers);
    }

    @Test
    void unknownAddonContentRemainsIndependentAndExportable() throws Exception {
        BlueprintResearchSnapshot snapshot = packagedSnapshot();
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>(catalog(OFFICIAL_GUNS));
        ResourceLocation addon = id("example_pack:laser_rifle");
        catalog.put(addon, data(addon));

        BlueprintResearchDiagnostics.Audit audit = BlueprintResearchDiagnostics.audit(
                snapshot, catalog, PROFILE_ID);
        assertEquals(List.of(addon), audit.unassignedBlueprintIds());
        assertEquals(OFFICIAL_GUNS.size() + 1, audit.treeVisibleBlueprintCount());
        assertEquals(8, audit.rootCount());
        assertEquals(8, audit.componentCount());
        assertEquals(List.of(addon), audit.independentBlueprintIds());
        assertFalse(audit.hasStructuralProblems());

        BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                snapshot, catalog, PROFILE_ID, addon);
        assertTrue(definition.ruleId().isEmpty());
        assertEquals(JournalVisibility.SILHOUETTE, definition.visibility());
        assertTrue(definition.prerequisites().isEmpty());

        String exported = BlueprintResearchCatalogExporter.export(snapshot, catalog, PROFILE_ID);
        assertTrue(exported.contains("\"blueprint\": \"example_pack:laser_rifle\""));
        assertTrue(exported.contains("\"catalog_size\": 55"));
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
        BlueprintResearchSnapshot snapshot = BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(id("example:custom_progression"), profile),
                rules,
                groups);
        assertEquals(2, snapshot.rules().size());
        assertEquals(1, snapshot.groups().size());
        assertEquals(
                id("example:pistols"),
                snapshot.placementFor(
                        id("example:custom_progression"),
                        id("example_guns:advanced_pistol"))
                        .orElseThrow()
                        .groupId());
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
                "src/main/resources/data/taczweaponblueprints/taczweaponblueprints/research_rules/default_tree");
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
        return BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(PROFILE_ID, profile),
                rules,
                groups);
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

    private static ResourceLocation ruleId(Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        return id("taczweaponblueprints:default_tree/"
                + relative.substring(0, relative.length() - ".json".length()));
    }

    private static ResourceLocation groupId(Path root, Path path) {
        String relative = root.relativize(path).toString().replace('\\', '/');
        return id("taczweaponblueprints:"
                + relative.substring(0, relative.length() - ".json".length()));
    }

    private static Map<ResourceLocation, BlueprintData> catalog(Set<ResourceLocation> ids) {
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        ids.stream().sorted().forEach(id -> catalog.put(id, data(id)));
        return catalog;
    }

    private static BlueprintData data(ResourceLocation id) {
        return new BlueprintData(
                id.toString(),
                "item." + id.getNamespace() + "." + id.getPath(),
                "tooltip." + id.getNamespace() + "." + id.getPath(),
                id,
                null,
                "gun",
                null);
    }

    private static Set<ResourceLocation> ids(String... paths) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        Stream.of(paths).map(path -> id("tacz:" + path)).forEach(ids::add);
        return Set.copyOf(ids);
    }

    private static List<List<ResourceLocation>> ranks(String encodedRanks) {
        return Stream.of(encodedRanks.split("\\|"))
                .map(rank -> Stream.of(rank.split(","))
                        .map(path -> id("tacz:" + path))
                        .toList())
                .toList();
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.tryParse(value);
    }
}
