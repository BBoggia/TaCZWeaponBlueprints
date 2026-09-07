package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerTier;
import com.gamergaming.taczweaponblueprints.resource.research.*;

/** Reports changes from the captured behavior. Differences may be intentional edits; this is not activation approval. */
public final class DesignerCaptureComparison {
    public static final long MAX_COMPARISON_WORK = 1_048_576;
    private DesignerCaptureComparison() {}
    public record Difference(String area, String blueprintId, String scenario, String field) {}
    public record Report(List<Difference> differences, boolean truncated, int scenariosChecked) {
        public Report { differences = List.copyOf(differences); }
        public boolean equivalentForCheckedScenarios() { return differences.isEmpty() && !truncated; }
    }
    public static Report compare(DesignerCapturedContext context, ResearchDesignerCompiler.Candidate candidate,
            List<? extends IPlayerRecipeData> scenarios) {
        if (scenarios.isEmpty() || scenarios.size() > 16) throw new IllegalArgumentException("Supply between one and sixteen scenarios");
        long work = (long) context.catalog().size() * (scenarios.size() + 2
                + AnalyzerTier.values().length * BlueprintAnalyzerPolicy.Origin.values().length
                + (long) scenarios.size() * AnalyzerTier.values().length * context.source().researchData().policies().size());
        if (work > MAX_COMPARISON_WORK) throw new IllegalArgumentException("Comparison exceeds work limit; check fewer scenarios or a smaller context");
        var changes = new ArrayList<Difference>();
        var originalProgression = context.progression(context.source()); var customProgression = context.progression(candidate.snapshot());
        var originalCrafting = context.crafting(context.source()); var customCrafting = context.crafting(candidate.snapshot());
        var originalAnalyzer = new BlueprintAnalyzerPolicyResolver.Context(context.source(), context.catalog(), 1, 1, originalProgression);
        var customAnalyzer = new BlueprintAnalyzerPolicyResolver.Context(candidate.snapshot(), context.catalog(), 1, 1, customProgression);
        var sourceConfig = DesignerCapturedSettings.progression(context.capture(), context.profile());
        var customConfig = DesignerCapturedSettings.progression(context.capture(), candidate.profile());
        for (var id : context.catalog().keySet()) {
            compare(changes, "progression", id.toString(), "all", originalProgression.policy(context.profile(), id), customProgression.policy(candidate.profile(), id),
                    Set.of("profileId", "tierSource", "selectedProgressionRuleId", "ruleSpecificity", "automaticScore", "automaticPercentileBasisPoints", "reviewRequired"));
            compare(changes, "crafting", id.toString(), "all", originalCrafting.policy(context.profile(), id), customCrafting.policy(candidate.profile(), id),
                    Set.of("profileId", "source", "selectedRuleId", "ruleSpecificity", "automaticScore", "automaticPercentileBasisPoints", "reviewRequired", "reasonCode", "warnings"));
            for (var tier : AnalyzerTier.values()) {
                compare(changes, "reconstruction", id.toString(), tier.toString(),
                        Optional.of(AnalysisReconstructionResolver.resolve(originalAnalyzer, context.profile(), id, tier)),
                        Optional.of(AnalysisReconstructionResolver.resolve(customAnalyzer, candidate.profile(), id, tier)), Set.of("authority", "source"));
            }
            for (var tier : AnalyzerTier.values()) for (var origin : BlueprintAnalyzerPolicy.Origin.values()) {
                compare(changes, "analyzer", id.toString(), tier + "/" + origin,
                        Optional.of(BlueprintAnalyzerPolicyResolver.resolve(originalAnalyzer, context.profile(), id, tier, origin, sourceConfig.foundWeaponRecoveryMode())),
                        Optional.of(BlueprintAnalyzerPolicyResolver.resolve(customAnalyzer, candidate.profile(), id, tier, origin, customConfig.foundWeaponRecoveryMode())),
                        Set.of("profileId", "selectedRuleId", "specificity", "tierSource", "reviewRequired"));
            }
        }
        var registry = context.capture().json().getAsJsonObject("registry_facts");
        var itemIds = new TreeSet<String>(); itemIds.addAll(registry.getAsJsonObject("items").keySet());
        var itemTags = registry.getAsJsonObject("item_tags");
        itemTags.entrySet().forEach(e -> e.getValue().getAsJsonArray().forEach(id -> itemIds.add(id.getAsString())));
        if (itemIds.size() > 65_536 || work + (long) itemIds.size()
                * (context.source().researchData().policies().size() + candidate.snapshot().researchData().policies().size()) > MAX_COMPARISON_WORK)
            throw new IllegalArgumentException("Research Data input comparison exceeds work limit");
        java.util.function.BiPredicate<net.minecraft.resources.ResourceLocation, net.minecraft.resources.ResourceLocation> inTag =
                (item, tag) -> itemTags.has(tag.toString()) && itemTags.getAsJsonArray(tag.toString()).contains(new JsonPrimitive(item.toString()));
        for (var item : itemIds) {
            var itemId = new net.minecraft.resources.ResourceLocation(item);
            var sourceOwner = context.source().researchData().select(itemId, context.profile(), inTag).selected();
            var expectedOwner = sourceOwner.map(id -> context.source().researchData().policies().get(id).profile().isPresent()
                    ? new net.minecraft.resources.ResourceLocation(candidate.profile().getNamespace(), "captured/research_data/" + id.getNamespace() + "/" + id.getPath()) : id);
            var customOwner = candidate.snapshot().researchData().select(itemId, candidate.profile(), inTag).selected();
            if (!expectedOwner.equals(customOwner) && changes.size() <= 1000) changes.add(new Difference("research_data", item, "all", "input_owner"));
        }
        var sourceData = new ResearchDataPoolResolver.Context(originalAnalyzer);
        var customData = new ResearchDataPoolResolver.Context(customAnalyzer);
        var raw = context.capture().json().getAsJsonObject("settings").getAsJsonObject("raw");
        var defaults = new ResearchDataPoolResolver.Defaults(raw.get("researchDataAllowLearned").getAsBoolean(),
                raw.get("researchDataAllowUndiscovered").getAsBoolean());
        var automatic = context.automatic().values().stream().findFirst().orElse(null);
        var prerequisitePlan = context.prerequisitePlan().orElse(null);
        int number = 0;
        for (var player : scenarios) {
            var original = DetachedResearchEvaluation.resolve(context.source(), context.catalog(), sourceConfig, context.profile(), player,
                    context::blocked, context::exempt, prerequisitePlan);
            var custom = DetachedResearchEvaluation.resolve(candidate.snapshot(), context.catalog(), customConfig, candidate.profile(), player,
                    context::blocked, context::exempt, null);
            for (var id : context.catalog().keySet()) compare(changes, "research", id.toString(), Integer.toString(number),
                    Optional.of(original.resolver().apply(id)), Optional.of(custom.resolver().apply(id)),
                    Set.of("profileId", "ruleId", "specificity", "automaticPrerequisitesAllowed"));
            var originalTree = com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBuilder.buildPublication(
                    context.catalog(), context.source(), sourceConfig, player, context::blocked, context::exempt, automatic, prerequisitePlan);
            var customTree = com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeBuilder.buildPublication(
                    context.catalog(), candidate.snapshot(), customConfig, player, context::blocked, context::exempt, null, null);
            if (!originalTree.graph().equals(customTree.graph()) && changes.size() <= 1000)
                changes.add(new Difference("public_graph", "tree", Integer.toString(number), "nodes_edges_requirements"));
            if (!ranks(originalTree.techTree()).equals(ranks(customTree.techTree())) && changes.size() <= 1000)
                changes.add(new Difference("placement", "tree", Integer.toString(number), "domain_rank"));
            for (var entry : context.source().researchData().policies().entrySet()) {
                var policy = entry.getValue();
                if (policy.profile().isPresent() && !policy.profile().get().equals(context.profile())) continue;
                var targetId = policy.profile().isPresent()
                        ? new net.minecraft.resources.ResourceLocation(candidate.profile().getNamespace(),
                                "captured/research_data/" + entry.getKey().getNamespace() + "/" + entry.getKey().getPath())
                        : entry.getKey();
                if (!candidate.snapshot().researchData().policies().containsKey(targetId)) {
                    if (changes.size() <= 1000) changes.add(new Difference("research_data", entry.getKey().toString(), Integer.toString(number), "availability"));
                    continue;
                }
                for (var tier : AnalyzerTier.values()) {
                    var view = ResearchDataPoolResolver.PlayerView.from(player);
                    java.util.function.Predicate<net.minecraft.resources.ResourceLocation> allowed = id -> sourceConfig.blueprintsEnabled()
                            && !context.blocked(id.toString()) && !context.exempt(id);
                    var a = DesignerValues.evidence(ResearchDataPoolResolver.evaluate(sourceData, entry.getKey(), context.profile(), tier, defaults, view, allowed)).getAsJsonObject();
                    var b = DesignerValues.evidence(ResearchDataPoolResolver.evaluate(customData, targetId, candidate.profile(), tier, defaults, view, allowed)).getAsJsonObject();
                    a.remove("definition"); b.remove("definition");
                    a.getAsJsonObject("policy").remove("profile"); b.getAsJsonObject("policy").remove("profile");
                    if (!a.equals(b) && changes.size() <= 1000) changes.add(new Difference("research_data", entry.getKey().toString(), number + "/" + tier, "pool"));
                }
            }
            number++;
        }
        return new Report(changes.stream().limit(1000).toList(), changes.size() > 1000, number);
    }
    private static Map<String, String> ranks(com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreePresentation tree) {
        var result = new TreeMap<String, String>();
        tree.domains().forEach(domain -> domain.lanes().forEach(lane -> lane.members().forEach(member ->
                result.put(member.nodeId().toString(), domain.domain().name() + "/" + member.rank()))));
        return result;
    }
    private static void compare(List<Difference> changes, String area, String id, String scenario,
            Optional<?> original, Optional<?> custom, Set<String> ignored) {
        if (changes.size() > 1000) return;
        if (original.isEmpty() || custom.isEmpty()) {
            if (original.isPresent() != custom.isPresent()) changes.add(new Difference(area, id, scenario, "availability"));
            return;
        }
        var a = DesignerValues.evidence(original.orElseThrow()).getAsJsonObject();
        var b = DesignerValues.evidence(custom.orElseThrow()).getAsJsonObject();
        var keys = new TreeSet<>(a.keySet()); keys.addAll(b.keySet()); keys.removeAll(ignored);
        for (var key : keys) if (!Objects.equals(a.get(key), b.get(key)) && changes.size() <= 1000)
            changes.add(new Difference(area, id, scenario, key));
    }
}
