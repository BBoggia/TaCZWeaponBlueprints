package com.gamergaming.taczweaponblueprints.villager.trade;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.*;
import net.minecraft.server.packs.metadata.MetadataSectionSerializer;
import net.minecraft.server.packs.resources.*;
import net.minecraft.util.profiling.InactiveProfiler;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeFormatTest.*;
class TradeDataManagerTest {
    @Test void reloadIsAtomicRetainsDefinitionsButNeverRetainsOldContentPermissionsAndCanRecover() throws Exception {
        var manager=new TradeDataManager();
        var key=id("test:"+TradeDataManager.DIRECTORY+"/offer.json");
        var valid=resourceManager(Map.of(key,resource(JSON)));
        manager.apply(manager.prepare(valid,InactiveProfiler.INSTANCE),valid,InactiveProfiler.INSTANCE);
        var before=manager.publication();assertEquals(1,before.revision());assertTrue(before.failure().isEmpty());
        var mixed=resourceManager(Map.of(key,resource(JSON.replace("\"count\":4","\"count\":5")),
                id("test:"+TradeDataManager.DIRECTORY+"/bad.json"),resource("{bad")));
        manager.apply(manager.prepare(mixed,InactiveProfiler.INSTANCE),mixed,InactiveProfiler.INSTANCE);
        assertSame(before.snapshot(),manager.publication().snapshot());assertEquals(1,manager.publication().revision());assertTrue(manager.publication().failure().isPresent());
        var content=new TradeResolverTest.Content();content.blocked.add(id("test:round"));
        assertFalse(TradeResolver.resolve(manager.publication().snapshot(),TradeResolverTest.defaults(),id("test:currency"),content).retains(id("test:slot"),id("test:offer"),id("test:round")));
        manager.apply(manager.prepare(valid,InactiveProfiler.INSTANCE),valid,InactiveProfiler.INSTANCE);
        assertEquals(2,manager.publication().revision());assertTrue(manager.publication().failure().isEmpty());
        content.blocked.clear();assertTrue(TradeResolver.resolve(manager.publication().snapshot(),TradeResolverTest.defaults(),id("test:currency"),content).retains(id("test:slot"),id("test:offer"),id("test:round")));
        manager.clear();assertEquals(0,manager.publication().revision());assertTrue(manager.publication().snapshot().definitions().isEmpty());
    }
    @Test void documentAndAggregateTextLimitsRejectBeforePartialPublication() {
        var resources=new HashMap<ResourceLocation,Resource>();
        for(int i=0;i<256;i++) resources.put(id("test:"+TradeDataManager.DIRECTORY+"/d"+i+".json"),resource(JSON.replace("test:slot","test:s"+i)));
        var manager=new TradeDataManager();var many=resourceManager(resources);
        // This also exceeds the per-profession slot budget.
        assertTrue(manager.prepare(many,InactiveProfiler.INSTANCE).failure().isPresent());
        resources.put(id("test:"+TradeDataManager.DIRECTORY+"/overflow.json"),resource(JSON));
        assertTrue(manager.prepare(resourceManager(resources),InactiveProfiler.INSTANCE).failure().orElseThrow().contains("too many trade documents"));
        resources.clear();
        for(int i=0;i<65;i++){String s=JSON.replace("test:slot","test:s"+i);resources.put(id("test:"+TradeDataManager.DIRECTORY+"/d"+i+".json"),resource(s+" ".repeat(TradeLimits.JSON_CHARACTERS-s.length())));}
        assertTrue(manager.prepare(resourceManager(resources),InactiveProfiler.INSTANCE).failure().orElseThrow().contains("aggregate trade JSON limit"));
    }
    @Test void abortedReloadRestoresCommittedLastValidDataAndItsRejectionDiagnostic() throws Exception {
        var manager=new TradeDataManager();
        var key=id("test:"+TradeDataManager.DIRECTORY+"/offer.json");
        var valid=resourceManager(Map.of(key,resource(JSON)));
        manager.apply(manager.prepare(valid,InactiveProfiler.INSTANCE),valid,InactiveProfiler.INSTANCE);
        var invalid=resourceManager(Map.of(key,resource("{bad")));
        manager.apply(manager.prepare(invalid,InactiveProfiler.INSTANCE),invalid,InactiveProfiler.INSTANCE);
        var committed=manager.publication();
        manager.publish(new TradeDataManager.Prepared(Optional.of(TradeSnapshot.EMPTY),Optional.empty()));
        long partialRevision=manager.publication().revision();
        manager.restoreAfterAbortedReload(committed);
        assertSame(committed.snapshot(),manager.publication().snapshot());
        assertEquals(committed.failure(),manager.publication().failure());
        assertTrue(manager.publication().revision()>partialRevision);
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
        @Override public net.minecraft.server.packs.PackLocationInfo location() {
            return new net.minecraft.server.packs.PackLocationInfo(packId(), net.minecraft.network.chat.Component.literal(packId()), net.minecraft.server.packs.repository.PackSource.DEFAULT, java.util.Optional.empty());
        }
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

}
