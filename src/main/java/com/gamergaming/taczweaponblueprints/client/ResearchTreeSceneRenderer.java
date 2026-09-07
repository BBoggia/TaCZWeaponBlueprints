package com.gamergaming.taczweaponblueprints.client;

import java.util.*;
import java.util.function.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Native compact-tile paint layer. It consumes current public presentation and validated geometry. */
final class ResearchTreeSceneRenderer {
    private static final int PANEL = 0xff18232e, BORDER = 0xff354653, TEXT = 0xffe3e9e8, MUTED = 0xff99a9b4, ACCENT = 0xffecc76e;
    private enum Detail { OVERVIEW, BROWSE }
    private record SegmentKey(int connection, int segment) { }
    private Detail detail = Detail.BROWSE;
    java.util.function.LongConsumer timingObserver;
    private final ResearchTreeSceneView geometry;
    private final ResearchTreeScene scene;
    private ResearchTechTreeProjection projection;
    private final ResearchTreeRoutingIndex routes;
    private final Map<SegmentKey, List<Double>> crossings = new HashMap<>();
    private final Map<ResearchTreeSceneInput.GroupId, List<ResearchTreeGraph.Edge>> groupEdges = new HashMap<>();
    private final List<ResearchTreeScene.Region> regionOrder;
    private final List<List<ResearchTreeGraph.Edge>> connectionEdges;
    private final int[] routeColors;
    private final ResearchTreeNodeArt art = new ResearchTreeNodeArt();
    private long artGeneration = -1;
    ResearchTreeSceneRenderer(ResearchTreeSceneView geometry) {
        this.geometry = geometry; scene = geometry.scene;
        routes = new ResearchTreeRoutingIndex(scene.routing().orElseThrow());
        for (var group : scene.key().topology().groups()) groupEdges.put(group.id(), group.alternatives().stream()
                .map(id -> new ResearchTreeGraph.Edge(id,group.id().dependentId())).toList());
        for (var crossing : scene.routing().orElseThrow().crossings()) crossings.computeIfAbsent(
                new SegmentKey(crossing.horizontalConnection(), crossing.horizontalSegment()), ignored -> new ArrayList<>()).add(crossing.point().x());
        crossings.values().forEach(v -> v.sort(Double::compare));
        regionOrder = scene.regions().stream().sorted(Comparator.comparingDouble(
                (ResearchTreeScene.Region r) -> r.bounds().width() * r.bounds().height()).reversed()).toList();
        connectionEdges = scene.routing().orElseThrow().connections().stream().map(c -> c.owner().group()
                .map(id -> c.owner().prerequisite().map(p -> List.of(new ResearchTreeGraph.Edge(p,id.dependentId())))
                        .orElseGet(() -> groupEdges.getOrDefault(id,List.of()))).orElse(List.of())).toList();
        routeColors = new int[connectionEdges.size()];
    }
    void bind(ResearchTechTreeProjection projection) { this.projection = projection; art.clearPresentation(); artGeneration = -1; labels.clear(); }
    private void prepareArt() {
        if (artGeneration != ResearchTreeNodeArt.reloadGeneration()) {
            art.prepare(projection); artGeneration = ResearchTreeNodeArt.reloadGeneration(); labels.clear();
        }
    }
    void renderArt(GuiGraphics g, ResearchTreeGraph.Node node, ResearchTreeScene.Bounds box) { prepareArt(); art.render(g, node, box); }
    private final Map<String, List<String>> labels = new HashMap<>();
    private List<String> label(Font font, Component name, int width) {
        String value = name.getString();
        return labels.computeIfAbsent(width + ":" + value, ignored -> {
            var lines = new ArrayList<String>(); String rest = value;
            for (int i = 0; i < 2 && !rest.isEmpty(); i++) {
                String line = font.plainSubstrByWidth(rest, i == 1 && font.width(rest) > width ? width - font.width("...") : width);
                if (line.isEmpty()) break;
                if (line.length() < rest.length() && line.lastIndexOf(' ') > line.length() / 2) line = line.substring(0, line.lastIndexOf(' '));
                rest = rest.substring(line.length()).stripLeading();
                lines.add(line + (i == 1 && !rest.isEmpty() ? "..." : ""));
            }
            return List.copyOf(lines);
        });
    }
    private Component regionTitle(ResearchTreeScene.Region region) {
        var authored = projection.presentation().branches().stream()
                .filter(branch -> region.seed().id().equals("authored/" + branch.id())).findFirst();
        if (authored.isPresent()) {
            var branch = authored.orElseThrow();
            if (!branch.title().isEmpty()) return branch.translationKey().map(Component::translatable)
                    .map(Component.class::cast).orElseGet(() -> Component.literal(branch.title()));
            return Component.translatable("gui.taczweaponblueprints.research_bench.tree.scene.unnamed_branch");
        }
        if (region.seed().id().equals("unassigned")) return Component.translatable("gui.taczweaponblueprints.research_bench.tree.scene.unassigned");
        return Component.translatable(region.seed().id().equals("automatic/shared")
                ? "gui.taczweaponblueprints.research_bench.tree.scene.shared"
                : "gui.taczweaponblueprints.research_bench.tree.scene.branch", region.seed().order() + 1);
    }
    void render(GuiGraphics g, Font font, ResearchTreeCanvas canvas,
            Function<ResearchTreeGraph.Node, Component> names, ToIntFunction<ResearchTreeGraph.Node> colors,
            Function<ResearchTreeGraph.Node, ResearchTreePresentationContract.StatusSymbol> symbols) {
        long cpuStart = timingObserver == null ? 0 : System.nanoTime();
        prepareArt();
        var camera = canvas.viewport();
        boolean browse = detail == Detail.BROWSE ? camera.scale() >= .48 : camera.scale() > .58;
        detail = browse ? Detail.BROWSE : Detail.OVERVIEW;
        var view = canvas.bounds();
        var cb = camera.visibleCanvasBounds();
        var visible = new ResearchTreeScene.Bounds(cb.x() - geometry.offsetX, cb.y() - geometry.offsetY, cb.width(), cb.height());
        g.enableScissor(view.x(), view.y(), view.right(), view.bottom());
        g.pose().pushPose();
        g.pose().translate(view.x() + (geometry.offsetX - camera.panX()) * camera.scale(), view.y() + (geometry.offsetY - camera.panY()) * camera.scale(), 0);
        g.pose().scale((float) camera.scale(), (float) camera.scale(), 1);
        try {
            g.drawManaged(() -> {
            for (var region : regionOrder) {
                if (!ResearchTreeRoutingIndex.intersects(region.bounds(), visible)) continue;
                var b = region.bounds();
                boolean root = region.seed().parentId().isEmpty();
                fill(g, b, root ? 0x101d3644 : 0x7040575b);
                border(g, b, root ? 0x40516973 : 0xc085a2a5, 1);
                if (detail == Detail.BROWSE) g.drawString(font,
                        font.plainSubstrByWidth(regionTitle(region).getString(), 80), (int) b.x() + 30, (int) b.y() + 12, MUTED, false);
            }
            });
            var routing = scene.routing().orElseThrow();
            Arrays.fill(routeColors, 0);
            // GuiGraphics otherwise flushes every filled segment. Batch the route layer
            // before drawing junctions and nodes, retaining crossing gaps and draw order.
            g.drawManaged(() -> {
                for (var segment : routes.query(visible)) {
                    int connection = segment.connection();
                    int color = routeColors[connection];
                    if (color == 0) routeColors[connection] = color = connectionColor(connectionEdges.get(connection), canvas);
                    double ax = segment.from().x(), ay = segment.from().y(), bx = segment.to().x(), by = segment.to().y();
                    if (segment.horizontal()) {
                        double from = Math.min(ax, bx), end = Math.max(ax, bx);
                        for (double cross : crossings.getOrDefault(new SegmentKey(segment.connection(), segment.ordinal()), List.of())) {
                            double left = Math.max(from, cross - 2), right = Math.min(end, cross + 2);
                            if (left > from) g.fill((int) from, (int) ay, (int) Math.ceil(left), (int) ay + 1, color);
                            from = Math.max(from, right);
                        }
                        if (end > from) g.fill((int) from, (int) ay, (int) Math.ceil(end), (int) ay + 1, color);
                    } else g.fill((int) ax, (int) Math.min(ay, by), (int) ax + 1, (int) Math.ceil(Math.max(ay, by)), color);
                }
            });
            g.drawManaged(() -> {
            for (var junction : routing.junctions()) {
                if (!ResearchTreeRoutingIndex.intersects(junction.bounds(), visible)) continue;
                fill(g, junction.bounds(), PANEL); border(g, junction.bounds(), 0xff73939a, 1);
                if (detail == Detail.BROWSE) {
                    String text = junction.group().alternatives().size() + junction.group().hiddenCount() + junction.group().externalCount() > 1 ? "OR" : "+";
                    g.pose().pushPose(); g.pose().translate(junction.bounds().centerX(), junction.bounds().y() + 1, 0); g.pose().scale(0.6f, 0.6f, 1);
                    g.drawString(font, text, -font.width(text) / 2, 0, MUTED, false); g.pose().popPose();
                }
            }
            for (var marker : scene.boundaries()) {
                if (!ResearchTreeRoutingIndex.intersects(marker.bounds(), visible)) continue;
                fill(g, marker.bounds(), PANEL); border(g, marker.bounds(), 0xff78b9ca, 1);
                if (detail == Detail.BROWSE) g.drawString(font,
                        marker.links().get(0).direction() == ResearchTechTreeProjection.Direction.REQUIREMENT ? "<" : ">", (int) marker.bounds().x() + 3,
                        (int) marker.bounds().y() + 2, TEXT, false);
            }
            });
            if (detail == Detail.OVERVIEW) g.drawManaged(() -> drawNodes(g,font,canvas,names,colors,symbols,visible));
            else drawNodes(g,font,canvas,names,colors,symbols,visible);
            g.flush();
        } finally { g.pose().popPose(); g.disableScissor();
            if (timingObserver != null) timingObserver.accept(System.nanoTime() - cpuStart);
        }
    }

