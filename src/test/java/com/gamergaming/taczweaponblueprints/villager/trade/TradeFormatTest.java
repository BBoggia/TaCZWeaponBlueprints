package com.gamergaming.taczweaponblueprints.villager.trade;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.google.gson.*;
import net.minecraft.resources.ResourceLocation;
import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

class TradeFormatTest {
    static final String JSON = """
        {"version":1,"profession":"taczweaponblueprints:gunsmith","level":1,"slot":"test:slot",
        "output":{"kind":"ammo","count":16,"candidates":[{"id":"test:round","weight":1}]},
        "price":{"item":"currency","count":4},"uses":12,"xp":1}
        """;
    static ResourceLocation id(String id) { return TradeDefinition.parseId(id); }
    static TradeDefinition read(String name, String json) throws IOException { return TradeParser.read(id(name), new StringReader(json)).definition(); }
    static TradeDefinition edit(String name, java.util.function.Consumer<JsonObject> mutation) throws IOException {
        JsonObject json = JsonParser.parseString(JSON).getAsJsonObject(); mutation.accept(json); return read(name, json.toString());
    }
    @Test void bundledTablesAreStructurallyValidAndOwnTwentyCoreAndThreeSupportSlots() throws Exception {
        Map<ResourceLocation, TradeDefinition> values = new TreeMap<>();
        Path directory = Path.of("src/main/resources/data/taczweaponblueprints/taczweaponblueprints/villager_trades");
        try (var paths = Files.walk(directory)) {
            for (var path : paths.filter(p -> p.toString().endsWith(".json")).toList()) {
                String name = "taczweaponblueprints:" + directory.relativize(path).toString().replace(".json", "");
                try (var input = Files.newBufferedReader(path)) { var d = TradeParser.read(id(name), input).definition(); values.put(d.id(), d); }
            }
        }
        var snapshot = new TradeSnapshot(values);
        assertEquals(20, values.values().stream().filter(d -> d.role() == Role.CORE).count());
        assertEquals(3, values.values().stream().filter(d -> d.role() == Role.SUPPORT).count());
        assertEquals(30, snapshot.definitions().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.definitions().clear());
    }
    @ParameterizedTest @ValueSource(strings={"strict","custom-gun-pack","legacy-data-exchange"})
    void installableExamplesMergeWithBundledFallbackGraph(String example)throws Exception {
        var values=new TreeMap<ResourceLocation,TradeDefinition>();
        for(var directory:List.of(Path.of("src/main/resources/data/taczweaponblueprints/taczweaponblueprints/villager_trades"),
                Path.of("examples/villager-trading",example,"data/taczweaponblueprints/taczweaponblueprints/villager_trades"))) {
            assertTrue(Files.isDirectory(directory),directory.toString());
            try(var paths=Files.walk(directory)) {
                for(var path:paths.filter(p->p.toString().endsWith(".json")).toList()) {
                    var id=id("taczweaponblueprints:"+directory.relativize(path).toString().replace(".json",""));
                    try(var reader=Files.newBufferedReader(path)){values.put(id,TradeParser.read(id,reader).definition());}
                }
            }
        }
        assertDoesNotThrow(()->new TradeSnapshot(values));
        if(example.equals("strict"))assertTrue(values.values().stream().filter(d->d.output().kind()==Kind.CURRENCY).noneMatch(TradeDefinition::enabled));
        if(example.equals("custom-gun-pack"))assertEquals(id("exampleguns:field_carbine"),values.get(id("taczweaponblueprints:gunsmith/starter_gun")).output().candidates().get(0).id());
    }

