package com.gamergaming.taczweaponblueprints.client;

/** Geometry-only settings. Measured dimensions belong here; text and textures do not. */
public record ResearchTreeScenePolicy(
        ResearchTechTreeLayoutPolicy spacing, int viewportWidth, NodeMetrics nodeMetrics,
        String metricsRevision, RegionMetrics regionMetrics) {
    public static final int MAX_DIMENSION = 1_000_000;
    public static final int MAX_ROUTE_POINTS = 64;
    public static final int MAX_TOTAL_ROUTE_POINTS = 1_048_576;

    public ResearchTreeScenePolicy(ResearchTechTreeLayoutPolicy spacing, int viewportWidth,
            NodeMetrics nodeMetrics, String metricsRevision) {
        this(spacing, viewportWidth, nodeMetrics, metricsRevision, RegionMetrics.DEFAULT);
    }

    public ResearchTreeScenePolicy {
        if (spacing == null || viewportWidth < 1 || nodeMetrics == null || regionMetrics == null
                || metricsRevision == null || metricsRevision.isBlank() || metricsRevision.length() > 128) {
            throw new IllegalArgumentException("invalid scene geometry policy");
        }
    }

    /** Reserved space; no inferred region titles or font handles are stored in the policy. */
    public record RegionMetrics(int gap, int padding, int headerHeight, int labelWidth,
            int routingGutter, int junctionSpacing, int routingTrackSpacing) {
        public static final RegionMetrics DEFAULT = new RegionMetrics(40, 12, 12, 80, 16, 10);
        public RegionMetrics(int gap, int padding, int headerHeight, int labelWidth, int routingGutter, int junctionSpacing) {
            this(gap, padding, headerHeight, labelWidth, routingGutter, junctionSpacing, 3);
        }
        public RegionMetrics {
            if (gap < 8 || gap > 512 || padding < 1 || padding > 128
                    || headerHeight < 0 || headerHeight > 128 || labelWidth < 0 || labelWidth > 2048
                    || routingGutter < 4 || routingGutter > 128 || junctionSpacing < 8 || junctionSpacing > 64
                    || routingTrackSpacing < 2 || routingTrackSpacing > 6) {
                throw new IllegalArgumentException("invalid scene region metrics");
            }
        }
    }

    public static ResearchTreeScenePolicy compatibility(ResearchTechTreeLayoutPolicy spacing, int width) {
        return new ResearchTreeScenePolicy(spacing, width, NodeMetrics.COMPATIBILITY, "compact-v1");
    }

    /** Paint padding reserves badges, borders, and focus rings, independently of the body. */
    public record NodeMetrics(double width, double height, double paintPadding) {
        public static final NodeMetrics COMPATIBILITY = new NodeMetrics(24, 24, 4);

        public NodeMetrics {
            if (!Double.isFinite(width) || !Double.isFinite(height) || !Double.isFinite(paintPadding)
                    || width < 1 || height < 1 || width > 512 || height > 512
                    || paintPadding < 0 || paintPadding > 32) {
                throw new IllegalArgumentException("invalid scene node metrics");
            }
        }
    }
}
