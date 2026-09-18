package com.gamergaming.taczweaponblueprints.compat.magazines;

import com.tacz.guns.api.item.IGun;
import net.minecraft.world.item.ItemStack;

/** Detects inserted magazines; removal is restricted to constructing a new market output. */
public final class InsertedMagazineGuard {
    private static final String STORED_MAGAZINE = "TaCZMag_StoredMagazine";

    public static boolean hasInsertedMagazine(ItemStack stack) {
        // Upstream hasMagazine() uses key presence, including empty/malformed data.
        // Do not deserialize the nested item or depend on recipes/add-on discovery:
        // disabled families and saves opened without the add-on still need protection.
        return stack != null && !stack.isEmpty() && stack.getItem() instanceof IGun
                && stack.getTag() != null && stack.getTag().contains(STORED_MAGAZINE);
    }

    /** Only use on a newly constructed market output; never deletes a player's magazine. */
    public static void clearNewOutput(ItemStack stack) {
        if (stack != null && !stack.isEmpty()) stack.removeTagKey(STORED_MAGAZINE);
    }
    private InsertedMagazineGuard() { }
}
