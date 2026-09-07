package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/** Immutable segment index. Visibility never depends on a blueprint endpoint being on screen. */
public final class ResearchTreeRoutingIndex {
    public record Segment(int connection, int ordinal, ResearchTreeScene.Point from, ResearchTreeScene.Point to,
            ResearchTreeScene.Bounds bounds) {
        public boolean horizontal() { return from.y() == to.y(); }
    }
    private final List<Segment> segments;
    private final BoundsIndex<Segment> index;

    public ResearchTreeRoutingIndex(ResearchTreeRouting routing) {
        var values = new ArrayList<Segment>();
        for (int c = 0; c < routing.connections().size(); c++) {
            var points = routing.connections().get(c).points();
            for (int p = 1; p < points.size(); p++) values.add(new Segment(c, p - 1, points.get(p - 1), points.get(p),
                    bounds(points.get(p - 1), points.get(p)).inflate(ResearchTreeRouting.INK_PADDING)));
        }
        segments = List.copyOf(values);
        index = new BoundsIndex<>(segments.stream().map(s -> new Entry<>(s.bounds(), s)).toList());
    }

    public List<Segment> segments() { return segments; }
    public List<Segment> query(ResearchTreeScene.Bounds viewport) {
        var result = new ArrayList<Segment>();
        index.query(viewport, result);
        result.sort(Comparator.comparingInt(Segment::connection).thenComparingInt(Segment::ordinal));
        return List.copyOf(result);
    }

    /** Selected routes use the same spatial query, independent of endpoint and node culling. */
    public List<Segment> selected(ResearchTreeScene.Bounds viewport, Set<Integer> connections) {
        return query(viewport).stream().filter(s -> connections.contains(s.connection())).toList();
    }

    static ResearchTreeScene.Bounds bounds(ResearchTreeScene.Point a, ResearchTreeScene.Point b) {
        return new ResearchTreeScene.Bounds(Math.min(a.x(), b.x()), Math.min(a.y(), b.y()),
                Math.abs(a.x() - b.x()), Math.abs(a.y() - b.y()));
    }

    static boolean intersects(ResearchTreeScene.Bounds a, ResearchTreeScene.Bounds b) {
        return a.x() <= b.right() && a.right() >= b.x() && a.y() <= b.bottom() && a.bottom() >= b.y();
    }

    record Entry<T>(ResearchTreeScene.Bounds bounds, T value) { }

    /** Balanced bounds hierarchy shared by obstacle checks and viewport queries. */
    static final class BoundsIndex<T> {
        private final ResearchTreeScene.Bounds bounds;
        private final List<Entry<T>> leaf;
        private final BoundsIndex<T> left, right;
        BoundsIndex(List<Entry<T>> entries) {
            var values = new ArrayList<>(entries);
            ResearchTreeScene.Bounds extent = null;
            for (var entry : values) extent = extent == null ? entry.bounds() : extent.union(entry.bounds());
            bounds = extent;
            if (values.size() <= 8) {
                leaf = List.copyOf(values); left = null; right = null;
            } else {
                boolean byX = bounds.width() >= bounds.height();
                values.sort(Comparator.comparingDouble(e -> byX ? e.bounds().centerX() : e.bounds().y() + e.bounds().height() / 2));
                int mid = values.size() / 2;
                left = new BoundsIndex<>(values.subList(0, mid));
                right = new BoundsIndex<>(values.subList(mid, values.size()));
                leaf = List.of();
            }
        }
        void query(ResearchTreeScene.Bounds area, List<T> result) {
            if (bounds == null || !intersects(bounds, area)) return;
            if (left != null) { left.query(area, result); right.query(area, result); }
            else for (var entry : leaf) if (intersects(entry.bounds(), area)) result.add(entry.value());
        }
    }
}
