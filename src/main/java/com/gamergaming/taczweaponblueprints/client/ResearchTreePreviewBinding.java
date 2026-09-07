package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;

/** Binds preview receipt to a publication and invalidates it on replacement. */
final class ResearchTreePreviewBinding {
    private Object publication;
    private ResearchSelectionPreview observed;
    private boolean current;

    ResearchSelectionPreview resolve(Object nextPublication, ResearchSelectionPreview nextPreview) {
        if (nextPublication == null || nextPreview == null) {
            throw new IllegalArgumentException("preview binding requires identities");
        }
        if (nextPreview != observed) {
            current = true;
        } else if (nextPublication != publication) {
            current = false;
        }
        publication = nextPublication;
        observed = nextPreview;
        return current ? nextPreview : ResearchSelectionPreview.EMPTY;
    }

    void clear() {
        publication = null;
        observed = null;
        current = false;
    }
}