    private void drawNodes(GuiGraphics g, Font font, ResearchTreeCanvas canvas,
            Function<ResearchTreeGraph.Node, Component> names, ToIntFunction<ResearchTreeGraph.Node> colors,
            Function<ResearchTreeGraph.Node, ResearchTreePresentationContract.StatusSymbol> symbols,
            ResearchTreeScene.Bounds visible) {
            for (var node : geometry.query(visible.x(), visible.y(), visible.right(), visible.bottom())) {
                if (!ResearchTreeRoutingIndex.intersects(node.paint(), visible)) continue;
                var publicNode = projection.graph().node(node.id()).orElseThrow();
                var b = node.body(); var decoration = canvas.sceneDecoration(node.id()); boolean chosen = decoration.focused();
                fill(g, b, detail == Detail.OVERVIEW ? colors.applyAsInt(publicNode)
                        : publicNode.learned() ? 0xff1b3733 : PANEL);
                border(g, b, chosen ? ACCENT : canvas.searchMatches().contains(node.id()) ? 0xff62c7d9 : BORDER, chosen ? 2 : 1);
                if (decoration.hovered()) border(g, b, TEXT, 1);
                if (decoration.pinned()) {
                    g.fill((int)b.x()+2,(int)b.bottom()-8,(int)b.x()+4,(int)b.bottom()-2,TEXT);
                    g.fill((int)b.x()+2,(int)b.bottom()-4,(int)b.x()+10,(int)b.bottom()-2,TEXT);
                }
                if (decoration.activeSearch()) border(g, node.paint(), 0xff62c7d9, 2);
                if (decoration.trackedTarget()) {
                    int x=(int)b.right()-9, y=(int)b.bottom()-7;
                    g.fill(x,y,x+5,y+5,0xff62c7d9); g.fill(x+2,y-2,x+3,y+7,0xff62c7d9);
                }
                if (canvas.isTrackedPathNode(node.id())) g.fill((int) b.x(), (int) b.bottom() - 2, (int) b.right(), (int) b.bottom(), 0xff62c7d9);
                g.fill((int) b.x() + 1, (int) b.y() + 1, (int) b.right() - 1, (int) b.y() + 3, colors.applyAsInt(publicNode));
                if (detail == Detail.BROWSE) {
                    art.render(g, publicNode, new ResearchTreeScene.Bounds(b.x() + 5, b.y() + 6, b.width() - 10, 16));
                    int y = (int) b.y() + 25;
                    for (String line : label(font, names.apply(publicNode), (int) b.width() - 12)) {
                        int x = (int) b.centerX() - font.width(line) / 2;
                        g.drawString(font, line, x, y, TEXT, false); y += font.lineHeight;
                    }
                    ResearchTreeStatusGlyph.render(g, (int) b.right() - 10, (int) b.y() + 4, colors.applyAsInt(publicNode), ResearchTreeStatusGlyph.forSymbol(symbols.apply(publicNode)));
                    if (decoration.fragments() || decoration.blocker()) g.drawString(font, decoration.blocker() ? "!" : "+",
                            (int)b.x()+5,(int)b.y()+5,decoration.blocker()?0xffffa45c:0xff62c7d9,false);
                }
                if (decoration.dimmed()) fill(g,b,0x99101923);
            }
    }

