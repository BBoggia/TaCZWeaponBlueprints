package com.gamergaming.taczweaponblueprints.progression;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.gamergaming.taczweaponblueprints.item.BlueprintData;
import com.gamergaming.taczweaponblueprints.item.BlueprintKind;

import net.minecraft.resources.ResourceLocation;

class BlueprintAccessConfigSnapshotTest {
    @Test
    void exactKindAndItemTypeSelectorsFormOneAdditiveExemptionPolicy() {
        ResourceLocation exact = id("addon:exact");
        ResourceLocation rifle = id("addon:rifle");
        ResourceLocation ammo = id("addon:ammo");
        ResourceLocation scope = id("addon:scope");
        BlueprintAccessConfigSnapshot config = new BlueprintAccessConfigSnapshot(
                Set.of(exact),
                Set.of(BlueprintKind.AMMO),
                Set.of("rifle"),
                Set.of(id("addon:starter")));

        assertTrue(config.isProgressionExempt(exact, blueprint(exact, "pistol")));
        assertTrue(config.isProgressionExempt(rifle, blueprint(rifle, "RIFLE")));
        assertTrue(config.isProgressionExempt(ammo, blueprint(ammo, "ammo")));
        assertFalse(config.isProgressionExempt(scope, blueprint(scope, "scope")));
        assertTrue(config.hasProgressionExemptions());
    }

    @Test
    void exemptRecipesAreDerivedFromTheCurrentCatalogWithoutCreatingKnowledge() {
        ResourceLocation rifle = id("addon:rifle");
        ResourceLocation pistol = id("addon:pistol");
        Map<ResourceLocation, BlueprintData> catalog = Map.of(
                rifle, blueprint(rifle, "rifle"),
                pistol, blueprint(pistol, "pistol"));
        BlueprintAccessConfigSnapshot config = new BlueprintAccessConfigSnapshot(
                Set.of(), Set.of(), Set.of("rifle"), Set.of());

        assertTrue(BlueprintProgressionAccess.exemptBlueprintIds(config, catalog)
                .contains(rifle));
        assertFalse(BlueprintProgressionAccess.exemptBlueprintIds(config, catalog)
                .contains(pistol));
        assertTrue(BlueprintProgressionAccess.exemptRecipeIds(config, catalog)
                .contains("test:recipe/rifle"));
    }

    private static BlueprintData blueprint(ResourceLocation id, String type) {
        return new BlueprintData(
                id.toString(),
                "item.test.name",
                "item.test.tooltip",
                new ResourceLocation("test", "recipe/" + id.getPath()),
                null,
                type,
                id("test:display"));
    }

    private static ResourceLocation id(String value) {
        return new ResourceLocation(value);
    }
}
