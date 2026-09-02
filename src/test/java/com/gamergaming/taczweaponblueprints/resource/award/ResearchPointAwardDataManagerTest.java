package com.gamergaming.taczweaponblueprints.resource.award;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.IoSupplier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.InactiveProfiler;

class ResearchPointAwardDataManagerTest {
    @Test
    void successfulReloadPublishesOneIndependentImmutableRevision() {
        ResearchPointAwardDataManager manager = new ResearchPointAwardDataManager();
        ResearchPointAwardDataManager.Prepared prepared = manager.prepare(
                resourceManager(Map.of(resourceId("discovery"), resource(validDefinition()))),
                InactiveProfiler.INSTANCE);

        assertTrue(prepared.successful());
        manager.apply(prepared, resourceManager(Map.of()), InactiveProfiler.INSTANCE);

        assertEquals(1L, manager.revision());
        assertEquals(Set.of(id("test:discovery")), manager.snapshot().definitions().keySet());
        assertTrue(manager.lastFailure().isEmpty());
        assertEquals(List.of(id("test:discovery")), manager.resolve(
                ResearchPointAwardContext.simple(
                        ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED,
                        id("test:profile"),
                        id("test:item"))).awards().stream()
                .map(value -> value.binding().definitionId()).toList());
    }

    @Test
    void rejectedReloadPreservesTheExactLastKnownGoodPublication() {
        ResearchPointAwardDataManager manager = new ResearchPointAwardDataManager();
        ResearchPointAwardDataManager.Prepared valid = manager.prepare(
                resourceManager(Map.of(resourceId("discovery"), resource(validDefinition()))),
                InactiveProfiler.INSTANCE);
        manager.apply(valid, resourceManager(Map.of()), InactiveProfiler.INSTANCE);
        ResearchPointAwardDataManager.Publication before = manager.publication();

        ResearchPointAwardDataManager.Prepared invalid = manager.prepare(
                resourceManager(Map.of(resourceId("invalid"), resource(
                        validDefinition().replace(
                                "\"format\": 1,", "\"format\": 1, \"unexpected\": true,")))),
                InactiveProfiler.INSTANCE);

        assertFalse(invalid.successful());
        manager.apply(invalid, resourceManager(Map.of()), InactiveProfiler.INSTANCE);
        assertSame(before, manager.publication());
        assertEquals(1L, manager.revision());
        assertEquals(Set.of(id("test:discovery")), manager.snapshot().definitions().keySet());
        assertTrue(manager.lastFailure().isPresent());

        ResearchPointAwardDataManager.Prepared empty = manager.prepare(
                resourceManager(Map.of()), InactiveProfiler.INSTANCE);
        manager.apply(empty, resourceManager(Map.of()), InactiveProfiler.INSTANCE);
        assertEquals(2L, manager.revision());
        assertTrue(manager.snapshot().definitions().isEmpty());
        assertTrue(manager.lastFailure().isEmpty());
    }

    @Test
    void conflictingBudgetsBecomePreparedFailureWithoutCrossManagerMutation() {
        ResearchPointAwardDataManager manager = new ResearchPointAwardDataManager();
        String first = withBudget(validDefinition(), 4);
        String second = withBudget(validDefinition().replace("test:group", "test:other_group"), 5);

        ResearchPointAwardDataManager.Prepared prepared = manager.prepare(
                resourceManager(Map.of(
                        resourceId("first"), resource(first),
                        resourceId("second"), resource(second))),
                InactiveProfiler.INSTANCE);

        assertFalse(prepared.successful());
        assertEquals(0L, manager.revision());
        assertTrue(manager.snapshot().definitions().isEmpty());
        assertTrue(manager.lastFailure().isEmpty());
        manager.apply(prepared, resourceManager(Map.of()), InactiveProfiler.INSTANCE);
        assertTrue(manager.lastFailure().orElseThrow().message().contains("conflicting"));
    }

    @Test
    void boundedReaderAndDefinitionPathRejectOversizedOrForeignResources() throws IOException {
        assertThrows(RuntimeException.class, () -> ResearchPointAwardDataManager.parseBoundedJson(
                new StringReader(" ".repeat(
                        PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_JSON_CHARACTERS + 1))));
        assertEquals(id("example:combat/zombie"), ResearchPointAwardDataManager.definitionId(
                id("example:taczweaponblueprints/research_point_awards/combat/zombie.json")));
        assertThrows(RuntimeException.class, () -> ResearchPointAwardDataManager.definitionId(
                id("example:taczweaponblueprints/research_rules/not_an_award.json")));
    }

    private static String validDefinition() {
        return """
                {
                  "format": 1,
                  "award_group": "test:group",
                  "trigger": {
                    "type": "blueprint_discovered",
                    "target": {"ids": ["test:item"]}
                  },
                  "reward": {"points": 2, "overflow": "clamp"},
                  "repeat": {"type": "unlimited"},
                  "presentation": {"visibility": "hidden"}
                }
                """;
    }

    private static String withBudget(String definition, int maximumAwards) {
        return definition.replace(
                "\"presentation\": {",
                "\"budget\": {\"id\": \"test:shared\", \"max_awards\": "
                        + maximumAwards
                        + ", \"max_points\": 10, \"window_ticks\": 200},\n"
                        + "  \"presentation\": {");
    }

    private static ResourceLocation resourceId(String path) {
        return id("test:taczweaponblueprints/research_point_awards/" + path + ".json");
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
            return "research-point-award-test";
        }

        @Override
        public void close() {
        }
    };

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
