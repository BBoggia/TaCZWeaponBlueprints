package com.gamergaming.taczweaponblueprints.acceptance;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.block.ResearchBenchBlock;
import com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisFragmentCounts;
import net.minecraft.core.*;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Local file coordination for marked disposable release acceptance only. */
@Mod.EventBusSubscriber(modid="taczweaponblueprints")
public final class DesignerReleaseServerProbe {
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event)throws Exception {
        String setting=System.getProperty("tacz.designerReleaseQA.root","");
        if(setting.isEmpty()||event.phase!=TickEvent.Phase.END)return;
        Path root=Path.of(setting);if(!Files.isRegularFile(root.resolve(".designer-disposable")))return;
        Path file=root.resolve("server-control.json");if(!Files.isRegularFile(file)||Files.size(file)>4096)return;
        var result=new JsonObject();
        try {
            var input=JsonParser.parseString(Files.readString(file)).getAsJsonObject();Files.delete(file);var server=event.getServer();
            var player=server.getPlayerList().getPlayers().stream().findFirst().orElseThrow(()->new IllegalStateException("Waiting for real client"));
            var data=player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).resolve().orElseThrow();
            switch(input.get("action").getAsString()) {
                case "op" -> server.getPlayerList().op(player.getGameProfile());
                case "deop" -> server.getPlayerList().deop(player.getGameProfile());
                case "seed" -> {
                    data.clearRecipes();data.replaceProgression(Set.of("tacz:ak47"),List.of("tacz:glock_17","tacz:ak47","tacz:aug","tacz:aa12","tacz:9mm"),137);
                    data.replaceSupplementalProgression(Map.of("tacz:ak47",3,"tacz:aug",2),Map.of(),Map.of("tacz:aa12",new AnalysisFragmentCounts(1,2,0,0)));
                    player.getInventory().clearContent();player.getInventory().add(new ItemStack(ModItems.SCRAP.get(),64));player.getInventory().add(new ItemStack(Items.PAPER,64));player.getInventory().add(new ItemStack(Items.IRON_INGOT,64));
                    com.gamergaming.taczweaponblueprints.network.NetworkHandler.syncPlayerRecipeData(player);
                }
                case "bench" -> {
                    player.closeContainer();player.teleportTo(player.serverLevel(),30.5,100,.5,0,0);var pos=new BlockPos(32,100,0);
                    for(int x=28;x<36;x++)for(int z=-3;z<4;z++)player.serverLevel().setBlockAndUpdate(new BlockPos(x,99,z),Blocks.STONE.defaultBlockState());
                    if(!(player.serverLevel().getBlockState(pos).getBlock() instanceof ResearchBenchBlock))ResearchBenchBlock.placeCompleteStructure(player.serverLevel(),pos,ModBlocks.EXPERIMENTAL_RESEARCH_BENCH.get().defaultBlockState());
                    var state=player.serverLevel().getBlockState(pos);state.getBlock().use(state,player.serverLevel(),pos,player,InteractionHand.MAIN_HAND,new BlockHitResult(Vec3.atCenterOf(pos),Direction.NORTH,pos,false));
                    if(!(player.containerMenu instanceof ResearchBenchMenu))throw new IllegalStateException("Research Bench did not open");
                }
                case "stop" -> server.halt(false);
                case "state" -> {}
                default -> throw new IllegalArgumentException("Unsupported acceptance action");
            }
            result.addProperty("status","ok");result.addProperty("operator",player.hasPermissions(2));result.addProperty("player",player.getGameProfile().getName());result.addProperty("knowledge",data.serializeNBT().toString());result.add("learned",new Gson().toJsonTree(data.getLearnedBlueprints()));result.add("ordinary",new Gson().toJsonTree(data.getArchivedBlueprintFragments()));result.add("analysis",new Gson().toJsonTree(data.getArchivedAnalysisFragments()));result.addProperty("rp",data.getResearchPoints());result.addProperty("scrap",player.getInventory().countItem(ModItems.SCRAP.get()));
            result.addProperty("profile",ModConfigs.BLUEPRINT.progressionSnapshot().activeProfileId().toString());
            if(player.containerMenu instanceof ResearchBenchMenu menu)result.add("preview",com.gamergaming.taczweaponblueprints.research.designer.DesignerValues.evidence(menu.preview()));
        }catch(Exception error){result.addProperty("status","failed");result.addProperty("error",error.toString());}
        Files.writeString(root.resolve("server-result.json"),result.toString());
    }
}
