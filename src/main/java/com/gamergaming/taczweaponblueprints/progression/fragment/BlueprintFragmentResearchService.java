package com.gamergaming.taczweaponblueprints.progression.fragment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.ResearchPathUnlockPlanner;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.ProgressionPolicyAccessService;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;

import net.minecraft.resources.ResourceLocation;

/** Applies and identifies one-use Blueprint Fragment discounts on a fixed route. */
public final class BlueprintFragmentResearchService {
    private BlueprintFragmentResearchService() {
    }

    /**
     * Applies discounts only after the stable route is selected. Fragments can
     * change the quote, but never choose a different prerequisite path.
     */
    public static ResearchPathUnlockPlanner.Plan adjustRuntimePlan(
            ResearchPathUnlockPlanner.Plan plan,
            IPlayerRecipeData playerData) {
        if (plan == null || playerData == null) {
            throw new IllegalArgumentException("fragment research inputs cannot be null");
        }
        var policyAccess = ProgressionPolicyAccessService.acquire(
                ProgressionPolicyAccessService.Mode.ENSURE_CURRENT).orElse(null);
        if (policyAccess == null) {
            return plan;
        }
        return adjust(plan, playerData, policyAccess.profilePolicies());
    }

    /** Pure adjustment seam used by contract tests and runtime publication code. */
    public static ResearchPathUnlockPlanner.Plan adjust(
            ResearchPathUnlockPlanner.Plan plan,
            IPlayerRecipeData playerData,
            Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> policies) {
        if (plan == null || playerData == null || policies == null) {
            throw new IllegalArgumentException("fragment research inputs cannot be null");
        }
        if (!plan.fragmentSetUses().isEmpty()) {
            throw new IllegalArgumentException("fragment discounts cannot be applied twice");
        }

        Map<String, Integer> archived = playerData.getArchivedBlueprintFragments();
        List<ResearchPathUnlockPlanner.PlannedNode> adjustedNodes =
                new ArrayList<>(plan.nodes().size());
        List<ResearchPathUnlockPlanner.FragmentSetUse> setUses = new ArrayList<>();
        for (ResearchPathUnlockPlanner.PlannedNode node : plan.nodes()) {
            BlueprintResearchPolicy policy = node.policy();
            ResolvedBlueprintProgressionPolicy progression = policies.get(node.blueprintId());
            int stored = archived.getOrDefault(node.blueprintId().toString(), 0);
            int discount = progression == null || node.costBypassed()
                    ? 0
                    : progression.fragments().researchDiscountFor(
                            policy.researchCost().points(), stored);
            if (discount <= 0) {
                adjustedNodes.add(node);
                continue;
            }
            BlueprintResearchCost adjustedCost = new BlueprintResearchCost(
                    policy.researchCost().points() - discount,
                    policy.researchCost().ingredients());
            adjustedNodes.add(new ResearchPathUnlockPlanner.PlannedNode(
                    node.blueprintId(),
                    policy.withResearchCost(adjustedCost),
                    false));
            setUses.add(new ResearchPathUnlockPlanner.FragmentSetUse(
                    node.blueprintId(),
                    stored,
                    progression.fragments().threshold(),
                    discount));
        }
        if (setUses.isEmpty()) {
            return plan;
        }

        var solution = new ResearchPathUnlockPlanner.SelectedUnlockSolution(
                plan.solution().supportIds(),
                adjustedNodes,
                plan.solution().selectedRequirements());
        int adjustedPoints = Math.subtractExact(
                plan.pointCost(),
                setUses.stream().mapToInt(
                        ResearchPathUnlockPlanner.FragmentSetUse::pointDiscount).sum());
        return new ResearchPathUnlockPlanner.Plan(
                solution,
                new ResearchPathUnlockPlanner.RouteQuote(
                        adjustedPoints,
                        plan.ingredients(),
                        plan.costBypassed()),
                setUses);
    }
}
