package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotSame;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchCost;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchIngredient;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchPolicy;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintResearchTarget.MatchSpecificity;
import com.gamergaming.taczweaponblueprints.resource.research.JournalVisibility;

import net.minecraft.resources.ResourceLocation;

class BlueprintProgressionConfigSnapshotTest {
    private static final ResourceLocation PROFILE = new ResourceLocation("test", "profile");
    private static final ResourceLocation BLUEPRINT = new ResourceLocation("test", "blueprint");

    @Test
    void rejectsInvalidPointCapsAndRequiredState() {
        assertThrows(IllegalArgumentException.class, () -> config(
                true, JournalVisibility.FULL, DuplicateBlueprintPolicy.MANUAL_RECYCLING, -1));
        assertThrows(IllegalArgumentException.class, () -> config(
                true,
                JournalVisibility.FULL,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                PlayerProgressionLimits.MAX_RESEARCH_POINTS + 1));
        assertThrows(IllegalArgumentException.class, () -> new BlueprintProgressionConfigSnapshot(
                true, true, true, null, true, DuplicateBlueprintPolicy.KEEP, false, 10, false, PROFILE));
    }

    @Test
    void mapsTheSynchronizedConfigDefaultsIntoAnImmutableSnapshot() {
        BlueprintConfig config = new BlueprintConfig();
        BlueprintProgressionConfigSnapshot snapshot = config.progressionSnapshot();

        assertTrue(snapshot.blueprintsEnabled());
        assertTrue(snapshot.discoveryTrackingEnabled());
        assertTrue(snapshot.journalEnabled());
        assertTrue(snapshot.researchEnabled());
        assertEquals(TreeResearchResultMode.DIRECT_LEARN, snapshot.treeResearchResultMode());
        assertEquals(ResearchCostMode.POINTS_AND_ITEMS, snapshot.researchCostMode());
        assertEquals(
                FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY,
                snapshot.foundWeaponRecoveryMode());
        assertEquals(JournalVisibility.FULL, snapshot.maximumUndiscoveredVisibility());
        assertEquals(DuplicateBlueprintPolicy.MANUAL_RECYCLING, snapshot.duplicatePolicy());
        assertFalse(snapshot.allowUnlearnedRecycling());
        assertEquals(BlueprintProgressionConfigSnapshot.DEFAULT_POINT_CAP, snapshot.pointCap());
        assertFalse(snapshot.creativeBypassesResearchCost());
        assertEquals(BlueprintConfig.DEFAULT_RESEARCH_PROFILE, snapshot.activeProfileId());
        assertEquals(10_000, BlueprintProgressionConfigSnapshot.DEFAULT_POINT_CAP);

        config.enableResearch.accept(false);
        config.onSyncServer();
        BlueprintProgressionConfigSnapshot updated = config.progressionSnapshot();
        assertNotSame(snapshot, updated);
        assertFalse(updated.researchEnabled());
        assertTrue(snapshot.researchEnabled());

        config.treeResearchResultMode.accept(TreeResearchResultMode.CREATE_BLUEPRINT);
        config.onSyncServer();
        assertEquals(
                TreeResearchResultMode.CREATE_BLUEPRINT,
                config.progressionSnapshot().treeResearchResultMode());
    }

    @Test
    void composesCoarseGatesWithoutMutatingDatapackPolicy() {
        BlueprintResearchPolicy base = basePolicy(5, 20);
        BlueprintProgressionConfigSnapshot disabled = new BlueprintProgressionConfigSnapshot(
                false,
                true,
                true,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                true,
                100,
                true,
                PROFILE);

        BlueprintResearchPolicy effective = disabled.apply(base);
        assertFalse(effective.journalEnabled());
        assertEquals(JournalVisibility.HIDDEN, effective.visibility());
        assertFalse(effective.researchEnabled());
        assertFalse(effective.recyclingEnabled());
        assertTrue(base.journalEnabled());
        assertTrue(base.researchEnabled());
    }

