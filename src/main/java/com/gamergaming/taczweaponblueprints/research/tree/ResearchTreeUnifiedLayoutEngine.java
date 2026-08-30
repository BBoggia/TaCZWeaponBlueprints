package com.gamergaming.taczweaponblueprints.research.tree;

/**
 * Compatibility entry point for the skeleton-composed unified overview.
 *
 * <p>Projection caches should reuse their prepared skeleton catalog. This adapter keeps older
 * integrations source-compatible when only a publication is available.
 */
public final class ResearchTreeUnifiedLayoutEngine {
    public static final int PADDING = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW.canvasPadding();
    public static final int NODE_GAP = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW.nodeGap();
    public static final int TIER_GAP = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW.tierGap();
    public static final int COMPONENT_GAP = ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW.componentGap();

    private ResearchTreeUnifiedLayoutEngine() {
    }

    public static ResearchTreeLayout layout(ResearchTreePublication publication) {
        if (publication == null) {
            throw new IllegalArgumentException("research publication cannot be null");
        }
        publication = publication.legacyView();
        ResearchTreeGroupSkeletonCatalog skeletons = ResearchTreeGroupSkeletonBuilder.build(
                publication, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);
        return ResearchTreeOverviewLayoutComposer.compose(
                publication, skeletons, ResearchTreeLayoutPolicy.UNIFIED_OVERVIEW);
    }
}