    @ParameterizedTest @ValueSource(strings={"0", "-1", "65", "1.5", "2147483648", "1e999", "\"4\"", "null"})
    void invalidPrimaryCountsCannotCoerceOrWrap(String number) {
        assertThrows(Exception.class, () -> read("test:a", JSON.replace("\"count\":4", "\"count\":" + number)));
    }
    @ParameterizedTest @ValueSource(strings={"0", "-1", "1e999", "NaN", "Infinity", "\"1\"", "1000001"})
    void invalidWeightsRejected(String number) {
        assertThrows(Exception.class, () -> read("test:a", JSON.replace("\"weight\":1", "\"weight\":"+number)));
    }
    @ParameterizedTest @ValueSource(strings={"round", "Test:round", "test:", "test:a b"})
    void canonicalIdsRequired(String value) { assertThrows(Exception.class, () -> read("test:a", JSON.replace("test:round",value))); }
    @Test void demandBoundsAreCheckedBeforeFloatRoundingAndStudySelectionMustMatchKindAndTier() {
        assertThrows(IllegalArgumentException.class, () -> edit("test:a", o -> o.addProperty("demand_multiplier", new java.math.BigDecimal("1.0000000000001"))));
        assertThrows(IllegalArgumentException.class, () -> edit("test:a", o -> o.getAsJsonObject("output").addProperty("selection", "study_t1")));
        assertThrows(IllegalArgumentException.class, () -> edit("test:a", o -> {
            o.getAsJsonObject("output").addProperty("kind", "analysis_fragments");
            o.getAsJsonObject("output").addProperty("selection", "study_t2");
            o.getAsJsonObject("output").addProperty("tier", 1);
        }));
    }
    @Test void duplicateFieldsTrailingDataUnknownFieldsAndDeepInputRejected() {
        for (String input : List.of(JSON.replace("\"version\":1", "\"version\":1,\"version\":1"),
                JSON.replace("\"kind\":\"ammo\"", "\"kind\":\"ammo\",\"kind\":\"gun\""), JSON+"{}",
                JSON.replace("\"uses\":12", "\"usess\":12"), "[".repeat(17)+"1"+"]".repeat(17)))
            assertThrows(Exception.class, () -> read("test:a", input));
    }
    @Test void documentCharacterBoundaryAndCandidateBoundaryAreEnforced() throws Exception {
        assertNotNull(read("test:a", JSON + " ".repeat(TradeLimits.JSON_CHARACTERS - JSON.length())));
        assertThrows(IllegalArgumentException.class, () -> read("test:a", JSON + " ".repeat(TradeLimits.JSON_CHARACTERS + 1 - JSON.length())));
        var json = JsonParser.parseString(JSON).getAsJsonObject(); var array = new JsonArray();
        for (int i=0;i<64;i++) { var c = new JsonObject(); c.addProperty("id", "test:c"+i); array.add(c); }
        json.getAsJsonObject("output").add("candidates",array); assertEquals(64, read("test:a",json.toString()).output().candidates().size());
        array.add(array.get(0)); assertThrows(IllegalArgumentException.class, () -> read("test:a",json.toString()));
    }
    @Test void duplicateCandidateAndSlotOwnersRejectEvenWhenDisabled() throws Exception {
        assertThrows(IllegalArgumentException.class, () -> read("test:a", JSON.replace("{\"id\":\"test:round\",\"weight\":1}","{\"id\":\"test:round\"},{\"id\":\"test:round\"}")));
        var a = read("test:a",JSON); var b = edit("test:b", o -> o.addProperty("enabled",false));
        assertThrows(IllegalArgumentException.class, () -> new TradeSnapshot(Map.of(a.id(),a,b.id(),b)));
    }
    @Test void cyclesMissingReferencesAndCrossProfessionFallbacksReject() throws Exception {
        var a = edit("test:a", o -> { o.addProperty("role","fallback"); o.add("fallbacks",JsonParser.parseString("[\"test:b\"]")); });
        var b = edit("test:b", o -> { o.addProperty("role","fallback"); o.add("fallbacks",JsonParser.parseString("[\"test:a\"]")); });
        assertThrows(IllegalArgumentException.class, () -> new TradeSnapshot(Map.of(a.id(),a,b.id(),b)));
        assertThrows(IllegalArgumentException.class, () -> new TradeSnapshot(Map.of(a.id(),a)));
        var wrong = edit("test:b", o -> { o.addProperty("role","fallback"); o.addProperty("profession","taczweaponblueprints:ballistics_researcher"); });
        assertThrows(IllegalArgumentException.class, () -> new TradeSnapshot(Map.of(a.id(),a,wrong.id(),wrong)));
    }
    @Test void fallbackDepthAndAggregateCandidateLimitsAreInclusive() throws Exception {
        Map<ResourceLocation,TradeDefinition> defs = new HashMap<>();
        for (int i=7;i>=0;i--) { final int n=i; var d=edit("test:d"+i,o->{o.addProperty("role","fallback"); if(n<7)o.add("fallbacks",JsonParser.parseString("[\"test:d"+(n+1)+"\"]"));});defs.put(d.id(),d); }
        assertDoesNotThrow(()->new TradeSnapshot(defs));
        var extra=edit("test:extra",o->o.add("fallbacks",JsonParser.parseString("[\"test:d0\"]")));defs.put(extra.id(),extra);
        assertThrows(IllegalArgumentException.class,()->new TradeSnapshot(defs));
        defs.clear();
        for(int i=0;i<64;i++){ final int n=i; var d=edit("test:c"+i,o->{o.addProperty("slot","test:s"+n);var a=new JsonArray();for(int j=0;j<64;j++){var c=new JsonObject();c.addProperty("id","test:i"+j);a.add(c);}o.getAsJsonObject("output").add("candidates",a);});defs.put(d.id(),d);}
        assertDoesNotThrow(()->new TradeSnapshot(defs));defs.put(extra.id(),read("test:extra",JSON));
        assertThrows(IllegalArgumentException.class,()->new TradeSnapshot(defs));
    }
    @Test void professionSlotLimitIsIndependentOfInactiveDefinitions() throws Exception {
        Map<ResourceLocation,TradeDefinition> defs=new HashMap<>();
        for(int i=0;i<128;i++){final int n=i;var d=edit("test:d"+n,o->{o.addProperty("slot","test:s"+n);o.addProperty("enabled",false);});defs.put(d.id(),d);}
        assertDoesNotThrow(()->new TradeSnapshot(defs));var d=read("test:last",JSON);defs.put(d.id(),d);
        assertThrows(IllegalArgumentException.class,()->new TradeSnapshot(defs));
    }
}
