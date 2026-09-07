package com.gamergaming.taczweaponblueprints.research.designer;

import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.research.designer.DesignerJson.ObjectValue;
import java.util.*;

/** Versioned local document. Never installs definitions or acquires live player/server state. */
public final class ResearchDesignerProject {
    public static final int FORMAT = 1, MAX_NODES = 4096, MAX_BRANCHES = 128, MAX_GROUPS = 65536;
    private static final Set<String> ROOT_FIELDS = Set.of("format", "project_id", "namespace", "title", "origin",
            "dependencies", "settings_assumptions", "draft", "supporting_resources", "extensions");
    private final ObjectValue source;
    private final ResearchDesignerDraft draft;
    private final UUID projectId;
    private final byte[] encoded;
    private final String digest, title, namespace;

    private ResearchDesignerProject(JsonObject source, ResearchDesignerDraft draft) {
        this.source = new ObjectValue(source); this.draft = draft;
        this.projectId = UUID.fromString(DesignerJson.string(source.get("project_id")));
        this.encoded = DesignerJson.bytes(source, DesignerJson.PROJECT_BYTES);
        this.digest = DesignerJson.digest(encoded);
        this.title = string(source, "title"); this.namespace = string(source, "namespace");
    }
    public UUID projectId() { return projectId; }
    public String title() { return title; }
    public String namespace() { return namespace; }
    public ResearchDesignerDraft draft() { return draft; }
    public JsonObject json() { return source.json(); }
    public byte[] bytes() { return encoded.clone(); }
    public int encodedSize() { return encoded.length; }
    public String digest() { return digest; }
    @Override public boolean equals(Object value) { return value instanceof ResearchDesignerProject p && source.equals(p.source); }
    @Override public int hashCode() { return source.hashCode(); }

