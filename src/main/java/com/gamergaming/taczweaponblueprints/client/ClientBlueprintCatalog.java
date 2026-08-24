package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.mixin.ICreativeModeTabsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class ClientBlueprintCatalog {
    private ClientBlueprintCatalog() {
    }

    /**
     * Forces Minecraft to rebuild creative-tab contents on the next normal tab
     * refresh. This covers both a tab screen that is already open and one opened
     * after the synchronized blueprint catalog arrives.
     */
    public static void invalidateCreativeTabs() {
        ICreativeModeTabsAccessor.setCachedParameters(null);
    }

    public static void refreshOpenGunSmithScreen() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.screen instanceof IBlueprintRecipeScreen refreshable) {
            refreshable.taczweaponblueprints$refreshRecipes();
        }
    }
}
