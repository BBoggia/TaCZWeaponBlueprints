package com.gamergaming.taczweaponblueprints.villager;

import java.util.*;
import com.gamergaming.taczweaponblueprints.villager.stock.*;
import com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Kind;
import com.gamergaming.taczweaponblueprints.compat.magazines.MagazineCatalog;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.trading.*;

/** Presentation only. Contains current issued offers, never future stock or player knowledge. */
public final class VillagerTradeStatus {
    public enum Reason { AVAILABLE, CONFIGURATION, CONTENT, SUPPLIES, INVALID }
    public record Row(int index,long revision,String fingerprint,Reason reason,int flags,
            Optional<ResourceLocation> family,Optional<ResourceLocation> ammo,int capacity) {
        public Row {
            if(index<0||index>=256||revision<0||fingerprint==null||!fingerprint.matches("[a-f0-9]{64}")||reason==null||flags<0||flags>7
                    ||family==null||ammo==null||family.isPresent()!=ammo.isPresent()||capacity<0||capacity>65536
                    ||family.isPresent()!=(capacity>0))throw new IllegalArgumentException("Invalid merchant status row");
            family.ifPresent(Row::id);ammo.ifPresent(Row::id);
        }
        private static void id(ResourceLocation id){if(id.toString().length()>256)throw new IllegalArgumentException("Oversized merchant ID");}
    }
    private VillagerTradeStatus(){}
    public static String fingerprint(MerchantOffer offer,net.minecraft.core.HolderLookup.Provider registries){
        var tag=new net.minecraft.nbt.CompoundTag();
        tag.put("a",offer.getCostA().saveOptional(registries));tag.put("b",offer.getCostB().saveOptional(registries));tag.put("out",offer.getResult().saveOptional(registries));
        try{return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(tag.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException impossible){throw new IllegalStateException(impossible);}
    }
    public static List<Row> capture(MerchantOffers offers,net.minecraft.core.HolderLookup.Provider registries){
        if(offers.size()>256)return List.of();var rows=new ArrayList<Row>();var catalog=StockCatalog.current();
        for(int i=0;i<offers.size();i++){
            var offer=offers.get(i);var owned=(OwnedVillagerOffer)offer;var terms=owned.taczweaponblueprints$terms();if(terms==null)continue;
            var binding=owned.taczweaponblueprints$binding();var reason=terms.available()?Reason.AVAILABLE:Reason.INVALID;int flags=0;
            Optional<ResourceLocation> family=Optional.empty(),ammo=Optional.empty();int capacity=0;
            if(binding!=null){
                var entry=binding.entry();var kind=entry.kind();flags=kind==Kind.GUN?1:(kind==Kind.BLUEPRINT||kind==Kind.ANALYSIS_FRAGMENTS)?2:kind==Kind.MAGAZINE?4:0;
                if(!terms.available()){
                    var row=catalog.inspection.resolution().slots().get(entry.template());
                    reason=(row!=null&&!catalog.inspection.settings().enabled(row.profession()))
                            ||!catalog.inspection.settings().categories().contains(kind)?Reason.CONFIGURATION:catalog.row(entry)==null?Reason.CONTENT:Reason.SUPPLIES;
                }
                if(kind==Kind.MAGAZINE){var descriptor=MagazineCatalog.INSTANCE.publication().catalog().families().get(entry.target());
                    if(descriptor!=null){family=Optional.of(descriptor.familyKey());ammo=Optional.of(descriptor.ammoId());capacity=descriptor.capacity();}}
            }
            rows.add(new Row(i,terms.revision(),fingerprint(offer,registries),reason,flags,family,ammo,capacity));
        }
        return List.copyOf(rows);
    }
}
