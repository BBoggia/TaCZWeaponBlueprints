package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.client.ResearchTreePresentationContract.RelationshipRole;

/** Priority applies to each routed connection, including shared AND/OR trunks. */
enum ResearchTreeConnectorStyle {
    QUIET(0xff435761, 1), ANCESTRY(0xff968767, 1), TRACKED(0xff62a6b2, 1),
    DIRECT(0xffecc76e, 2), REVIEW(0xffffd681, 3);
    final int color, thickness;
    ResearchTreeConnectorStyle(int color, int thickness) { this.color = color; this.thickness = thickness; }
    static ResearchTreeConnectorStyle resolve(boolean reviewed, boolean tracked, RelationshipRole role) {
        if (reviewed) return REVIEW;
        if (role == RelationshipRole.DIRECT_REQUIREMENT || role == RelationshipRole.ALTERNATIVE_REQUIREMENT) return DIRECT;
        if (tracked) return TRACKED;
        if (role == RelationshipRole.REQUIREMENT_PATH) return ANCESTRY;
        return QUIET;
    }
    ResearchTreeConnectorStyle strongest(ResearchTreeConnectorStyle other) {
        return ordinal() >= other.ordinal() ? this : other;
    }
}