    @Test
    void capsUndiscoveredDisclosureAndEnforcesPointEconomy() {
        BlueprintResearchPolicy base = basePolicy(9, 8);
        BlueprintProgressionConfigSnapshot capped = config(
                true,
                JournalVisibility.NAME,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                10);

        BlueprintResearchPolicy effective = capped.apply(base);
        assertEquals(JournalVisibility.NAME, effective.visibility());
        assertTrue(effective.researchable());
        assertTrue(effective.canAffordPoints());
        assertFalse(effective.recyclable(), "crediting two points would exceed the configured cap");

        BlueprintResearchPolicy unaffordableByPolicy = config(
                true,
                JournalVisibility.FULL,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                5).apply(base);
        assertFalse(unaffordableByPolicy.researchable());
        assertFalse(unaffordableByPolicy.canAffordPoints());
    }

    @Test
    void fullDatapackPolicyLetsEveryGlobalVisibilityCeilingRemainDistinct() {
        BlueprintResearchPolicy fullPolicy = basePolicy(9, 8, JournalVisibility.FULL);
        for (JournalVisibility maximum : JournalVisibility.values()) {
            BlueprintResearchPolicy effective = config(
                    true,
                    maximum,
                    DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                    100).apply(fullPolicy);
            assertEquals(maximum, effective.visibility());
        }
    }

    @Test
    void keepPolicyAndUnlearnedGateDisablePermissiveDatapackRecycling() {
        BlueprintResearchPolicy base = basePolicy(0, 8);
        BlueprintProgressionConfigSnapshot keep = new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.KEEP,
                true,
                100,
                false,
                PROFILE);
        assertFalse(keep.apply(base).recyclable());

        BlueprintProgressionConfigSnapshot learnedOnly = new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                JournalVisibility.FULL,
                true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING,
                false,
                100,
                false,
                PROFILE);
        assertFalse(learnedOnly.apply(base).allowUnlearnedRecycling());
    }

    @Test
    void researchCostModesMaskOnlyTheSelectedRuntimeChannels() {
        BlueprintResearchCost authored = new BlueprintResearchCost(
                8,
                List.of(new BlueprintResearchIngredient(
                        List.of(new ResourceLocation("minecraft:paper")),
                        Optional.empty(),
                        4)));
        BlueprintResearchPolicy base = basePolicy(10, 8).withResearchCost(authored);

        BlueprintProgressionConfigSnapshot pointsOnly = new BlueprintProgressionConfigSnapshot(
                true, true, true, JournalVisibility.FULL, true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING, true, 100, false, PROFILE,
                TreeResearchResultMode.DIRECT_LEARN,
                ResearchCostMode.POINTS_ONLY,
                FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);
        BlueprintProgressionConfigSnapshot itemsOnly = new BlueprintProgressionConfigSnapshot(
                true, true, true, JournalVisibility.FULL, true,
                DuplicateBlueprintPolicy.MANUAL_RECYCLING, true, 100, false, PROFILE,
                TreeResearchResultMode.DIRECT_LEARN,
                ResearchCostMode.ITEMS_ONLY,
                FoundWeaponRecoveryMode.PROTECTED_BLUEPRINT_ONLY);

        assertEquals(8, pointsOnly.apply(base).researchCost().points());
        assertTrue(pointsOnly.apply(base).researchCost().ingredients().isEmpty());
        assertEquals(0, itemsOnly.apply(base).researchCost().points());
        assertEquals(authored.ingredients(), itemsOnly.apply(base).researchCost().ingredients());
        assertEquals(authored, base.researchCost(), "runtime masking must not rewrite authored cost");
    }

    private static BlueprintProgressionConfigSnapshot config(
            boolean researchEnabled,
            JournalVisibility visibility,
            DuplicateBlueprintPolicy duplicatePolicy,
            int pointCap) {
        return new BlueprintProgressionConfigSnapshot(
                true,
                true,
                true,
                visibility,
                researchEnabled,
                duplicatePolicy,
                true,
                pointCap,
                false,
                PROFILE);
    }

    private static BlueprintResearchPolicy basePolicy(int points, int cost) {
        return basePolicy(points, cost, JournalVisibility.PREVIEW);
    }

    private static BlueprintResearchPolicy basePolicy(
            int points,
            int cost,
            JournalVisibility visibility) {
        return new BlueprintResearchPolicy(
                BLUEPRINT,
                PROFILE,
                true,
                false,
                true,
                false,
                true,
                points,
                PlayerProgressionLimits.MAX_RESEARCH_POINTS,
                true,
                true,
                visibility,
                true,
                true,
                true,
                2,
                new BlueprintResearchCost(cost, List.of()),
                false,
                List.of(),
                true,
                Optional.empty(),
                MatchSpecificity.NONE);
    }
}
