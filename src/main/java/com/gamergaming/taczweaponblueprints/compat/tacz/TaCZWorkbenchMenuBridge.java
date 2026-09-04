package com.gamergaming.taczweaponblueprints.compat.tacz;

import java.util.Optional;

import com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchContext;

/** Server-owned context attached to TaCZ's native crafting menu. */
public interface TaCZWorkbenchMenuBridge {
    void taczweaponblueprints$attachWorkbenchContext(ResearchWorkbenchContext context);

    Optional<ResearchWorkbenchContext> taczweaponblueprints$workbenchContext();

    boolean taczweaponblueprints$acceptCraftingAccessRequest(long requestId);

    long taczweaponblueprints$craftingAccessRequestId();

    long taczweaponblueprints$nextCraftingAccessSnapshotId();
}
