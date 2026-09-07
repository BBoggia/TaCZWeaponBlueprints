package com.gamergaming.taczweaponblueprints.research.designer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import java.util.stream.Stream;
import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.research.*;

class ResearchDesignerCompilerTest {
    static Stream<JsonObject> cases() { return ResearchDesignerContractTest.runtimeCases(); }
    @ParameterizedTest @MethodSource("cases")
    void productionCompilerPreservesFoundationGraphs(JsonObject fixture) {
        var json = ResearchDesignerContractTest.project(fixture);
        // The D0 automatic fixture intentionally contains only a synthetic origin, not a server capture.
        json.add("origin", JsonParser.parseString("{\"kind\":\"blank\"}"));
        var old = ResearchDesignerContractTest.compileFixture(json, fixture);
        var result = ResearchDesignerCompiler.compile(ResearchDesignerProject.fromJson(json), old.catalog(), Optional.empty());
        assertTrue(result.candidate().isPresent(), result.diagnostics().toString());
        var candidate = result.candidate().orElseThrow();
        var compiled = new ResearchDesignerContractTest.Fixture(candidate.profile(), old.catalog(), candidate.snapshot());
        assertEquals(old.publication(new PlayerRecipeData()).graph(), compiled.publication(new PlayerRecipeData()).graph());
        assertEquals(old.snapshot().authoredBranches(old.snapshot().techTrees().keySet().iterator().next()),
                candidate.snapshot().authoredBranches(candidate.snapshot().techTrees().keySet().iterator().next()));
    }
    @ParameterizedTest @MethodSource("cases")
    void convertedCapturesCompileAgainstDeclaredContext(JsonObject fixture) {
        var capture = ResearchDesignerCaptureTest.fixture(fixture);
        var project = ResearchDesignerConversion.createCustom(capture, "custom_pack", "Custom").project();
        var original = ResearchDesignerContractTest.compileFixture(ResearchDesignerContractTest.project(fixture), fixture);
        var result = ResearchDesignerCompiler.compile(project, original.catalog(), Optional.of(capture));
        assertTrue(result.candidate().isPresent(), result.diagnostics().toString());
        assertTrue(ResearchDesignerCompiler.compile(project, original.catalog(), Optional.empty()).candidate().isEmpty());
        var emitted = ResearchDesignerCompiler.decodeResources(result.candidate().orElseThrow().resources().json());
        assertEquals(result.candidate().orElseThrow().snapshot().rules(), emitted.rules());
        assertEquals(result.candidate().orElseThrow().snapshot().researchData(), emitted.researchData());
    }
    @Test void cancelledCompilationStopsWithoutPublishingAnInvalidDraft() {
        var project = ResearchDesignerProject.create("custom_pack", "Cancelled");
        var session = new ResearchDesignerSession(project);
        Thread.currentThread().interrupt();
        try {
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> ResearchDesignerCompiler.compile(session.checkpoint(), Map.of(), Optional.empty()));
            assertThrows(java.util.concurrent.CancellationException.class,
                    () -> ResearchDesignerCompiler.decodeResources(new JsonObject()));
            assertTrue(Thread.currentThread().isInterrupted());
        } finally { Thread.interrupted(); }
        // The worker remains usable for the next request; genuine validation errors still produce diagnostics.
        var result = ResearchDesignerCompiler.compile(session.checkpoint(), Map.of(), Optional.empty());
        assertTrue(result.candidate().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("empty_tree")));
    }
    @Test void staleAsyncResultsCannotReplaceCurrentDraftValidation() {
        var fixture = ResearchDesignerContractTest.named("single"); var json = ResearchDesignerContractTest.project(fixture);
        var old = ResearchDesignerContractTest.compileFixture(json, fixture);
        var first = ResearchDesignerProject.fromJson(json);
        var result = ResearchDesignerCompiler.compile(first, old.catalog(), Optional.empty());
        json.addProperty("title", "New edit"); var next = ResearchDesignerProject.fromJson(json);
        var state = new ResearchDesignerCompiler.State(); state.begin(next);
        assertFalse(state.accept(result)); assertTrue(state.current().isEmpty()); assertTrue(state.lastValid().isEmpty());
    }
    @Test void sharedEvaluationRetainsLearnedSupportSemantics() {
        var fixture = ResearchDesignerContractTest.named("inclusive_or");
        var old = ResearchDesignerContractTest.compileFixture(ResearchDesignerContractTest.project(fixture), fixture);
        var config = new com.gamergaming.taczweaponblueprints.progression.BlueprintProgressionConfigSnapshot(
                true, true, true, JournalVisibility.FULL, true,
                com.gamergaming.taczweaponblueprints.progression.DuplicateBlueprintPolicy.MANUAL_RECYCLING, false, 100, false, old.profile());
        var player = new PlayerRecipeData();
        for (var name : List.of("root", "left", "right", "support")) player.addBlueprint("designer_fixture:" + name);
        var resolution = DetachedResearchEvaluation.resolve(old.snapshot(), old.catalog(), config, old.profile(), player, ignored -> false, ignored -> false, null);
        var target = new net.minecraft.resources.ResourceLocation("designer_fixture:end");
        assertTrue(resolution.resolver().apply(target).prerequisitesSatisfied());
        assertEquals(2, resolution.resolver().apply(target).requirements().allOf().size());
        var path = com.gamergaming.taczweaponblueprints.progression.ResearchPathUnlockPlanner.plan(target, player, resolution.resolver(), ignored -> false, false);
        assertTrue(path.plan().isPresent());
    }
    @Test void inheritedFragmentThresholdSurvivesCaptureCompilationAndSettingsRoundTrip() {
        var fixture = ResearchDesignerContractTest.named("single");
        var original = ResearchDesignerContractTest.compileFixture(ResearchDesignerContractTest.project(fixture), fixture);
        var config = new com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig();
        var features = config.researchFeatureSnapshot();
        var policies = BlueprintProgressionPolicyResolver.resolve(original.snapshot(), original.catalog(), 1, 1, Map.of(), features);
        var sourcePolicy = policies.policiesByProfile().get(original.profile()).values().iterator().next();
        assertFalse(sourcePolicy.exactFragmentThreshold());
        var settings = new JsonObject(); var effective = new JsonObject();
        effective.add("progression", DesignerValues.evidence(config.progressionSnapshot()));
        effective.add("features", DesignerValues.evidence(features)); settings.add("effective", effective);
        var dependencies = new JsonObject(); dependencies.add("mods", new JsonObject());
        var context = DesignerCaptureAssembly.assemble(new DesignerCaptureAssembly.Inputs(original.snapshot(), original.catalog(), original.profile(),
                Map.of(), Map.of(), policies.policiesByProfile().get(original.profile()), Map.of()), config.progressionSnapshot(),
                com.gamergaming.taczweaponblueprints.progression.BlueprintAccessConfigSnapshot.EMPTY, ignored -> false,
                settings, new JsonObject(), dependencies, (kind, id) -> "fixture");
        var revisions = new JsonObject(); for (var key : List.of("catalog", "research", "automatic", "config", "access", "tags")) revisions.addProperty(key, 1);
        var capture = ResearchDesignerCapture.create(UUID.randomUUID(), revisions, context);
        assertEquals(features, DesignerCapturedSettings.features(capture));
        assertEquals(original.profile(), DesignerCapturedSettings.progression(capture, original.profile()).activeProfileId());
        var project = ResearchDesignerConversion.createCustom(capture, "custom_pack", "Custom").project();
        var compiled = ResearchDesignerCompiler.compile(project, original.catalog(), Optional.of(capture)).candidate().orElseThrow();
        var convertedPolicies = BlueprintProgressionPolicyResolver.resolve(compiled.snapshot(), original.catalog(), 1, 1, Map.of(), features);
        var convertedPolicy = convertedPolicies.policy(compiled.profile(), sourcePolicy.blueprintId()).orElseThrow();
        assertFalse(convertedPolicy.exactFragmentThreshold());
        assertEquals(sourcePolicy.fragments(), convertedPolicy.fragments());
        var offline = new DesignerCapturedContext(capture);
        assertEquals(original.catalog().keySet(), offline.catalog().keySet());
        assertTrue(offline.catalog().values().stream().allMatch(data -> data.getRecipe() == null));
    }
    @Test void simulatedCurrencyAndMaterialCannotSpendTheSameSlotTwice() {
        var scrap = new net.minecraft.resources.ResourceLocation("test:scrap");
        var tag = new net.minecraft.resources.ResourceLocation("test:metals");
        var requirements = List.of(
                new com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner.Requirement(List.of(scrap), Optional.empty(), 2),
                new com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner.Requirement(List.of(), Optional.of(tag), 2));
        var shortInventory = List.of(new com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner.SlotFact(scrap, 3, Set.of(tag)));
        assertFalse(com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner.allocationFromFacts(shortInventory, requirements).orElseThrow().complete());
        var enough = List.of(new com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner.SlotFact(scrap, 4, Set.of(tag)));
        assertTrue(com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner.allocationFromFacts(enough, requirements).orElseThrow().complete());
    }
    @Test void pendingInputsBlockCompilationAndDiagnosticUsesPublishedEnvelope() {
        var fixture = ResearchDesignerContractTest.named("single"); var project = ResearchDesignerProject.fromJson(ResearchDesignerContractTest.project(fixture));
        var session = new ResearchDesignerSession(project);
        session.pending("Cost", new ResearchDesignerSession.InputKey(project.draft().nodes().get(0).id(), "/policy/research_cost/points"), "-");
        var result = ResearchDesignerCompiler.compile(session.checkpoint(), Map.of(), Optional.empty());
        assertTrue(result.candidate().isEmpty()); assertEquals("pending_input", result.diagnostics().get(0).code());
        var diagnostic = result.diagnostics().get(0).json(3);
        var contract = ResearchDesignerContractTest.read(ResearchDesignerContractTest.CONTRACTS.resolve("contract.json"));
        assertEquals(new TreeSet<>(contract.getAsJsonArray("diagnostic_required").asList().stream().map(JsonElement::getAsString).toList()), new TreeSet<>(diagnostic.keySet()));
    }
    @Test void nullableWeaponMeasurementsRoundTripButRequiredPrimitivesRejectNull() {
        var type = com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponStatEvidence.class;
        var json = new JsonObject();
        for (var component : type.getRecordComponents()) {
            if (component.getType() == String.class) json.addProperty(component.getName(), component.getName().equals("blueprintId") ? "test:gun" : "unknown");
            else if (component.getType() == boolean.class) json.addProperty(component.getName(), false);
            else if (component.getType() == List.class) json.add(component.getName(), new JsonArray());
            else json.add(component.getName(), JsonNull.INSTANCE);
        }
        var decoded = DesignerCapturedSettings.read(type, json);
        assertEquals(decoded, DesignerCapturedSettings.read(type, DesignerValues.evidence(decoded)));
        json.add("explosive", JsonNull.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> DesignerCapturedSettings.read(type, json));
    }
    @Test void sameDocumentNewRequestStillRejectsAnOldValidationCallback() {
        var fixture = ResearchDesignerContractTest.named("single"); var project = ResearchDesignerProject.fromJson(ResearchDesignerContractTest.project(fixture));
        var catalog = ResearchDesignerContractTest.compileFixture(project.json(), fixture).catalog();
        var state = new ResearchDesignerCompiler.State(); long old = state.begin(project);
        var result = ResearchDesignerCompiler.compile(project, catalog, Optional.empty());
        long current = state.begin(project);
        assertFalse(state.accept(old, result)); assertTrue(state.accept(current, result));
    }
    @Test void invalidPlacementBranchAndOwnershipCannotProduceCandidates() {
        var fixture = ResearchDesignerContractTest.named("single"); var original = ResearchDesignerContractTest.project(fixture);
        var catalog = ResearchDesignerContractTest.compileFixture(original, fixture).catalog();
        var rank = original.deepCopy(); rank.getAsJsonObject("draft").getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("placement").addProperty("rank", -1);
        var branch = original.deepCopy(); branch.getAsJsonObject("draft").getAsJsonArray("nodes").get(0).getAsJsonObject().addProperty("branch_id", "test:missing");
        var owner = original.deepCopy(); owner.getAsJsonObject("draft").getAsJsonObject("profile").addProperty("id", "foreign:profile");
        assertThrows(IllegalArgumentException.class, () -> ResearchDesignerProject.fromJson(owner));
        for (var json : List.of(rank, branch)) {
            var result = ResearchDesignerCompiler.compile(ResearchDesignerProject.fromJson(json), catalog, Optional.empty());
            assertTrue(result.candidate().isEmpty(), result.toString());
        }
    }
    @Test void diagnosticsAreBoundedAndMissingCostFactsAreExplicit() {
        var fixture = ResearchDesignerContractTest.named("large_addon"); var json = ResearchDesignerContractTest.project(fixture);
        var result = ResearchDesignerCompiler.compile(ResearchDesignerProject.fromJson(json), Map.of(), Optional.empty());
        assertTrue(result.candidate().isEmpty()); assertTrue(result.diagnostics().size() <= 1000);
        fixture = ResearchDesignerContractTest.named("single");
        var capture = ResearchDesignerCaptureTest.fixture(fixture);
        var converted = ResearchDesignerConversion.createCustom(capture, "custom_pack", "Custom").project().json();
        converted.getAsJsonObject("draft").getAsJsonArray("nodes").get(0).getAsJsonObject().getAsJsonObject("policy").add("research_cost",
                JsonParser.parseString("{\"points\":2,\"ingredients\":[{\"tag\":\"test:missing\",\"count\":1}]}"));
        var catalog = ResearchDesignerContractTest.compileFixture(ResearchDesignerContractTest.project(fixture), fixture).catalog();
        result = ResearchDesignerCompiler.compile(ResearchDesignerProject.fromJson(converted), catalog, Optional.of(capture));
        assertTrue(result.candidate().isEmpty()); assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("missing_item_tag")));
    }
    @Test void badDraftDoesNotReplaceLastValidCandidate() {
        var fixture = ResearchDesignerContractTest.named("single"); var json = ResearchDesignerContractTest.project(fixture);
        var old = ResearchDesignerContractTest.compileFixture(json, fixture);
        var state = new ResearchDesignerCompiler.State();
        var valid = ResearchDesignerCompiler.compile(ResearchDesignerProject.fromJson(json), old.catalog(), Optional.empty());
        state.accept(valid); assertTrue(state.lastValid().isPresent());
        json.getAsJsonObject("draft").getAsJsonArray("nodes").get(0).getAsJsonObject().remove("blueprint_id");
        var invalid = ResearchDesignerCompiler.compile(ResearchDesignerProject.fromJson(json), old.catalog(), Optional.empty());
        state.accept(invalid); assertTrue(state.current().orElseThrow().candidate().isEmpty());
        assertSame(valid.candidate().orElseThrow(), state.lastValid().orElseThrow());
        assertEquals("unassigned_blueprint", invalid.diagnostics().get(0).code());
    }
    @Test void cycleAndMissingTargetAreRejectedWithoutChangingTheDraft() {
        var fixture = ResearchDesignerContractTest.named("split_merge"); var json = ResearchDesignerContractTest.project(fixture);
        var catalog = ResearchDesignerContractTest.compileFixture(json, fixture).catalog();
        json.getAsJsonObject("draft").getAsJsonArray("nodes").get(0).getAsJsonObject().add("requirements",
                JsonParser.parseString("[{\"id\":\"cycle\",\"any_of\":[\"designer_fixture:end\"]}]"));
        var project = ResearchDesignerProject.fromJson(json); byte[] before = project.bytes();
        var result = ResearchDesignerCompiler.compile(project, catalog, Optional.empty());
        assertTrue(result.candidate().isEmpty()); assertArrayEquals(before, project.bytes());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("runtime_validation")));
        result = ResearchDesignerCompiler.compile(ResearchDesignerProject.fromJson(ResearchDesignerContractTest.project(ResearchDesignerContractTest.named("missing"))), catalog, Optional.empty());
        assertTrue(result.candidate().isEmpty());
        assertTrue(result.diagnostics().stream().anyMatch(d -> d.code().equals("missing_blueprint")));
    }
}
