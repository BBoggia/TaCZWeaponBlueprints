package com.gamergaming.taczweaponblueprints.client.settings;

import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import me.fzzyhmstrs.fzzy_config.validation.misc.ChoiceValidator;
import me.fzzyhmstrs.fzzy_config.validation.misc.ValidatedCondition;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/** Builds the control for one setting. Switches read On and Off everywhere, the same as Quick Setup. */
final class SettingsEditors {
    private SettingsEditors() {}

    static Component onOff(boolean value) {
        return Component.translatable("settings.taczweaponblueprints.choice." + value);
    }

    private static Object stored(ValidatedField<Object> field) {
        return field instanceof ValidatedCondition<?> condition ? condition.getUnconditional() : field.get();
    }

    /** {@code changed} runs after a switch is flipped, for screens that copy edits into a draft. */
    static AbstractWidget create(ValidatedField<Object> field, Runnable changed) {
        if (!(stored(field) instanceof Boolean)) {
            return field.widgetEntry(ChoiceValidator.Companion.any());
        }
        return Button.builder(onOff((Boolean) stored(field)), button -> {
            boolean next = !(Boolean) stored(field);
            field.validateAndSet(next);
            button.setMessage(onOff((Boolean) stored(field)));
            changed.run();
        }).bounds(0, 0, 150, 20).build();
    }
}
