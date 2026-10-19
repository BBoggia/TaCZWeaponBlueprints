package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import com.google.gson.*;
import com.mojang.serialization.*;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.gamergaming.taczweaponblueprints.resource.research.*;

/** Detached compilation only: no activation, file writes, configuration changes or player operations. */
public final class ResearchDesignerCompiler {
    private ResearchDesignerCompiler() {}
    private static void checkCancelled() {
        if (Thread.currentThread().isInterrupted()) throw new java.util.concurrent.CancellationException();
    }
    public enum Severity { ERROR, WARNING, INFO }
    public record Diagnostic(String code, Severity severity, String objectId, String pointer,
            String cause, String action) {
        public JsonObject json(long projectRevision) {
            var result = new JsonObject(); result.addProperty("format", 1); result.addProperty("code", code);
            result.addProperty("severity", severity.name().toLowerCase(Locale.ROOT));
            result.addProperty("stage", severity == Severity.INFO ? "simulation" : "structure");
            result.addProperty("project_revision", projectRevision); result.addProperty("object_id", objectId);
            result.addProperty("pointer", pointer); result.addProperty("message_key", "designer.diagnostic." + code);
            var arguments = new JsonArray(); arguments.add(cause); result.add("arguments", arguments);
            var blocks = new JsonArray(); if (severity == Severity.ERROR) blocks.add("simulation");
            if (severity == Severity.ERROR) blocks.add("export"); result.add("blocks", blocks);
            var actions = new JsonArray(); actions.add(action); result.add("actions", actions); return result;
        }
    }
    public record Candidate(String projectDigest, ResourceLocation profile,
            BlueprintResearchSnapshot snapshot, DesignerJson.ObjectValue resources) {}
    public record Result(String projectDigest, Optional<Candidate> candidate, List<Diagnostic> diagnostics) {
        public Result { diagnostics = List.copyOf(diagnostics); }
    }

    /** Context is explicit; an absent capture is suitable only for projects with no captured dependencies. */
    public static Result compile(ResearchDesignerProject project, Map<ResourceLocation, BlueprintData> catalog,
            Optional<ResearchDesignerCapture> capture) {
        checkCancelled();
        var work = new Compilation(project, Map.copyOf(catalog), capture);
        try { return work.run(); }
        catch (java.util.concurrent.CancellationException cancelled) { throw cancelled; }
        catch (RuntimeException error) {
            work.issue("invalid_context", "project", "", message(error), "Restore or correct the dependency context and validate again.");
            return work.result(null);
        }
    }

    public static Result compile(ResearchDesignerSession.Checkpoint checkpoint, Map<ResourceLocation, BlueprintData> catalog,
            Optional<ResearchDesignerCapture> capture) {
        checkCancelled();
        if (!checkpoint.pendingInputs().isEmpty()) return new Result(checkpoint.project().digest(), Optional.empty(),
                List.of(new Diagnostic("pending_input", Severity.ERROR, "project", "", "An input has not been committed.", "Finish or discard the pending input before validating.")));
        return compile(checkpoint.project(), catalog, capture);
    }

    /** A failed/current draft never replaces the separately retained last successful candidate. */
    public static final class State {
        private String requestedDigest;
        private long generation;
        private Result current;
        private Candidate lastValid;
        public long begin(ResearchDesignerProject project) { requestedDigest = project.digest(); current = null; return ++generation; }
        public boolean accept(long request, Result result) {
            return request == generation && accept(result);
        }
        /** Synchronous use only; asynchronous callers must supply the generation returned by begin. */
        public boolean accept(Result result) {
            Objects.requireNonNull(result);
            if (requestedDigest != null && !requestedDigest.equals(result.projectDigest())) return false;
            current = result; result.candidate().ifPresent(c -> lastValid = c); return true;
        }
        public Optional<Result> current() { return Optional.ofNullable(current); }
        public Optional<Candidate> lastValid() { return Optional.ofNullable(lastValid); }
    }

