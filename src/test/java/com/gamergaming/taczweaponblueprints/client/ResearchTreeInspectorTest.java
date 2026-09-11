package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

class ResearchTreeInspectorTest {
    @ParameterizedTest @CsvSource({"320,240", "419,240", "420,240", "540,240", "640,300", "854,480", "1600,480"})
    void expandedDetailsReserveFourRowsAndSeparateFixedControls(int width, int height) {
        for (int measured : new int[]{33, 55, 180}) {
            var layout = ResearchTreeWorkbenchLayout.create(width, height, true, true, measured, 9);
            assertEquals(ResearchTreeWorkbenchLayout.Mode.EXPANDED, layout.inspectorMode());
            assertTrue(layout.body().height() >= 4 * 13, layout.toString());
            assertTrue(layout.body().inside(width,height));
            assertFalse(layout.body().overlaps(layout.summary()));
            var controls = List.of(layout.back(), layout.action(), layout.track(), layout.target(), layout.toggle(), layout.review());
            for (int i = 0; i < controls.size(); i++) {
                assertTrue(layout.inspector().contains(controls.get(i)));
                assertFalse(controls.get(i).overlaps(layout.body()));
                assertFalse(controls.get(i).overlaps(layout.summary()));
                for (int j = i + 1; j < controls.size(); j++) assertFalse(controls.get(i).overlaps(controls.get(j)));
            }
        }
    }
    @Test void compactSelectionReclaimsCanvasSpaceAndExpansionUsesAnOverlayAtMinimumSize() {
        var compact = ResearchTreeWorkbenchLayout.create(320, 240, true);
        var expanded = ResearchTreeWorkbenchLayout.create(320, 240, true, true, 44, 9);
        var closed = ResearchTreeWorkbenchLayout.create(320, 240, false);
        assertEquals(ResearchTreeWorkbenchLayout.Mode.COMPACT, compact.inspectorMode());
        assertEquals(40, compact.inspector().height());
        assertTrue(expanded.inspectorOverlay());
        assertTrue(closed.canvas().contains(compact.canvas()));
        assertFalse(compact.canvas().overlaps(compact.inspector()));
        assertEquals(ResearchTreeWorkbenchLayout.Mode.CLOSED, closed.inspectorMode());
        assertEquals(ResearchTreeWorkbenchLayout.Mode.EXPANDED, ResearchTreeWorkbenchLayout.create(854,480,true).inspectorMode());
        assertEquals(ResearchTreeWorkbenchLayout.Mode.COMPACT, ResearchTreeWorkbenchLayout.create(854,480,true,false,33,9).inspectorMode());
    }
    @Test void linksRetainRequirementGroupsAndAllowOnlyDisclosedLocalOrReciprocalRemoteTargets() {
        var publication = ResearchTechTreeClientFixture.publication();
        var catalog = ResearchTechTreeProjectionBuilder.build(publication);
        var rows = ResearchTreeInspectorRequirements.rows(publication.graph(), catalog, ResearchTechTreeClientFixture.WEAPON_UPGRADE,
                node -> Component.literal(node.blueprintId().toString()), domain -> Component.literal(domain.name()), 1,2,3,4);
        assertTrue(rows.stream().anyMatch(r -> r.kind() == ResearchTreeInspectorRow.Kind.PREREQUISITE && r.target().filter(ResearchTechTreeClientFixture.WEAPON_ROOT::equals).isPresent()));
        assertTrue(rows.stream().anyMatch(r -> r.kind() == ResearchTreeInspectorRow.Kind.EXTERNAL_LINK && r.target().filter(ResearchTechTreeClientFixture.AMMO::equals).isPresent()));
        assertFalse(ResearchTreeInspectorRequirements.mayFollow(publication.graph(), catalog, ResearchTechTreeClientFixture.WEAPON_ROOT, ResearchTechTreeClientFixture.OPAQUE));
        assertFalse(ResearchTreeInspectorRequirements.mayFollow(publication.graph(), catalog, ResearchTechTreeClientFixture.AMMO, ResearchTechTreeClientFixture.SCOPE));
        assertTrue(ResearchTreeInspectorRequirements.mayFollow(publication.graph(), catalog, ResearchTechTreeClientFixture.SCOPE, ResearchTechTreeClientFixture.WEAPON_UPGRADE));
        assertFalse(ResearchTreeInspectorRequirements.mayFollow(publication.graph(), ResearchTechTreeProjectionCatalog.EMPTY, ResearchTechTreeClientFixture.SCOPE, ResearchTechTreeClientFixture.WEAPON_UPGRADE));
    }
    @Test void historyIsBoundedAndRestoresDomainCameraScrollAndExpansion() {
        var history = new ResearchTreeInspectorHistory();
        var camera = new ResearchTreeViewport.Snapshot(42, 84, .7);
        for (int i=0;i<40;i++) history.push(new ResearchTreeInspectorHistory.Entry(Domain.AMMO, new ResourceLocation("test:n"+i), camera, i, true));
        assertEquals(ResearchTreeInspectorHistory.LIMIT, history.size());
        var last = history.pop().orElseThrow();
        assertEquals(39,last.scroll()); assertEquals(camera,last.camera()); assertEquals(Domain.AMMO,last.domain()); assertTrue(last.expanded());
        history.retain(e -> e.scroll() < 12);
        assertEquals(4,history.size());
        assertEquals(11,history.pop().orElseThrow().scroll());
        history.clear(); assertFalse(history.available());
    }
}
