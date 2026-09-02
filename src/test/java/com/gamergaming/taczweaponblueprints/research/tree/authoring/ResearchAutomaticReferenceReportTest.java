package com.gamergaming.taczweaponblueprints.research.tree.authoring;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.research.tree.automatic.WeaponMechanicalReferenceCatalog;

class ResearchAutomaticReferenceReportTest {
    @Test
    void reportIsDeterministicAppealFreeAndLoadable() {
        TaCZGunStats weak = gun("test:weak", 4.0, "weak-hash");
        TaCZGunStats strong = gun("test:strong", 12.0, "strong-hash");

        String first = ResearchAutomaticReferenceReport.toJson(
                List.of(strong, weak), "test-source-v1");
        String second = ResearchAutomaticReferenceReport.toJson(
                List.of(weak, strong), "test-source-v1");

        assertEquals(first, second);
        assertTrue(!first.contains("appeal"));
        WeaponMechanicalReferenceCatalog catalog =
                WeaponMechanicalReferenceCatalog.parse(first);
        assertEquals(2, catalog.blueprintIds().size());
        assertEquals("test-source-v1", catalog.sourceVersion());
    }

    private static TaCZGunStats gun(String id, double damage, String hash) {
        return new TaCZGunStats(
                id,
                "rifle",
                id + "_data",
                damage,
                0.0,
                600.0,
                20,
                2.0,
                100.0,
                50.0,
                0.1,
                1.5,
                1,
                0.2,
                0.3,
                3.0,
                0.2,
                0.4,
                -0.2,
                2,
                3,
                null,
                null,
                "magazine",
                false,
                hash,
                List.of());
    }
}
