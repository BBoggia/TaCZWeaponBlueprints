package com.gamergaming.taczweaponblueprints.progression.workbench;

import com.gamergaming.taczweaponblueprints.progression.ProgressionIds;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

/** Immutable identity of the workstation authorizing one server interaction. */
public record ResearchWorkbenchContext(
        BlockPos rootPosition,
        ResourceLocation dimensionId,
        ResourceLocation workstationId,
        ResearchWorkbenchTier tier,
        ResearchInteractionMode interactionMode,
        long sessionId) {
    public static final long NO_SESSION = 0L;

    public ResearchWorkbenchContext {
        if (rootPosition == null || tier == null || interactionMode == null || sessionId < 0L) {
            throw new IllegalArgumentException("invalid Research Bench context");
        }
        rootPosition = rootPosition.immutable();
        dimensionId = ProgressionIds.require(dimensionId, "workbench dimension ID");
        workstationId = ProgressionIds.require(workstationId, "workstation ID");
    }

    public static ResearchWorkbenchContext of(
            BlockPos rootPosition,
            String dimensionId,
            String workstationId,
            ResearchWorkbenchTier tier,
            ResearchInteractionMode interactionMode,
            long sessionId) {
        return new ResearchWorkbenchContext(
                rootPosition,
                ProgressionIds.parse(dimensionId, "workbench dimension ID"),
                ProgressionIds.parse(workstationId, "workstation ID"),
                tier,
                interactionMode,
                sessionId);
    }

    public boolean hasSession() {
        return sessionId != NO_SESSION;
    }

    public boolean sameWorkstation(ResearchWorkbenchContext other) {
        return other != null
                && rootPosition.equals(other.rootPosition)
                && dimensionId.equals(other.dimensionId)
                && workstationId.equals(other.workstationId)
                && tier == other.tier;
    }

    public ResearchWorkbenchContext transitionTo(
            ResearchInteractionMode mode,
            long nextSessionId) {
        return new ResearchWorkbenchContext(
                rootPosition,
                dimensionId,
                workstationId,
                tier,
                mode,
                nextSessionId);
    }
}
