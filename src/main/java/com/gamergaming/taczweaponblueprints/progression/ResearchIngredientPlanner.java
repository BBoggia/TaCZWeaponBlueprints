package com.gamergaming.taczweaponblueprints.progression;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Finds an exact, overlap-safe ingredient allocation across supplied inventory slots. */
public final class ResearchIngredientPlanner {
    public static final int MAX_TOTAL_REQUIREMENT_COUNT =
            PlayerProgressionLimits.MAX_IDS_PER_COLLECTION
                    * BlueprintResearchCost.MAX_INGREDIENT_TYPES
                    * BlueprintResearchIngredient.MAX_COUNT;

    private ResearchIngredientPlanner() {
    }

    public static Optional<Plan> plan(List<ItemStack> stacks, BlueprintResearchCost cost) {
        return allocation(stacks, cost)
                .filter(Allocation::complete)
                .map(allocation -> new Plan(allocation.decrements));
    }

    public static Optional<Plan> plan(
            List<ItemStack> stacks,
            List<Requirement> requirements) {
        return allocation(stacks, requirements)
                .filter(Allocation::complete)
                .map(allocation -> new Plan(allocation.decrements));
    }

    /**
     * Finds the largest overlap-safe allocation available from the supplied
     * slots, even when the complete cost cannot yet be satisfied.
     */
    public static Optional<Allocation> allocation(List<ItemStack> stacks, BlueprintResearchCost cost) {
        if (cost == null) {
            return Optional.empty();
        }
        return allocation(stacks, cost.ingredients().stream()
                .map(Requirement::fromIngredient)
                .toList());
    }