    private static final class Compilation {
        final ResearchDesignerProject project;
        final Map<ResourceLocation, BlueprintData> catalog;
        final Optional<ResearchDesignerCapture> capture;
        final List<Diagnostic> diagnostics = new ArrayList<>();
        final JsonObject resources = new JsonObject();
        Compilation(ResearchDesignerProject project, Map<ResourceLocation, BlueprintData> catalog,
                Optional<ResearchDesignerCapture> capture) { this.project = project; this.catalog = catalog; this.capture = capture; }
        void issue(String code, String object, String pointer, String cause, String action) {
            if (diagnostics.size() < 999) diagnostics.add(new Diagnostic(code, Severity.ERROR, object, pointer, cause, action));
            else if (diagnostics.size() == 999) diagnostics.add(new Diagnostic("diagnostic_limit", Severity.ERROR,
                    "project", "", "Further issues were omitted.", "Fix the listed issues and validate again."));
        }
        void put(String kind, String id, JsonObject definition, String owner) {
            if (!resources.has(kind)) resources.add(kind, new JsonObject());
            var family = resources.getAsJsonObject(kind);
            if (family.has(id)) issue("resource_collision", owner, "", "Two resources own " + kind + "/" + id, "Use a unique resource ID.");
            else family.add(id, definition.deepCopy());
        }
        void owned(String id, String owner) {
            if (!new ResourceLocation(id).getNamespace().equals(project.namespace()))
                issue("ownership", owner, "", "Resource is outside the project namespace: " + id, "Use the project namespace for owned resources.");
        }
        Result run() {
            var draft = project.draft(); var profile = draft.profile(); var tree = draft.tree();
            owned(profile.id(), "profile"); owned(tree.id(), "tree");
            dependencies();
            put("research_profiles", profile.id(), profile.definition().json(), "profile");
            put("research_tech_trees", tree.id(), tree.definition().json(), "tree");
            var source = project.json();
            for (var element : source.getAsJsonArray("supporting_resources")) {
                var support = element.getAsJsonObject(); String id = support.get("id").getAsString();
                owned(id, id); put(support.get("kind").getAsString(), id, support.getAsJsonObject("definition"), id);
            }
            if (draft.nodes().isEmpty()) issue("empty_tree", "tree", "/draft/nodes", "No research is assigned.", "Add a blueprint to the tree.");
            var entries = new JsonArray(); var memberships = new TreeMap<String, JsonArray>();
            Set<String> assigned = new HashSet<>();
            for (int index = 0; index < draft.nodes().size(); index++) {
                checkCancelled();
                var node = draft.nodes().get(index); String pointer = "/draft/nodes/" + index;
                if (node.blueprintId().isEmpty()) {
                    issue("unassigned_blueprint", node.id(), pointer, "This node has no blueprint.", "Choose an installed blueprint."); continue;
                }
                String bp = node.blueprintId().orElseThrow();
                if (!assigned.add(bp)) issue("duplicate_blueprint", node.id(), pointer, "Blueprint is assigned twice: " + bp, "Keep one node per blueprint.");
                if (!catalog.containsKey(new ResourceLocation(bp))) issue("missing_blueprint", node.id(), pointer + "/blueprint_id", "Blueprint is absent: " + bp, "Restore its content dependency or choose another blueprint.");
                var target = new JsonObject(); var ids = new JsonArray(); ids.add(bp); target.add("blueprints", ids);
                var rule = node.policy().json(); rule.addProperty("format", 5); rule.addProperty("profile", profile.id());
                rule.addProperty("priority", 0); rule.add("target", target);
                var requirements = new JsonArray();
                for (var group : node.requirements()) {
                    var value = new JsonObject(); var alternatives = new JsonArray();
                    for (String prerequisite : group.anyOf()) {
                        alternatives.add(prerequisite);
                        if (!catalog.containsKey(new ResourceLocation(prerequisite))) issue("missing_prerequisite", group.id(), pointer + "/requirements", "Prerequisite is absent: " + prerequisite, "Restore its dependency or edit this requirement.");
                    }
                    if (alternatives.isEmpty()) issue("empty_requirement", group.id(), pointer + "/requirements", "A requirement has no alternatives.", "Add an alternative or remove the requirement.");
                    value.add("any_of", alternatives); requirements.add(value);
                }
                rule.add("prerequisite_groups", requirements);
                put("research_rules", project.namespace() + ":designer/node/" + node.id(), rule, node.id());
                var placement = node.placement().json();
                if (placement.size() == 0) issue("unplaced_node", node.id(), pointer + "/placement", "Node has no placement.", "Choose a domain, lane and rank.");
                else { placement.add("target", target); entries.add(placement); }
                node.branchId().ifPresent(b -> memberships.computeIfAbsent(b, ignored -> new JsonArray()).add(bp));
                // Decode here as well to associate malformed policy errors with the exact editor node.
                try { decode(BlueprintResearchRule.CODEC, rule); }
                catch (RuntimeException e) { issue("invalid_policy", node.id(), pointer + "/policy", message(e), "Correct the policy values shown in this node."); }
            }
            var bundle = new JsonObject(); bundle.addProperty("format", 3); bundle.addProperty("tree", tree.id()); bundle.add("entries", entries);
            var members = new JsonArray(); memberships.forEach((branch, ids) -> {
                var membership = new JsonObject(); membership.addProperty("branch", branch); membership.add("members", ids); members.add(membership);
            }); bundle.add("branch_memberships", members);
            put("research_tech_tree_entries", project.namespace() + ":designer/entries", bundle, "tree");
            if (hasErrors()) return result(null);
            try {
                var snapshot = decodeResources(resources);
                if (!snapshot.profiles().get(new ResourceLocation(profile.id())).techTree().equals(Optional.of(new ResourceLocation(tree.id()))))
                    throw new IllegalArgumentException("Project profile must select its owned tree");
                if (snapshot.techTrees().get(new ResourceLocation(tree.id())).usesAutomaticWeaponPlacement())
                    throw new IllegalArgumentException("Custom drafts must use authored_only placement");
                ResearchTechTreeCatalogValidator.validate(snapshot, catalog);
                var selectedProfile = snapshot.profiles().get(new ResourceLocation(profile.id()));
                var domainSettings = capture.map(c -> DesignerCapturedSettings.progression(
                        c, new ResourceLocation(profile.id())).domainSettings()).orElse(
                                com.gamergaming.taczweaponblueprints.progression.ResearchDomainSettings.PROFILE_DEFAULTS);
                for (var domain : com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain.values()) {
                    var gate = domainSettings.apply(domain, selectedProfile.domainPolicy(domain));
                    if ((!gate.treeEnabled() || !gate.researchEnabled()) && draft.nodes().stream().anyMatch(n -> n.blueprintId()
                            .map(ResourceLocation::new).map(catalog::get).filter(Objects::nonNull)
                            .map(data -> com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain.forKind(data.getKind()) == domain).orElse(false)))
                        diagnostics.add(new Diagnostic("domain_disabled", Severity.WARNING, "profile", "/draft/profile/definition/domain_policies",
                                domain.name().toLowerCase(Locale.ROOT) + " contains nodes but its effective category gate hides or disables research.",
                                "Check this profile's category gates and the captured server category settings if these nodes should be available."));
                }
                validateMaterials(resources, "");
                if (hasErrors()) return result(null);
                // Round-trip the complete source through ordinary codecs again; no loader or reload is invoked.
                var roundTrip = decodeResources(DesignerJson.parse(DesignerJson.bytes(resources, DesignerJson.RECOVERY_BYTES), DesignerJson.RECOVERY_BYTES));
                diagnostics.add(new Diagnostic("scenario_validation_required", Severity.INFO, "project", "",
                        "Compilation validates definitions and structure; inventory readiness, route budgets and capture equivalence require scenario evaluation.",
                        "Run detached scenario checks before exporting or activating this candidate."));
                return result(new Candidate(project.digest(), new ResourceLocation(profile.id()), roundTrip, new DesignerJson.ObjectValue(resources)));
            } catch (java.util.concurrent.CancellationException cancelled) { throw cancelled;
            } catch (RuntimeException e) {
                issue("runtime_validation", "project", "/draft", message(e), "Correct the referenced definition or dependency and validate again.");
                return result(null);
            }
        }
        boolean hasErrors() { return diagnostics.stream().anyMatch(d -> d.severity() == Severity.ERROR); }
        Result result(Candidate candidate) { return new Result(project.digest(), Optional.ofNullable(candidate), diagnostics); }
        void validateMaterials(JsonElement value, String pointer) {
            checkCancelled();
            if (value.isJsonArray()) {
                for (int i = 0; i < value.getAsJsonArray().size(); i++) validateMaterials(value.getAsJsonArray().get(i), pointer + "/" + i);
            } else if (value.isJsonObject()) {
                var object = value.getAsJsonObject();
                if (object.has("type") && object.has("id") && object.has("scope") && object.has("message")) {
                    String type = object.get("type").getAsString();
                    if (type.equals("advancement") && capture.isPresent()) {
                        var advancements = capture.orElseThrow().json().getAsJsonObject("registry_facts").getAsJsonObject("advancements");
                        if ((advancements == null || !advancements.has(object.get("id").getAsString())) && diagnostics.size() < 999)
                            diagnostics.add(new Diagnostic("missing_advancement", Severity.WARNING, "project", pointer,
                                    "The captured context does not contain advancement " + object.get("id").getAsString(),
                                    "Restore the advancement or change this gate; another OR alternative may still satisfy the group."));
                    } else if (type.equals("criterion") && diagnostics.size() < 999) {
                        diagnostics.add(new Diagnostic("external_gate_unassessed", Severity.INFO, "project", pointer,
                                "Criterion availability depends on its external provider: " + object.get("id").getAsString(),
                                "Check this gate using explicit simulated criterion values and verify its provider on the server."));
                    }
                }
                if (object.has("count") && (object.has("items") || object.has("tag"))) {
                    var ingredient = decode(BlueprintResearchIngredient.CODEC, object);
                    if (capture.isPresent()) {
                        var facts = capture.orElseThrow().json().getAsJsonObject("registry_facts");
                        var items = facts.getAsJsonObject("items"); var tags = facts.getAsJsonObject("item_tags");
                        for (var id : ingredient.items()) if (items == null || !items.has(id.toString()))
                            issue("missing_item", "project", pointer, "Item is absent from the captured context: " + id,
                                    "Capture a context containing this item before validating the new cost.");
                        ingredient.tag().ifPresent(id -> {
                            if (tags == null || !tags.has(id.toString())) issue("missing_item_tag", "project", pointer,
                                    "Item tag is absent from the captured context: " + id, "Capture the required tag before validating this cost.");
                            else if (tags.getAsJsonArray(id.toString()).isEmpty() && diagnostics.size() < 999) diagnostics.add(new Diagnostic("empty_item_tag", Severity.WARNING,
                                    "project", pointer, "No item currently matches " + id, "Populate this tag before players need to pay the cost."));
                        });
                    }
                    return;
                }
                object.entrySet().forEach(e -> validateMaterials(e.getValue(), pointer + "/" + e.getKey()));
            }
        }
        void dependencies() {
            var root = project.json(); var deps = root.getAsJsonObject("dependencies");
            boolean required = root.getAsJsonObject("origin").has("capture_id")
                    || deps.entrySet().stream().anyMatch(e -> e.getValue().isJsonArray() && e.getValue().getAsJsonArray().size() > 0);
            if (capture.isEmpty()) {
                if (required) issue("missing_context", "project", "/dependencies", "The project dependency context is unavailable.", "Open the matching saved capture or capture the source server again.");
                return;
            }
            var context = capture.orElseThrow().json(); var origin = root.getAsJsonObject("origin");
            if (origin.has("content_fingerprint") && !origin.get("content_fingerprint").getAsString().equals(capture.orElseThrow().fingerprint()))
                issue("stale_context", "project", "/origin", "Capture fingerprint differs from this project's origin.", "Use the original capture or explicitly rebase the project.");
            var assumed = root.getAsJsonObject("settings_assumptions");
            var capturedSettings = context.getAsJsonObject("settings");
            for (var key : List.of("raw", "effective")) if (assumed.has(key) && !Objects.equals(assumed.get(key), capturedSettings.get(key)))
                issue("settings_mismatch", "project", "/settings_assumptions/" + key,
                        "Project settings assumptions differ from the capture.", "Choose a matching context or explicitly rebase the assumptions.");
            if (capturedSettings.has("effective") && capturedSettings.getAsJsonObject("effective").has("progression")) {
                var progression = DesignerCapturedSettings.progression(capture.orElseThrow(), new ResourceLocation(project.draft().profile().id()));
                var items = context.getAsJsonObject("registry_facts").getAsJsonObject("items");
                progression.currencyPolicy().item().ifPresent(id -> {
                    if (items != null && (!items.has(id.toString()) || items.get(id.toString()).getAsInt() < 1 || items.get(id.toString()).getAsInt() > 64))
                        issue("invalid_currency", "project", "/settings_assumptions", "Currency item is unavailable or has an unsupported stack size: " + id,
                                "Capture a valid currency item and its current stack size.");
                });
            }
            for (var e : deps.getAsJsonArray("mods")) {
                var dep = e.getAsJsonObject(); var actual = context.getAsJsonObject("dependencies").getAsJsonObject("mods").get(dep.get("id").getAsString());
                if (actual == null || !actual.equals(dep.get("version"))) issue("mod_dependency", "project", "/dependencies/mods", "A captured mod version does not match: " + dep.get("id"), "Restore the declared version or rebase the project.");
            }
            for (var entry : catalog.entrySet()) {
                checkCancelled();
                var captured = context.getAsJsonObject("catalog").getAsJsonObject(entry.getKey().toString());
                var data = entry.getValue();
                if (captured == null || !captured.get("kind").getAsString().equalsIgnoreCase(data.getKind().name())
                        || !captured.get("item_type").getAsString().equals(data.getItemType())
                        || !captured.get("recipe_id").getAsString().equals(data.getRecipeId().toString())
                        || captured.get("output_count").getAsInt() != data.getCanonicalOutputCount()
                        || !Objects.equals(captured.get("name_key"), DesignerValues.evidence(data.getNameKey()))
                        || !Objects.equals(captured.get("display_slot"), DesignerValues.evidence(data.getDisplaySlotKey())))
                    issue("catalog_context_mismatch", "project", "/dependencies/content", "Evaluation catalog differs from capture: " + entry.getKey(),
                            "Use the matching captured catalog.");
            }
            for (var e : deps.getAsJsonArray("content")) {
                var dep = e.getAsJsonObject(); var actual = context.getAsJsonObject("catalog").get(dep.get("id").getAsString());
                if (actual == null || !DesignerFingerprint.of(actual).equals(dep.get("fingerprint").getAsString())) issue("content_dependency", "project", "/dependencies/content", "A content dependency differs: " + dep.get("id"), "Restore the content dependency or rebase the project.");
            }
            for (var e : deps.getAsJsonArray("resources")) {
                var dep = e.getAsJsonObject(); String kind = dep.get("kind").getAsString(), id = dep.get("id").getAsString();
                var family = context.getAsJsonObject("definitions").getAsJsonObject(kind);
                var record = family == null ? null : family.getAsJsonObject(id);
                if (record == null || !DesignerFingerprint.of(record.get("definition")).equals(dep.get("fingerprint").getAsString()))
                    issue("resource_dependency", id, "/dependencies/resources", "A resource dependency differs: " + id, "Restore the declared resource or rebase the project.");
                else put(kind, id, record.getAsJsonObject("definition"), id);
            }
        }
    }

