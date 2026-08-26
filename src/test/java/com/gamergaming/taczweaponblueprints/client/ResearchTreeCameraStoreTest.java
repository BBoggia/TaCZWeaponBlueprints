package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeCameraStoreTest {
    @Test
    void atlasAndEachBranchRestoreIndependentCameras() {
        ResearchTreeCameraStore store = new ResearchTreeCameraStore();
        ResearchTreeViewport viewport = viewport();
        ResearchTreeCameraStore.Key atlas = key(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS, null);
        ResearchTreeCameraStore.Key first = key(
                ResearchTreePresentationContract.BrowseView.BRANCHES, "test:first");
        ResearchTreeCameraStore.Key second = key(
                ResearchTreePresentationContract.BrowseView.BRANCHES, "test:second");

        viewport.zoomAt(1.0D, 60, 40);
        store.save(first, viewport);
        double firstScale = viewport.scale();
        viewport.fit();
        store.save(atlas, viewport);
        viewport.zoomAt(1.0D, 60, 40);
        viewport.zoomAt(1.0D, 60, 40);
        store.save(second, viewport);

        viewport.fit();
        assertTrue(store.restore(first, viewport));
        assertEquals(firstScale, viewport.scale(), 0.0001D);
        assertTrue(store.restore(atlas, viewport));
        assertNotEquals(firstScale, viewport.scale());
        assertEquals(3, store.size());
        store.clear();
        assertFalse(store.restore(first, viewport));
    }

    @Test
    void keyRejectsAnAtlasScopedToAGroup() {
        assertThrows(IllegalArgumentException.class, () ->
                new ResearchTreeCameraStore.Key(
                        ResearchTreeScreenLayout.ViewMode.FULLSCREEN,
                        ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                        Optional.of(new ResourceLocation("test:first"))));
    }

    @Test
    void savingDuringEasingRestoresTheIntendedFinalCamera() {
        ResearchTreeCameraStore store = new ResearchTreeCameraStore();
        ResearchTreeViewport viewport = viewport();
        viewport.setAnimated(true);
        viewport.focus(420, 250, 32, 32);
        ResearchTreeViewport.Snapshot target = viewport.snapshot();

        store.save(key(ResearchTreePresentationContract.BrowseView.BRANCHES, "test:first"), viewport);
        viewport.cancelAnimation();
        assertTrue(store.restore(
                key(ResearchTreePresentationContract.BrowseView.BRANCHES, "test:first"),
                viewport));

        assertEquals(target, viewport.snapshot());
        assertFalse(viewport.isAnimating());
    }

    private static ResearchTreeViewport viewport() {
        ResearchTreeViewport viewport = new ResearchTreeViewport();
        viewport.configure(120, 80, 600, 400);
        return viewport;
    }

    private static ResearchTreeCameraStore.Key key(
            ResearchTreePresentationContract.BrowseView view,
            String groupId) {
        return new ResearchTreeCameraStore.Key(
                ResearchTreeScreenLayout.ViewMode.FULLSCREEN,
                view,
                groupId == null
                        ? Optional.empty()
                        : Optional.of(new ResourceLocation(groupId)));
    }
}
