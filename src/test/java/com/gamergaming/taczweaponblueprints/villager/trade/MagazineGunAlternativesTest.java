package com.gamergaming.taczweaponblueprints.villager.trade;

import java.util.*;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.*;

class MagazineGunAlternativesTest {
    private static ResourceLocation id(String value){return new ResourceLocation("test",value);}
    private static TradeDefinition gun(String name,Role role,boolean enabled,String target,Selection selection,String... fallbacks){
        return new TradeDefinition(id(name),Profession.GUNSMITH,1,id(name),role,enabled,
                new Output(Kind.GUN,1,1,selection,List.of(new Choice(id(target),1))),
                new Input(Optional.empty(),4),Optional.empty(),4,2,.05F,Optional.empty(),List.of(),
                Arrays.stream(fallbacks).map(MagazineGunAlternativesTest::id).toList());
    }
    private static TradeSnapshot snapshot(TradeDefinition... definitions){
        var map=new TreeMap<ResourceLocation,TradeDefinition>();for(var d:definitions)map.put(d.id(),d);return new TradeSnapshot(map);
    }
    private static Set<ResourceLocation> candidates(TradeSnapshot snapshot,TradeResolverTest.Content content){
        return TradeResolver.magazineGuns(snapshot,TradeResolverTest.defaults(),id("coin"),content,Selection.STARTER);
    }
    @Test void discoversUsableReachableFallbackWithoutAdmittingOrphans(){
        var root=gun("root",Role.CORE,true,"missing",Selection.STARTER,"fallback");
        var fallback=gun("fallback",Role.FALLBACK,true,"working",Selection.STARTER);
        var orphan=gun("orphan",Role.FALLBACK,true,"orphan_gun",Selection.STARTER);
        var content=new TradeResolverTest.Content();content.blocked.add(id("missing"));
        assertEquals(Set.of(id("working")),candidates(snapshot(root,fallback,orphan),content));
    }
    @Test void disabledBranchStopsDescendantsButAnEnabledSharedPathStillWorks(){
        var root=gun("root",Role.CORE,true,"root_gun",Selection.STARTER,"disabled");
        var disabled=gun("disabled",Role.FALLBACK,false,"disabled_gun",Selection.STARTER,"leaf");
        var leaf=gun("leaf",Role.FALLBACK,true,"leaf_gun",Selection.STARTER);
        var content=new TradeResolverTest.Content();
        assertEquals(Set.of(id("root_gun")),candidates(snapshot(root,disabled,leaf),content));
        root=gun("root",Role.CORE,true,"root_gun",Selection.STARTER,"disabled","leaf");
        assertEquals(Set.of(id("root_gun"),id("leaf_gun")),candidates(snapshot(root,disabled,leaf),content));
        assertTrue(candidates(snapshot(gun("root",Role.CORE,false,"root_gun",Selection.STARTER,"leaf"),leaf),content).isEmpty());
    }
    @Test void currentPermissionsPriceAndSelectionRestrictFallbackSupplies(){
        var root=gun("root",Role.CORE,true,"missing",Selection.STARTER,"fallback");
        var fallback=gun("fallback",Role.FALLBACK,true,"working",Selection.EXPERT);
        var content=new TradeResolverTest.Content();content.blocked.add(id("missing"));
        assertTrue(candidates(snapshot(root,fallback),content).isEmpty());
        fallback=gun("fallback",Role.FALLBACK,true,"working",Selection.STARTER);
        var data=snapshot(root,fallback);content.ready=false;assertTrue(candidates(data,content).isEmpty());
        content.ready=true;content.limit=1;assertTrue(candidates(data,content).isEmpty());
        content.limit=64;content.blocked.add(id("working"));assertTrue(candidates(data,content).isEmpty());
        content.blocked.clear();var c=new com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig();
        c.enableGunsmith.validateAndSet(false);c.initializeLoadedSnapshots();
        assertTrue(TradeResolver.magazineGuns(data,c.villagerSettings(),id("coin"),content,Selection.STARTER).isEmpty());
        c.enableGunsmith.validateAndSet(true);c.villagerGunSales.validateAndSet(false);c.initializeLoadedSnapshots();
        assertTrue(TradeResolver.magazineGuns(data,c.villagerSettings(),id("coin"),content,Selection.STARTER).isEmpty());
    }
    @Test void missingRequiredModAllowsAReachableFallbackButDoesNotAuthorizeItsOwnGun(){
        var root=gun("root",Role.CORE,true,"root_gun",Selection.STARTER,"fallback");
        root=new TradeDefinition(root.id(),root.profession(),root.level(),root.slot(),root.role(),true,root.output(),root.primary(),root.secondary(),root.uses(),root.xp(),root.demandMultiplier(),root.minimumBasisPoints(),List.of("absent_mod"),root.fallbacks());
        var fallback=gun("fallback",Role.FALLBACK,true,"working",Selection.STARTER);
        assertEquals(Set.of(id("working")),candidates(snapshot(root,fallback),new TradeResolverTest.Content()));
    }
}
