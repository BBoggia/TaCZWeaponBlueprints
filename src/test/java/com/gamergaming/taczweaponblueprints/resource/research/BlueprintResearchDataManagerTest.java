package com.gamergaming.taczweaponblueprints.resource.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;

class BlueprintResearchDataManagerTest {
    @Test
    void preparesStrictResearchTreeGroupsAlongsideExistingDefinitions() {
        ResourceManager resources = resourceManager(Map.of(
                new ResourceLocation("test", "taczweaponblueprints/research_profiles/profile.json"),
                resource(validProfileJson()),
                new ResourceLocation("test", "taczweaponblueprints/research_tree_groups/pistols.json"),
                resource("""
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "title": "Pistols",
                          "icon": "test:starter",
                          "order": 10,
                          "ranks": [["test:starter"]]
                        }
                        """)));

        BlueprintResearchSnapshot prepared = BlueprintResearchDataManager.INSTANCE.prepare(
                resources,
                InactiveProfiler.INSTANCE);

        assertEquals(1, prepared.groups().size());
        assertEquals(
                new ResearchTreeGroupPlacement(
                        new ResourceLocation("test", "pistols"),
                        0,
                        0),
                prepared.placementFor(
                        new ResourceLocation("test", "profile"),
                        new ResourceLocation("test", "starter"))
                        .orElseThrow());
    }

    @Test
    void failedPreparationLeavesThePublishedSnapshotUntouched() {
        BlueprintResearchDataManager.Publication before = BlueprintResearchDataManager.INSTANCE.publication();
        ResourceLocation resourceId = new ResourceLocation(
                "test",
                "taczweaponblueprints/research_profiles/invalid.json");
        ResourceManager resources = resourceManager(Map.of(
                resourceId,
                resource("""
                        {
                          "format": 1,
                          "journal_enabled": true,
                          "visibility": "silhouette",
                          "research_enabled": true,
                          "recycling_enabled": true,
                          "allow_unlearned_recycling": false,
                          "recycling_value": 1,
                          "research_cost": {"points": 8},
                          "requires_discovery": false,
                          "creative_bypasses_cost": false,
                          "unexpected": true
                        }
                        """)));

        assertThrows(
                IllegalStateException.class,
                () -> BlueprintResearchDataManager.INSTANCE.prepare(resources, InactiveProfiler.INSTANCE));
        assertSame(before, BlueprintResearchDataManager.INSTANCE.publication());
    }

    @Test
    void invalidGroupPreparationLeavesThePublishedSnapshotUntouched() {
        BlueprintResearchDataManager.Publication before = BlueprintResearchDataManager.INSTANCE.publication();
        ResourceManager resources = resourceManager(Map.of(
                new ResourceLocation("test", "taczweaponblueprints/research_profiles/profile.json"),
                resource(validProfileJson()),
                new ResourceLocation("test", "taczweaponblueprints/research_tree_groups/invalid.json"),
                resource("""
                        {
                          "format": 1,
                          "profile": "test:profile",
                          "title": "Invalid",
                          "icon": "test:starter",
                          "order": 10,
                          "ranks": [["test:starter"], ["test:starter"]]
                        }
                        """)));

        assertThrows(
                IllegalStateException.class,
                () -> BlueprintResearchDataManager.INSTANCE.prepare(resources, InactiveProfiler.INSTANCE));
        assertSame(before, BlueprintResearchDataManager.INSTANCE.publication());
    }

    @Test
    void oversizedDefinitionIsRejectedBeforeJsonMaterialization() {
        ResourceManager resources = resourceManager(Map.of(
                new ResourceLocation("test", "taczweaponblueprints/research_profiles/oversized.json"),
                resource(validProfileJson() + " ".repeat(
                        BlueprintResearchDataManager.MAX_DEFINITION_JSON_CHARACTERS + 1))));

        assertThrows(
                IllegalStateException.class,
                () -> BlueprintResearchDataManager.INSTANCE.prepare(
                        resources,
                        InactiveProfiler.INSTANCE));
    }

