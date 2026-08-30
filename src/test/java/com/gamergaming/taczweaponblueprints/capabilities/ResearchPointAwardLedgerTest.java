package com.gamergaming.taczweaponblueprints.capabilities;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.BudgetUpdate;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ClaimKey;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.CooldownUpdate;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.Mutation;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.ScopeKey;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.WindowEntry;
import com.gamergaming.taczweaponblueprints.capabilities.ResearchPointAwardLedger.WindowUpdate;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;

class ResearchPointAwardLedgerTest {
    @Test
    void atomicallyRecordsAndDeterministicallyRoundTripsEveryStateKind() {
        ResearchPointAwardLedger ledger = new ResearchPointAwardLedger();
        ClaimKey claim = ClaimKey.targeted(id("test:first_discovery"), id("tacz:ak47"));
        ScopeKey scope = ScopeKey.targeted(id("test:discovery_cooldown"), id("tacz:ak47"));
        ResourceLocation budgetId = id("test:discovery_budget");
        Mutation mutation = new Mutation(
                Optional.of(claim),
                Optional.of(new CooldownUpdate(scope, 120L)),
                Optional.of(new WindowUpdate(scope, new WindowEntry(120L, 2))),
                Optional.of(new BudgetUpdate(budgetId, new WindowEntry(120L, 2))));

        assertTrue(ledger.apply(mutation));
        assertFalse(ledger.apply(mutation));
        assertTrue(ledger.hasClaim(claim));
        assertEquals(120L, ledger.lastAwardGameTime(scope).orElseThrow());
        assertEquals(new ResearchPointAwardLedger.Usage(1, 2L), ledger.windowUsage(scope, 0L));
        assertEquals(new ResearchPointAwardLedger.Usage(1, 2L), ledger.budgetUsage(budgetId, 0L));
        assertEquals(1, ledger.claimCount());
        assertEquals(3, ledger.rateStateCount());
        assertEquals(2, ledger.windowEntryCount());

        CompoundTag serialized = ledger.serializeNBT();
        ResearchPointAwardLedger restored = new ResearchPointAwardLedger();
        restored.deserializeNBT(serialized);

        assertEquals(ledger.claims(), restored.claims());
        assertEquals(ledger.cooldowns(), restored.cooldowns());
        assertEquals(ledger.windows(), restored.windows());
        assertEquals(ledger.budgets(), restored.budgets());
        assertEquals(serialized, restored.serializeNBT());
    }

    @Test
    void pruningUsesInclusiveServerGameTimeAndReleasesEmptyRateStates() {
        ResearchPointAwardLedger ledger = new ResearchPointAwardLedger();
        ScopeKey scope = ScopeKey.global(id("test:combat_window"));
        ResourceLocation budget = id("test:combat_budget");
        assertTrue(ledger.apply(new Mutation(
                Optional.empty(),
                Optional.empty(),
                Optional.of(new WindowUpdate(scope, new WindowEntry(10L, 2))),
                Optional.of(new BudgetUpdate(budget, new WindowEntry(10L, 2))))));
        assertTrue(ledger.apply(new Mutation(
                Optional.empty(),
                Optional.empty(),
                Optional.of(new WindowUpdate(scope, new WindowEntry(20L, 3))),
                Optional.of(new BudgetUpdate(budget, new WindowEntry(20L, 3))))));

        assertEquals(new ResearchPointAwardLedger.Usage(1, 3L), ledger.windowUsage(scope, 20L));
        assertEquals(new ResearchPointAwardLedger.Usage(1, 3L), ledger.budgetUsage(budget, 20L));
        assertEquals(2, ledger.windowEntryCount());

        assertEquals(new ResearchPointAwardLedger.Usage(0, 0L), ledger.windowUsage(scope, 21L));
        assertEquals(new ResearchPointAwardLedger.Usage(0, 0L), ledger.budgetUsage(budget, 21L));
        assertEquals(0, ledger.rateStateCount());
        assertEquals(0, ledger.windowEntryCount());
    }

    @Test
    void rejectsBackwardCooldownAndCapacityFailuresWithoutPartialMutation() {
        ResearchPointAwardLedger ledger = new ResearchPointAwardLedger();
        ScopeKey firstScope = ScopeKey.global(id("test:cooldown_0"));
        assertTrue(ledger.apply(new Mutation(
                Optional.empty(),
                Optional.of(new CooldownUpdate(firstScope, 100L)),
                Optional.empty(),
                Optional.empty())));
        assertFalse(ledger.apply(new Mutation(
                Optional.of(ClaimKey.once(id("test:must_not_commit"))),
                Optional.of(new CooldownUpdate(firstScope, 99L)),
                Optional.empty(),
                Optional.empty())));
        assertFalse(ledger.hasClaim(ClaimKey.once(id("test:must_not_commit"))));
        assertEquals(100L, ledger.lastAwardGameTime(firstScope).orElseThrow());

        for (int index = 1;
                index < PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_RATE_STATES;
                index++) {
            assertTrue(ledger.apply(new Mutation(
                    Optional.empty(),
                    Optional.of(new CooldownUpdate(
                            ScopeKey.global(id("test:cooldown_" + index)), index)),
                    Optional.empty(),
                    Optional.empty())));
        }
        ClaimKey capacityClaim = ClaimKey.once(id("test:capacity_claim"));
        assertFalse(ledger.apply(new Mutation(
                Optional.of(capacityClaim),
                Optional.empty(),
                Optional.of(new WindowUpdate(
                        ScopeKey.global(id("test:one_too_many")), new WindowEntry(500L, 1))),
                Optional.empty())));
        assertFalse(ledger.hasClaim(capacityClaim));
        assertEquals(PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_RATE_STATES,
                ledger.rateStateCount());
    }

