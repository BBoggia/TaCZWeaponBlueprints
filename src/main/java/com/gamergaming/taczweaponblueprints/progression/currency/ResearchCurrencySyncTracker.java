package com.gamergaming.taczweaponblueprints.progression.currency;

import java.util.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import net.minecraft.server.level.ServerPlayer;

/** One main-inventory count per player tick; publish only changed currency or Data settings. */
public final class ResearchCurrencySyncTracker {
    private static final Map<UUID,State> LAST = new HashMap<>();
    private ResearchCurrencySyncTracker() { }
    private record State(ResearchCurrencyPolicy policy,int available,ResearchDataSettings data) { }
    public static void tick(ServerPlayer player) {
        ModAttachments.recipeData(player).ifPresent(data -> {
            var policy=ModConfigs.BLUEPRINT.currencyPolicy();
            var now=new State(policy,ResearchCurrencyPayments.balance(policy,player.getInventory().items,data),ModConfigs.BLUEPRINT.dataSettings());
            var before=LAST.put(player.getUUID(),now);
            if(!now.equals(before)) BlueprintProgressionSyncScheduler.markDirty(player);
        });
    }
    public static void clear(ServerPlayer player) { LAST.remove(player.getUUID()); }
    public static void clear() { LAST.clear(); }
}
