package com.gamergaming.taczweaponblueprints.client;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import java.util.function.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Native compact-tile paint layer. It consumes current public presentation and validated geometry. */
final class ResearchTreeSceneRenderer {
    private static final int PANEL = 0xff18232e, BORDER = 0xff354653, TEXT = 0xffe3e9e8, MUTED = 0xff99a9b4, ACCENT = 0xffecc76e;
    private record SegmentKey(int connection, int segment) { }
    private boolean junctionLabels;
    private final double minimumNodeHeight;
    private final Map<ResourceLocation, ResearchTreeNodePresentation.State> presentations = new HashMap<>();
    private record NodeFrame(ResearchTreeScene.Node node, ResearchTreeGraph.Node publicNode,
            ResearchTreeCanvas.SceneDecoration decoration, ResearchTreeNodePresentation presentation) { }
    private List<NodeFrame> frames = List.of();
    boolean labelsVisible(ResourceLocation id) {
        return frames.stream().anyMatch(f -> f.node().id().equals(id) && f.presentation().named());
    }
    record PresentationEvidence(ResourceLocation id, ResearchTreeScene.Bounds body,
            ResearchTreeNodePresentation presentation, ResearchTreeCanvas.SceneDecoration decoration) { }
    List<PresentationEvidence> presentationEvidence() {
        return frames.stream().map(f -> new PresentationEvidence(f.node().id(),f.node().body(),f.presentation(),f.decoration())).toList();
    }
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
    void bind(ResearchTechTreeProjection projection) { this.projection = projection; art.clearPresentation(); artGeneration = -1; labels.clear(); landmarks = List.of(); annotationCamera = null; frames = List.of(); }
    private void prepareArt() {
        if (artGeneration != ResearchTreeNodeArt.reloadGeneration()) {
            art.prepare(projection); artGeneration = ResearchTreeNodeArt.reloadGeneration(); labels.clear();
        }
    }
    void renderArt(GuiGraphics g, ResearchTreeGraph.Node node, ResearchTreeScene.Bounds box) { prepareArt(); art.render(g, node, box); }
    private final Map<String, List<String>> labels = new LinkedHashMap<>(128, .75f, true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,List<String>> entry) { return size() > 4096; }
    };
    private List<String> label(Font font, Component name, int width, int capacity) {
        String value = name.getString().strip();
        return labels.computeIfAbsent(width + ":" + capacity + ":" + value, ignored -> {
            var lines = new ArrayList<String>(); String rest = value;
            for (int i = 0; i < capacity && !rest.isEmpty(); i++) {
                String line = font.plainSubstrByWidth(rest, i == capacity-1 && font.width(rest) > width ? width - font.width("...") : width);
                if (line.isEmpty() || font.width(line)==0) break;
                if (line.length() < rest.length() && line.lastIndexOf(' ') > line.length() / 2) line = line.substring(0, line.lastIndexOf(' '));
                rest = rest.substring(line.length()).stripLeading();
                lines.add(line + (i == capacity-1 && !rest.isEmpty() ? "..." : ""));
                if (!rest.isEmpty()) rest = continuationFormat(line)+rest;
            }
            return List.copyOf(lines);
        });
    }
    /** Legacy formatting in pack names must survive an independently drawn wrapped line. */
    static String continuationFormat(String line) {
        var result = new StringBuilder();
        for (int i=0;i+1<line.length();i++) if (line.charAt(i)=='\u00a7') {
            var format = net.minecraft.ChatFormatting.getByCode(line.charAt(++i));
            if (format == null) continue;
            if (format.isColor() || format == net.minecraft.ChatFormatting.RESET) result.setLength(0);
            if (format != net.minecraft.ChatFormatting.RESET) result.append(format);
        }
        return result.toString();
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
        junctionLabels = minimumNodeHeight*camera.scale() >= (junctionLabels ? 26 : 28);
        var view = canvas.bounds();
        var cb = camera.visibleCanvasBounds();
        var visible = new ResearchTreeScene.Bounds(cb.x() - geometry.offsetX, cb.y() - geometry.offsetY, cb.width(), cb.height());
        var nextFrames = new ArrayList<NodeFrame>();
        for (var node : geometry.query(visible.x(), visible.y(), visible.right(), visible.bottom())) {
            if (!ResearchTreeRoutingIndex.intersects(node.paint(), visible)) continue;
            var publicNode = projection.graph().node(node.id()).orElseThrow();
            var presentation = ResearchTreeNodePresentation.resolve(presentations.get(node.id()), node.body(),
                    camera.scale(), font.lineHeight, (width, capacity) -> label(font,names.apply(publicNode),width,capacity));
            presentations.put(node.id(), presentation.state());
            nextFrames.add(new NodeFrame(node,publicNode,canvas.sceneDecoration(node.id()),presentation));
        }
        frames = List.copyOf(nextFrames);
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
                if (junctionLabels) {
                    String text = junction.group().alternatives().size() + junction.group().hiddenCount() + junction.group().externalCount() > 1 ? "OR" : "+";
                    g.pose().pushPose(); g.pose().translate(junction.bounds().centerX(), junction.bounds().y() + 1, 0); g.pose().scale(0.6f, 0.6f, 1);
                    g.drawString(font, text, -font.width(text) / 2, 0, MUTED, false); g.pose().popPose();
                }
            }
            for (var marker : scene.boundaries()) {
                if (!ResearchTreeRoutingIndex.intersects(marker.bounds(), visible)) continue;
                fill(g, marker.bounds(), PANEL); border(g, marker.bounds(), 0xff78b9ca, 1);
                if (junctionLabels) g.drawString(font,
                        marker.links().get(0).direction() == ResearchTechTreeProjection.Direction.REQUIREMENT ? "<" : ">", (int) marker.bounds().x() + 3,
                        (int) marker.bounds().y() + 2, TEXT, false);
            }
            });
            if (frames.stream().noneMatch(f -> f.presentation().channels().artwork()))
                g.drawManaged(() -> drawNodes(g,font,colors,symbols));
            else drawNodes(g,font,colors,symbols);
            g.flush();
        } finally { g.pose().popPose(); g.disableScissor(); }
        renderAnnotations(g, font, canvas, names);
        if (timingObserver != null) timingObserver.accept(System.nanoTime() - cpuStart);
    }

    private void drawNodes(GuiGraphics g, Font font, ToIntFunction<ResearchTreeGraph.Node> colors,
            Function<ResearchTreeGraph.Node, ResearchTreePresentationContract.StatusSymbol> symbols) {
        for (var frame : frames) {
            var node = frame.node(); var publicNode = frame.publicNode(); var b = node.body();
            var decoration = frame.decoration(); var presentation = frame.presentation();
            var marks = presentation.marks(); boolean chosen = decoration.focused();
            int statusColor = colors.applyAsInt(publicNode);
            fill(g,b,!presentation.channels().artwork() ? statusColor : publicNode.learned() ? 0xff1b3733 : PANEL);
            if (presentation.channels().artwork()) art.render(g,publicNode,presentation.artwork());
            border(g,b,chosen ? ACCENT : decoration.search() ? 0xff62c7d9 : BORDER,chosen ? 2 : 1);
            if (decoration.hovered()) border(g,b,TEXT,1);
            if (decoration.activeSearch()) border(g,node.paint(),0xff62c7d9,2);
            if (decoration.trackedPath()) g.fill((int)b.x(),(int)b.bottom()-2,(int)b.right(),(int)b.bottom(),0xff62c7d9);
            g.fill((int)b.x()+1,(int)b.y()+1,(int)b.right()-1,(int)b.y()+3,statusColor);
            if (presentation.channels().stateMarks()) {
                var status = marks.status(); fill(g,status.inflate(1),PANEL);
                ResearchTreeStatusGlyph.render(g,(int)status.x(),(int)status.y(),statusColor,ResearchTreeStatusGlyph.forSymbol(symbols.apply(publicNode)));
                if (decoration.fragments() || decoration.blocker()) {
                    var progress = marks.progression(); fill(g,progress,PANEL);
                    g.drawString(font,decoration.blocker() ? "!" : "+",(int)progress.x()+1,(int)progress.y(),
                            decoration.blocker() ? 0xffffa45c : 0xff62c7d9,false);
                }
                if (decoration.pinned()) {
                    var pin = marks.inspected(); fill(g,pin.inflate(1),PANEL);
                    g.fill((int)pin.x(),(int)pin.y(),(int)pin.x()+2,(int)pin.bottom(),TEXT);
                    g.fill((int)pin.x(),(int)pin.bottom()-2,(int)pin.right(),(int)pin.bottom(),TEXT);
                }
                if (decoration.trackedTarget()) {
                    var target = marks.tracked(); fill(g,target.inflate(1),PANEL);
                    int x=(int)target.x()+3,y=(int)target.y()+3;
                    g.fill(x-3,y-1,x+4,y+2,0xff62c7d9); g.fill(x-1,y-3,x+2,y+4,0xff62c7d9);
                }
            }
            if (decoration.dimmed()) fill(g,b,0x99101923);
        }
    }

    private static int lineThickness(ResearchTreeConnectorStyle style,double scale) {
        // Earlier possible prerequisites need modest emphasis of their own.
        // Round down so they stay thinner than direct requirements at distant zoom.
        int zoomBoost = switch (style) {
            case ANCESTRY -> (int)Math.min(12,Math.floor(.75/scale));
            case DIRECT -> (int)Math.ceil(1/scale);
            case REVIEW -> (int)Math.ceil(2/scale);
            default -> 1;
        };
        return Math.max(style.thickness,zoomBoost);
    }
    private ResearchTreeConnectorStyle connectionStyle(List<ResearchTreeGraph.Edge> edges, ResearchTreeCanvas canvas) {
        var style = ResearchTreeConnectorStyle.QUIET;
        for (var edge : edges) style = style.strongest(canvas.sceneEdgeStyle(edge));
        return style;
    }

    record Landmark(ResearchTreeScene.Region region, ResearchTreeScreenLayout.Rect bounds, Component title) { }
    private List<Landmark> landmarks = List.of();
    private final ResearchTreeLandmarkPlacement landmarkPlacement = new ResearchTreeLandmarkPlacement();
    void clearLandmarkHits() { landmarks = List.of(); annotationCamera = null; landmarkPlacement.hideAll(); }
    private ResearchTreeViewport.Snapshot annotationCamera;
    private ResearchTreeScreenLayout.Rect annotationView;
    Optional<ResearchTreeScene.Region> landmarkAt(ResearchTreeCanvas canvas, double x, double y) {
        if (!canvas.branchLabelsVisible() || !canvas.viewport().renderedSnapshot().equals(annotationCamera) || !canvas.bounds().equals(annotationView)) return Optional.empty();
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
        // Labels never cover visible tiles, connectors, junctions or portals.
        var cb = camera.visibleCanvasBounds();
        var visible = new ResearchTreeScene.Bounds(cb.x()-geometry.offsetX, cb.y()-geometry.offsetY, cb.width(), cb.height());
        for (var node : geometry.query(visible.x(), visible.y(), visible.right(), visible.bottom()))
            annotationObstacle(canvas, node.paint(), occupied);
        for (var segment : routes.query(visible)) {
            double x = Math.min(segment.from().x(), segment.to().x());
            double y = Math.min(segment.from().y(), segment.to().y());
            annotationObstacle(canvas, new ResearchTreeScene.Bounds(x, y,
                    Math.max(1, Math.abs(segment.to().x()-segment.from().x())),
                    Math.max(1, Math.abs(segment.to().y()-segment.from().y()))), occupied);
        }
        scene.boundaries().forEach(b -> annotationObstacle(canvas, b.bounds(), occupied));
        scene.routing().orElseThrow().junctions().forEach(j -> annotationObstacle(canvas, j.bounds(), occupied));
        var priorities = new LinkedHashSet<net.minecraft.resources.ResourceLocation>();
        // Inspected target, keyboard focus, then active search result win collisions.
        scene.nodes().stream().filter(n -> canvas.sceneDecoration(n.id()).pinned()).findFirst().ifPresent(n -> priorities.add(n.id()));
        canvas.focusedId().ifPresent(priorities::add); canvas.activeSearchMatch().ifPresent(priorities::add);
        g.enableScissor(view.x(),view.y(),view.right(),view.bottom());
        try {
            frames.stream().filter(f -> f.decoration().trackedTarget() && !f.presentation().channels().stateMarks()).findFirst().ifPresent(frame -> {
                var b = screen(canvas,frame.node().body());
                if (b.overlaps(view)) {
                    int x = Math.max(view.x()+4,Math.min(b.right()-3,view.right()-5));
                    int y = Math.max(view.y()+4,Math.min(b.y()+3,view.bottom()-5));
                    g.fill(x-4,y-1,x+5,y+2,0xff62c7d9); g.fill(x-1,y-4,x+2,y+5,0xff62c7d9);
                    occupied.add(new ResearchTreeScreenLayout.Rect(x-4,y-4,9,9));
                }
            });
            for (var id : priorities) {
                var node = scene.nodes().stream().filter(n -> n.id().equals(id)).findFirst().orElse(null);
                if (node == null || labelsVisible(id)) continue;
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
            // These headers stay at readable GUI size, even at very distant overview.
            var next = new ArrayList<Landmark>();
            for (var region : regionOrder) {
                var body = screen(canvas,landmarkBounds.get(region));
                if (!canvas.branchLabelsVisible() || !body.overlaps(view)) { landmarkPlacement.hide(region.seed().id()); continue; }
                var title = regionTitle(region);
                int w = Math.min(Math.min(180,view.width()-8),Math.max(48,font.width(title)+16));
                if (w < 48) { landmarkPlacement.hide(region.seed().id()); continue; }
                int left = Math.max(view.x(), body.x()), top = Math.max(view.y(), body.y());
                var footprint = new ResearchTreeScreenLayout.Rect(left, top,
                        Math.min(view.right(), body.right()) - left, Math.min(view.bottom(), body.bottom()) - top);
                var box = landmarkPlacement.place(region.seed().id(), view, footprint, body.x(), body.y(),
                        camera.scale(), w, font.lineHeight+6, occupied);
                if (box.isEmpty()) continue;
                var b = box.orElseThrow(); occupied.add(b); next.add(new Landmark(region,b,title));
                g.fill(b.x(),b.y(),b.right(),b.bottom(),0xf018232e);
                g.renderOutline(b.x(),b.y(),b.width(),b.height(),0xff85a2a5);
                g.drawString(font,clipped(font,title.getString(),w-16)+" ›",b.x()+4,b.y()+3,TEXT,false);
            }
            landmarks = List.copyOf(next); annotationCamera = camera.renderedSnapshot(); annotationView = view;
            for (var frame : frames) {
                var presentation = frame.presentation();
                if (!presentation.named()) continue;
                var textBox = screen(canvas,presentation.nameBounds());
                int y = textBox.y();
                for (var line : presentation.nameLines()) {
                    g.drawString(font,line,textBox.x()+(textBox.width()-font.width(line))/2,y,
                            frame.decoration().dimmed() ? MUTED : TEXT,false);
                    y += font.lineHeight;
                }
            }
            g.flush();
        } finally { g.disableScissor(); }
    }
    private void annotationObstacle(ResearchTreeCanvas canvas, ResearchTreeScene.Bounds world,
            List<ResearchTreeScreenLayout.Rect> occupied) {
        var b = screen(canvas, world); var view = canvas.bounds();
        int x = Math.max(view.x(), b.x()-2), y = Math.max(view.y(), b.y()-2);
        int right = Math.min(view.right(), b.right()+2), bottom = Math.min(view.bottom(), b.bottom()+2);
        if (right > x && bottom > y) occupied.add(new ResearchTreeScreenLayout.Rect(x,y,right-x,bottom-y));
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
