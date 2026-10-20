package com.gamergaming.taczweaponblueprints.research.designer;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Stream;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.research.tree.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.google.gson.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.JsonOps;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

/** Contract fixtures exercise today's loaders; this is not the future Designer compiler. */
class ResearchDesignerContractTest {
    static final Path ROOT = Path.of("src/test/resources/research-designer");
    static final Path CONTRACTS = Path.of("docs/contracts/research-designer");
    static JsonObject read(Path path) {
        try { return JsonParser.parseString(Files.readString(path)).getAsJsonObject(); }
        catch (Exception e) { throw new IllegalArgumentException(path.toString(), e); }
    }
    static Stream<JsonObject> cases() {
        return read(ROOT.resolve("cases.json")).getAsJsonArray("cases").asList().stream().map(JsonElement::getAsJsonObject);
    }
    static Stream<JsonObject> runtimeCases() {
        return cases().filter(c -> c.getAsJsonObject("expected").get("exportable").getAsBoolean());
    }
    static JsonObject named(String id) { return cases().filter(c -> c.get("id").getAsString().equals(id)).findFirst().orElseThrow(); }
    static JsonObject project(JsonObject c) { return read(ROOT.resolve(c.get("project").getAsString())); }
    static ResourceLocation id(String value) { return ResourceLocation.parse(value); }
    static <T> T decode(Codec<T> codec, JsonElement json) {
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
    }
    static <T> void roundTrip(Codec<T> codec, T value) {
        assertEquals(value, decode(codec, codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow()));
    }

    @ParameterizedTest @MethodSource("cases")
    void projectEnvelopesAndStableIdsAreWellFormedIncludingUnfinishedDrafts(JsonObject c) {
        var p = project(c); var contract = read(CONTRACTS.resolve("contract.json"));
        assertEquals(1, p.get("format").getAsInt());
        assertEquals(p.get("project_id").getAsString(), UUID.fromString(p.get("project_id").getAsString()).toString());
        assertEquals(new TreeSet<>(contract.getAsJsonArray("project_required").asList().stream().map(JsonElement::getAsString).toList()), new TreeSet<>(p.keySet()));
        Set<String> editorIds = new HashSet<>(), blueprints = new HashSet<>();
        for (var element : p.getAsJsonObject("draft").getAsJsonArray("nodes")) {
            var n = element.getAsJsonObject();
            assertTrue(n.get("id").getAsString().matches("[a-z0-9_-]{1,64}"));
            assertTrue(editorIds.add(n.get("id").getAsString()));
            if (n.has("blueprint_id")) assertTrue(blueprints.add(n.get("blueprint_id").getAsString()));
            for (var group : n.getAsJsonArray("requirements")) assertTrue(editorIds.add(group.getAsJsonObject().get("id").getAsString()));
            assertFalse(n.getAsJsonObject("policy").has("prerequisites"));
            assertFalse(n.getAsJsonObject("policy").has("prerequisite_groups"));
        }
        assertTrue(p.getAsJsonObject("draft").getAsJsonArray("nodes").size() <= contract.getAsJsonObject("limits").get("draft_nodes").getAsInt());
        assertEquals(c.getAsJsonArray("catalog").size(), c.getAsJsonObject("expected").get("catalog_count").getAsInt());
    }

