package com.gamergaming.taczweaponblueprints.client.designer;

import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.research.designer.*;

class DesignerBranchOutlineTest {
    private static ResearchDesignerDraft draft(String branches) {
        var original=ResearchDesignerProject.create("test","Outline").draft();
        var definition=original.tree().definition().json();definition.add("branches",JsonParser.parseString(branches));
        return new ResearchDesignerDraft(original.profile(),new ResearchDesignerDraft.Resource(original.tree().id(),new DesignerJson.ObjectValue(definition)),original.nodes());
    }
    @Test void ordersEmptyChildrenAfterTheirParentAndFiltersDomains() {
        var result=DesignerBranchOutline.read(draft("""
            [{"id":"test:child","parent":"test:root","title":"Child","domain":"weapons","order":-1},
             {"id":"test:root","title":"Root","domain":"weapons","order":2},
             {"id":"test:ammo","domain":"ammo"}]
            """),"weapons");
        assertEquals(2,result.size());assertEquals("Root",result.get(0).title());assertEquals("Child",result.get(1).title());assertEquals(1,result.get(1).depth());
    }
    @Test void incompleteBranchesAndCyclesStayReachableWithoutRecursion() {
        var result=DesignerBranchOutline.read(draft("""
            [{"id":"test:a","parent":"test:b","order":{}},{"id":"test:b","parent":"test:a"},
             {"id":"test:c","parent":"test:missing"},{"id":"test:c"},{}]
            """),"weapons");
        assertEquals(3,result.size());assertEquals(3,result.stream().map(DesignerBranchOutline.Entry::id).distinct().count());
    }
}
