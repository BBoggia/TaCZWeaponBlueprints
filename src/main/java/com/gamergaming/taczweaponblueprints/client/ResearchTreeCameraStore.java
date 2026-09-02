package com.gamergaming.taczweaponblueprints.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import net.minecraft.resources.ResourceLocation;

/** Client-only pan/zoom snapshots isolated by presentation mode and projection. */
public final class ResearchTreeCameraStore {
    private final Map<Key, ResearchTreeViewport.Snapshot> snapshots = new LinkedHashMap<>();

    public void save(Key key, ResearchTreeViewport viewport) {
        if (key == null || viewport == null) {
            throw new IllegalArgumentException("Research Tree camera inputs cannot be null");
        }
        snapshots.put(key, viewport.snapshot());
    }

    public boolean restore(Key key, ResearchTreeViewport viewport) {
        if (key == null || viewport == null) {
            throw new IllegalArgumentException("Research Tree camera inputs cannot be null");
        }
        ResearchTreeViewport.Snapshot snapshot = snapshots.get(key);
        if (snapshot == null) {
            return false;
        }
        viewport.restore(snapshot);
        return true;
    }

    public void clear() {
        snapshots.clear();
    }

    int size() {
        return snapshots.size();
    }

    public record Key(
            ResearchTreeScreenLayout.ViewMode mode,
            ResearchTreePresentationContract.BrowseView view,
            Optional<ResourceLocation> groupId) {
        public Key {
            if (mode == null || view == null) {
                throw new IllegalArgumentException("Research Tree camera identity cannot be null");
            }
            if (view == ResearchTreePresentationContract.BrowseView.TECH_TREE) {
                throw new IllegalArgumentException(
                        "Tech Tree cameras are isolated by domain in ResearchTechTreeViewState");
            }
            groupId = groupId == null ? Optional.empty() : groupId;
            if (view == ResearchTreePresentationContract.BrowseView.ALL_WEAPONS
                    && groupId.isPresent()) {
                throw new IllegalArgumentException("All Weapons camera cannot name a branch");
            }
        }
    }
}