    @Test
    void groupAdditionRemovalAndProfileSwitchPrepareIndependentSnapshots() {
        ResourceLocation alphaProfile = new ResourceLocation(
                "test", "taczweaponblueprints/research_profiles/alpha.json");
        ResourceLocation betaProfile = new ResourceLocation(
                "test", "taczweaponblueprints/research_profiles/beta.json");
        ResourceLocation groupResource = new ResourceLocation(
                "test", "taczweaponblueprints/research_tree_groups/weapons.json");

        BlueprintResearchSnapshot alpha = BlueprintResearchDataManager.INSTANCE.prepare(
                resourceManager(Map.of(
                        alphaProfile, resource(validProfileJson()),
                        betaProfile, resource(validProfileJson()),
                        groupResource, resource(validGroupJson("test:alpha")))),
                InactiveProfiler.INSTANCE);
        BlueprintResearchSnapshot removed = BlueprintResearchDataManager.INSTANCE.prepare(
                resourceManager(Map.of(
                        alphaProfile, resource(validProfileJson()),
                        betaProfile, resource(validProfileJson()))),
                InactiveProfiler.INSTANCE);
        BlueprintResearchSnapshot beta = BlueprintResearchDataManager.INSTANCE.prepare(
                resourceManager(Map.of(
                        alphaProfile, resource(validProfileJson()),
                        betaProfile, resource(validProfileJson()),
                        groupResource, resource(validGroupJson("test:beta")))),
                InactiveProfiler.INSTANCE);

        assertEquals(1, alpha.groupsForProfile(new ResourceLocation("test", "alpha")).size());
        assertEquals(0, alpha.groupsForProfile(new ResourceLocation("test", "beta")).size());
        assertEquals(0, removed.groups().size());
        assertEquals(0, beta.groupsForProfile(new ResourceLocation("test", "alpha")).size());
        assertEquals(1, beta.groupsForProfile(new ResourceLocation("test", "beta")).size());
        assertEquals(1, alpha.groups().size());
    }

    private static Resource resource(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new Resource(TEST_PACK, () -> new ByteArrayInputStream(bytes));
    }

    private static String validProfileJson() {
        return """
                {
                  "format": 1,
                  "journal_enabled": true,
                  "visibility": "silhouette",
                  "research_enabled": true,
                  "recycling_enabled": true,
                  "allow_unlearned_recycling": false,
                  "recycling_value": 1,
                  "research_cost": {"points": 8},
                  "requires_discovery": false,
                  "creative_bypasses_cost": false
                }
                """;
    }

    private static String validGroupJson(String profileId) {
        return """
                {
                  "format": 1,
                  "profile": "%s",
                  "title": "Weapons",
                  "icon": "test:starter",
                  "order": 10,
                  "ranks": [["test:starter"]]
                }
                """.formatted(profileId);
    }

    private static ResourceManager resourceManager(Map<ResourceLocation, Resource> resources) {
        return new ResourceManager() {
            @Override
            public Set<String> getNamespaces() {
                return Set.of("test");
            }

            @Override
            public Optional<Resource> getResource(ResourceLocation id) {
                return Optional.ofNullable(resources.get(id));
            }

            @Override
            public List<Resource> getResourceStack(ResourceLocation id) {
                Resource resource = resources.get(id);
                return resource == null ? List.of() : List.of(resource);
            }

            @Override
            public Map<ResourceLocation, Resource> listResources(
                    String directory,
                    Predicate<ResourceLocation> filter) {
                return resources.entrySet().stream()
                        .filter(entry -> entry.getKey().getPath().startsWith(directory + "/"))
                        .filter(entry -> filter.test(entry.getKey()))
                        .collect(java.util.stream.Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }

            @Override
            public Map<ResourceLocation, List<Resource>> listResourceStacks(
                    String directory,
                    Predicate<ResourceLocation> filter) {
                return listResources(directory, filter).entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> List.of(entry.getValue())));
            }

            @Override
            public Stream<PackResources> listPacks() {
                return Stream.of(TEST_PACK);
            }
        };
    }

    private static final PackResources TEST_PACK = new PackResources() {
        @Override
        public IoSupplier<InputStream> getRootResource(String... path) {
            return null;
        }

        @Override
        public IoSupplier<InputStream> getResource(PackType type, ResourceLocation id) {
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, ResourceOutput output) {
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            return Set.of("test");
        }

        @Override
        public <T> T getMetadataSection(MetadataSectionSerializer<T> serializer) {
            return null;
        }

        @Override
        public String packId() {
            return "phase-3-test";
        }

        @Override
        public void close() {
        }
    };
}
