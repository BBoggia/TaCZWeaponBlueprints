package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.List;
import java.util.function.BooleanSupplier;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;
import com.gamergaming.taczweaponblueprints.progression.ResearchIngredientPlanner;
import com.gamergaming.taczweaponblueprints.progression.ResearchPointTransactionService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** Manual output delivery and separate input/progression participation. */
public final class ResearchCurrencyManual {
    private ResearchCurrencyManual() { }
    public static ResearchCurrencyTransaction.Context context(ServerPlayer player, IPlayerRecipeData data) {
        var config = com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.progressionSnapshot();
        long research = com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager.INSTANCE.revision();
        long catalog = com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.SERVER.catalogRevision();
        var delegate = player.containerMenu instanceof BlueprintRecyclerMenu menu ? menu.currencyContext(player)
                : PlayerResearchCurrencyContext.inventory(player, data);
        return ResearchCurrencyTransaction.guarded(delegate, () -> config.equals(com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.progressionSnapshot())
                && research == com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager.INSTANCE.revision()
                && catalog == com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.SERVER.catalogRevision());
    }
    public static ResearchCurrencyPlanner.Plan prepare(ResearchCurrencyTransaction.Context context, int debit,
            List<ResearchIngredientPlanner.Requirement> materials, int reward) {
        return ResearchCurrencyPlanner.prepare(context.policy(), context.read(), new ResearchCurrencyPlanner.Request(debit, materials, reward,
                context.destination(),
                ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL));
    }
    public static ResearchCurrencyPlanner.Plan prepareInput(ResearchCurrencyTransaction.Context context, ServerPlayer player,
            ItemStack input, int count, int debit, List<ResearchIngredientPlanner.Requirement> materials, int reward) {
        int slot = inputSlot(player, input);
        var request = new ResearchCurrencyPlanner.Request(debit, materials, reward, context.destination(), ResearchPointTransactionService.OverflowPolicy.REQUIRE_FULL);
        return slot >= 0 && count > 0 ? ResearchCurrencyPlanner.prepareWithInput(context.policy(), context.read(), request, slot, count)
                : ResearchCurrencyPlanner.prepare(context.policy(), context.read(), request);
    }
    private static int inputSlot(ServerPlayer player, ItemStack input) {
        for (int i = 0; i < player.getInventory().items.size(); i++) if (player.getInventory().items.get(i) == input) return i;
        return -1;
    }

    /** Detached eligibility projection; real saved banked RP is never changed by this view. */
    public static IPlayerRecipeData eligibility(IPlayerRecipeData data) {
        var copy = new PlayerRecipeData(); copy.deserializeNBT(data.serializeNBT().copy()); copy.setResearchPoints(0); return copy;
    }
    public static ResearchCurrencyTransaction.Participant input(ServerPlayer player, ItemStack input, int count) {
        if (player.containerMenu instanceof BlueprintRecyclerMenu menu && menu.inputStack() == input)
            return new ResearchCurrencyInput(menu::inputStack, menu::setCurrencyInput, count);
        // Non-menu callers must supply a separate stack, not a currency-owned inventory slot.
        if (inputSlot(player, input) >= 0) return ResearchCurrencyTransaction.NONE;
        return new ResearchCurrencyInput(() -> input, stack -> input.setCount(stack.getCount()), count);
    }
    /** Caller mutation must be local capability work without Forge callbacks or currency writes. */
    public static ResearchCurrencyTransaction.Participant progression(IPlayerRecipeData data, BooleanSupplier mutation) {
        CompoundTag before = data.serializeNBT().copy();
        return new ResearchCurrencyTransaction.Participant() {
            private CompoundTag after;
            public boolean ready() { return before.equals(data.serializeNBT()); }
            public boolean commit() {
                if (!ready()) return false;
                try { return mutation.getAsBoolean(); }
                finally { after = data.serializeNBT().copy(); }
            }
            public boolean committed() { return after != null && after.equals(data.serializeNBT()); }
            public boolean rollback() {
                if (ready()) return true;
                if (!committed()) return false;
                data.deserializeNBT(before.copy()); return ready();
            }
        };
    }
}
