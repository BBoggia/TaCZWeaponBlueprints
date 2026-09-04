package com.gamergaming.taczweaponblueprints.progression;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.progression.gate.ProgressionGateCondition;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.resource.research.ResolvedBlueprintProgressionPolicy;

import net.minecraft.resources.ResourceLocation;

/** Builds the bounded, disclosure-safe subset of supplemental player state. */
public record PlayerSupplementalProgressionView(
        Map<String, Integer> archivedFragments,
        Map<String, Integer> publicCriteria) {
    public static final PlayerSupplementalProgressionView EMPTY =
            new PlayerSupplementalProgressionView(Map.of(), Map.of());

    public PlayerSupplementalProgressionView {
        archivedFragments = immutableProgressMap(
                archivedFragments,
                PlayerProgressionLimits.MAX_FRAGMENT_TARGETS);
        publicCriteria = immutableProgressMap(
                publicCriteria,
                PlayerProgressionLimits.MAX_PROGRESSION_CRITERIA);
    }

    public static PlayerSupplementalProgressionView create(
            IPlayerRecipeData playerData,
            Collection<ResourceLocation> disclosedBlueprintIds,
            Map<ResourceLocation, ResolvedBlueprintProgressionPolicy> policies) {
        if (playerData == null || disclosedBlueprintIds == null || policies == null
                || disclosedBlueprintIds.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("supplemental progression view inputs are invalid");
        }
        Set<ResourceLocation> disclosed = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        for (ResourceLocation id : disclosedBlueprintIds) {
            if (id == null) {
                throw new IllegalArgumentException("disclosed blueprint IDs contain null");
            }
            disclosed.add(ProgressionIds.require(id, "disclosed blueprint ID"));
        }

        Map<String, Integer> savedFragments = playerData.getArchivedBlueprintFragments();
        Map<String, Integer> savedCriteria = playerData.getProgressionCriteria();
        if (savedFragments == null || savedCriteria == null) {
            throw new IllegalArgumentException("player supplemental progression maps cannot be null");
        }

        TreeMap<String, Integer> fragments = new TreeMap<>();
        TreeSet<String> permittedCriteria = new TreeSet<>();
        for (ResourceLocation blueprintId : disclosed) {
            ResolvedBlueprintProgressionPolicy policy = policies.get(blueprintId);
            if (policy == null) {
                continue;
            }
            Integer fragmentCount = savedFragments.get(blueprintId.toString());
            if (policy.fragments().enabled() && validSavedValue(fragmentCount)) {
                fragments.put(blueprintId.toString(), fragmentCount);
            }
            policy.gates().allOf().forEach(group -> group.anyOf().forEach(condition -> {
                if (condition instanceof ProgressionGateCondition.Criterion criterion
                        && criterion.disclosure()
                                == ProgressionGateCondition.Disclosure.PUBLIC) {
                    permittedCriteria.add(criterion.criterionId().toString());
                }
            }));
        }

        TreeMap<String, Integer> criteria = new TreeMap<>();
        for (String criterionId : permittedCriteria) {
            Integer value = savedCriteria.get(criterionId);
            if (validSavedValue(value)) {
                criteria.put(criterionId, value);
            }
        }
        return new PlayerSupplementalProgressionView(fragments, criteria);
    }

    /**
     * Collects identities disclosed by either supported research surface. A
     * disabled Journal must not suppress state for identities that the Tech
     * Tree already reveals, while redacted tree IDs remain excluded.
     */
    public static Set<ResourceLocation> disclosedBlueprintIds(
            BlueprintJournalSnapshot journal,
            ResearchTreePublication tree) {
        if (journal == null || tree == null) {
            throw new IllegalArgumentException("player research publications cannot be null");
        }
        TreeSet<ResourceLocation> disclosed = new TreeSet<>(
                java.util.Comparator.comparing(ResourceLocation::toString));
        journal.entries().stream()
                .flatMap(entry -> entry.blueprintId().stream())
                .forEach(disclosed::add);
        tree.graph().nodes().stream()
                .filter(node -> node.visibility().revealsIdentity())
                .map(com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph.Node::blueprintId)
                .forEach(disclosed::add);
        if (disclosed.size() > PlayerProgressionLimits.MAX_IDS_PER_COLLECTION) {
            throw new IllegalArgumentException("disclosed blueprint IDs exceed the entry limit");
        }
        return Collections.unmodifiableSet(disclosed);
    }

    private static boolean validSavedValue(Integer value) {
        return value != null && value > 0
                && value <= PlayerProgressionLimits.MAX_PROGRESS_VALUE;
    }

    private static Map<String, Integer> immutableProgressMap(
            Map<String, Integer> values,
            int maximumEntries) {
        if (values == null || values.size() > maximumEntries) {
            throw new IllegalArgumentException("supplemental progression map is invalid");
        }
        TreeMap<String, Integer> normalized = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : values.entrySet()) {
            String id = com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData
                    .normalizeResourceId(entry.getKey());
            if (id == null || !id.equals(entry.getKey()) || !validSavedValue(entry.getValue())
                    || normalized.putIfAbsent(id, entry.getValue()) != null) {
                throw new IllegalArgumentException("supplemental progression entry is invalid");
            }
        }
        return Collections.unmodifiableMap(normalized);
    }
}
