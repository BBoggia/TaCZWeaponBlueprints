package com.gamergaming.taczweaponblueprints.progression;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;

import net.minecraft.resources.ResourceLocation;

/** Bounded, disclosure-safe authoritative guidance for one research target. */
public record ResearchGuidanceSnapshot(
        ResourceLocation targetId,
        State state,
        int pointCost,
        int pointBalance,
        ResearchCostMode costMode,
        boolean costBypassed,
        boolean transactionCapacityAvailable,
        int totalMaterialTypes,
        int totalMaterialUnits,
        int allocatedMaterialUnits,
        int missingMaterialTypes,
        List<MaterialProgress> materials,
        List<ResourceLocation> supportIds,
        List<ResourceLocation> purchaseIds,
        List<ResearchPathUnlockPlanner.SelectedRequirement> selectedRequirements,
        Optional<ResourceLocation> nextStepId) {
    /**
     * Guidance is transported as one optional UI packet, so its disclosure payload is
     * intentionally smaller than the server-side transaction limits. The transaction
     * remains valid when a route exceeds these values; the client simply receives an
     * unavailable guidance state instead of a dangerously large packet.
     */
    public static final int MAX_MATERIAL_PROGRESS = 32;
    public static final int MAX_SUPPORT_IDS = 1_024;
    public static final int MAX_PURCHASE_IDS = 1_024;
    public static final int MAX_SELECTED_REQUIREMENTS = 1_024;

    public ResearchGuidanceSnapshot {
        materials = materials == null ? List.of() : List.copyOf(materials);
        supportIds = supportIds == null ? List.of() : List.copyOf(supportIds);
        purchaseIds = purchaseIds == null ? List.of() : List.copyOf(purchaseIds);
        selectedRequirements = selectedRequirements == null
                ? List.of()
                : List.copyOf(selectedRequirements);
        nextStepId = nextStepId == null ? Optional.empty() : nextStepId;
        Set<ResourceLocation> support = new LinkedHashSet<>();
        Set<ResourceLocation> purchases = new LinkedHashSet<>();
        Set<String> groups = new LinkedHashSet<>();
        if (materials.stream().anyMatch(java.util.Objects::isNull)) {
            throw new IllegalArgumentException("research guidance material cannot be null");
        }
        int visibleRequired = 0;
        int visibleAllocated = 0;
        int visibleMissing = 0;
        try {
            for (MaterialProgress material : materials) {
                visibleRequired = Math.addExact(visibleRequired, material.required());
                visibleAllocated = Math.addExact(visibleAllocated, material.allocated());
                if (material.allocated() < material.required()) {
                    visibleMissing++;
                }
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("research guidance material totals overflow", exception);
        }
        if (!validId(targetId) || state == null || costMode == null
                || pointCost < 0 || pointCost > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || pointBalance < 0
                || pointBalance > PlayerProgressionLimits.MAX_RESEARCH_POINTS
                || !costMode.pointsEnabled() && (pointCost != 0 || pointBalance != 0)
                || totalMaterialTypes < 0
                || totalMaterialTypes > BlueprintResearchSnapshot.MAX_TOTAL_PREREQUISITES
                || totalMaterialUnits < 0
                || totalMaterialUnits > ResearchIngredientPlanner.MAX_TOTAL_REQUIREMENT_COUNT
                || allocatedMaterialUnits < 0
                || allocatedMaterialUnits > totalMaterialUnits
                || missingMaterialTypes < 0
                || missingMaterialTypes > totalMaterialTypes
                || totalMaterialTypes == 0
                        && (totalMaterialUnits != 0
                                || allocatedMaterialUnits != 0
                                || missingMaterialTypes != 0)
                || materials.size() > MAX_MATERIAL_PROGRESS
                || materials.size() > totalMaterialTypes
                || !costMode.itemsEnabled() && (totalMaterialTypes != 0
                        || totalMaterialUnits != 0 || allocatedMaterialUnits != 0
                        || missingMaterialTypes != 0 || !materials.isEmpty())
                || visibleRequired > ResearchIngredientPlanner.MAX_TOTAL_REQUIREMENT_COUNT
                || visibleAllocated > visibleRequired
                || visibleRequired > totalMaterialUnits
                || visibleAllocated > allocatedMaterialUnits
                || visibleMissing > missingMaterialTypes
                || materials.size() == totalMaterialTypes
                        && (visibleRequired != totalMaterialUnits
                                || visibleAllocated != allocatedMaterialUnits
                                || visibleMissing != missingMaterialTypes)
                || supportIds.isEmpty()
                || supportIds.size() > MAX_SUPPORT_IDS
                || supportIds.stream().anyMatch(id -> !validId(id) || !support.add(id))
                || !support.contains(targetId)
                || purchaseIds.size() > MAX_PURCHASE_IDS
                || purchaseIds.stream().anyMatch(id -> !validId(id) || !purchases.add(id))
                || !support.containsAll(purchases)
                || selectedRequirements.size() > MAX_SELECTED_REQUIREMENTS
                || selectedRequirements.stream().anyMatch(java.util.Objects::isNull)
                || selectedRequirements.stream().anyMatch(requirement ->
                        !support.contains(requirement.dependentId())
                                || !support.contains(requirement.prerequisiteId())
                                || !groups.add(requirement.dependentId() + "\u0000"
                                        + requirement.groupOrdinal()))
                || nextStepId.filter(id -> !purchases.contains(id)).isPresent()
                || (state.routeExpected() && (purchaseIds.isEmpty() || nextStepId.isEmpty()))
                || (state.routeUnavailable() && (supportIds.size() != 1
                        || !purchaseIds.isEmpty()
                        || !selectedRequirements.isEmpty() || nextStepId.isPresent()
                        || pointCost != 0 || totalMaterialTypes != 0
                        || totalMaterialUnits != 0 || allocatedMaterialUnits != 0
                        || missingMaterialTypes != 0))
                || (state == State.LEARNED && (supportIds.size() != 1
                        || !purchaseIds.isEmpty()
                        || !selectedRequirements.isEmpty() || nextStepId.isPresent()
                        || pointCost != 0 || totalMaterialTypes != 0
                        || totalMaterialUnits != 0 || allocatedMaterialUnits != 0
                        || missingMaterialTypes != 0))
                || !state.matchesResourceState(
                        costMode,
                        costBypassed,
                        pointBalance,
                        pointCost,
                        allocatedMaterialUnits,
                        totalMaterialUnits,
                        missingMaterialTypes)) {
            throw new IllegalArgumentException("research guidance snapshot is invalid");
        }
    }

    /** Convenience constructor for complete, non-truncated in-process fixtures. */
    public ResearchGuidanceSnapshot(
            ResourceLocation targetId,
            State state,
            int pointCost,
            int pointBalance,
            ResearchCostMode costMode,
            boolean costBypassed,
            boolean transactionCapacityAvailable,
            int totalMaterialTypes,
            List<MaterialProgress> materials,
            List<ResourceLocation> supportIds,
            List<ResourceLocation> purchaseIds,
            List<ResearchPathUnlockPlanner.SelectedRequirement> selectedRequirements,
            Optional<ResourceLocation> nextStepId) {
        this(
                targetId,
                state,
                pointCost,
                pointBalance,
                costMode,
                costBypassed,
                transactionCapacityAvailable,
                totalMaterialTypes,
                visibleRequired(materials),
                visibleAllocated(materials),
                visibleMissingTypes(materials),
                materials,
                supportIds,
                purchaseIds,
                selectedRequirements,
                nextStepId);
    }

    public boolean routeAvailable() {
        return state == State.LEARNED || !purchaseIds.isEmpty();
    }

    private static boolean validId(ResourceLocation id) {
        return id != null
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    private static int visibleRequired(List<MaterialProgress> materials) {
        return safeMaterialSum(materials, MaterialProgress::required);
    }

    private static int visibleAllocated(List<MaterialProgress> materials) {
        return safeMaterialSum(materials, MaterialProgress::allocated);
    }

    private static int visibleMissingTypes(List<MaterialProgress> materials) {
        if (materials == null) {
            return 0;
        }
        return Math.toIntExact(materials.stream()
                .filter(java.util.Objects::nonNull)
                .filter(material -> material.allocated() < material.required())
                .count());
    }

    private static int safeMaterialSum(
            List<MaterialProgress> materials,
            java.util.function.ToIntFunction<MaterialProgress> value) {
        if (materials == null) {
            return 0;
        }
        int total = 0;
        try {
            for (MaterialProgress material : materials) {
                if (material != null) {
                    total = Math.addExact(total, value.applyAsInt(material));
                }
            }
        } catch (ArithmeticException exception) {
            throw new IllegalArgumentException("research guidance material totals overflow", exception);
        }
        return total;
    }

    public enum State {
        LEARNED,
        AFFORDABLE,
        MISSING_POINTS,
        MISSING_MATERIALS,
        MISSING_POINTS_AND_MATERIALS,
        POLICY_BLOCKED,
        ROUTE_UNAVAILABLE,
        CHECKING;

        public boolean resourceAffordable() {
            return this == LEARNED || this == AFFORDABLE;
        }

        private boolean routeExpected() {
            return this == AFFORDABLE
                    || this == MISSING_POINTS
                    || this == MISSING_MATERIALS
                    || this == MISSING_POINTS_AND_MATERIALS;
        }

        private boolean routeUnavailable() {
            return this == POLICY_BLOCKED || this == ROUTE_UNAVAILABLE || this == CHECKING;
        }

        private boolean matchesResourceState(
                ResearchCostMode costMode,
                boolean costBypassed,
                int pointBalance,
                int pointCost,
                int allocatedMaterialUnits,
                int totalMaterialUnits,
                int missingMaterialTypes) {
            if (!routeExpected()) {
                return true;
            }
            boolean pointsMissing = costMode.pointsEnabled()
                    && !costBypassed
                    && pointBalance < pointCost;
            boolean materialsMissing = costMode.itemsEnabled()
                    && !costBypassed
                    && (allocatedMaterialUnits < totalMaterialUnits
                            || missingMaterialTypes > 0);
            return switch (this) {
                case AFFORDABLE -> !pointsMissing && !materialsMissing;
                case MISSING_POINTS -> pointsMissing && !materialsMissing;
                case MISSING_MATERIALS -> !pointsMissing && materialsMissing;
                case MISSING_POINTS_AND_MATERIALS -> pointsMissing && materialsMissing;
                default -> true;
            };
        }
    }

    public record MaterialProgress(
            List<ResourceLocation> items,
            Optional<ResourceLocation> tag,
            int required,
            int allocated) {
        public MaterialProgress {
            items = items == null ? List.of() : List.copyOf(items);
            tag = tag == null ? Optional.empty() : tag;
            if (items.size() > BlueprintResearchIngredient.MAX_ITEMS
                    || items.stream().anyMatch(id -> !validId(id))
                    || tag.filter(id -> !validId(id)).isPresent()
                    || items.isEmpty() == tag.isEmpty()
                    || required < 1
                    || required > ResearchIngredientPlanner.MAX_TOTAL_REQUIREMENT_COUNT
                    || allocated < 0 || allocated > required) {
                throw new IllegalArgumentException("research guidance material is invalid");
            }
        }
    }
}
