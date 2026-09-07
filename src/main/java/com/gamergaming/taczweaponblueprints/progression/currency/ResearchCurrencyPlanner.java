package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner;
import com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner.Requirement;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointTransactionService.OverflowPolicy;
import net.minecraft.world.item.ItemStack;
import static com.gamergaming.taczweaponblueprints.progression.currency.ResearchCurrencyQuote.Status.*;

/** Pure gross-payment and complete-delivery planning. No RNG, player mutation, or entity spawning. */
public final class ResearchCurrencyPlanner {
    public static final int MAX_MATERIAL_REQUIREMENTS = 6 * 1024;
    public static final int MAX_EVENT_STACKS = 64;
    public static final int MAX_EVENT_DROPS = 16;
    public enum Destination { MANUAL_OUTPUT, INVENTORY_ONLY, EVENT }

    /** Amounts have already had cost-channel masks, discounts, and per-node Creative bypass applied. */
    public record Request(long debit, List<Requirement> materials, long reward,
            Destination destination, OverflowPolicy bankedOverflow) {
        public Request {
            if (debit < 0 || reward < 0 || materials == null || materials.size() > MAX_MATERIAL_REQUIREMENTS
                    || destination == null || bankedOverflow == null) throw new IllegalArgumentException("invalid currency request");
            materials = List.copyOf(materials);
        }
    }

    private ResearchCurrencyPlanner() { }

    public static Plan prepare(ResearchCurrencyPolicy policy, ResearchCurrencyState before, Request request) {
        Objects.requireNonNull(policy); Objects.requireNonNull(before); Objects.requireNonNull(request);
        var inventory = before.inventory();
        boolean physical = policy.kind() == ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM;
        var unit = physical ? policy.physicalUnit().orElse(ItemStack.EMPTY) : ItemStack.EMPTY;
        long available = physical ? inventory.stream().filter(s -> !s.isEmpty() && s.is(unit.getItem())).mapToLong(ItemStack::getCount).sum()
                : before.bankedPoints();
        if (physical && unit.isEmpty()) return failed(INVALID_CURRENCY, policy, before, request, 0);
        // Reward never finances the gross debit, even if the final net balance would be positive.
        if (request.debit() > available) return failed(INSUFFICIENT_CURRENCY, policy, before, request, available);
        var requirements = new ArrayList<>(request.materials());
        if (physical && request.debit() > 0) requirements.add(new Requirement(List.of(policy.item().orElseThrow()),
                java.util.Optional.empty(), (int) request.debit()));
        long demand = requirements.stream().mapToLong(Requirement::count).sum();
        long supplies = inventory.stream().mapToLong(ItemStack::getCount).sum();
        if (demand > supplies) return failed(INSUFFICIENT_MATERIALS, policy, before, request, available);
        var allocation = ResearchIngredientPlanner.plan(inventory, requirements);
        if (allocation.isEmpty()) return failed(INSUFFICIENT_MATERIALS, policy, before, request, available);
        var paid = allocation.orElseThrow().applyToCopies(inventory);
        int bank = before.bankedPoints() - (physical ? 0 : (int) request.debit());
        var afterPayment = new ResearchCurrencyState(paid, before.output(), bank);
        long delivered = request.reward();
        if (!physical) {
            long room = Math.max(0L, (long) policy.pointCap() - bank);
            if (request.reward() > 0 && request.bankedOverflow() == OverflowPolicy.REQUIRE_FULL && request.reward() > room)
                return failed(POINT_CAP_REACHED, policy, before, request, available);
            delivered = Math.min(request.reward(), room);
            return ready(policy, before, afterPayment, new ResearchCurrencyState(paid, before.output(), bank + (int) delivered),
                    request, available, delivered, 0, 0, 0, List.of());
        }
        if (request.reward() == 0) return ready(policy, before, afterPayment, afterPayment, request, available, 0, 0, 0, 0, List.of());
        int max = unit.getMaxStackSize();
        // Limits apply to the aggregate event request, including fills into existing stacks.
        if (request.destination() == Destination.EVENT && request.reward() > (long) max * MAX_EVENT_STACKS)
            return failed(DELIVERY_LIMIT, policy, before, request, available);
        var output = before.output();
        long left = request.reward();
        int outputUnits = 0;
        if (request.destination() == Destination.MANUAL_OUTPUT) {
            if (!output.isEmpty() && !ItemStack.isSameItemSameTags(output, unit))
                return failed(OUTPUT_BLOCKED, policy, before, request, available);
            outputUnits = (int) Math.min(left, output.isEmpty() ? max : max - output.getCount());
            if (output.isEmpty()) { output = unit.copy(); output.setCount(outputUnits); }
            else output.grow(outputUnits);
            left -= outputUnits;
        }
        var filled = new ArrayList<>(ResearchCurrencyState.copies(paid));
        int inventoryUnits = 0;
        // Existing compatible stacks first; renamed/NBT-bearing currency retains its data and is not overwritten.
        for (int pass = 0; pass < 2; pass++) for (int slot = 0; slot < filled.size() && left > 0; slot++) {
            var stack = filled.get(slot);
            if (pass == 0 ? stack.isEmpty() || !ItemStack.isSameItemSameTags(stack, unit) : !stack.isEmpty()) continue;
            int count = (int) Math.min(left, stack.isEmpty() ? max : max - stack.getCount());
            if (count == 0) continue;
            if (stack.isEmpty()) { stack = unit.copy(); stack.setCount(count); filled.set(slot, stack); }
            else stack.grow(count);
            inventoryUnits += count; left -= count;
        }
        var drops = new ArrayList<ItemStack>();
        if (left > 0) {
            if (request.destination() != Destination.EVENT) return failed(DELIVERY_FULL, policy, before, request, available);
            if (left > (long) max * MAX_EVENT_DROPS) return failed(DELIVERY_LIMIT, policy, before, request, available);
            while (left > 0) { var stack = unit.copy(); int count = (int) Math.min(left, max); stack.setCount(count); drops.add(stack); left -= count; }
        }
        int dropped = drops.stream().mapToInt(ItemStack::getCount).sum();
        return ready(policy, before, afterPayment, new ResearchCurrencyState(filled, output, bank), request, available,
                delivered, outputUnits, inventoryUnits, dropped, drops);
    }

