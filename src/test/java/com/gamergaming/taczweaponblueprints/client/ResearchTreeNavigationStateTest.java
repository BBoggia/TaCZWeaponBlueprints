package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePresentation;

import net.minecraft.resources.ResourceLocation;

class ResearchTreeNavigationStateTest {
    @Test
    void defaultsToBranchesAndUsesViewSpecificGroupSelection() {
        ResearchTreePresentation presentation = presentation();
        ResearchTreeNavigationState state = new ResearchTreeNavigationState();
        state.retain(presentation, id("test:b"));

        assertEquals(ResearchTreePresentationContract.BrowseView.BRANCHES, state.browseView());
        assertEquals(id("test:second"), state.selectedGroupId().orElseThrow());
        assertEquals(
                ResearchTreePresentationContract.GroupSelectionAction.SHOW_GROUP,
                state.selectGroup(id("test:first"), presentation));

        state.setBrowseView(
                ResearchTreePresentationContract.BrowseView.ALL_WEAPONS,
                presentation);
        assertEquals(
                ResearchTreePresentationContract.GroupSelectionAction.FOCUS_GROUP_REGION,
                state.selectGroup(id("test:second"), presentation));
        assertEquals(ResearchTreePresentationContract.BrowseView.ALL_WEAPONS, state.browseView());
        assertThrows(IllegalArgumentException.class, () ->
                state.selectGroup(id("test:missing"), presentation));
    }

    @Test
    void publicationChangesDiscardOnlyStaleGroupState() {
        ResearchTreeNavigationState state = new ResearchTreeNavigationState();
        ResearchTreePresentation first = presentation();
        state.retain(first, id("test:a"));
        state.selectGroup(id("test:second"), first);

        ResearchTreePresentation replacement = new ResearchTreePresentation(List.of(
                group("test:replacement", "Replacement", "test:c", 0)));
        state.retain(replacement, id("test:c"));

        assertEquals(id("test:replacement"), state.selectedGroupId().orElseThrow());
        assertEquals(Optional.of(id("test:replacement")), state.nextGroup(replacement, 1));
        state.retain(ResearchTreePresentation.EMPTY, null);
        assertTrue(state.selectedGroupId().isEmpty());
    }

    private static ResearchTreePresentation presentation() {
        return new ResearchTreePresentation(List.of(
                group("test:first", "First", "test:a", 0),
                group("test:second", "Second", "test:b", 1)));
    }

    private static ResearchTreePresentation.Group group(
            String groupId,
            String title,
            String nodeId,
            int order) {
        return new ResearchTreePresentation.Group(
                id(groupId),
                title,
                Optional.empty(),
                Optional.of(id(nodeId)),
                order,
                ResearchTreePresentation.Kind.AUTHORED,
                List.of(new ResearchTreePresentation.Member(id(nodeId), order, 0)));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
