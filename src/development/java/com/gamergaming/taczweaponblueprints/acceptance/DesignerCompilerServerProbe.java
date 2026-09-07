package com.gamergaming.taczweaponblueprints.acceptance;

import java.util.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;

/** Opt-in scenario comparisons over real server catalog objects; it never changes player or publication state. */
final class DesignerCompilerServerProbe {
    static void verify(ResearchDesignerCapture capture, ResearchDesignerProject project,
            ProgressionPolicyAccessService.Context baseline, JsonObject report) {
        var catalog = baseline.catalog().blueprints();
        long compileStart = System.nanoTime();
        var compilation = ResearchDesignerCompiler.compile(project, catalog, Optional.of(capture));
        if (compilation.candidate().isEmpty()) throw new IllegalStateException(compilation.diagnostics().toString());
        var candidate = compilation.candidate().orElseThrow();
        report.addProperty("compile_wall_ms", (System.nanoTime() - compileStart) / 1_000_000.0);
        var config = DesignerCapturedSettings.progression(capture, candidate.profile());
        var features = DesignerCapturedSettings.features(capture);
        if (!features.equals(baseline.config())) throw new IllegalStateException("Captured feature settings differ");
        var facts = capture.json().getAsJsonObject("catalog");
        java.util.function.Predicate<String> blocked = id -> facts.has(id) && facts.getAsJsonObject(id).get("blocked").getAsBoolean();
        java.util.function.Predicate<net.minecraft.resources.ResourceLocation> exempt = id -> facts.has(id.toString()) && facts.getAsJsonObject(id.toString()).get("exempt").getAsBoolean();
        var originalConfig = DesignerCapturedSettings.progression(capture, baseline.profileId());
        var automatic = baseline.automatic().prerequisitePlansByProfile().get(baseline.profileId());
        ResearchDesignerPreview.Evaluation evaluation = null;
        for (int scenario = 0; scenario < 3; scenario++) {
            var player = new PlayerRecipeData();
            int index = 0;
            for (var id : new TreeSet<>(catalog.keySet())) {
                if (scenario > 0) player.discoverBlueprint(id.toString());
                if (scenario == 2 && index++ % 2 == 0) player.addBlueprint(id.toString());
            }
            evaluation = ResearchDesignerPreview.evaluate(candidate, catalog, new ResearchDesignerPreview.Scenario(
                    config, features, player, blocked, exempt, Optional.empty(), false));
            var original = DetachedResearchEvaluation.resolve(baseline.research().snapshot(), catalog, originalConfig,
                    baseline.profileId(), player, blocked, exempt, automatic);
            for (var id : catalog.keySet()) compare("research " + scenario + "/" + id,
                    DesignerValues.evidence(original.resolver().apply(id)).getAsJsonObject(),
                    DesignerValues.evidence(evaluation.policies().get(id)).getAsJsonObject(),
                    Set.of("profileId", "ruleId", "specificity", "automaticPrerequisitesAllowed"));
        }
        var progression = evaluation.progression();
        for (var id : baseline.profilePolicies().keySet()) compare("progression " + id,
                DesignerValues.evidence(baseline.profilePolicies().get(id)).getAsJsonObject(),
                DesignerValues.evidence(progression.policy(candidate.profile(), id).orElseThrow()).getAsJsonObject(),
                Set.of("profileId", "tierSource", "selectedProgressionRuleId", "specificity", "ruleSpecificity", "automaticScore", "automaticPercentileBasisPoints", "reviewRequired"));
        var crafting = BlueprintCraftingPolicyResolver.resolve(candidate.snapshot(), catalog,
                baseline.catalog().revision(), baseline.research().revision(), baseline.automatic().revision(), Map.of(),
                AutomaticWeaponEvidenceManager.INSTANCE.publication(), BlueprintAmmoAssociationManager.INSTANCE.publication(), features);
        for (var id : baseline.profileCraftingPolicies().keySet()) compare("crafting " + id,
                DesignerValues.evidence(baseline.profileCraftingPolicies().get(id)).getAsJsonObject(),
                DesignerValues.evidence(crafting.policy(candidate.profile(), id).orElseThrow()).getAsJsonObject(),
                Set.of("profileId", "source", "selectedRuleId", "specificity", "ruleSpecificity", "automaticScore", "automaticPercentileBasisPoints", "reviewRequired", "reasonCode", "warnings"));
        var originalAnalyzer = new BlueprintAnalyzerPolicyResolver.Context(baseline.research().snapshot(), catalog,
                baseline.research().revision(), baseline.catalog().revision(), baseline.policy().snapshot());
        var customAnalyzer = new BlueprintAnalyzerPolicyResolver.Context(candidate.snapshot(), catalog, 1, 1, progression);
        for (var id : catalog.keySet()) for (var station : AnalyzerTier.values()) for (var origin : BlueprintAnalyzerPolicy.Origin.values()) {
            compare("analyzer " + id + "/" + station + "/" + origin,
                    DesignerValues.evidence(BlueprintAnalyzerPolicyResolver.resolve(originalAnalyzer, baseline.profileId(), id, station, origin, config.foundWeaponRecoveryMode())).getAsJsonObject(),
                    DesignerValues.evidence(BlueprintAnalyzerPolicyResolver.resolve(customAnalyzer, candidate.profile(), id, station, origin, config.foundWeaponRecoveryMode())).getAsJsonObject(),
                    Set.of("profileId", "selectedRuleId", "specificity", "tierSource", "reviewRequired"));
        }
        long offlineStart = System.nanoTime();
        var offline = new DesignerCapturedContext(capture);
        var detachedCompilation = ResearchDesignerCompiler.compile(project, offline.catalog(), Optional.of(capture));
        var scenarios = new ArrayList<PlayerRecipeData>();
        for (int scenario = 0; scenario < 3; scenario++) {
            var player = new PlayerRecipeData(); int i = 0;
            for (var id : offline.catalog().keySet()) {
                if (scenario > 0) player.discoverBlueprint(id.toString());
                if (scenario == 2 && i++ % 2 == 0) player.addBlueprint(id.toString());
            }
            scenarios.add(player);
        }
        var comparison = DesignerCaptureComparison.compare(offline, detachedCompilation.candidate().orElseThrow(), scenarios);
        if (!comparison.equivalentForCheckedScenarios()) throw new IllegalStateException("Offline comparison differs: " + comparison);
        report.addProperty("offline_comparison_wall_ms", (System.nanoTime() - offlineStart) / 1_000_000.0);
        report.addProperty("offline_capture_comparison", true);
        report.addProperty("compiled_nodes", project.draft().nodes().size());
        report.addProperty("research_scenarios", 3); report.addProperty("progression_crafting_analyzer_parity", true);
    }
    private static void compare(String name, JsonObject original, JsonObject custom, Set<String> ignored) {
        ignored.forEach(k -> { original.remove(k); custom.remove(k); });
        if (!original.equals(custom)) throw new IllegalStateException(name + " differs: " + original + " versus " + custom);
    }
}
