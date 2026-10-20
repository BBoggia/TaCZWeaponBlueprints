package com.gamergaming.taczweaponblueprints.research.designer;

import com.google.gson.*;
import java.util.*;

/** Explicit capture-to-custom operation. Source context remains separate and unchanged. */
public final class ResearchDesignerConversion {
    private ResearchDesignerConversion() {}
    public record Result(ResearchDesignerProject project, List<String> reviewNotes) {
        public Result { reviewNotes = List.copyOf(reviewNotes); }
    }
    public static Result createCustom(ResearchDesignerCapture capture, String namespace, String title) {
        var source = capture.json(); var root = ResearchDesignerProject.create(namespace,title).json();
        var draft = root.getAsJsonObject("draft"); var definitions = source.getAsJsonObject("definitions");
        if(definitions.getAsJsonObject("research_profiles").has(namespace + ":profile")
                || definitions.getAsJsonObject("research_tech_trees").has(namespace + ":tree"))
            throw new IllegalArgumentException("Choose a namespace with new profile and tree IDs");
        String sourceProfile = source.get("profile_id").getAsString();
        var profile = definition(definitions,"research_profiles",sourceProfile);
        String sourceTree = profile.get("tech_tree").getAsString();
        var tree = definition(definitions,"research_tech_trees",sourceTree);
        var decodedProfile = com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.CODEC
                .parse(com.mojang.serialization.JsonOps.INSTANCE,profile).getOrThrow(IllegalArgumentException::new);
        var domains=new JsonObject();
        for(var domain:com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain.values())
            domains.add(domain.name().toLowerCase(Locale.ROOT),DesignerValues.encode(com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.DomainPolicy.CODEC,decodedProfile.domainPolicy(domain)));
        profile.add("domain_policies",domains);
        profile.add("progression",DesignerValues.encode(com.gamergaming.taczweaponblueprints.resource.research.BlueprintProgressionProfilePolicy.CODEC,decodedProfile.progression()));
        profile.add("crafting",DesignerValues.encode(com.gamergaming.taczweaponblueprints.resource.research.BlueprintCraftingProfilePolicy.CODEC,decodedProfile.crafting()));
        profile.addProperty("format",5); profile.addProperty("tech_tree",namespace + ":tree");
        tree.addProperty("format",3); tree.addProperty("weapon_placement_mode","authored_only");
        tree.remove("automatic_weapon_placement");
        // Original lane and branch IDs are local references inside this tree, not competing resources.
        draft.getAsJsonObject("profile").add("definition",profile);
        draft.getAsJsonObject("tree").add("definition",tree);
        var nodes = new JsonArray(); var notes = new ArrayList<String>();
        source.getAsJsonArray("conversion_notes").forEach(n -> notes.add(n.getAsJsonObject().get("code").getAsString()
                + ": " + n.getAsJsonObject().get("blueprint_id").getAsString()));
        for (var entry : source.getAsJsonObject("resolved_nodes").entrySet()) {
            String id = entry.getKey(); var resolved = entry.getValue().getAsJsonObject();
            if (!source.getAsJsonObject("catalog").has(id)) throw new IllegalArgumentException("Resolved node has no catalog identity");
            var node = new JsonObject(); node.addProperty("id",UUID.randomUUID().toString()); node.addProperty("blueprint_id",id);
            node.add("policy",resolved.getAsJsonObject("policy").deepCopy());
            node.add("placement",resolved.has("placement") ? resolved.getAsJsonObject("placement").deepCopy() : new JsonObject());
            if (resolved.has("branch_id")) node.add("branch_id",resolved.get("branch_id"));
            var groups = new JsonArray(); resolved.getAsJsonArray("requirements").forEach(g -> {
                var group = g.getAsJsonObject().deepCopy(); group.addProperty("id",UUID.randomUUID().toString()); groups.add(group);
                group.getAsJsonArray("any_of").forEach(bp -> {
                    if (!source.getAsJsonObject("catalog").has(bp.getAsString())) notes.add("missing_reference: " + bp.getAsString());
                });
            }); node.add("requirements",groups); nodes.add(node);
        }
        var branches = tree.has("branches") ? tree.getAsJsonArray("branches") : new JsonArray();
        var generated = new TreeMap<Integer,String>();
        for (var nodeElement : nodes) {
            var node = nodeElement.getAsJsonObject(); var resolved = source.getAsJsonObject("resolved_nodes").getAsJsonObject(node.get("blueprint_id").getAsString());
            if (!node.has("branch_id") && resolved.has("automatic_branch")) {
                int index = resolved.getAsJsonObject("automatic_branch").get("branchIndex").getAsInt();
                if (index >= 0) {
                    String branch = generated.computeIfAbsent(index, i -> namespace + ":captured/automatic_branch_" + i);
                    node.addProperty("branch_id", branch);
                }
            }
        }
        generated.forEach((index,id) -> {
            if (branches.asList().stream().anyMatch(b -> b.getAsJsonObject().get("id").getAsString().equals(id)))
                throw new IllegalArgumentException("Generated branch ID conflicts with an authored branch");
            var branch = new JsonObject(); branch.addProperty("id",id); branch.addProperty("title","Branch " + (index+1));
            branch.addProperty("order",index); branch.addProperty("domain","weapons");
            branch.addProperty("label_visibility","public_when_populated"); branches.add(branch);
        });
        tree.add("branches",branches);
        if (!generated.isEmpty()) notes.add("Automatic branch positions became named custom branches; transition spacing will be re-laid out.");
        draft.add("nodes",nodes);
        // Membership is represented once, by node.branch_id in the project schema.
        if (tree.has("branches")) tree.getAsJsonArray("branches").forEach(b -> b.getAsJsonObject().remove("members"));
        var support = new JsonArray();
        // Global resources remain declared dependencies. Copying them would alter unrelated profiles.
        for (String kind : List.of("research_data","analysis_reconstruction")) {
            var family = definitions.getAsJsonObject(kind); if (family == null) continue;
            family.entrySet().forEach(e -> {
                var value = e.getValue().getAsJsonObject().getAsJsonObject("definition").deepCopy();
                if (!value.has("profile") || !value.get("profile").getAsString().equals(sourceProfile)) return;
                var resource = new JsonObject(); resource.addProperty("kind",kind);
                String sourceId = e.getKey();
                resource.addProperty("id",namespace + ":captured/" + kind + "/" + sourceId.replace(':','/'));
                value.addProperty("profile",namespace + ":profile");
                resource.add("definition",value); support.add(resource);
            });
        }
        root.add("supporting_resources",support);
        var origin = new JsonObject(); origin.addProperty("kind","captured"); origin.add("capture_id",source.get("capture_id"));
        origin.addProperty("content_fingerprint",capture.fingerprint()); origin.addProperty("conversion","resolved_to_custom"); root.add("origin",origin);
        var assumptions = source.getAsJsonObject("settings").deepCopy(); assumptions.addProperty("activation_profile",namespace + ":profile");
        assumptions.addProperty("capture_fingerprint",capture.fingerprint()); assumptions.addProperty("requires_runtime_equivalence_check",true);
        root.add("settings_assumptions",assumptions);
        var dependencies = root.getAsJsonObject("dependencies");
        source.getAsJsonObject("dependencies").getAsJsonObject("mods").entrySet().forEach(e -> {
            var dep = new JsonObject(); dep.addProperty("id",e.getKey()); dep.add("version",e.getValue()); dependencies.getAsJsonArray("mods").add(dep);
        });
        source.getAsJsonObject("catalog").entrySet().forEach(e -> {
            var dep = new JsonObject(); dep.addProperty("id",e.getKey()); dep.addProperty("fingerprint",DesignerFingerprint.of(e.getValue()));
            dependencies.getAsJsonArray("content").add(dep);
        });
        definitions.entrySet().stream().filter(f -> Set.of("blueprint_tags","research_data","analysis_reconstruction").contains(f.getKey()))
                .forEach(f -> f.getValue().getAsJsonObject().entrySet().forEach(e -> {
            var definition=e.getValue().getAsJsonObject().getAsJsonObject("definition");
            if(definition.has("profile") && definition.get("profile").getAsString().equals(sourceProfile)) return;
            var dep = new JsonObject(); dep.addProperty("kind",f.getKey()); dep.addProperty("id",e.getKey());
            dep.addProperty("fingerprint",DesignerFingerprint.of(e.getValue().getAsJsonObject().get("definition")));
            dependencies.getAsJsonArray("resources").add(dep);
        }));
        notes.add("Custom draft requires validation against its captured context before export.");
        com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchProfile.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,profile).getOrThrow(IllegalArgumentException::new);
        com.gamergaming.taczweaponblueprints.resource.research.ResearchTechTreeDefinition.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE,tree).getOrThrow(IllegalArgumentException::new);
        return new Result(ResearchDesignerProject.fromJson(root),notes);
    }
    private static JsonObject definition(JsonObject definitions,String kind,String id) {
        var family = definitions.getAsJsonObject(kind);
        if (family == null || !family.has(id)) throw new IllegalArgumentException("Capture is missing " + kind + "/" + id);
        return family.getAsJsonObject(id).getAsJsonObject("definition").deepCopy();
    }
}
