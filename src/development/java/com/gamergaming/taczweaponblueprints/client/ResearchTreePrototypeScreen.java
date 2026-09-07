package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

/** Opt-in native comparison. All selection is local; this screen has no research/network action. */
final class ResearchTreePrototypeScreen extends Screen {
    static final int SURFACE = 0xff101923, PANEL = 0xff18232e, BORDER = 0xff354653;
    static final int TEXT = 0xffe3e9e8, MUTED = 0xff99a9b4, ACCENT = 0xffecc76e;
    record Source(ResearchTreeBaselineInput input, List<ResearchTreeBranchStructure.Seed> seeds) { }
    record Line(String text, int color) { }
    private record SegmentKey(int connection, int segment) { }
    private final Screen parent;
    private final List<Source> sources = new ArrayList<>();
    private final ResearchTreePrototypeIcons icons = new ResearchTreePrototypeIcons();
    private final ResearchTreePrototypeModel.Camera camera = new ResearchTreePrototypeModel.Camera();
    private final ResearchTreeViewState legacyState = new ResearchTreeViewState();
    private final ResearchTreeCanvas legacy = new ResearchTreeCanvas(legacyState, new ResearchTreeCanvas.Style(
            0x40101923, 0x18354653, BORDER, MUTED, TEXT, ACCENT, 0xff477e89,
            0xff183023, 0xff292516, 0xff291b13, 0xff151a20, 0xff111820,
            0xffe4c56a, 0xff9b874e, 0xff62c7d9, 0xff477e89, 0xff394552, TEXT,
            0x700b0f14, 0x181d2a35, 0x90141920, 0x10283846, 0x50475869, 0xc018222c));
    private final Map<ResourceLocation, List<String>> labels = new HashMap<>();
    private final Map<SegmentKey, List<Double>> crossings = new HashMap<>();
    private final List<Line> inspectorLines = new ArrayList<>();
    private ResearchTreePrototypeModel.Layout boxes;
    private ResearchTreePrototypeModel.Treatment treatment = ResearchTreePrototypeModel.Treatment.TILE;
    private ResearchTreePrototypeModel.Detail detail = ResearchTreePrototypeModel.Detail.BROWSE;
    private ResearchTechTreeProjection projection;
    private ResearchTreeScene scene;
    private ResearchTreeRoutingIndex routes;
    private ResourceLocation selected;
    private int sourceIndex, domainIndex, scroll;
    private boolean old, quiet = true, pressed, dragged;
    private double pressX, pressY;
    private String failure;
    private double buildMs;
    private long reloadGeneration;
    private ResearchTreePrototypeCapture capture;
    private int labelWidth, clippedNames;
    private String pendingSnapshot;

    ResearchTreePrototypeScreen(Screen parent, ResearchTreeBaselineInput initial, boolean captureEnabled) {
        super(Component.literal("Tech Tree comparison"));
        this.parent = parent;
        sources.add(new Source(initial, List.of()));
        for (var f : java.util.stream.Stream.concat(ResearchTreeBaselineFixtures.structural().stream(),
                ResearchTreeBaselineFixtures.routing().stream()).filter(f -> List.of("unequal_branches", "nested_split_merge",
                        "repeated_alternatives", "anonymous_groups", "multiple_boundaries", "dense_256").contains(f.input().name())).toList()) {
            sources.add(new Source(f.input(), ResearchTreeBranchFixtures.seeds(f)));
        }
        if (captureEnabled) capture = new ResearchTreePrototypeCapture(this);
    }

    @Override protected void init() {
        boxes = ResearchTreePrototypeModel.layout(width, height);
        camera.configure(boxes.canvas());
        legacy.setBounds(ResearchTreeScreenLayout.ViewMode.FULLSCREEN, boxes.canvas());
        rebuild(scene == null);
    }

