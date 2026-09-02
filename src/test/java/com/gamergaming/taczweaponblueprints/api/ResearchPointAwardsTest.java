package com.gamergaming.taczweaponblueprints.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import net.minecraft.resources.ResourceLocation;

class ResearchPointAwardsTest {
    @Test
    void registrationIsIdempotentBoundedAndDeterministicallyPublished() {
        ResourceLocation later = new ResourceLocation("phase7_test:z_later");
        ResourceLocation earlier = new ResourceLocation("phase7_test:a_earlier");

        ResearchPointAwards.registerSource(later);
        ResearchPointAwards.registerSource(earlier);

        assertFalse(ResearchPointAwards.registerSource(earlier));
        assertTrue(ResearchPointAwards.isRegistered(later));
        List<ResourceLocation> matching = ResearchPointAwards.registeredSources().stream()
                .filter(id -> id.getNamespace().equals("phase7_test"))
                .toList();
        assertEquals(List.of(earlier, later), matching);
        assertThrows(IllegalArgumentException.class,
                () -> ResearchPointAwards.registerSource(null));
    }

    @Test
    void publicResultsCannotRepresentInvalidCounts() {
        assertThrows(IllegalArgumentException.class, () -> new ResearchPointAwards.Result(
                ResearchPointAwards.Status.TRIGGERED, -1, 1));
        assertThrows(IllegalArgumentException.class, () -> new ResearchPointAwards.Result(
                ResearchPointAwards.Status.TRIGGERED, 1, 65));
        assertTrue(new ResearchPointAwards.Result(
                ResearchPointAwards.Status.TRIGGERED, 2, 1).successful());
        assertFalse(new ResearchPointAwards.Result(
                ResearchPointAwards.Status.NO_MATCH, 0, 0).successful());
    }
}
