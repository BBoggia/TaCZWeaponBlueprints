package com.gamergaming.taczweaponblueprints.resource.research;

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

    private static Resource resource(String json) {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        return new Resource(TEST_PACK, () -> new ByteArrayInputStream(bytes));
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
