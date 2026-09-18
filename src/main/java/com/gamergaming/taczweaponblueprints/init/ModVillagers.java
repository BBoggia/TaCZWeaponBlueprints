package com.gamergaming.taczweaponblueprints.init;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.google.common.collect.ImmutableSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

public final class ModVillagers {
    private static final DeferredRegister<PoiType> POIS = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, TaCZWeaponBlueprints.MODID);
    private static final DeferredRegister<VillagerProfession> PROFESSIONS = DeferredRegister.create(Registries.VILLAGER_PROFESSION, TaCZWeaponBlueprints.MODID);
    public static final RegistryObject<PoiType> GUNSMITH_POI = POIS.register("gunsmith",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.GUNSMITH_STATION.get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final RegistryObject<PoiType> BALLISTICS_RESEARCHER_POI = POIS.register("ballistics_researcher",
            () -> new PoiType(ImmutableSet.copyOf(ModBlocks.BALLISTICS_DESK.get().getStateDefinition().getPossibleStates()), 1, 1));
    public static final RegistryObject<VillagerProfession> GUNSMITH = PROFESSIONS.register("gunsmith",
            () -> new VillagerProfession("gunsmith", h -> h.is(GUNSMITH_POI.getKey()), h -> h.is(GUNSMITH_POI.getKey()),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_WEAPONSMITH));
    public static final RegistryObject<VillagerProfession> BALLISTICS_RESEARCHER = PROFESSIONS.register("ballistics_researcher",
            () -> new VillagerProfession("ballistics_researcher", h -> h.is(BALLISTICS_RESEARCHER_POI.getKey()), h -> h.is(BALLISTICS_RESEARCHER_POI.getKey()),
                    ImmutableSet.of(), ImmutableSet.of(), SoundEvents.VILLAGER_WORK_LIBRARIAN));
    private ModVillagers() {}
    public static void register(IEventBus bus) { POIS.register(bus); PROFESSIONS.register(bus); }
    public static boolean owns(VillagerProfession profession) {
        return profession == GUNSMITH.get() || profession == BALLISTICS_RESEARCHER.get();
    }
}
