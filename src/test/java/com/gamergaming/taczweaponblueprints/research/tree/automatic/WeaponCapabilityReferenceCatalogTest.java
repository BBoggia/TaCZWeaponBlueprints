package com.gamergaming.taczweaponblueprints.research.tree.automatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract;
import com.google.gson.JsonParser;

class WeaponCapabilityReferenceCatalogTest {
    @Test
    void bundledReferenceIsPinnedCompleteAndSelfVerifying() {
        WeaponCapabilityReferenceCatalog catalog =
                WeaponCapabilityReferenceCatalog.bundled();

        assertEquals(ResearchTechTreeContract.CAPABILITY_REFERENCE_VERSION,
                catalog.referenceVersion());
        assertEquals(WeaponCapabilityReferenceCatalog.BUNDLED_SOURCE_VERSION,
                catalog.sourceVersion());
        assertEquals(WeaponCapabilityReferenceCatalog.BUNDLED_SOURCE_FINGERPRINT,
                catalog.sourceFingerprint());
        assertEquals(WeaponCapabilityReferenceCatalog.BUNDLED_METRICS_FINGERPRINT,
                catalog.metricsFingerprint());
        assertEquals(WeaponCapabilityReferenceCatalog.BUNDLED_WEAPON_COUNT,
                catalog.blueprintIds().size());
        assertTrue(catalog.blueprintIds().contains("tacz:m320"));
        assertTrue(catalog.blueprintIds().contains("tacz:rpg7"));
        assertEquals(catalog.metricsFingerprint(),
                WeaponCapabilityReferenceCatalog.fingerprint(
                        catalog.reference().distributions()));
        for (CapabilityMetric metric : CapabilityMetric.values()) {
            assertTrue(catalog.reference().sampleCount(metric) >= 2);
            assertTrue(catalog.reference().sampleCount(metric)
                    <= WeaponCapabilityReferenceCatalog.BUNDLED_WEAPON_COUNT);
        }
    }

    @Test
    void parserRejectsUnknownFieldsFingerprintDriftAndUnsortedIds() throws Exception {
        var stream = getClass().getResourceAsStream(
                WeaponCapabilityReferenceCatalog.BUNDLED_RESOURCE);
        assertTrue(stream != null);
        String json;
        try (var reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            json = readAll(reader);
        }

        var unknown = JsonParser.parseString(json).getAsJsonObject();
        unknown.addProperty("unexpected", true);
        assertThrows(IllegalArgumentException.class,
                () -> WeaponCapabilityReferenceCatalog.parse(unknown.toString()));

        var drift = JsonParser.parseString(json).getAsJsonObject();
        drift.getAsJsonObject("metrics").getAsJsonArray("impact_damage")
                .set(0, new com.google.gson.JsonPrimitive(999.0));
        assertThrows(IllegalArgumentException.class,
                () -> WeaponCapabilityReferenceCatalog.parse(drift.toString()));

        var unsorted = JsonParser.parseString(json).getAsJsonObject();
        var ids = unsorted.getAsJsonArray("blueprints");
        var first = ids.get(0);
        ids.set(0, ids.get(1));
        ids.set(1, first);
        assertThrows(IllegalArgumentException.class,
                () -> WeaponCapabilityReferenceCatalog.parse(unsorted.toString()));
    }

    @Test
    void parserEnforcesTheSharedBoundedInputLimit() {
        String oversized = " ".repeat(
                WeaponMechanicalReferenceCatalog.MAX_REFERENCE_CHARACTERS + 1);
        assertThrows(IllegalArgumentException.class,
                () -> WeaponCapabilityReferenceCatalog.parse(oversized));
    }

    private static String readAll(java.io.Reader reader) throws java.io.IOException {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int read;
        while ((read = reader.read(buffer)) >= 0) {
            result.append(buffer, 0, read);
        }
        return result.toString();
    }
}
