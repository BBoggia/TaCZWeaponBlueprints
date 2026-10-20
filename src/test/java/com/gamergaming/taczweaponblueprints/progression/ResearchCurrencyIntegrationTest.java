package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.gamergaming.taczweaponblueprints.capabilities.*;
import com.gamergaming.taczweaponblueprints.progression.currency.*;
import com.gamergaming.taczweaponblueprints.resource.award.*;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;

class ResearchCurrencyIntegrationTest {
    static final ResourceLocation PROFILE = id("test:profile"), TARGET = id("test:weapon");
    static final ResearchCurrencyPolicy CURRENCY = ResearchCurrencyPolicy.physical(id("minecraft:iron_ingot"), 1);
    static final ResearchPointAwardConfigSnapshot CONFIG = new ResearchPointAwardConfigSnapshot(true, false, 0, PROFILE, CURRENCY);
    static final ResearchPointAwardContext CONTEXT = ResearchPointAwardContext.simple(ResearchPointAwardTrigger.Type.BLUEPRINT_DISCOVERED, PROFILE, TARGET);
    @BeforeAll static void bootstrap() { SharedConstants.tryDetectVersion(); Bootstrap.bootStrap(); }
    static ResourceLocation id(String id) { return ResourceLocation.parse(id); }
    static ResearchPointAwardResolver.Resolution awards(int... values) {
        var definitions = new LinkedHashMap<ResourceLocation, ResearchPointAwardDefinition>();
        for (int i = 0; i < values.length; i++) {
            var json = """
                {"format":1,"profiles":["test:profile"],"award_group":"test:group%s",
                 "trigger":{"type":"blueprint_discovered"},"reward":{"points":%s,"overflow":"clamp"},
                 "repeat":{"type":"once_per_target","claim_id":"test:claim%s"},"presentation":{"visibility":"public","name":"test.award"}}
                """.formatted(i, values[i], i);
            definitions.put(id("test:award" + i), ResearchPointAwardDefinition.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().orElseThrow());
        }
        return ResearchPointAwardResolver.resolve(ResearchPointAwardSnapshot.create(definitions), CONTEXT);
    }
    static ResearchPointAwardService.BatchResult award(Context context, int... values) {
        return ResearchPointAwardService.commitPhysicalAwards(context.data, awards(values).awards(), CONTEXT, CONFIG, 20, context, null);
    }
    @Test void finiteAwardsIgnoreBankCapPreserveDormantBalanceAndSurviveSerialization() {
        var context = new Context(); context.data.setResearchPoints(137);
        assertEquals(70, award(context, 70).awardedPoints());
        assertEquals(137, context.data.getResearchPoints()); assertEquals(70, context.units());
        assertEquals(1, context.data.getResearchPointAwardLedger().claimCount());
        var saved = context.data.serializeNBT().copy(); context.data = new PlayerRecipeData(); context.data.deserializeNBT(saved);
        assertEquals(0, award(context, 70).awardedPoints()); assertEquals(70, context.units());
        assertEquals(137, context.data.getResearchPoints());
    }
    @Test void wholeAwardBatchSharesPhysicalUnitAndEntityBounds() {
        var context = new Context(); var before = context.read();
        var result = award(context, 2100, 2100);
        assertFalse(result.changed()); assertTrue(before.matches(context.read())); assertTrue(context.data.getResearchPointAwardLedger().isEmpty());
        context.full(); before = context.read();
        result = award(context, 600, 600);
        assertFalse(result.changed()); assertTrue(before.matches(context.read())); assertTrue(context.drops.isEmpty());
        assertTrue(context.data.getResearchPointAwardLedger().isEmpty());
    }
    @ParameterizedTest @ValueSource(strings={"cancel", "throw", "alter", "payment", "delivery"})
    void failedDeliveryRestoresFiniteClaimsAndAllowsAnHonestRetry(String failure) {
        var context = new Context(); context.data.setResearchPoints(137); context.full(); context.fault = failure;
        var before = context.read();
        assertFalse(award(context, 129).changed()); assertTrue(context.data.getResearchPointAwardLedger().isEmpty());
        assertTrue(before.matches(context.read())); assertTrue(context.drops.stream().allMatch(d -> d.removed));
        context.fault = ""; context.writes = 0;
        assertEquals(129, award(context, 129).awardedPoints()); assertEquals(1, context.data.getResearchPointAwardLedger().claimCount());
        assertEquals(137, context.data.getResearchPoints());
    }
    @Test void manualRewardInputAndClaimRollbackTogetherWhenOutputCannotBeRetained() {
        var context = new Context(); context.fault = "delivery"; var input = new ItemStack[]{new ItemStack(Items.PAPER, 3)};
        var inputParticipant = new ResearchCurrencyInput(() -> input[0], s -> input[0] = s, 1);
        var external = new ResearchPointAwardService.ExternalTransaction() {
            public boolean valid() { return inputParticipant.ready(); }
            public void commit() { assertTrue(inputParticipant.commit()); }
            public boolean committed() { return inputParticipant.committed(); }
            public boolean rollback() { return inputParticipant.rollback(); }
            public ResearchCurrencyTransaction.Context currencyContext() { return context; }
            public ResearchCurrencyPlanner.Destination destination() { return ResearchCurrencyPlanner.Destination.MANUAL_OUTPUT; }
        };
        var result = ResearchPointAwardService.commitPhysicalAwards(context.data, awards(3).awards(), CONTEXT, CONFIG, 20, context, external);
        assertFalse(result.changed()); assertEquals(3, input[0].getCount()); assertTrue(context.output.isEmpty());
        assertTrue(context.data.getResearchPointAwardLedger().isEmpty());
    }
    @Test void inventoryTurnInFreesItsOwnSlotWithoutParticipantInventoryWrites() {
        var context = new Context(); context.full(); context.inventory.set(0, new ItemStack(Items.PAPER));
        var external = new ResearchPointAwardService.ExternalTransaction() {
            public int inventorySlot() { return 0; }
            public boolean valid() { return context.inventory.get(0).is(Items.PAPER); }
            public void commit() { fail("input is owned by the combined inventory plan"); }
        };
        var result = ResearchPointAwardService.commitPhysicalAwards(context.data, awards(64).awards(), CONTEXT, CONFIG, 20, context, external);
        assertEquals(64, result.awardedPoints()); assertTrue(context.inventory.get(0).is(Items.IRON_INGOT));
        assertEquals(64, context.inventory.get(0).getCount()); assertTrue(context.drops.isEmpty());
    }
    @Test void inputAndGrossCurrencyCostAreReservedBeforeSalvageReward() {
        var context = new Context(); context.inventory.set(0, new ItemStack(Items.IRON_INGOT, 3));
        var request = new ResearchCurrencyPlanner.Request(3, List.of(), 5, ResearchCurrencyPlanner.Destination.INVENTORY_ONLY,
                ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL);
        var plan = ResearchCurrencyPlanner.prepareWithInput(CURRENCY, context.read(), request, 0, 1);
        assertFalse(plan.quote().ready()); assertEquals(3, context.units());
    }
    @Test void outputOccupiedDoesNotConsumeInputOrArchive() {
        var context = new Context(); context.output = new ItemStack(Items.STONE);
        var input = new ItemStack[]{new ItemStack(Items.PAPER, 4)};
        var participant = ResearchCurrencyInput.combine(new ResearchCurrencyInput(() -> input[0], s -> input[0] = s, 4),
                ResearchCurrencyManual.progression(context.data, () -> context.data.discoverBlueprint(TARGET.toString())));
        var plan = ResearchCurrencyPlanner.prepare(CURRENCY, context.read(), new ResearchCurrencyPlanner.Request(0, List.of(), 70,
                ResearchCurrencyPlanner.Destination.MANUAL_OUTPUT, ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL));
        assertFalse(ResearchCurrencyTransaction.commit(plan, context, participant).successful());
        assertEquals(4, input[0].getCount()); assertFalse(context.data.hasDiscoveredBlueprint(TARGET.toString())); assertEquals(0, context.units());
    }
    @Test void dataEligibilityPreviewAndPhysicalCommitAgreeAtZeroBankCap() {
        var context = new Context(); context.data.setResearchPoints(1_000_000_000);
        var preview = ResearchPointAwardService.evaluateResolved(context.data, awards(6), CONTEXT, CONFIG, 20);
        assertEquals(6, preview.awardablePoints()); assertEquals(6, award(context, 6).awardedPoints());
        assertEquals(1_000_000_000, context.data.getResearchPoints());
        assertFalse(ResearchPointAwardService.awardResolved(context.data, awards(6), CONTEXT, CONFIG, 20).changed());
    }
    @Test void savedFragmentRewardCanKeepAnUnacceptedPhysicalInput() {
        var context = new Context();
        var input = new ItemStack[]{new ItemStack(Items.PAPER, 1)};
        var participant = new ResearchCurrencyInput(() -> input[0], value -> input[0] = value, 0);
        var plan = ResearchCurrencyPlanner.prepare(CURRENCY, context.read(), new ResearchCurrencyPlanner.Request(0, List.of(), 1,
                ResearchCurrencyPlanner.Destination.MANUAL_OUTPUT, ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL));
        assertTrue(ResearchCurrencyTransaction.commit(plan, context, participant).successful());
        assertEquals(1, input[0].getCount()); assertTrue(input[0].is(Items.PAPER)); assertEquals(1, context.output.getCount());
    }
    @Test void bankedTurnInFailureRestoresItsInputBalanceAndClaim() {
        var data = new PlayerRecipeData(); data.setResearchPoints(7); var input = new int[]{1};
        var external = new ResearchPointAwardService.ExternalTransaction() {
            public boolean valid() { return input[0] == 1; }
            public void commit() { input[0] = 0; throw new IllegalStateException("injected input failure"); }
            public boolean rollback() { input[0] = 1; return true; }
        };
        var result = ResearchPointAwardService.awardResolved(data, awards(3), CONTEXT,
                new ResearchPointAwardConfigSnapshot(true, false, 100, PROFILE), 20, external);
        assertFalse(result.changed()); assertEquals(7, data.getResearchPoints()); assertEquals(1, input[0]);
        assertTrue(data.getResearchPointAwardLedger().isEmpty());
    }

