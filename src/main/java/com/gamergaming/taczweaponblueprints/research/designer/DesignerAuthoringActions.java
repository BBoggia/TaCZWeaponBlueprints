package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import com.google.gson.JsonPrimitive;
import static com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerStructure.*;

/** Small, reversible authoring intentions, expressed as one document change each. */
public final class DesignerAuthoringActions {
    private DesignerAuthoringActions() {}
    public static Proposal add(ResearchDesignerProject project, ResearchDesignerCapture capture,
            String blueprint, Optional<String> parentId) {
        var parent = parentId.map(id -> node(project, id));
        var inserted = insert(project, capture, List.of(blueprint), Optional.empty()).result();
        var added = inserted.draft().nodes().stream().filter(n -> n.blueprintId().filter(blueprint::equals).isPresent()).findFirst().orElseThrow();
        if (parent.isPresent()) {
            String prerequisite = parent.get().blueprintId().orElseThrow(() -> new IllegalArgumentException("Assign a weapon to the starting node first."));
            inserted = connect(inserted, added.id(), Optional.empty(), List.of(prerequisite), true).result();
            if (domain(parent.get()).equals(domain(added)))
                inserted = ResearchDesignerEdits.apply(inserted, ResearchDesignerEdits.membership(added.id(), parent.get().branchId())).project();
        }
        return new Proposal(project.digest(), parent.isPresent() ? "Add next weapon" : "Add starting weapon", inserted,
                List.of(parent.isPresent() ? "Add and connect " + blueprint : "Add " + blueprint));
    }
    public static Proposal assign(ResearchDesignerProject project,ResearchDesignerCapture capture,String nodeId,String blueprint){
        var node=node(project,nodeId);
        if(node.blueprintId().isPresent())throw new IllegalArgumentException("This node already has a weapon.");
        if(project.draft().nodes().stream().anyMatch(n->n.blueprintId().filter(blueprint::equals).isPresent()))throw new IllegalArgumentException("This weapon is already in the tree.");
        var record=capture.json().getAsJsonObject("resolved_nodes").getAsJsonObject(blueprint);
        if(record==null)throw new IllegalArgumentException("The weapon is missing from the saved catalog.");
        var placement=record.has("placement")?record.getAsJsonObject("placement").deepCopy():new com.google.gson.JsonObject();
        node.placement().json().entrySet().forEach(e->placement.add(e.getKey(),e.getValue().deepCopy()));
        if(!placement.has("rank"))placement.addProperty("rank",0);if(!placement.has("order"))placement.addProperty("order",0);
        var policy=record.getAsJsonObject("policy").deepCopy();node.policy().json().entrySet().forEach(e->policy.add(e.getKey(),e.getValue().deepCopy()));
        var next=ResearchDesignerEdits.apply(project,ResearchDesignerEdits.batch(List.of(ResearchDesignerEdits.blueprint(nodeId,Optional.of(blueprint)),ResearchDesignerEdits.placement(nodeId,placement),ResearchDesignerEdits.policy(nodeId,policy)))).project();
        return new Proposal(project.digest(),"Assign weapon",next,List.of("Keep this node's identity, authored values and prerequisites."));
    }
    public static Proposal connectRequired(ResearchDesignerProject project, String parent, String target) {
        var source = node(project, parent); var child = node(project, target);
        String bp = source.blueprintId().orElseThrow(() -> new IllegalArgumentException("Assign a weapon before connecting it."));
        if (child.requirements().stream().anyMatch(g -> g.anyOf().equals(List.of(bp))))
            throw new IllegalArgumentException("This weapon is already required.");
        return connect(project, target, Optional.empty(), List.of(bp), true);
    }
    public static Proposal groupSelection(ResearchDesignerProject project, Set<String> selected, String title) {
        if (selected.isEmpty()) throw new IllegalArgumentException("Select the weapons to place in a branch.");
        String domain = domain(node(project, selected.iterator().next()));
        if (selected.stream().anyMatch(id -> !domain(node(project,id)).equals(domain)))
            throw new IllegalArgumentException("A branch contains one category. Select weapons, ammo, or attachments together.");
        var next = branch(project, Optional.empty(), title, domain, Optional.empty(), 0).result();
        var definitions = next.draft().tree().definition().json().getAsJsonArray("branches");
        String id = definitions.get(definitions.size()-1).getAsJsonObject().get("id").getAsString();
        next = ResearchDesignerEdits.apply(next, ResearchDesignerEdits.batch(selected.stream().sorted()
                .map(n -> ResearchDesignerEdits.membership(n, Optional.of(id))).toList())).project();
        return new Proposal(project.digest(), "Create branch", next, List.of("Group selected weapons without changing prerequisites or costs."));
    }
    public static Proposal reorder(ResearchDesignerProject project, String id, int rank, int index, Optional<String> branch) {
        var moving = node(project,id);
        var peers = project.draft().nodes().stream().filter(n -> !n.id().equals(id) && domain(n).equals(domain(moving))
                && n.branchId().equals(branch) && number(n,"rank")==rank)
                .sorted(Comparator.comparingInt((ResearchDesignerDraft.Node n)->number(n,"order")).thenComparing(ResearchDesignerDraft.Node::id)).toList();
        var order = new ArrayList<>(peers.stream().map(ResearchDesignerDraft.Node::id).toList());
        order.add(Math.max(0,Math.min(index,order.size())),id);
        var next = move(project,Set.of(id),rank,order.indexOf(id),branch).result();
        var edits = new ArrayList<ResearchDesignerEdits.Edit>();
        for(int i=0;i<order.size();i++) edits.add(ResearchDesignerEdits.nodeField(order.get(i),"/placement/order",Optional.of(new JsonPrimitive(i))));
        next = ResearchDesignerEdits.apply(next,ResearchDesignerEdits.batch(edits)).project();
        return new Proposal(project.digest(),"Move weapon",next,List.of("Move placement only; prerequisites, costs and bench tiers stay unchanged."));
    }
    public static String domain(ResearchDesignerDraft.Node node) {
        var value=node.placement().json().get("domain");
        return value!=null&&value.isJsonPrimitive()&&Set.of("weapons","ammo","attachments").contains(value.getAsString())?value.getAsString():"weapons";
    }
    public static int number(ResearchDesignerDraft.Node node,String key) {
        try { return Math.max(0,Math.min(MAX_POSITION,Math.toIntExact(DesignerJson.integer(node.placement().json().get(key))))); }
        catch(RuntimeException invalid){return 0;}
    }
}
