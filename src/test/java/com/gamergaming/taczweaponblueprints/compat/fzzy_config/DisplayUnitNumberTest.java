package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicBoolean;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import org.junit.jupiter.api.Test;

class DisplayUnitNumberTest {
    @Test void chanceUsesPercentButRetainsFractionStorage() {
        var field=DisplayUnitNumber.percent(.2);
        assertEquals(20,field.displayValue());
        assertEquals(.205,field.fromDisplay(20.5).get(),1e-12);
        field.accept(.32830057026427484);
        assertEquals(32.830057026427484,field.displayValue(),1e-10);
        assertFalse(field.fromDisplay(100.1).isValid());
        assertFalse(field.fromDisplay(Double.NaN).isValid());
        assertFalse(field.fromDisplay(Double.POSITIVE_INFINITY).isValid());
        assertEquals(1,field.fromDisplay(100).get());
    }
    @Test void discountUnitsFollowSelectedModeWithoutChangingStoredAmount() {
        var percentage=new AtomicBoolean(true);
        var field=DisplayUnitNumber.integer(2500,10000,1,()->percentage.get()?100:1);
        assertEquals(25,field.displayValue());
        assertEquals(2500,field.fromDisplay(25).get());
        assertEquals(1,field.fromDisplay(.01).get());
        assertFalse(field.fromDisplay(.001).isValid());
        percentage.set(false);
        assertEquals(2500,field.displayValue());
        assertEquals(25,field.fromDisplay(25).get());
        assertFalse(field.fromDisplay(25.5).isValid());
        assertEquals(2500,field.get());
    }
    @Test void secondsRemainWholeMillisecondsAndRejectOutOfRangeValues() {
        var field=DisplayUnitNumber.integer(600,3000,100,()->1000);
        assertEquals(.6,field.displayValue());
        assertEquals(1250,field.fromDisplay(1.25).get());
        assertFalse(field.fromDisplay(.05).isValid());
        assertFalse(field.fromDisplay(3.1).isValid());
        assertFalse(field.fromDisplay(.6005).isValid());
    }
    @Test void settingsRoundTripOldUnitsAndDoNotPersistNewGroupNames() {
        var server=new BlueprintConfig();server.blueprintSpawnChance.validateAndSet(.32830057026427484);
        server.fragmentDiscountValue.validateAndSet(2375);
        var text=ConfigApi.serializeConfig(server).get();
        var restored=ConfigApi.deserializeConfig(new BlueprintConfig(),text).get();
        assertEquals(.32830057026427484,restored.blueprintSpawnChance.getUnconditional());
        assertEquals(2375,restored.fragmentDiscountValue.getUnconditional());
        for(var group:new String[]{"currency","researchData","customTiers","advancedCrafting","ordinaryFragments"})
            assertFalse(text.contains(group+" ="));
        var client=new ResearchTreeClientConfig();client.holdDurationMillis.validateAndSet(1250);
        var saved=ConfigApi.serializeConfig(client).get();
        assertTrue(saved.contains("holdDurationMillis = 1250"));
        assertEquals(1250,ConfigApi.deserializeConfig(new ResearchTreeClientConfig(),saved).get().holdDurationMillis.getUnconditional());
    }
}
