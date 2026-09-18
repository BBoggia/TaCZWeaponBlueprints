package com.gamergaming.taczweaponblueprints.acceptance.villager;

import java.nio.file.*;
import java.util.*;
import com.mojang.authlib.GameProfile;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import net.minecraft.core.*;
import net.minecraft.nbt.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.*;
import net.minecraft.world.entity.ai.behavior.AssignProfessionFromJobSite;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.registries.ForgeRegistries;

/** Runs first under 1.3.5, then under the unchanged candidate using the same saved world. */
public final class VillagerUpgradeProbe {
    private static int ticks;
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || ++ticks != 40) return;
        var server=event.getServer();var level=server.overworld();
        var root=Path.of(System.getProperty("tacz.villagerQA.root"));
        var mode=System.getProperty("tacz.villagerQA.upgrade");
        var result=new ArrayList<String>();
        try {
            var player=FakePlayerFactory.get(level,new GameProfile(UUID.fromString("8e09e884-e874-4038-aa45-252224fed970"),"UpgradeFixture"));
            var data=player.getCapability(ModCapabilities.PLAYER_RECIPE_DATA).orElseThrow(IllegalStateException::new);
            var file=root.resolve("old-player.nbt");var marker=new BlockPos(10,80,10);
            if (mode.equals("create")) {
                data.addBlueprint("tacz:m1911");data.addBlueprint("tacz:m4a1");data.discoverBlueprint("tacz:m107");data.setResearchPoints(123);
                player.getInventory().add(new ItemStack(Items.IRON_INGOT,11));
                NbtIo.writeCompressed(player.serializeNBT(),file.toFile());
                level.setBlockAndUpdate(marker,Blocks.GOLD_BLOCK.defaultBlockState());
                result.add("Old release saved native player inventory/capability and existing world marker");
            } else {
                var saved=NbtIo.readCompressed(file.toFile());player.load(saved);
                var expected=saved.getCompound("ForgeCaps").getCompound("taczweaponblueprints:player_recipe_data");
                check(expected.equals(data.serializeNBT()),"Complete saved research capability is unchanged",result);
                check(data.getResearchPoints()==123&&data.hasBlueprint("tacz:m1911")&&data.hasBlueprint("tacz:m4a1"),"Learned blueprints and banked RP retained",result);
                check(player.getInventory().countItem(Items.IRON_INGOT)==11,"Native player inventory retained",result);
                check(level.getBlockState(marker).is(Blocks.GOLD_BLOCK),"Existing world block survives upgrade",result);
                for (String name:List.of("gunsmith","ballistics_researcher")) {
                    var id=new ResourceLocation("taczweaponblueprints",name);
                    var block=ForgeRegistries.BLOCKS.getValue(new ResourceLocation("taczweaponblueprints",name.equals("gunsmith")?"gunsmith_station":"ballistics_desk"));
                    var pos=marker.offset(name.equals("gunsmith")?2:4,0,0);level.setBlockAndUpdate(pos,block.defaultBlockState());
                    var v=new Villager(EntityType.VILLAGER,level);v.moveTo(pos.getX()+.5,pos.getY(),pos.getZ()+.5);v.setNoAi(true);level.addFreshEntity(v);
                    level.getPoiManager().take(h->h.is(id),(h,p)->p.equals(pos),pos,1).orElseThrow();
                    v.getBrain().setMemory(MemoryModuleType.POTENTIAL_JOB_SITE,GlobalPos.of(level.dimension(),pos));
                    AssignProfessionFromJobSite.create().tryStart(level,v,level.getGameTime());
                    check(v.getVillagerData().getProfession()==ForgeRegistries.VILLAGER_PROFESSIONS.getValue(id)&&!v.getOffers().isEmpty(),"New profession acquires its job site in upgraded world: "+name,result);
                }
                NbtIo.writeCompressed(player.serializeNBT(),root.resolve("upgraded-player.nbt").toFile());
            }
            result.add("PASS");
        } catch (Throwable failure) { result.add("FAIL: "+failure);failure.printStackTrace(); }
        finally {
            try {Files.write(root.resolve("upgrade-"+mode+"-result.txt"),result);}catch(Exception failure){throw new RuntimeException(failure);}
            server.halt(false);
        }
    }
    private static void check(boolean valid,String message,List<String> result) {
        if (!valid) throw new AssertionError(message);result.add(message);
    }
}
