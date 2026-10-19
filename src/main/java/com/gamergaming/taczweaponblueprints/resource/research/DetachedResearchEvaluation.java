package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.*;
import java.util.function.*;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.*;

/** Shared read-only evaluation. Callers keep the supplied knowledge and predicates stable for this evaluation. */
public final class DetachedResearchEvaluation {
    private DetachedResearchEvaluation() {}

    public static Resolution resolve(BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog, BlueprintProgressionConfigSnapshot config,
            ResourceLocation profile, IPlayerRecipeData knowledge, Predicate<String> blocked,
            Predicate<ResourceLocation> exempt, AutomaticWeaponPrerequisitePlan automatic) {
        Objects.requireNonNull(snapshot); Objects.requireNonNull(config);
        var effectiveSnapshot = snapshot.withDomainSettings(config.domainSettings());
        var entries = BlueprintResearchPolicyResolver.entryPointReplacements(effectiveSnapshot, catalog, profile, blocked, exempt);
        Map<ResourceLocation, BlueprintResearchPolicy> structural = new HashMap<>();
        Function<ResourceLocation, BlueprintResearchPolicy> structuralResolver = id -> structural.computeIfAbsent(id, key -> {
            var policy = BlueprintResearchPolicyResolver.resolve(effectiveSnapshot, catalog, profile, key, knowledge, blocked, exempt);
            var data = catalog.get(key);
            return AutomaticWeaponPrerequisiteOverlay.apply(config.apply(policy), automatic, knowledge, blocked,
                    config.maximumUndiscoveredVisibility().allowsServerSelection(), catalog::containsKey,
                    exempt, entries, effectiveSnapshot.usesAutomaticWeaponPlacement(profile)
                            && data != null && data.getKind() == BlueprintKind.GUN);
        });
        if (knowledge == null) return new Resolution(structuralResolver, structuralResolver, entries);
        var connectivity = new ResearchProgressionConnectivity(knowledge, structuralResolver, exempt);
        Map<ResourceLocation, BlueprintResearchPolicy> effective = new HashMap<>();
        Function<ResourceLocation, BlueprintResearchPolicy> resolver = id -> effective.computeIfAbsent(id, key -> {
            var policy = structuralResolver.apply(key);
            return policy.withPrerequisitesSatisfied(connectivity.requirementsSatisfied(policy));
        });
        return new Resolution(structuralResolver, resolver, entries);
    }

    public record Resolution(Function<ResourceLocation, BlueprintResearchPolicy> structuralResolver,
            Function<ResourceLocation, BlueprintResearchPolicy> resolver,
            Map<ResourceLocation, ResourceLocation> entryPointReplacements) {
        public Resolution { entryPointReplacements = Map.copyOf(entryPointReplacements); }
    }
}
