package com.gamergaming.taczweaponblueprints.resource.research;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import net.minecraft.resources.ResourceLocation;

/** Creates a deterministic, author-friendly view of the live research catalog. */
public final class BlueprintResearchCatalogExporter {
    public static final int CURRENT_FORMAT = 2;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private BlueprintResearchCatalogExporter() {
    }

    public static String export(
            BlueprintResearchSnapshot snapshot,
            Map<ResourceLocation, BlueprintData> catalog,
            ResourceLocation profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId cannot be null");
        }
        BlueprintResearchSnapshot stableSnapshot = snapshot == null
                ? BlueprintResearchSnapshot.EMPTY
                : snapshot;
        Map<ResourceLocation, BlueprintData> stableCatalog = catalog == null ? Map.of() : catalog;
        List<Map.Entry<ResourceLocation, BlueprintData>> entries = new ArrayList<>(stableCatalog.entrySet());
        entries.removeIf(entry -> entry.getKey() == null || entry.getValue() == null);
        entries.sort(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)));
        Set<ResourceLocation> catalogIds = entries.stream()
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());

        JsonObject root = new JsonObject();
        root.addProperty("format", CURRENT_FORMAT);
        root.addProperty("profile", profileId.toString());
        root.addProperty("catalog_size", entries.size());
        List<BlueprintResearchSnapshot.GroupBinding> groups = stableSnapshot.groupsForProfile(profileId);
        root.addProperty("authored_group_count", groups.size());
        JsonArray exportedGroups = new JsonArray();
        for (BlueprintResearchSnapshot.GroupBinding binding : groups) {
            ResearchTreeGroupDefinition definition = binding.definition();
            JsonObject exportedGroup = new JsonObject();
            exportedGroup.addProperty("id", binding.groupId().toString());
            exportedGroup.addProperty("title", definition.title());
            definition.translationKey().ifPresent(value ->
                    exportedGroup.addProperty("translation_key", value));
            exportedGroup.addProperty("icon", definition.icon().toString());
            exportedGroup.addProperty("order", definition.order());
            exportedGroup.addProperty("rank_count", definition.ranks().size());
            exportedGroup.addProperty("member_count", definition.memberCount());
            JsonArray missingMembers = new JsonArray();
            definition.members().stream()
                    .filter(id -> !catalogIds.contains(id))
                    .forEach(id -> missingMembers.add(id.toString()));
            exportedGroup.add("missing_members", missingMembers);
            exportedGroups.add(exportedGroup);
        }
        root.add("groups", exportedGroups);
        JsonArray exportedEntries = new JsonArray();
        for (Map.Entry<ResourceLocation, BlueprintData> entry : entries) {
            ResourceLocation blueprintId = entry.getKey();
            BlueprintData data = entry.getValue();
            BlueprintResearchPolicyDefinition definition = BlueprintResearchPolicyResolver.definitionFor(
                    stableSnapshot,
                    stableCatalog,
                    profileId,
                    blueprintId);
            JsonObject exported = new JsonObject();
            exported.addProperty("blueprint", blueprintId.toString());
            exported.addProperty("item_type", data.getItemType());
            if (data.getRecipeId() != null) {
                exported.addProperty("recipe", data.getRecipeId().toString());
            }
            definition.ruleId().ifPresent(id -> exported.addProperty("selected_rule", id.toString()));
            exported.addProperty("specificity", definition.specificity().name().toLowerCase(java.util.Locale.ROOT));
            exported.addProperty("visibility", definition.visibility().serializedName());
            exported.addProperty("research_enabled", definition.researchEnabled());
            exported.addProperty("research_points", definition.researchCost().points());
            exported.addProperty("ingredient_types", definition.researchCost().ingredients().size());
            JsonArray prerequisites = new JsonArray();
            definition.prerequisites().forEach(id -> prerequisites.add(id.toString()));
            exported.add("prerequisites", prerequisites);
            stableSnapshot.placementFor(profileId, blueprintId).ifPresentOrElse(placement -> {
                ResearchTreeGroupDefinition group = stableSnapshot.groups()
                        .get(placement.groupId());
                exported.addProperty("presentation_source", "authored");
                exported.addProperty("research_group", placement.groupId().toString());
                if (group != null) {
                    exported.addProperty("research_group_title", group.title());
                    exported.addProperty("research_group_order", group.order());
                }
                exported.addProperty("research_rank", placement.rank());
                exported.addProperty("research_order_in_rank", placement.orderInRank());
            }, () -> exported.addProperty("presentation_source", "automatic_fallback"));
            exportedEntries.add(exported);
        }
        root.add("entries", exportedEntries);
        return GSON.toJson(root) + System.lineSeparator();
    }
}