    @Test void throwingSecondaryRollbackStillRestoresPrimaryInputAndCurrency() {
        var context = new Context(); var input = new ItemStack[]{new ItemStack(Items.PAPER, 3)};
        var first = new ResearchCurrencyInput(() -> input[0], stack -> input[0] = stack, 1);
        var second = new ResearchCurrencyTransaction.Participant() {
            public boolean ready() { return true; }
            public boolean commit() { return false; }
            public boolean committed() { return false; }
            public boolean rollback() { throw new IllegalStateException("secondary rollback failed"); }
        };
        var before = context.read();
        var plan = ResearchCurrencyPlanner.prepare(CURRENCY, before, new ResearchCurrencyPlanner.Request(0, List.of(), 3,
                ResearchCurrencyPlanner.Destination.MANUAL_OUTPUT, ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL));
        var result = ResearchCurrencyTransaction.commit(plan, context, ResearchCurrencyInput.combine(first, second));
        assertEquals(ResearchCurrencyTransaction.Status.ROLLBACK_FAILED, result.status());
        assertEquals(3, input[0].getCount()); assertTrue(before.matches(context.read()));
    }
    @Test void throwingExternalRollbackStillRestoresFiniteAwardLedger() {
        var context = new Context(); context.fault = "delivery";
        var external = new ResearchPointAwardService.ExternalTransaction() {
            public boolean valid() { return true; }
            public void commit() { }
            public boolean committed() { return true; }
            public boolean rollback() { throw new IllegalStateException("external rollback failed"); }
        };
        var before = context.read();
        var result = ResearchPointAwardService.commitPhysicalAwards(context.data, awards(3).awards(), CONTEXT, CONFIG, 20, context, external);
        assertFalse(result.changed()); assertTrue(before.matches(context.read()));
        assertTrue(context.data.getResearchPointAwardLedger().isEmpty());
        assertEquals(ResearchPointAwardService.Status.ROLLBACK_FAILED, result.awards().get(0).status());
    }

