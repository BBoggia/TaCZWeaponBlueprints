package com.gamergaming.taczweaponblueprints.compat.magazines;

import java.util.List;
import java.util.Set;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.tacz.guns.api.DefaultAssets;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/** One upstream family and its current empty-magazine recipe, not a learned blueprint. */
public record MagazineDescriptor(
        ResourceLocation familyKey,
        String upstreamFamily,
        ResourceLocation ammoId,
        int capacity,
        int extensionLevel,
        Set<ResourceLocation> compatibleGuns,
        ResourceLocation recipeId,
        ItemStack output,
        List<IngredientCost> ingredients) {

    public MagazineDescriptor {
        if (!TaCZMagazinesAdapter.familyKey(upstreamFamily).equals(familyKey)
                || !validId(ammoId) || DefaultAssets.EMPTY_AMMO_ID.equals(ammoId)
                || capacity < 1 || capacity > 65536
                || extensionLevel < 0 || extensionLevel > 3
                || compatibleGuns == null || compatibleGuns.isEmpty()
                || compatibleGuns.size() > TaCZMagazinesAdapter.MAX_LINKS
                || compatibleGuns.stream().anyMatch(id -> !validId(id) || DefaultAssets.EMPTY_GUN_ID.equals(id))
                || !validId(recipeId) || output == null || output.isEmpty()
                || output.getCount() != 1 || ingredients == null || ingredients.isEmpty()
                || ingredients.size() > 64) {
            throw new IllegalArgumentException("invalid magazine descriptor");
        }
        compatibleGuns = Set.copyOf(compatibleGuns);
        output = output.copy();
        ingredients = List.copyOf(ingredients);
    }

    private static boolean validId(ResourceLocation id) {
        return id != null && !id.getPath().isBlank()
                && id.toString().length() <= PlayerProgressionLimits.MAX_RESOURCE_ID_LENGTH;
    }

    /** Callers cannot mutate the cached upstream output or the published descriptor. */
    @Override
    public ItemStack output() {
        return output.copy();
    }

    /** Preserve item/tag/custom ingredient JSON without expanding tags to today's items. */
    public record IngredientCost(String ingredientJson, int count) {
        public IngredientCost {
            if (ingredientJson == null || ingredientJson.isBlank() || ingredientJson.length() > 16384
                    || count < 1 || count > 65536) {
                throw new IllegalArgumentException("invalid magazine ingredient");
            }
        }
    }
}