    private void rebuild(boolean start) {
        long began = System.nanoTime();
        failure = null; scene = null; routes = null; crossings.clear(); labels.clear();
        reloadGeneration = ResearchTreePrototypeIcons.reloadGeneration();
        try {
            var source = sources.get(sourceIndex);
            if (source.input().projections().isEmpty()) throw new IllegalArgumentException("This sample has no public domain.");
            domainIndex = Math.floorMod(domainIndex, source.input().projections().size());
            projection = source.input().projections().get(domainIndex).replay();
            if (selected != null && projection.graph().node(selected).isEmpty()) selected = null;
            int[] measured = projection.graph().nodes().stream().mapToInt(n -> font.width(name(n))).sorted().toArray();
            int median = measured.length == 0 ? 80 : measured[measured.length / 2];
            var metrics = ResearchTreePrototypeModel.metrics(treatment, font.lineHeight, median);
            var policy = new ResearchTreeScenePolicy(new ResearchTechTreeLayoutPolicy(20, 18, 8, 20, 4, 1000, 6, 6),
                    boxes.canvas().width(), metrics, "native-" + treatment + "-font-" + font.lineHeight,
                    new ResearchTreeScenePolicy.RegionMetrics(44, 12, 16, 80, 16, 10, 2));
            var input = ResearchTreeSceneInput.from(projection, policy).withBranchSeeds(source.seeds());
            var engine = new ResearchTreeBranchSceneEngine();
            scene = ResearchTreeSceneValidator.validate(input, engine.revision(), engine.layout(input));
            routes = new ResearchTreeRoutingIndex(scene.routing().orElseThrow());
            for (var crossing : scene.routing().orElseThrow().crossings()) crossings.computeIfAbsent(
                    new SegmentKey(crossing.horizontalConnection(), crossing.horizontalSegment()), ignored -> new ArrayList<>()).add(crossing.point().x());
            crossings.values().forEach(v -> v.sort(Double::compare));
            labelWidth = (int) metrics.width() - (treatment == ResearchTreePrototypeModel.Treatment.TILE ? 12 : 60);
            clippedNames = 0;
            for (var node : projection.graph().nodes()) {
                var lines = wrap(name(node), labelWidth);
                if (lines.size() > 2) clippedNames++;
                var bounded = new ArrayList<>(lines.subList(0, Math.min(2, lines.size())));
                if (lines.size() > 2) bounded.set(1, ellipsis(bounded.get(1), labelWidth - font.width("...")) + "...");
                labels.put(node.blueprintId(), bounded);
            }
            icons.prepare(projection);
            var stacks = new LinkedHashMap<ResourceLocation, ItemStack>();
            projection.graph().nodes().stream().filter(n -> n.visibility().revealsIcon()).forEach(n ->
                    stacks.put(n.blueprintId(), BlueprintItem.createBlueprint(n.blueprintId().toString())));
            legacy.setTechContent(projection, ResearchTechTreeLayoutEngine.layout(projection, source.input().policy(), boxes.canvas().width()),
                    stacks, selected, null);
            legacy.viewport().setAnimated(false);
            if (start) {
                var candidates = scene.nodes().stream().filter(n -> ResearchTreePrototypeModel.branch(scene, n.id())
                        .map(r -> r.seed().parentId().isPresent()).orElse(false)).toList();
                if (candidates.isEmpty()) candidates = scene.nodes();
                var first = candidates.stream().min(Comparator.comparingInt((ResearchTreeScene.Node n) -> projection.placements().get(n.id()).rank())
                        .thenComparing(n -> n.id().toString())).orElse(null);
                if (first != null) {
                    var prerequisites = projection.graph().edges().stream().filter(e -> e.dependentId().equals(first.id())).map(ResearchTreeGraph.Edge::prerequisiteId).toList();
                    var near = scene.nodes().stream().filter(n -> prerequisites.contains(n.id())).min(Comparator.comparingDouble(n -> Math.abs(n.body().centerX() - first.body().centerX())));
                    camera.fit(near.map(n -> n.body().union(first.body())).orElse(first.body()), 0.6);
                } else camera.fit(scene.extent(), 0);
            }
            inspector();
        } catch (RuntimeException rejected) {
            failure = rejected.getClass().getSimpleName() + ": " + rejected.getMessage();
            scene = null;
        }
        buildMs = (System.nanoTime() - began) / 1_000_000.0;
    }

    @Override public void tick() {
        if (reloadGeneration != ResearchTreePrototypeIcons.reloadGeneration()) rebuild(false);
    }