    static final class Context implements ResearchCurrencyTransaction.Context {
        PlayerRecipeData data = new PlayerRecipeData(); List<ItemStack> inventory = new ArrayList<>(Collections.nCopies(36, ItemStack.EMPTY));
        ItemStack output = ItemStack.EMPTY; String fault = ""; int writes; List<Drop> drops = new ArrayList<>();
        public boolean valid() { return true; }
        public ResearchCurrencyPolicy policy() { return CURRENCY; }
        public ResearchCurrencyState read() { return new ResearchCurrencyState(inventory, output, data.getResearchPoints()); }
        public boolean replace(ResearchCurrencyState before, ResearchCurrencyState after) {
            if (!before.matches(read())) return false;
            inventory = new ArrayList<>(after.inventory()); output = after.output(); data.setResearchPoints(after.bankedPoints()); writes++;
            return !(fault.equals("payment") && writes == 1 || fault.equals("delivery") && writes == 2);
        }
        public boolean restore(ResearchCurrencyState before, ResearchCurrencyState paid, ResearchCurrencyState after) {
            var restored = ResearchCurrencyState.restoration(before, paid, after, read());
            if (restored.isEmpty()) return false;
            var value = restored.orElseThrow(); inventory = new ArrayList<>(value.inventory()); output = value.output(); data.setResearchPoints(value.bankedPoints()); return true;
        }
        public ResearchCurrencyTransaction.StagedDrop stageDrop(ItemStack stack) { var drop = new Drop(stack, this); drops.add(drop); return drop; }
        void full() { for (int i = 0; i < 36; i++) inventory.set(i, new ItemStack(Items.STONE, 64)); }
        int units() { return inventory.stream().filter(s -> s.is(Items.IRON_INGOT)).mapToInt(ItemStack::getCount).sum(); }
    }
    static final class Drop implements ResearchCurrencyTransaction.StagedDrop {
        ItemStack stack; Context context; boolean removed, published;
        Drop(ItemStack stack, Context context) { this.stack = stack.copy(); this.context = context; }
        public boolean publish() { published = true; if (context.fault.equals("throw")) throw new IllegalStateException(); if (context.fault.equals("alter")) stack.shrink(1); return !context.fault.equals("cancel"); }
        public boolean matches(ItemStack expected) { return published && !removed && ItemStack.matches(expected, stack); }
        public void discard() { removed = true; }
        public boolean removed() { return removed; }
    }
}
