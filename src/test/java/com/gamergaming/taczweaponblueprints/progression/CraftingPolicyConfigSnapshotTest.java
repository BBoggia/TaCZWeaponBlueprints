package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintKind;
import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier;

import net.minecraft.resources.ResourceLocation;

class CraftingPolicyConfigSnapshotTest {
    @Test
    void snapshotNormalizesSelectorsAndDefensivelyCopiesOverrides() {
        Map<ResourceLocation, CraftingAccessOverride> overrides = new LinkedHashMap<>();
        overrides.put(new ResourceLocation("test:rifle"), CraftingAccessOverride.TIER_3);

        CraftingPolicyConfigSnapshot snapshot = new CraftingPolicyConfigSnapshot(
                AmmoCraftingStrategy.LINKED_WEAPON,
                AttachmentCraftingStrategy.DISABLED,
                ResearchWorkbenchTier.TIER_2,
                Set.of(BlueprintKind.AMMO),
                Set.of("SMG"),
                Set.of(BlueprintKind.ATTACHMENT),
                Set.of("SCOPE"),
                overrides);
        overrides.clear();

        assertEquals(Set.of("smg"), snapshot.unrestrictedItemTypes());
        assertEquals(Set.of("scope"), snapshot.disabledItemTypes());
        assertEquals(
                CraftingAccessOverride.TIER_3,
                snapshot.exactOverrides().get(new ResourceLocation("test:rifle")));
        assertThrows(
                UnsupportedOperationException.class,
                () -> snapshot.disabledKinds().add(BlueprintKind.GUN));
    }

    @Test
    void snapshotRejectsInvalidOrUnboundedSelectorInput() {
        assertThrows(IllegalArgumentException.class, () -> new CraftingPolicyConfigSnapshot(
                AmmoCraftingStrategy.PROFILE,
                AttachmentCraftingStrategy.PROFILE,
                ResearchWorkbenchTier.TIER_1,
                Set.of(),
                Set.of("not a subgroup"),
                Set.of(),
                Set.of(),
                Map.of()));

        Set<String> oversized = IntStream.range(0, CraftingPolicyConfigSnapshot.MAX_SELECTORS + 1)
                .mapToObj(index -> "type" + index)
                .collect(Collectors.toSet());
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new CraftingPolicyConfigSnapshot(
                        AmmoCraftingStrategy.PROFILE,
                        AttachmentCraftingStrategy.PROFILE,
                        ResearchWorkbenchTier.TIER_1,
                        Set.of(),
                        oversized,
                        Set.of(),
                        Set.of(),
                        Map.of()));
        assertTrue(exception.getMessage().contains("too many selectors"));
    }
}
