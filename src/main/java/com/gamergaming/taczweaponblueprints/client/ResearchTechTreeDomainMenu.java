package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

import net.minecraft.resources.ResourceLocation;

/**
 * Immutable, disclosure-safe menu model shared by compact and fullscreen Tech
 * Tree navigation. Slot identity never changes when a domain is unpublished.
 */
public record ResearchTechTreeDomainMenu(
        List<Entry> entries,
        Optional<Domain> selectedDomain) {
    public ResearchTechTreeDomainMenu {
        List<Entry> normalizedEntries = entries == null ? List.of() : List.copyOf(entries);
        Optional<Domain> normalizedSelection = selectedDomain == null
                ? Optional.empty() : selectedDomain;
        entries = normalizedEntries;
        selectedDomain = normalizedSelection;
        if (normalizedEntries.size() != ResearchTechTreeContract.DOMAIN_ORDER.size()
                || !normalizedEntries.stream().map(Entry::domain).toList()
                        .equals(ResearchTechTreeContract.DOMAIN_ORDER)
                || normalizedEntries.stream().filter(Entry::selected).count()
                        != (normalizedSelection.isPresent() ? 1L : 0L)
                || normalizedSelection.filter(domain -> normalizedEntries.stream()
                        .noneMatch(entry -> entry.domain() == domain
                                && entry.available() && entry.selected())).isPresent()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree domain menu is inconsistent");
        }
    }

    public static ResearchTechTreeDomainMenu create(
            ResearchTechTreeProjectionCatalog catalog,
            ResearchTechTreeViewState state) {
        if (catalog == null || state == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree domain menu inputs cannot be null");
        }
        Optional<Domain> selected = state.selectedDomain()
                .filter(domain -> catalog.projection(domain).isPresent());
        List<Entry> entries = ResearchTechTreeContract.DOMAIN_ORDER.stream()
                .map(domain -> {
                    Optional<ResearchTechTreeProjection> projection =
                            catalog.projection(domain);
                    Optional<ResourceLocation> icon = catalog.presentation().domain(domain)
                            .flatMap(value -> value.iconNodeId());
                    return new Entry(
                            domain,
                            projection.isPresent(),
                            selected.filter(domain::equals).isPresent(),
                            projection.map(value -> value.graph().nodes().size()).orElse(0),
                            projection.isPresent() ? icon : Optional.empty());
                })
                .toList();
        return new ResearchTechTreeDomainMenu(entries, selected);
    }

    public Entry entry(Domain domain) {
        if (domain == null) {
            throw new IllegalArgumentException(
                    "Research Tech Tree domain menu lookup cannot be null");
        }
        return entries.get(domain.ordinal());
    }

    public Entry entryAt(int index) {
        if (index < 0 || index >= entries.size()) {
            throw new IllegalArgumentException(
                    "Research Tech Tree domain menu index is outside the stable slots");
        }
        return entries.get(index);
    }

    /** Wraps through available domains only, independent of missing stable slots. */
    public Optional<Domain> cycle(int delta) {
        List<Domain> available = entries.stream()
                .filter(Entry::available)
                .map(Entry::domain)
                .toList();
        if (available.isEmpty()) {
            return Optional.empty();
        }
        if (delta == 0) {
            return selectedDomain.isPresent()
                    ? selectedDomain
                    : Optional.of(available.get(0));
        }
        int current = selectedDomain.map(available::indexOf).orElse(-1);
        int base = current < 0 ? (delta > 0 ? -1 : 0) : current;
        return Optional.of(available.get(Math.floorMod(base + delta, available.size())));
    }

    public record Entry(
            Domain domain,
            boolean available,
            boolean selected,
            int visibleBlueprintCount,
            Optional<ResourceLocation> iconNodeId) {
        public Entry {
            iconNodeId = iconNodeId == null ? Optional.empty() : iconNodeId;
            if (domain == null || visibleBlueprintCount < 0
                    || selected && !available
                    || available != (visibleBlueprintCount > 0)
                    || !available && iconNodeId.isPresent()) {
                throw new IllegalArgumentException(
                        "Research Tech Tree domain menu entry is invalid");
            }
        }
    }
}
