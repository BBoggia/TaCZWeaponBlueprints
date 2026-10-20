package com.gamergaming.taczweaponblueprints.progression;

import com.gamergaming.taczweaponblueprints.compat.magazines.InsertedMagazineGuard;
import java.util.*;
import java.util.function.*;
import com.gamergaming.taczweaponblueprints.capabilities.IPlayerRecipeData;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IGun;
import com.tacz.guns.api.item.IAmmo;
import com.tacz.guns.api.item.builder.AmmoItemBuilder;
import com.tacz.guns.resource.pojo.data.gun.Bolt;
import com.tacz.guns.resource.pojo.data.gun.FeedType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/** Returns loaded physical rounds only after weapon consumption succeeds, before awards/sync. */
public final class AnalyzerAmmoRecovery {
    // Bound hostile NBT and the number of overflow entities created by one action.
    static final int MAX_ROUNDS = 65536, MAX_STACKS = 1024;
    public record Plan(ResourceLocation ammoId, int count, ItemStack ammunition) {
        public List<ItemStack> stacks() { return count == 0 ? List.of() : split(ammunition, count); }
    }
    enum Failure { INVALID_AMMO, TRANSACTION_FAILED, ROLLBACK_FAILED, MAGAZINE_INSERTED }

    public static Optional<Plan> plan(ItemStack input) {
        if (InsertedMagazineGuard.hasInsertedMagazine(input)) return Optional.empty();
        try {
            var copy = input.copy(); var gun = IGun.getIGunOrNull(copy);
            if (gun == null) return Optional.of(new Plan(null, 0, ItemStack.EMPTY));
            if (copy.getCount() != 1) return Optional.empty();
            int magazine = gun.getCurrentAmmoCount(copy);
            var index = TimelessAPI.getCommonGunIndex(gun.getGunId(copy));
            if (index.isEmpty()) return Optional.empty();
            var data = index.orElseThrow().getGunData();
            boolean virtual = gun.useDummyAmmo(copy) || gun.useInventoryAmmo(copy)
                    || data.getReloadData().getType() == FeedType.FUEL;
            int count = roundCount(magazine, gun.hasBulletInBarrel(copy), data.getBolt(), virtual);
            if (count == 0) return Optional.of(new Plan(null, 0, ItemStack.EMPTY));
            var id = data.getAmmoId();
            if (id == null || TimelessAPI.getCommonAmmoIndex(id).isEmpty()) return Optional.empty();
            var ammo = AmmoItemBuilder.create().setId(id).setCount(1).build();
            var accessor = IAmmo.getIAmmoOrNull(ammo);
            if (ammo.isEmpty() || accessor == null || !id.equals(accessor.getAmmoId(ammo))) return Optional.empty();
            int size = ammo.getMaxStackSize();
            if (size < 1 || (count + (long)size - 1) / size > MAX_STACKS) return Optional.empty();
            return Optional.of(new Plan(id, count, ammo));
        } catch (RuntimeException invalid) { return Optional.empty(); }
    }

    static int roundCount(int magazine, boolean chambered, Bolt bolt, boolean virtual) {
        if (magazine < 0 || magazine > MAX_ROUNDS) throw new IllegalArgumentException("invalid magazine count");
        if (virtual) return 0;
        if (bolt == null) throw new IllegalArgumentException("unknown bolt type");
        int count = Math.addExact(magazine, chambered && bolt != Bolt.OPEN_BOLT ? 1 : 0);
        if (count > MAX_ROUNDS) throw new IllegalArgumentException("too much ammunition");
        return count;
    }

    static List<ItemStack> split(ItemStack ammo, int count) {
        int size = ammo.getMaxStackSize();
        if (count < 0 || count > MAX_ROUNDS || size < 1 || (count + (long)size - 1) / size > MAX_STACKS)
            throw new IllegalArgumentException("invalid ammo delivery");
        var result = new ArrayList<ItemStack>();
        for (int left = count; left > 0; left -= Math.min(left, size)) {
            var stack = ammo.copy(); stack.setCount(Math.min(left, size)); result.add(stack);
        }
        return List.copyOf(result);
    }

