package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import java.util.Map;
import com.gamergaming.taczweaponblueprints.compat.magazines.TaCZMagazinesAdapter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class MagazineWorkbenchTabsTest {
    @Test
    void emptyInitialMagazineViewStillOffersANamespaceFilterWhenRecipesReturn() {
        var gunTab = ResourceLocation.parse("tacz:rifle");
        var gun = ResourceLocation.parse("tacz:gun/ak47");
        Map<ResourceLocation, List<ResourceLocation>> recipes = Map.of(gunTab, List.of(gun));
        var scaffold = MagazineWorkbenchTabs.namespaceScaffold(recipes, true);
        assertEquals(List.of(TaCZMagazinesAdapter.MAGAZINE_TAB), scaffold.get(TaCZMagazinesAdapter.MAGAZINE_TAB));
        assertSame(recipes.get(gunTab), scaffold.get(gunTab));
        assertEquals(Map.of(gunTab, List.of(gun)), recipes, "native recipe lists must never receive the namespace hint");
        assertSame(scaffold, MagazineWorkbenchTabs.namespaceScaffold(scaffold, true));
        assertSame(recipes, MagazineWorkbenchTabs.namespaceScaffold(recipes, false));
        assertEquals(1, MagazineWorkbenchTabs.namespaceScaffold(Map.of(), true).size());
    }
}
