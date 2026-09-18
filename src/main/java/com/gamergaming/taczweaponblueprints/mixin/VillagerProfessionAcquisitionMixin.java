package com.gamergaming.taczweaponblueprints.mixin;

import com.gamergaming.taczweaponblueprints.init.ModVillagers;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Profession;
import net.minecraft.core.GlobalPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import net.minecraft.world.entity.ai.behavior.declarative.BehaviorBuilder;
import net.minecraft.world.entity.ai.behavior.declarative.MemoryAccessor;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Gate new AI assignments only; saved/traded professions keep their held POI predicate. */
@Mixin(AssignProfessionFromJobSite.class)
public abstract class VillagerProfessionAcquisitionMixin {
    // Official and SRG names of the pinned 1.20.1 behavior callback.
    @Inject(method={"lambda$create$4","m_257167_"},remap=false,at=@At("HEAD"),cancellable=true)
    private static void availableProfession(BehaviorBuilder.Instance<Villager> instance,
            MemoryAccessor<?,GlobalPos> potential, MemoryAccessor<?,GlobalPos> held,
            ServerLevel level,Villager villager,long time,CallbackInfoReturnable<Boolean> ci){
        if(villager.getVillagerData().getProfession()!=VillagerProfession.NONE)return;
        var pos=villager.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).orElse(null);
        if(pos==null)return;
        var siteLevel=level.getServer().getLevel(pos.dimension());if(siteLevel==null)return;
        var site=siteLevel.getPoiManager().getType(pos.pos()).orElse(null);if(site==null)return;
        Profession kind;
        if(site.is(ModVillagers.GUNSMITH_POI.getKey()))kind=Profession.GUNSMITH;
        else if(site.is(ModVillagers.BALLISTICS_RESEARCHER_POI.getKey()))kind=Profession.BALLISTICS_RESEARCHER;
        else return;
        if(StockPublicationCoordinator.pending()||!StockCatalog.current().canAcquire(kind)){
            // Release before vanilla converts POTENTIAL_JOB_SITE into JOB_SITE. Merely
            // rejecting its profession predicate would strand the acquired POI ticket.
            villager.releasePoi(MemoryModuleType.POTENTIAL_JOB_SITE);
            potential.erase();
            ci.setReturnValue(false);
        }
    }
}