    public record Opened(ObjectValue source, Optional<ResearchDesignerProject> editable, List<String> reasons) {
        public Opened { Objects.requireNonNull(source); editable = Objects.requireNonNull(editable); reasons = List.copyOf(reasons); }
        public boolean readOnly() { return editable.isEmpty(); }
    }
    public static Opened open(byte[] bytes) {
        try { return parse(bytes); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Invalid Designer project: " + e.getMessage(), e); }
    }
    private static Opened parse(byte[] bytes) {
        JsonObject root = DesignerJson.parse(bytes, DesignerJson.PROJECT_BYTES);
        List<String> reasons = new ArrayList<>();
        long format = DesignerJson.integer(root.get("format"));
        if (format != FORMAT) reasons.add("unsupported_version");
        if (!ROOT_FIELDS.containsAll(root.keySet())) reasons.add("unknown_project_field");
        if (!reasons.isEmpty()) return new Opened(new ObjectValue(root), Optional.empty(), reasons);
        keys(root, ROOT_FIELDS, ROOT_FIELDS);
        if (object(root, "extensions").size() != 0)
            return new Opened(new ObjectValue(root), Optional.empty(), List.of("unsupported_extension"));
        String uuid = string(root, "project_id");
        if (!UUID.fromString(uuid).toString().equals(uuid)) throw new IllegalArgumentException("Noncanonical project UUID");
        String namespace = string(root, "namespace");
        if (!namespace.matches("[a-z0-9_.-]{1,64}")) throw new IllegalArgumentException("Invalid namespace");
        title(string(root, "title"));
        JsonObject origin = object(root, "origin");
        keys(origin, Set.of("kind"), Set.of("kind", "capture_id", "content_fingerprint", "conversion"));
        String kind = string(origin, "kind");
        if (!Set.of("blank", "captured", "opened").contains(kind)) throw new IllegalArgumentException("Invalid origin");
        if (kind.equals("captured") || origin.has("capture_id") || origin.has("conversion") || origin.has("content_fingerprint")) {
            if (!UUID.fromString(string(origin, "capture_id")).toString().equals(string(origin, "capture_id")))
                throw new IllegalArgumentException("Invalid capture UUID");
            fingerprint(string(origin, "content_fingerprint"));
            if (!string(origin, "conversion").equals("resolved_to_custom")) throw new IllegalArgumentException("Invalid conversion");
        }
        JsonObject dependencies = object(root, "dependencies");
        keys(dependencies, Set.of("mods", "content", "resources"), Set.of("mods", "content", "resources"));
        for (String family : dependencies.keySet()) {
            Set<String> seen = new HashSet<>();
            for (JsonElement entry : array(dependencies, family)) {
                JsonObject dep = entry.getAsJsonObject();
                Set<String> fields = family.equals("mods") ? Set.of("id", "version")
                        : family.equals("content") ? Set.of("id", "fingerprint") : Set.of("kind", "id", "fingerprint");
                keys(dep, fields, fields);
                String id = string(dep, "id");
                if (id.isBlank() || id.length() > 256 || id.chars().anyMatch(Character::isISOControl))
                    throw new IllegalArgumentException("Invalid dependency ID");
                if (family.equals("resources")) resourceId(id);
                String identity = (dep.has("kind") ? string(dep, "kind") + ":" : "") + id;
                if (!seen.add(identity)) throw new IllegalArgumentException("Duplicate dependency");
                if (dep.has("fingerprint")) fingerprint(string(dep, "fingerprint")); else string(dep, "version");
            }
        }
        object(root, "settings_assumptions");
        JsonObject d = object(root, "draft");
        keys(d, Set.of("profile", "tree", "nodes"), Set.of("profile", "tree", "nodes"));
        var profile = resource(object(d, "profile")); var tree = resource(object(d, "tree"));
        if (!profile.id().startsWith(namespace + ":") || !tree.id().startsWith(namespace + ":"))
            throw new IllegalArgumentException("Managed resources must use the project namespace");
        sourceVersion(profile.definition().json(), 5, reasons); sourceVersion(tree.definition().json(), 3, reasons);
        if (!reasons.isEmpty()) return new Opened(new ObjectValue(root), Optional.empty(), reasons);
        inspect(BlueprintResearchProfile.CODEC, profile.definition().json(), reasons);
        inspect(ResearchTechTreeDefinition.CODEC, tree.definition().json(), reasons);
        JsonObject treeJson = tree.definition().json();
        if (treeJson.has("weapon_placement_mode") && !string(treeJson, "weapon_placement_mode").equals("authored_only"))
            reasons.add("source_conversion_required");
        JsonArray branches = treeJson.has("branches") ? array(treeJson, "branches") : new JsonArray();
        if (branches.size() > MAX_BRANCHES) throw new IllegalArgumentException("Too many branches");
        Set<String> branchIds = new HashSet<>();
        for (var b : branches) {
            String id = string(b.getAsJsonObject(), "id"); resourceId(id);
            if (!branchIds.add(id)) throw new IllegalArgumentException("Duplicate branch ID");
        }
        var nodes = new ArrayList<ResearchDesignerDraft.Node>(); Set<String> ids = new HashSet<>(), blueprints = new HashSet<>();
        int groupCount = 0, alternativeCount = 0;
        if (array(d, "nodes").size() > MAX_NODES) throw new IllegalArgumentException("Too many draft nodes");
        for (var element : array(d, "nodes")) {
            JsonObject n = element.getAsJsonObject();
            keys(n, Set.of("id", "placement", "policy", "requirements"), Set.of("id", "blueprint_id", "placement", "branch_id", "policy", "requirements"));
            String id = string(n, "id"); editorId(id);
            if (!ids.add(id)) throw new IllegalArgumentException("Duplicate editor ID");
            Optional<String> blueprint = optionalString(n, "blueprint_id");
            blueprint.ifPresent(value -> { resourceId(value); if (!blueprints.add(value)) throw new IllegalArgumentException("Duplicate blueprint ID"); });
            var requirements = new ArrayList<ResearchDesignerDraft.Requirement>();
            for (var elementGroup : array(n, "requirements")) {
                var g = elementGroup.getAsJsonObject(); keys(g, Set.of("id", "any_of"), Set.of("id", "any_of"));
                String groupId = string(g, "id"); editorId(groupId);
                if (!ids.add(groupId)) throw new IllegalArgumentException("Duplicate editor ID");
                var alternatives = array(g, "any_of").asList().stream().map(DesignerJson::string).toList();
                alternativeCount += alternatives.size(); groupCount++;
                requirements.add(new ResearchDesignerDraft.Requirement(groupId, alternatives));
            }
            JsonObject policy = object(n, "policy");
            for (String forbidden : List.of("format", "profile", "priority", "target", "prerequisites", "prerequisite_groups"))
                if (policy.has(forbidden)) throw new IllegalArgumentException("Node policy has compiler-owned field: " + forbidden);
            JsonObject rule = policy.deepCopy(); rule.addProperty("format", 5); rule.addProperty("profile", profile.id());
            JsonObject target = new JsonObject(); JsonArray targets = new JsonArray(); targets.add(blueprint.orElse("designer:placeholder")); target.add("blueprints", targets); rule.add("target", target);
            inspect(BlueprintResearchRule.CODEC, rule, reasons);
            JsonObject placement = object(n, "placement");
            if (placement.has("target")) throw new IllegalArgumentException("Placement target is compiler-owned");
            JsonObject placed = placement.deepCopy(); placed.add("target", target);
            inspect(ResearchTechTreeEntryBundle.ENTRY_CODEC, placed, reasons);
            nodes.add(new ResearchDesignerDraft.Node(id, blueprint, new ObjectValue(placement), optionalString(n, "branch_id"), new ObjectValue(policy), requirements));
        }
        if (groupCount > MAX_GROUPS || alternativeCount > MAX_GROUPS) throw new IllegalArgumentException("Too many requirements");
        JsonArray supporting = array(root, "supporting_resources");
        if (supporting.size() > 4096) throw new IllegalArgumentException("Too many supporting resources");
        Set<String> resourceIds = new HashSet<>();
        for (var entry : supporting) {
            JsonObject r = entry.getAsJsonObject(); keys(r, Set.of("kind", "id", "definition"), Set.of("kind", "id", "definition"));
            resourceId(string(r, "id")); object(r, "definition");
            if (!resourceIds.add(string(r, "kind") + "/" + string(r, "id"))) throw new IllegalArgumentException("Duplicate supporting resource");
            inspectSupporting(string(r, "kind"), object(r, "definition"), reasons);
        }
        if (reasons.isEmpty()) integralSourceUnits(root);
        var project = new ResearchDesignerProject(root, new ResearchDesignerDraft(profile, tree, nodes));
        return new Opened(new ObjectValue(root), reasons.isEmpty() ? Optional.of(project) : Optional.empty(), reasons.stream().distinct().toList());
    }
    public static ResearchDesignerProject fromJson(JsonObject source) {
        Opened opened = open(DesignerJson.bytes(source, DesignerJson.PROJECT_BYTES));
        return opened.editable().orElseThrow(() -> new IllegalArgumentException("Read-only project: " + opened.reasons()));
    }
    public ResearchDesignerProject copyAs(UUID newId) {
        if (newId.equals(projectId)) throw new IllegalArgumentException("Save As requires a new identity");
        JsonObject copy = json(); copy.addProperty("project_id", newId.toString()); return fromJson(copy);
    }
    public static ResearchDesignerProject create(String namespace, String title) {
        JsonObject p = JsonParser.parseString("""
            {"format":1,"project_id":"","namespace":"","title":"","origin":{"kind":"blank"},
             "dependencies":{"mods":[],"content":[],"resources":[]},"settings_assumptions":{},
             "draft":{"profile":{"id":"","definition":{"format":1,"journal_enabled":true,"visibility":"full",
                "tree_enabled":true,"research_enabled":true,"recycling_enabled":true,"allow_unlearned_recycling":false,
                "recycling_value":0,"research_cost":{"points":1},"requires_discovery":false,"creative_bypasses_cost":false}},
              "tree":{"id":"","definition":{"format":3,"title":"Custom Tech Tree","weapon_placement_mode":"authored_only",
                "layout":{"max_nodes_per_layer":20},"domains":[],"branches":[]}},"nodes":[]},
             "supporting_resources":[],"extensions":{}}
            """).getAsJsonObject();
        p.addProperty("project_id", UUID.randomUUID().toString()); p.addProperty("namespace", namespace); p.addProperty("title", title);
        JsonObject draft = object(p, "draft"); object(draft, "profile").addProperty("id", namespace + ":profile");
        object(object(draft, "profile"), "definition").addProperty("tech_tree", namespace + ":tree");
        object(draft, "tree").addProperty("id", namespace + ":tree");
        return fromJson(p);
    }
    private static void sourceVersion(JsonObject definition, int maximum, List<String> reasons) {
        long version = DesignerJson.integer(definition.get("format"));
        if (version < 1 || version > maximum) reasons.add("unsupported_source_version");
    }
    private static <T> void inspect(Codec<T> codec, JsonObject definition, List<String> reasons) {
        codec.parse(JsonOps.INSTANCE, definition).error().ifPresent(error -> {
            if (error.message().contains("unknown field(s)")) reasons.add("unsupported_source_field");
        });
    }
    private static void inspectSupporting(String kind, JsonObject definition, List<String> reasons) {
        switch (kind) {
            case "research_profiles" -> { sourceVersion(definition, 5, reasons); inspect(BlueprintResearchProfile.CODEC, definition, reasons); }
            case "research_rules" -> { sourceVersion(definition, 5, reasons); inspect(BlueprintResearchRule.CODEC, definition, reasons); }
            case "research_tech_trees" -> { sourceVersion(definition, 3, reasons); inspect(ResearchTechTreeDefinition.CODEC, definition, reasons); }
            case "research_tech_tree_entries" -> { sourceVersion(definition, 3, reasons); inspect(ResearchTechTreeEntryBundle.CODEC, definition, reasons); }
            case "research_automatic_placement_profiles" -> { sourceVersion(definition, 4, reasons); inspect(ResearchAutomaticPlacementProfile.CODEC, definition, reasons); }
            case "research_tree_groups" -> { sourceVersion(definition, 1, reasons); inspect(ResearchTreeGroupDefinition.CODEC, definition, reasons); }
            case "research_data" -> { sourceVersion(definition, 1, reasons); inspect(ResearchDataPolicy.CODEC, definition, reasons); }
            case "analysis_reconstruction" -> { sourceVersion(definition, 1, reasons); inspect(AnalysisReconstructionDefinition.CODEC, definition, reasons); }
            case "blueprint_tags" -> { sourceVersion(definition, 1, reasons); inspect(com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag.CODEC, definition, reasons); }
            default -> reasons.add("unsupported_resource_kind");
        }
    }
    private static ResearchDesignerDraft.Resource resource(JsonObject value) {
        keys(value, Set.of("id", "definition"), Set.of("id", "definition"));
        return new ResearchDesignerDraft.Resource(string(value, "id"), new ObjectValue(object(value, "definition")));
    }
    static JsonObject object(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonObject()) throw new IllegalArgumentException("Expected object: " + key);
        return value.getAsJsonObject(key);
    }
    static JsonArray array(JsonObject value, String key) {
        if (!value.has(key) || !value.get(key).isJsonArray()) throw new IllegalArgumentException("Expected array: " + key);
        return value.getAsJsonArray(key);
    }
    static String string(JsonObject value, String key) { return DesignerJson.string(value.get(key)); }
    static Optional<String> optionalString(JsonObject value, String key) { return value.has(key) ? Optional.of(string(value, key)) : Optional.empty(); }
    static void keys(JsonObject value, Set<String> required, Set<String> allowed) {
        if (!value.keySet().containsAll(required) || !allowed.containsAll(value.keySet())) throw new IllegalArgumentException("Missing or unknown document fields");
    }
    public static void resourceId(String id) {
        if (id == null || id.length() > 256 || !id.matches("[a-z0-9_.-]+:[a-z0-9/._-]+")) throw new IllegalArgumentException("Invalid resource ID");
    }
    public static void editorId(String id) {
        if (id == null || !id.matches("[a-z0-9_-]{1,64}")) throw new IllegalArgumentException("Invalid editor ID");
    }
    private static void fingerprint(String value) {
        if (!value.matches("[a-f0-9]{64}")) throw new IllegalArgumentException("Invalid fingerprint");
    }
    private static void title(String value) {
        if (value.isBlank() || !value.equals(value.trim()) || value.length() > 80 || value.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("Invalid project title");
    }
    private static final Set<String> INTEGER_UNITS = Set.of("format", "points", "count", "rank", "order", "level", "priority",
            "research_tier", "crafting_tier", "fragment_threshold", "recycling_value", "max_nodes_per_layer", "min_nodes_per_layer",
            "ranks_per_band", "maximum", "combat", "utility", "appeal", "color");
    private static void integralSourceUnits(JsonElement element) {
        if (element.isJsonObject()) for (var entry : element.getAsJsonObject().entrySet()) {
            if (INTEGER_UNITS.contains(entry.getKey()) && entry.getValue().isJsonPrimitive() && entry.getValue().getAsJsonPrimitive().isNumber())
                DesignerJson.integer(entry.getValue());
            integralSourceUnits(entry.getValue());
        } else if (element.isJsonArray()) element.getAsJsonArray().forEach(ResearchDesignerProject::integralSourceUnits);
    }
}
