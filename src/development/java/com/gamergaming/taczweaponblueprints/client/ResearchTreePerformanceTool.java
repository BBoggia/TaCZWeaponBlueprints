package com.gamergaming.taczweaponblueprints.client;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.function.Supplier;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.research.tree.*;

/** Offline production-metric profiling. No timing thresholds are asserted by unit tests. */
public final class ResearchTreePerformanceTool {
    private static volatile Object consumed;
    private static final com.sun.management.ThreadMXBean ALLOCATIONS =
            (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
    private ResearchTreePerformanceTool() { }
    static ResearchTreeScenePolicy policy() {
        return new ResearchTreeScenePolicy(new ResearchTechTreeLayoutPolicy(20,18,8,20,4,1000,6,6),1024,
                new ResearchTreeScenePolicy.NodeMetrics(96,44,3),"compact-tile-v1-font-9",
                new ResearchTreeScenePolicy.RegionMetrics(44,12,16,80,16,10,2));
    }
    public static void main(String[] args) throws Exception {
        Path output = Path.of(args[0]);
        var fixtures = new ArrayList<ResearchTreeBaselineInput>();
        fixtures.add(ResearchTreeBaselineInput.read(Path.of("src/test/resources/research-tree-baseline/live-weapons-287.json")));
        ResearchTreeBaselineFixtures.structural().stream().filter(f -> f.input().name().equals("connected_512")
                || f.input().name().contains("dense")).forEach(f -> fixtures.add(f.input()));
        fixtures.add(ResearchTreeBaselineFixtures.maximumNodes().input());
        fixtures.add(ResearchTreeBaselineFixtures.maximumEdges().input());
        var report = ResearchTreeBaselineTool.environment();
        report.addProperty("scope", "CPU and current-thread allocation; full scene includes routing and validation; isolated routing rebuild uses fixed measured geometry; no GPU/native rendering");
        var cases = new JsonArray();
        for (var replay : fixtures) for (var data : replay.projections()) {
            var result = new JsonObject(); result.addProperty("name",replay.name());
            result.addProperty("input_sha256",replay.sha256());
            var projection = data.replay(); var input = ResearchTreeSceneInput.from(projection,policy());
            result.addProperty("nodes",projection.graph().nodes().size()); result.addProperty("edges",projection.graph().edges().size());
            result.addProperty("groups",projection.graph().requirementGroups().size());
            if (projection.graph().nodes().size() < 1024) {
                var publication = publication(data);
                result.add("legacy_publication_preparation", measure(() -> {
                    var cache = new ResearchTreeProjectionCache(); cache.update(publication); return cache;
                }, 12));
                result.add("scene_publication_preparation", measure(() -> {
                    var cache = new ResearchTreeProjectionCache(); cache.updateForScene(publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW); return cache;
                }, 12));
            }
            int repetitions = projection.graph().edges().size()>10000 ? 3 : 12;
            var fullPublication = publication(data);
            result.add("scene_cache_including_unavailable",measure(() -> {
                var cache=new ResearchTreeSceneCache(p -> new ResearchTreeBranchSceneEngine(),new ResearchTreeSimpleSceneEngine());
                cache.bind(fullPublication);return cache.scene(fullPublication,projection.domain(),policy());
            },repetitions));
            result.add("scene", measure(() -> ResearchTreeSceneValidator.validate(input, ResearchTreeBranchSceneEngine.REVISION,
                    new ResearchTreeBranchSceneEngine().layout(input)),repetitions));
            try {
                var scene = new ResearchTreeBranchSceneEngine().layout(input);
                result.addProperty("regions",scene.regions().size());
                var routing = scene.routing().orElseThrow();
                result.addProperty("crossings",routing.crossings().size());
                result.add("routing_statistics",ResearchTreeBaselineInput.JSON.toJsonTree(routing.statistics()));
                result.add("routing_fixed_geometry",measure(() -> ResearchTreeSceneRouter.route(input,new ResearchTreeRoutingPlan(input),
                        scene.nodes(),routing.junctions(),scene.boundaries(),scene.regions(),routing.statistics().spacingRetries(),new ResearchTreeSceneRouter.Budget()),repetitions));
                result.add("route_index",measure(() -> new ResearchTreeRoutingIndex(routing),repetitions));
                result.add("node_index_navigation",measure(() -> new ResearchTreeSceneView(scene),repetitions));
                var routes = new ResearchTreeRoutingIndex(routing); var nodes = new ResearchTreeSceneView(scene);
                result.add("camera_200_queries",measure(() -> {
                    long count=0;
                    for(int i=0;i<200;i++) {
                        double fraction=i/200.0;
                        var viewport = new ResearchTreeScene.Bounds(scene.extent().x()+fraction*scene.extent().width(),
                                scene.extent().y()+fraction*scene.extent().height(),1024,600);
                        count+=routes.query(viewport).size();count+=nodes.query(viewport.x(),viewport.y(),viewport.right(),viewport.bottom()).size();
                    }
                    return count;
                },repetitions));
                consumed=null; collect(); long before=used();
                var retained=new ResearchTreeBranchSceneEngine().layout(input);
                consumed=List.of(retained,new ResearchTreeRoutingIndex(retained.routing().orElseThrow()),new ResearchTreeSceneView(retained));
                collect();result.addProperty("observed_retained_heap_delta_bytes",used()-before);
            } catch(RuntimeException rejected) { result.addProperty("unavailable",rejected.toString()); }
            cases.add(result);System.out.println("Profiled "+replay.name());
            report.add("cases",cases);Files.createDirectories(output.toAbsolutePath().getParent());
            Files.writeString(output,ResearchTreeBaselineInput.JSON.toJson(report)+"\n");
        }
    }
    static ResearchTreePublication publication(ResearchTreeBaselineInput.ProjectionData data) {
        var projection = data.replay();
        return new ResearchTreePublication(projection.graph(), ResearchTreePresentation.EMPTY,
                new ResearchTechTreePresentation(Optional.of(new net.minecraft.resources.ResourceLocation("replay:tree")),
                        "Replay", Optional.empty(), Optional.empty(), List.of(), data.bands(), data.maxNodesPerLayer(), List.of(data.presentation())));
    }
    static JsonObject measure(Supplier<Object> work,int repetitions) {
        var result=new JsonObject();var times=new ArrayList<Long>();var bytes=new ArrayList<Long>();
        String failure=null;
        for(int i=-4;i<repetitions;i++) {
            long allocation=ALLOCATIONS.getThreadAllocatedBytes(Thread.currentThread().getId()),start=System.nanoTime();
            try {
                consumed=work.get();
                if (consumed instanceof ResearchTreeSceneCache.Result sceneResult
                        && sceneResult.status()==ResearchTreeSceneCache.Status.UNAVAILABLE)
                    failure=sceneResult.diagnostics().toString();
            } catch(RuntimeException error) { failure=error.toString(); }
            long elapsed=System.nanoTime()-start, allocated=ALLOCATIONS.getThreadAllocatedBytes(Thread.currentThread().getId())-allocation;
            if(i==-4){result.addProperty("cold_ns",elapsed);result.addProperty("cold_allocated_bytes",allocated);}
            if(i>=0){times.add(elapsed);bytes.add(allocated);}
        }
        result.addProperty("status",failure==null?"valid":"unavailable");if(failure!=null)result.addProperty("reason",failure);
        result.add("cpu",ResearchTreeBaselineTool.statistics(times));bytes.sort(Long::compare);
        result.addProperty("median_allocated_bytes",bytes.get(bytes.size()/2));return result;
    }
    private static long used(){return ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed();}
    private static void collect() throws InterruptedException {System.gc();Thread.sleep(40);System.gc();Thread.sleep(40);}
}
