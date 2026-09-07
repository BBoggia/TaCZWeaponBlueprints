package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Repeatable CPU baseline. Deliberately separate from native screen/GPU measurements. */
public final class ResearchTreeBaselineTool {
    private static volatile long consumed;
    private ResearchTreeBaselineTool() { }

    public static void main(String[] args) throws Exception {
        Path output = null;
        Path input = null;
        for (int i = 0; i < args.length; i += 2) {
            if (i + 1 >= args.length) throw new IllegalArgumentException("expected --input/--output path");
            switch (args[i]) {
                case "--input" -> input = Path.of(args[i + 1]);
                case "--output" -> output = Path.of(args[i + 1]);
                default -> throw new IllegalArgumentException("unknown argument: " + args[i]);
            }
        }
        if (output == null) throw new IllegalArgumentException("--output is required");
        List<ResearchTreeBaselineInput> inputs = input == null
                ? ResearchTreeBaselineFixtures.structural().stream().map(ResearchTreeBaselineFixtures.Fixture::input).toList()
                : List.of(ResearchTreeBaselineInput.read(input));
        var report = environment();
        report.addProperty("measurement", "CPU layout, combined edge routing/index construction, node index, camera/query replay; no rendering or GPU");
        report.addProperty("warmup_iterations", 5);
        report.addProperty("measured_iterations", 20);
        var cases = new JsonArray();
        for (var fixture : inputs) {
            cases.add(measure(fixture, 5, 20));
            System.out.println("Baseline measured: " + fixture.name());
        }
        report.add("inputs", cases);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, ResearchTreeBaselineInput.JSON.toJson(report) + "\n");
        System.out.println("Baseline report: " + output.toAbsolutePath());
    }

    static JsonObject environment() {
        var result = new JsonObject();
        result.addProperty("recorded_at", Instant.now().toString());
        result.addProperty("java_version", System.getProperty("java.version"));
        result.addProperty("java_vendor", System.getProperty("java.vendor"));
        result.addProperty("os", System.getProperty("os.name"));
        result.addProperty("os_version", System.getProperty("os.version"));
        result.addProperty("architecture", System.getProperty("os.arch"));
        result.addProperty("available_processors", Runtime.getRuntime().availableProcessors());
        result.addProperty("max_heap_bytes", Runtime.getRuntime().maxMemory());
        return result;
    }

    static JsonObject measure(ResearchTreeBaselineInput input, int warmup, int iterations) {
        if (warmup < 0 || iterations < 1) throw new IllegalArgumentException("invalid measurement repetitions");
        var result = new JsonObject();
        result.addProperty("name", input.name());
        result.addProperty("input_sha256", input.sha256());
        result.addProperty("viewport_width_gui", input.viewportWidth());
        result.add("policy", ResearchTreeBaselineInput.JSON.toJsonTree(input.policy()));
        var domains = new JsonArray();
        for (var data : input.projections()) {
            var projection = data.replay();
            var domain = summary(projection);
            var layoutTimes = new ArrayList<Long>();
            var edgeTimes = new ArrayList<Long>();
            var nodeTimes = new ArrayList<Long>();
            var queryTimes = new ArrayList<Long>();
            ResearchTechTreeLayout reference = null;
            List<ResearchTreeEdgeIndex.PositionedEdge> referenceEdges = null;
            for (int iteration = -warmup - 1; iteration < iterations; iteration++) {
                long start = System.nanoTime();
                var layout = ResearchTechTreeLayoutEngine.layout(projection, input.policy(), input.viewportWidth());
                long placed = System.nanoTime();
                var edges = ResearchTreeEdgeIndex.create(projection.graph(), layout.graphLayout(),
                        ResearchTreeEdgeIndex.RoutingProfile.UNIFIED_OVERVIEW);
                long routed = System.nanoTime();
                var nodes = ResearchTreeNodeIndex.create(layout.graphLayout());
                long indexed = System.nanoTime();
                queryReplay(input.viewportWidth(), layout.graphLayout(), nodes, edges);
                long queried = System.nanoTime();
                var allEdges = edges.visible(-ResearchTreeLayout.MAX_DIMENSION, -ResearchTreeLayout.MAX_DIMENSION,
                        ResearchTreeLayout.MAX_DIMENSION, ResearchTreeLayout.MAX_DIMENSION);
                if (reference == null) {
                    reference = layout;
                    referenceEdges = allEdges;
                    var first = new JsonObject();
                    first.addProperty("layout_ms", millis(placed - start));
                    first.addProperty("routing_and_edge_index_ms", millis(routed - placed));
                    first.addProperty("node_index_ms", millis(indexed - routed));
                    domain.add("first_iteration_in_process", first);
                    domain.addProperty("canvas_width", layout.graphLayout().width());
                    domain.addProperty("canvas_height", layout.graphLayout().height());
                    domain.addProperty("visual_rows", layout.diagnostics().visualRowCount());
                    domain.add("node_positions", ResearchTreeBaselineInput.JSON.toJsonTree(layout.graphLayout().nodes()));
                    domain.add("edge_routes", ResearchTreeBaselineInput.JSON.toJsonTree(allEdges));
                    domain.add("group_junctions", ResearchTreeBaselineInput.JSON.toJsonTree(edges.requirementJunctions()));
                } else if (!reference.equals(layout) || !referenceEdges.equals(allEdges)) {
                    throw new IllegalStateException("non-deterministic baseline geometry: " + input.name());
                }
                if (iteration >= 0) {
                    layoutTimes.add(placed - start);
                    edgeTimes.add(routed - placed);
                    nodeTimes.add(indexed - routed);
                    queryTimes.add(queried - indexed);
                }
            }
            domain.add("layout", statistics(layoutTimes));
            domain.add("routing_and_edge_index", statistics(edgeTimes));
            domain.add("node_index", statistics(nodeTimes));
            domain.add("camera_and_queries_120_steps", statistics(queryTimes));
            domains.add(domain);
        }
        result.add("domains", domains);
        return result;
    }

    static JsonObject summary(ResearchTechTreeProjection projection) {
        var result = new JsonObject();
        var graph = projection.graph();
        result.addProperty("domain", projection.domain().name());
        result.addProperty("nodes", graph.nodes().size());
        result.addProperty("edges", graph.edges().size());
        result.addProperty("requirement_groups", graph.requirementGroups().size());
        result.addProperty("multi_alternative_groups", graph.requirementGroups().stream()
                .filter(g -> g.visibleAlternativeIds().size() + g.hiddenAlternativeCount() + g.externalAlternativeCount() > 1).count());
        result.addProperty("hidden_alternatives", graph.requirementGroups().stream().mapToInt(g -> g.hiddenAlternativeCount()).sum());
        result.addProperty("external_alternatives", graph.requirementGroups().stream().mapToInt(g -> g.externalAlternativeCount()).sum());
        result.addProperty("boundary_links", projection.boundaryLinks().size());
        result.addProperty("occupied_ranks", projection.placements().values().stream().map(p -> p.rank()).distinct().count());
        result.addProperty("max_in_degree", graph.nodes().stream().mapToInt(n -> graph.prerequisitesOf(n.blueprintId()).size()).max().orElse(0));
        result.addProperty("automatic_members", projection.placements().values().stream().filter(p -> p.automaticBranch().isPresent()).count());
        result.addProperty("automatic_families", projection.placements().values().stream().flatMap(p -> p.automaticBranch().stream()).map(p -> p.branchIndex()).distinct().count());
        return result;
    }

    static JsonObject statistics(List<Long> nanos) {
        var result = new JsonObject();
        result.addProperty("samples", nanos.size());
        if (nanos.isEmpty()) return result;
        var sorted = nanos.stream().sorted(Comparator.naturalOrder()).toList();
        result.addProperty("median_ms", (sorted.get((sorted.size() - 1) / 2) / 2.0
                + sorted.get(sorted.size() / 2) / 2.0) / 1_000_000.0);
        result.addProperty("p95_ms", millis(sorted.get((int) Math.ceil(sorted.size() * 0.95) - 1)));
        result.addProperty("max_ms", millis(sorted.get(sorted.size() - 1)));
        return result;
    }

    private static void queryReplay(int width, ResearchTreeLayout layout,
            ResearchTreeNodeIndex nodes, ResearchTreeEdgeIndex edges) {
        var camera = new ResearchTreeViewport();
        camera.configure(width, 480, layout.width(), layout.height());
        camera.fit();
        long count = 0;
        for (int step = 0; step < 120; step++) {
            if (step % 20 == 0) camera.zoomAt(step < 60 ? 1 : -1, width / 2.0, 240);
            camera.panByScreenDelta(Math.sin(step * 0.15) * 12, Math.cos(step * 0.15) * 8);
            double x = camera.canvasX(0), y = camera.canvasY(0);
            count += nodes.visible(x, y, camera.canvasX(width), camera.canvasY(480)).size();
            count += edges.visible(x, y, camera.canvasX(width), camera.canvasY(480)).size();
            count += edges.visibleRequirementJunctions(x, y, camera.canvasX(width), camera.canvasY(480)).size();
            count += nodes.at(camera.canvasX(width / 2.0), camera.canvasY(240), 4).isPresent() ? 1 : 0;
        }
        consumed = count;
    }

    private static double millis(long nanos) { return nanos / 1_000_000.0; }
}
