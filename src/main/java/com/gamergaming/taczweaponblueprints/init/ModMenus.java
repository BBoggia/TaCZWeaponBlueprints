package com.gamergaming.taczweaponblueprints.init;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TaCZWeaponBlueprints.MODID);

    public static final DeferredHolder<MenuType<?>, MenuType<ResearchBenchMenu>> RESEARCH_BENCH = MENUS.register(
            "research_bench",
            () -> IMenuTypeExtension.create(ResearchBenchMenu::new));

    public static final DeferredHolder<MenuType<?>, MenuType<BlueprintRecyclerMenu>> BLUEPRINT_RECYCLER =
            MENUS.register(
                    "blueprint_recycler",
                    () -> IMenuTypeExtension.create(BlueprintRecyclerMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
