package com.gamergaming.taczweaponblueprints.client.designer;

import java.util.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerDraft;

/** Tolerant outline for drafts, including empty branches, missing parents and cycles. */
final class DesignerBranchOutline {
    record Entry(String id, String title, String translation, int depth) {}
    private record Branch(String id, String title, String translation, String parent, int order) {}
    private record Visit(Branch branch, int depth) {}
    static List<Entry> read(ResearchDesignerDraft draft, String domain) {
        var branches=new LinkedHashMap<String,Branch>();
        var source=draft.tree().definition().json().get("branches");
        if(source!=null&&source.isJsonArray())for(var value:source.getAsJsonArray())if(value.isJsonObject()) {
            var b=value.getAsJsonObject();String id=string(b,"id",""),d=string(b,"domain",domain);
            if(id.isEmpty()||!d.equalsIgnoreCase(domain))continue;
            int order=0;try{order=b.has("order")?b.get("order").getAsBigDecimal().intValueExact():0;}catch(RuntimeException ignored){}
            branches.putIfAbsent(id,new Branch(id,string(b,"title",id),string(b,"translation_key",""),string(b,"parent",""),order));
        }
        draft.nodes().stream().filter(n->string(n.placement().json(),"domain",domain).equalsIgnoreCase(domain))
            .flatMap(n->n.branchId().stream()).forEach(id->branches.putIfAbsent(id,new Branch(id,id,"","",0)));
        var ordered=branches.values().stream().sorted(Comparator.comparingInt(Branch::order).thenComparing(Branch::id)).toList();
        var children=new HashMap<String,List<Branch>>();ordered.forEach(b->children.computeIfAbsent(b.parent,k->new ArrayList<>()).add(b));
        var result=new ArrayList<Entry>();var seen=new HashSet<String>();
        var roots=new ArrayList<>(ordered.stream().filter(b->!branches.containsKey(b.parent)).toList());roots.addAll(ordered);
        for(var root:roots){var stack=new ArrayDeque<Visit>();stack.push(new Visit(root,0));
            while(!stack.isEmpty()){var next=stack.pop();var b=next.branch;if(!seen.add(b.id))continue;
                result.add(new Entry(b.id,b.title,b.translation,Math.min(8,next.depth)));
                var list=children.getOrDefault(b.id,List.of());for(int i=list.size()-1;i>=0;i--)stack.push(new Visit(list.get(i),next.depth+1));
            }}
        return List.copyOf(result);
    }
    private static String string(JsonObject object,String key,String fallback){var value=object.get(key);return value!=null&&value.isJsonPrimitive()&&value.getAsJsonPrimitive().isString()?value.getAsString():fallback;}
    private DesignerBranchOutline() {}
}
