package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Live adapter restricted to the player's 36 main slots and an explicitly supplied output. */
public final class PlayerResearchCurrencyContext implements ResearchCurrencyTransaction.Context {
    private final ServerPlayer player;
    private final IPlayerRecipeData data;
    private final Supplier<ItemStack> output;
    private final Consumer<ItemStack> setOutput;
    private final BooleanSupplier lease;
    private ResearchCurrencyPlanner.Destination destination = ResearchCurrencyPlanner.Destination.MANUAL_OUTPUT;

    public PlayerResearchCurrencyContext(ServerPlayer player, IPlayerRecipeData data, Supplier<ItemStack> output,
            Consumer<ItemStack> setOutput, BooleanSupplier lease) {
        this.player = java.util.Objects.requireNonNull(player); this.data = java.util.Objects.requireNonNull(data);
        this.output = output; this.setOutput = setOutput; this.lease = lease;
    }
    public static PlayerResearchCurrencyContext inventory(ServerPlayer player, IPlayerRecipeData data) {
        var context = new PlayerResearchCurrencyContext(player, data, () -> ItemStack.EMPTY,
                stack -> { if (!stack.isEmpty()) throw new IllegalArgumentException("no output slot"); }, () -> true);
        context.destination = ResearchCurrencyPlanner.Destination.INVENTORY_ONLY;
        return context;
    }
    @Override public ResearchCurrencyPlanner.Destination destination() { return destination; }
    @Override public boolean valid() {
        return player.server != null && player.server.isSameThread() && !player.level().isClientSide
                && player.isAlive() && !player.hasDisconnected()
                && player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElse(null) == data && lease.getAsBoolean();
    }
    @Override public ResearchCurrencyPolicy policy() { return ModConfigs.BLUEPRINT.currencyPolicy(); }
    @Override public ResearchCurrencyState read() {
        return new ResearchCurrencyState(player.getInventory().items, output.get(), data.getResearchPoints());
    }
    @Override public boolean replace(ResearchCurrencyState expected, ResearchCurrencyState replacement) {
        if (!valid() || !expected.matches(read())) return false;
        write(expected, replacement);
        return replacement.matches(read());
    }
    private void write(ResearchCurrencyState expected, ResearchCurrencyState replacement) {
        var before = expected.inventory(); var after = replacement.inventory();
        for (int i = 0; i < after.size(); i++) if (!ItemStack.matches(before.get(i), after.get(i)))
            player.getInventory().items.set(i, after.get(i).copy());
        if (expected.bankedPoints() != replacement.bankedPoints() && !data.setResearchPoints(replacement.bankedPoints()))
            throw new IllegalStateException("banked currency write rejected");
        if (!ItemStack.matches(expected.output(), replacement.output())) setOutput.accept(replacement.output());
        player.getInventory().setChanged();
    }
    @Override public boolean restore(ResearchCurrencyState before, ResearchCurrencyState paid, ResearchCurrencyState after) {
        var current = read();
        var restored = ResearchCurrencyState.restoration(before, paid, after, current);
        if (restored.isEmpty()) return false;
        write(current, restored.orElseThrow());
        return restored.orElseThrow().matches(read());
    }
    @Override public ResearchCurrencyTransaction.StagedDrop stageDrop(ItemStack stack) {
        var level = player.serverLevel();
        var entity = new ItemEntity(level, player.getX(), player.getY() + 0.5, player.getZ(), stack.copy());
        entity.setDefaultPickUpDelay();
        entity.setThrower(player.getUUID());
        return new ResearchCurrencyTransaction.StagedDrop() {
            @Override public boolean publish() { return level.addFreshEntity(entity); }
            @Override public boolean matches(ItemStack expected) {
                return !entity.isRemoved() && level.getEntity(entity.getUUID()) == entity
                        && ItemStack.matches(expected, entity.getItem());
            }
            @Override public void discard() { entity.discard(); }
            @Override public boolean removed() { return entity.isRemoved() && level.getEntity(entity.getUUID()) != entity; }
        };
    }
}
