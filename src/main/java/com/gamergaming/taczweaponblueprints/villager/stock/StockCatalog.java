package com.gamergaming.taczweaponblueprints.villager.stock;

import java.util.*;
import com.gamergaming.taczweaponblueprints.compat.magazines.*;
import com.gamergaming.taczweaponblueprints.init.ModConfigs;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.gamergaming.taczweaponblueprints.resource.research.*;
import com.gamergaming.taczweaponblueprints.villager.trade.*;
import com.tacz.guns.api.item.IGun;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponEvidenceManager;
import com.gamergaming.taczweaponblueprints.research.tree.automatic.tacz.AutomaticWeaponPlacementCandidateManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One shared snapshot per publication/config change, not one catalog scan per villager tick. */
public final class StockCatalog implements StockPlanner.Facts {
    private static List<Object> cachedKey;
    private static StockCatalog cached;
    private boolean coherent=true;
    public boolean coherent(){return coherent&&!StockPublicationCoordinator.pending();}
    public final TradeRuntime.Inspection inspection;
    public final Map<ResourceLocation,List<TradeResolver.Row>> alternatives;
    private final Map<ResourceLocation,StockPlanner.Supply> supplies=new HashMap<>();
    private final Map<String,Boolean> attachments=new HashMap<>();
    private final Map<StockPlan.Entry,ItemStack> outputs=new HashMap<>();
    private final Map<StockPlan.Entry,List<Object>> merchandiseTerms=new HashMap<>();
    private final Set<TradeDefinition.Profession> unreachableReported=EnumSet.noneOf(TradeDefinition.Profession.class);
    private final Map<com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Profession,Boolean> acquisition=new EnumMap<>(com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Profession.class);
    private final Map<ResourceLocation,ResourceLocation> ammo;
    private final TaCZMagazinesAdapter.Capture magazines;
    private StockCatalog(){
        var view=TradeRuntime.view();inspection=view.inspection();alternatives=view.alternatives();
        ammo=BlueprintAmmoAssociationManager.INSTANCE.snapshotForCatalogRevision(inspection.catalogRevision())
                .map(BlueprintAmmoAssociationSnapshot::ammoByGun).orElse(Map.of());
        magazines=MagazineCatalog.INSTANCE.publication().catalog();
    }
    private static List<Object> key(){
        var c=ModConfigs.BLUEPRINT;
        return List.of(StockPublicationCoordinator.pending(),TradeDataManager.INSTANCE.publication(),BlueprintDataManager.SERVER.catalogRevision(),
                BlueprintResearchDataManager.INSTANCE.revision(),BlueprintProgressionPolicyManager.INSTANCE.publication().identity(),
                BlueprintAmmoAssociationManager.INSTANCE.publication().revision(),AutomaticWeaponEvidenceManager.INSTANCE.publication().revision(),
                AutomaticWeaponPlacementCandidateManager.INSTANCE.publication().revision(),
                MagazineCatalog.INSTANCE.publication().revision(),MagazineCatalog.INSTANCE.publication().catalog().status(),c.villagerSettings(),c.currencyPolicy(),c.researchFeatureSnapshot(),
                c.progressionSnapshot(),c.accessSnapshot(),c.dataSettings(),c.awardSnapshot(),
                com.gamergaming.taczweaponblueprints.resource.award.ResearchPointAwardDataManager.INSTANCE.revision(),c.gunBlacklist.get(),c.ammoBlacklist.get(),c.attachmentBlacklist.get());
    }
    public static StockCatalog current(){
        var key=key();
        if(cached==null||!key.equals(cachedKey)){
            var next=new StockCatalog();
            next.coherent=key.equals(key());
            cached=next;cachedKey=next.coherent?key:null;
        }
        return cached;
    }
    public boolean canAcquire(com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Profession profession){
        if(!coherent())return false;
        return acquisition.computeIfAbsent(profession,p->{var result=StockPlanner.create(p,alternatives,this,0);return !result.pending()&&result.plan().reachable();});
    }
    public void reportUnreachable(StockPlan plan){
        if(!plan.reachable()&&unreachableReported.add(plan.profession()))
            com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints.LOGGER.warn(
                    "Villager {} stock cannot progress from Novice after pairing/fallbacks; no offers issued. Excluded slots: {}",
                    plan.profession(),plan.excluded());
    }
    public static void clear(){cached=null;cachedKey=null;}
    @Override public Optional<ResourceLocation> ammo(ResourceLocation gun){return Optional.ofNullable(ammo.get(gun));}
    @Override public StockPlanner.Supply magazine(ResourceLocation gun){return supplies.computeIfAbsent(gun,this::resolveMagazine);}
    private StockPlanner.Supply resolveMagazine(ResourceLocation gun){
        if(magazines.status()==TaCZMagazinesAdapter.Status.ABSENT)return StockPlanner.Supply.none();
        if(magazines.status()==TaCZMagazinesAdapter.Status.WAITING_FOR_RECIPES)return new StockPlanner.Supply(StockPlanner.SupplyStatus.PENDING,Optional.empty());
        if(magazines.status()!=TaCZMagazinesAdapter.Status.READY)return unavailable();
        try{
            var family=MagazineDiscoveryEvents.requiredFamily(gun);if(family.isEmpty())return StockPlanner.Supply.none();
            var descriptor=magazines.families().get(family.orElseThrow());
            if(descriptor==null||descriptor.extensionLevel()!=0||!descriptor.compatibleGuns().contains(gun)
                    ||!ammo(gun).filter(descriptor.ammoId()::equals).isPresent())return unavailable();
            return new StockPlanner.Supply(StockPlanner.SupplyStatus.READY,family);
        }catch(RuntimeException|LinkageError failure){return unavailable();}
    }
    private static StockPlanner.Supply unavailable(){return new StockPlanner.Supply(StockPlanner.SupplyStatus.UNAVAILABLE,Optional.empty());}
    @Override public boolean attachmentFits(ResourceLocation gun,ResourceLocation attachment){
        String key=gun+"|"+attachment;
        // Bound this compatibility cache independently of pack size or villager count.
        if(attachments.size()>=4096)attachments.clear();
        return attachments.computeIfAbsent(key,ignored->{
            var a=recipe(gun);var b=recipe(attachment);var accessor=IGun.getIGunOrNull(a);
            try{return accessor!=null&&!b.isEmpty()&&accessor.allowAttachment(a,b);}catch(RuntimeException failure){return false;}
        });
    }
    /** Expected merchandise is rebuilt once per publication, including changed recipe NBT. */
    public ItemStack output(StockPlan.Entry entry) {
        if(outputs.size()>=4096)outputs.clear();
        return outputs.computeIfAbsent(entry,StockOutputs::create).copy();
    }
    /** Only the selected product's effective dependencies, not unrelated publication counters. */
    public List<Object> merchandiseTerms(StockPlan.Entry entry){
        if(merchandiseTerms.size()>=4096)merchandiseTerms.clear();
        return merchandiseTerms.computeIfAbsent(entry,e->{
            if(e.kind()==com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Kind.GUN){
                var supply=magazine(e.target());var descriptor=supply.family().map(magazines.families()::get).orElse(null);
                return List.of(ammo(e.target()),supply,descriptor==null?List.of():List.of(descriptor.ammoId(),descriptor.capacity(),descriptor.extensionLevel(),descriptor.compatibleGuns().contains(e.target()),descriptor.ingredients()));
            }
            if(e.kind()==com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Kind.MAGAZINE){
                var d=magazines.families().get(e.target());return d==null?List.of():List.of(d.familyKey(),d.ammoId(),d.capacity(),d.extensionLevel(),d.compatibleGuns(),d.ingredients());
            }
            return List.of();
        });
    }
    public TradeResolver.Row row(StockPlan.Entry entry){
        return alternatives.getOrDefault(entry.template(),List.of()).stream().filter(entry::matches).findFirst().orElse(null);
    }
    public static ItemStack recipe(ResourceLocation id){
        var data=BlueprintDataManager.SERVER.catalogPublication().blueprints().get(id);
        return data==null||data.getRecipe()==null?ItemStack.EMPTY:data.getRecipe().getOutput().copy();
    }
}
