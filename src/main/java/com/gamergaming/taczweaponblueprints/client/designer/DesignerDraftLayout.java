package com.gamergaming.taczweaponblueprints.client.designer;

import java.util.*;
import com.gamergaming.taczweaponblueprints.research.designer.*;

/** Authoring geometry includes incomplete nodes. It is never published to gameplay. */
public record DesignerDraftLayout(List<Tile> tiles,List<Region> regions,List<Integer> ranks,int width,int height) {
    public static final int TILE_W=132,TILE_H=58,GAP=20,ROW=96,TOP=44;
    public record Tile(String id,int x,int y,int rank,int index,Optional<String> branch) {}
    public record Region(Optional<String> id,String title,int x,int width,int height) {}
    public record Drop(int rank,int index,Optional<String> branch,int x,int y) {}
    public DesignerDraftLayout {tiles=List.copyOf(tiles);regions=List.copyOf(regions);ranks=List.copyOf(ranks);}
    public static DesignerDraftLayout create(ResearchDesignerDraft draft,String domain) {return create(draft,domain,Set.of());}
    public static DesignerDraftLayout create(ResearchDesignerDraft draft,String domain,Set<String> collapsed) {
        var nodes=draft.nodes().stream().filter(n->DesignerAuthoringActions.domain(n).equals(domain)).toList();
        var ranks=new TreeSet<Integer>();ranks.add(0);nodes.forEach(n->ranks.add(DesignerAuthoringActions.number(n,"rank")));
        var rows=List.copyOf(ranks);var byBranch=new LinkedHashMap<Optional<String>,List<ResearchDesignerDraft.Node>>();
        var titles=new HashMap<Optional<String>,String>();
        for(var b:DesignerBranchOutline.read(draft,domain)){var id=Optional.of(b.id());byBranch.put(id,new ArrayList<>());titles.put(id,"  ".repeat(b.depth())+b.title());}
        nodes.forEach(n->{byBranch.computeIfAbsent(n.branchId(),k->new ArrayList<>()).add(n);titles.putIfAbsent(n.branchId(),n.branchId().orElse("Starting area"));});
        if(byBranch.isEmpty()){byBranch.put(Optional.empty(),new ArrayList<>());titles.put(Optional.empty(),"Starting area");}
        var tiles=new ArrayList<Tile>();var regions=new ArrayList<Region>();int x=16,h=TOP+(rows.size()+1)*ROW;
        for(var branch:byBranch.entrySet()){
            var grouped=new TreeMap<Integer,List<ResearchDesignerDraft.Node>>();branch.getValue().forEach(n->grouped.computeIfAbsent(DesignerAuthoringActions.number(n,"rank"),k->new ArrayList<>()).add(n));
            boolean folded=branch.getKey().filter(collapsed::contains).isPresent();
            int columns=folded?1:Math.max(1,grouped.values().stream().mapToInt(List::size).max().orElse(1));int w=columns*(TILE_W+GAP)+GAP;
            regions.add(new Region(branch.getKey(),titles.get(branch.getKey()),x,w,folded?TOP+48:h));
            if(folded){x+=w+32;continue;}
            for(var row:grouped.entrySet()){row.getValue().sort(Comparator.comparingInt((ResearchDesignerDraft.Node n)->DesignerAuthoringActions.number(n,"order")).thenComparing(ResearchDesignerDraft.Node::id));
                for(int i=0;i<row.getValue().size();i++)tiles.add(new Tile(row.getValue().get(i).id(),x+GAP+i*(TILE_W+GAP),TOP+rows.indexOf(row.getKey())*ROW,row.getKey(),i,branch.getKey()));}
            x+=w+32;
        }
        return new DesignerDraftLayout(tiles,regions,rows,x,h+16);
    }
    public Optional<Tile> hit(double x,double y){return tiles.stream().filter(t->x>=t.x&&x<t.x+TILE_W&&y>=t.y&&y<t.y+TILE_H).findFirst();}
    public Drop drop(double x,double y){
        var r=regions.stream().min(Comparator.comparingDouble(v->Math.abs(x-Math.max(v.x,Math.min(x,v.x+v.width))))).orElseThrow();
        int row=Math.max(0,Math.min(ranks.size(),(int)Math.round((y-TOP)/(double)ROW)));
        int rank=row==ranks.size()?Math.min(ResearchDesignerStructure.MAX_POSITION,ranks.get(ranks.size()-1)+1):ranks.get(row);
        int index=Math.max(0,Math.min((r.width-GAP)/(TILE_W+GAP),(int)Math.round((x-r.x-GAP)/(TILE_W+GAP))));
        return new Drop(rank,index,r.id,r.x+GAP+index*(TILE_W+GAP),TOP+row*ROW);
    }
}
