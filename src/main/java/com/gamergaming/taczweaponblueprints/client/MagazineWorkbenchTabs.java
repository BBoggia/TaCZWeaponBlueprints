package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

import com.gamergaming.taczweaponblueprints.compat.magazines.MagazineCraftingAccess;
import com.gamergaming.taczweaponblueprints.compat.magazines.TaCZMagazinesAdapter;
import com.tacz.guns.resource.pojo.data.block.TabConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;

/** Decorates the screen's tab list without mutating shared gun-pack block data. */
public final class MagazineWorkbenchTabs {
    private MagazineWorkbenchTabs() { }

    public static List<TabConfig> forWorkbench(ResourceLocation workstation, List<TabConfig> authoredTabs) {
        if (!supported(workstation)
                || authoredTabs.stream().anyMatch(tab -> TaCZMagazinesAdapter.MAGAZINE_TAB.equals(tab.id()))) {
            return authoredTabs;
        }
        var icon = ForgeRegistries.ITEMS.getValue(new ResourceLocation("taczmagazines:tab_icon"));
        if (icon == null || icon == Items.AIR) return authoredTabs;
        var decorated = new ArrayList<>(authoredTabs);
        decorated.add(new TabConfig(TaCZMagazinesAdapter.MAGAZINE_TAB,
                "taczmagazines.tab.magazines", new ItemStack(icon)));
        return decorated;
    }

    public static boolean supported(ResourceLocation workstation) {
        return MagazineCraftingAccess.supportsWorkbench(workstation)
                && ModList.get().getModContainerById(TaCZMagazinesAdapter.MOD_ID)
                        .map(container -> TaCZMagazinesAdapter.SUPPORTED_VERSION.equals(
                                container.getModInfo().getVersion().toString())).orElse(false);
    }

    public static Map<ResourceLocation, List<ResourceLocation>> namespaceScaffold(ResourceLocation workstation,
            Map<ResourceLocation, List<ResourceLocation>> recipes) {
        return namespaceScaffold(recipes, supported(workstation));
    }

    static Map<ResourceLocation, List<ResourceLocation>> namespaceScaffold(
            Map<ResourceLocation, List<ResourceLocation>> recipes, boolean supported) {
        if (!supported || recipes.values().stream().flatMap(List::stream)
                .anyMatch(id -> TaCZMagazinesAdapter.MOD_ID.equals(id.getNamespace()))) return recipes;
        var scaffold = new LinkedHashMap<>(recipes);
        var magazineIds = new ArrayList<>(recipes.getOrDefault(TaCZMagazinesAdapter.MAGAZINE_TAB, List.of()));
        // GunPackList's constructor reads only namespaces from this copy. The tab
        // ID seeds a checkbox even when no compatible recipes exist on first open.
        // This map never enters recipe classification, selection, or crafting.
        magazineIds.add(TaCZMagazinesAdapter.MAGAZINE_TAB);
        scaffold.put(TaCZMagazinesAdapter.MAGAZINE_TAB, magazineIds);
        return scaffold;
    }
}