    private int connectionColor(List<ResearchTreeGraph.Edge> edges, ResearchTreeCanvas canvas) {
        boolean requirement = false, unlock = false;
        for (var edge : edges) {
            if (canvas.isTrackedPathEdge(edge)) return 0xff62c7d9;
            var role = canvas.edgeRole(edge);
            requirement |= role == ResearchTreePresentationContract.RelationshipRole.DIRECT_REQUIREMENT
                    || role == ResearchTreePresentationContract.RelationshipRole.ALTERNATIVE_REQUIREMENT
                    || role == ResearchTreePresentationContract.RelationshipRole.REQUIREMENT_PATH;
            unlock |= role == ResearchTreePresentationContract.RelationshipRole.DIRECT_UNLOCK
                    || role == ResearchTreePresentationContract.RelationshipRole.UNLOCK_PATH;
        }
        if (requirement) return ACCENT;
        if (unlock) return 0xff78b9ca;
        return detail == Detail.OVERVIEW ? 0x70617e86 : 0xff58717b;
    }

    private static void fill(GuiGraphics g, ResearchTreeScene.Bounds b, int color) { g.fill((int) b.x(), (int) b.y(), (int) Math.ceil(b.right()), (int) Math.ceil(b.bottom()), color); }
    private static void border(GuiGraphics g, ResearchTreeScene.Bounds b, int color, int thickness) {
        int x = (int) b.x(), y = (int) b.y(), r = (int) Math.ceil(b.right()), bottom = (int) Math.ceil(b.bottom());
        g.fill(x, y, r, y + thickness, color); g.fill(x, bottom - thickness, r, bottom, color);
        g.fill(x, y, x + thickness, bottom, color); g.fill(r - thickness, y, r, bottom, color);
    }

}
