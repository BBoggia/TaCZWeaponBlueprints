package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.menu.ResearchSelectionPreview;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeContextCardPresenterTest {
    @Test
    void matchingInputsReusePlacementAndAuthorityModel() {
        ResearchTreeContextCardPresenter presenter = new ResearchTreeContextCardPresenter();
        ResearchSelectionPreview preview = ResearchTreeUxPhaseZeroFixture.readyPreview();
        ResourceLocation node = preview.blueprintId().orElseThrow();
        ResearchTreeContextCardPresenter.Input input = input(node, preview, 120);

        ResearchTreeContextCardPresenter.Presentation first = presenter.present(input);
        ResearchTreeContextCardPresenter.Presentation second = presenter.present(input);

        assertSame(first, second);
        assertTrue(first.exactPreview());
        assertTrue(first.actionVisible());

        ResearchTreeContextCardPresenter.Presentation moved =
                presenter.present(input(node, preview, 140));
        assertNotSame(first, moved);
        presenter.invalidate();
        assertNotSame(moved, presenter.present(input(node, preview, 140)));
    }

    @Test
    void unmatchedPreviewProducesTheExistingConciseCardWithoutAnAction() {
        ResearchTreeContextCardPresenter presenter = new ResearchTreeContextCardPresenter();
        ResearchSelectionPreview preview = ResearchTreeUxPhaseZeroFixture.readyPreview();
        ResourceLocation node = preview.blueprintId().orElseThrow();
        ResearchTreeContextCardPresenter.Input input = new ResearchTreeContextCardPresenter.Input(
                320,
                240,
                node,
                Optional.of(new ResourceLocation("test:other")),
                preview,
                new ResearchTreeContextCardLayout.Anchor(120, 80, 24, 24),
                List.of());

        ResearchTreeContextCardPresenter.Presentation presentation = presenter.present(input);

        assertFalse(presentation.exactPreview());
        assertFalse(presentation.actionVisible());
        assertThrows(IllegalArgumentException.class, () -> presenter.present(null));
    }

    private static ResearchTreeContextCardPresenter.Input input(
            ResourceLocation node,
            ResearchSelectionPreview preview,
            int x) {
        return new ResearchTreeContextCardPresenter.Input(
                320,
                240,
                node,
                Optional.of(node),
                preview,
                new ResearchTreeContextCardLayout.Anchor(x, 80, 24, 24),
                List.of());
    }
}
