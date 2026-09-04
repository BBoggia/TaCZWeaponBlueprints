package com.gamergaming.taczweaponblueprints.compat.fzzy_config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.client.ResearchTreeLayoutPreset;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeMinimapMode;
import com.gamergaming.taczweaponblueprints.progression.BlueprintBalancePreset;
import com.gamergaming.taczweaponblueprints.progression.BlueprintFragmentPreset;
import com.gamergaming.taczweaponblueprints.progression.AmmoCraftingStrategy;
import com.gamergaming.taczweaponblueprints.progression.AttachmentCraftingStrategy;
import com.gamergaming.taczweaponblueprints.progression.CraftingAccessOverride;
import com.gamergaming.taczweaponblueprints.progression.ResearchProgressionPreset;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;
import com.gamergaming.taczweaponblueprints.resource.research.BlueprintFragmentProfilePolicy;
import me.fzzyhmstrs.fzzy_config.api.ConfigApi;
import net.minecraft.resources.ResourceLocation;

class SettingsSerializationCompatibilityTest {
    @Test
    void clientGroupsAndActionsDoNotChangePersistedFieldPaths() {
        String serialized = ConfigApi.serializeConfig(new ResearchTreeClientConfig()).get();

        assertTrue(serialized.contains("version = 3"));
        assertTrue(serialized.contains("layoutPreset = \"BALANCED\""));
        assertTrue(serialized.contains("minimap = \"AUTOMATIC\""));
        assertTrue(serialized.contains("holdToResearch = true"));
        assertTrue(serialized.contains("nodeGap = "));
        assertFalse(serialized.contains("componentGap = "));
        assertFalse(serialized.contains("intraGroupGap = "));
        assertFalse(serialized.contains("interGroupGap = "));
        assertFalse(serialized.contains("groupPadding = "));
        assertFalse(serialized.contains("groupHeaderHeight = "));
        assertFalse(serialized.contains("maxRankBlockWidth = "));
        assertFalse(serialized.contains("interaction ="));
        assertFalse(serialized.contains("[interaction]"));
        assertFalse(serialized.contains("treeAppearance ="));
        assertFalse(serialized.contains("advancedLayout ="));
        assertFalse(serialized.contains("resetTreeAppearance"));
    }

    @Test
    void serverGroupsDoNotChangePersistedFieldPaths() {
        String serialized = ConfigApi.serializeConfig(new BlueprintConfig()).get();

        assertTrue(serialized.contains("version = 4"));
        assertTrue(serialized.contains("balancePreset = \"BALANCED\""));
        assertTrue(serialized.contains("enableBlueprints = true"));
        assertTrue(serialized.contains("researchPointCap = 10000"));
        assertTrue(serialized.contains("blueprintSpawnChance = "));
        assertTrue(serialized.contains("progressionPreset = \"TIERED_RESEARCH_AND_CRAFTING\""));
        assertTrue(serialized.contains("fragmentPreset = \"TARGETED_RESEARCH_BOOST\""));
        assertTrue(serialized.contains("ammoCraftingStrategy = \"PROFILE\""));
        assertTrue(serialized.contains("attachmentCraftingStrategy = \"PROFILE\""));
        assertTrue(serialized.contains("exactCraftingOverrides ="));
        assertFalse(serialized.contains("generalProgression ="));
        assertFalse(serialized.contains("[generalProgression]"));
        assertFalse(serialized.contains("discoveryAndLoot ="));
        assertFalse(serialized.contains("researchAndPoints ="));
        assertFalse(serialized.contains("startingAccess ="));
        assertFalse(serialized.contains("advanced ="));
    }

