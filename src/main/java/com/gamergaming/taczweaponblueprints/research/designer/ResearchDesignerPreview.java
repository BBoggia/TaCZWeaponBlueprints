package com.gamergaming.taczweaponblueprints.research.designer;

import java.util.*;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.research.tree.*;
import com.gamergaming.taczweaponblueprints.resource.research.*;

/** One explicit scenario, using runtime graph/policy/planner implementations and their existing work limits. */
public final class ResearchDesignerPreview {
    private ResearchDesignerPreview() {}
    public record Scenario(BlueprintProgressionConfigSnapshot progression, ResearchFeatureConfigSnapshot features,
            IPlayerRecipeData knowledge, Predicate<String> blocked, Predicate<ResourceLocation> exempt,
            Optional<ResourceLocation> routeTarget, boolean creative) {
        public Scenario {
            Objects.requireNonNull(progression); Objects.requireNonNull(features); Objects.requireNonNull(knowledge);
            Objects.requireNonNull(blocked); Objects.requireNonNull(exempt); Objects.requireNonNull(routeTarget);
        }
    }
    public record Evaluation(ResearchTreePublication tree, Map<ResourceLocation, BlueprintResearchPolicy> policies,
            BlueprintProgressionPolicySnapshot progression, Optional<ResearchPathUnlockPlanner.Result> route) {
        public Evaluation { policies = Map.copyOf(policies); }
    }
    /** Affordability of a supplied quote only; station/gate and delivery checks remain separate. */
    public static Optional<ResearchIngredientPlanner.Allocation> allocateQuote(ResearchPathUnlockPlanner.RouteQuote quote,
            com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy currency,
            int bankedPoints, List<ResearchIngredientPlanner.SlotFact> inventory) {
        if (bankedPoints < 0) throw new IllegalArgumentException("Negative simulated balance");
        var requirements = new ArrayList<>(quote.ingredients());
        if (currency.kind() == com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy.Kind.BANKED_RP) {
            if (bankedPoints < quote.pointCost()) return Optional.empty();
        } else if (quote.pointCost() > 0) {
            if (quote.pointCost() > ResearchIngredientPlanner.MAX_TOTAL_REQUIREMENT_COUNT) return Optional.empty();
            requirements.add(new ResearchIngredientPlanner.Requirement(List.of(currency.item().orElseThrow()), Optional.empty(), quote.pointCost()));
        }
        return ResearchIngredientPlanner.allocationFromFacts(inventory, requirements);
    }
    public static Evaluation evaluate(ResearchDesignerCompiler.Candidate candidate,
            Map<ResourceLocation, BlueprintData> catalog, Scenario scenario) {
        if (!scenario.progression().activeProfileId().equals(candidate.profile()))
            throw new IllegalArgumentException("Preview configuration must select the candidate profile");
        var resolution = DetachedResearchEvaluation.resolve(candidate.snapshot(), catalog, scenario.progression(),
                candidate.profile(), scenario.knowledge(), scenario.blocked(), scenario.exempt(), null);
        var policies = new TreeMap<ResourceLocation, BlueprintResearchPolicy>();
        catalog.keySet().forEach(id -> policies.put(id, resolution.resolver().apply(id)));
        var tree = ResearchTreeBuilder.buildPublication(catalog, candidate.snapshot(), scenario.progression(),
                scenario.knowledge(), scenario.blocked(), scenario.exempt(), null, null);
        var progression = BlueprintProgressionPolicyResolver.resolve(candidate.snapshot(), catalog, 1, 1, Map.of(), scenario.features());
        var route = scenario.routeTarget().map(target -> ResearchPathUnlockPlanner.plan(target, scenario.knowledge(),
                resolution.resolver(), scenario.exempt(), scenario.creative()));
        return new Evaluation(tree, policies, progression, route);
    }
}
