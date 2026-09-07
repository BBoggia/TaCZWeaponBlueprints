package com.gamergaming.taczweaponblueprints.resource.research;

import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.*;
import com.gamergaming.taczweaponblueprints.resource.loot.BlueprintLootTag;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import net.minecraft.resources.ResourceLocation;
import java.util.*;
import java.util.function.*;
import static com.gamergaming.taczweaponblueprints.research.designer.DesignerValues.*;

/** Field-by-field conversion projection over a retained publication; no publication or file writes. */
public final class DesignerCaptureAssembly {
    private DesignerCaptureAssembly() {}
    public static JsonObject assemble(ProgressionPolicyAccessService.Context c,
            BlueprintProgressionConfigSnapshot progression, BlueprintAccessConfigSnapshot access,
            Predicate<String> blocked, JsonObject settings, JsonObject registryFacts, JsonObject dependencies,
            BiFunction<String, ResourceLocation, String> provenance) {
        return assemble(new Inputs(c.research().snapshot(), c.catalog().blueprints(), c.profileId(),
                c.automatic().snapshotsByTree(), c.automatic().prerequisitePlansByProfile(), c.profilePolicies(), c.profileCraftingPolicies()),
                progression, access, blocked, settings, registryFacts, dependencies, provenance);
    }
    public record Inputs(BlueprintResearchSnapshot snapshot, Map<ResourceLocation,BlueprintData> catalog,
            ResourceLocation profile, Map<ResourceLocation,com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateSnapshot> candidates,
            Map<ResourceLocation,AutomaticWeaponPrerequisitePlan> plans,
            Map<ResourceLocation,ResolvedBlueprintProgressionPolicy> researchPolicies,
            Map<ResourceLocation,ResolvedBlueprintCraftingPolicy> craftingPolicies) {}
    public static JsonObject assemble(Inputs c, BlueprintProgressionConfigSnapshot progression, BlueprintAccessConfigSnapshot access,
            Predicate<String> blocked, JsonObject settings, JsonObject registryFacts, JsonObject dependencies,
            BiFunction<String,ResourceLocation,String> provenance) {
        var snapshot = c.snapshot(); var catalog = c.catalog();
        if (catalog.size() > 4096) throw new IllegalArgumentException("Catalog exceeds capture node limit");
        var profile = snapshot.profiles().get(c.profile());
        if (profile == null || profile.techTree().isEmpty()) throw new IllegalArgumentException("No active Tech Tree");
        var treeId = profile.techTree().orElseThrow();
        var tree = snapshot.techTrees().get(treeId);
        if (tree == null) throw new IllegalArgumentException("Missing active Tech Tree");
        var context = new JsonObject(); var definitions = new JsonObject();
        add(definitions, "research_profiles", Map.of(c.profile(), profile), BlueprintResearchProfile.CODEC, provenance);
        add(definitions, "research_tech_trees", Map.of(treeId, tree), ResearchTechTreeDefinition.CODEC, provenance);
        add(definitions, "research_rules", select(snapshot.rules(), rule -> rule.profile().equals(c.profile())), BlueprintResearchRule.CODEC, provenance);
        add(definitions, "research_tree_groups", select(snapshot.groups(), group -> group.profile().equals(c.profile())), ResearchTreeGroupDefinition.CODEC, provenance);
        add(definitions, "research_tech_tree_entries", select(snapshot.techTreeEntryBundles(), bundle -> bundle.tree().equals(treeId)), ResearchTechTreeEntryBundle.CODEC, provenance);
        add(definitions, "research_automatic_placement_profiles", select(snapshot.automaticPlacementProfiles(), p -> p.tree().equals(treeId)), ResearchAutomaticPlacementProfile.CODEC, provenance);
        add(definitions, "research_data", select(snapshot.researchData().policies(), p -> p.profile().isEmpty() || p.profile().get().equals(c.profile())), ResearchDataPolicy.CODEC, provenance);
        add(definitions, "analysis_reconstruction", select(snapshot.researchData().reconstruction(), p -> p.profile().isEmpty() || p.profile().get().equals(c.profile())), AnalysisReconstructionDefinition.CODEC, provenance);
        // Only tags actually referenced by the selected research resource closure.
        Set<String> tagRefs = new HashSet<>(); collectTags(definitions, tagRefs);
        add(definitions, "blueprint_tags", selectIds(snapshot.tags(), id -> tagRefs.contains(id.toString())), BlueprintLootTag.CODEC, provenance);
        context.addProperty("profile_id", c.profile().toString()); context.addProperty("catalog_export_format", 22);
        context.add("definitions", definitions); context.add("settings", settings); context.add("registry_facts", registryFacts);
        context.add("dependencies", dependencies);
        var records = new JsonObject(); var nodes = new JsonObject(); var notes = new JsonArray();
        Predicate<ResourceLocation> exempt = id -> BlueprintProgressionAccess.isProgressionExempt(access, id, catalog.get(id));
        var replacements = BlueprintResearchPolicyResolver.entryPointReplacements(snapshot, catalog, c.profile(), blocked, exempt);
        var candidates = c.candidates().get(treeId);
        var plan = c.plans().get(c.profile());
        var analyzerSelector = BlueprintResearchPolicyResolver.analyzerRuleSelector(snapshot,c.profile());
        for (var entry : new TreeMap<>(catalog).entrySet()) {
            var id = entry.getKey(); var data = entry.getValue();
            var record = new JsonObject(); record.addProperty("blueprint_id", id.toString());
            record.addProperty("kind", data.getKind().serializedName()); record.addProperty("item_type", data.getItemType());
            record.addProperty("recipe_id", data.getRecipeId() == null ? "" : data.getRecipeId().toString());
            record.addProperty("name_key", data.getNameKey()); record.add("display_slot", evidence(data.getDisplaySlotKey()));
            record.addProperty("output_count", data.getCanonicalOutputCount());
            record.addProperty("exempt", exempt.test(id)); record.addProperty("blocked", blocked.test(id.toString()));
            records.add(id.toString(), record);
            var definition = BlueprintResearchPolicyResolver.definitionFor(snapshot, catalog, c.profile(), id);
            var node = new JsonObject(); var policy = new JsonObject();
            definition.ruleId().ifPresent(rule -> {
                var source = encode(BlueprintResearchRule.CODEC, snapshot.rules().get(rule));
                for (String field : List.of("analyzer", "progression", "crafting")) if (source.has(field)) policy.add(field, source.get(field));
                node.addProperty("selected_rule", rule.toString());
            });
            var progressionSelection = BlueprintResearchPolicyResolver.researchProgressionRuleSelection(snapshot,c.profile(),id,data);
            progressionSelection.selectedRuleId().map(snapshot.rules()::get).flatMap(BlueprintResearchRule::progression)
                    .ifPresent(p -> policy.add("progression",encode(BlueprintProgressionRuleOverride.CODEC,p)));
            var analyzerSelection = analyzerSelector.apply(id,data);
            policy.remove("analyzer");
            analyzerSelection.selectedRuleId().map(snapshot.rules()::get).flatMap(BlueprintResearchRule::analyzer)
                    .ifPresent(a -> policy.add("analyzer",encode(BlueprintAnalyzerOverride.CODEC,a)));
            analyzerSelection.selectedRuleId().ifPresent(a -> node.addProperty("analyzer_rule",a.toString()));
            var selectedDefinition = definition.ruleId().map(snapshot.rules()::get);
            policy.addProperty("tree_enabled", selectedDefinition.flatMap(BlueprintResearchRule::treeEnabled).orElse(profile.treeEnabled()));
            // Omitting inherited visibility preserves discovery's ability to reveal an identity.
            selectedDefinition.flatMap(BlueprintResearchRule::visibility).ifPresent(v -> policy.addProperty("visibility",v.serializedName()));
            policy.addProperty("research_enabled", selectedDefinition.flatMap(BlueprintResearchRule::researchEnabled).orElse(profile.researchEnabled()));
            policy.addProperty("recycling_enabled", definition.recyclingEnabled());
            policy.addProperty("allow_unlearned_recycling", definition.allowUnlearnedRecycling());
            policy.addProperty("recycling_value", definition.recyclingValue());
            policy.add("research_cost", encode(BlueprintResearchCost.CODEC, definition.researchCost()));
            policy.addProperty("requires_discovery", definition.requiresDiscovery());
            policy.addProperty("creative_bypasses_cost", definition.creativeBypassesCost());
            policy.add("reverse_engineering", encode(BlueprintReverseEngineeringPolicy.CODEC, definition.reverseEngineering()));
            var researchPolicy = c.researchPolicies().get(id);
            if (researchPolicy != null) {
                node.add("progression_evidence", evidence(researchPolicy));
                JsonObject overrides = policy.has("progression") ? policy.getAsJsonObject("progression") : new JsonObject();
                overrides.add("research_tier", BlueprintProgressionCodecs.WORKBENCH_TIER.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, researchPolicy.researchWorkbenchTier()).getOrThrow(false, ignored -> {}));
                // Exactness affects fragment loot, not just diagnostics. Preserve inherited thresholds as inherited.
                if (researchPolicy.exactFragmentThreshold() && researchPolicy.fragments().threshold() > 0)
                    overrides.addProperty("fragment_threshold", researchPolicy.fragments().threshold());
                policy.add("progression", overrides);
            }
            var crafting = c.craftingPolicies().get(id);
            if (crafting != null) {
                node.add("crafting_evidence", evidence(crafting));
                // Preserve the independently selected crafting gates; the profile supplies its own gates.
                var craft = new JsonObject(); craft.addProperty("disposition", crafting.disposition().serializedName());
                crafting.requiredWorkbenchTier().ifPresent(t -> craft.add("workbench_tier", BlueprintProgressionCodecs.WORKBENCH_TIER.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, t).getOrThrow(false, ignored -> {})));
                crafting.selectedRuleId().map(snapshot.rules()::get).ifPresent(rule -> {
                    var source = encode(BlueprintResearchRule.CODEC, rule);
                    if (source.has("crafting") && source.getAsJsonObject("crafting").has("gates")) craft.add("gates", source.getAsJsonObject("crafting").get("gates"));
                });
                if (crafting.gates().conditionCount() > 0) craft.add("gates",BlueprintProgressionCodecs.GATE_REQUIREMENTS.encodeStart(com.mojang.serialization.JsonOps.INSTANCE,crafting.gates()).getOrThrow(false, ignored -> {}));
                policy.add("crafting", craft);
            }
            // Use exactly the runtime exemption, entry-point and automatic replacement semantics.
            var structural = BlueprintResearchPolicyResolver.resolve(snapshot, catalog, c.profile(), id, null, blocked, exempt);
            structural = AutomaticWeaponPrerequisiteOverlay.apply(progression.apply(structural), plan, null, blocked,
                    progression.maximumUndiscoveredVisibility().allowsServerSelection(), catalog::containsKey, exempt, replacements,
                    snapshot.usesAutomaticWeaponPlacement(c.profile()) && data.getKind() == BlueprintKind.GUN);
            node.add("effective_policy", evidence(structural));
            boolean automaticWeapon = snapshot.usesAutomaticWeaponPlacement(c.profile()) && data.getKind() == BlueprintKind.GUN;
            // Materialize topology independently of temporary player discovery. Keep the actual
            // empty-player projection separately as evidence, never as the future graph's source.
            var materialized = automaticWeapon ? AutomaticWeaponPrerequisiteOverlay.apply(structural,plan,null,blocked,
                    true,catalog::containsKey,exempt,replacements,true) : structural;
            var requirements = automaticWeapon ? materialized.requirements() : definition.requirements();
            if (automaticWeapon && plan != null) node.add("automatic_requirements",evidence(plan.requirementsFor(id)));
            var groups = new JsonArray();
            requirements.allOf().forEach(g -> { var group = new JsonObject(); group.add("any_of", evidence(g.anyOf())); groups.add(group); });
            node.add("requirements", groups); node.add("policy", policy);
            var selection = ResearchTechTreePlacementResolver.resolveWithAutomaticForProfile(snapshot, c.profile(), treeId, id, data, candidates);
            var placement = selection.base().placement();
            placement.ifPresent(p -> {
                var mapped = new JsonObject(); mapped.addProperty("domain", p.domain().name().toLowerCase(Locale.ROOT));
                mapped.addProperty("lane", p.lane().toString()); mapped.addProperty("tier", p.tier().name().toLowerCase(Locale.ROOT));
                mapped.addProperty("rank", p.progressionCoordinate().rank()); mapped.addProperty("order", p.order());
                node.add("placement", mapped); node.add("placement_evidence", evidence(p));
            });
            selection.automaticProposal().ifPresent(p -> {
                var mapped = new JsonObject(); mapped.addProperty("domain", "weapons");
                mapped.addProperty("lane", placement.map(ResearchTechTreePlacementResolver.Placement::lane).orElseGet(() -> tree.domain(com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain.WEAPONS).orElseThrow().fallbackLane()).toString());
                mapped.addProperty("tier", p.position().tier().name().toLowerCase(Locale.ROOT));
                mapped.addProperty("rank", p.progressionCoordinate().rank()); mapped.addProperty("order", 0);
                node.add("placement", mapped); node.add("automatic_evidence", evidence(p));
                if (plan != null) plan.branchCoordinateFor(id).ifPresent(b -> node.add("automatic_branch", evidence(b)));
            });
            snapshot.authoredBranches(treeId).stream().filter(b -> b.members().contains(id)).findFirst()
                    .ifPresent(b -> node.addProperty("branch_id", b.id().toString()));
            if (!node.has("placement")) note(notes, "unplaced", id);
            if (candidates != null && candidates.excludedAutomaticCandidates().containsKey(id.toString())) {
                node.addProperty("automatic_omission",candidates.excludedAutomaticCandidates().get(id.toString()));
                note(notes,"automatic_omission",id);
            }
            if (researchPolicy == null) note(notes, "progression_omitted", id);
            if (exempt.test(id)) note(notes, "progression_exempt", id);
            if (data.getRecipeId() == null) note(notes, "missing_recipe", id);
            nodes.add(id.toString(), node);
        }
        // Compress automatic sibling order into the bounded authored order field without changing its order.
        var automaticNodes = nodes.entrySet().stream().filter(e -> e.getValue().getAsJsonObject().has("automatic_evidence"))
                .sorted(Comparator.<Map.Entry<String,JsonElement>>comparingLong(e -> e.getValue().getAsJsonObject().getAsJsonObject("automatic_evidence").getAsJsonObject("progressionCoordinate").get("siblingOrder").getAsLong()).thenComparing(Map.Entry::getKey)).toList();
        for (int i=0;i<automaticNodes.size();i++) automaticNodes.get(i).getValue().getAsJsonObject().getAsJsonObject("placement").addProperty("order",i);
        Set<String> referencedBlueprints=new TreeSet<>(); collectBlueprints(definitions,referencedBlueprints,false);
        for(String reference:referencedBlueprints) if(!records.has(reference)) note(notes,"missing_reference",new ResourceLocation(reference));
        context.add("catalog", records); context.add("resolved_nodes", nodes); context.add("conversion_notes", notes);
        context.addProperty("context_completeness", "complete");
        return context;
    }
    private static void note(JsonArray notes, String code, ResourceLocation id) {
        if (notes.size() == 1000) throw new IllegalArgumentException("Too many conversion notes");
        var note = new JsonObject(); note.addProperty("code", code); note.addProperty("blueprint_id", id.toString()); notes.add(note);
    }
    private static <T> Map<ResourceLocation,T> select(Map<ResourceLocation,T> map, Predicate<T> predicate) {
        var result = new TreeMap<ResourceLocation,T>(); map.forEach((id,v) -> { if (predicate.test(v)) result.put(id,v); }); return result;
    }
    private static <T> Map<ResourceLocation,T> selectIds(Map<ResourceLocation,T> map, Predicate<ResourceLocation> predicate) {
        var result = new TreeMap<ResourceLocation,T>(); map.forEach((id,v) -> { if (predicate.test(id)) result.put(id,v); }); return result;
    }
    private static <T> void add(JsonObject definitions, String kind, Map<ResourceLocation,T> values, Codec<T> codec,
            BiFunction<String,ResourceLocation,String> provenance) {
        var family = new JsonObject(); new TreeMap<>(values).forEach((id,v) -> {
            var source = new JsonObject(); source.add("definition", encode(codec,v)); source.addProperty("content_fingerprint",DesignerFingerprint.of(source.get("definition")));
            source.addProperty("source_pack", provenance.apply(kind,id)); source.addProperty("representation", "typed_runtime");
            family.add(id.toString(),source);
        }); definitions.add(kind,family);
    }
    private static void collectTags(JsonElement value, Set<String> tags) {
        if (value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(e -> {
            if (e.getKey().equals("tags") && e.getValue().isJsonArray()) e.getValue().getAsJsonArray().forEach(v -> tags.add(v.getAsString()));
            else collectTags(e.getValue(),tags);
        }); else if (value.isJsonArray()) value.getAsJsonArray().forEach(v -> collectTags(v,tags));
    }
    private static void collectBlueprints(JsonElement value,Set<String> result,boolean blueprintList) {
        if(value.isJsonObject()) value.getAsJsonObject().entrySet().forEach(e -> collectBlueprints(e.getValue(),result,
                Set.of("blueprints","values","prerequisites","members","anchor","entry_point_candidates","tech_entry_point_candidates","any_of").contains(e.getKey()) || blueprintList && !e.getValue().isJsonObject()));
        else if(value.isJsonArray()) value.getAsJsonArray().forEach(v -> collectBlueprints(v,result,blueprintList && !v.isJsonObject()));
        else if(blueprintList && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()
                && value.getAsString().contains(":") && ResourceLocation.tryParse(value.getAsString())!=null) result.add(value.getAsString());
    }

}
