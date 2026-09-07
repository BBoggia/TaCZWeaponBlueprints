package com.gamergaming.taczweaponblueprints.research.designer;

import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerStructure.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;

class ResearchDesignerStructureTest {
    private ResearchDesignerProject project(){
        var session=new ResearchDesignerSession(ResearchDesignerProject.create("test","Structural editing"));
        for(int i=0;i<5;i++){
            var placement=JsonParser.parseString("{\"domain\":\"weapons\",\"lane\":\"test:lane\",\"tier\":\"basic\",\"rank\":0,\"order\":0}").getAsJsonObject();placement.addProperty("order",i);
            var policy=JsonParser.parseString("{\"research_cost\":{\"points\":7},\"research_enabled\":true}").getAsJsonObject();
            session.execute("Add",ResearchDesignerEdits.addNode(new ResearchDesignerDraft.Node("node_"+i,Optional.of("test:gun_"+i),new DesignerJson.ObjectValue(placement),Optional.empty(),new DesignerJson.ObjectValue(policy),List.of())));
        }return session.project();
    }
    @Test void splitMergeAndInclusiveAlternativesRetainMeaningsAndIdentity(){
        var p=project();p=connect(p,"node_1",Optional.empty(),List.of("test:gun_0"),true).result();p=connect(p,"node_2",Optional.empty(),List.of("test:gun_0"),true).result();
        p=connect(p,"node_3",Optional.empty(),List.of("test:gun_1","test:gun_2"),false).result();
        var group=node(p,"node_3").requirements().get(0);assertEquals(2,group.anyOf().size());assertEquals(2,node(p,"node_3").placement().json().get("rank").getAsInt());
        var changed=connect(p,"node_3",Optional.of(group.id()),List.of("test:gun_0","test:gun_2"),false).result();assertEquals(group.id(),node(changed,"node_3").requirements().get(0).id());
        assertEquals(p.draft().nodes().stream().map(ResearchDesignerDraft.Node::id).toList(),changed.draft().nodes().stream().map(ResearchDesignerDraft.Node::id).toList());
        assertEquals(p.draft().nodes().stream().map(ResearchDesignerDraft.Node::policy).toList(),changed.draft().nodes().stream().map(ResearchDesignerDraft.Node::policy).toList());
    }
    @Test void allCreatesSeparateGroupsAndDoesNotBecomeOr(){var p=connect(project(),"node_3",Optional.empty(),List.of("test:gun_0","test:gun_1"),true).result();assertEquals(2,node(p,"node_3").requirements().size());assertTrue(node(p,"node_3").requirements().stream().allMatch(g->g.anyOf().size()==1));}
    @Test void cyclesSelfReferencesAndMissingPrerequisitesAreRejected(){var p=connect(project(),"node_1",Optional.empty(),List.of("test:gun_0"),true).result();assertThrows(IllegalArgumentException.class,()->connect(p,"node_0",Optional.empty(),List.of("test:gun_1"),false));assertThrows(IllegalArgumentException.class,()->connect(p,"node_0",Optional.empty(),List.of("test:gun_0"),true));assertThrows(IllegalArgumentException.class,()->connect(p,"node_0",Optional.empty(),List.of("test:missing"),true));}
    @Test void movePreviewIncludesDownstreamRowsAndIsOneUndo(){var p=connect(project(),"node_1",Optional.empty(),List.of("test:gun_0"),true).result();var session=new ResearchDesignerSession(p);var plan=move(p,Set.of("node_0"),8,2,Optional.empty());assertTrue(plan.changes().stream().anyMatch(s->s.contains("outside selection")));assertEquals(0,node(session.project(),"node_0").placement().json().get("rank").getAsInt());plan.commit(session);assertEquals(9,node(session.project(),"node_1").placement().json().get("rank").getAsInt());assertEquals(node(p,"node_0").policy(),node(session.project(),"node_0").policy());session.undo();assertEquals(p.json(),session.project().json());session.redo();assertEquals(plan.result().json(),session.project().json());}
    @Test void impossibleMoveAndOverflowExplainFailure(){var p=connect(project(),"node_1",Optional.empty(),List.of("test:gun_0"),true).result();assertThrows(IllegalArgumentException.class,()->move(p,Set.of("node_1"),0,0,Optional.empty()));assertThrows(IllegalArgumentException.class,()->move(p,Set.of("node_0"),MAX_POSITION,0,Optional.empty()));assertThrows(IllegalArgumentException.class,()->move(p,Set.of("node_0","node_1"),2,MAX_POSITION,Optional.empty()));}
    @Test void staleProposalCannotOverwriteAnotherEdit(){var session=new ResearchDesignerSession(project());var plan=move(session.project(),Set.of("node_0"),2,0,Optional.empty());session.execute("Title",ResearchDesignerEdits.title("Changed"));assertThrows(IllegalStateException.class,()->plan.commit(session));assertEquals("Changed",session.project().title());}
    @Test void crossDomainRequirementsDoNotChangeMembershipOrCosts(){var p=project();var placement=node(p,"node_0").placement().json();placement.addProperty("domain","ammo");p=ResearchDesignerEdits.apply(p,ResearchDesignerEdits.placement("node_0",placement)).project();var next=connect(p,"node_1",Optional.empty(),List.of("test:gun_0"),true).result();assertEquals("ammo",node(next,"node_0").placement().json().get("domain").getAsString());assertEquals(node(p,"node_1").policy(),node(next,"node_1").policy());assertEquals(5,next.draft().nodes().size());}
    @Test void deletionRetainsDanglingRequirementsForExplicitRepair(){var p=connect(project(),"node_1",Optional.empty(),List.of("test:gun_0","test:gun_2"),false).result();var group=node(p,"node_1").requirements().get(0);var plan=deleteNodes(p,Set.of("node_0"));assertEquals(group,node(plan.result(),"node_1").requirements().get(0));assertTrue(plan.changes().stream().anyMatch(s->s.contains("Unresolved")));}
    @Test void nestedBranchCopiesAreEmptyAndHaveFreshIds(){var p=branch(project(),Optional.empty(),"Parent","weapons",Optional.empty(),0).result();String parent=p.draft().tree().definition().json().getAsJsonArray("branches").get(0).getAsJsonObject().get("id").getAsString();p=branch(p,Optional.empty(),"Child","weapons",Optional.of(parent),1).result();String child=p.draft().tree().definition().json().getAsJsonArray("branches").get(1).getAsJsonObject().get("id").getAsString();p=move(p,Set.of("node_0"),0,0,Optional.of(child)).result();var copy=duplicateBranch(p,parent,"Copy").result();var branches=copy.draft().tree().definition().json().getAsJsonArray("branches");assertEquals(4,branches.size());assertEquals(4,branches.asList().stream().map(b->b.getAsJsonObject().get("id").getAsString()).distinct().count());assertEquals(p.draft().nodes(),copy.draft().nodes());var finalP=p;assertThrows(IllegalArgumentException.class,()->branch(finalP,Optional.of(parent),"Parent","weapons",Optional.of(child),0));}
    @Test void branchMembershipCannotCrossDomains(){var p=branch(project(),Optional.empty(),"Ammo","ammo",Optional.empty(),0).result();String id=p.draft().tree().definition().json().getAsJsonArray("branches").get(0).getAsJsonObject().get("id").getAsString();assertThrows(IllegalArgumentException.class,()->move(p,Set.of("node_0"),0,0,Optional.of(id)));}
    @Test void arrangeRetainsRulesAndCanBeUndone(){var p=move(project(),Set.of("node_0"),7,10,Optional.empty()).result();p=connect(p,"node_1",Optional.empty(),List.of("test:gun_0"),false).result();var plan=arrange(p,Optional.empty());assertEquals(0,node(plan.result(),"node_0").placement().json().get("rank").getAsInt());assertEquals(1,node(plan.result(),"node_1").placement().json().get("rank").getAsInt());assertEquals(node(p,"node_1").requirements(),node(plan.result(),"node_1").requirements());assertEquals(node(p,"node_1").policy(),node(plan.result(),"node_1").policy());var session=new ResearchDesignerSession(p);plan.commit(session);session.undo();assertEquals(p.json(),session.project().json());}