    @ParameterizedTest @MethodSource("runtimeCases")
    void normalCodecsAndPublicTreePreserveExpectedFixtureSemantics(JsonObject c) {
        var fixture = compileFixture(project(c), c);
        var expected = c.getAsJsonObject("expected");
        var publication = fixture.publication(new PlayerRecipeData());
        assertEquals(expected.get("public_nodes").getAsInt(), publication.graph().nodes().size());
        assertEquals(expected.get("public_edges").getAsInt(), publication.graph().edges().size());
        assertEquals(expected.get("total_points").getAsInt(), publication.graph().nodes().stream().mapToInt(ResearchTreeGraph.Node::pointCost).sum());
        for (var hidden : expected.getAsJsonArray("hidden_ids")) {
            assertTrue(publication.graph().node(id(hidden.getAsString())).isEmpty());
            assertTrue(publication.graph().edges().stream().noneMatch(e -> e.prerequisiteId().toString().equals(hidden.getAsString()) || e.dependentId().toString().equals(hidden.getAsString())));
        }
        fixture.snapshot.rules().values().forEach(rule -> roundTrip(BlueprintResearchRule.CODEC, rule));
        roundTrip(ResearchTechTreeDefinition.CODEC, fixture.snapshot.techTrees().values().iterator().next());
        roundTrip(ResearchTechTreeEntryBundle.CODEC, fixture.snapshot.techTreeEntryBundles().values().iterator().next());
    }

    @Test void inclusiveAlternativesStayInclusiveAndMandatorySupportIsIndependent() {
        var c = named("inclusive_or"); var f = compileFixture(project(c), c);
        var player = new PlayerRecipeData();
        for (var s : List.of("root", "left", "support")) player.addBlueprint("designer_fixture:" + s);
        var graph = f.publication(player).graph();
        assertEquals(ResearchTreeGraph.State.AVAILABLE, graph.node(id("designer_fixture:end")).orElseThrow().state());
        assertEquals(2, graph.requirementGroupsOf(id("designer_fixture:end")).size());
        player.addBlueprint("designer_fixture:right");
        assertEquals(ResearchTreeGraph.State.AVAILABLE, f.publication(player).graph().node(id("designer_fixture:end")).orElseThrow().state());
    }

    @Test void visualBranchesDoNotChangeProgression() {
        var c = named("nested"); var p = project(c); var original = compileFixture(p, c).publication(new PlayerRecipeData());
        assertEquals(3, original.techTree().domains().get(0).branches().size());
        p.getAsJsonObject("draft").getAsJsonObject("tree").getAsJsonObject("definition").add("branches", new JsonArray());
        p.getAsJsonObject("draft").getAsJsonArray("nodes").forEach(n -> n.getAsJsonObject().remove("branch_id"));
        assertEquals(original.graph(), compileFixture(p, c).publication(new PlayerRecipeData()).graph());
    }

    @Test void syntheticResolvedCapturePinsTheFutureConversionComparison() throws Exception {
        var capture = read(ROOT.resolve("synthetic-resolved-capture.json")); var p = project(named("automatic_conversion"));
        assertEquals(new TreeSet<>(read(CONTRACTS.resolve("contract.json")).getAsJsonArray("capture_required").asList().stream().map(JsonElement::getAsString).toList()), new TreeSet<>(capture.keySet()));
        assertEquals("fixture_only", capture.get("context_completeness").getAsString());
        assertEquals(capture.get("resolved_nodes"), p.getAsJsonObject("draft").get("nodes"));
        assertEquals(capture.get("dependency_fingerprint"), p.getAsJsonObject("origin").get("content_fingerprint"));
        assertEquals(capture.get("dependency_fingerprint").getAsString(), syntheticDigest(capture));
        assertEquals("authored_only", p.getAsJsonObject("draft").getAsJsonObject("tree").getAsJsonObject("definition").get("weapon_placement_mode").getAsString());
    }

    @Test void unfinishedCasesRemainExplicitInsteadOfSubstitutingContent() {
        assertEquals(0, project(named("blank")).getAsJsonObject("draft").getAsJsonArray("nodes").size());
        var unassigned = project(named("unassigned")).getAsJsonObject("draft").getAsJsonArray("nodes");
        assertEquals(1, unassigned.size());
        assertFalse(unassigned.get(0).getAsJsonObject().has("blueprint_id"));
        var c = named("missing"); var p = project(c);
        assertFalse(c.getAsJsonObject("expected").get("exportable").getAsBoolean());
        assertEquals("designer_fixture:missing", p.getAsJsonObject("draft").getAsJsonArray("nodes").get(1).getAsJsonObject().get("blueprint_id").getAsString());
        assertFalse(c.getAsJsonArray("catalog").asList().stream().anyMatch(n -> n.getAsJsonObject().get("blueprint_id").getAsString().equals("designer_fixture:missing")));
    }

