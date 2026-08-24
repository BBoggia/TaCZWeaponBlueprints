package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.IntStream;

import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintCatalogSelector;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class BlueprintResearchSnapshotTest {
    @Test
    void validatesProfilesReferencesTagsAndDirectEconomy() {
        BlueprintResearchRule dormant = rule(
                target(List.of(id("missing:optional_blueprint")), List.of(), false),
                0,
                Optional.empty(),
                Optional.of(2),
                Optional.empty());
        assertDoesNotThrow(() -> BlueprintResearchSnapshot.create(
                Map.of(),
                Map.of(profileId(), profile()),
                Map.of(id("test:dormant"), dormant)));

        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintResearchSnapshot.create(
                        Map.of(),
                        Map.of(),
                        Map.of(id("test:missing_profile"), dormant)));

        BlueprintResearchRule missingTag = rule(
                target(List.of(), List.of(id("test:missing")), false),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintResearchSnapshot.create(
                        Map.of(),
                        Map.of(profileId(), profile()),
                        Map.of(id("test:missing_tag"), missingTag)));

        BlueprintResearchRule unprofitable = rule(
                target(List.of(id("test:item")), List.of(), false),
                0,
                Optional.of(new BlueprintResearchCost(3, List.of())),
                Optional.of(3),
                Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintResearchSnapshot.create(
                        Map.of(),
                        Map.of(profileId(), profile()),
                        Map.of(id("test:profit"), unprofitable)));

        BlueprintResearchRule negativeProgrammaticValue = rule(
                target(List.of(id("test:item")), List.of(), false),
                0,
                Optional.empty(),
                Optional.of(-1),
                Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintResearchSnapshot.create(
                        Map.of(),
                        Map.of(profileId(), profile()),
                        Map.of(id("test:negative"), negativeProgrammaticValue)));

        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintResearchSnapshot.create(
                        Map.of(id("test:empty"), new BlueprintLootTag(1, List.of())),
                        Map.of(profileId(), profile()),
                        Map.of()));

        BlueprintCatalogSelector oversizedSelector = new BlueprintCatalogSelector(
                IntStream.rangeClosed(0, BlueprintCatalogSelector.MAX_TERMS)
                        .mapToObj(index -> "namespace" + index)
                        .toList(),
                List.of(),
                List.of(),
                List.of(),
                1.0F);
        BlueprintResearchRule programmaticSelector = rule(
                new BlueprintResearchTarget(List.of(), List.of(), Optional.of(oversizedSelector)),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintResearchSnapshot.create(
                        Map.of(),
                        Map.of(profileId(), profile()),
                        Map.of(id("test:oversized_selector"), programmaticSelector)));

        BlueprintResearchIngredient invalidProgrammaticIngredient = new BlueprintResearchIngredient(
                List.of(id("minecraft:paper")),
                Optional.of(id("forge:paper")),
                1);
        BlueprintResearchProfile invalidProgrammaticProfile = new BlueprintResearchProfile(
                1,
                true,
                JournalVisibility.SILHOUETTE,
                true,
                true,
                false,
                1,
                new BlueprintResearchCost(8, List.of(invalidProgrammaticIngredient)),
                false,
                false);
        assertThrows(
                IllegalArgumentException.class,
                () -> BlueprintResearchSnapshot.create(
                        Map.of(),
                        Map.of(profileId(), invalidProgrammaticProfile),
                        Map.of()));
    }

    @Test
    void rejectsSelfReferencesCyclesAndExcessiveDepth() {
        BlueprintResearchRule self = rule(
                target(List.of(id("test:a")), List.of(), false),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.of(List.of(id("test:a"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(Map.of(id("test:self"), self)));

        BlueprintResearchRule aToB = rule(
                target(List.of(id("test:a")), List.of(), false),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.of(List.of(id("test:b"))));
        BlueprintResearchRule bToA = rule(
                target(List.of(id("test:b")), List.of(), false),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.of(List.of(id("test:a"))));
        assertThrows(
                IllegalArgumentException.class,
                () -> snapshot(Map.of(id("test:a_rule"), aToB, id("test:b_rule"), bToA)));

        Map<ResourceLocation, BlueprintResearchRule> deep = new LinkedHashMap<>();
        for (int index = 0; index <= BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH; index++) {
            ResourceLocation current = id("test:depth_" + index);
            Optional<List<ResourceLocation>> next = index == BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH
                    ? Optional.empty()
                    : Optional.of(List.of(id("test:depth_" + (index + 1))));
            deep.put(id("test:rule_" + index), rule(
                    target(List.of(current), List.of(), false),
                    0,
                    Optional.empty(),
                    Optional.empty(),
                    next));
        }
        assertThrows(IllegalArgumentException.class, () -> snapshot(deep));
    }

    @Test
    void shadowedPrerequisiteRulesDoNotCreateFalseCycles() {
        BlueprintResearchRule selected = rule(
                target(List.of(id("test:a")), List.of(), false),
                100,
                Optional.empty(),
                Optional.empty(),
                Optional.empty());
        BlueprintResearchRule shadowed = rule(
                target(List.of(id("test:a")), List.of(), false),
                0,
                Optional.empty(),
                Optional.empty(),
                Optional.of(List.of(id("test:a"))));

        assertDoesNotThrow(() -> snapshot(Map.of(
                id("test:selected"), selected,
                id("test:shadowed"), shadowed)));
    }

    @Test
    void packagedDuplicateRecoveryProfileIsStrictAndEconomicallySafe() throws Exception {
        String path = "data/taczweaponblueprints/taczweaponblueprints/research_profiles/duplicate_recovery.json";
        try (var stream = getClass().getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing packaged profile " + path);
            }
            BlueprintResearchProfile packaged = BlueprintResearchProfile.CODEC.parse(
                            JsonOps.INSTANCE,
                            JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
                    .result()
                    .orElseThrow();
            BlueprintResearchSnapshot loaded = BlueprintResearchSnapshot.create(
                    Map.of(),
                    Map.of(BlueprintResearchDataManager.DEFAULT_PROFILE, packaged),
                    Map.of());

            assertEquals(1, loaded.profiles().size());
            assertEquals(JournalVisibility.SILHOUETTE, packaged.visibility());
            assertEquals(1, packaged.recyclingValue());
            assertEquals(8, packaged.researchCost().points());
        }
    }

    @Test
    void derivesStableDefinitionIdsFromDatapackPaths() {
        assertEquals(
                id("example:hardcore/nether"),
                BlueprintResearchDataManager.definitionId(
                        id("example:taczweaponblueprints/research_rules/hardcore/nether.json"),
                        BlueprintResearchDataManager.RULE_DIRECTORY));
    }

    private static BlueprintResearchSnapshot snapshot(Map<ResourceLocation, BlueprintResearchRule> rules) {
        return BlueprintResearchSnapshot.create(Map.of(), Map.of(profileId(), profile()), rules);
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
            BlueprintResearchTarget target,
            int priority,
            Optional<BlueprintResearchCost> cost,
            Optional<Integer> recyclingValue,
            Optional<List<ResourceLocation>> prerequisites) {
        return new BlueprintResearchRule(
                1,
                profileId(),
                priority,
                target,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                recyclingValue,
                cost,
                Optional.empty(),
                prerequisites,
                Optional.empty());
    }

    private static BlueprintResearchTarget target(
            List<ResourceLocation> blueprints,
            List<ResourceLocation> tags,
            boolean selector) {
        return new BlueprintResearchTarget(blueprints, tags, Optional.empty());
    }

    private static ResourceLocation profileId() {
        return id("test:profile");
    }

    private static ResourceLocation id(String value) {
        ResourceLocation id = ResourceLocation.tryParse(value);
        if (id == null) {
            throw new IllegalArgumentException(value);
        }
        return id;
    }
}