    /**
     * Sparse maximum-flow allocation for a combined multi-node path cost.
     * Unlike the original square matrix, memory grows with real
     * ingredient/slot matches, so broad prerequisite closures stay bounded.
     */
    public static Optional<Allocation> allocation(
            List<ItemStack> stacks,
            List<Requirement> requirements) {
        if (stacks == null || requirements == null
                || stacks.stream().anyMatch(java.util.Objects::isNull)
                || requirements.stream().anyMatch(java.util.Objects::isNull)) {
            return Optional.empty();
        }
        if (requirements.isEmpty()) {
            return Optional.of(new Allocation(
                    new int[stacks.size()], new int[0][stacks.size()], 0, 0));
        }

        int ingredientCount = requirements.size();
        int slotCount = stacks.size();
        int source = 0;
        int ingredientStart = 1;
        int slotStart = ingredientStart + ingredientCount;
        int sink = slotStart + slotCount;
        SparseFlow flow = new SparseFlow(sink + 1);
        List<MatchEdge> matches = new ArrayList<>();
        SparseFlow.Edge[] slotEdges = new SparseFlow.Edge[slotCount];
        int required = 0;

        for (int ingredientIndex = 0; ingredientIndex < ingredientCount; ingredientIndex++) {
            Requirement ingredient = requirements.get(ingredientIndex);
            try {
                required = Math.addExact(required, ingredient.count());
            } catch (ArithmeticException exception) {
                return Optional.empty();
            }
            if (required > MAX_TOTAL_REQUIREMENT_COUNT) {
                return Optional.empty();
            }
            flow.addEdge(source, ingredientStart + ingredientIndex, ingredient.count());
            for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
                if (matches(stacks.get(slotIndex), ingredient)) {
                    SparseFlow.Edge edge = flow.addEdge(
                            ingredientStart + ingredientIndex,
                            slotStart + slotIndex,
                            ingredient.count());
                    matches.add(new MatchEdge(ingredientIndex, slotIndex, edge));
                }
            }
        }
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            ItemStack stack = stacks.get(slotIndex);
            slotEdges[slotIndex] = flow.addEdge(
                    slotStart + slotIndex,
                    sink,
                    stack.isEmpty() ? 0 : stack.getCount());
        }

        int allocated = flow.maximumFlow(source, sink);
        int[] decrements = new int[slotCount];
        for (int slotIndex = 0; slotIndex < slotCount; slotIndex++) {
            decrements[slotIndex] = slotEdges[slotIndex].used();
        }
        int[][] ingredientAllocations = new int[ingredientCount][slotCount];
        for (MatchEdge match : matches) {
            ingredientAllocations[match.ingredient()][match.slot()] = match.edge().used();
        }
        return Optional.of(new Allocation(
                decrements, ingredientAllocations, required, allocated));
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

    static boolean matches(ItemStack stack, Requirement ingredient) {
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

    public record Requirement(
            List<ResourceLocation> items,
            Optional<ResourceLocation> tag,
            int count) {
        public Requirement {
            items = items == null
                    ? List.of()
                    : List.copyOf(new LinkedHashSet<>(items)).stream()
                            .sorted(Comparator.comparing(ResourceLocation::toString))
                            .toList();
            tag = tag == null ? Optional.empty() : tag;
            if (items.size() > BlueprintResearchIngredient.MAX_ITEMS
                    || (items.isEmpty() == tag.isEmpty())
                    || count < 1
                    || count > MAX_TOTAL_REQUIREMENT_COUNT
                    || items.stream().anyMatch(id -> id == null
                            || id.toString().length()
                                    > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH)
                    || tag.filter(id -> id.toString().length()
                            > PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH).isPresent()) {
                throw new IllegalArgumentException("invalid combined research ingredient");
            }
        }

        private static Requirement fromIngredient(BlueprintResearchIngredient ingredient) {
            return new Requirement(ingredient.items(), ingredient.tag(), ingredient.count());
        }
    }

    private record MatchEdge(int ingredient, int slot, SparseFlow.Edge edge) {
    }

    /** Dinic flow with stable insertion-order traversal. */
    private static final class SparseFlow {
        private final List<List<Edge>> graph;
        private final int[] level;
        private final int[] cursor;

        private SparseFlow(int nodeCount) {
            graph = new ArrayList<>(nodeCount);
            for (int node = 0; node < nodeCount; node++) {
                graph.add(new ArrayList<>());
            }
            level = new int[nodeCount];
            cursor = new int[nodeCount];
        }

        private Edge addEdge(int from, int to, int capacity) {
            Edge forward = new Edge(to, graph.get(to).size(), capacity, capacity);
            Edge reverse = new Edge(from, graph.get(from).size(), 0, 0);
            graph.get(from).add(forward);
            graph.get(to).add(reverse);
            return forward;
        }

        private int maximumFlow(int source, int sink) {
            int total = 0;
            while (buildLevels(source, sink)) {
                Arrays.fill(cursor, 0);
                int sent;
                while ((sent = send(source, sink, Integer.MAX_VALUE)) > 0) {
                    total = Math.addExact(total, sent);
                }
            }
            return total;
        }

        private boolean buildLevels(int source, int sink) {
            Arrays.fill(level, -1);
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            level[source] = 0;
            queue.add(source);
            while (!queue.isEmpty()) {
                int node = queue.removeFirst();
                for (Edge edge : graph.get(node)) {
                    if (edge.capacity > 0 && level[edge.to] < 0) {
                        level[edge.to] = level[node] + 1;
                        queue.addLast(edge.to);
                    }
                }
            }
            return level[sink] >= 0;
        }

        private int send(int node, int sink, int available) {
            if (node == sink) {
                return available;
            }
            List<Edge> edges = graph.get(node);
            while (cursor[node] < edges.size()) {
                Edge edge = edges.get(cursor[node]);
                if (edge.capacity > 0 && level[edge.to] == level[node] + 1) {
                    int sent = send(edge.to, sink, Math.min(available, edge.capacity));
                    if (sent > 0) {
                        edge.capacity -= sent;
                        graph.get(edge.to).get(edge.reverse).capacity += sent;
                        return sent;
                    }
                }
                cursor[node]++;
            }
            return 0;
        }

        private static final class Edge {
            private final int to;
            private final int reverse;
            private final int originalCapacity;
            private int capacity;

            private Edge(int to, int reverse, int capacity, int originalCapacity) {
                this.to = to;
                this.reverse = reverse;
                this.capacity = capacity;
                this.originalCapacity = originalCapacity;
            }

            private int used() {
                return originalCapacity - capacity;
            }
        }
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

    /** Read-only maximum-flow allocation used by previews and safe auto-fill. */
    public static final class Allocation {
        private final int[] decrements;
        private final int[][] ingredientAllocations;
        private final int totalRequired;
        private final int totalAllocated;

        private Allocation(
                int[] decrements,
                int[][] ingredientAllocations,
                int totalRequired,
                int totalAllocated) {
            this.decrements = decrements.clone();
            this.ingredientAllocations = new int[ingredientAllocations.length][];
            for (int index = 0; index < ingredientAllocations.length; index++) {
                this.ingredientAllocations[index] = ingredientAllocations[index].clone();
            }
            this.totalRequired = totalRequired;
            this.totalAllocated = totalAllocated;
        }

        public int slotCount() {
            return decrements.length;
        }

        public int ingredientCount() {
            return ingredientAllocations.length;
        }

        public int decrement(int slot) {
            return decrements[slot];
        }

        public int allocatedForIngredient(int ingredient) {
            return allocatedForIngredientFromSlots(ingredient, 0, decrements.length);
        }

        public int allocatedForIngredientFromSlots(int ingredient, int fromSlot, int toSlot) {
            if (ingredient < 0 || ingredient >= ingredientAllocations.length
                    || fromSlot < 0 || toSlot < fromSlot || toSlot > decrements.length) {
                throw new IndexOutOfBoundsException("invalid research ingredient allocation range");
            }
            return Arrays.stream(ingredientAllocations[ingredient], fromSlot, toSlot).sum();
        }

        public int totalRequired() {
            return totalRequired;
        }

        public int totalAllocated() {
            return totalAllocated;
        }

        public boolean complete() {
            return totalAllocated == totalRequired;
        }
    }
}
