package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import com.google.gson.*;
import static com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerEdits.*;

/** Reviewed structural commands. Geometry, costs and gameplay state are not editing inputs. */
public final class ResearchDesignerStructure {
    public static final int MAX_POSITION = 1_000_000;
    public record Proposal(String baseDigest, String label, ResearchDesignerProject result, List<String> changes) {
        public Proposal { changes=List.copyOf(changes); }
        public void commit(ResearchDesignerSession session) {
            if(!baseDigest.equals(session.project().digest()))throw new IllegalStateException("The project changed. Review a new proposal.");
            if(!session.pendingInputs().isEmpty())throw new IllegalStateException("Finish or discard unfinished inputs first.");
            var replacement=result.json();
            session.execute(label,(source,refs)->{source.keySet().clear();replacement.entrySet().forEach(e->source.add(e.getKey(),e.getValue().deepCopy()));});
        }
    }
    private static Proposal propose(ResearchDesignerProject before,String label,ResearchDesignerProject after,List<String> notes) {
        return new Proposal(before.digest(),label,after,notes);
    }
    public static Proposal reset(ResearchDesignerProject project) {
        var json=project.json();nodes(json).asList().clear();branches(json).asList().clear();
        return propose(project,"Start empty tree",ResearchDesignerProject.fromJson(json),List.of("Remove every node and branch. Retain the captured catalog, domain settings and project identity. Undo restores the tree."));
    }
    public static Proposal insert(ResearchDesignerProject project, ResearchDesignerCapture capture, Collection<String> blueprintIds, Optional<String> branch) {
        var source=capture.json().getAsJsonObject("resolved_nodes");var edits=new ArrayList<Edit>();var notes=new ArrayList<String>();
        var existing=new HashSet<String>();project.draft().nodes().forEach(n->n.blueprintId().ifPresent(existing::add));
        for(String bp:new LinkedHashSet<>(blueprintIds)) {
            if(existing.contains(bp))throw new IllegalArgumentException("Blueprint already belongs to this tree: "+bp);
            if(!source.has(bp))throw new IllegalArgumentException("Blueprint is absent from the captured catalog: "+bp);
            var resolved=source.getAsJsonObject(bp);var placement=resolved.has("placement")?resolved.getAsJsonObject("placement").deepCopy():new JsonObject();
            if(!placement.has("domain")||!placement.has("lane")||!placement.has("tier"))throw new IllegalArgumentException("Captured placement is incomplete for "+bp+". Capture a source with domain, lane and tier information.");
            placement.addProperty("rank",0);placement.addProperty("order",project.draft().nodes().size()+edits.size());
            branch.ifPresent(id->checkMembership(project,id,placement.get("domain").getAsString()));
            edits.add(addNode(new ResearchDesignerDraft.Node(UUID.randomUUID().toString(),Optional.of(bp),new DesignerJson.ObjectValue(placement),branch,new DesignerJson.ObjectValue(resolved.getAsJsonObject("policy")),List.of())));
            notes.add("Add "+bp+" with its captured policy, without prerequisites.");
        }
        if(edits.isEmpty())throw new IllegalArgumentException("Select at least one unused blueprint.");
        return propose(project,"Add blueprints",apply(project,batch(edits)).project(),notes);
    }
    public static Proposal move(ResearchDesignerProject project,Set<String> selected,int rank,int order,Optional<String> branch) {
        position(rank);position(order);if(selected.isEmpty())throw new IllegalArgumentException("Select nodes to move.");
        var edits=new ArrayList<Edit>();var notes=new ArrayList<String>();int offset=0;
        for(String id:new TreeSet<>(selected)) {
            var n=node(project,id);String domain=string(n.placement().json(),"domain");branch.ifPresent(b->checkMembership(project,b,domain));
            var placement=n.placement().json();placement.addProperty("rank",rank);placement.addProperty("order",Math.addExact(order,offset++));position(placement.get("order").getAsInt());
            edits.add(placement(id,placement));edits.add(membership(id,branch));notes.add("Move "+name(n)+" to row "+rank+", order "+placement.get("order")+", branch "+branch.orElse("Unassigned")+".");
        }
        var next=apply(project,batch(edits)).project();next=adjustRanks(project,next,selected,false,notes);
        return propose(project,"Move nodes",next,notes);
    }
    /** Each group is inclusive OR; multiple groups are AND. Replacement retains the chosen group ID. */
    public static Proposal connect(ResearchDesignerProject project,String targetId,Optional<String> groupId,List<String> prerequisites,boolean all) {
        var target=node(project,targetId);var wanted=new LinkedHashSet<>(prerequisites);
        if(wanted.isEmpty())throw new IllegalArgumentException("Choose at least one prerequisite.");
        var byBlueprint=new HashMap<String,ResearchDesignerDraft.Node>();project.draft().nodes().forEach(n->n.blueprintId().ifPresent(bp->byBlueprint.put(bp,n)));
        for(String bp:wanted){if(!byBlueprint.containsKey(bp))throw new IllegalArgumentException("Add this prerequisite to the tree first: "+bp);if(target.blueprintId().filter(bp::equals).isPresent())throw new IllegalArgumentException("A blueprint cannot require itself.");}
        var edits=new ArrayList<Edit>();var notes=new ArrayList<String>();
        if(all&&groupId.isPresent())throw new IllegalArgumentException("Edit an existing group as inclusive alternatives, or add separate required groups.");
        if(all)for(String bp:wanted)edits.add(addRequirement(targetId,new ResearchDesignerDraft.Requirement(UUID.randomUUID().toString(),List.of(bp))));
        else {var group=new ResearchDesignerDraft.Requirement(groupId.orElseGet(()->UUID.randomUUID().toString()),List.copyOf(wanted));edits.add(groupId.isPresent()?replaceRequirement(targetId,group):addRequirement(targetId,group));}
        notes.add(name(target)+(all?" requires all of: ":" requires any one of: ")+String.join(", ",wanted)+". Alternatives are inclusive; learning more than one remains allowed.");
        var next=adjustRanks(project,apply(project,batch(edits)).project(),Set.of(),false,notes);
        return propose(project,"Edit prerequisites",next,notes);
    }
    public static Proposal removeGroup(ResearchDesignerProject project,String node,String group) {
        return propose(project,"Remove requirement group",apply(project,removeRequirement(node,group)).project(),List.of("Remove this entire requirement group. Other groups retain their meaning and identity."));
    }
    public static Proposal deleteNodes(ResearchDesignerProject project,Set<String> ids) {
        if(ids.isEmpty())throw new IllegalArgumentException("Select nodes to delete.");
        var result=apply(project,batch(ids.stream().sorted().map(ResearchDesignerEdits::removeNode).toList()));
        var notes=new ArrayList<String>();ids.stream().sorted().forEach(id->notes.add("Delete "+name(node(project,id))+"."));
        var deletedBlueprints=new HashSet<String>();ids.forEach(id->node(project,id).blueprintId().ifPresent(deletedBlueprints::add));
        var remainingReferences=new ArrayList<Reference>();references(result.project().json(),"",deletedBlueprints,remainingReferences);
        remainingReferences.forEach(r->notes.add("Unresolved reference retained: "+r.target()+" at "+r.pointer()+". Repair it in Problems; requirements are not silently weakened."));
        return propose(project,"Delete nodes",result.project(),notes);
    }
    public static Proposal branch(ResearchDesignerProject project,Optional<String> existing,String title,String domain,Optional<String> parent,int order) {
        if(title.isBlank()||title.length()>160)throw new IllegalArgumentException("Use a branch name between 1 and 160 characters.");
        position(order);if(!Set.of("weapons","ammo","attachments").contains(domain))throw new IllegalArgumentException("Choose a supported domain.");
        String id=existing.orElseGet(()->project.namespace()+":branch/"+UUID.randomUUID());
        var value=existing.map(b->branch(project,b).deepCopy()).orElseGet(JsonObject::new);
        if(existing.isPresent()&&!domain.equals(string(value,"domain")))throw new IllegalArgumentException("A branch retains its domain. Create a new branch to move across domains.");
        parent.ifPresent(p->{checkMembership(project,p,domain);var seen=new HashSet<String>();String cursor=p;
            while(cursor!=null){if(cursor.equals(id)||!seen.add(cursor))throw new IllegalArgumentException("A branch cannot be nested inside itself or a descendant.");var b=branch(project,cursor);cursor=b.has("parent")?b.get("parent").getAsString():null;}});
        value.addProperty("id",id);value.addProperty("title",title.trim());value.remove("translation_key");value.addProperty("domain",domain);value.addProperty("order",order);
        if(!value.has("label_visibility"))value.addProperty("label_visibility","public_when_populated");
        if(parent.isPresent())value.addProperty("parent",parent.get());else value.remove("parent");
        var next=apply(project,existing.isPresent()?replaceBranch(id,value):addBranch(value)).project();
        return propose(project,existing.isPresent()?"Edit branch":"Create branch",next,List.of(title+" in "+domain+", parent "+parent.orElse("none")+", order "+order+". Empty branches remain drafts until members are assigned."));
    }
    public static Proposal deleteBranch(ResearchDesignerProject project,String branch,boolean subtree) {
        var result=apply(project,removeBranch(branch,subtree?BranchRemoval.DELETE_SUBTREE:BranchRemoval.UNGROUP));
        var notes=new ArrayList<String>();notes.add(subtree?"Delete the branch, descendants and their owned nodes.":"Remove this branch. Keep its nodes unassigned and promote its child branches.");
        result.affected().forEach(r->notes.add("Reference requires review: "+r.target()+" at "+r.pointer()));
        return propose(project,"Delete branch",result.project(),notes);
    }
    public static Proposal duplicateBranch(ResearchDesignerProject project,String root,String title) {
        if(title.isBlank()||title.length()>160)throw new IllegalArgumentException("Use a branch name between 1 and 160 characters.");branch(project,root);
        var all=branchArray(project);var selected=new LinkedHashSet<String>();selected.add(root);
        boolean changed;do{changed=false;for(var item:all){var b=item.getAsJsonObject();if(b.has("parent")&&selected.contains(b.get("parent").getAsString()))changed|=selected.add(string(b,"id"));}}while(changed);
        var mapping=new HashMap<String,String>();selected.forEach(id->mapping.put(id,project.namespace()+":branch/"+UUID.randomUUID()));
        var edits=new ArrayList<Edit>();for(var item:all){var b=item.getAsJsonObject();String id=string(b,"id");if(!selected.contains(id))continue;var copy=b.deepCopy();copy.addProperty("id",mapping.get(id));
            if(id.equals(root)){copy.addProperty("title",title.trim());copy.remove("translation_key");}
            if(copy.has("parent")&&mapping.containsKey(copy.get("parent").getAsString()))copy.addProperty("parent",mapping.get(copy.get("parent").getAsString()));
            copy.remove("members");copy.remove("anchor");edits.add(addBranch(copy));}
        return propose(project,"Copy branch template",apply(project,batch(edits)).project(),List.of("Copy "+selected.size()+" branch containers with new IDs and no members or anchors. Assign unused blueprints explicitly; no blueprint ownership or requirements are copied. Copies keep their label visibility rules; anchor-based labels need a new anchor before validation can pass."));
    }
    public static Proposal arrange(ResearchDesignerProject project,Optional<String> branch) {
        var selected=new HashSet<String>();if(branch.isPresent()){
            branch(project,branch.get());var descendants=new HashSet<String>();descendants.add(branch.get());boolean more;
            do{more=false;for(var item:branchArray(project)){var b=item.getAsJsonObject();if(b.has("parent")&&descendants.contains(b.get("parent").getAsString()))more|=descendants.add(string(b,"id"));}}while(more);
            project.draft().nodes().stream().filter(n->n.branchId().filter(descendants::contains).isPresent()).forEach(n->selected.add(n.id()));
        }else project.draft().nodes().forEach(n->selected.add(n.id()));
        var notes=new ArrayList<String>();var next=adjustRanks(project,project,selected,true,notes);var edits=new ArrayList<Edit>();
        var groups=new TreeMap<String,List<ResearchDesignerDraft.Node>>();for(var n:next.draft().nodes())if(selected.contains(n.id()))groups.computeIfAbsent(n.branchId().orElse("")+"/"+string(n.placement().json(),"domain")+"/"+integer(n.placement().json(),"rank"),k->new ArrayList<>()).add(n);
        for(var group:groups.values()){group.sort(Comparator.comparingInt((ResearchDesignerDraft.Node n)->integer(n.placement().json(),"order")).thenComparing(ResearchDesignerDraft.Node::id));for(int i=0;i<group.size();i++){var n=group.get(i);if(integer(n.placement().json(),"order")!=i){edits.add(nodeField(n.id(),"/placement/order",Optional.of(new JsonPrimitive(i))));notes.add("Order "+name(n)+": "+integer(n.placement().json(),"order")+" → "+i);}}}
        notes.add("Branch clearance is solved globally. Geometry outside the selected branch may move even when its row/order stays unchanged.");
        return propose(project,"Arrange tree",apply(next,batch(edits)).project(),notes);
    }
    private static ResearchDesignerProject adjustRanks(ResearchDesignerProject before,ResearchDesignerProject after,Set<String> selected,boolean arrange,List<String> notes) {
        var byId=new TreeMap<String,ResearchDesignerDraft.Node>();var blueprint=new HashMap<String,String>();after.draft().nodes().forEach(n->{byId.put(n.id(),n);n.blueprintId().ifPresent(bp->blueprint.put(bp,n.id()));});
        var incoming=new HashMap<String,Set<String>>();var outgoing=new HashMap<String,Set<String>>();var degree=new HashMap<String,Integer>();
        for(var n:byId.values()){var parents=new TreeSet<String>();n.requirements().forEach(g->g.anyOf().forEach(bp->{if(blueprint.containsKey(bp))parents.add(blueprint.get(bp));}));incoming.put(n.id(),parents);degree.put(n.id(),parents.size());parents.forEach(p->outgoing.computeIfAbsent(p,k->new TreeSet<>()).add(n.id()));}
        var ready=new PriorityQueue<String>();degree.forEach((id,d)->{if(d==0)ready.add(id);});var ranks=new HashMap<String,Integer>();var edits=new ArrayList<Edit>();
        while(!ready.isEmpty()){String id=ready.remove();var n=byId.get(id);int minimum=0;for(String parent:incoming.get(id))minimum=Math.max(minimum,Math.addExact(ranks.get(parent),1));
            int old=integer(n.placement().json(),"rank");int rank=arrange&&selected.contains(id)?minimum:Math.max(old,minimum);
            if(!arrange&&selected.contains(id)&&old<minimum)throw new IllegalArgumentException(name(n)+" must be after its prerequisites (minimum row "+minimum+"). Move those prerequisites first or choose a later row.");
            position(rank);ranks.put(id,rank);if(rank!=old){edits.add(nodeField(id,"/placement/rank",Optional.of(new JsonPrimitive(rank))));notes.add((selected.contains(id)?"Row ":"Downstream row outside selection: ")+name(n)+": "+old+" → "+rank);}
            for(String child:outgoing.getOrDefault(id,Set.of()))if(degree.compute(child,(k,v)->v-1)==0)ready.add(child);
        }
        if(ranks.size()!=byId.size())throw new IllegalArgumentException("These requirements form a cycle. Remove the loop before connecting or arranging nodes.");
        return apply(after,batch(edits)).project();
    }
    public static ResearchDesignerDraft.Node node(ResearchDesignerProject p,String id){return p.draft().nodes().stream().filter(n->n.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown node: "+id));}
    private static JsonArray branchArray(ResearchDesignerProject p){var tree=p.draft().tree().definition().json();return tree.has("branches")?tree.getAsJsonArray("branches"):new JsonArray();}
    public static JsonObject branch(ResearchDesignerProject p,String id){var list=branchArray(p);return list.get(index(list,id)).getAsJsonObject();}
    private static void checkMembership(ResearchDesignerProject p,String id,String domain){if(!domain.equals(string(branch(p,id),"domain")))throw new IllegalArgumentException("A branch and its members must have the same domain.");}
    private static String string(JsonObject json,String key){if(!json.has(key)||!json.get(key).isJsonPrimitive()||!json.get(key).getAsJsonPrimitive().isString())throw new IllegalArgumentException("Complete the "+key+" field first.");return json.get(key).getAsString();}
    private static int integer(JsonObject json,String key){try{int value=json.get(key).getAsBigDecimal().intValueExact();position(value);return value;}catch(RuntimeException error){throw new IllegalArgumentException("Complete the "+key+" field with a whole number from 0 to "+MAX_POSITION+".");}}
    private static void position(int value){if(value<0||value>MAX_POSITION)throw new IllegalArgumentException("Rows and order must be between 0 and "+MAX_POSITION+".");}
    private static String name(ResearchDesignerDraft.Node n){return n.blueprintId().orElse(n.id());}
    private ResearchDesignerStructure() {}
}