    @Test
    void versionTwoServerFileMigratesToClassicWithoutChangingDormantCustomValues() {
        BlueprintConfig migrated = ConfigApi.deserializeConfig(
                new BlueprintConfig(),
                """
                        version = 2
                        customEnforceResearchTiers = false
                        customEnforceCraftingTiers = true
                        tierOneFragmentThreshold = 7
                        """)
                .get();
        migrated.update(2);

        assertEquals(ResearchProgressionPreset.CLASSIC, migrated.progressionPreset.get());
        assertFalse(migrated.researchFeatureSnapshot().enforceResearchTiers());
        assertFalse(migrated.researchFeatureSnapshot().enforceCraftingTiers());
        assertFalse(migrated.customEnforceResearchTiers.get());
        assertTrue(migrated.customEnforceCraftingTiers.get());
        assertEquals(7, migrated.tierOneFragmentThreshold.get());
        assertEquals(BlueprintFragmentPreset.DISABLED, migrated.fragmentPreset.get());
        assertFalse(migrated.researchFeatureSnapshot().fragmentPolicy(
                BlueprintFragmentProfilePolicy.DEFAULT,
                new ResourceLocation("test", "legacy_target"),
                ResearchWorkbenchTier.TIER_1,
                Optional.empty()).enabled());
        assertEquals(AmmoCraftingStrategy.PROFILE, migrated.ammoCraftingStrategy.get());
        assertEquals(AttachmentCraftingStrategy.PROFILE,
                migrated.attachmentCraftingStrategy.get());
        assertTrue(migrated.researchFeatureSnapshot().craftingPolicy()
                .exactOverrides().isEmpty());
    }

    @Test
    void versionThreeServerFilePreservesProfileCraftingBehavior() {
        BlueprintConfig migrated = ConfigApi.deserializeConfig(
                new BlueprintConfig(),
                "version = 3\nprogressionExemptKinds = [ \"gun\" ]\n").get();
        migrated.update(3);

        assertEquals(AmmoCraftingStrategy.PROFILE, migrated.ammoCraftingStrategy.get());
        assertEquals(AttachmentCraftingStrategy.PROFILE,
                migrated.attachmentCraftingStrategy.get());
        assertTrue(migrated.researchFeatureSnapshot().craftingPolicy()
                .unrestrictedKinds().isEmpty());
        assertTrue(migrated.researchFeatureSnapshot().craftingPolicy()
                .disabledKinds().isEmpty());
        assertEquals(Set.of(com.gamergaming.taczweaponblueprints.item.BlueprintKind.GUN),
                migrated.accessSnapshot().progressionExemptKinds());
    }

    @Test
    void versionZeroClientFileKeepsItsFlatCustomLayoutValues() {
        ResearchTreeClientConfig migrated = ConfigApi.deserializeConfig(
                new ResearchTreeClientConfig(),
                "version = 0\nnodeGap = 47\ninterGroupGap = 73\n").get();
        // deserializeConfig is the low-level parser; the registered file loader
        // supplies the serialized version to this hook afterward.
        migrated.update(0);

        assertEquals(ResearchTreeLayoutPreset.CUSTOM, migrated.layoutPreset.get());
        assertEquals(47, migrated.layoutPolicy().nodeGap());
        assertEquals(73, migrated.layoutPolicy().interGroupGap());
    }

    @Test
    void versionTwoClientFileReceivesTheAutomaticMinimapDefault() {
        ResearchTreeClientConfig migrated = ConfigApi.deserializeConfig(
                new ResearchTreeClientConfig(),
                "version = 2\nlayoutPreset = \"SPACIOUS\"\n").get();
        migrated.update(2);

        assertEquals(ResearchTreeLayoutPreset.SPACIOUS, migrated.layoutPreset.get());
        assertEquals(ResearchTreeMinimapMode.AUTOMATIC, migrated.minimapMode());
    }

    @Test
    void versionZeroServerFileKeepsItsFlatCustomDiscoveryValues() {
        BlueprintConfig migrated = ConfigApi.deserializeConfig(
                new BlueprintConfig(),
                "version = 0\nblueprintSpawnChance = 0.73\nminBlueprints = 4\nmaxBlueprints = 7\n")
                .get();
        migrated.update(0);

        assertEquals(BlueprintBalancePreset.CUSTOM, migrated.balancePreset.get());
        assertEquals(0.73, migrated.balanceSettings().lootChance());
        assertEquals(4, migrated.balanceSettings().minimumLootRolls());
        assertEquals(7, migrated.balanceSettings().maximumLootRolls());
    }

    @Test
    void versionOneOversizedPointCapIsBoundedWithoutAffectingTheStorageLimit() {
        BlueprintConfig migrated = ConfigApi.deserializeConfig(
                new BlueprintConfig(),
                "version = 1\nresearchPointCap = 1000000\n").get();
        migrated.update(1);

        assertEquals(
                BlueprintConfig.MAX_CONFIGURED_RESEARCH_POINT_CAP,
                migrated.progressionSnapshot().pointCap());
    }