    static <T> T execute(ServerPlayer player, BlueprintReverseEngineeringService.WorkstationTransaction station,
            IPlayerRecipeData data, Supplier<T> action, Predicate<T> success, Function<Failure,T> failure) {
        if (InsertedMagazineGuard.hasInsertedMagazine(station.physicalInput())) return failure.apply(Failure.MAGAZINE_INSERTED);
        var plan = plan(station.physicalInput());
        if (plan.isEmpty()) return failure.apply(Failure.INVALID_AMMO);
        if (plan.orElseThrow().count() == 0) return action.get();
        var spawned = new ArrayList<ItemEntity>();
        return execute(plan.orElseThrow(), station, data, action, success, failure, new Delivery() {
            public void give(List<ItemStack> stacks) {
                for (var original : stacks) {
                    var remaining = original.copy();
                    insert(player.getInventory().items, remaining, player.getInventory().getMaxStackSize());
                    if (!remaining.isEmpty()) {
                        var entity = new ItemEntity(player.serverLevel(), player.getX(), player.getY() + .5,
                                player.getZ(), remaining.copy());
                        entity.setDefaultPickUpDelay(); spawned.add(entity);
                        if (!player.serverLevel().addFreshEntity(entity)) throw new IllegalStateException("ammo drop rejected");
                    }
                }
                player.getInventory().setChanged();
            }
            public void rollback() { spawned.forEach(ItemEntity::discard); }
        });
    }

    /** Merge before using empty slots; never touches armor/offhand or creative deletion behavior. */
    static void insert(List<ItemStack> slots, ItemStack remaining, int slotLimit) {
        for (int pass=0;pass<2&&!remaining.isEmpty();pass++) for (int i=0;i<slots.size()&&!remaining.isEmpty();i++) {
            var current=slots.get(i);
            if (pass==0 ? current.isEmpty() || !ItemStack.isSameItemSameComponents(current,remaining) : !current.isEmpty()) continue;
            int moved=Math.min(remaining.getCount(), Math.max(0,Math.min(slotLimit,remaining.getMaxStackSize())-current.getCount()));
            if (moved==0) continue;
            if (current.isEmpty()) { var stack=remaining.copy();stack.setCount(moved);slots.set(i,stack); }
            else current.grow(moved);
            remaining.shrink(moved);
        }
    }
    interface Delivery { void give(List<ItemStack> stacks); void rollback(); }
    static <T> T execute(Plan plan, BlueprintReverseEngineeringService.WorkstationTransaction station,
            IPlayerRecipeData data, Supplier<T> action, Predicate<T> success, Function<Failure,T> failure, Delivery delivery) {
        if (InsertedMagazineGuard.hasInsertedMagazine(station.physicalInput())) return failure.apply(Failure.MAGAZINE_INSERTED);
        var input = station.physicalInput().copy(); var output = station.outputStack().copy();
        var inventory = station.inventoryStacks().stream().map(ItemStack::copy).toList();
        var progression = data.serializeNBT().copy();
        try {
            T result = action.get();
            if (!success.test(result)) return result;
            if (!station.physicalInput().isEmpty()) throw new IllegalStateException("weapon was not consumed");
            delivery.give(plan.stacks());
            return result;
        } catch (RuntimeException failedDelivery) {
            boolean restored = true;
            try { delivery.rollback(); } catch (RuntimeException e) { restored = false; }
            try {
                restored &= station.restore(input.copy(), inventory.stream().map(ItemStack::copy).toList(), output.copy());
                data.deserializeNBT(progression.copy());
                restored &= ItemStack.matches(input, station.physicalInput()) && ItemStack.matches(output, station.outputStack())
                        && progression.equals(data.serializeNBT()) && matching(inventory, station.inventoryStacks());
            } catch (RuntimeException rollbackFailure) { restored = false; }
            return failure.apply(restored ? Failure.TRANSACTION_FAILED : Failure.ROLLBACK_FAILED);
        }
    }
    private static boolean matching(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i=0;i<a.size();i++) if (!ItemStack.matches(a.get(i),b.get(i))) return false;
        return true;
    }
    private AnalyzerAmmoRecovery() { }
}
