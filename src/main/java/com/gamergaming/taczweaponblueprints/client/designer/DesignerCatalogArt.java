package com.gamergaming.taczweaponblueprints.client.designer;

import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.google.gson.JsonObject;
import com.tacz.guns.api.TimelessAPI;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import java.util.Optional;

/** Resolves capture references against local art only. Missing or reloading gun packs use a blueprint item. */
public final class DesignerCatalogArt {
    private DesignerCatalogArt(){}
    public record Art(Optional<ResourceLocation> texture,ItemStack fallback){
        public Art{fallback=fallback.copy();}
        @Override public ItemStack fallback(){return fallback.copy();}
    }
    public static Art resolve(ResourceLocation blueprint,JsonObject record){
        Optional<ResourceLocation> texture=Optional.empty();
        try{
            ResourceLocation slot=record.has("display_slot")&&!record.get("display_slot").isJsonNull()
                    ? ResourceLocation.tryParse(record.get("display_slot").getAsString()):blueprint;
            texture=switch(record.get("kind").getAsString()){
                case "gun" -> TimelessAPI.getGunDisplay(slot==null?blueprint:slot,blueprint).map(d->d.getSlotTexture());
                case "ammo" -> TimelessAPI.getClientAmmoIndex(blueprint).map(d->d.getSlotTextureLocation());
                case "attachment" -> TimelessAPI.getClientAttachmentIndex(blueprint).map(d->d.getSlotTexture());
                default -> Optional.empty();
            };
            texture=texture.filter(id->net.minecraft.client.Minecraft.getInstance().getResourceManager().getResource(id).isPresent());
        }catch(RuntimeException unavailable){texture=Optional.empty();}
        return new Art(texture,BlueprintItem.createBlueprint(blueprint.toString()));
    }
}