    /** Explicit one-way withdrawal. Bank debit and physical delivery share one owned state. */
    public static Plan prepareWithdrawal(ResearchCurrencyPolicy policy, ResearchCurrencyState before, int amount) {
        if (amount < 1 || amount > 36 * 64) throw new IllegalArgumentException("withdrawal must be 1..2304 units");
        var request = new Request(0, List.of(), amount, Destination.INVENTORY_ONLY, OverflowPolicy.REQUIRE_FULL);
        if (policy.kind() != ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM)
            return failed(INVALID_CURRENCY, policy, before, request, before.bankedPoints());
        if (before.bankedPoints() < amount)
            return failed(INSUFFICIENT_CURRENCY, policy, before, request, before.bankedPoints());
        var paid = new ResearchCurrencyState(before.inventory(), before.output(), before.bankedPoints() - amount);
        var delivery = prepare(policy, paid, request);
        if (!delivery.quote().ready()) return failed(delivery.quote().status(), policy, before, request, before.bankedPoints());
        return new Plan(delivery.quote(), before, paid, delivery.after(), List.of());
    }

    /** Reserves a known inventory turn-in slot with delivery, freeing its capacity only after consumption. */
    public static Plan prepareWithInput(ResearchCurrencyPolicy policy, ResearchCurrencyState before, Request request, int slot, int count) {
        var inventory = before.inventory();
        if (slot < 0 || slot >= inventory.size() || count < 1 || inventory.get(slot).getCount() < count)
            throw new IllegalArgumentException("invalid currency input slot");
        inventory.get(slot).shrink(count);
        var plan = prepare(policy, new ResearchCurrencyState(inventory, before.output(), before.bankedPoints()), request);
        return new Plan(plan.quote, before, plan.afterPayment, plan.after, plan.drops);
    }

    private static Plan failed(ResearchCurrencyQuote.Status status, ResearchCurrencyPolicy policy, ResearchCurrencyState before,
            Request request, long available) {
        return new Plan(new ResearchCurrencyQuote(status, policy, request.debit(), request.reward(), available, 0, 0, 0, 0),
                before, before, before, List.of());
    }
    private static Plan ready(ResearchCurrencyPolicy policy, ResearchCurrencyState before, ResearchCurrencyState paid,
            ResearchCurrencyState after, Request request, long available, long delivered, int output, int inventory, int dropped, List<ItemStack> drops) {
        return new Plan(new ResearchCurrencyQuote(READY, policy, request.debit(), request.reward(), available, delivered, output, inventory, dropped),
                before, paid, after, drops);
    }

    /** State is deeply copied; the one-use marker is private to the transaction coordinator. */
    public static final class Plan {
        private final ResearchCurrencyQuote quote;
        private final ResearchCurrencyState before, afterPayment, after;
        private final List<ItemStack> drops;
        private final AtomicBoolean used = new AtomicBoolean();
        private Plan(ResearchCurrencyQuote quote, ResearchCurrencyState before, ResearchCurrencyState paid,
                ResearchCurrencyState after, List<ItemStack> drops) {
            this.quote = quote; this.before = before; this.afterPayment = paid; this.after = after;
            this.drops = ResearchCurrencyState.copies(drops);
        }
        public ResearchCurrencyQuote quote() { return quote; }
        public ResearchCurrencyState before() { return before; }
        public ResearchCurrencyState afterPayment() { return afterPayment; }
        public ResearchCurrencyState after() { return after; }
        public List<ItemStack> drops() { return ResearchCurrencyState.copies(drops); }
        boolean claim() { return used.compareAndSet(false, true); }
    }
}
