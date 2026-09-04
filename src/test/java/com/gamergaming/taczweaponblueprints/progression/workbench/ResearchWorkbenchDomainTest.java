package com.gamergaming.taczweaponblueprints.progression.workbench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;

class ResearchWorkbenchDomainTest {
    private static final ResourceLocation DIMENSION = new ResourceLocation("test", "range");
    private static final ResourceLocation WORKSTATION = new ResourceLocation("test", "bench");

    @Test
    void tierOrderingIsExhaustiveAndHigherTiersInheritLowerWork() {
        for (ResearchWorkbenchTier actual : ResearchWorkbenchTier.values()) {
            for (ResearchWorkbenchTier required : ResearchWorkbenchTier.values()) {
                assertEquals(actual.level() >= required.level(), actual.satisfies(required));
            }
        }

        assertTrue(ResearchWorkbenchTier.TIER_3.satisfies(ResearchWorkbenchTier.TIER_1));
        assertTrue(ResearchWorkbenchTier.TIER_3.satisfies(ResearchWorkbenchTier.TIER_2));
        assertTrue(ResearchWorkbenchTier.TIER_3.satisfies(ResearchWorkbenchTier.TIER_3));
        assertFalse(ResearchWorkbenchTier.TIER_1.satisfies(ResearchWorkbenchTier.TIER_2));
        assertSame(
                ResearchWorkbenchTier.TIER_3,
                ResearchWorkbenchTier.TIER_3.higherOf(ResearchWorkbenchTier.TIER_3));
        assertSame(
                ResearchWorkbenchTier.TIER_2,
                ResearchWorkbenchTier.TIER_1.higherOf(ResearchWorkbenchTier.TIER_2));
    }

    @Test
    void tierParsingAndTraversalAreCanonicalAndStrict() {
        assertSame(ResearchWorkbenchTier.TIER_1, ResearchWorkbenchTier.parse(" tier-1 "));
        assertSame(ResearchWorkbenchTier.TIER_2, ResearchWorkbenchTier.parse("2"));
        assertSame(ResearchWorkbenchTier.TIER_3, ResearchWorkbenchTier.fromLevel(3));
        assertEquals("tier_2", ResearchWorkbenchTier.TIER_2.serializedName());
        assertEquals(
                ResearchWorkbenchTier.TIER_2,
                ResearchWorkbenchTier.TIER_1.next().orElseThrow());
        assertTrue(ResearchWorkbenchTier.TIER_3.next().isEmpty());

        assertThrows(IllegalArgumentException.class, () -> ResearchWorkbenchTier.parse(null));
        assertThrows(IllegalArgumentException.class, () -> ResearchWorkbenchTier.parse("tier_4"));
        assertThrows(IllegalArgumentException.class, () -> ResearchWorkbenchTier.fromLevel(0));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchWorkbenchTier.TIER_1.satisfies(null));
        assertThrows(IllegalArgumentException.class,
                () -> ResearchWorkbenchTier.TIER_1.higherOf(null));
    }

    @Test
    void contextDefensivelyFreezesPositionAndCanonicalizesStringIds() {
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos(4, 70, -9);
        ResearchWorkbenchContext context = ResearchWorkbenchContext.of(
                mutable,
                " TEST:RANGE ",
                " TEST:BENCH ",
                ResearchWorkbenchTier.TIER_2,
                ResearchInteractionMode.RESEARCH,
                42L);
        mutable.set(100, 100, 100);

        assertEquals(new BlockPos(4, 70, -9), context.rootPosition());
        assertEquals(DIMENSION, context.dimensionId());
        assertEquals(WORKSTATION, context.workstationId());
        assertTrue(context.hasSession());

        ResearchWorkbenchContext crafting = context.transitionTo(
                ResearchInteractionMode.CRAFTING,
                43L);
        assertTrue(context.sameWorkstation(crafting));
        assertEquals(ResearchInteractionMode.CRAFTING, crafting.interactionMode());
        assertEquals(43L, crafting.sessionId());
        assertFalse(context.sameWorkstation(new ResearchWorkbenchContext(
                context.rootPosition(),
                DIMENSION,
                WORKSTATION,
                ResearchWorkbenchTier.TIER_3,
                ResearchInteractionMode.RESEARCH,
                42L)));
    }

    @Test
    void contextRejectsMissingFieldsAndNegativeSessions() {
        assertThrows(IllegalArgumentException.class, () -> context(null, DIMENSION, WORKSTATION, 0));
        assertThrows(IllegalArgumentException.class, () -> context(BlockPos.ZERO, null, WORKSTATION, 0));
        assertThrows(IllegalArgumentException.class, () -> context(BlockPos.ZERO, DIMENSION, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchWorkbenchContext(
                BlockPos.ZERO,
                DIMENSION,
                WORKSTATION,
                null,
                ResearchInteractionMode.RESEARCH,
                0));
        assertThrows(IllegalArgumentException.class, () -> new ResearchWorkbenchContext(
                BlockPos.ZERO,
                DIMENSION,
                WORKSTATION,
                ResearchWorkbenchTier.TIER_1,
                null,
                0));
        assertThrows(IllegalArgumentException.class, () -> context(
                BlockPos.ZERO,
                DIMENSION,
                WORKSTATION,
                -1));
        assertFalse(context(BlockPos.ZERO, DIMENSION, WORKSTATION, 0).hasSession());
    }

    @Test
    void legacyCombinedRequirementRemainsStrictAuthoringInput() {
        assertEquals(
                new ResearchWorkbenchTierRequirement(
                        ResearchWorkbenchTier.TIER_2,
                        ResearchWorkbenchTier.TIER_3),
                new ResearchWorkbenchTierRequirement(
                        ResearchWorkbenchTier.TIER_2,
                        ResearchWorkbenchTier.TIER_3));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchWorkbenchTierRequirement(null, ResearchWorkbenchTier.TIER_1));
        assertThrows(IllegalArgumentException.class,
                () -> new ResearchWorkbenchTierRequirement(ResearchWorkbenchTier.TIER_1, null));
    }

    private static ResearchWorkbenchContext context(
            BlockPos position,
            ResourceLocation dimension,
            ResourceLocation workstation,
            long session) {
        return context(
                position,
                dimension,
                workstation,
                session,
                ResearchWorkbenchTier.TIER_1,
                ResearchInteractionMode.RESEARCH);
    }

    private static ResearchWorkbenchContext context(
            BlockPos position,
            ResourceLocation dimension,
            ResourceLocation workstation,
            long session,
            ResearchWorkbenchTier tier,
            ResearchInteractionMode mode) {
        return new ResearchWorkbenchContext(
                position,
                dimension,
                workstation,
                tier,
                mode,
                session);
    }
}
