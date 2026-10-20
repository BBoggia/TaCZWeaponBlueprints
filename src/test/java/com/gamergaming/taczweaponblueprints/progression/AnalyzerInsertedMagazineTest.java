package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.gamergaming.taczweaponblueprints.compat.magazines.InsertedMagazineGuard;
import com.gamergaming.taczweaponblueprints.testing.AnalysisTestItems;
import com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyPolicy;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

class AnalyzerInsertedMagazineTest {
    private static final String KEY = "TaCZMag_StoredMagazine";
    @BeforeAll static void bootstrap() { AnalysisTestItems.initialize(); }

    static ItemStack gun(String magazine) {
        var stack = new ItemStack(AnalysisTestItems.gun);
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, customTag -> customTag.putString("GunId", "test:weapon"));
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, customTag -> customTag.putBoolean("HasBulletInBarrel", true));
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, customTag -> customTag.putInt("GunCurrentAmmoCount", 12));
        if (magazine.equals("malformed")) net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, customTag -> customTag.putString(KEY, "broken"));
        else {
            var stored = new CompoundTag();
            if (!magazine.equals("empty_compound")) {
                stored.putString("id", magazine.equals("missing_item") ? "removed:magazine" : "taczmagazines:magazine");
                stored.putByte("Count", (byte) 1);
                var data = new CompoundTag();
                data.putInt("AmmoCount", magazine.equals("empty") ? 0 : 30);
                stored.put("tag", data);
            }
            net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, stack, customTag -> customTag.put(KEY, stored));
        }
        return stack;
    }

    @ParameterizedTest @ValueSource(strings={"empty", "loaded", "empty_compound", "missing_item", "malformed"})
    void markerBlocksPreviewAndAmmoPlanningWithoutReadingOrChangingStoredRounds(String magazine) {
        var tx = new BlueprintWeaponAnalysisServiceTest.Tx(); tx.input = gun(magazine); tx.loaded = true;
        var before = tx.input.copy(); var data = BlueprintWeaponAnalysisServiceTest.data();
        assertTrue(InsertedMagazineGuard.hasInsertedMagazine(tx.input));
        assertEquals(BlueprintReverseEngineeringService.Status.MAGAZINE_INSERTED,
                BlueprintWeaponAnalysisServiceTest.evaluate(tx, data).status());
        assertTrue(AnalyzerAmmoRecovery.plan(tx.input).isEmpty());
        assertTrue(ItemStack.matches(before, tx.input));
    }

    @Test void ordinaryGunsAndOtherItemsDoNotAcquireAnOptionalDependencyOrTags() {
        var stack = new ItemStack(AnalysisTestItems.gun);
        assertFalse(InsertedMagazineGuard.hasInsertedMagazine(stack)); assertFalse(stack.has(net.minecraft.core.component.DataComponents.CUSTOM_DATA));
        var paper = new ItemStack(Items.PAPER); net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, paper, customTag -> customTag.put(KEY, new CompoundTag()));
        assertFalse(InsertedMagazineGuard.hasInsertedMagazine(paper));
        assertFalse(InsertedMagazineGuard.hasInsertedMagazine(ItemStack.EMPTY));
        assertFalse(InsertedMagazineGuard.hasInsertedMagazine(null));
    }

    @Test void removingOnlyTheStoredMagazineMarkerRestoresTheNormalEvaluation() {
        var tx = new BlueprintWeaponAnalysisServiceTest.Tx(); tx.input = gun("empty");
        net.minecraft.world.item.component.CustomData.update(net.minecraft.core.component.DataComponents.CUSTOM_DATA, tx.input, tag -> tag.remove(KEY));
        assertTrue(BlueprintWeaponAnalysisServiceTest.evaluate(tx, BlueprintWeaponAnalysisServiceTest.data()).ready());
    }

    @ParameterizedTest @ValueSource(booleans={false,true})
    void finalExecutionRejectsInsertedMagazineBeforePaymentRandomnessOrOutput(boolean physicalCurrency) {
        var tx = new BlueprintWeaponAnalysisServiceTest.Tx(); tx.input = new ItemStack(AnalysisTestItems.gun);
        if (physicalCurrency) {
            tx.currency = ResearchCurrencyPolicy.physical(net.minecraft.resources.ResourceLocation.parse("minecraft:iron_ingot"), 1);
            tx.inventory.get(0).setCount(64);
            while (tx.inventory.size() < 36) tx.inventory.add(ItemStack.EMPTY);
        }
        var data = BlueprintWeaponAnalysisServiceTest.data();
        // Freeze eligible quotes, then insert a magazine before execution.
        var reverse = BlueprintWeaponAnalysisServiceTest.evaluate(tx, data);
        assertTrue(reverse.ready(), () -> reverse.status().name());
        var prepared = BlueprintWeaponAnalysisServiceTest.prepare(tx, data, BlueprintWeaponAnalysisServiceTest.mixed());
        tx.input = gun("loaded"); var before = tx.input.copy(); var progression = data.serializeNBT().copy();
        var inventory = tx.inventory.stream().map(ItemStack::copy).toList();
        assertEquals(BlueprintReverseEngineeringService.Status.MAGAZINE_INSERTED,
                BlueprintReverseEngineeringService.commit(reverse, data, tx).status());
        var result = BlueprintWeaponAnalysisService.commit(prepared, data, tx, bound -> { fail("must not draw an outcome"); return 0; });
        assertEquals(BlueprintReverseEngineeringService.Status.MAGAZINE_INSERTED, result.transaction().status());
        assertTrue(result.outcome().isEmpty());
        // A stale ready direct-recovery quote must also be unable to consume the gun.
        var recovery = new FoundWeaponRecoveryService.Evaluation(FoundWeaponRecoveryService.Status.READY,
                reverse, 3, data.getResearchPoints(), 100, data.getResearchPoints());
        assertEquals(FoundWeaponRecoveryService.Status.MAGAZINE_INSERTED,
                FoundWeaponRecoveryService.commit(recovery, data, tx).status());
        assertTrue(ItemStack.matches(before, tx.input)); assertTrue(tx.output.isEmpty());
        assertEquals(progression, data.serializeNBT());
        for (int i=0;i<inventory.size();i++) assertTrue(ItemStack.matches(inventory.get(i),tx.inventory.get(i)));
    }

    @Test void pointRecoveryPreviewExplainsTheMagazineInsteadOfGenericIneligibility() {
        var tx = new BlueprintWeaponAnalysisServiceTest.Tx(); tx.input = gun("empty");
        var data = BlueprintWeaponAnalysisServiceTest.data();
        var reverse = BlueprintWeaponAnalysisServiceTest.evaluate(tx, data);
        for (var mode : FoundWeaponRecoveryMode.values()) {
            var result = FoundWeaponRecoveryService.evaluate(tx.input, data, reverse, mode,
                    id -> { fail("must not request a recovery reward"); return null; });
            assertEquals(FoundWeaponRecoveryService.Status.MAGAZINE_INSERTED, result.status());
        }
    }

    @ParameterizedTest @ValueSource(ints={0,31})
    void staleAmmoPlanCannotRefundOrConsumeAnInsertedMagazineEvenWithFullInventory(int count) {
        var tx = new BlueprintWeaponAnalysisServiceTest.Tx(); tx.input = gun("loaded");
        tx.inventory = new ArrayList<>(Collections.nCopies(36, new ItemStack(Items.STONE,64)));
        var before = tx.input.copy(); var data = BlueprintWeaponAnalysisServiceTest.data();
        String result = AnalyzerAmmoRecovery.execute(new AnalyzerAmmoRecovery.Plan(null, count, new ItemStack(Items.ARROW)), tx, data,
                () -> { fail("must not consume input"); return "ok"; }, "ok"::equals, Enum::name,
                new AnalyzerAmmoRecovery.Delivery() {
                    public void give(List<ItemStack> stacks) { fail("must not refund ammunition"); }
                    public void rollback() { fail("no mutation needs rollback"); }
                });
        assertEquals("MAGAZINE_INSERTED", result); assertTrue(ItemStack.matches(before,tx.input));
        // The real server wrapper must reject before touching a player or planning ammo.
        assertEquals("MAGAZINE_INSERTED", AnalyzerAmmoRecovery.execute(null, tx, data,
                () -> { fail("must not consume input"); return "ok"; }, "ok"::equals, Enum::name));
    }
}