    @Override public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        long began = System.nanoTime();
        if (!quiet && minecraft.level != null) g.fill(0, 0, width, height, 0x99101923);
        else g.fill(0, 0, width, height, SURFACE);
        toolbar(g, mouseX, mouseY);
        var view = boxes.canvas();
        g.fill(view.x(), view.y(), view.right(), view.bottom(), quiet ? SURFACE : 0x40101923);
        if (scene == null) {
            int y = view.y() + 14;
            for (String line : wrap("Scene unavailable: " + failure, view.width() - 16)) {
                g.drawString(font, line, view.x() + 8, y, ACCENT, false); y += font.lineHeight + 3;
                if (y > view.bottom() - 12) break;
            }
        } else if (old) {
            legacy.render(g, font, n -> Component.literal(name(n)), this::color,
                    n -> ResearchTreePresentationContract.statusSymbol(n, true), id -> Component.literal(id.getPath()));
        } else drawScene(g);
        g.flush();
        drawInspector(g);
        String footer = scene == null ? "Explicit unavailable state; no dependencies were dropped."
                : (old ? "Current canvas" : detail == ResearchTreePrototypeModel.Detail.OVERVIEW ? "Overview: zoom in for names" : "Browse")
                + "  |  Drag to pan / scroll to zoom  |  D: domain";
        g.drawString(font, ellipsis(footer, width - 16), 8, height - 14, MUTED, false);
        g.flush();
        long renderNanos = System.nanoTime() - began;
        if (capture != null) capture.afterRender(g, renderNanos);
        if (pendingSnapshot != null) {
            String name = pendingSnapshot; pendingSnapshot = null;
            try {
                var directory = java.nio.file.Path.of(System.getProperty("tacz.treePrototype.output")).resolve("manual");
                java.nio.file.Files.createDirectories(directory);
                java.nio.file.Files.writeString(directory.resolve(name + ".json"), ResearchTreeBaselineInput.JSON.toJson(evidence()) + "\n");
                net.minecraft.client.Screenshot.grab(directory.toFile(), name + ".png", minecraft.getMainRenderTarget(),
                        message -> System.out.println("[Tree prototype] " + message.getString()));
            } catch (Exception failure) { System.err.println("[Tree prototype] Snapshot failed: " + failure); }
        }
    }

    private void toolbar(GuiGraphics g, int mx, int my) {
        g.fill(0, 0, width, boxes.canvas().y() - 4, PANEL);
        String domain = projection == null ? "" : projection.domain().name().toLowerCase();
        g.drawString(font, ellipsis("TECH TREE  /  " + domain + "  /  " + sources.get(sourceIndex).input().name(), width - 16), 8, 8, TEXT, false);
        String[] labels = {"Overview", "Branch", "Target", treatment == ResearchTreePrototypeModel.Treatment.TILE ? "Tile" : "Card",
                old ? "Current" : "New", quiet ? "Quiet" : "World", "Sample", "Back"};
        for (int i = 0; i < labels.length; i++) {
            var b = button(i); boolean hover = b.contains(mx, my);
            g.fill(b.x(), b.y(), b.right(), b.bottom(), hover ? 0xff30414d : 0xff22313d);
            g.fill(b.x(), b.bottom() - 1, b.right(), b.bottom(), hover ? ACCENT : BORDER);
            g.drawString(font, labels[i], b.x() + 3, b.y() + 6, hover ? ACCENT : TEXT, false);
        }
    }

    private ResearchTreeScreenLayout.Rect button(int index) {
        return new ResearchTreeScreenLayout.Rect(8 + index % boxes.toolbarColumns() * 50,
                25 + index / boxes.toolbarColumns() * 22, 48, 20);
    }

    private void drawScene(GuiGraphics g) {
        detail = ResearchTreePrototypeModel.detail(detail, camera.scale());
        var view = boxes.canvas(); var visible = camera.visible();
        g.enableScissor(view.x(), view.y(), view.right(), view.bottom());
        g.pose().pushPose();
        g.pose().translate(camera.screenX(0), camera.screenY(0), 0);
        g.pose().scale((float) camera.scale(), (float) camera.scale(), 1);
        try {
            for (var region : scene.regions().stream().sorted(Comparator.comparingDouble((ResearchTreeScene.Region r) -> r.bounds().width() * r.bounds().height()).reversed()).toList()) {
                if (!ResearchTreeRoutingIndex.intersects(region.bounds(), visible)) continue;
                var b = region.bounds();
                boolean root = region.seed().parentId().isEmpty();
                fill(g, b, root ? 0x101d3644 : 0x7040575b);
                border(g, b, root ? 0x40516973 : 0xc085a2a5, 1);
                if (detail == ResearchTreePrototypeModel.Detail.BROWSE) g.drawString(font,
                        ellipsis(regionTitle(region), 80), (int) b.x() + 30, (int) b.y() + 12, MUTED, false);
            }
            var routing = scene.routing().orElseThrow();
            // GuiGraphics otherwise flushes every filled segment. Batch the route layer
            // before drawing junctions and nodes, retaining crossing gaps and draw order.
            g.drawManaged(() -> {
                for (var segment : routes.query(visible)) {
                    var connection = routing.connections().get(segment.connection());
                    boolean emphasized = connection.owner().group().map(id -> id.dependentId().equals(selected)).orElse(false);
                    int color = emphasized ? ACCENT : (detail == ResearchTreePrototypeModel.Detail.OVERVIEW ? 0x70617e86 : 0xff58717b);
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
            for (var junction : routing.junctions()) {
                if (!ResearchTreeRoutingIndex.intersects(junction.bounds(), visible)) continue;
                fill(g, junction.bounds(), PANEL); border(g, junction.bounds(), 0xff73939a, 1);
                if (detail == ResearchTreePrototypeModel.Detail.BROWSE) {
                    String text = junction.group().alternatives().size() + junction.group().hiddenCount() + junction.group().externalCount() > 1 ? "OR" : "+";
                    g.pose().pushPose(); g.pose().translate(junction.bounds().centerX(), junction.bounds().y() + 1, 0); g.pose().scale(0.6f, 0.6f, 1);
                    g.drawString(font, text, -font.width(text) / 2, 0, MUTED, false); g.pose().popPose();
                }
            }
            for (var marker : scene.boundaries()) {
                if (!ResearchTreeRoutingIndex.intersects(marker.bounds(), visible)) continue;
                fill(g, marker.bounds(), PANEL); border(g, marker.bounds(), 0xff78b9ca, 1);
                if (detail == ResearchTreePrototypeModel.Detail.BROWSE) g.drawString(font,
                        marker.links().get(0).remoteDomain().name().substring(0, 1), (int) marker.bounds().x() + 3,
                        (int) marker.bounds().y() + 2, TEXT, false);
            }
            for (var node : scene.nodes()) {
                if (!ResearchTreeRoutingIndex.intersects(node.paint(), visible)) continue;
                var publicNode = projection.graph().node(node.id()).orElseThrow();
                var b = node.body(); boolean chosen = node.id().equals(selected);
                fill(g, b, detail == ResearchTreePrototypeModel.Detail.OVERVIEW ? color(publicNode)
                        : publicNode.learned() ? 0xff1b3733 : PANEL);
                border(g, b, chosen ? ACCENT : BORDER, chosen ? 2 : 1);
                g.fill((int) b.x() + 1, (int) b.y() + 1, (int) b.right() - 1, (int) b.y() + 3, color(publicNode));
                if (detail == ResearchTreePrototypeModel.Detail.BROWSE) {
                    boolean tile = treatment == ResearchTreePrototypeModel.Treatment.TILE;
                    icons.render(g, publicNode, new ResearchTreeScene.Bounds(b.x() + 5, b.y() + 6, tile ? b.width() - 10 : 46, tile ? 16 : b.height() - 12));
                    int y = (int) b.y() + (tile ? 25 : 9);
                    for (String line : labels.getOrDefault(node.id(), List.of())) {
                        int x = tile ? (int) b.centerX() - font.width(line) / 2 : (int) b.x() + 56;
                        g.drawString(font, line, x, y, TEXT, false); y += font.lineHeight;
                    }
                    String status = publicNode.learned() ? "+" : publicNode.availability() == ResearchTreeGraph.Availability.AVAILABLE ? "o" : "-";
                    g.drawString(font, status, (int) b.right() - 7, (int) b.y() + 3, color(publicNode), false);
                }
            }
            g.flush();
        } finally { g.pose().popPose(); g.disableScissor(); }
    }

    private void drawInspector(GuiGraphics g) {
        var box = boxes.inspector();
        g.fill(box.x(), box.y(), box.right(), box.bottom(), PANEL);
        g.fill(box.x(), box.y(), box.x() + 1, box.bottom(), BORDER);
        g.enableScissor(box.x() + 1, box.y(), box.right() - 1, box.bottom());
        try {
            var node = projection == null || selected == null ? null : projection.graph().node(selected).orElse(null);
            int header = node == null ? 28 : Math.min(64, box.height() / 3 + 12);
            if (node == null) {
                g.drawString(font, "RESEARCH", box.x() + 10, box.y() + 12, ACCENT, false);
            } else {
                icons.render(g, node, new ResearchTreeScene.Bounds(box.x() + 8, box.y() + 10, 44, Math.max(16, header - 20)));
                int y = box.y() + 10;
                for (String line : wrap(name(node), box.width() - 66)) {
                    if (y + font.lineHeight > box.y() + header - 3) break;
                    g.drawString(font, line, box.x() + 60, y, TEXT, false); y += font.lineHeight;
                }
            }
            g.flush();
            int top = box.y() + header, bottom = box.bottom() - 16;
            g.enableScissor(box.x() + 6, top, box.right() - 7, Math.max(top + 1, bottom));
            int lineHeight = font.lineHeight + 4;
            int capacity = Math.max(1, (bottom - top) / lineHeight);
            scroll = Math.max(0, Math.min(scroll, Math.max(0, inspectorLines.size() - capacity)));
            for (int i = scroll, y = top; i < inspectorLines.size() && y < bottom; i++, y += lineHeight) {
                var line = inspectorLines.get(i); g.drawString(font, line.text(), box.x() + 10, y, line.color(), false);
            }
            g.disableScissor();
            if (inspectorLines.size() > capacity) {
                int track = Math.max(8, bottom - top), thumb = Math.max(8, track * capacity / inspectorLines.size());
                int y = top + (track - thumb) * scroll / Math.max(1, inspectorLines.size() - capacity);
                g.fill(box.right() - 4, y, box.right() - 2, y + thumb, MUTED);
            }
            g.drawString(font, "Read-only preview", box.x() + 8, box.bottom() - 11, MUTED, false);
        } finally { g.flush(); g.disableScissor(); }
    }

    private void inspector() {
        inspectorLines.clear(); scroll = 0;
        if (selected == null || projection.graph().node(selected).isEmpty()) {
            add("Choose a blueprint", TEXT);
            add("Inspect its requirements, then focus its branch or target.", MUTED);
            add("Branches keep their space as you pan and zoom.", MUTED);
            add("Green: learned", 0xff7ccba3); add("Gold: available in public state", ACCENT); add("Slate: locked or unavailable", MUTED);
            add("This development comparison uses a fixed public snapshot.", MUTED);
            return;
        }
        var node = projection.graph().node(selected).orElseThrow();
        int header = Math.min(64, boxes.inspector().height() / 3 + 12);
        if (wrap(name(node), boxes.inspector().width() - 66).size() * font.lineHeight > header - 13)
            add(name(node), TEXT);
        add(status(node), color(node));
        if (node.visibility().revealsResearchSummary()) {
            add("Published cost: " + node.pointCost() + " RP", TEXT);
            add(node.ingredientTypeCount() + " material types", MUTED);
        }
        if (scene != null) ResearchTreePrototypeModel.branch(scene, selected).ifPresent(r -> add(regionTitle(r), ACCENT));
        var groups = projection.graph().requirementGroups().stream().filter(g -> g.dependentId().equals(selected)).toList();
        if (groups.isEmpty()) add("No prerequisite groups", MUTED);
        else add("All groups required:", TEXT);
        for (var group : groups) {
            add((group.ordinal() + 1) + ". Choose one:", ACCENT);
            for (var id : group.visibleAlternativeIds()) {
                var alternative = projection.graph().node(id).orElseThrow();
                add((alternative.learned() ? "+ " : "- ") + name(alternative), alternative.learned() ? 0xff7ccba3 : TEXT);
            }
            if (group.hiddenAlternativeCount() > 0) add(group.hiddenAlternativeCount() + " undisclosed alternative(s)", MUTED);
            if (group.externalAlternativeCount() > 0) add(group.externalAlternativeCount() + " external alternative(s)", MUTED);
        }
        var links = projection.boundaryLinks().stream().filter(l -> l.localNodeId().equals(selected)).toList();
        if (!links.isEmpty()) {
            add("Linked domains", ACCENT);
            for (var domain : links.stream().map(ResearchTechTreeProjection.BoundaryLink::remoteDomain).distinct().toList())
                add(domain.name() + ": " + links.stream().filter(l -> l.remoteDomain() == domain).count() + " disclosed links", MUTED);
            add("Domain links do not identify which OR group they satisfy.", MUTED);
        }
        add("State comes from the public snapshot; it is not a purchase preview.", MUTED);
    }

    private void add(String value, int color) { for (String line : wrap(value, boxes.inspector().width() - 24)) inspectorLines.add(new Line(line, color)); }
    private List<String> wrap(String value, int width) {
        var result = new ArrayList<String>(); String rest = value;
        while (!rest.isEmpty()) {
            String part = font.plainSubstrByWidth(rest, Math.max(10, width));
            if (part.isEmpty()) part = rest.substring(0, 1);
            if (part.length() < rest.length() && part.lastIndexOf(' ') > part.length() / 2) part = part.substring(0, part.lastIndexOf(' '));
            result.add(part); rest = rest.substring(part.length()).stripLeading();
        }
        if (result.isEmpty()) result.add("");
        return result;
    }
    private String ellipsis(String text, int width) { return font.width(text) <= width ? text : font.plainSubstrByWidth(text, Math.max(0, width - font.width("..."))) + "..."; }
    private String name(ResearchTreeGraph.Node node) {
        String translated = Component.translatable(node.nameKey()).getString();
        return translated.equals(node.nameKey()) ? node.blueprintId().getPath().replace('_', ' ') : translated;
    }
    private String regionTitle(ResearchTreeScene.Region region) {
        String id = region.seed().id();
        if (id.equals("automatic/shared")) return "Shared foundations";
        if (id.startsWith("automatic/family/")) return "Branch " + (region.seed().order() + 1);
        if (id.startsWith("component/")) return "Branch " + (region.seed().order() + 1);
        return id.replace('_', ' ');
    }
    private int color(ResearchTreeGraph.Node n) { return n.learned() ? 0xff7ccba3 : n.availability() == ResearchTreeGraph.Availability.AVAILABLE ? ACCENT : 0xff8a9da8; }
    private String status(ResearchTreeGraph.Node n) {
        return switch (n.availability()) {
            case LEARNED -> "Learned";
            case AVAILABLE -> "Available in public state";
            case PREREQUISITES_REQUIRED -> "Prerequisites required";
            case DISCOVERY_REQUIRED -> "Discover this blueprint";
            case RESEARCH_DISABLED -> "Research disabled";
            case COST_ABOVE_CAP -> "Cost unavailable";
            case CONTENT_UNAVAILABLE -> "Content unavailable";
            case REDACTED -> "Undisclosed";
            case PREVIEW -> "Preview";
        };
    }
    private static void fill(GuiGraphics g, ResearchTreeScene.Bounds b, int color) { g.fill((int) b.x(), (int) b.y(), (int) Math.ceil(b.right()), (int) Math.ceil(b.bottom()), color); }
    private static void border(GuiGraphics g, ResearchTreeScene.Bounds b, int color, int thickness) {
        int x = (int) b.x(), y = (int) b.y(), r = (int) Math.ceil(b.right()), bottom = (int) Math.ceil(b.bottom());
        g.fill(x, y, r, y + thickness, color); g.fill(x, bottom - thickness, r, bottom, color);
        g.fill(x, y, x + thickness, bottom, color); g.fill(r - thickness, y, r, bottom, color);
    }

    @Override public boolean mouseClicked(double x, double y, int button) {
        interruptCapture();
        if (button == 0) for (int i = 0; i < 8; i++) if (button(i).contains(x, y)) { action(i); return true; }
        if (!boxes.canvas().contains(x, y) || scene == null || button > 1) return boxes.inspector().contains(x, y);
        pressed = true; dragged = false; pressX = x; pressY = y;
        return true;
    }
    @Override public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        if (!pressed) return false;
        if (Math.hypot(x - pressX, y - pressY) > 3) dragged = true;
        if (dragged) { if (old) legacy.viewport().panByScreenDelta(dx, dy); else camera.pan(dx, dy); }
        return true;
    }
    @Override public boolean mouseReleased(double x, double y, int button) {
        if (!pressed) return false;
        pressed = false;
        if (!dragged && button == 0 && boxes.canvas().contains(x, y)) {
            ResourceLocation id = old ? legacy.nodeAt(x, y).map(ResearchTreeGraph.Node::blueprintId).orElse(null)
                    : ResearchTreePrototypeModel.hit(scene.nodes(), camera.worldX(x), camera.worldY(y), camera.scale()).map(ResearchTreeScene.Node::id).orElse(null);
            if (id != null) select(id, false);
        }
        return true;
    }
    @Override public boolean mouseScrolled(double x, double y, double delta) {
        interruptCapture();
        if (boxes.inspector().contains(x, y)) { scroll -= (int) Math.signum(delta) * 3; return true; }
        if (!boxes.canvas().contains(x, y) || scene == null) return false;
        if (old) legacy.mouseScrolled(x, y, delta); else camera.zoom(delta, x, y);
        return true;
    }
    @Override public boolean keyPressed(int key, int scanCode, int modifiers) {
        interruptCapture();
        if (key == GLFW.GLFW_KEY_F9) { capture = new ResearchTreePrototypeCapture(this); return true; }
        if (key == GLFW.GLFW_KEY_D) { domainIndex++; selected = null; rebuild(true); return true; }
        if (key == GLFW.GLFW_KEY_O) { action(0); return true; }
        if (key == GLFW.GLFW_KEY_B) { action(1); return true; }
        if (key == GLFW.GLFW_KEY_F) { action(2); return true; }
        return super.keyPressed(key, scanCode, modifiers);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { if (capture != null) capture.close(); ResearchTreePrototypeEvents.restoreWindow(); minecraft.setScreen(parent); }

    private void interruptCapture() { if (capture != null) { capture.close(); capture = null; } }

    private void select(ResourceLocation id, boolean focus) {
        selected = id; legacyState.focus(id); inspector();
        if (focus) action(2);
    }
    private void action(int action) {
        pressed = false;
        switch (action) {
            case 0 -> { if (scene != null) { if (old) legacy.viewport().fit(); else camera.fit(scene.extent(), 0); } }
            case 1 -> {
                if (scene == null) break;
                if (selected == null) selected = scene.nodes().get(0).id();
                if (old) legacy.focusNode(selected);
                else ResearchTreePrototypeModel.branch(scene, selected).ifPresent(r -> camera.fit(r.bounds(), 0.6));
                inspector();
            }
            case 2 -> {
                if (scene == null) break;
                if (selected == null) selected = scene.nodes().get(0).id();
                if (old) { legacy.viewport().restore(new ResearchTreeViewport.Snapshot(0, 0, 1)); legacy.focusNode(selected); }
                else scene.nodes().stream().filter(n -> n.id().equals(selected)).findFirst().ifPresent(n -> camera.focus(n.body(), 1));
                inspector();
            }
            case 3 -> { treatment = treatment == ResearchTreePrototypeModel.Treatment.TILE ? ResearchTreePrototypeModel.Treatment.CARD : ResearchTreePrototypeModel.Treatment.TILE; rebuild(false); action(2); }
            case 4 -> { old = !old; action(selected == null ? 0 : 2); }
            case 5 -> quiet = !quiet;
            case 6 -> { sourceIndex = (sourceIndex + 1) % sources.size(); selected = null; domainIndex = 0; rebuild(true); }
            case 7 -> onClose();
        }
    }

    void captureStage(int stage) {
        switch (stage) {
            case 0 -> { sourceIndex = 0; domainIndex = 0; selected = null; treatment = ResearchTreePrototypeModel.Treatment.TILE;
                old = false; quiet = true; rebuild(true); }
            case 1 -> action(0);
            case 2 -> {
                if (scene != null) select(projection.graph().requirementGroups().stream().filter(g -> g.visibleAlternativeIds().size() > 1)
                        .map(ResearchTreeGraph.RequirementGroup::dependentId).findFirst().orElse(scene.nodes().get(0).id()), true);
            }
            case 3 -> action(1);
            case 4 -> { treatment = ResearchTreePrototypeModel.Treatment.CARD; rebuild(false); action(2); }
            case 5 -> action(0);
            case 6 -> { old = true; action(0); }
            case 7 -> action(2);
            case 8 -> { old = false; quiet = false; action(2); }
            case 9 -> { quiet = true; treatment = ResearchTreePrototypeModel.Treatment.TILE; sample("nested_split_merge"); action(0); }
            case 10 -> { sample("anonymous_groups"); if (scene != null) select(scene.nodes().get(scene.nodes().size() - 1).id(), true); }
            case 11 -> { sample("dense_256"); action(0); }
            case 12 -> { sourceIndex = 0; domainIndex = 0; selected = null; rebuild(true); }
        }
    }
    private void sample(String name) {
        for (int i = 0; i < sources.size(); i++) if (sources.get(i).input().name().equals(name)) { sourceIndex = i; break; }
        selected = null; domainIndex = 0; rebuild(true);
    }
    void control(String action, String value) {
        interruptCapture();
        switch (action) {
            case "capture" -> capture = new ResearchTreePrototypeCapture(this);
            case "snapshot" -> { if (!value.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("invalid snapshot name"); pendingSnapshot = value; }
            case "overview" -> action(0);
            case "branch" -> action(1);
            case "target" -> action(2);
            case "tile" -> { treatment = ResearchTreePrototypeModel.Treatment.TILE; rebuild(false); action(2); }
            case "card" -> { treatment = ResearchTreePrototypeModel.Treatment.CARD; rebuild(false); action(2); }
            case "current" -> { old = true; action(2); }
            case "new" -> { old = false; action(2); }
            case "quiet" -> quiet = true;
            case "world" -> quiet = false;
            case "sample" -> sample(value);
            case "select" -> {
                var id = new ResourceLocation(value);
                if (projection.graph().node(id).isEmpty()) throw new IllegalArgumentException("node is not in this public sample");
                select(id, true);
            }
            case "scroll-inspector" -> scroll += Integer.parseInt(value);
            case "reload" -> minecraft.reloadResourcePacks();
            default -> throw new IllegalArgumentException("unknown prototype command");
        }
    }
    JsonObject evidence() {
        var result = new JsonObject();
        result.addProperty("source", sources.get(sourceIndex).input().name());
        result.addProperty("public_input_sha256", sources.get(sourceIndex).input().sha256());
        result.addProperty("treatment", treatment.name()); result.addProperty("current_canvas", old); result.addProperty("quiet_surface", quiet);
        result.addProperty("world_available", minecraft.level != null);
        result.addProperty("gui_width", width); result.addProperty("gui_height", height);
        result.addProperty("framebuffer_width", minecraft.getWindow().getWidth()); result.addProperty("framebuffer_height", minecraft.getWindow().getHeight());
        result.addProperty("gui_scale", minecraft.getWindow().getGuiScale());
        result.addProperty("selected", selected == null ? "" : selected.toString());
        result.addProperty("font_line_height", font.lineHeight); result.addProperty("label_width", labelWidth); result.addProperty("names_over_two_lines", clippedNames);
        result.addProperty("slot_art_resolved", icons.resolved()); result.addProperty("rebuild_including_both_engines_ms", buildMs);
        result.addProperty("failure", failure); result.add("layout", ResearchTreeBaselineInput.JSON.toJsonTree(boxes));
        if (scene != null) {
            result.add("new_scene_node_metrics", ResearchTreeBaselineInput.JSON.toJsonTree(scene.key().policy().nodeMetrics()));
            result.add("new_scene_extent", ResearchTreeBaselineInput.JSON.toJsonTree(scene.extent()));
            result.add("displayed_extent", ResearchTreeBaselineInput.JSON.toJsonTree(old
                    ? new ResearchTreeScene.Bounds(0, 0, legacy.layout().width(), legacy.layout().height()) : scene.extent()));
            result.addProperty("displayed_node_width", old ? 24 : scene.key().policy().nodeMetrics().width());
            result.addProperty("displayed_node_height", old ? 24 : scene.key().policy().nodeMetrics().height());
            result.addProperty("nodes", scene.nodes().size()); result.addProperty("regions", scene.regions().size());
            result.addProperty("connections", scene.routing().orElseThrow().connections().size());
            result.addProperty("crossings", scene.routing().orElseThrow().crossings().size());
            result.addProperty("zoom", old ? legacy.viewport().scale() : camera.scale());
        }
        return result;
    }
}
