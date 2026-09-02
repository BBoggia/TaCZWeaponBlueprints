package com.gamergaming.taczweaponblueprints.client;

/**
 * Keeps a rail label open while the pointer crosses from its icon into the
 * revealed label. The label does not claim its hidden area until the icon,
 * selection, or keyboard focus has revealed it.
 */
final class ResearchTreeRailHoverState {
    private boolean pointerReveal;

    void update(
            boolean visible,
            boolean selectedOrFocused,
            boolean pointerOverIcon,
            boolean pointerOverRevealedLabel) {
        pointerReveal = visible
                && (selectedOrFocused
                        || pointerOverIcon
                        || pointerReveal && pointerOverRevealedLabel);
    }

    boolean labelVisible(boolean visible, boolean selectedOrFocused) {
        return visible && (selectedOrFocused || pointerReveal);
    }

    boolean ownsRevealedLabel(
            boolean visible,
            boolean selectedOrFocused,
            boolean pointerOverRevealedLabel) {
        return labelVisible(visible, selectedOrFocused) && pointerOverRevealedLabel;
    }
}
