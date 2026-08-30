package com.gamergaming.taczweaponblueprints.compat.recipeviewer;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Shared recipe-viewer help and disclosure-safe blueprint selection.
 *
 * <p>These entries describe public workstations and item families only. They
 * never derive content from the server catalog. Concrete item pages are
 * limited to identities already present in the server-filtered Journal, so a
 * client recipe viewer cannot reveal hidden blueprint identities or research
 * requirements.</p>
 */
public final class BlueprintRecipeViewerInfo {
    private BlueprintRecipeViewerInfo() {
    }

    public static List<Component> components(Topic topic) {
        if (topic == null) {
            return List.of();
        }
        return topic.translationKeys().stream()
                .<Component>map(Component::translatable)
                .toList();
    }

    /**
     * Returns only identities the server has explicitly disclosed to this
     * client. Recipe-viewer integrations may use these IDs to construct exact
     * physical blueprint stacks without enumerating the hidden catalog.
     */
    public static List<ResourceLocation> disclosedBlueprintIds(
            BlueprintJournalSnapshot journal) {
        if (journal == null) {
            return List.of();
        }
        return journal.entries().stream()
                .flatMap(entry -> entry.blueprintId().stream())
                .sorted(Comparator.comparing(ResourceLocation::toString))
                .toList();
    }

    public enum Topic {
        RESEARCH_BENCH("research_bench", 3),
        BLUEPRINT_ANALYZER("blueprint_analyzer", 3),
        BLUEPRINT("blueprint", 2),
        RESEARCH_DATA("research_data", 2);

        private final String path;
        private final List<String> translationKeys;

        Topic(String path, int lineCount) {
            this.path = path;
            this.translationKeys = java.util.stream.IntStream.rangeClosed(1, lineCount)
                    .mapToObj(line -> "recipe_viewer.taczweaponblueprints."
                            + path + "." + line)
                    .toList();
        }

        public String path() {
            return path;
        }

        public List<String> translationKeys() {
            return translationKeys;
        }

        public static List<Topic> all() {
            return Arrays.asList(values());
        }
    }
}
