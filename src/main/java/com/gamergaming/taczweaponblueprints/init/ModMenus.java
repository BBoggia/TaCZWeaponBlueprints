package com.gamergaming.taczweaponblueprints.init;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.menu.ResearchBenchMenu;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, TaCZWeaponBlueprints.MODID);

    public static final RegistryObject<MenuType<ResearchBenchMenu>> RESEARCH_BENCH = MENUS.register(
            "research_bench",
            () -> IForgeMenuType.create(ResearchBenchMenu::new));

    public static final RegistryObject<MenuType<BlueprintRecyclerMenu>> BLUEPRINT_RECYCLER =
            MENUS.register(
                    "blueprint_recycler",
                    () -> IForgeMenuType.create(BlueprintRecyclerMenu::new));

    private ModMenus() {
    }

    public static void register(IEventBus eventBus) {
        MENUS.register(eventBus);
    }
}