    @Test void ungroupKeepsNodesWhileSubtreeDeletionRetainsExternalRulesAndUndo(){
        var p=branch(project(),Optional.empty(),"Parent","weapons",Optional.empty(),0).result();
        String id=p.draft().tree().definition().json().getAsJsonArray("branches").get(0).getAsJsonObject().get("id").getAsString();
        p=move(p,Set.of("node_0","node_2"),0,0,Optional.of(id)).result();
        p=connect(p,"node_1",Optional.empty(),List.of("test:gun_0","test:gun_2"),false).result();
        var ungroup=deleteBranch(p,id,false).result();assertEquals(5,ungroup.draft().nodes().size());assertTrue(node(ungroup,"node_0").branchId().isEmpty());
        assertEquals(node(p,"node_1").requirements(),node(ungroup,"node_1").requirements());
        var session=new ResearchDesignerSession(p);deleteBranch(p,id,true).commit(session);assertEquals(3,session.project().draft().nodes().size());
        assertEquals(node(p,"node_1").requirements(),node(session.project(),"node_1").requirements());session.undo();assertEquals(p.json(),session.project().json());
    }
    @Test void multipleSelectionRetainsIdsGroupsAndPolicies(){
        var p=connect(project(),"node_3",Optional.empty(),List.of("test:gun_0","test:gun_2"),false).result();
        var next=move(p,Set.of("node_0","node_2"),4,7,Optional.empty()).result();
        for(var n:p.draft().nodes()){var after=node(next,n.id());assertEquals(n.blueprintId(),after.blueprintId());assertEquals(n.policy(),after.policy());assertEquals(n.requirements(),after.requirements());}
        assertEquals(5,node(next,"node_3").placement().json().get("rank").getAsInt());
    }
    @Test void emptyTemplateDoesNotReuseAnchorOrNodeOwnership(){
        var p=branch(project(),Optional.empty(),"Parent","weapons",Optional.empty(),0).result();
        var b=p.draft().tree().definition().json().getAsJsonArray("branches").get(0).getAsJsonObject();String id=b.get("id").getAsString();
        b.addProperty("anchor","test:gun_0");b.addProperty("label_visibility","anchor_revealed");p=ResearchDesignerEdits.apply(p,ResearchDesignerEdits.replaceBranch(id,b)).project();
        var plan=duplicateBranch(p,id,"Copy");var copy=plan.result().draft().tree().definition().json().getAsJsonArray("branches").get(1).getAsJsonObject();
        assertFalse(copy.has("anchor"));assertEquals("anchor_revealed",copy.get("label_visibility").getAsString());assertEquals(p.draft().nodes(),plan.result().draft().nodes());assertTrue(plan.changes().get(0).contains("new anchor"));
    }
    @Test void deletingBothEndsReportsOnlyReferencesThatActuallySurvive(){
        var p=connect(project(),"node_1",Optional.empty(),List.of("test:gun_0"),true).result();
        var plan=deleteNodes(p,Set.of("node_0","node_1"));assertTrue(plan.changes().stream().noneMatch(s->s.contains("Unresolved")));
    }
}
