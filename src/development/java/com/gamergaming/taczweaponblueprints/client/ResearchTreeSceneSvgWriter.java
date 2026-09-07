package com.gamergaming.taczweaponblueprints.client;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;

/** Development geometry preview, not a mockup of Minecraft art, text, controls, or interaction. */
final class ResearchTreeSceneSvgWriter {
    private static final String[] COLORS = {"#63d8dc", "#f5c86b", "#a4abff", "#f09cb1", "#86d7a0", "#dca9ef", "#83bcee"};
    private ResearchTreeSceneSvgWriter() { }

    static void write(Path path, String title, ResearchTreeSceneInput input, ResearchTreeScene scene) throws IOException {
        ResearchTreeScene.Bounds frame = scene.nodes().isEmpty() ? new ResearchTreeScene.Bounds(0, 0, 100, 100) : scene.extent();
        frame = frame.inflate(20);
        // A square viewport also avoids Quick Look cropping portrait SVG thumbnails on macOS.
        double size = Math.max(frame.width(), frame.height());
        frame = new ResearchTreeScene.Bounds(frame.centerX() - size / 2,
                frame.y() + frame.height() / 2 - size / 2, size, size);
        var svg = new StringBuilder("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"1400\" height=\"1400\" viewBox=\"")
                .append(frame.x()).append(' ').append(frame.y()).append(' ').append(frame.width()).append(' ')
                .append(frame.height()).append("\"><title>").append(escape(title)).append("</title>")
                .append("<rect x=\"").append(frame.x()).append("\" y=\"").append(frame.y())
                .append("\" width=\"").append(frame.width()).append("\" height=\"").append(frame.height())
                .append("\" fill=\"#101923\"/>");
        var owners = new HashMap<net.minecraft.resources.ResourceLocation, Integer>();
        var headers = new ResearchTreeRoutingPlan(input).headers(input, scene.regions(),
                scene.routing().orElseThrow().statistics().spacingRetries());
        for (int i = 0; i < scene.regions().size(); i++) {
            var region = scene.regions().get(i);
            int color = i;
            region.seed().directMembers().forEach(id -> owners.put(id, color));
            rect(svg, region.bounds(), COLORS[i % COLORS.length], "none", 1.5);
            if (!headers.isEmpty()) {
                var header = headers.get(i);
                double font = Math.min(8, header.height());
                int count = Math.max(1, (int) (header.width() / (font * .65)));
                String label = region.seed().id();
                if (label.length() > count) label = label.substring(0, Math.max(0, count - 1)) + "…";
                svg.append("<text x=\"").append(header.x()).append("\" y=\"")
                        .append(header.y() + font).append("\" font-family=\"monospace\" font-size=\"").append(font)
                        .append("\" fill=\"").append(COLORS[i % COLORS.length]).append("\"><title>")
                        .append(escape(region.seed().id())).append("</title>").append(escape(label)).append("</text>");
            }
        }
        scene.routing().ifPresent(routing -> {
            record SegmentKey(int connection, int segment) { }
            var gaps = new HashMap<SegmentKey, java.util.List<Double>>();
            for (var crossing : routing.crossings()) gaps.computeIfAbsent(
                    new SegmentKey(crossing.horizontalConnection(), crossing.horizontalSegment()), ignored -> new java.util.ArrayList<>())
                    .add(crossing.point().x());
            for (var segment : new ResearchTreeRoutingIndex(routing).segments()) {
                var owner = routing.connections().get(segment.connection()).owner();
                int color = owner.group().map(g -> owners.getOrDefault(g.dependentId(), 0)).orElse(0);
                String stroke = COLORS[color % COLORS.length];
                var marks = gaps.getOrDefault(new SegmentKey(segment.connection(), segment.ordinal()), java.util.List.of());
                if (!segment.horizontal() || marks.isEmpty()) line(svg, segment.from().x(), segment.from().y(), segment.to().x(), segment.to().y(), stroke);
                else {
                    double start = Math.min(segment.from().x(), segment.to().x());
                    double end = Math.max(segment.from().x(), segment.to().x());
                    for (double x : marks.stream().sorted().toList()) {
                        if (x - 1 > start) line(svg, start, segment.from().y(), Math.min(end, x - 1), segment.from().y(), stroke);
                        start = Math.max(start, x + 1);
                    }
                    if (start < end) line(svg, start, segment.from().y(), end, segment.from().y(), stroke);
                }
            }
            for (var j : routing.junctions()) {
                svg.append("<g><title>").append(escape(j.group().id() + ": one of " + j.group().alternatives()
                        + "; undisclosed=" + j.group().hiddenCount() + "; external=" + j.group().externalCount())).append("</title>");
                rect(svg, j.bounds(), "#ecda99", "#182330", .7);
                svg.append("<text text-anchor=\"middle\" x=\"").append(j.bounds().centerX()).append("\" y=\"")
                        .append(j.bounds().y() + 5.5).append("\" font-family=\"monospace\" font-size=\"4\" fill=\"#ecda99\">")
                        .append(j.group().alternatives().size() + j.group().hiddenCount() + j.group().externalCount() > 1 ? "OR" : "1")
                        .append("</text></g>");
            }
        });
        for (var b : scene.boundaries()) {
            svg.append("<g><title>").append(escape(b.links().toString())).append("</title>");
            rect(svg, b.bounds(), "#f09cb1", "#182330", 1);
            svg.append("<text text-anchor=\"middle\" x=\"").append(b.bounds().centerX()).append("\" y=\"")
                    .append(b.bounds().y() + 8).append("\" font-family=\"monospace\" font-size=\"7\" fill=\"#f09cb1\">")
                    .append(b.links().get(0).remoteDomain().name().charAt(0)).append("</text></g>");
        }
        int ordinal = 0;
        for (var node : scene.nodes()) {
            String color = COLORS[owners.getOrDefault(node.id(), 0) % COLORS.length];
            svg.append("<g><title>").append(escape(node.id().toString())).append("</title>");
            rect(svg, node.body(), color, "#23303e", 1.5);
            svg.append("<text text-anchor=\"middle\" x=\"").append(node.body().centerX()).append("\" y=\"")
                    .append(node.body().y() + node.body().height() / 2 + 3)
                    .append("\" font-family=\"monospace\" font-size=\"7\" fill=\"").append(color).append("\">")
                    .append(++ordinal).append("</text></g>");
        }
        svg.append("</svg>\n");
        Files.createDirectories(path.toAbsolutePath().getParent());
        Files.writeString(path, svg);
    }

    private static void line(StringBuilder out, double x1, double y1, double x2, double y2, String color) {
        out.append("<path d=\"M ").append(x1).append(' ').append(y1).append(" L ").append(x2).append(' ').append(y2)
                .append("\" fill=\"none\" stroke=\"").append(color).append("\" stroke-width=\"0.7\"/>");
    }

    private static void rect(StringBuilder out, ResearchTreeScene.Bounds b, String stroke, String fill, double width) {
        out.append("<rect x=\"").append(b.x()).append("\" y=\"").append(b.y()).append("\" width=\"")
                .append(b.width()).append("\" height=\"").append(b.height()).append("\" rx=\"2\" stroke=\"")
                .append(stroke).append("\" stroke-width=\"").append(width).append("\" fill=\"").append(fill).append("\"/>");
    }
    private static String escape(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
