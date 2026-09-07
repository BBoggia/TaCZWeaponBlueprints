package com.gamergaming.taczweaponblueprints.research.designer;

import com.google.gson.*;
import java.util.*;
import static com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerProject.*;

/** Pure source edits. A batch validates and publishes one replacement document. */
public final class ResearchDesignerEdits {
    private ResearchDesignerEdits() {}
    @FunctionalInterface public interface Edit { void apply(JsonObject source, List<Reference> affected); }
    public record Reference(String target, String pointer) {}
    public record Change(ResearchDesignerProject project, List<Reference> affected) {
        public Change { affected = List.copyOf(affected); }
    }
    public enum BranchRemoval { UNGROUP, DELETE_SUBTREE }
    public static Change apply(ResearchDesignerProject project, Edit edit) {
        JsonObject copy = project.json(); var affected = new ArrayList<Reference>(); edit.apply(copy, affected);
        ResearchDesignerProject result = fromJson(copy);
        if (!result.projectId().equals(project.projectId())) throw new IllegalArgumentException("Edits cannot change project identity");
        return new Change(result, affected);
    }
    public static Edit batch(List<Edit> edits) {
        var owned = List.copyOf(edits); return (source, affected) -> owned.forEach(e -> e.apply(source, affected));
    }
    public static Edit title(String title) { return (p, affected) -> p.addProperty("title", title); }
    public static Edit addNode(ResearchDesignerDraft.Node node) {
        JsonObject value = nodeJson(node); return (p, affected) -> nodes(p).add(value.deepCopy());
    }
    public static Edit removeNode(String id) {
        return (p, affected) -> {
            JsonObject n = node(p, id); Optional<String> blueprint = optionalString(n, "blueprint_id");
            nodes(p).remove(n);
            blueprint.ifPresent(bp -> references(p, "", Set.of(bp), affected));
        };
    }
    public static Edit blueprint(String id, Optional<String> blueprint) {
        blueprint.ifPresent(ResearchDesignerProject::resourceId);
        return (p, affected) -> {
            JsonObject n = node(p, id); Optional<String> previous = optionalString(n, "blueprint_id");
            if (blueprint.isPresent()) n.addProperty("blueprint_id", blueprint.get()); else n.remove("blueprint_id");
            if (!previous.equals(blueprint)) previous.ifPresent(bp -> references(p, "", Set.of(bp), affected));
        };
    }
    public static Edit policy(String id, JsonObject policy) { return nodeObject(id, "policy", policy); }
    public static Edit placement(String id, JsonObject placement) { return nodeObject(id, "placement", placement); }
    /** Update one placement/policy leaf while preserving all unrelated source fields. */
    public static Edit nodeField(String id, String pointer, Optional<JsonElement> value) {
        String[] path = pointer.split("/", -1);
        if (path.length < 3 || !path[0].isEmpty() || !Set.of("policy", "placement").contains(path[1])
                || Arrays.stream(path).skip(1).anyMatch(p -> !p.matches("[a-z_]+")))
            throw new IllegalArgumentException("Expected a node placement/policy field path");
        Optional<JsonElement> owned = value.map(JsonElement::deepCopy);
        return (p, affected) -> {
            JsonObject target = node(p, id);
            for (int i = 1; i < path.length - 1; i++) {
                if (!target.has(path[i])) {
                    if (owned.isEmpty()) return;
                    target.add(path[i], new JsonObject());
                }
                target = object(target, path[i]);
            }
            if (owned.isPresent()) target.add(path[path.length - 1], owned.get().deepCopy()); else target.remove(path[path.length - 1]);
        };
    }
    private static Edit nodeObject(String id, String key, JsonObject value) {
        var copy = new DesignerJson.ObjectValue(value);
        return (p, affected) -> node(p, id).add(key, copy.json());
    }
    public static Edit membership(String id, Optional<String> branch) {
        branch.ifPresent(ResearchDesignerProject::resourceId);
        return (p, affected) -> {
            JsonObject n = node(p, id);
            if (branch.isPresent()) n.addProperty("branch_id", branch.get()); else n.remove("branch_id");
        };
    }
    public static Edit addRequirement(String nodeId, ResearchDesignerDraft.Requirement group) {
        JsonObject value = requirementJson(group);
        return (p, affected) -> array(node(p, nodeId), "requirements").add(value.deepCopy());
    }
    public static Edit replaceRequirement(String nodeId, ResearchDesignerDraft.Requirement group) {
        JsonObject value = requirementJson(group);
        return (p, affected) -> {
            JsonArray groups = array(node(p, nodeId), "requirements");
            int index = index(groups, group.id()); groups.set(index, value.deepCopy());
        };
    }
    public static Edit removeRequirement(String nodeId, String groupId) {
        return (p, affected) -> { JsonArray groups = array(node(p, nodeId), "requirements"); groups.remove(index(groups, groupId)); };
    }
    public static Edit addBranch(JsonObject branch) {
        var copy = new DesignerJson.ObjectValue(branch);
        return (p, affected) -> branches(p).add(copy.json());
    }
    public static Edit replaceBranch(String branchId, JsonObject branch) {
        if (!string(branch, "id").equals(branchId)) throw new IllegalArgumentException("Branch edits preserve identity");
        var copy = new DesignerJson.ObjectValue(branch);
        return (p, affected) -> { JsonArray branches = branches(p); branches.set(index(branches, branchId), copy.json()); };
    }
    public static Edit removeBranch(String branchId, BranchRemoval mode) {
        Objects.requireNonNull(mode);
        return (p, affected) -> {
            JsonArray branches = branches(p); JsonObject removed = branches.get(index(branches, branchId)).getAsJsonObject();
            Set<String> removedIds = new HashSet<>(); removedIds.add(branchId);
            if (mode == BranchRemoval.DELETE_SUBTREE) {
                boolean changed;
                do {
                    changed = false;
                    for (var entry : branches) {
                        JsonObject b = entry.getAsJsonObject();
                        if (optionalString(b, "parent").filter(removedIds::contains).isPresent()) changed |= removedIds.add(string(b, "id"));
                    }
                } while (changed);
            } else {
                Optional<String> parent = optionalString(removed, "parent");
                for (var entry : branches) {
                    JsonObject b = entry.getAsJsonObject();
                    if (optionalString(b, "parent").filter(branchId::equals).isPresent()) {
                        if (parent.isPresent()) b.addProperty("parent", parent.get()); else b.remove("parent");
                    }
                }
            }
            branches.asList().removeIf(b -> removedIds.contains(string(b.getAsJsonObject(), "id")));
            var removedBlueprints = new ArrayList<String>();
            nodes(p).asList().removeIf(entry -> {
                JsonObject n = entry.getAsJsonObject();
                if (!optionalString(n, "branch_id").filter(removedIds::contains).isPresent()) return false;
                if (mode == BranchRemoval.UNGROUP) { n.remove("branch_id"); return false; }
                optionalString(n, "blueprint_id").ifPresent(removedBlueprints::add); return true;
            });
            var removedTargets = new HashSet<>(removedIds); removedTargets.addAll(removedBlueprints);
            references(p, "", removedTargets, affected);
        };
    }
    /** Complete source replacement preserves fields beyond the first UI's editable subset. */
    public static Edit profileDefinition(JsonObject definition) { return definition("profile", definition); }
    public static Edit treeDefinition(JsonObject definition) { return definition("tree", definition); }
    private static Edit definition(String key, JsonObject value) {
        var owned = new DesignerJson.ObjectValue(value);
        return (p, affected) -> object(object(p, "draft"), key).add("definition", owned.json());
    }
    static JsonArray nodes(JsonObject p) { return array(object(p, "draft"), "nodes"); }
    static JsonObject node(JsonObject p, String id) { return nodes(p).get(index(nodes(p), id)).getAsJsonObject(); }
    static JsonArray branches(JsonObject p) {
        JsonObject tree = object(object(object(p, "draft"), "tree"), "definition");
        if (!tree.has("branches")) tree.add("branches", new JsonArray());
        return array(tree, "branches");
    }
    static int index(JsonArray values, String id) {
        for (int i = 0; i < values.size(); i++) if (string(values.get(i).getAsJsonObject(), "id").equals(id)) return i;
        throw new IllegalArgumentException("Unknown editor object: " + id);
    }
    static void references(JsonElement value, String pointer, Set<String> targets, List<Reference> found) {
        if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(e -> references(e.getValue(), pointer + "/" + e.getKey().replace("~", "~0").replace("/", "~1"), targets, found));
        else if (value.isJsonArray()) {
            JsonArray array = value.getAsJsonArray(); for (int i = 0; i < array.size(); i++) references(array.get(i), pointer + "/" + i, targets, found);
        } else if (value.isJsonPrimitive() && value.getAsJsonPrimitive().isString() && targets.contains(value.getAsString())) found.add(new Reference(value.getAsString(), pointer));
    }
    public static JsonObject nodeJson(ResearchDesignerDraft.Node node) {
        JsonObject n = new JsonObject(); n.addProperty("id", node.id()); node.blueprintId().ifPresent(id -> n.addProperty("blueprint_id", id));
        n.add("placement", node.placement().json()); n.add("policy", node.policy().json()); node.branchId().ifPresent(id -> n.addProperty("branch_id", id));
        JsonArray requirements = new JsonArray(); node.requirements().forEach(g -> requirements.add(requirementJson(g))); n.add("requirements", requirements); return n;
    }
    private static JsonObject requirementJson(ResearchDesignerDraft.Requirement group) {
        JsonObject result = new JsonObject(); result.addProperty("id", group.id()); JsonArray alternatives = new JsonArray();
        group.anyOf().forEach(alternatives::add); result.add("any_of", alternatives); return result;
    }
}
