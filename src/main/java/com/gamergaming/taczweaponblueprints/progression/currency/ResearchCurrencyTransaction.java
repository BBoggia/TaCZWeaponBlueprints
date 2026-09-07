package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.ItemStack;

/** Server-side coordinator; caller adapters own inventory, input/progression mutations, and staged entities. */
public final class ResearchCurrencyTransaction {
    public enum Status { SUCCESS, NOT_READY, ALREADY_USED, STALE, FAILED, ROLLBACK_FAILED }
    public record Result(Status status, ResearchCurrencyQuote quote) {
        public boolean successful() { return status == Status.SUCCESS; }
        public long deliveredUnits() { return successful() ? quote.deliveredReward() : 0; }
    }

    public interface Context {
        /** Must include server-thread, player/menu identity, station and policy-publication validity. */
        boolean valid();
        default ResearchCurrencyPlanner.Destination destination() { return ResearchCurrencyPlanner.Destination.INVENTORY_ONLY; }
        ResearchCurrencyPolicy policy();
        ResearchCurrencyState read();
        /** Compare before any mutation. A false return/exception may have partially written; restore must handle it. */
        boolean replace(ResearchCurrencyState expected, ResearchCurrencyState replacement);
        /** Restore owned changes; use ResearchCurrencyState.restoration to avoid overwriting listener changes. */
        boolean restore(ResearchCurrencyState before, ResearchCurrencyState paid, ResearchCurrencyState after);
        /** Create an unpublished handle. It must not insert into the world or throw after spawning an item. */
        StagedDrop stageDrop(ItemStack stack);
    }

    public interface StagedDrop {
        boolean publish();
        boolean matches(ItemStack expected);
        void discard();
        boolean removed();
    }

    /** Input, knowledge, and claim mutations must participate here, never after successful delivery. */
    public interface Participant {
        boolean ready();
        boolean commit();
        boolean committed();
        boolean rollback();
    }

    public static final Participant NONE = new Participant() {
        public boolean ready() { return true; }
        public boolean commit() { return true; }
        public boolean committed() { return true; }
        public boolean rollback() { return true; }
    };

    public static Context guarded(Context delegate, java.util.function.BooleanSupplier current) {
        return new Context() {
            public boolean valid() { return delegate.valid() && current.getAsBoolean(); }
            public ResearchCurrencyPolicy policy() { return delegate.policy(); }
            public ResearchCurrencyPlanner.Destination destination() { return delegate.destination(); }
            public ResearchCurrencyState read() { return delegate.read(); }
            public boolean replace(ResearchCurrencyState before, ResearchCurrencyState after) {
                return valid() && delegate.replace(before, after);
            }
            public boolean restore(ResearchCurrencyState before, ResearchCurrencyState paid, ResearchCurrencyState after) {
                return delegate.restore(before, paid, after);
            }
            public StagedDrop stageDrop(ItemStack stack) { return delegate.stageDrop(stack); }
        };
    }

    private ResearchCurrencyTransaction() { }

    public static Result commit(ResearchCurrencyPlanner.Plan plan, Context context, Participant participant) {
        if (plan == null || context == null || participant == null) throw new IllegalArgumentException("missing currency transaction context");
        if (!plan.quote().ready()) return new Result(Status.NOT_READY, plan.quote());
        if (!plan.claim()) return new Result(Status.ALREADY_USED, plan.quote());
        List<StagedDrop> staged = new ArrayList<>();
        boolean currencyTouched = false, participantTouched = false;
        try {
            if (!current(plan, context) || !plan.before().matches(context.read()) || !participant.ready())
                return new Result(Status.STALE, plan.quote());
            // Participant preflight can invoke listeners; recheck their effects before the first write.
            if (!current(plan, context) || !plan.before().matches(context.read())) return new Result(Status.STALE, plan.quote());
            currencyTouched = true;
            if (!context.replace(plan.before(), plan.afterPayment()) || !plan.afterPayment().matches(context.read()))
                throw new IllegalStateException("currency payment was not retained");
            if (!current(plan, context)) throw new IllegalStateException("currency lease changed");
            participantTouched = true;
            if (!participant.commit() || !participant.committed()) throw new IllegalStateException("participant failed");
            if (!current(plan, context) || !context.replace(plan.afterPayment(), plan.after()) || !plan.after().matches(context.read()))
                throw new IllegalStateException("currency delivery was not retained");
            var expectedDrops = plan.drops();
            for (var stack : expectedDrops) {
                if (!current(plan, context)) throw new IllegalStateException("currency lease changed");
                var drop = context.stageDrop(stack.copy());
                if (drop == null) throw new IllegalStateException("missing staged currency drop");
                staged.add(drop); // Retain the handle even when publication throws or is cancelled.
                if (!drop.publish() || !drop.matches(stack)) throw new IllegalStateException("currency drop failed");
            }
            if (!current(plan, context) || !plan.after().matches(context.read()) || !participant.committed())
                throw new IllegalStateException("currency result changed");
            for (int i = 0; i < staged.size(); i++) if (!staged.get(i).matches(expectedDrops.get(i)))
                throw new IllegalStateException("currency drop changed during publication");
            return new Result(Status.SUCCESS, plan.quote());
        } catch (RuntimeException error) {
            boolean restored = true;
            for (var drop : staged) {
                try { drop.discard(); restored &= drop.removed(); }
                catch (RuntimeException failure) { restored = false; }
            }
            if (participantTouched) {
                try { restored &= participant.rollback(); }
                catch (RuntimeException failure) { restored = false; }
            }
            if (currencyTouched) {
                try { restored &= context.restore(plan.before(), plan.afterPayment(), plan.after());
                    restored &= plan.before().matches(context.read()); }
                catch (RuntimeException failure) { restored = false; }
            }
            return new Result(restored ? Status.FAILED : Status.ROLLBACK_FAILED, plan.quote());
        }
    }

    private static boolean current(ResearchCurrencyPlanner.Plan plan, Context context) {
        return context.valid() && plan.quote().policy().equals(context.policy());
    }
}
