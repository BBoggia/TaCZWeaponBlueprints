package com.gamergaming.taczweaponblueprints.client;

/** Canvas-wide status, independent of selection and inspector visibility. */
final class ResearchTreeCanvasStatusPresenter {
    private ResearchTreeCanvasStatusPresenter() { }

    static State present(Input input) {
        if (input.rejected()) return State.REJECTED;
        if (!input.publicationReceived()) return State.LOADING;
        if (input.bindingFailed() || input.sceneStatus() == ResearchTreeSceneCache.Status.UNAVAILABLE)
            return State.UNAVAILABLE;
        if (input.publicationEmpty()) return State.EMPTY_PUBLICATION;
        if (!input.catalogAvailable()) return State.TECH_TREE_UNAVAILABLE;
        if (input.domainEmpty()) return State.EMPTY_DOMAIN;
        if (input.sceneStatus() == null) return State.LOADING;
        if (input.searchActive() && input.matchCount() == 0) return State.NO_SEARCH_MATCHES;
        return State.NONE;
    }

    record Input(boolean rejected, boolean publicationReceived, boolean bindingFailed,
            boolean publicationEmpty, boolean catalogAvailable, boolean domainEmpty,
            ResearchTreeSceneCache.Status sceneStatus, boolean searchActive, int matchCount) {
        Input {
            if (matchCount < 0) throw new IllegalArgumentException("negative search count");
        }
    }

    enum Action { NONE, CLEAR_SEARCH, CLOSE_BENCH }

    enum State {
        NONE("", Action.NONE),
        LOADING("tree.scene.loading", Action.NONE),
        REJECTED("tree.display_failed", Action.CLOSE_BENCH),
        UNAVAILABLE("tree.scene.unavailable", Action.CLOSE_BENCH),
        EMPTY_PUBLICATION("search.unavailable", Action.NONE),
        TECH_TREE_UNAVAILABLE("tree.tech_tree.empty", Action.CLOSE_BENCH),
        EMPTY_DOMAIN("tree.scene.empty_domain", Action.NONE),
        NO_SEARCH_MATCHES("search.empty", Action.CLEAR_SEARCH);

        private final String suffix;
        private final Action action;
        State(String suffix, Action action) { this.suffix = suffix; this.action = action; }
        String messageKey() { return "gui.taczweaponblueprints.research_bench." + suffix; }
        Action action() { return action; }
        boolean visible() { return this != NONE; }
    }
}
