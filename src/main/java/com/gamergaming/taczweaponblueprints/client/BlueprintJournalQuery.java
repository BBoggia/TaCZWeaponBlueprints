package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalEntry;
import com.gamergaming.taczweaponblueprints.journal.BlueprintJournalSnapshot;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

/** Pure, disclosure-safe search/filter/sort/pagination for the Journal UI. */
public final class BlueprintJournalQuery {
    public static final int MAX_SEARCH_LENGTH = 80;

    private BlueprintJournalQuery() {
    }

    public static Result query(
            List<BlueprintJournalEntry> entries,
            String search,
            StatusFilter status,
            String category,
            SortOrder sort,
            int requestedPage,
            int pageSize,
            Function<BlueprintJournalEntry, String> nameResolver) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Journal page size must be positive");
        }
        List<BlueprintJournalEntry> source = entries == null ? List.of() : entries;
        StatusFilter safeStatus = status == null ? StatusFilter.ALL : status;
        SortOrder safeSort = sort == null ? SortOrder.CATALOG : sort;
        Function<BlueprintJournalEntry, String> safeResolver =
                nameResolver == null ? ignored -> "" : nameResolver;
        String needle = normalizeSearch(search);
        String selectedCategory = normalizeCategory(category);

        List<BlueprintJournalEntry> matches = new ArrayList<>();
        for (BlueprintJournalEntry entry : source) {
            if (entry == null || !safeStatus.matches(entry) || !matchesCategory(entry, selectedCategory)) {
                continue;
            }
            if (!needle.isEmpty() && !searchableText(entry, safeResolver).contains(needle)) {
                continue;
            }
            matches.add(entry);
        }

        matches.sort(comparator(safeSort, safeResolver));
        int pageCount = Math.max(1, (matches.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = Math.min(matches.size(), page * pageSize);
        int to = Math.min(matches.size(), from + pageSize);
        return new Result(List.copyOf(matches.subList(from, to)), matches.size(), page, pageCount, pageSize);
    }

    public static HistoryResult queryHistory(
            List<BlueprintJournalSnapshot.HistoryEntry> entries,
            String search,
            int requestedPage,
            int pageSize) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Journal page size must be positive");
        }
        String needle = normalizeSearch(search);
        List<BlueprintJournalSnapshot.HistoryEntry> matches = (entries == null ? List.<BlueprintJournalSnapshot.HistoryEntry>of() : entries)
                .stream()
                .filter(java.util.Objects::nonNull)
                .filter(entry -> needle.isEmpty()
                        || entry.blueprintId().toString().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing((BlueprintJournalSnapshot.HistoryEntry entry) -> !entry.learned())
                        .thenComparing(entry -> entry.blueprintId().toString()))
                .toList();
        int pageCount = Math.max(1, (matches.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = Math.min(matches.size(), page * pageSize);
        int to = Math.min(matches.size(), from + pageSize);
        return new HistoryResult(matches.subList(from, to), matches.size(), page, pageCount, pageSize);
    }

    public static RecentResult queryRecent(
            List<BlueprintJournalSnapshot.RecentUnlockBatch> entries,
            String search,
            int requestedPage,
            int pageSize,
            Function<ResourceLocation, String> nameResolver) {
        if (pageSize < 1) {
            throw new IllegalArgumentException("Journal page size must be positive");
        }
        String needle = normalizeSearch(search);
        Function<ResourceLocation, String> safeResolver =
                nameResolver == null ? ignored -> "" : nameResolver;
        List<BlueprintJournalSnapshot.RecentUnlockBatch> matches =
                (entries == null
                        ? List.<BlueprintJournalSnapshot.RecentUnlockBatch>of()
                        : entries).stream()
                        .filter(java.util.Objects::nonNull)
                        .filter(entry -> needle.isEmpty()
                                || recentSearchableText(entry, safeResolver).contains(needle))
                        .sorted(Comparator.comparingLong(
                                BlueprintJournalSnapshot.RecentUnlockBatch::sequence).reversed())
                        .toList();
        int pageCount = Math.max(1, (matches.size() + pageSize - 1) / pageSize);
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));
        int from = Math.min(matches.size(), page * pageSize);
        int to = Math.min(matches.size(), from + pageSize);
        return new RecentResult(
                matches.subList(from, to), matches.size(), page, pageCount, pageSize);
    }

    private static String recentSearchableText(
            BlueprintJournalSnapshot.RecentUnlockBatch entry,
            Function<ResourceLocation, String> nameResolver) {
        StringBuilder text = new StringBuilder(entry.source().name());
        append(text, entry.targetBlueprintId().toString());
        append(text, nameResolver.apply(entry.targetBlueprintId()));
        for (ResourceLocation member : entry.memberBlueprintIds()) {
            append(text, member.toString());
            append(text, nameResolver.apply(member));
        }
        return text.toString().toLowerCase(Locale.ROOT);
    }

    public static List<String> categories(List<BlueprintJournalEntry> entries) {
        if (entries == null) {
            return List.of();
        }
        return entries.stream()
                .filter(java.util.Objects::nonNull)
                .map(BlueprintJournalEntry::itemType)
                .flatMap(java.util.Optional::stream)
                .map(BlueprintJournalQuery::normalizeCategory)
                .filter(category -> !category.isEmpty())
                .distinct()
                .sorted()
                .toList();
    }

    private static boolean matchesCategory(BlueprintJournalEntry entry, String category) {
        return category.isEmpty()
                || entry.itemType().map(BlueprintJournalQuery::normalizeCategory).filter(category::equals).isPresent();
    }

    private static String searchableText(
            BlueprintJournalEntry entry,
            Function<BlueprintJournalEntry, String> nameResolver) {
        // Only fields physically present in the disclosure-filtered entry are searchable.
        StringBuilder text = new StringBuilder();
        if (entry.visibility() != JournalVisibility.SILHOUETTE) {
            append(text, nameResolver.apply(entry));
        }
        entry.blueprintId().ifPresent(id -> append(text, id.toString()));
        entry.itemType().ifPresent(type -> append(text, type));
        return text.toString().toLowerCase(Locale.ROOT);
    }

    private static Comparator<BlueprintJournalEntry> comparator(
            SortOrder sort,
            Function<BlueprintJournalEntry, String> nameResolver) {
        Comparator<BlueprintJournalEntry> ordinal = Comparator.comparingInt(BlueprintJournalEntry::ordinal);
        return switch (sort) {
            case CATALOG -> ordinal;
            case NAME -> Comparator
                    .comparing((BlueprintJournalEntry entry) -> sortName(entry, nameResolver))
                    .thenComparing(ordinal);
            case CATEGORY -> Comparator
                    .comparing((BlueprintJournalEntry entry) -> entry.itemType().orElse("\uffff"), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(entry -> sortName(entry, nameResolver))
                    .thenComparing(ordinal);
            case PROGRESS -> Comparator
                    .comparingInt(BlueprintJournalQuery::progressRank)
                    .thenComparing(entry -> sortName(entry, nameResolver))
                    .thenComparing(ordinal);
        };
    }

    private static String sortName(
            BlueprintJournalEntry entry,
            Function<BlueprintJournalEntry, String> nameResolver) {
        if (entry.visibility() == JournalVisibility.SILHOUETTE) {
            return "\uffff";
        }
        String resolved = nameResolver.apply(entry);
        return resolved == null ? "" : resolved.toLowerCase(Locale.ROOT);
    }

    private static int progressRank(BlueprintJournalEntry entry) {
        if (entry.learned()) {
            return 0;
        }
        if (entry.researchable()) {
            return 1;
        }
        if (entry.discovered()) {
            return 2;
        }
        if (entry.recyclable()) {
            return 3;
        }
        if (entry.visibility() == JournalVisibility.NAME) {
            return 4;
        }
        return 5;
    }

    private static String normalizeSearch(String search) {
        if (search == null) {
            return "";
        }
        String bounded = search.length() > MAX_SEARCH_LENGTH
                ? search.substring(0, MAX_SEARCH_LENGTH)
                : search;
        return bounded.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCategory(String category) {
        return category == null ? "" : category.strip().toLowerCase(Locale.ROOT);
    }

    private static void append(StringBuilder builder, String value) {
        if (value != null && !value.isBlank()) {
            builder.append(' ').append(value);
        }
    }

    public enum StatusFilter {
        ALL {
            @Override
            boolean matches(BlueprintJournalEntry entry) {
                return true;
            }
        },
        LEARNED {
            @Override
            boolean matches(BlueprintJournalEntry entry) {
                return entry.learned();
            }
        },
        DISCOVERED {
            @Override
            boolean matches(BlueprintJournalEntry entry) {
                return entry.discovered() && !entry.learned();
            }
        },
        RESEARCHABLE {
            @Override
            boolean matches(BlueprintJournalEntry entry) {
                return entry.researchable();
            }
        },
        RECYCLABLE {
            @Override
            boolean matches(BlueprintJournalEntry entry) {
                return entry.recyclable();
            }
        },
        UNREVEALED {
            @Override
            boolean matches(BlueprintJournalEntry entry) {
                return entry.visibility() == JournalVisibility.SILHOUETTE
                        || entry.visibility() == JournalVisibility.NAME;
            }
        };

        abstract boolean matches(BlueprintJournalEntry entry);

        public StatusFilter next() {
            StatusFilter[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public enum SortOrder {
        CATALOG,
        NAME,
        CATEGORY,
        PROGRESS;

        public SortOrder next() {
            SortOrder[] values = values();
            return values[(ordinal() + 1) % values.length];
        }
    }

    public record Result(
            List<BlueprintJournalEntry> entries,
            int totalMatches,
            int page,
            int pageCount,
            int pageSize) {
    }

    public record HistoryResult(
            List<BlueprintJournalSnapshot.HistoryEntry> entries,
            int totalMatches,
            int page,
            int pageCount,
            int pageSize) {
    }

    public record RecentResult(
            List<BlueprintJournalSnapshot.RecentUnlockBatch> entries,
            int totalMatches,
            int page,
            int pageCount,
            int pageSize) {
    }
}
