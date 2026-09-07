package com.gamergaming.taczweaponblueprints.research.designer;

import com.google.gson.*;
import java.util.*;

/** Detached operator context; session identity is deliberately outside the content fingerprint. */
public final class ResearchDesignerCapture {
    public static final int MAX_BYTES = 32 * 1024 * 1024;
    private static final Set<String> CONTEXT = Set.of("profile_id", "catalog_export_format", "definitions", "settings",
            "catalog", "resolved_nodes", "registry_facts", "dependencies", "conversion_notes", "context_completeness");
    private final byte[] bytes;
    private final DesignerJson.ObjectValue source;
    private ResearchDesignerCapture(JsonObject json) {
        bytes = DesignerJson.bytes(json, MAX_BYTES, DesignerJson.MAX_DEPTH);
        source = new DesignerJson.ObjectValue(json);
    }
    public static ResearchDesignerCapture create(UUID session, JsonObject revisions, JsonObject context) {
        if (!context.keySet().equals(CONTEXT)) throw new IllegalArgumentException("Incomplete capture context");
        var root = context.deepCopy();
        root.addProperty("format", 1); root.addProperty("capture_id", UUID.randomUUID().toString());
        root.addProperty("source_kind", "server"); root.addProperty("disclosure", "operator_full");
        root.addProperty("session_token", session.toString()); root.add("revisions", revisions.deepCopy());
        root.addProperty("dependency_fingerprint", DesignerFingerprint.of(semanticContext(context)));
        return open(DesignerJson.bytes(root, MAX_BYTES, DesignerJson.MAX_DEPTH));
    }
    public static ResearchDesignerCapture open(byte[] bytes) {
        var root = DesignerJson.parse(bytes, MAX_BYTES, DesignerJson.MAX_DEPTH);
        var fields = new HashSet<>(CONTEXT);
        fields.addAll(Set.of("format", "capture_id", "source_kind", "disclosure", "session_token", "revisions", "dependency_fingerprint"));
        if (!root.keySet().equals(fields) || DesignerJson.integer(root.get("format")) != 1
                || !"server".equals(string(root, "source_kind")) || !"operator_full".equals(string(root, "disclosure"))
                || !"complete".equals(string(root, "context_completeness")))
            throw new IllegalArgumentException("Unsupported or incomplete operator capture");
        uuid(root, "capture_id"); uuid(root, "session_token");
        if (!root.getAsJsonObject("revisions").keySet().containsAll(Set.of("catalog", "research", "automatic", "config", "access", "tags")))
            throw new IllegalArgumentException("Incomplete revision identity");
        var context = context(root);
        if (!DesignerFingerprint.of(semanticContext(context)).equals(string(root, "dependency_fingerprint")))
            throw new IllegalArgumentException("Capture fingerprint mismatch");
        for (String key : List.of("definitions", "catalog", "resolved_nodes", "registry_facts", "settings", "dependencies"))
            if (!root.get(key).isJsonObject()) throw new IllegalArgumentException("Expected keyed capture facts");
        if (root.getAsJsonObject("catalog").size() > 4096 || root.getAsJsonObject("resolved_nodes").size() > 4096
                || root.getAsJsonArray("conversion_notes").size() > 1000)
            throw new IllegalArgumentException("Capture exceeds record limits");
        int resources=0;
        for(var family:root.getAsJsonObject("definitions").entrySet()) {
            if(!family.getValue().isJsonObject()) throw new IllegalArgumentException("Expected keyed definitions");
            resources+=family.getValue().getAsJsonObject().size();
            for(var resource:family.getValue().getAsJsonObject().entrySet()) {
                if(net.minecraft.resources.ResourceLocation.tryParse(resource.getKey())==null
                        || !resource.getValue().getAsJsonObject().has("definition")) throw new IllegalArgumentException("Invalid definition record");
            }
        }
        if(resources>4096)throw new IllegalArgumentException("Capture exceeds resource limit");
        if(!root.getAsJsonObject("catalog").keySet().equals(root.getAsJsonObject("resolved_nodes").keySet()))
            throw new IllegalArgumentException("Capture catalog and resolved records disagree");
        return new ResearchDesignerCapture(root);
    }
    public static JsonObject semanticContext(JsonObject context) {
        var result=context.deepCopy();
        if(result.has("definitions")) result.getAsJsonObject("definitions").entrySet().forEach(f -> f.getValue().getAsJsonObject().entrySet().forEach(e -> {
            var record=e.getValue().getAsJsonObject();record.remove("source_pack");record.remove("representation");
        }));
        if(result.has("settings") && result.getAsJsonObject("settings").has("effective"))
            stripRevisions(result.getAsJsonObject("settings").getAsJsonObject("effective"));
        return result;
    }
    private static void stripRevisions(JsonElement value) {
        if(value.isJsonObject()) {value.getAsJsonObject().remove("revision");value.getAsJsonObject().entrySet().forEach(e -> stripRevisions(e.getValue()));}
        else if(value.isJsonArray()) value.getAsJsonArray().forEach(ResearchDesignerCapture::stripRevisions);
    }
    private static JsonObject context(JsonObject root) {
        var result = new JsonObject(); CONTEXT.forEach(k -> result.add(k, root.get(k))); return result;
    }
    private static void uuid(JsonObject root, String key) {
        String value = string(root, key);
        if (!UUID.fromString(value).toString().equals(value)) throw new IllegalArgumentException("Invalid UUID");
    }
    static String string(JsonObject root, String key) { return DesignerJson.string(root.get(key)); }
    public JsonObject json() { return source.json(); }
    public byte[] bytes() { return bytes.clone(); }
    public String fingerprint() { return string(source.json(), "dependency_fingerprint"); }
    public boolean sameContent(ResearchDesignerCapture other) { return fingerprint().equals(other.fingerprint()); }
}
