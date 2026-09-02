package com.gamergaming.taczweaponblueprints.client;

/** Coalesces unchanged screen snapshots into no-op widget refreshes. */
final class ResearchTreeUiUpdateController {
    private Object previousWidgetSnapshot;
    private boolean widgetsInvalid = true;

    boolean shouldRefreshWidgets(Object snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("Research Tree widget snapshot cannot be null");
        }
        if (!widgetsInvalid && snapshot.equals(previousWidgetSnapshot)) {
            return false;
        }
        previousWidgetSnapshot = snapshot;
        widgetsInvalid = false;
        return true;
    }

    void invalidateWidgets() {
        widgetsInvalid = true;
    }
}
