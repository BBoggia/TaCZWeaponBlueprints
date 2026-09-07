package com.gamergaming.taczweaponblueprints.client.designer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import com.gamergaming.taczweaponblueprints.research.designer.*;

class DesignerDraftLayoutTest {
    private ResearchDesignerProject project(){
        var s=new ResearchDesignerSession(ResearchDesignerProject.create("test","Draft canvas"));
        for(int i=0;i<4;i++){
            var placement=new JsonObject();placement.addProperty("domain","weapons");placement.addProperty("rank",i/2);placement.addProperty("order",i%2);
            s.execute("Add",ResearchDesignerEdits.addNode(new ResearchDesignerDraft.Node("node_"+i,i==0?Optional.empty():Optional.of("test:gun_"+i),new DesignerJson.ObjectValue(placement),Optional.empty(),new DesignerJson.ObjectValue(new JsonObject()),List.of())));
        }return s.project();
    }
    @Test void incompleteDraftNodesRemainVisibleAndPickable(){var p=project();var layout=DesignerDraftLayout.create(p.draft(),"weapons");assertEquals(4,layout.tiles().size());for(var tile:layout.tiles())assertEquals(tile.id(),layout.hit(tile.x()+3,tile.y()+3).orElseThrow().id());assertEquals(p.digest(),projectDigest(p));}
    private String projectDigest(ResearchDesignerProject p){DesignerDraftLayout.create(p.draft(),"ammo");return p.digest();}
    @Test void progressionRowsAreTopToBottomAndDoNotOverlap(){var layout=DesignerDraftLayout.create(project().draft(),"weapons");for(var a:layout.tiles())for(var b:layout.tiles())if(a.rank()<b.rank())assertTrue(a.y()+DesignerDraftLayout.TILE_H<b.y());}
    @Test void branchRegionsRemainSeparateAndCollapseDoesNotEditMembership(){var p=project();var ids=new HashSet<>(p.draft().nodes().stream().map(ResearchDesignerDraft.Node::id).toList());p=DesignerAuthoringActions.groupSelection(p,ids,"Branch").result();String branch=p.draft().nodes().get(0).branchId().orElseThrow();var before=p.json();var folded=DesignerDraftLayout.create(p.draft(),"weapons",Set.of(branch));assertEquals(1,folded.regions().size());assertTrue(folded.tiles().isEmpty());assertEquals(before,p.json());assertEquals(4,DesignerDraftLayout.create(p.draft(),"weapons").tiles().size());}
    @Test void dropOffersAnAdditionalProgressionRow(){var layout=DesignerDraftLayout.create(project().draft(),"weapons");var drop=layout.drop(40,layout.height());assertEquals(2,drop.rank());assertEquals(0,drop.index());assertTrue(drop.branch().isEmpty());}
    @Test void absentPlacementHasAnEditableFallback(){var p=project();p=ResearchDesignerEdits.apply(p,ResearchDesignerEdits.placement("node_0",new JsonObject())).project();assertEquals(4,DesignerDraftLayout.create(p.draft(),"weapons").tiles().size());}
    @Test void maximumDraftRemainsPickableWithoutRequiringAValidGameplayGraph(){
        var json=project().json();var entries=json.getAsJsonObject("draft").getAsJsonArray("nodes");var template=entries.get(0).getAsJsonObject().deepCopy();entries.asList().clear();
        for(int i=0;i<ResearchDesignerProject.MAX_NODES;i++){var n=template.deepCopy();n.addProperty("id","bulk_"+i);n.getAsJsonObject("placement").addProperty("rank",i);entries.add(n);}
        var draft=ResearchDesignerProject.fromJson(json).draft();var layout=DesignerDraftLayout.create(draft,"weapons");assertEquals(4096,layout.tiles().size());var last=layout.tiles().get(4095);assertEquals(last.id(),layout.hit(last.x()+1,last.y()+1).orElseThrow().id());
    }
    @Test void sparseRowsAndSeparateBranchesKeepTheirAuthoredOrder(){
        var p=project();p=DesignerAuthoringActions.groupSelection(p,Set.of("node_0","node_2"),"Left").result();p=DesignerAuthoringActions.groupSelection(p,Set.of("node_1","node_3"),"Right").result();
        p=ResearchDesignerEdits.apply(p,ResearchDesignerEdits.nodeField("node_2","/placement/rank",Optional.of(new JsonPrimitive(999999)))).project();
        var layout=DesignerDraftLayout.create(p.draft(),"weapons");assertEquals(2,layout.regions().size());var a=layout.regions().get(0);var b=layout.regions().get(1);assertTrue(a.x()+a.width()<b.x());assertEquals(List.of(0,1,999999),layout.ranks());
        assertTrue(layout.height()<1000,"Sparse authored rows should not create huge empty canvas space");
    }

}
