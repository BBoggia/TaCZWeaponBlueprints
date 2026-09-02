package com.gamergaming.taczweaponblueprints.resource.award;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchDataManager;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.resources.ResourceLocation;

/** Immutable server-side catalog facts used by blueprint award selectors. */
public record ResearchPointAwardBlueprintFacts(
        ResourceLocation id,
        Set<ResourceLocation> tags,
        String category,
        BlueprintKind kind) {
    private static volatile Publication publication = new Publication(Map.of(), -1L, -1L);
    public ResearchPointAwardBlueprintFacts {
        if (id == null || category == null || category.isBlank() || kind == null) {
            throw new IllegalArgumentException("invalid blueprint award facts");
        }
        tags = tags == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(tags));
    }

    public ResearchPointAwardContext context(
            ResearchPointAwardTrigger.Type type,
            ResourceLocation profile,
            ResearchPointAwardContext.DispatchMode mode) {
        return context(type, profile, mode, Optional.empty(), 0, 0);
    }

    public ResearchPointAwardContext context(
            ResearchPointAwardTrigger.Type type,
            ResourceLocation profile,
            ResearchPointAwardContext.DispatchMode mode,
            Optional<ResearchPointAwardTrigger.MilestoneState> milestoneState,
            int previousCount,
            int currentCount) {
        return new ResearchPointAwardContext(
                type,
                profile,
                mode,
                Optional.of(id),
                tags,
                Optional.of(category),
                Optional.of(kind),
                milestoneState,
                previousCount,
                currentCount,
                Optional.empty());
    }

    public static Map<ResourceLocation, ResearchPointAwardBlueprintFacts> index(
            Map<ResourceLocation, BlueprintData> catalog,
            BlueprintResearchSnapshot research) {
        if (catalog == null || research == null) {
            return Map.of();
        }
        Map<ResourceLocation, Set<ResourceLocation>> tagsByBlueprint = new LinkedHashMap<>();
        research.tags().entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> entry.getValue().values().forEach(blueprintId ->
                        tagsByBlueprint.computeIfAbsent(blueprintId, ignored -> new LinkedHashSet<>())
                                .add(entry.getKey())));
        Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts = new LinkedHashMap<>();
        catalog.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(Comparator.comparing(ResourceLocation::toString)))
                .forEach(entry -> facts.put(entry.getKey(), new ResearchPointAwardBlueprintFacts(
                        entry.getKey(),
                        tagsByBlueprint.getOrDefault(entry.getKey(), Set.of()),
                        entry.getValue().getItemType(),
                        entry.getValue().getKind())));
        return Map.copyOf(facts);
    }

    /** Returns one revision-consistent cached index for the current live publications. */
    public static Publication currentPublication() {
        var catalog = BlueprintDataManager.SERVER.catalogPublication();
        var research = BlueprintResearchDataManager.INSTANCE.publication();
        Publication current = publication;
        if (current.catalogRevision() == catalog.revision()
                && current.researchRevision() == research.revision()) {
            return current;
        }
        synchronized (ResearchPointAwardBlueprintFacts.class) {
            current = publication;
            if (current.catalogRevision() != catalog.revision()
                    || current.researchRevision() != research.revision()) {
                current = new Publication(
                        index(catalog.blueprints(), research.snapshot()),
                        catalog.revision(),
                        research.revision());
                publication = current;
            }
            return current;
        }
    }

    public static void clearCurrentPublication() {
        publication = new Publication(Map.of(), -1L, -1L);
    }

    public record Publication(
            Map<ResourceLocation, ResearchPointAwardBlueprintFacts> facts,
            long catalogRevision,
            long researchRevision) {
        public Publication {
            if (facts == null || catalogRevision < -1L || researchRevision < -1L) {
                throw new IllegalArgumentException("invalid blueprint award-facts publication");
            }
            facts = Map.copyOf(facts);
        }
    }
}
