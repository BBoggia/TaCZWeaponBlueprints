package com.gamergaming.taczweaponblueprints.api;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria.ChangeOperation;
import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria.ChangeResult;
import com.gamergaming.taczweaponblueprints.api.ProgressionCriteria.Status;

import net.minecraft.resources.ResourceLocation;

class ProgressionCriteriaTest {
    @Test
    void operationOperandsKeepAdministrativeClearExplicit() {
        assertTrue(ChangeOperation.GRANT.acceptsOperand(1));
        assertTrue(ChangeOperation.INCREMENT.acceptsOperand(1));
        assertFalse(ChangeOperation.GRANT.acceptsOperand(0));
        assertFalse(ChangeOperation.INCREMENT.acceptsOperand(0));
        assertTrue(ChangeOperation.ADMINISTRATIVE_CLEAR.acceptsOperand(0));
        assertFalse(ChangeOperation.ADMINISTRATIVE_CLEAR.acceptsOperand(1));
    }

    @Test
    void publicResultsDistinguishMutationFromIdempotentSuccess() {
        ResourceLocation id = new ResourceLocation("test:trial");
        ChangeResult applied = new ChangeResult(
                Status.APPLIED, id, ChangeOperation.GRANT, 1, 0, 1);
        ChangeResult unchanged = new ChangeResult(
                Status.UNCHANGED, id, ChangeOperation.GRANT, 1, 1, 1);

        assertTrue(applied.successful());
        assertTrue(applied.changed());
        assertTrue(unchanged.successful());
        assertFalse(unchanged.changed());
        assertThrows(IllegalArgumentException.class, () -> new ChangeResult(
                Status.APPLIED, id, ChangeOperation.GRANT, 1, 1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ChangeResult(
                Status.UNCHANGED, id, ChangeOperation.GRANT, 1, 0, 1));
        assertThrows(IllegalArgumentException.class, () ->
                ChangeResult.failure(Status.APPLIED));
    }
}
