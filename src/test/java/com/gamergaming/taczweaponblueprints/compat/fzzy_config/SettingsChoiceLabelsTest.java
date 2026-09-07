package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import com.google.gson.JsonParser;
import me.fzzyhmstrs.fzzy_config.util.EnumTranslatable;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import org.junit.jupiter.api.Test;

class SettingsChoiceLabelsTest {
    @Test void everyVisibleEnumChoiceHasAReadableNameAndExplanation() throws Exception {
        var labels=JsonParser.parseString(Files.readString(Path.of("src/main/resources/assets/taczweaponblueprints/lang/en_us.json"))).getAsJsonObject();
        int choices=0;
        for(Object config:new Object[]{new BlueprintConfig(),new ResearchTreeClientConfig()}) {
            for(var field:config.getClass().getFields()) {
                Object entry=field.get(config);
                if(!(entry instanceof ValidatedField<?> setting) || !(setting.get() instanceof Enum<?> value))continue;
                for(Object constant:value.getDeclaringClass().getEnumConstants()) {
                    assertInstanceOf(EnumTranslatable.class,constant,field.getName());
                    String key=((EnumTranslatable)constant).prefix()+"."+((Enum<?>)constant).name();
                    assertTrue(labels.has(key),key);assertTrue(labels.has(key+".desc"),key+".desc");
                    assertFalse(labels.get(key).getAsString().isBlank(),key);choices++;
                }
            }
        }
        assertTrue(choices>50,"cover all registered settings, not a small hand-picked subset");
    }
}
