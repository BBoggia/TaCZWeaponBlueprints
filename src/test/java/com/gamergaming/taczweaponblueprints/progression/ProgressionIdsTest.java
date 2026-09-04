package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;

import net.minecraft.resources.ResourceLocation;

class ProgressionIdsTest {
    @Test
    void stringIdsAreTrimmedLowercasedAndBounded() {
        assertEquals(
                new ResourceLocation("example", "weapon/trial"),
                ProgressionIds.parse("  EXAMPLE:Weapon/Trial  ", "test ID"));

        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.parse(null, "test ID"));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.parse(" ", "test ID"));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.parse("not valid", "test ID"));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.parse(
                        "test:" + "a".repeat(PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH),
                        "test ID"));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.require(null, "test ID"));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.require(
                        new ResourceLocation("test", "a".repeat(
                                PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)),
                        "test ID"));
    }

    @Test
    void messageKeysAreCanonicalAndRejectUnsafeText() {
        assertEquals(
                "gui.example.requirement-ready",
                ProgressionIds.messageKey(" GUI.Example.Requirement-Ready ", "message"));

        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.messageKey(null, "message"));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.messageKey("", "message"));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.messageKey("contains spaces", "message"));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.messageKey("test:key", "message"));
        assertThrows(IllegalArgumentException.class,
                () -> ProgressionIds.messageKey(
                        "a".repeat(ProgressionIds.MAX_MESSAGE_KEY_LENGTH + 1),
                        "message"));
    }
}
