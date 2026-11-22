package com.gamergaming.taczweaponblueprints.client;

import java.util.Optional;
import net.minecraft.resources.ResourceLocation;

/** Correlates local browsing, accepted selection, and a purchase without overwriting newer input. */
final class ResearchSheetSelection {
    private Optional<ResourceLocation> selected = Optional.empty();
    private long revision;
    private int selectionRequest, researchRequest;
    private long researchRevision;
    private boolean confirmed, failed;

    Optional<ResourceLocation> selected() { return selected; }
    boolean confirmed() { return confirmed; }
    boolean failed() { return failed; }
    boolean researching() { return researchRequest != 0; }

    void restore(Optional<ResourceLocation> serverSelection) {
        if (revision == 0 && selected.isEmpty()) {
            selected = serverSelection;
            confirmed = selected.isPresent();
        }
    }

    void choose(ResourceLocation id) {
        selected = Optional.of(id);
        revision++;
        confirmed = false;
        failed = false;
        selectionRequest = 0;
    }

    void sentSelection(int request) {
        if (request < 1 || selected.isEmpty()) throw new IllegalArgumentException("missing selection request");
        selectionRequest = request;
        confirmed = false;
        failed = false;
    }

    boolean acceptSelection(int request, Optional<ResourceLocation> target, boolean successful) {
        if (selectionRequest == 0 || request != selectionRequest || !target.equals(selected)) return false;
        selectionRequest = 0;
        confirmed = successful;
        failed = !successful;
        return true;
    }

    void clearedPreview() {
        if (confirmed && selectionRequest == 0 && researchRequest == 0) clear();
    }

    void sentResearch(int request) {
        if (request < 1 || !confirmed || selected.isEmpty() || researching()) {
            throw new IllegalStateException("research requires a confirmed selection");
        }
        researchRequest = request;
        researchRevision = revision;
    }

    /** Empty means an unrelated reply; false means the user has moved on since this purchase. */
    Optional<Boolean> acceptResearch(int request, Optional<ResourceLocation> target, boolean successful) {
        if (request != researchRequest || researchRequest == 0) return Optional.empty();
        researchRequest = 0;
        boolean current = revision == researchRevision && selected.equals(target);
        if (current && successful) clear();
        return Optional.of(current);
    }

    void clear() {
        selectionRequest = 0;
        selected = Optional.empty();
        confirmed = false;
        failed = false;
        revision++;
    }
}
