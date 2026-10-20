package com.gamergaming.taczweaponblueprints.verification;

import static com.gamergaming.taczweaponblueprints.verification.ClientVerification.*;
import com.gamergaming.taczweaponblueprints.client.ClientCraftingAccessState;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.tacz.guns.client.gui.GunSmithTableScreen;
import com.tacz.guns.inventory.GunSmithTableMenu;
import com.tacz.guns.network.message.ClientMessageCraft;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

final class LifecycleClientVerification {
    static boolean respawnRequested;
    static void tick(Minecraft mc) throws Exception {
        boolean main = mc.getUser().getName().equals(LifecycleVerification.MAIN);
        if (expected == 203) {
            if (mc.player.isDeadOrDying() && !respawnRequested) {
                respawnRequested = true;
                mc.getConnection().send(new ServerboundClientCommandPacket(ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                return;
            }
            if (!respawnRequested || mc.player.isDeadOrDying() || wait < 30) return;
        } else if (expected == 205) {
            if (mc.level.dimension() != Level.NETHER || wait < 20) return;
        } else {
            if (!(mc.screen instanceof GunSmithTableScreen screen) || !(mc.player.containerMenu instanceof GunSmithTableMenu menu)) return;
            var access = ClientCraftingAccessState.snapshot(menu.containerId, menu);
            if (!access.receivedForMode(ModConfigs.BLUEPRINT.enableBlueprints.get())) return;
            var bp = BlueprintDataManager.CLIENT.getBlueprintData(CoreVerification.TARGET.toString()); if (bp == null) return;
            var field = GunSmithTableScreen.class.getDeclaredField("recipes"); field.setAccessible(true);
            @SuppressWarnings("unchecked") var recipes = (Map<ResourceLocation, List<ResourceLocation>>)field.get(screen);
            boolean visible = recipes.values().stream().anyMatch(ids -> ids.contains(bp.getRecipeId()));
            if (visible != main && wait < 100) return;
            require(visible == main && access.allowedRecipeIds().contains(bp.getRecipeId().toString()) == main,
                    "independent native recipe visibility and permission " + mc.getUser().getName() + "/" + expected);
            if (!main && expected == 201) PacketDistributor.sendToServer(new ClientMessageCraft(bp.getRecipeId(), menu.containerId));
        }
        var d = ModAttachments.recipeData(mc.player).orElseThrow();
        if (d.getResearchPoints() != (main ? 137 : 41) && wait < 100) return;
        require(d.getResearchPoints() == (main ? 137 : 41), "client retains its own RP at stage " + expected);
        require(d.getLearnedBlueprints().contains(CoreVerification.TARGET.toString()) == main, "client retains its own learned knowledge at stage " + expected);
        require(d.getArchivedBlueprintFragments().getOrDefault(CoreVerification.TARGET.toString(), 0) == (main ? 3 : 7), "ordinary fragments synchronized at stage " + expected);
        require(d.getArchivedAnalysisFragments().get(CoreVerification.TARGET.toString()).recyclableBypass() == (main ? 4 : 6), "protected fragment classes synchronized at stage " + expected);
        int next = expected; expected = 0; PacketDistributor.sendToServer(new CoreVerification.Probe(next));
    }
}