    @Test void syntheticDigestChangesForCostsButNotSessionOrEditorIdentity() throws Exception {
        var capture = read(ROOT.resolve("synthetic-resolved-capture.json"));
        var original = syntheticDigest(capture);
        capture.addProperty("session_token", UUID.randomUUID().toString());
        capture.getAsJsonObject("revisions").addProperty("catalog", 99);
        var first = capture.getAsJsonArray("resolved_nodes").get(0).getAsJsonObject();
        first.addProperty("id", "renamed-editor-id");
        assertEquals(original, syntheticDigest(capture));
        first.getAsJsonObject("policy").getAsJsonObject("research_cost").addProperty("points", 900);
        assertNotEquals(original, syntheticDigest(capture));
    }

    @Test void diagnosticFixtureUsesKnownVocabularyAndReferencesTheMissingNode() {
        var diagnostic = read(ROOT.resolve("diagnostic.json")); var contract = read(CONTRACTS.resolve("contract.json"));
        assertEquals(new TreeSet<>(contract.getAsJsonArray("diagnostic_required").asList().stream().map(JsonElement::getAsString).toList()), new TreeSet<>(diagnostic.keySet()));
        assertEquals(contract.get("diagnostic_format"), diagnostic.get("format"));
        for (var pair : Map.of("code", "diagnostic_codes", "severity", "diagnostic_severities", "stage", "validation_stages").entrySet())
            assertTrue(contract.getAsJsonArray(pair.getValue()).contains(diagnostic.get(pair.getKey())));
        diagnostic.getAsJsonArray("blocks").forEach(stage -> assertTrue(contract.getAsJsonArray("validation_stages").contains(stage)));
        var missing = project(named("missing")).getAsJsonObject("draft").getAsJsonArray("nodes").get(1).getAsJsonObject();
        assertEquals(missing.get("id"), diagnostic.get("object_id"));
        assertEquals("/draft/nodes/1/blueprint_id", diagnostic.get("pointer").getAsString());
        assertEquals(missing.get("blueprint_id"), diagnostic.getAsJsonArray("arguments").get(0));
    }

