package com.gamergaming.taczweaponblueprints.client;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Pure geometry and interaction rules for the development prototype. */
final class ResearchTreePrototypeModel {
    enum Treatment { TILE, CARD }
    enum Detail { OVERVIEW, BROWSE }
    record Layout(ResearchTreeScreenLayout.Rect canvas, ResearchTreeScreenLayout.Rect inspector, int toolbarColumns) { }

    static Layout layout(int width, int height) {
        int columns = Math.max(2, Math.min(8, (width - 16) / 50));
        int top = 25 + ((8 + columns - 1) / columns) * 22;
        int usable = Math.max(48, height - top - 22);
        if (width >= 360) {
            int inspector = Math.min(218, Math.max(160, width * 36 / 100));
            return new Layout(new ResearchTreeScreenLayout.Rect(8, top, width - inspector - 28, usable),
                    new ResearchTreeScreenLayout.Rect(width - inspector - 8, top, inspector, usable), columns);
        }
        int inspector = Math.min(110, Math.max(42, usable / 2));
        return new Layout(new ResearchTreeScreenLayout.Rect(8, top, width - 16, Math.max(24, usable - inspector - 6)),
                new ResearchTreeScreenLayout.Rect(8, top + Math.max(24, usable - inspector - 6) + 6, width - 16, inspector), columns);
    }

    static ResearchTreeScenePolicy.NodeMetrics metrics(Treatment treatment, int lineHeight, int medianNameWidth) {
        return treatment == Treatment.TILE
                ? new ResearchTreeScenePolicy.NodeMetrics(Math.max(76, Math.min(96, medianNameWidth + 12)), 26 + lineHeight * 2, 3)
                : new ResearchTreeScenePolicy.NodeMetrics(Math.max(132, Math.min(164, medianNameWidth + 62)), Math.max(36, 10 + lineHeight * 2), 3);
    }

    static Detail detail(Detail previous, double scale) {
        if (previous == Detail.BROWSE) return scale < 0.48 ? Detail.OVERVIEW : previous;
        return scale > 0.58 ? Detail.BROWSE : previous;
    }

    static Optional<ResearchTreeScene.Node> hit(List<ResearchTreeScene.Node> nodes, double x, double y, double scale) {
        var point = new ResearchTreeScene.Point(x, y);
        var order = Comparator.comparingDouble((ResearchTreeScene.Node n) -> Math.hypot(n.body().centerX() - x,
                n.body().y() + n.body().height() / 2 - y)).thenComparing(n -> n.id().toString());
        var body = nodes.stream().filter(n -> n.body().contains(point)).min(order);
        if (body.isPresent()) return body;
        double padding = Math.min(32, 4 / scale);
        return nodes.stream().filter(n -> n.hitBounds(padding).contains(point)).min(order);
    }

    static Optional<ResearchTreeScene.Region> branch(ResearchTreeScene scene, ResourceLocation id) {
        return scene.regions().stream().filter(r -> r.seed().directMembers().contains(id)).findFirst();
    }

    /** Aspect-preserving placement after transparent padding is removed. */
    static ResearchTreeScene.Bounds fitArt(double sourceWidth, double sourceHeight, ResearchTreeScene.Bounds target) {
        if (sourceWidth <= 0 || sourceHeight <= 0) throw new IllegalArgumentException("empty artwork");
        double scale = Math.min(target.width() / sourceWidth, target.height() / sourceHeight);
        double w = sourceWidth * scale, h = sourceHeight * scale;
        return new ResearchTreeScene.Bounds(target.x() + (target.width() - w) / 2, target.y() + (target.height() - h) / 2, w, h);
    }

    static final class Camera {
        private ResearchTreeScreenLayout.Rect view = new ResearchTreeScreenLayout.Rect(0, 0, 1, 1);
        private double x, y, scale = 1;
        void configure(ResearchTreeScreenLayout.Rect view) { this.view = view; normalize(); }
        double scale() { return scale; }
        double worldX(double screen) { return x + (screen - view.x() - view.width() / 2.0) / scale; }
        double worldY(double screen) { return y + (screen - view.y() - view.height() / 2.0) / scale; }
        double screenX(double world) { return view.x() + view.width() / 2.0 + (world - x) * scale; }
        double screenY(double world) { return view.y() + view.height() / 2.0 + (world - y) * scale; }
        void pan(double dx, double dy) { x -= dx / scale; y -= dy / scale; normalize(); }
        void focus(ResearchTreeScene.Bounds bounds, double scale) {
            this.scale = Math.max(0.00001, Math.min(1.8, scale));
            x = bounds.centerX(); y = bounds.y() + bounds.height() / 2;
            normalize();
        }
        void fit(ResearchTreeScene.Bounds bounds, double minimum) {
            focus(bounds, Math.max(minimum, Math.min(1, Math.min(Math.max(1, view.width() - 24) / Math.max(1, bounds.width()),
                    Math.max(1, view.height() - 24) / Math.max(1, bounds.height())))));
        }
        void zoom(double wheel, double mouseX, double mouseY) {
            double anchorX = worldX(mouseX), anchorY = worldY(mouseY);
            scale = Math.max(0.00001, Math.min(1.8, scale * Math.pow(1.18, Math.max(-8, Math.min(8, wheel)))));
            x += anchorX - worldX(mouseX); y += anchorY - worldY(mouseY);
            normalize();
        }
        private void normalize() {
            scale = Math.max(Math.max(view.width(), view.height()) / 999_000.0, Math.min(1.8, scale));
            double limitX = 999_999 - view.width() / scale / 2, limitY = 999_999 - view.height() / scale / 2;
            x = Math.max(-limitX, Math.min(limitX, x)); y = Math.max(-limitY, Math.min(limitY, y));
        }
        ResearchTreeScene.Bounds visible() {
            return new ResearchTreeScene.Bounds(worldX(view.x()), worldY(view.y()), view.width() / scale, view.height() / scale);
        }
    }
}
