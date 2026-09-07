package com.gamergaming.taczweaponblueprints.client;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/** Development-only replay seam; does not register a player screen, command, or setting. */
public final class ResearchTreeSceneComparisonTool {
    private ResearchTreeSceneComparisonTool() { }

    public static void main(String[] args) throws Exception {
        Path output = Path.of("build/reports/research-tree/scenes.json");
        Path inputPath = null;
        Path svgDirectory = null;
        String selection = "compare";
        boolean stress = false;
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--output" -> output = Path.of(args[++i]);
                case "--input" -> inputPath = Path.of(args[++i]);
                case "--engine" -> selection = args[++i];
                case "--svg-directory" -> svgDirectory = Path.of(args[++i]);
                case "--stress" -> stress = true;
                default -> throw new IllegalArgumentException("unknown scene comparison argument: " + args[i]);
            }
        }
        if (!List.of("compare", "compatibility", "simple", "branches").contains(selection)) {
            throw new IllegalArgumentException("engine must be compare, compatibility, simple, or branches");
        }
        var inputs = new ArrayList<ResearchTreeBaselineInput>();
        var branchSeeds = new java.util.HashMap<String, List<ResearchTreeBranchStructure.Seed>>();
        if (inputPath != null) inputs.add(ResearchTreeBaselineInput.read(inputPath));
        else java.util.stream.Stream.concat(ResearchTreeBaselineFixtures.structural().stream(),
                ResearchTreeBaselineFixtures.routing().stream()).forEach(f -> {
            inputs.add(f.input()); branchSeeds.put(f.input().name(), ResearchTreeBranchFixtures.seeds(f));
        });
        if (stress) {
            inputs.add(ResearchTreeBaselineFixtures.maximumNodes().input());
            inputs.add(ResearchTreeBaselineFixtures.maximumEdges().input());
        }
        var report = new JsonObject();
        report.addProperty("format", 3);
        report.addProperty("selection", selection);
        report.addProperty("java", System.getProperty("java.runtime.version"));
        report.addProperty("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.addProperty("timing_scope", "scene generation plus final validation; 3 warmups, 10 samples; no native rendering");
        var cases = new JsonArray();
        for (var replay : inputs) cases.add(compare(replay, selection,
                branchSeeds.getOrDefault(replay.name(), List.of()), svgDirectory));
        report.add("cases", cases);
        Files.createDirectories(output.toAbsolutePath().getParent());
        Files.writeString(output, ResearchTreeBaselineInput.JSON.toJson(report) + "\n");
        System.out.println("Scene comparison written to " + output.toAbsolutePath());
    }

    static JsonObject compare(ResearchTreeBaselineInput replay, String selection) {
        return compare(replay, selection, List.of(), null);
    }

    private static JsonObject compare(ResearchTreeBaselineInput replay, String selection,
            List<ResearchTreeBranchStructure.Seed> seeds, Path svgDirectory) {
        var result = new JsonObject();
        result.addProperty("name", replay.name());
        result.addProperty("public_input_sha256", replay.sha256());
        var domains = new JsonArray();
        var policy = ResearchTreeScenePolicy.compatibility(replay.policy(), replay.viewportWidth());
        if (replay.projections().isEmpty()) {
            var input = ResearchTreeSceneInput.empty(Domain.WEAPONS, policy);
            var domain = new JsonObject();
            domain.addProperty("domain", "WEAPONS");
            domain.addProperty("geometry_input_sha256", digest(input.key("input-v1")));
            domain.add("simple", measure(input, new ResearchTreeSimpleSceneEngine()));
            if (selection.equals("compare") || selection.equals("branches")) {
                domain.add("branches", measure(input, new ResearchTreeBranchSceneEngine()));
            }
            domain.addProperty("compatibility", "absent typed domain: legacy engine bypassed");
            domains.add(domain);
        }
        for (var data : replay.projections()) {
            var projection = data.replay();
            var input = ResearchTreeSceneInput.from(projection, policy).withBranchSeeds(seeds);
            var domain = new JsonObject();
            domain.addProperty("domain", projection.domain().name());
            domain.addProperty("geometry_input_sha256", digest(input.key("input-v1")));
            if (selection.equals("compare") || selection.equals("compatibility")) domain.add("compatibility", measure(input,
                    new ResearchTreeCompatibilitySceneEngine(projection)));
            if (selection.equals("compare") || selection.equals("simple")) domain.add("simple", measure(input, new ResearchTreeSimpleSceneEngine()));
            if (selection.equals("compare") || selection.equals("branches")) {
                domain.add("branches", measure(input, new ResearchTreeBranchSceneEngine()));
                if (svgDirectory != null) {
                    // A validated geometry artifact is required when explicitly requested.
                    var engine = new ResearchTreeBranchSceneEngine();
                    var scene = ResearchTreeSceneValidator.validate(input, engine.revision(), engine.layout(input));
                    try {
                        String filename = replay.name().replaceAll("[^A-Za-z0-9_-]", "_") + "-" + projection.domain().name() + ".svg";
                        ResearchTreeSceneSvgWriter.write(svgDirectory.resolve(filename), replay.name(), input, scene);
                    } catch (java.io.IOException error) { throw new java.io.UncheckedIOException(error); }
                }
            }
            domains.add(domain);
        }
        result.add("domains", domains);
        return result;
    }

    private static JsonObject measure(ResearchTreeSceneInput input, ResearchTreeSceneEngine engine) {
        var result = new JsonObject();
        result.addProperty("engine_revision", engine.revision());
        try {
            ResearchTreeScene expected = null;
            var samples = new ArrayList<Long>();
            for (int i = 0; i < 13; i++) {
                long start = System.nanoTime();
                var scene = ResearchTreeSceneValidator.validate(input, engine.revision(), engine.layout(input));
                long elapsed = System.nanoTime() - start;
                if (expected != null && !expected.equals(scene)) throw new IllegalStateException("scene changed across identical runs");
                expected = scene;
                if (i >= 3) samples.add(elapsed);
            }
            result.addProperty("status", "valid");
            result.addProperty("geometry_sha256", digest(expected));
            result.addProperty("nodes", expected.nodes().size());
            result.addProperty("edges", expected.routes().size());
            result.addProperty("groups", expected.requirements().size());
            result.addProperty("regions", expected.regions().size());
            result.addProperty("occupied_ranks", expected.occupiedRanks().size());
            expected.routing().ifPresent(r -> {
                result.addProperty("grouped_connections", r.connections().size());
                result.addProperty("crossings", r.crossings().size());
                result.add("routing", ResearchTreeBaselineInput.JSON.toJsonTree(r.statistics()));
                result.add("junction_geometry", ResearchTreeBaselineInput.JSON.toJsonTree(r.junctions()));
            });
            if (!expected.regions().isEmpty()) {
                result.add("region_geometry", ResearchTreeBaselineInput.JSON.toJsonTree(expected.regions()));
                result.add("node_geometry", ResearchTreeBaselineInput.JSON.toJsonTree(expected.nodes()));
                result.add("rank_geometry", ResearchTreeBaselineInput.JSON.toJsonTree(expected.occupiedRanks()));
            }
            result.addProperty("boundary_links", expected.boundaries().stream().mapToInt(b -> b.links().size()).sum());
            result.add("extent", ResearchTreeBaselineInput.JSON.toJsonTree(expected.extent()));
            result.add("scene_and_validation", ResearchTreeBaselineTool.statistics(samples));
        } catch (RuntimeException failure) {
            result.addProperty("status", "rejected");
            result.addProperty("reason", failure.toString());
        }
        return result;
    }

    private static String digest(Object value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                    ResearchTreeBaselineInput.JSON.toJson(value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
