package com.gamergaming.taczweaponblueprints.compat.magazines;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;

import com.tacz.guns.api.item.IAmmoBox;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/** Public 0.2.0-Beta API boundary, linked only after checking the installed mod version. */
final class ReflectiveMagazineSource implements TaCZMagazinesAdapter.Source {
    private static final String ROOT = "com.raiiiden.taczmagazines.";
    private static final ResourceLocation ITEM_ID = new ResourceLocation(TaCZMagazinesAdapter.MOD_ID, "magazine");
    private final Class<?> magazine;
    private final Method families, family, capacity, familyCapacity, ammo, guns, extended, extension, override, enabled, familyForGun;

    ReflectiveMagazineSource() {
        try {
            ClassLoader loader = ReflectiveMagazineSource.class.getClassLoader();
            magazine = Class.forName(ROOT + "item.MagazineItem", false, loader);
            Class<?> system = Class.forName(ROOT + "magazine.MagazineFamilySystem", false, loader);
            Class<?> overrides = Class.forName(ROOT + "crafting.MagazineRecipeOverrides", false, loader);
            families = system.getMethod("getAllFamilies");
            familyForGun = system.getMethod("getFamilyForGun", ResourceLocation.class);
            family = magazine.getMethod("getMagazineFamilyId", ItemStack.class);
            capacity = magazine.getMethod("getMaxCapacity", ItemStack.class);
            familyCapacity = system.getMethod("getCapacityForFamily", String.class);
            ammo = system.getMethod("getAmmoTypeForFamily", String.class);
            guns = system.getMethod("getCompatibleGuns", String.class);
            extended = system.getMethod("isExtendedFamily", String.class);
            extension = system.getMethod("getExtLevelForFamily", String.class);
            override = overrides.getMethod("get", String.class);
            enabled = override.getReturnType().getMethod("enabled");
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("TaCZ Magazines API unavailable", failure);
        }
    }

    @Override @SuppressWarnings("unchecked")
    public Set<String> families() {
        return Set.copyOf((Set<String>) call(families, null));
    }

    @Override
    public boolean isMagazine(ItemStack stack) {
        return !stack.isEmpty() && magazine.isInstance(stack.getItem())
                && stack.getItem() instanceof IAmmoBox
                && ITEM_ID.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
    }

    @Override public String familyForGun(ResourceLocation gun) { return (String) call(familyForGun, null, gun); }
    @Override public String family(ItemStack stack) { return (String) call(family, null, stack); }
    @Override public int capacity(ItemStack stack) { return (int) call(capacity, null, stack); }
    @Override public int ammoCount(ItemStack stack) { return ((IAmmoBox) stack.getItem()).getAmmoCount(stack); }

    @Override
    public boolean recipeEnabled(String family) {
        Object value = call(override, null, family);
        return value == null || (boolean) call(enabled, value);
    }

    @Override @SuppressWarnings("unchecked")
    public TaCZMagazinesAdapter.Family describe(String family) {
        int level = (boolean) call(extended, null, family) ? (int) call(extension, null, family) : 0;
        return new TaCZMagazinesAdapter.Family((ResourceLocation) call(ammo, null, family),
                (int) call(familyCapacity, null, family), level,
                Set.copyOf((Set<ResourceLocation>) call(guns, null, family)));
    }

    private static Object call(Method method, Object receiver, Object... args) {
        try {
            return method.invoke(receiver, args);
        } catch (InvocationTargetException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof LinkageError linkage) throw linkage;
            if (cause instanceof RuntimeException runtime) throw runtime;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("TaCZ Magazines API call failed", cause);
        } catch (ReflectiveOperationException failure) {
            throw new IllegalStateException("TaCZ Magazines API call unavailable", failure);
        }
    }
}
