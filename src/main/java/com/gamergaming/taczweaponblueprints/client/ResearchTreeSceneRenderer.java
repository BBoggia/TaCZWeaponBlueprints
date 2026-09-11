package com.gamergaming.taczweaponblueprints.client;

import java.util.*;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeDetailPolicy.Detail;
import java.util.function.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Native compact-tile paint layer. It consumes current public presentation and validated geometry. */
final class ResearchTreeSceneRenderer {
    private static final int PANEL = 0xff18232e, BORDER = 0xff354653, TEXT = 0xffe3e9e8, MUTED = 0xff99a9b4, ACCENT = 0xffecc76e;
    private record SegmentKey(int connection, int segment) { }
    private Detail detail = Detail.BROWSE;
    private ResearchTreeDetailPolicy.Channels channels = ResearchTreeDetailPolicy.Channels.EMPTY;
    private final double minimumNodeWidth, minimumNodeHeight;
    boolean labelsVisible() { return channels.names(); }
    java.util.function.LongConsumer timingObserver;
    private final ResearchTreeSceneView geometry;
    private final ResearchTreeScene scene;
    private ResearchTechTreeProjection projection;
    private final ResearchTreeRoutingIndex routes;
    private final Map<SegmentKey, List<ResearchTreeRouting.Crossing>> crossings = new HashMap<>();
    private final Map<ResearchTreeSceneInput.GroupId, List<ResearchTreeGraph.Edge>> groupEdges = new HashMap<>();
    private final List<ResearchTreeScene.Region> regionOrder;
    private final Map<ResearchTreeScene.Region,ResearchTreeScene.Bounds> landmarkBounds = new HashMap<>();
    private final List<List<ResearchTreeGraph.Edge>> connectionEdges;
    private final ResearchTreeConnectorStyle[] routeStyles;
    private final ResearchTreeNodeArt art = new ResearchTreeNodeArt();
    private long artGeneration = -1;
    ResearchTreeSceneRenderer(ResearchTreeSceneView geometry) {
        this.geometry = geometry; scene = geometry.scene;
        minimumNodeWidth = scene.nodes().stream().mapToDouble(n -> n.body().width()).min().orElse(0);
        minimumNodeHeight = scene.nodes().stream().mapToDouble(n -> n.body().height()).min().orElse(0);
        routes = new ResearchTreeRoutingIndex(scene.routing().orElseThrow());
        for (var group : scene.key().topology().groups()) groupEdges.put(group.id(), group.alternatives().stream()
                .map(id -> new ResearchTreeGraph.Edge(id,group.id().dependentId())).toList());
        for (var crossing : scene.routing().orElseThrow().crossings()) crossings.computeIfAbsent(
                new SegmentKey(crossing.horizontalConnection(), crossing.horizontalSegment()), ignored -> new ArrayList<>()).add(crossing);
        crossings.values().forEach(v -> v.sort(Comparator.comparingDouble(c -> c.point().x())));
        regionOrder = scene.regions().stream().sorted(Comparator.comparingDouble(
                (ResearchTreeScene.Region r) -> r.bounds().width() * r.bounds().height()).reversed()).toList();
        regionOrder.forEach(r -> landmarkBounds.put(r,ResearchTreeSceneLandmarks.bounds(scene,r)));
        connectionEdges = scene.routing().orElseThrow().connections().stream().map(c -> c.owner().group()
                .map(id -> c.owner().prerequisite().map(p -> List.of(new ResearchTreeGraph.Edge(p,id.dependentId())))
                        .orElseGet(() -> groupEdges.getOrDefault(id,List.of()))).orElse(List.of())).toList();
        routeStyles = new ResearchTreeConnectorStyle[connectionEdges.size()];
    }
    void bind(ResearchTechTreeProjection projection) { this.projection = projection; art.clearPresentation(); artGeneration = -1; labels.clear(); landmarks = List.of(); annotationCamera = null; }
    private void prepareArt() {
        if (artGeneration != ResearchTreeNodeArt.reloadGeneration()) {
            art.prepare(projection); artGeneration = ResearchTreeNodeArt.reloadGeneration(); labels.clear();
        }
    }
    void renderArt(GuiGraphics g, ResearchTreeGraph.Node node, ResearchTreeScene.Bounds box) { prepareArt(); art.render(g, node, box); }
    private final Map<String, List<String>> labels = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<String>> entry) { return size() > 4096; }
    };
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
    Component regionTitle(ResearchTreeScene.Region region) {
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
        detail = ResearchTreeDetailPolicy.resolve(detail,
                minimumNodeWidth * camera.scale(), minimumNodeHeight * camera.scale());
        channels = ResearchTreeDetailPolicy.channels(channels,
                minimumNodeWidth * camera.scale(), minimumNodeHeight * camera.scale());
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

            }
            });
            var routing = scene.routing().orElseThrow();
            Arrays.fill(routeStyles, null);
            // GuiGraphics otherwise flushes every filled segment. Batch the route layer
            // before drawing junctions and nodes, retaining crossing gaps and draw order.
            g.drawManaged(() -> {
                for (var segment : routes.query(visible)) {
                    int connection = segment.connection();
                    var style = routeStyles[connection];
                    if (style == null) routeStyles[connection] = style = connectionStyle(connectionEdges.get(connection), canvas);
                    int color = style.color, thickness = lineThickness(style,camera.scale());
                    double ax = segment.from().x(), ay = segment.from().y(), bx = segment.to().x(), by = segment.to().y();
                    if (segment.horizontal()) {
                        double from = Math.min(ax, bx), end = Math.max(ax, bx);
                        for (var crossing : crossings.getOrDefault(new SegmentKey(segment.connection(), segment.ordinal()), List.of())) {
                            double cross = crossing.point().x();
                            var verticalStyle = connectionStyle(connectionEdges.get(crossing.verticalConnection()),canvas);
                            int gap = Math.max(thickness, lineThickness(verticalStyle,camera.scale()))+1;
                            double left = Math.max(from, cross-gap), right = Math.min(end, cross+gap);
                            if (left > from) g.fill((int) from, (int) ay, (int) Math.ceil(left), (int) ay + thickness, color);
                            from = Math.max(from, right);
                        }
                        if (end > from) g.fill((int) from, (int) ay, (int) Math.ceil(end), (int) ay + thickness, color);
                    } else g.fill((int) ax, (int) Math.min(ay, by), (int) ax + thickness, (int) Math.ceil(Math.max(ay, by)), color);
                }
            });
            g.drawManaged(() -> {
            for (var junction : routing.junctions()) {
                if (!ResearchTreeRoutingIndex.intersects(junction.bounds(), visible)) continue;
                fill(g, junction.bounds(), PANEL); border(g, junction.bounds(), 0xff73939a, 1);
                if (channels.junctions()) {
                    String text = junction.group().alternatives().size() + junction.group().hiddenCount() + junction.group().externalCount() > 1 ? "OR" : "+";
                    g.pose().pushPose(); g.pose().translate(junction.bounds().centerX(), junction.bounds().y() + 1, 0); g.pose().scale(0.6f, 0.6f, 1);
                    g.drawString(font, text, -font.width(text) / 2, 0, MUTED, false); g.pose().popPose();
                }
            }
            for (var marker : scene.boundaries()) {
                if (!ResearchTreeRoutingIndex.intersects(marker.bounds(), visible)) continue;
                fill(g, marker.bounds(), PANEL); border(g, marker.bounds(), 0xff78b9ca, 1);
                if (channels.junctions()) g.drawString(font,
                        marker.links().get(0).direction() == ResearchTechTreeProjection.Direction.REQUIREMENT ? "<" : ">", (int) marker.bounds().x() + 3,
                        (int) marker.bounds().y() + 2, TEXT, false);
            }
            });
            if (detail == Detail.OVERVIEW) g.drawManaged(() -> drawNodes(g,font,canvas,names,colors,symbols,visible));
            else drawNodes(g,font,canvas,names,colors,symbols,visible);
            g.flush();
        } finally { g.pose().popPose(); g.disableScissor(); }
        renderAnnotations(g, font, canvas, names);
        if (timingObserver != null) timingObserver.accept(System.nanoTime() - cpuStart);
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
                        : detail == Detail.BROWSE && publicNode.learned() ? 0xff1b3733 : PANEL);
                if (channels.artwork()) art.render(g, publicNode, ResearchTreeDetailPolicy.artwork(b));
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
                if (channels.stateMarks()) {
                    ResearchTreeStatusGlyph.render(g, (int) b.right() - 10, (int) b.y() + 4, colors.applyAsInt(publicNode), ResearchTreeStatusGlyph.forSymbol(symbols.apply(publicNode)));
                    if (decoration.fragments() || decoration.blocker()) g.drawString(font, decoration.blocker() ? "!" : "+",
                            (int)b.x()+5,(int)b.y()+5,decoration.blocker()?0xffffa45c:0xff62c7d9,false);
                }
                if (decoration.dimmed()) fill(g,b,0x99101923);
            }
    }

    private static int lineThickness(ResearchTreeConnectorStyle style,double scale) {
        return Math.max(style.thickness,style == ResearchTreeConnectorStyle.REVIEW ? (int)Math.ceil(2/scale)
                : style == ResearchTreeConnectorStyle.DIRECT ? (int)Math.ceil(1/scale) : 1);
    }
    private ResearchTreeConnectorStyle connectionStyle(List<ResearchTreeGraph.Edge> edges, ResearchTreeCanvas canvas) {
        var style = ResearchTreeConnectorStyle.QUIET;
        for (var edge : edges) style = style.strongest(canvas.sceneEdgeStyle(edge));
        return style;
    }

    record Landmark(ResearchTreeScene.Region region, ResearchTreeScreenLayout.Rect bounds, Component title) { }
    private List<Landmark> landmarks = List.of();
    private ResearchTreeViewport.Snapshot annotationCamera;
    private ResearchTreeScreenLayout.Rect annotationView;
    Optional<ResearchTreeScene.Region> landmarkAt(ResearchTreeCanvas canvas, double x, double y) {
        if (!canvas.viewport().renderedSnapshot().equals(annotationCamera) || !canvas.bounds().equals(annotationView)) return Optional.empty();
        return landmarks.stream().filter(l -> l.bounds().contains(x,y)).map(Landmark::region).findFirst();
    }
    record ScreenBox(int x, int y, int width, int height) {
        int right() { return x+width; }
        int bottom() { return y+height; }
        boolean overlaps(ResearchTreeScreenLayout.Rect other) {
            return x<other.right() && right()>other.x() && y<other.bottom() && bottom()>other.y();
        }
    }
    private ScreenBox screen(ResearchTreeCanvas canvas, ResearchTreeScene.Bounds b) {
        var v = canvas.viewport(); var view = canvas.bounds();
        int x = view.x() + v.viewportX(b.x() + geometry.offsetX), y = view.y() + v.viewportY(b.y() + geometry.offsetY);
        return new ScreenBox(x,y,Math.max(1,(int)Math.round(b.width()*v.scale())),Math.max(1,(int)Math.round(b.height()*v.scale())));
    }
    private void renderAnnotations(GuiGraphics g, Font font, ResearchTreeCanvas canvas,
            Function<ResearchTreeGraph.Node, Component> names) {
        var view = canvas.bounds(); var camera = canvas.viewport();
        var occupied = new ArrayList<ResearchTreeScreenLayout.Rect>();
        var priorities = new LinkedHashSet<net.minecraft.resources.ResourceLocation>();
        // Inspected target, keyboard focus, then active search result win collisions.
        scene.nodes().stream().filter(n -> canvas.sceneDecoration(n.id()).pinned()).findFirst().ifPresent(n -> priorities.add(n.id()));
        canvas.focusedId().ifPresent(priorities::add); canvas.activeSearchMatch().ifPresent(priorities::add);
        g.enableScissor(view.x(),view.y(),view.right(),view.bottom());
        try {
            for (var id : priorities) {
                var node = scene.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
                if (node == null) continue;
                var body = screen(canvas,node.body());
                if (!body.overlaps(view)) continue;
                var text = names.apply(projection.graph().node(id).orElseThrow());
                int w = Math.min(Math.min(200, view.width()-8),font.width(text)+8);
                var box = ResearchTreeAnnotationPlacement.place(view, body.x()+(body.width()-w)/2,
                        body.bottom()+3,w,font.lineHeight+6,occupied);
                if (box.isEmpty()) continue;
                var b = box.orElseThrow(); occupied.add(b);
                var d = canvas.sceneDecoration(id);
                int color = d.pinned() ? TEXT : d.activeSearch() ? 0xff62c7d9 : ACCENT;
                g.fill(b.x(),b.y(),b.right(),b.bottom(),PANEL); g.renderOutline(b.x(),b.y(),b.width(),b.height(),color);
                g.drawString(font, clipped(font,text.getString(),b.width()-8),b.x()+4,b.y()+3,color,false);
            }
            canvas.trackedTargetId().flatMap(id -> scene.nodes().stream().filter(n -> n.id().equals(id)).findFirst()).ifPresent(node -> {
                var b = screen(canvas,node.body());
                if (b.overlaps(view)) {
                    int x = Math.max(view.x()+4,Math.min(b.right()-3,view.right()-5));
                    int y = Math.max(view.y()+4,Math.min(b.y()+3,view.bottom()-5));
                    g.fill(x-4,y-1,x+5,y+2,0xff62c7d9); g.fill(x-1,y-4,x+2,y+5,0xff62c7d9);
                    occupied.add(new ResearchTreeScreenLayout.Rect(x-4,y-4,9,9));
                }
            });
            // These headers stay at readable GUI size, even at very distant overview.
            var next = new ArrayList<Landmark>();
            for (var region : regionOrder) {
                var body = screen(canvas,landmarkBounds.get(region));
                if (!body.overlaps(view)) continue;
                var title = regionTitle(region);
                int w = Math.min(Math.min(180,view.width()-8),font.width(title)+16);
                if (w < 48) continue;
                var box = ResearchTreeAnnotationPlacement.place(view,Math.max(view.x(),body.x())+4,
                        Math.max(view.y(),body.y())+4,w,font.lineHeight+8,occupied);
                if (box.isEmpty()) continue;
                var b = box.orElseThrow(); occupied.add(b); next.add(new Landmark(region,b,title));
                g.fill(b.x(),b.y(),b.right(),b.bottom(),0xf018232e);
                g.renderOutline(b.x(),b.y(),b.width(),b.height(),0xff85a2a5);
                g.drawString(font,clipped(font,title.getString(),w-16)+" ›",b.x()+4,b.y()+4,TEXT,false);
            }
            landmarks = List.copyOf(next); annotationCamera = camera.renderedSnapshot(); annotationView = view;
            if (channels.names()) {
                var cb = camera.visibleCanvasBounds();
                for (var node : geometry.query(cb.x()-geometry.offsetX,cb.y()-geometry.offsetY,cb.x()+cb.width()-geometry.offsetX,cb.y()+cb.height()-geometry.offsetY)) {
                    if (priorities.contains(node.id())) continue;
                    var b = screen(canvas,node.body());
                    var textBox = new ScreenBox(b.x()+3,b.y()+(int)Math.ceil(23*camera.scale()),Math.max(1,b.width()-6),Math.max(1,b.height()-(int)Math.ceil(23*camera.scale())-2));
                    if (occupied.stream().anyMatch(textBox::overlaps)) continue;
                    int y=textBox.y();
                    var name = names.apply(projection.graph().node(node.id()).orElseThrow());
                    var nodeLines = textBox.height() < 2*font.lineHeight
                            ? List.of(clipped(font,name.getString(),textBox.width())) : label(font,name,textBox.width());
                    for (var line : nodeLines) {
                        if(y+font.lineHeight>textBox.bottom()) break;
                        g.drawString(font,line,b.x()+(b.width()-font.width(line))/2,y,TEXT,false); y+=font.lineHeight;
                    }
                }
            }
            g.flush();
        } finally { g.disableScissor(); }
    }
    private static String clipped(Font font,String text,int width) {
        return font.width(text)<=width ? text : font.plainSubstrByWidth(text,Math.max(1,width-font.width("…")))+"…";
    }

    private static void fill(GuiGraphics g, ResearchTreeScene.Bounds b, int color) { g.fill((int) b.x(), (int) b.y(), (int) Math.ceil(b.right()), (int) Math.ceil(b.bottom()), color); }
    private static void border(GuiGraphics g, ResearchTreeScene.Bounds b, int color, int thickness) {
        int x = (int) b.x(), y = (int) b.y(), r = (int) Math.ceil(b.right()), bottom = (int) Math.ceil(b.bottom());
        g.fill(x, y, r, y + thickness, color); g.fill(x, bottom - thickness, r, bottom, color);
        g.fill(x, y, x + thickness, bottom, color); g.fill(r - thickness, y, r, bottom, color);
    }

}
