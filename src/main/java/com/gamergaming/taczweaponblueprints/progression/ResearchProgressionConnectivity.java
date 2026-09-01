package com.gamergaming.taczweaponblueprints.progression;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;

import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchPrerequisiteGroup;
import com.gamergaming.taczweaponblueprints.resource.research.ResearchRequirements;

import net.minecraft.resources.ResourceLocation;

/**
 * Request-scoped authority for deciding whether learned research is connected
 * to a root of the effective prerequisite graph.
 *
 * <p>Knowledge and progression connectivity are deliberately separate. A
 * blueprint learned from loot, reverse engineering, or an administrator stays
 * learned and usable, but it cannot satisfy a later research requirement until
 * at least one complete route beneath it has also been learned. Connectivity is
 * derived from current policies and player data, so it needs no persisted
 * provenance and repairs itself as soon as the missing route is learned.</p>
 */
public final class ResearchProgressionConnectivity {
    private final IPlayerRecipeData playerData;
    private final Function<ResourceLocation, BlueprintResearchPolicy> policyResolver;
    private final Predicate<ResourceLocation> progressionExempt;
    private final Map<ResourceLocation, Boolean> connected = new HashMap<>();

    public ResearchProgressionConnectivity(
            IPlayerRecipeData playerData,
            Function<ResourceLocation, BlueprintResearchPolicy> policyResolver,
            Predicate<ResourceLocation> progressionExempt) {
        if (playerData == null || policyResolver == null || progressionExempt == null) {
            throw new IllegalArgumentException(
                    "research progression connectivity requires complete inputs");
        }
        this.playerData = playerData;
        this.policyResolver = policyResolver;
        this.progressionExempt = progressionExempt;
    }

    /** Returns whether every mandatory group has a root-connected alternative. */
    public boolean requirementsSatisfied(BlueprintResearchPolicy policy) {
        if (policy == null || policy.blueprintId() == null) {
            return false;
        }
        LinkedHashSet<ResourceLocation> visiting = new LinkedHashSet<>();
        visiting.add(policy.blueprintId());
        return requirementsSatisfied(policy.requirements(), visiting);
    }

    /** Returns whether this ID is exempt or learned through a complete root path. */
    public boolean isConnected(ResourceLocation blueprintId) {
        return isConnected(blueprintId, new LinkedHashSet<>());
    }

    /** Returns whether one any-of group has a root-connected alternative. */
    public boolean groupSatisfied(ResearchPrerequisiteGroup group) {
        return group != null && group.satisfiedBy(this::isConnected);
    }

    private boolean isConnected(
            ResourceLocation blueprintId,
            LinkedHashSet<ResourceLocation> visiting) {
        if (blueprintId == null) {
            return false;
        }
        try {
            if (progressionExempt.test(blueprintId)) {
                return true;
            }
        } catch (RuntimeException exception) {
            return false;
        }
        Boolean cached = connected.get(blueprintId);
        if (cached != null) {
            return cached;
        }
        if (!playerData.hasBlueprint(blueprintId.toString())
                || visiting.size() >= BlueprintResearchSnapshot.MAX_PREREQUISITE_DEPTH
                || !visiting.add(blueprintId)) {
            return false;
        }

        boolean result = false;
        try {
            BlueprintResearchPolicy policy = policyResolver.apply(blueprintId);
            result = policy != null
                    && blueprintId.equals(policy.blueprintId())
                    && policy.playerDataAvailable()
                    && policy.learned()
                    && requirementsSatisfied(policy.requirements(), visiting);
        } catch (RuntimeException exception) {
            result = false;
        } finally {
            visiting.remove(blueprintId);
        }
        connected.put(blueprintId, result);
        return result;
    }

    private boolean requirementsSatisfied(
            ResearchRequirements requirements,
            LinkedHashSet<ResourceLocation> visiting) {
        if (requirements == null) {
            return false;
        }
        for (ResearchPrerequisiteGroup group : requirements.allOf()) {
            boolean groupSatisfied = false;
            for (ResourceLocation alternative : group.anyOf()) {
                if (isConnected(alternative, visiting)) {
                    groupSatisfied = true;
                    break;
                }
            }
            if (!groupSatisfied) {
                return false;
            }
        }
        return true;
    }
}
