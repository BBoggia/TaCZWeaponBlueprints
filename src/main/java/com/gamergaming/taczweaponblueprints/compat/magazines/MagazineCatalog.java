package com.gamergaming.taczweaponblueprints.compat.magazines;

import java.util.Collection;

import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.ItemStack;

/** Server-owned publication, rebuilt after upstream generation and cleared between worlds. */
public final class MagazineCatalog {
    public static final MagazineCatalog INSTANCE = new MagazineCatalog();
    private volatile Publication publication = new Publication(0,
            TaCZMagazinesAdapter.Capture.empty(TaCZMagazinesAdapter.Status.ABSENT));

    public Publication publication() { return publication; }

    void initialize(TaCZMagazinesAdapter adapter) {
        publication = new Publication(0, TaCZMagazinesAdapter.Capture.empty(
                adapter.status() == TaCZMagazinesAdapter.Status.READY
                        ? TaCZMagazinesAdapter.Status.WAITING_FOR_RECIPES : adapter.status()));
    }

    Publication rebuild(TaCZMagazinesAdapter adapter, Collection<? extends Recipe<?>> recipes) {
        var captured = adapter.capture(recipes);
        if (!sameContent(publication.catalog(), captured))
            publication = new Publication(Math.incrementExact(publication.revision()), captured);
        return publication;
    }

    void clear() {
        publication = new Publication(0,
                TaCZMagazinesAdapter.Capture.empty(TaCZMagazinesAdapter.Status.ABSENT));
    }

    /** Upstream also regenerates recipes on login; compare content, not ItemStack identity. */
    static boolean sameRecipes(TaCZMagazinesAdapter.Capture before, TaCZMagazinesAdapter.Capture after) {
        if (before.status() != after.status() || !before.familyByRecipe().equals(after.familyByRecipe())) return false;
        for (var entry : before.families().entrySet()) {
            var old = entry.getValue();
            var current = after.families().get(entry.getKey());
            if (current == null || !old.ingredients().equals(current.ingredients())
                    || !ItemStack.matches(old.output(), current.output())) return false;
        }
        return before.families().size() == after.families().size();
    }

    public static boolean sameContent(TaCZMagazinesAdapter.Capture before, TaCZMagazinesAdapter.Capture after) {
        if (!sameRecipes(before,after) || !before.rejected().equals(after.rejected())) return false;
        for(var entry:before.families().entrySet()) {
            var a=entry.getValue();var b=after.families().get(entry.getKey());
            if(!a.familyKey().equals(b.familyKey()) || !a.upstreamFamily().equals(b.upstreamFamily())
                    || !a.ammoId().equals(b.ammoId()) || a.capacity()!=b.capacity() || a.extensionLevel()!=b.extensionLevel()
                    || !a.compatibleGuns().equals(b.compatibleGuns()) || !a.recipeId().equals(b.recipeId())) return false;
        }
        return true;
    }

    public record Publication(long revision, TaCZMagazinesAdapter.Capture catalog) { }
}
