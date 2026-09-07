package com.gamergaming.taczweaponblueprints.client;

import java.util.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeLayout;
import net.minecraft.resources.ResourceLocation;

/** View geometry over one validated scene. The compatibility positions are navigation centers only. */
final class ResearchTreeSceneView {
    final ResearchTreeScene scene;
    final double offsetX, offsetY;
    final ResearchTreeLayout navigation;
    private final Map<ResourceLocation, ResearchTreeScene.Node> nodes = new LinkedHashMap<>();
    private final List<ResearchTreeScene.Node> sorted;
    private final double[] maximumRight;

    ResearchTreeSceneView(ResearchTreeScene scene) {
        this(scene, scene.nodes().stream().map(ResearchTreeScene.Node::id).toList());
    }
    ResearchTreeSceneView(ResearchTreeScene scene, List<ResourceLocation> publicOrder) {
        this.scene = Objects.requireNonNull(scene);
        offsetX = -scene.extent().x(); offsetY = -scene.extent().y();
        for (var node : scene.nodes()) nodes.put(node.id(), node);
        sorted = scene.nodes().stream().sorted(Comparator.comparingDouble(n -> n.paint().x())).toList();
        maximumRight = new double[sorted.size()];
        double right = -Double.MAX_VALUE;
        for (int i = 0; i < sorted.size(); i++) maximumRight[i] = right = Math.max(right, sorted.get(i).paint().right());
        var positions = new ArrayList<ResearchTreeLayout.PositionedNode>();
        if (publicOrder.size() != nodes.size() || !new HashSet<>(publicOrder).equals(nodes.keySet()))
            throw new IllegalArgumentException("scene navigation membership differs from public graph");
        for (int i = 0; i < publicOrder.size(); i++) {
            var n = nodes.get(publicOrder.get(i));
            positions.add(new ResearchTreeLayout.PositionedNode(i, n.id(), 0, 0, i,
                    Math.max(0, (int) Math.round(n.body().centerX() + offsetX - 12)),
                    Math.max(0, (int) Math.round(n.body().y() + n.body().height() / 2 + offsetY - 12))));
        }
        navigation = positions.isEmpty() ? ResearchTreeLayout.EMPTY : new ResearchTreeLayout(
                (int) Math.ceil(scene.extent().width()), (int) Math.ceil(scene.extent().height()), 1, positions);
    }

    Optional<ResearchTreeScene.Bounds> body(ResourceLocation id) {
        return Optional.ofNullable(nodes.get(id)).map(n -> canvas(n.body()));
    }
    ResearchTreeScene.Bounds canvas(ResearchTreeScene.Bounds b) {
        return new ResearchTreeScene.Bounds(b.x() + offsetX, b.y() + offsetY, b.width(), b.height());
    }
    List<ResearchTreeScene.Node> query(double left, double top, double right, double bottom) {
        int lo = 0, hi = sorted.size();
        while (lo < hi) { int mid = (lo + hi) >>> 1; if (sorted.get(mid).paint().x() <= right) lo = mid + 1; else hi = mid; }
        var result = new ArrayList<ResearchTreeScene.Node>();
        for (int i = lo - 1; i >= 0 && maximumRight[i] >= left; i--) {
            var n = sorted.get(i); var b = n.paint();
            if (b.right() >= left && b.bottom() >= top && b.y() <= bottom) result.add(n);
        }
        return result;
    }
    Optional<ResourceLocation> hit(double x, double y, double scale) {
        x -= offsetX; y -= offsetY;
        final double px = x, py = y, padding = Math.min(32, 4 / scale);
        var order = Comparator.comparingDouble((ResearchTreeScene.Node n) -> Math.pow(n.body().centerX() - px, 2)
                + Math.pow(n.body().y() + n.body().height() / 2 - py, 2)).thenComparing(n -> n.id().toString());
        var candidates = query(x - padding, y - padding, x + padding, y + padding);
        var body = candidates.stream().filter(n -> contains(n.body(), px, py, 0)).min(order);
        return body.or(() -> candidates.stream().filter(n -> contains(n.body(), px, py, padding)).min(order)).map(ResearchTreeScene.Node::id);
    }
    static boolean contains(ResearchTreeScene.Bounds b, double x, double y, double padding) {
        return x >= b.x() - padding && x < b.right() + padding && y >= b.y() - padding && y < b.bottom() + padding;
    }
    Optional<ResearchTechTreeLayout.PortalTarget> portal(double x, double y, double scale) {
        if (hit(x, y, scale).isPresent()) return Optional.empty();
        for (var boundary : scene.boundaries()) {
            if (!contains(canvas(boundary.bounds()), x, y, Math.min(16, 3 / scale))) continue;
            var first = boundary.links().get(0);
            return Optional.of(new ResearchTechTreeLayout.PortalTarget(first.localNodeId(), first.remoteDomain(), first.direction(), boundary.links()));
        }
        return Optional.empty();
    }
    Optional<ResearchTreeScene.Bounds> branch(ResourceLocation id) {
        return scene.regions().stream().filter(r -> r.seed().directMembers().contains(id)).findFirst().map(r -> canvas(r.bounds()));
    }
    static ResearchTreeScene.Bounds fitArt(double sourceWidth, double sourceHeight, ResearchTreeScene.Bounds target) {
        if (sourceWidth <= 0 || sourceHeight <= 0) throw new IllegalArgumentException("empty artwork");
        double scale = Math.min(target.width() / sourceWidth, target.height() / sourceHeight);
        double w = sourceWidth * scale, h = sourceHeight * scale;
        return new ResearchTreeScene.Bounds(target.x() + (target.width() - w) / 2, target.y() + (target.height() - h) / 2, w, h);
    }
}
