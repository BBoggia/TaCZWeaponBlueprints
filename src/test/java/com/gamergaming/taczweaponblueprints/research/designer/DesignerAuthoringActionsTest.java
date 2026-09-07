package com.gamergaming.taczweaponblueprints.research.designer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class DesignerAuthoringActionsTest {
    private final DesignerPropertiesSimulationTest fixtures=new DesignerPropertiesSimulationTest();
    @Test void addNextPlacesAndConnectsInOneUndoWithoutChangingCapturedPolicy(){
        var f=fixtures.fixture("inclusive_or");var empty=ResearchDesignerStructure.reset(f.project()).result();var session=new ResearchDesignerSession(empty);
        DesignerAuthoringActions.add(session.project(),f.context().capture(),"designer_fixture:root",Optional.empty()).commit(session);
        String root=session.project().draft().nodes().get(0).id();var before=session.project();
        DesignerAuthoringActions.add(before,f.context().capture(),"designer_fixture:left",Optional.of(root)).commit(session);
        var child=session.project().draft().nodes().stream().filter(n->n.blueprintId().filter("designer_fixture:left"::equals).isPresent()).findFirst().orElseThrow();
        assertEquals(List.of("designer_fixture:root"),child.requirements().get(0).anyOf());
        assertEquals(1,DesignerAuthoringActions.number(child,"rank"));
        assertEquals(f.context().capture().json().getAsJsonObject("resolved_nodes").getAsJsonObject("designer_fixture:left").get("policy"),child.policy().json());
        session.undo();assertEquals(before.json(),session.project().json());session.redo();assertEquals(2,session.project().draft().nodes().size());
    }
    @Test void connectingAnotherRequiredWeaponPreservesExistingAlternatives(){
        var f=fixtures.fixture("inclusive_or");var target=f.project().draft().nodes().stream().filter(n->n.blueprintId().filter("designer_fixture:end"::equals).isPresent()).findFirst().orElseThrow();
        var root=f.project().draft().nodes().stream().filter(n->n.blueprintId().filter("designer_fixture:root"::equals).isPresent()).findFirst().orElseThrow();
        var after=DesignerAuthoringActions.connectRequired(f.project(),root.id(),target.id()).result();var child=ResearchDesignerStructure.node(after,target.id());
        assertTrue(child.requirements().containsAll(target.requirements()));assertEquals(target.requirements().size()+1,child.requirements().size());assertEquals(target.policy(),child.policy());
        assertThrows(IllegalArgumentException.class,()->DesignerAuthoringActions.connectRequired(after,root.id(),target.id()));
    }
    @Test void groupingPreservesEachNodesRowsPoliciesAndRequirements(){
        var f=fixtures.fixture("inclusive_or");Set<String> selected=new HashSet<>(f.project().draft().nodes().stream().map(ResearchDesignerDraft.Node::id).toList());
        var after=DesignerAuthoringActions.groupSelection(f.project(),selected,"Rifles").result();
        for(var node:f.project().draft().nodes()){var changed=ResearchDesignerStructure.node(after,node.id());assertEquals(node.placement(),changed.placement());assertEquals(node.policy(),changed.policy());assertEquals(node.requirements(),changed.requirements());assertTrue(changed.branchId().isPresent());}
    }
    @Test void reorderInsertsBetweenPeersAndRetainsResearchRules(){
        var f=fixtures.fixture("inclusive_or");var p=ResearchDesignerStructure.reset(f.project()).result();
        for(String bp:List.of("root","left","right"))p=DesignerAuthoringActions.add(p,f.context().capture(),"designer_fixture:"+bp,Optional.empty()).result();
        var nodes=p.draft().nodes();var after=DesignerAuthoringActions.reorder(p,nodes.get(2).id(),0,1,Optional.empty()).result();
        assertEquals(0,DesignerAuthoringActions.number(ResearchDesignerStructure.node(after,nodes.get(0).id()),"order"));
        assertEquals(1,DesignerAuthoringActions.number(ResearchDesignerStructure.node(after,nodes.get(2).id()),"order"));
        assertEquals(2,DesignerAuthoringActions.number(ResearchDesignerStructure.node(after,nodes.get(1).id()),"order"));
        for(var node:nodes){var changed=ResearchDesignerStructure.node(after,node.id());assertEquals(node.policy(),changed.policy());assertEquals(node.requirements(),changed.requirements());}
    }
    @Test void failedAddOrConnectionLeavesSessionAndHistoryUnchanged(){
        var f=fixtures.fixture("single");var session=new ResearchDesignerSession(f.project());var node=f.project().draft().nodes().get(0);
        assertThrows(IllegalArgumentException.class,()->DesignerAuthoringActions.add(session.project(),f.context().capture(),node.blueprintId().orElseThrow(),Optional.empty()));
        assertThrows(IllegalArgumentException.class,()->DesignerAuthoringActions.connectRequired(session.project(),node.id(),node.id()));
        assertEquals(f.project().json(),session.project().json());assertFalse(session.canUndo());assertEquals(0,session.sequence());
    }
    @Test void assigningAnUnfinishedNodePreservesIdentityAndAuthoredCostAndCanUndo(){
        var f=fixtures.fixture("single");var n=f.project().draft().nodes().get(0);String bp=n.blueprintId().orElseThrow();
        var blank=ResearchDesignerEdits.apply(f.project(),ResearchDesignerEdits.blueprint(n.id(),Optional.empty())).project();
        var session=new ResearchDesignerSession(blank);DesignerAuthoringActions.assign(blank,f.context().capture(),n.id(),bp).commit(session);
        assertEquals(n,ResearchDesignerStructure.node(session.project(),n.id()));session.undo();assertEquals(blank.json(),session.project().json());
    }
    @Test void assignmentCannotStealAnExistingBlueprintOrReplaceAnAssignedNode(){
        var f=fixtures.fixture("inclusive_or");var first=f.project().draft().nodes().get(0);var second=f.project().draft().nodes().get(1);
        var blank=ResearchDesignerEdits.apply(f.project(),ResearchDesignerEdits.blueprint(first.id(),Optional.empty())).project();
        assertThrows(IllegalArgumentException.class,()->DesignerAuthoringActions.assign(blank,f.context().capture(),first.id(),second.blueprintId().orElseThrow()));
        assertThrows(IllegalArgumentException.class,()->DesignerAuthoringActions.assign(f.project(),f.context().capture(),first.id(),first.blueprintId().orElseThrow()));
    }
    @Test void testSuppliesPrioritizeCurrencyAndAuthoredMaterialsAndRespectStackLimits(){
        var f=fixtures.fixture("single");var n=f.project().draft().nodes().get(0);
        var cost=com.google.gson.JsonParser.parseString("{\"points\":12,\"ingredients\":[{\"items\":[\"minecraft:iron_ingot\"],\"count\":3}]}");
        var p=DesignerResearchProperties.change(f.project(),n.id(),DesignerResearchProperties.Field.COST,Optional.of(cost)).result();
        var before=p.digest();var slots=DesignerSimulation.testSupplies(p,f.context().capture());
        assertEquals("taczweaponblueprints:scrap",slots.get(0).item().toString());assertTrue(slots.stream().anyMatch(slot->slot.item().toString().equals("minecraft:iron_ingot")));assertTrue(slots.stream().allMatch(slot->slot.count()==64));assertEquals(before,p.digest());
    }

}