    @Test
    void enforcesClaimAndWindowEntryLimitsWithoutEviction() {
        ResearchPointAwardLedger claims = new ResearchPointAwardLedger();
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_CLAIMS;
                index++) {
            assertTrue(claims.apply(Mutation.claim(
                    ClaimKey.once(id("test:claim_" + index)))));
        }
        ClaimKey overflow = ClaimKey.once(id("test:claim_overflow"));
        assertFalse(claims.apply(Mutation.claim(overflow)));
        assertFalse(claims.hasClaim(overflow));

        ResearchPointAwardLedger windows = new ResearchPointAwardLedger();
        ScopeKey scope = ScopeKey.global(id("test:long_window"));
        for (int index = 0;
                index < PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_WINDOW_ENTRIES;
                index++) {
            assertTrue(windows.apply(new Mutation(
                    Optional.empty(),
                    Optional.empty(),
                    Optional.of(new WindowUpdate(scope, new WindowEntry(index, 1))),
                    Optional.empty())));
        }
        assertFalse(windows.apply(new Mutation(
                Optional.empty(),
                Optional.empty(),
                Optional.of(new WindowUpdate(scope, new WindowEntry(5000L, 1))),
                Optional.empty())));
        assertEquals(PlayerProgressionLimits.MAX_RESEARCH_POINT_AWARD_WINDOW_ENTRIES,
                windows.windowEntryCount());
    }

    @Test
    void malformedPersistedEntriesAreIgnoredAndFutureVersionsRetainKnownFacts() {
        CompoundTag root = new CompoundTag();
        root.putInt("LedgerVersion", 99);
        ListTag claims = new ListTag();
        claims.add(claim("test:valid", null));
        claims.add(claim("not a resource location", null));
        claims.add(claim("a:" + "x".repeat(300), null));
        root.put("Claims", claims);

        ListTag cooldowns = new ListTag();
        CompoundTag negativeCooldown = claim("test:negative", null);
        negativeCooldown.putLong("LastAwardGameTime", -1L);
        cooldowns.add(negativeCooldown);
        CompoundTag validCooldown = claim("test:cooldown", "test:target");
        validCooldown.putLong("LastAwardGameTime", 44L);
        cooldowns.add(validCooldown);
        root.put("Cooldowns", cooldowns);

        ListTag windows = new ListTag();
        CompoundTag window = claim("test:window", null);
        ListTag entries = new ListTag();
        CompoundTag invalidEntry = new CompoundTag();
        invalidEntry.putLong("GameTime", 1L);
        invalidEntry.putLong("Points", Integer.MAX_VALUE);
        entries.add(invalidEntry);
        CompoundTag validEntry = new CompoundTag();
        validEntry.putLong("GameTime", 2L);
        validEntry.putInt("Points", 3);
        entries.add(validEntry);
        window.put("Entries", entries);
        windows.add(window);
        root.put("Windows", windows);

        ResearchPointAwardLedger restored = new ResearchPointAwardLedger();
        restored.deserializeNBT(root);

        assertEquals(1, restored.claimCount());
        assertTrue(restored.hasClaim(ClaimKey.once(id("test:valid"))));
        assertEquals(44L, restored.lastAwardGameTime(
                ScopeKey.targeted(id("test:cooldown"), id("test:target"))).orElseThrow());
        assertEquals(new ResearchPointAwardLedger.Usage(1, 3L), restored.windowUsage(
                ScopeKey.global(id("test:window")), 0L));
    }

    @Test
    void publicValueObjectsRejectInvalidStateAndSnapshotsAreReadOnly() {
        assertThrows(IllegalArgumentException.class,
                () -> ClaimKey.once(new ResourceLocation("a", "x".repeat(300))));
        assertThrows(NullPointerException.class,
                () -> ClaimKey.targeted(id("test:claim"), null));
        assertThrows(IllegalArgumentException.class, () -> new WindowEntry(-1L, 1));
        assertThrows(IllegalArgumentException.class, () -> new WindowEntry(1L, 0));

        ResearchPointAwardLedger ledger = new ResearchPointAwardLedger();
        ClaimKey claim = ClaimKey.once(id("test:claim"));
        assertTrue(ledger.apply(Mutation.claim(claim)));
        assertThrows(UnsupportedOperationException.class,
                () -> ledger.claims().add(ClaimKey.once(id("test:other"))));
    }

    private static CompoundTag claim(String id, String target) {
        CompoundTag value = new CompoundTag();
        value.putString("Id", id);
        if (target != null) {
            value.putString("Target", target);
        }
        return value;
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
