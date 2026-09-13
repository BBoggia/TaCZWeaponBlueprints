package com.gamergaming.taczweaponblueprints.compat.magazines;

import java.util.Set;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.TreeSet;
import java.util.function.Function;

import com.gamergaming.taczweaponblueprints.capabilities.PlayerProgressionLimits;
import com.gamergaming.taczweaponblueprints.progression.workbench.CraftingWorkbenchTierResolver;
import com.tacz.guns.crafting.GunSmithTableRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;

/** Access to validated, current upstream recipes at supported Workbenches. */
public final class MagazineCraftingAccess {
    public static final ResourceLocation AMMO_WORKBENCH = new ResourceLocation("tacz:ammo_workbench");

    private MagazineCraftingAccess() { }

    public static boolean supportsWorkbench(ResourceLocation id) {
        return AMMO_WORKBENCH.equals(id) || CraftingWorkbenchTierResolver.isNativeCraftingWorkbench(id);
    }

    /** Reserve room for every blueprint, so learning another recipe never evicts a magazine. */
    public static boolean fitsAccessSnapshot(int blueprintCount, int magazineCount) {
        return blueprintCount >= 0 && magazineCount >= 0
                && (long) blueprintCount + magazineCount <= PlayerProgressionLimits.MAX_IDS_PER_COLLECTION;
    }

    public static Optional<MagazineDescriptor> validatedRecipe(TaCZMagazinesAdapter.Capture catalog,
            ResourceLocation workstation, ResourceLocation recipeId,
            Function<ResourceLocation, Recipe<?>> currentRecipe, int blueprintCount) {
        if (!supportsWorkbench(workstation) || catalog.status() != TaCZMagazinesAdapter.Status.READY
                || !fitsAccessSnapshot(blueprintCount, catalog.families().size())) return Optional.empty();
        var family = catalog.familyByRecipe().get(recipeId);
        var descriptor = family == null ? null : catalog.families().get(family);
        return descriptor != null && descriptor.recipeId().equals(recipeId)
                && matchesRecipe(descriptor, currentRecipe.apply(recipeId)) ? Optional.of(descriptor) : Optional.empty();
    }

    public static boolean allows(TaCZMagazinesAdapter.Capture catalog, ResourceLocation workstation,
            ResourceLocation recipeId, Function<ResourceLocation, Recipe<?>> currentRecipe, int blueprintCount,
            Predicate<MagazineDescriptor> progression) {
        return validatedRecipe(catalog, workstation, recipeId, currentRecipe, blueprintCount)
                .filter(progression).isPresent();
    }

    public static Set<ResourceLocation> allowedRecipes(TaCZMagazinesAdapter.Capture catalog,
            ResourceLocation workstation, Function<ResourceLocation, Recipe<?>> currentRecipe, int blueprintCount,
            Predicate<MagazineDescriptor> progression) {
        if (!supportsWorkbench(workstation) || catalog.status() != TaCZMagazinesAdapter.Status.READY
                || !fitsAccessSnapshot(blueprintCount, catalog.families().size())) return Set.of();
        Set<ResourceLocation> allowed = new TreeSet<>();
        for (var id : catalog.familyByRecipe().keySet()) {
            if (allows(catalog, workstation, id, currentRecipe, blueprintCount, progression)) allowed.add(id);
        }
        return Set.copyOf(allowed);
    }

    /** A cached recipe ID is insufficient if another reload replaced its output or costs. */
    static boolean matchesRecipe(MagazineDescriptor descriptor, Recipe<?> recipe) {
        try {
            if (!(recipe instanceof GunSmithTableRecipe table)
                    || !descriptor.recipeId().equals(table.getId())
                    || !TaCZMagazinesAdapter.MAGAZINE_TAB.equals(table.getTab())
                    || !ItemStack.matches(descriptor.output(), table.getOutput())
                    || descriptor.ingredients().size() != table.getInputs().size()) return false;
            for (int i = 0; i < descriptor.ingredients().size(); i++) {
                var captured = descriptor.ingredients().get(i);
                var current = table.getInputs().get(i);
                if (captured.count() != current.getCount()
                        || !captured.ingredientJson().equals(current.getIngredient().toJson().toString())) return false;
            }
            return true;
        } catch (RuntimeException | LinkageError unavailableRecipe) {
            return false;
        }
    }

    /** Native tab membership only; doCraft separately authenticates workstation and player access. */
    public static boolean hasMagazineTab(TaCZMagazinesAdapter.Capture catalog, GunSmithTableRecipe recipe) {
        if (recipe == null || catalog.status() != TaCZMagazinesAdapter.Status.READY) return false;
        var key = catalog.familyByRecipe().get(recipe.getId());
        var descriptor = key == null ? null : catalog.families().get(key);
        return descriptor != null && matchesRecipe(descriptor, recipe);
    }
}
