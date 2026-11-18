package com.gamergaming.taczweaponblueprints.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;

import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.contents.TranslatableContents;

class SettingsEditorsTest {
    @SuppressWarnings("unchecked")
    private static ValidatedField<Object> field(BlueprintConfig config, String id) throws Exception {
        return (ValidatedField<Object>) BlueprintConfig.class.getField(id).get(config);
    }

    private static String key(Button button) {
        return ((TranslatableContents) button.getMessage().getContents()).getKey();
    }

    @Test
    void aSwitchReadsOnOrOffAndFlipsTheStoredValue() throws Exception {
        BlueprintConfig config = new BlueprintConfig();
        AtomicInteger changes = new AtomicInteger();
        // One plain switch and one that is only active while research is enabled.
        for (String id : new String[] {"enableGunsmith", "enableResearch"}) {
            Button button = assertInstanceOf(Button.class,
                    SettingsEditors.create(field(config, id), changes::incrementAndGet));
            assertEquals("settings.taczweaponblueprints.choice.true", key(button), id + " starts On");
            button.onPress();
            assertEquals("settings.taczweaponblueprints.choice.false", key(button), id + " now reads Off");
        }
        assertFalse(config.enableGunsmith.get());
        assertFalse(config.enableResearch.getUnconditional());
        assertEquals(2, changes.get(), "each flip is reported so the draft can record it");
    }
}
