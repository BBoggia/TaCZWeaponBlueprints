package com.gamergaming.taczweaponblueprints.villager.stock;

import com.gamergaming.taczweaponblueprints.compat.magazines.*;
import com.gamergaming.taczweaponblueprints.item.BlueprintFragmentItem;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass;
import com.tacz.guns.api.item.*;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;

/** New output stacks only: copying recipe attachments never grants their loaded ammunition. */
public final class StockOutputs {
    private StockOutputs(){}
    public static ItemStack create(StockPlan.Entry entry){
        ItemStack output=switch(entry.kind()){
            case CURRENCY,RESEARCH_DATA -> BuiltInRegistries.ITEM.containsKey(entry.target())?new ItemStack(BuiltInRegistries.ITEM.get(entry.target())):ItemStack.EMPTY;
            case ANALYSIS_FRAGMENTS -> BlueprintFragmentItem.createAnalysis(entry.target(),AnalysisRewardClass.PROTECTED_PREREQUISITES);
            case BLUEPRINT -> com.gamergaming.taczweaponblueprints.item.BlueprintItem.createBlueprint(entry.target().toString(), com.gamergaming.taczweaponblueprints.item.BlueprintProvenance.traded());
            case MAGAZINE -> {var family=MagazineCatalog.INSTANCE.publication().catalog().families().get(entry.target());yield family==null?ItemStack.EMPTY:family.output();}
            default -> StockCatalog.recipe(entry.target());
        };
        if(output.isEmpty()||entry.count()>output.getMaxStackSize())return ItemStack.EMPTY;
        output.setCount(entry.count());
        if(entry.kind()==com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Kind.GUN){
            var gun=IGun.getIGunOrNull(output);if(gun==null||!gun.getGunId(output).equals(entry.target()))return ItemStack.EMPTY;
            gun.setCurrentAmmoCount(output,0);gun.setBulletInBarrel(output,false);gun.setDummyAmmoAmount(output,0);
            // This pinned adapter owns removal of an inserted magazine from a newly manufactured copy.
            InsertedMagazineGuard.clearNewOutput(output);
            if(gun.getCurrentAmmoCount(output)!=0||gun.hasBulletInBarrel(output)||gun.getDummyAmmoAmount(output)!=0
                    ||InsertedMagazineGuard.hasInsertedMagazine(output))return ItemStack.EMPTY;
            if(!com.gamergaming.taczweaponblueprints.item.PhysicalWeaponProvenance.stampTraded(output,entry.definition()))return ItemStack.EMPTY;
        }
        if(entry.kind()==com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Kind.AMMO){var item=IAmmo.getIAmmoOrNull(output);if(item==null||!entry.target().equals(item.getAmmoId(output)))return ItemStack.EMPTY;}
        if(entry.kind()==com.gamergaming.taczweaponblueprints.villager.trade.TradeDefinition.Kind.ATTACHMENT){var item=IAttachment.getIAttachmentOrNull(output);if(item==null||!entry.target().equals(item.getAttachmentId(output)))return ItemStack.EMPTY;}
        return output;
    }
}
