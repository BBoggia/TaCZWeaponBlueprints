package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.progression.analyzer.*;
import net.minecraft.resources.ResourceLocation;

/** Shared target authority; acquiring fragments does not grant weapon-attempt eligibility. */
public final class AnalysisReconstructionResolver {
    private AnalysisReconstructionResolver() { }
    public enum Status { AVAILABLE, DISABLED, CONTENT_UNAVAILABLE, TIER_UNAVAILABLE, STALE_PROGRESSION }
    public record Resolution(Status status, AnalysisFragmentReconstructionService.Policy policy,
            Optional<ResourceLocation> authority, String source) {
        public boolean available() { return status == Status.AVAILABLE; }
    }
    public static Resolution resolve(BlueprintAnalyzerPolicyResolver.Context context, ResourceLocation profile,
            ResourceLocation target, AnalyzerTier station) {
        var data = context.catalog().get(target);
        if (data == null || data.getKind() != BlueprintKind.GUN || !context.research().profiles().containsKey(profile))
            return unavailable(context, target, Status.CONTENT_UNAVAILABLE);
        var selected = context.research().researchData().reconstruction().entrySet().stream()
                .filter(e -> e.getValue().matches(profile, target))
                .max(Comparator.comparingInt(e -> e.getValue().specificity()));
        if (selected.isPresent() && !selected.orElseThrow().getValue().fallback())
            return explicit(context, profile, target, selected.orElseThrow());
        var legacy = BlueprintAnalyzerPolicyResolver.resolveLegacy(context, profile, target, station,
                BlueprintAnalyzerPolicy.Origin.UNKNOWN, FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
        if (legacy.status() == AnalyzerPolicyQuote.Status.STALE_PROGRESSION)
            return unavailable(context, target, Status.STALE_PROGRESSION);
        if (legacy.fragmentThreshold() > 0) {
            var learning = legacy.attempt().map(BlueprintReverseEngineeringPolicy::physicalBlueprintLearningMode).orElse(PhysicalBlueprintLearningMode.DISABLED);
            boolean enabled = learning.learningPermitted();
            return new Resolution(enabled ? Status.AVAILABLE : Status.DISABLED,
                    new AnalysisFragmentReconstructionService.Policy(target, enabled, legacy.fragmentThreshold(), legacy.retainedProgressCap(),
                            true, learning, Math.max(1, context.researchRevision())), legacy.selectedRuleId(), "legacy_analyzer");
        }
        return selected.map(e -> explicit(context, profile, target, e)).orElseGet(() -> unavailable(context, target, Status.DISABLED));
    }
    private static Resolution explicit(BlueprintAnalyzerPolicyResolver.Context context, ResourceLocation profile,
            ResourceLocation target, Map.Entry<ResourceLocation, AnalysisReconstructionDefinition> selected) {
        var definition = selected.getValue();
        if (!definition.enabled()) return new Resolution(Status.DISABLED,
                disabled(context, target), Optional.of(selected.getKey()), "explicit_disable");
        if (!context.progression().matches(context.catalogRevision(), context.researchRevision()))
            return unavailable(context, target, Status.STALE_PROGRESSION);
        var tier = context.progression().policy(profile, target).map(p -> AnalyzerTier.fromLevel(p.researchWorkbenchTier().level()));
        if (tier.isEmpty()) return unavailable(context, target, Status.TIER_UNAVAILABLE);
        return new Resolution(Status.AVAILABLE, new AnalysisFragmentReconstructionService.Policy(target, true,
                definition.fragments().thresholds().get(tier.orElseThrow()), definition.fragments().retainedProgressCap(),
                definition.recyclable(), definition.learning(), Math.max(1, context.researchRevision())),
                Optional.of(selected.getKey()), definition.fallback() ? "shared_fallback" : "shared_explicit");
    }
    private static Resolution unavailable(BlueprintAnalyzerPolicyResolver.Context context, ResourceLocation target, Status status) {
        return new Resolution(status, disabled(context, target), Optional.empty(), status.name().toLowerCase(Locale.ROOT));
    }
    private static AnalysisFragmentReconstructionService.Policy disabled(BlueprintAnalyzerPolicyResolver.Context context, ResourceLocation target) {
        return new AnalysisFragmentReconstructionService.Policy(target, false, 1, 1, false, PhysicalBlueprintLearningMode.DISABLED, Math.max(1, context.researchRevision()));
    }
}
