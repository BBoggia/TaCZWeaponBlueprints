package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.function.Supplier;

import com.mojang.brigadier.context.StringRange;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;

import me.fzzyhmstrs.fzzy_config.entry.EntryValidator;
import me.fzzyhmstrs.fzzy_config.screen.widget.SuggestionBackedTextFieldWidget;
import me.fzzyhmstrs.fzzy_config.util.AllowableStrings;
import me.fzzyhmstrs.fzzy_config.validation.misc.ChoiceValidator;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

/**
 * Resource-ID text entry that can match catalog suggestions by either their
 * stable ID or their localized player-facing name.
 *
 * <p>The suggestion text remains the ID, so selecting a name match always
 * writes a portable value to the config. Validation is intentionally separate
 * from catalog membership: a syntactically valid ID from an unavailable pack
 * remains valid even when it cannot currently be suggested.</p>
 */
public final class BlueprintIdAutocompleteString extends ValidatedString {
    private static final int MAX_SUGGESTIONS = 50;

    private final String defaultValue;
    private final Predicate<String> validator;
    private final Supplier<? extends List<BlueprintIdSuggestion>> suggestionSupplier;

    public BlueprintIdAutocompleteString(
            String defaultValue,
            Predicate<String> validator,
            Supplier<? extends List<BlueprintIdSuggestion>> suggestionSupplier) {
        super(
                defaultValue,
                new AllowableStrings(
                        Objects.requireNonNull(validator, "validator"),
                        () -> suggestionIds(suggestionSupplier)));
        this.defaultValue = defaultValue;
        this.validator = validator;
        this.suggestionSupplier = Objects.requireNonNull(suggestionSupplier, "suggestionSupplier");
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public AbstractWidget widgetEntry(ChoiceValidator<String> choicePredicate) {
        List<BlueprintIdSuggestion> snapshot = suggestionSnapshot();
        return new SuggestionBackedTextFieldWidget(
                110,
                20,
                this,
                choicePredicate,
                this,
                this,
                (input, cursor, ignored) -> buildSuggestions(
                        snapshot,
                        input,
                        cursor,
                        value -> choicePredicate
                                .validateEntry(value, EntryValidator.ValidationType.STRONG)
                                .isValid()));
    }

    @Override
    public BlueprintIdAutocompleteString instanceEntry() {
        return new BlueprintIdAutocompleteString(defaultValue, validator, suggestionSupplier);
    }

    CompletableFuture<Suggestions> suggestionsForTesting(
            String input,
            int cursor,
            Predicate<String> allowed) {
        return buildSuggestions(suggestionSnapshot(), input, cursor, allowed);
    }

    private List<BlueprintIdSuggestion> suggestionSnapshot() {
        try {
            List<? extends BlueprintIdSuggestion> supplied = suggestionSupplier.get();
            if (supplied == null || supplied.isEmpty()) {
                return List.of();
            }
            Map<String, BlueprintIdSuggestion> byId = new LinkedHashMap<>();
            for (BlueprintIdSuggestion suggestion : supplied) {
                if (suggestion != null && validator.test(suggestion.id())) {
                    byId.putIfAbsent(suggestion.id(), suggestion);
                }
            }
            return List.copyOf(byId.values());
        } catch (RuntimeException ignored) {
            // Server assets may not be synchronized when a local settings
            // screen is first constructed. Manual ID entry remains available.
            return List.of();
        }
    }

    private static List<String> suggestionIds(
            Supplier<? extends List<BlueprintIdSuggestion>> suggestionSupplier) {
        if (suggestionSupplier == null) {
            return List.of();
        }
        try {
            List<? extends BlueprintIdSuggestion> suggestions = suggestionSupplier.get();
            if (suggestions == null || suggestions.isEmpty()) {
                return List.of();
            }
            return suggestions.stream()
                    .filter(Objects::nonNull)
                    .map(BlueprintIdSuggestion::id)
                    .distinct()
                    .sorted()
                    .toList();
        } catch (RuntimeException ignored) {
            return List.of();
        }
    }

    private static CompletableFuture<Suggestions> buildSuggestions(
            List<BlueprintIdSuggestion> candidates,
            String input,
            int cursor,
            Predicate<String> allowed) {
        String safeInput = input == null ? "" : input;
        int safeCursor = Math.max(0, Math.min(cursor, safeInput.length()));
        String query = normalize(safeInput.substring(0, safeCursor));
        StringRange replacement = StringRange.between(0, safeCursor);

        List<RankedSuggestion> matches = new ArrayList<>();
        for (BlueprintIdSuggestion candidate : candidates) {
            if (candidate == null || !allowed.test(candidate.id())) {
                continue;
            }
            int rank = matchRank(candidate, query);
            if (rank >= 0) {
                matches.add(new RankedSuggestion(candidate, rank));
            }
        }
        matches.sort(Comparator
                .comparingInt(RankedSuggestion::rank)
                .thenComparing(
                        match -> match.suggestion().displayName(),
                        String.CASE_INSENSITIVE_ORDER)
                .thenComparing(match -> match.suggestion().id()));

        List<Suggestion> built = matches.stream()
                .limit(MAX_SUGGESTIONS)
                .map(match -> {
                    BlueprintIdSuggestion suggestion = match.suggestion();
                    return new Suggestion(
                            replacement,
                            suggestion.id(),
                            Component.literal(suggestion.displayName() + " — " + suggestion.id()));
                })
                .toList();
        return CompletableFuture.completedFuture(new Suggestions(replacement, built));
    }

    private static int matchRank(BlueprintIdSuggestion candidate, String query) {
        if (query.isEmpty()) {
            return 10;
        }
        String id = normalize(candidate.id());
        String path = normalize(candidate.idPath());
        String name = normalize(candidate.displayName());
        String searchable = normalizeSeparators(candidate.displayName() + " " + candidate.id());
        String wordQuery = normalizeSeparators(query);

        if (name.equals(query)) {
            return 0;
        }
        if (id.equals(query) || path.equals(query)) {
            return 1;
        }
        if (name.startsWith(query)) {
            return 2;
        }
        if (path.startsWith(query) || id.startsWith(query)) {
            return 3;
        }
        if (name.contains(query)) {
            return 4;
        }
        if (path.contains(query) || id.contains(query)) {
            return 5;
        }
        if (allWordsMatch(searchable, wordQuery)) {
            return 6;
        }
        return -1;
    }

    private static boolean allWordsMatch(String searchable, String query) {
        if (query.isEmpty()) {
            return true;
        }
        for (String word : query.split(" ")) {
            if (!word.isEmpty() && !searchable.contains(word)) {
                return false;
            }
        }
        return true;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
    }

    private static String normalizeSeparators(String value) {
        return normalize(value)
                .replace(':', ' ')
                .replace('/', ' ')
                .replace('_', ' ')
                .replace('-', ' ')
                .replace('.', ' ')
                .replaceAll("\\s+", " ")
                .strip();
    }

    public record BlueprintIdSuggestion(String id, String displayName) {
        public BlueprintIdSuggestion {
            id = Objects.requireNonNull(id, "id");
            displayName = displayName == null || displayName.isBlank() ? id : displayName;
        }

        private String idPath() {
            int separator = id.indexOf(':');
            return separator >= 0 && separator + 1 < id.length()
                    ? id.substring(separator + 1)
                    : id;
        }
    }

    private record RankedSuggestion(BlueprintIdSuggestion suggestion, int rank) { }
}
