package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.ResearchCostMode;
import com.gamergaming.taczweaponblueprints.progression.ResearchGuidanceSnapshot;

/** Pure compact presentation model for one authoritative tracked research goal. */
public final class ResearchGoalProgressPresenter {
    private ResearchGoalProgressPresenter() {
    }

    public static Presentation present(
            Optional<ResearchGuidanceSnapshot> currentSnapshot) {
        return present(currentSnapshot, false);
    }

    public static Presentation present(
            Optional<ResearchGuidanceSnapshot> currentSnapshot,
            boolean unavailable) {
        if (currentSnapshot == null || currentSnapshot.isEmpty()) {
            return unavailable ? Presentation.unavailable() : Presentation.checking();
        }
        ResearchGuidanceSnapshot snapshot = currentSnapshot.orElseThrow();
        Status status = !snapshot.transactionCapacityAvailable()
                        && snapshot.routeAvailable()
                        && snapshot.state() != ResearchGuidanceSnapshot.State.LEARNED
                ? Status.TRANSACTION_BLOCKED
                : switch (snapshot.state()) {
            case LEARNED -> Status.COMPLETE;
            case AFFORDABLE -> Status.READY;
            case MISSING_POINTS -> Status.MISSING_POINTS;
            case MISSING_MATERIALS -> Status.MISSING_MATERIALS;
            case MISSING_POINTS_AND_MATERIALS -> Status.MISSING_POINTS_AND_MATERIALS;
            case POLICY_BLOCKED -> Status.POLICY_BLOCKED;
            case ROUTE_UNAVAILABLE -> Status.ROUTE_UNAVAILABLE;
            case CHECKING -> Status.CHECKING;
                };
        boolean showEconomy = snapshot.routeAvailable()
                && snapshot.state() != ResearchGuidanceSnapshot.State.LEARNED;
        Optional<Progress> points = showEconomy
                        && snapshot.costMode().pointsEnabled()
                        && !snapshot.costBypassed()
                        && snapshot.pointCost() > 0
                ? Optional.of(new Progress(
                        Math.min(snapshot.pointBalance(), snapshot.pointCost()),
                        snapshot.pointCost()))
                : Optional.empty();
        Optional<Progress> materials = showEconomy
                        && snapshot.costMode().itemsEnabled()
                        && !snapshot.costBypassed()
                        && snapshot.totalMaterialUnits() > 0
                ? Optional.of(new Progress(
                        snapshot.allocatedMaterialUnits(),
                        snapshot.totalMaterialUnits()))
                : Optional.empty();
        return new Presentation(
                status,
                snapshot.costMode(),
                snapshot.costBypassed(),
                points,
                materials,
                snapshot.missingMaterialTypes(),
                snapshot.totalMaterialTypes(),
                Math.max(0, snapshot.totalMaterialTypes() - snapshot.materials().size()),
                snapshot.materials());
    }

    public enum Status {
        CHECKING,
        COMPLETE,
        READY,
        MISSING_POINTS,
        MISSING_MATERIALS,
        MISSING_POINTS_AND_MATERIALS,
        TRANSACTION_BLOCKED,
        POLICY_BLOCKED,
        ROUTE_UNAVAILABLE
    }

    public record Progress(int available, int required) {
        public Progress {
            if (available < 0 || required < 0 || available > required) {
                throw new IllegalArgumentException("invalid research goal progress");
            }
        }

        public int missing() {
            return required - available;
        }

        public boolean complete() {
            return available >= required;
        }
    }

    public record Presentation(
            Status status,
            ResearchCostMode costMode,
            boolean costBypassed,
            Optional<Progress> points,
            Optional<Progress> materials,
            int missingMaterialTypes,
            int totalMaterialTypes,
            int additionalMaterialRows,
            List<ResearchGuidanceSnapshot.MaterialProgress> displayedMaterials) {
        public Presentation {
            points = points == null ? Optional.empty() : points;
            materials = materials == null ? Optional.empty() : materials;
            displayedMaterials = displayedMaterials == null
                    ? List.of()
                    : List.copyOf(displayedMaterials);
            if (status == null || costMode == null
                    || missingMaterialTypes < 0
                    || missingMaterialTypes > totalMaterialTypes
                    || totalMaterialTypes < 0
                    || additionalMaterialRows < 0
                    || displayedMaterials.size() + additionalMaterialRows
                            != totalMaterialTypes
                    || status == Status.CHECKING && (points.isPresent()
                            || materials.isPresent() || costBypassed
                            || totalMaterialTypes != 0)) {
                throw new IllegalArgumentException("invalid research goal presentation");
            }
        }

        private static Presentation checking() {
            return new Presentation(
                    Status.CHECKING,
                    ResearchCostMode.POINTS_AND_ITEMS,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    0,
                    0,
                    List.of());
        }

        private static Presentation unavailable() {
            return new Presentation(
                    Status.ROUTE_UNAVAILABLE,
                    ResearchCostMode.POINTS_AND_ITEMS,
                    false,
                    Optional.empty(),
                    Optional.empty(),
                    0,
                    0,
                    0,
                    List.of());
        }
    }
}
