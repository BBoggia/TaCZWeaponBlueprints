package com.gamergaming.taczweaponblueprints.menu;

import java.util.List;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.BlueprintReverseEngineeringService;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalyzerOutcomeTable;
import com.gamergaming.taczweaponblueprints.resource.research.AnalyzerPolicyQuote;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintReverseEngineeringPolicy;

/** Bounded server facts for display. Contains policy weights, never an upcoming outcome. */
public record AnalyzerPresentation(int stationTier, Optional<Weapon> weapon,
        Optional<Fragments> fragments, Optional<RecoveryCost> recoveryCost, Optional<ResearchDataPresentation> data) {
    public static final AnalyzerPresentation EMPTY = new AnalyzerPresentation(0, Optional.empty(), Optional.empty(), Optional.empty());
    public AnalyzerPresentation(int tier, Optional<Weapon> weapon, Optional<Fragments> fragments, Optional<RecoveryCost> cost) {
        this(tier, weapon, fragments, cost, Optional.empty());
    }
    public AnalyzerPresentation withData(ResearchDataPresentation value) { return new AnalyzerPresentation(stationTier, weapon, fragments, recoveryCost, Optional.of(value)); }
    public AnalyzerPresentation {
        if (stationTier < 0 || stationTier > 3 || weapon == null || fragments == null || recoveryCost == null
                || data == null || data.isPresent() && (weapon.isPresent() || fragments.isPresent() || recoveryCost.isPresent())
                || weapon.isPresent() && fragments.isPresent()) throw new IllegalArgumentException("invalid Analyzer presentation");
    }
    public AnalyzerPresentation withStation(int tier) { return new AnalyzerPresentation(tier, weapon, fragments, recoveryCost, data); }
    public record Weapon(int tier, AnalyzerPolicyQuote.Status status, Optional<AnalyzerOutcomeTable> outcomes,
            Optional<AnalysisRewardClass> reward, int saved, int totalSaved, int threshold, int cap) {
        /** Configured attempts omit the fragment reward class only when learning is disabled. */
        public boolean blueprintLearningDisabled() {
            return status == AnalyzerPolicyQuote.Status.CONFIGURED && reward.isEmpty()
                    && outcomes.filter(table -> table.blueprintWeight() > 0).isPresent();
        }
        public Weapon {
            if (tier < 0 || tier > 3 || status == null || outcomes == null || reward == null
                    || status == AnalyzerPolicyQuote.Status.LEGACY) throw new IllegalArgumentException("invalid weapon presentation");
            progress(saved, totalSaved, threshold, cap);
            if (status == AnalyzerPolicyQuote.Status.CONFIGURED && (tier == 0 || outcomes.isEmpty()))
                throw new IllegalArgumentException("configured weapon presentation is incomplete");
        }
    }
    public record Fragments(AnalysisRewardClass reward, Optional<AnalysisRewardClass> outputReward, int totalSaved, int cap) {
        public Fragments {
            if (reward == null || outputReward == null) throw new IllegalArgumentException("invalid fragment presentation");
            progress(0, totalSaved, 0, cap);
        }
    }
    public record RecoveryCost(int inputCount, int points, List<BlueprintRecyclerPreview.IngredientPreview> ingredients,
            BlueprintReverseEngineeringService.Status status) {
        public RecoveryCost {
            ingredients = List.copyOf(ingredients);
            if (inputCount < 0 || inputCount > BlueprintReverseEngineeringPolicy.MAX_INPUT_COUNT || points < 0
                    || points > PlayerProgressionLimits.MAX_RESEARCH_POINTS || ingredients.size() > BlueprintResearchCost.MAX_INGREDIENT_TYPES
                    || status == null) throw new IllegalArgumentException("invalid recovery cost");
        }
    }
    private static void progress(int saved, int total, int threshold, int cap) {
        if (saved < 0 || total < saved || total > PlayerProgressionLimits.MAX_PROGRESS_VALUE || threshold < 0
                || cap < threshold || cap > PlayerProgressionLimits.MAX_PROGRESS_VALUE) throw new IllegalArgumentException("invalid analysis progress");
    }
}
