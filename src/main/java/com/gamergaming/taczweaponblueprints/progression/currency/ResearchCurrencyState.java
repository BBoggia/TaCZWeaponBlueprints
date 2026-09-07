package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.List;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import net.minecraft.world.item.ItemStack;

/** Detached currency-owned state. Input stacks, knowledge, and ledgers remain caller-owned. */
public final class ResearchCurrencyState {
    public static final int INVENTORY_SLOTS = 36;
    private final List<ItemStack> inventory;
    private final ItemStack output;
    private final int bankedPoints;

    public ResearchCurrencyState(List<ItemStack> inventory, ItemStack output, int bankedPoints) {
        if (inventory == null || inventory.size() != INVENTORY_SLOTS || output == null
                || bankedPoints < 0 || bankedPoints > PlayerProgressionLimits.MAX_RESEARCH_POINTS) {
            throw new IllegalArgumentException("currency state requires exactly the main inventory, output, and valid RP");
        }
        inventory.forEach(ResearchCurrencyState::validateStack);
        validateStack(output);
        this.inventory = copies(inventory); this.output = output.copy(); this.bankedPoints = bankedPoints;
    }

    public List<ItemStack> inventory() { return copies(inventory); }
    public ItemStack output() { return output.copy(); }
    public int bankedPoints() { return bankedPoints; }

    public boolean matches(ResearchCurrencyState other) {
        if (other == null || bankedPoints != other.bankedPoints || !ItemStack.matches(output, other.output)) return false;
        for (int i = 0; i < INVENTORY_SLOTS; i++) if (!ItemStack.matches(inventory.get(i), other.inventory.get(i))) return false;
        return true;
    }

    /**
     * Plan a compensation without overwriting an unrelated listener's changes. Values changed by this
     * attempt must still match one of its three known states. Untouched slots keep their current value.
     */
    public static Optional<ResearchCurrencyState> restoration(ResearchCurrencyState before,
            ResearchCurrencyState paid, ResearchCurrencyState after, ResearchCurrencyState current) {
        if (before == null || paid == null || after == null || current == null) return Optional.empty();
        var result = new java.util.ArrayList<>(current.inventory());
        for (int i = 0; i < INVENTORY_SLOTS; i++) {
            var b = before.inventory.get(i); var p = paid.inventory.get(i); var a = after.inventory.get(i); var c = current.inventory.get(i);
            if (ItemStack.matches(b,p) && ItemStack.matches(b,a)) continue;
            if (!ItemStack.matches(c,b) && !ItemStack.matches(c,p) && !ItemStack.matches(c,a)) return Optional.empty();
            result.set(i,b.copy());
        }
        var output = current.output();
        if (!ItemStack.matches(before.output,paid.output) || !ItemStack.matches(before.output,after.output)) {
            if (!ItemStack.matches(output,before.output) && !ItemStack.matches(output,paid.output) && !ItemStack.matches(output,after.output))
                return Optional.empty();
            output = before.output();
        }
        int points = current.bankedPoints;
        if (before.bankedPoints != paid.bankedPoints || before.bankedPoints != after.bankedPoints) {
            if (points != before.bankedPoints && points != paid.bankedPoints && points != after.bankedPoints) return Optional.empty();
            points = before.bankedPoints;
        }
        return Optional.of(new ResearchCurrencyState(result,output,points));
    }

    static List<ItemStack> copies(List<ItemStack> stacks) { return stacks.stream().map(ItemStack::copy).toList(); }

    private static void validateStack(ItemStack stack) {
        if (stack == null || !stack.isEmpty() && (stack.getCount() < 1 || stack.getCount() > stack.getMaxStackSize()
                || stack.getMaxStackSize() < 1 || stack.getMaxStackSize() > 64)) {
            throw new IllegalArgumentException("invalid currency inventory stack");
        }
    }
}
