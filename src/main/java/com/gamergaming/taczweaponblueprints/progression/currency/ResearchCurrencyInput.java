package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.function.Consumer;
import java.util.function.Supplier;
import net.minecraft.world.item.ItemStack;

/** Compare-and-consume input participant for manual rewards; never owns the currency inventory. */
public final class ResearchCurrencyInput implements ResearchCurrencyTransaction.Participant {
    private final Supplier<ItemStack> read;
    private final Consumer<ItemStack> write;
    private final ItemStack before, after;
    public ResearchCurrencyInput(Supplier<ItemStack> read, Consumer<ItemStack> write, int count) {
        this.read = read; this.write = write; this.before = read.get().copy(); this.after = before.copy();
        if (count < 0 || before.getCount() < count) throw new IllegalArgumentException("invalid currency input consumption");
        after.shrink(count);
    }
    @Override public boolean ready() { return ItemStack.matches(before, read.get()); }
    @Override public boolean commit() { if (!ready()) return false; write.accept(after.copy()); return committed(); }
    @Override public boolean committed() { return ItemStack.matches(after, read.get()); }
    @Override public boolean rollback() {
        if (ready()) return true;
        if (!committed()) return false;
        write.accept(before.copy()); return ready();
    }
    public static ResearchCurrencyTransaction.Participant combine(ResearchCurrencyTransaction.Participant first,
            ResearchCurrencyTransaction.Participant second) {
        return new ResearchCurrencyTransaction.Participant() {
            public boolean ready() { return first.ready() && second.ready(); }
            public boolean commit() { return first.commit() && second.commit(); }
            public boolean committed() { return first.committed() && second.committed(); }
            public boolean rollback() {
                boolean restored = true;
                try { restored = second.rollback(); } catch (RuntimeException failure) { restored = false; }
                try { restored = first.rollback() && restored; } catch (RuntimeException failure) { restored = false; }
                return restored;
            }
        };
    }
}
