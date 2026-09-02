package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;

import net.minecraft.resources.ResourceLocation;

/** Pure cached presentation model for the fullscreen selected-node card. */
final class ResearchTreeContextCardPresenter {
    private Input previousInput;
    private Presentation previousPresentation;

    Presentation present(Input input) {
        if (input == null) {
            throw new IllegalArgumentException("Research Tree context card input cannot be null");
        }
        if (input.equals(previousInput)) {
            return previousPresentation;
        }
        boolean exactPreview = ResearchTreeContextCardPolicy.hasMatchingAuthoritativePreview(
                input.pinnedNodeId(), input.authoritativeSelection(), input.preview());
        ResearchTreeContextCardLayout.Layout layout = ResearchTreeContextCardLayout.place(
                input.screenWidth(),
                input.screenHeight(),
                input.anchor(),
                input.obstacles(),
                exactPreview ? input.preview().ingredients().size() : 0,
                exactPreview);
        ArrayList<ResearchTreeScreenLayout.Rect> visibilityObstacles =
                new ArrayList<>(input.obstacles());
        visibilityObstacles.add(layout.card());
        boolean anchorVisible = ResearchTreeContextCardLayout.isAnchorVisible(
                input.anchor(),
                input.screenWidth(),
                input.screenHeight(),
                visibilityObstacles);
        previousInput = input;
        previousPresentation = new Presentation(
                layout,
                exactPreview,
                layout.action() != null,
                !anchorVisible);
        return previousPresentation;
    }

    void invalidate() {
        previousInput = null;
        previousPresentation = null;
    }

    record Input(
            int screenWidth,
            int screenHeight,
            ResourceLocation pinnedNodeId,
            Optional<ResourceLocation> authoritativeSelection,
            ResearchSelectionPreview preview,
            ResearchTreeContextCardLayout.Anchor anchor,
            List<ResearchTreeScreenLayout.Rect> obstacles) {
        Input {
            if (screenWidth <= 0 || screenHeight <= 0 || pinnedNodeId == null
                    || authoritativeSelection == null || preview == null
                    || anchor == null || obstacles == null
                    || obstacles.stream().anyMatch(java.util.Objects::isNull)) {
                throw new IllegalArgumentException("invalid Research Tree context card input");
            }
            obstacles = List.copyOf(obstacles);
        }
    }

    record Presentation(
            ResearchTreeContextCardLayout.Layout layout,
            boolean exactPreview,
            boolean actionVisible,
            boolean returnActionVisible) {
        Presentation {
            if (layout == null || actionVisible != (layout.action() != null)) {
                throw new IllegalArgumentException("invalid Research Tree context card presentation");
            }
        }
    }
}
