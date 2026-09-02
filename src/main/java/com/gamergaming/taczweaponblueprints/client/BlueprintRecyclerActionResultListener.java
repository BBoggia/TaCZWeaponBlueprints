package com.gamergaming.taczweaponblueprints.client;

import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract;

/** Client screen hook used only after the dedicated Recycler screen is registered. */
public interface BlueprintRecyclerActionResultListener {
    void acceptRecyclerActionResult(
            int requestId,
            BlueprintRecyclerActionContract.ActionResult result);
}

