package com.gamergaming.taczweaponblueprints.client;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph;

/** Pure disclosure-safe category cycling for the Research Tree toolbar. */
public final class ResearchTreeCategoryFilter {
    private ResearchTreeCategoryFilter() {
    }

    /** Empty means all categories; cycling past the final category returns to all. */
    public static Optional<String> next(List<String> publishedCategories, String current) {
        List<String> categories = validate(publishedCategories);
        if (categories.isEmpty()) {
            return Optional.empty();
        }
        if (current == null) {
            return Optional.of(categories.get(0));
        }
        int index = categories.indexOf(current);
        return index < 0 || index + 1 >= categories.size()
                ? Optional.empty()
                : Optional.of(categories.get(index + 1));
    }

    public static boolean matches(ResearchTreeGraph.Node node, String category) {
        if (node == null) {
            throw new IllegalArgumentException("Research Tree node cannot be null");
        }
        return category == null
                || ResearchTreePresentationContract.categoryLane(node).equals(category);
    }

    private static List<String> validate(List<String> categories) {
        if (categories == null
                || categories.stream().anyMatch(value -> value == null || value.isBlank())
                || new LinkedHashSet<>(categories).size() != categories.size()) {
            throw new IllegalArgumentException("invalid published Research Tree categories");
        }
        return List.copyOf(categories);
    }
}
