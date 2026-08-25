package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;

import net.minecraft.resources.ResourceLocation;

/** Client-local Branches/All Weapons selection with publication-safe fallback. */
public final class ResearchTreeNavigationState {
    private ResearchTreePresentationContract.BrowseView browseView =
            ResearchTreePresentationContract.DEFAULT_BROWSE_VIEW;
    private ResourceLocation selectedGroupId;

    public ResearchTreePresentationContract.BrowseView browseView() {
        return browseView;
    }

    public Optional<ResourceLocation> selectedGroupId() {
        return Optional.ofNullable(selectedGroupId);
    }

    public void retain(
            ResearchTreePresentation presentation,
            ResourceLocation preferredNodeId) {
        if (presentation == null) {
            throw new IllegalArgumentException("Research Tree presentation cannot be null");
        }
        if (selectedGroupId != null && presentation.group(selectedGroupId).isPresent()) {
            return;
        }
        selectedGroupId = presentation.membership(preferredNodeId)
                .map(ResearchTreePresentation.Membership::groupId)
                .orElseGet(() -> presentation.groups().isEmpty()
                        ? null
                        : presentation.groups().get(0).id());
    }

    public void setBrowseView(
            ResearchTreePresentationContract.BrowseView browseView,
            ResearchTreePresentation presentation) {
        if (browseView == null || presentation == null) {
            throw new IllegalArgumentException("Research Tree navigation inputs cannot be null");
        }
        this.browseView = browseView;
        retain(presentation, null);
    }

    /**
     * Selects one public group and reports whether the active view should swap
     * projections or only move its camera.
     */
    public ResearchTreePresentationContract.GroupSelectionAction selectGroup(
            ResourceLocation groupId,
            ResearchTreePresentation presentation) {
        if (groupId == null || presentation == null
                || presentation.group(groupId).isEmpty()) {
            throw new IllegalArgumentException("unknown Research Tree navigation group");
        }
        selectedGroupId = groupId;
        return ResearchTreePresentationContract.groupSelectionAction(browseView);
    }

    public Optional<ResourceLocation> nextGroup(
            ResearchTreePresentation presentation,
            int delta) {
        if (presentation == null) {
            throw new IllegalArgumentException("Research Tree presentation cannot be null");
        }
        List<ResearchTreePresentation.Group> groups = presentation.groups();
        if (groups.isEmpty()) {
            selectedGroupId = null;
            return Optional.empty();
        }
        int current = selectedGroupId == null
                ? -1
                : groups.stream().map(ResearchTreePresentation.Group::id).toList()
                        .indexOf(selectedGroupId);
        int next = Math.floorMod(current + delta, groups.size());
        return Optional.of(groups.get(next).id());
    }
}
