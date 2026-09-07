package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.List;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import net.minecraft.world.item.ItemStack;

/** Payment adapter for existing inventory transactions. The caller owns commit and compensation. */
public final class ResearchCurrencyPayments {
    private ResearchCurrencyPayments() { }
    public static boolean physical(ResearchCurrencyPolicy policy) {
        return policy.kind() == ResearchCurrencyPolicy.Kind.PHYSICAL_ITEM;
    }
    public static int balance(ResearchCurrencyPolicy policy, List<ItemStack> inventory, IPlayerRecipeData data) {
        if (!physical(policy)) return data == null ? 0 : data.getResearchPoints();
        var unit = policy.physicalUnit();
        if (unit.isEmpty()) return 0;
        return (int) Math.min(Integer.MAX_VALUE, inventory.stream()
                .filter(s -> !s.isEmpty() && s.is(unit.orElseThrow().getItem())).mapToLong(ItemStack::getCount).sum());
    }
    public static List<ResearchIngredientPlanner.Requirement> materials(BlueprintResearchCost cost) {
        return cost.ingredients().stream().map(i -> new ResearchIngredientPlanner.Requirement(i.items(), i.tag(), i.count())).toList();
    }
    public static Optional<ResearchIngredientPlanner.Plan> plan(ResearchCurrencyPolicy policy,
            List<ItemStack> inventory, IPlayerRecipeData data, int debit, BlueprintResearchCost cost) {
        return plan(policy, inventory, data, debit, materials(cost));
    }
    public static Optional<ResearchIngredientPlanner.Plan> plan(ResearchCurrencyPolicy policy,
            List<ItemStack> inventory, IPlayerRecipeData data, int debit, List<ResearchIngredientPlanner.Requirement> materials) {
        if (debit < 0 || debit > balance(policy, inventory, data)) return Optional.empty();
        if (!physical(policy)) return ResearchIngredientPlanner.plan(inventory, materials);
        if (inventory.size() != ResearchCurrencyState.INVENTORY_SLOTS) return Optional.empty();
        var prepared = ResearchCurrencyPlanner.prepare(policy, new ResearchCurrencyState(inventory, ItemStack.EMPTY, data.getResearchPoints()),
                new ResearchCurrencyPlanner.Request(debit, materials, 0, ResearchCurrencyPlanner.Destination.INVENTORY_ONLY,
                        com.gamergaming.taczweaponblueprints.progression.ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL));
        if (!prepared.quote().ready()) return Optional.empty();
        var after = prepared.afterPayment().inventory();
        int[] decrements = new int[inventory.size()];
        for (int i = 0; i < decrements.length; i++) decrements[i] = inventory.get(i).getCount() - after.get(i).getCount();
        return Optional.of(new ResearchIngredientPlanner.Plan(decrements));
    }
    /** Physical debit is already reserved in the returned ingredient plan. */
    public static boolean debitBank(ResearchCurrencyPolicy policy, IPlayerRecipeData data, int amount) {
        return physical(policy) || amount == 0 || data.spendResearchPoints(amount);
    }
}