    @Test void existingRuntimeRejectsCyclesAndInvalidCostAmounts() {
        var c = named("split_merge"); var p = project(c);
        p.getAsJsonObject("draft").getAsJsonArray("nodes").get(0).getAsJsonObject().add("requirements",
                JsonParser.parseString("[{\"id\":\"cycle\",\"any_of\":[\"designer_fixture:end\"]}]"));
        assertThrows(RuntimeException.class, () -> compileFixture(p, c));
        var cost = BlueprintResearchCost.CODEC;
        assertTrue(cost.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"points\":-1}")).error().isPresent());
        assertTrue(cost.parse(JsonOps.INSTANCE, JsonParser.parseString("{\"points\":1000000001}")).error().isPresent());
    }

    @Test void aCostOnlyWinningRuleDoesNotInheritThePreviousRulesPrerequisites() {
        var c = named("split_merge"); var f = compileFixture(project(c), c);
        assertEquals(2, f.publication(new PlayerRecipeData()).graph().requirementGroupsOf(id("designer_fixture:end")).size());
        var replacement = decode(BlueprintResearchRule.CODEC, JsonParser.parseString("""
                {"format":2,"profile":"designer_fixture:profile","priority":100,
                 "target":{"blueprints":["designer_fixture:end"]},"research_cost":{"points":9}}
                """));
        var rules = new LinkedHashMap<>(f.snapshot.rules());
        rules.put(id("designer_fixture:replacement"), replacement);
        var old = f.snapshot;
        var snapshot = BlueprintResearchSnapshot.create(old.tags(), old.profiles(), rules,
                old.groups(), old.techTrees(), old.techTreeEntryBundles());
        var graph = new Fixture(f.profile, f.catalog, snapshot).publication(new PlayerRecipeData()).graph();
        assertEquals(9, graph.node(id("designer_fixture:end")).orElseThrow().pointCost());
        assertTrue(graph.requirementGroupsOf(id("designer_fixture:end")).isEmpty(),
                "Conversion must preserve a complete winning overlay, not assume field-by-field rule merging");
    }

    @Test void fieldInventoryCoversEveryMappedRecordAndSharedConfigField() throws Exception {
        var inventory = read(CONTRACTS.resolve("field-inventory.json"));
        for (var entry : inventory.getAsJsonArray("records")) {
            var record = entry.getAsJsonObject(); var cls = Class.forName(record.get("class").getAsString());
            var fields = new TreeSet<String>(); for (var component : cls.getRecordComponents()) fields.add(component.getName());
            assertEquals(fields, new TreeSet<>(record.getAsJsonObject("fields").keySet()), cls.getSimpleName());
        }
        var actual = new TreeSet<String>();
        for (var field : BlueprintConfig.class.getFields()) if (ValidatedField.class.isAssignableFrom(field.getType())) actual.add(field.getName());
        assertEquals(actual, new TreeSet<>(inventory.getAsJsonArray("external_config_fields").asList().stream().map(JsonElement::getAsString).toList()));
    }

    @Test void versionAndLimitInventoryMatchesTheCurrentRuntime() throws Exception {
        var contract = read(CONTRACTS.resolve("contract.json"));
        for (var entry : contract.getAsJsonObject("runtime_formats").entrySet()) {
            var f = entry.getValue().getAsJsonObject(); var cls = Class.forName("com.gamergaming.taczweaponblueprints.resource.research." + f.get("class").getAsString());
            assertEquals(f.get("current").getAsInt(), cls.getField(f.get("constant").getAsString()).getInt(null));
        }
        var limits = contract.getAsJsonObject("limits");
        assertEquals(ResearchTreeGraph.MAX_NODES, limits.get("draft_nodes").getAsInt());
        assertEquals(ResearchTechTreeBranch.MAX_BRANCHES, limits.get("draft_branches").getAsInt());
        assertEquals(ResearchTechTreeBranch.MAX_DEPTH, limits.get("branch_depth").getAsInt());
        assertEquals(ResearchTreeGraph.MAX_REQUIREMENT_GROUPS, limits.get("requirement_groups").getAsInt());
    }

    /** Fixed fixture translation only. Production editing/compilation is implemented in later milestones. */
    static Fixture compileFixture(JsonObject p, JsonObject c) {
        var draft = p.getAsJsonObject("draft"); var profileEntry = draft.getAsJsonObject("profile"); var treeEntry = draft.getAsJsonObject("tree");
        var profileId = id(profileEntry.get("id").getAsString()); var treeId = id(treeEntry.get("id").getAsString());
        var profile = decode(BlueprintResearchProfile.CODEC, profileEntry.get("definition"));
        var tree = decode(ResearchTechTreeDefinition.CODEC, treeEntry.get("definition"));
        Map<ResourceLocation, BlueprintData> catalog = new LinkedHashMap<>();
        for (var entry : c.getAsJsonArray("catalog")) {
            var data = entry.getAsJsonObject(); var bp = id(data.get("blueprint_id").getAsString());
            var kind = BlueprintKind.valueOf(data.get("kind").getAsString().toUpperCase(Locale.ROOT));
            catalog.put(bp, new BlueprintData(bp.toString(), "name." + bp.getPath(), "tooltip." + bp.getPath(), id(bp.getNamespace() + ":recipe/" + bp.getPath()), null, "rifle", bp, kind));
        }
        var rules = new LinkedHashMap<ResourceLocation, BlueprintResearchRule>(); var entries = new JsonArray();
        var members = new TreeMap<String, JsonArray>(); int index = 0;
        for (var element : draft.getAsJsonArray("nodes")) {
            var n = element.getAsJsonObject(); var target = new JsonObject(); var ids = new JsonArray(); ids.add(n.get("blueprint_id")); target.add("blueprints", ids);
            var rule = n.getAsJsonObject("policy").deepCopy(); rule.addProperty("format", 2); rule.addProperty("profile", profileId.toString()); rule.add("target", target);
            var groups = new JsonArray();
            for (var g : n.getAsJsonArray("requirements")) { var group = g.getAsJsonObject().deepCopy(); group.remove("id"); groups.add(group); }
            rule.add("prerequisite_groups", groups);
            rules.put(id("designer_fixture:rule/" + index++), decode(BlueprintResearchRule.CODEC, rule));
            var placement = n.getAsJsonObject("placement").deepCopy(); placement.add("target", target); entries.add(placement);
            if (n.has("branch_id")) members.computeIfAbsent(n.get("branch_id").getAsString(), ignored -> new JsonArray()).add(n.get("blueprint_id"));
        }
        var memberships = new JsonArray(); members.forEach((branch, ids) -> {var m = new JsonObject(); m.addProperty("branch", branch); m.add("members", ids); memberships.add(m);});
        var bundle = new JsonObject(); bundle.addProperty("format", 3); bundle.addProperty("tree", treeId.toString()); bundle.add("entries", entries); bundle.add("branch_memberships", memberships);
        var snapshot = BlueprintResearchSnapshot.create(Map.of(), Map.of(profileId, profile), rules, Map.of(), Map.of(treeId, tree), Map.of(treeId, decode(ResearchTechTreeEntryBundle.CODEC, bundle)));
        ResearchTechTreeCatalogValidator.validate(snapshot, catalog);
        return new Fixture(profileId, catalog, snapshot);
    }

    /** ASCII/integer fixture subset only; not a production semantic-capture fingerprint adapter. */
    static String syntheticDigest(JsonObject capture) throws Exception {
        var payload = new JsonObject();
        for (var key : List.of("catalog", "resolved_nodes")) {
            var sorted = new JsonArray();
            capture.getAsJsonArray(key).asList().stream().map(e -> e.getAsJsonObject().deepCopy())
                    .sorted(Comparator.comparing(n -> n.get("blueprint_id").getAsString())).forEach(n -> {
                        if (key.equals("resolved_nodes")) {
                            n.remove("id");
                            n.getAsJsonArray("requirements").forEach(g -> g.getAsJsonObject().remove("id"));
                        }
                        sorted.add(n);
                    });
            payload.add(key, sorted);
        }
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(
                sortedObjectKeys(payload).toString().getBytes(StandardCharsets.UTF_8)));
    }

    static JsonElement sortedObjectKeys(JsonElement value) {
        if (value.isJsonObject()) {
            var sorted = new JsonObject(); var object = value.getAsJsonObject();
            new TreeSet<>(object.keySet()).forEach(key -> sorted.add(key, sortedObjectKeys(object.get(key))));
            return sorted;
        }
        if (value.isJsonArray()) {
            var result = new JsonArray(); value.getAsJsonArray().forEach(e -> result.add(sortedObjectKeys(e))); return result;
        }
        return value.deepCopy();
    }
    record Fixture(ResourceLocation profile, Map<ResourceLocation, BlueprintData> catalog, BlueprintResearchSnapshot snapshot) {
        ResearchTreePublication publication(PlayerRecipeData player) {
            return ResearchTreeBuilder.buildPublication(catalog, snapshot,
                    new BlueprintProgressionConfigSnapshot(true, true, true, JournalVisibility.FULL, true, DuplicateBlueprintPolicy.MANUAL_RECYCLING, false, 100, false, profile), player, ignored -> false);
        }
    }
}
