package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.TreeSet;
import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;

import me.fzzyhmstrs.fzzy_config.entry.EntryValidator;
import me.fzzyhmstrs.fzzy_config.util.ValidationResult;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedChoiceList;
import me.fzzyhmstrs.fzzy_config.validation.collection.ValidatedList;
import me.fzzyhmstrs.fzzy_config.validation.misc.ChoiceValidator;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedString;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.peanuuutz.tomlkt.TomlElement;

/**
 * A Fzzy Config multi-choice entry that accepts valid selectors from content
 * packs even when those selectors are not present in the current catalog.
 *
 * <p>Fzzy's native choice list deliberately rejects values outside its fixed
 * choice list. TaCZ gun subgroups are pack-provided strings, so this entry uses
 * normal list validation for storage and creates a native searchable choice
 * widget from the current catalog for editing. Saved selectors from a missing
 * pack remain selected and removable instead of being silently discarded.</p>
 */
public final class PreservingStringChoiceList extends ValidatedField<List<String>> {
    private final List<String> defaults;
    private final List<String> baseChoices;
    private final ValidatedString entryHandler;
    private final Supplier<? extends Collection<String>> dynamicChoices;
    private final Predicate<String> choiceFilter;
    private final BiFunction<String, String, MutableComponent> translationProvider;
    private final BiFunction<String, String, Component> descriptionProvider;
    private final ValidatedChoiceList.WidgetType widgetType;
    private final ValidatedList<String> storage;

    public PreservingStringChoiceList(
            List<String> defaultValues,
            List<String> baseChoices,
            ValidatedString entryHandler,
            Supplier<? extends Collection<String>> dynamicChoices,
            Predicate<String> choiceFilter,
            BiFunction<String, String, MutableComponent> translationProvider,
            BiFunction<String, String, Component> descriptionProvider,
            ValidatedChoiceList.WidgetType widgetType) {
        super(List.copyOf(defaultValues), List.copyOf(defaultValues));
        if (baseChoices == null || baseChoices.isEmpty()) {
            throw new IllegalArgumentException("a preserving choice list requires a base choice");
        }
        this.defaults = List.copyOf(defaultValues);
        this.baseChoices = List.copyOf(baseChoices);
        this.entryHandler = entryHandler;
        this.dynamicChoices = dynamicChoices;
        this.choiceFilter = choiceFilter;
        this.translationProvider = translationProvider;
        this.descriptionProvider = descriptionProvider;
        this.widgetType = widgetType;
        this.storage = new ValidatedList<>(this.defaults, entryHandler);
    }

    @Override
    public ValidationResult<List<String>> deserialize(TomlElement toml, String fieldName) {
        return storage.deserialize(toml, fieldName);
    }

    @Override
    public ValidationResult<TomlElement> serialize(List<String> value) {
        return storage.serialize(value);
    }

    @Override
    public ValidationResult<List<String>> correctEntry(
            List<String> input,
            EntryValidator.ValidationType type) {
        return storage.correctEntry(input, type);
    }

    @Override
    public ValidationResult<List<String>> validateEntry(
            List<String> input,
            EntryValidator.ValidationType type) {
        return storage.validateEntry(input, type);
    }

    @Override
    public PreservingStringChoiceList instanceEntry() {
        return new PreservingStringChoiceList(
                defaults,
                baseChoices,
                entryHandler,
                dynamicChoices,
                choiceFilter,
                translationProvider,
                descriptionProvider,
                widgetType);
    }

    @Override
    public boolean isValidEntry(Object input) {
        return storage.isValidEntry(input);
    }

    @Override
    public List<String> copyValue(List<String> value) {
        return List.copyOf(value);
    }

    @Override
    @OnlyIn(Dist.CLIENT)
    public AbstractWidget widgetEntry(ChoiceValidator<List<String>> choicePredicate) {
        ValidatedChoiceList<String> editor = new ValidatedChoiceList<>(
                get(),
                availableChoices(),
                entryHandler,
                translationProvider,
                descriptionProvider,
                widgetType);
        editor.listenToEntry(ignored -> accept(List.copyOf(editor.get())));
        return editor.widgetEntry(choicePredicate);
    }

    private List<String> availableChoices() {
        LinkedHashSet<String> available = new LinkedHashSet<>();
        addNormalizedInOrder(available, baseChoices);
        Collection<String> supplied;
        try {
            supplied = dynamicChoices == null ? List.of() : dynamicChoices.get();
        } catch (RuntimeException ignored) {
            // The settings screen can open before TaCZ publishes its catalog.
            supplied = List.of();
        }
        available.addAll(normalize(supplied));

        // Keep selectors from a removed or unavailable pack visible so an
        // operator can explicitly retain or remove them.
        available.addAll(normalize(get()));
        return List.copyOf(available);
    }

    List<String> availableChoicesSnapshot() {
        return availableChoices();
    }

    private void addNormalizedInOrder(
            Collection<String> destination,
            Iterable<? extends String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            if (value == null) {
                continue;
            }
            String candidate = value.trim().toLowerCase(Locale.ROOT);
            if (!candidate.isEmpty() && choiceFilter.test(candidate)) {
                destination.add(candidate);
            }
        }
    }

    private List<String> normalize(Iterable<? extends String> values) {
        TreeSet<String> normalized = new TreeSet<>();
        if (values != null) {
            for (String value : values) {
                if (value == null) {
                    continue;
                }
                String candidate = value.trim().toLowerCase(Locale.ROOT);
                if (!candidate.isEmpty() && choiceFilter.test(candidate)) {
                    normalized.add(candidate);
                }
            }
        }
        return List.copyOf(normalized);
    }
}
