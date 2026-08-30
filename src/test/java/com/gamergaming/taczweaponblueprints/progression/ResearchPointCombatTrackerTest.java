package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.UUID;

import com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardContext.SpawnProvenance;
import org.junit.jupiter.api.Test;

import net.minecraft.world.entity.MobSpawnType;

class ResearchPointCombatTrackerTest {
    @Test
    void mapsFarmableSpawnReasonsToExplicitUnsafeProvenance() {
        assertEquals(SpawnProvenance.NATURAL,
                ResearchPointCombatTracker.classifySpawnType(MobSpawnType.NATURAL));
        assertEquals(SpawnProvenance.STRUCTURE,
                ResearchPointCombatTracker.classifySpawnType(MobSpawnType.CHUNK_GENERATION));
        assertEquals(SpawnProvenance.STRUCTURE,
                ResearchPointCombatTracker.classifySpawnType(MobSpawnType.STRUCTURE));
        assertEquals(SpawnProvenance.SPAWNER,
                ResearchPointCombatTracker.classifySpawnType(MobSpawnType.SPAWNER));
        assertEquals(SpawnProvenance.BRED,
                ResearchPointCombatTracker.classifySpawnType(MobSpawnType.BREEDING));

        for (MobSpawnType type : new MobSpawnType[] {
                MobSpawnType.MOB_SUMMONED,
                MobSpawnType.TRIGGERED,
                MobSpawnType.SPAWN_EGG,
                MobSpawnType.COMMAND,
                MobSpawnType.DISPENSER}) {
            assertEquals(SpawnProvenance.SUMMONED,
                    ResearchPointCombatTracker.classifySpawnType(type));
        }
        assertEquals(SpawnProvenance.OTHER,
                ResearchPointCombatTracker.classifySpawnType(MobSpawnType.CONVERSION));
    }

    @Test
    void suppressesOnlyRecentDuplicateDeathCallbacks() {
        ResearchPointCombatRecentEvents events = new ResearchPointCombatRecentEvents();
        UUID victim = UUID.randomUUID();

        assertTrue(events.accept(victim, 100L));
        assertFalse(events.accept(victim, 100L));
        assertTrue(events.accept(victim, 101L),
                "a later real death of the same player UUID is a distinct event");
        assertTrue(events.accept(victim, 121L));
        assertTrue(events.accept(victim, 50L), "server-time rollback must discard future cache entries");
    }

    @Test
    void duplicateCacheRemainsStrictlyBounded() {
        ResearchPointCombatRecentEvents events = new ResearchPointCombatRecentEvents();
        for (int index = 0; index <= ResearchPointCombatRecentEvents.MAX_ENTRIES; index++) {
            assertTrue(events.accept(new UUID(0L, index), 10L));
        }

        assertEquals(ResearchPointCombatRecentEvents.MAX_ENTRIES, events.size());
        assertTrue(events.accept(new UUID(0L, 0L), 10L),
                "the oldest token must be evicted when the hard limit is exceeded");
        events.clear();
        assertEquals(0, events.size());
    }
}
