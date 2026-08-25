package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Finds an exact, overlap-safe ingredient allocation across the bench slots. */
public final class ResearchIngredientPlanner {
    private ResearchIngredientPlanner() {
    }

    public static Optional<Plan> plan(List<ItemStack> stacks, BlueprintResearchCost cost) {
        if (stacks == null || cost == null || stacks.stream().anyMatch(java.util.Objects::isNull)) {
            return Optional.empty();
        }
        List<BlueprintResearchIngredient> ingredients = cost.ingredients();
        if (ingredients.isEmpty()) {
            return Optional.of(new Plan(new int[stacks.size()]));
        }

        int ingredientCount = ingredients.size();
        int slotCount = stacks.size();
        int source = 0;
        int ingredientStart = 1;
        int slotStart = ingredientStart + ingredientCount;
        int sink = slotStart + slotCount;
        int[][] capacity = new int[sink + 1][sink + 1];
        int required = 0;

        for (int ingredientIndex = 0; ingredientIndex < ingredientCount; ingredientIndex++) {
            BlueprintResearchIngredient ingredient = ingredients.get(ingredientIndex);
            required += ingredient.count();
            capacity[source][ingredientStart + ingredientIndex] = ingredient.count();
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                if (matches(stacks.get(slotIndex), ingredient)) {
                    capacity[ingredientStart + ingredientIndex][slotStart + slotIndex] = ingredient.count();
                }
            }
        }
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            ItemStack stack = stacks.get(slotIndex);
            capacity[slotStart + slotIndex][sink] = stack.isEmpty() ? 0 : stack.getCount();
        }

        int[][] residual = copy(capacity);
        int flow = maximumFlow(residual, source, sink);
        if (flow != required) {
            return Optional.empty();
        }
        int[] decrements = new int[slotCount];
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            int node = slotStart + slotIndex;
            decrements[slotIndex] = capacity[node][sink] - residual[node][sink];
        }
        return Optional.of(new Plan(decrements));
    }

    static boolean matches(ItemStack stack, BlueprintResearchIngredient ingredient) {
        if (stack == null || stack.isEmpty() || ingredient == null) {
            return false;
        }
        var itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (itemId != null && ingredient.items().contains(itemId)) {
            return true;
        }
        return ingredient.tag()
                .map(id -> stack.is(TagKey.create(Registries.ITEM, id)))
                .orElse(false);
    }

    public static int matchingCount(List<ItemStack> stacks, BlueprintResearchIngredient ingredient) {
        if (stacks == null || ingredient == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : stacks) {
            if (matches(stack, ingredient)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static int maximumFlow(int[][] residual, int source, int sink) {
        int total = 0;
        int[] parent = new int[residual.length];
        while (findPath(residual, source, sink, parent)) {
            int pathFlow = Integer.MAX_VALUE;
            for (int node = sink; node != source; node = parent[node]) {
                pathFlow = Math.min(pathFlow, residual[parent[node]][node]);
            }
            for (int node = sink; node != source; node = parent[node]) {
                int previous = parent[node];
                residual[previous][node] -= pathFlow;
                residual[node][previous] += pathFlow;
            }
            total += pathFlow;
        }
        return total;
    }

    private static boolean findPath(int[][] residual, int source, int sink, int[] parent) {
        Arrays.fill(parent, -1);
        parent[source] = source;
        ArrayDeque<Integer> queue = new ArrayDeque<>();
        queue.add(source);
        while (!queue.isEmpty()) {
            int current = queue.removeFirst();
            for (int next = 0; next < residual.length; next++) {
                if (parent[next] == -1 && residual[current][next] > 0) {
                    parent[next] = current;
                    if (next == sink) {
                        return true;
                    }
                    queue.addLast(next);
                }
            }
        }
        return false;
    }

    private static int[][] copy(int[][] source) {
        int[][] copy = new int[source.length][];
        for (int index = 0; index < source.length; index++) {
            copy[index] = source[index].clone();
        }
        return copy;
    }

    public static final class Plan {
        private final int[] decrements;

        Plan(int[] decrements) {
            this.decrements = decrements.clone();
        }

        public int slotCount() {
            return decrements.length;
        }

        public int decrement(int slot) {
            return decrements[slot];
        }

        public int totalConsumed() {
            return Arrays.stream(decrements).sum();
        }

        public List<ItemStack> applyToCopies(List<ItemStack> stacks) {
            if (stacks == null || stacks.size() != decrements.length) {
                throw new IllegalArgumentException("ingredient stack count changed before commit");
            }
            List<ItemStack> result = new ArrayList<>(stacks.size());
            for (int slot = 0; slot < decrements.length; slot++) {
                ItemStack copy = stacks.get(slot).copy();
                if (copy.getCount() < decrements[slot]) {
                    throw new IllegalStateException("ingredient stack shrank before commit");
                }
                copy.shrink(decrements[slot]);
                result.add(copy);
            }
            return List.copyOf(result);
        }
    }
}
