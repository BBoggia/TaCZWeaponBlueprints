package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

/** Pure geometry for the compact, explicit three-domain Tech Tree selector. */
public final class ResearchTechTreeDomainSelectorLayout {
    private ResearchTechTreeDomainSelectorLayout() {
    }

    /**
     * Divides the existing compact selector into the stable Weapons,
     * Attachments, Ammunition publication order without consuming more toolbar
     * space. Any remainder is assigned from left to right.
     */
    public static List<Entry> forBounds(ResearchTreeScreenLayout.Rect bounds) {
        if (bounds == null || bounds.width() < ResearchTechTreeContract.DOMAIN_ORDER.size()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree domain selector bounds are too small");
        }
        int count = ResearchTechTreeContract.DOMAIN_ORDER.size();
        int baseWidth = bounds.width() / count;
        int remainder = bounds.width() % count;
        int x = bounds.x();
        ArrayList<Entry> entries = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            int width = baseWidth + (index < remainder ? 1 : 0);
            entries.add(new Entry(
                    ResearchTechTreeContract.DOMAIN_ORDER.get(index),
                    new ResearchTreeScreenLayout.Rect(
                            x, bounds.y(), width, bounds.height())));
            x += width;
        }
        return List.copyOf(entries);
    }

    public record Entry(Domain domain, ResearchTreeScreenLayout.Rect bounds) {
        public Entry {
            if (domain == null || bounds == null) {
                throw new IllegalArgumentException(
                        "Research Tech Tree domain selector entry is invalid");
            }
        }
    }
}