    public static BlueprintResearchSnapshot decodeResources(JsonObject resources) {
        checkCancelled();
        if (!Set.of("blueprint_tags", "research_profiles", "research_rules", "research_tree_groups",
                "research_tech_trees", "research_tech_tree_entries", "research_automatic_placement_profiles",
                "research_data", "analysis_reconstruction").containsAll(resources.keySet()))
            throw new IllegalArgumentException("Unsupported compiled resource family");
        var snapshot = BlueprintResearchSnapshot.create(family(resources, "blueprint_tags", BlueprintLootTag.CODEC),
                family(resources, "research_profiles", BlueprintResearchProfile.CODEC),
                family(resources, "research_rules", BlueprintResearchRule.CODEC),
                family(resources, "research_tree_groups", ResearchTreeGroupDefinition.CODEC),
                family(resources, "research_tech_trees", ResearchTechTreeDefinition.CODEC),
                family(resources, "research_tech_tree_entries", ResearchTechTreeEntryBundle.CODEC),
                family(resources, "research_automatic_placement_profiles", ResearchAutomaticPlacementProfile.CODEC));
        return snapshot.withResearchData(new ResearchDataSnapshot(family(resources, "research_data", ResearchDataPolicy.CODEC),
                family(resources, "analysis_reconstruction", AnalysisReconstructionDefinition.CODEC)));
    }
    private static <T> Map<ResourceLocation, T> family(JsonObject resources, String name, Codec<T> codec) {
        var result = new TreeMap<ResourceLocation, T>(); var source = resources.getAsJsonObject(name);
        if (source != null) source.entrySet().forEach(e -> { checkCancelled(); result.put(new ResourceLocation(e.getKey()), decode(codec, e.getValue())); });
        return result;
    }
    private static <T> T decode(Codec<T> codec, JsonElement value) {
        return codec.parse(JsonOps.INSTANCE, value).getOrThrow(false, ignored -> {});
    }
    private static String message(RuntimeException error) {
        String text = Objects.toString(error.getMessage(), "Invalid definition");
        return text.length() > 2048 ? text.substring(0, 2048) : text;
    }
}