    @Test
    void legacyExemptionArraysLoadIntoTheMultiChoiceEditors() {
        BlueprintConfig migrated = ConfigApi.deserializeConfig(
                new BlueprintConfig(),
                """
                        version = 2
                        progressionExemptKinds = [ "gun", "attachment" ]
                        progressionExemptItemTypes = [ "pistol", "pack_defined_type" ]
                        """)
                .get();
        migrated.update(2);

        assertEquals(List.of("gun", "attachment"), migrated.progressionExemptKinds.get());
        assertEquals(List.of("pistol", "pack_defined_type"), migrated.progressionExemptItemTypes.get());
        assertEquals(
                2,
                migrated.accessSnapshot().progressionExemptKinds().size());
        assertTrue(migrated.accessSnapshot().progressionExemptItemTypes().contains("pack_defined_type"));
        assertTrue(migrated.progressionExemptItemTypes.availableChoicesSnapshot().contains("rifle"));
        assertTrue(migrated.progressionExemptItemTypes.availableChoicesSnapshot().contains("pack_defined_type"));

        String serialized = ConfigApi.serializeConfig(migrated).get();
        BlueprintConfig roundTripped = ConfigApi.deserializeConfig(new BlueprintConfig(), serialized).get();
        roundTripped.update(4);
        assertEquals(List.of("gun", "attachment"), roundTripped.progressionExemptKinds.get());
        assertEquals(
                List.of("pistol", "pack_defined_type"),
                roundTripped.progressionExemptItemTypes.get());
    }

    @Test
    void autocompleteIdCollectionsPreserveValidUnavailablePackEntries() {
        BlueprintConfig migrated = ConfigApi.deserializeConfig(
                new BlueprintConfig(),
                """
                        version = 2
                        startingBlueprints = [ "tacz:ak47", "futurepack:prototype" ]
                        progressionExemptBlueprints = [ "futurepack:starter_ammo" ]
                        gunBlacklist = [ "futurepack:prototype" ]
                        ammoBlacklist = [ "futurepack:prototype_round" ]
                        attachmentBlacklist = [ "futurepack:prototype_scope" ]
                        exactCraftingOverrides = { "futurepack:prototype" = "TIER_3" }
                        """)
                .get();
        migrated.update(2);

        assertTrue(migrated.startingBlueprints.contains("tacz:ak47"));
        assertTrue(migrated.startingBlueprints.contains("futurepack:prototype"));
        assertTrue(migrated.progressionExemptBlueprints.contains("futurepack:starter_ammo"));
        assertTrue(migrated.gunBlacklist.contains("futurepack:prototype"));
        assertTrue(migrated.ammoBlacklist.contains("futurepack:prototype_round"));
        assertTrue(migrated.attachmentBlacklist.contains("futurepack:prototype_scope"));
        assertEquals(CraftingAccessOverride.TIER_3,
                migrated.exactCraftingOverrides.get("futurepack:prototype"));

        String serialized = ConfigApi.serializeConfig(migrated).get();
        BlueprintConfig roundTripped = ConfigApi.deserializeConfig(new BlueprintConfig(), serialized).get();
        roundTripped.update(4);
        assertEquals(Set.copyOf(migrated.startingBlueprints), Set.copyOf(roundTripped.startingBlueprints));
        assertEquals(
                Set.copyOf(migrated.progressionExemptBlueprints),
                Set.copyOf(roundTripped.progressionExemptBlueprints));
        assertEquals(Set.copyOf(migrated.gunBlacklist), Set.copyOf(roundTripped.gunBlacklist));
        assertEquals(Set.copyOf(migrated.ammoBlacklist), Set.copyOf(roundTripped.ammoBlacklist));
        assertEquals(
                Set.copyOf(migrated.attachmentBlacklist),
                Set.copyOf(roundTripped.attachmentBlacklist));
        assertEquals(CraftingAccessOverride.TIER_3,
                roundTripped.exactCraftingOverrides.get("futurepack:prototype"));
    }
}
