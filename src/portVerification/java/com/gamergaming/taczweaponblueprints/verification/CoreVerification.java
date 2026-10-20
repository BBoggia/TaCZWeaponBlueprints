package com.gamergaming.taczweaponblueprints.verification;

import com.gamergaming.taczweaponblueprints.block.CraftingWorkbenchBlock;
import com.gamergaming.taczweaponblueprints.init.*;
import com.gamergaming.taczweaponblueprints.item.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.inventory.GunSmithTableMenu;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Isolated real-client/server exercise of the production addon and TaCZ menus. */
@Mod(CoreVerification.ID)
public final class CoreVerification {
    public static final String ID = "wrb_core_verification";
    static final ResourceLocation TARGET = ResourceLocation.parse("tacz:ak47");
    static final BlockPos BENCH = new BlockPos(0, 80, 0);
    static final org.slf4j.Logger LOG = com.mojang.logging.LogUtils.getLogger();
    static final List<String> checks = new ArrayList<>();
    static MinecraftServer server;
    static ServerPlayer player;
    static int stage, ticks, delay, deadline = 2400;
    static boolean finished;
    static BlueprintData blueprint;
    static List<ItemStack> ingredients;

    public CoreVerification(IEventBus bus) {
        bus.addListener(CoreVerification::register);
        NeoForge.EVENT_BUS.addListener((ServerStartedEvent e) -> { server=e.getServer(); RuntimeIdentity.verify(); if(LifecycleVerification.enabled()) LifecycleVerification.started(); if(ReviewVerification.enabled()) ReviewVerification.run(server); else LOG.info("WRB_CORE waiting for QA client"); });
        NeoForge.EVENT_BUS.addListener(CoreVerification::login);
        NeoForge.EVENT_BUS.addListener(CoreVerification::tick);
    }
    static void register(RegisterPayloadHandlersEvent event) {
        event.registrar("1").playBidirectional(Probe.TYPE, Probe.CODEC, (packet, context) -> {
            if (context.player() instanceof ServerPlayer sender) {
                if (LifecycleVerification.enabled()) LifecycleVerification.acknowledge(sender, packet.stage());
                else if (sender == player) acknowledge(packet.stage());
            }
            else ClientVerification.expect(packet.stage());
        });
    }
    public record Probe(int stage) implements CustomPacketPayload {
        static final Type<Probe> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(ID,"probe"));
        static final StreamCodec<RegistryFriendlyByteBuf, Probe> CODEC = StreamCodec.ofMember((v,b)->b.writeInt(v.stage()), b->new Probe(b.readInt()));
        @Override public Type<Probe> type(){return TYPE;}
    }
    static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer p) || !p.getGameProfile().getName().equals("WRBCoreQA")) return;
        player=p;delay=100;
        if(stage==6)stage=7;
    }
    static void tick(ServerTickEvent.Post event) {
        if(server==null)return;
        if(finished){if(--delay==0)server.halt(false);return;}
        if(++ticks>deadline){fail(new IllegalStateException("Timed out at stage "+stage));return;}
        if(player==null||--delay>0)return;
        if (LifecycleVerification.enabled()) { LifecycleVerification.tick(); return; }
        if (FeatureVerification.enabled()) { FeatureVerification.tick(); return; }
        try {
            if(stage==0) {
                player.setGameMode(GameType.SURVIVAL);
                player.teleportTo(server.overworld(),0.5,81,2.5,Set.of(),0,0);
                player.setNoGravity(true);
                outputs().forEach(ItemEntity::discard);
                player.getInventory().clearContent();
                blueprint=BlueprintDataManager.SERVER.getBlueprintData(TARGET.toString());
                require(blueprint!=null,"stock TaCZ catalog contains AK47");
                sharedSerialization();
                // Repeatable fixture: only this disposable player's knowledge is reset.
                ModAttachments.recipeData(player).orElseThrow().deserializeNBT(new CompoundTag());
                player.setItemInHand(InteractionHand.MAIN_HAND,BlueprintItem.createBlueprint(TARGET.toString()));
                fillIngredients();open(false);
                var before=inventory();
                require(CraftingEligibilityService.evaluate(player,menu(),blueprint.getRecipeId()).status()==CraftingEligibilityService.Status.RECIPE_NOT_LEARNED,"unlearned recipe denied by server");
                menu().doCraft(blueprint.getRecipeId(),player);
                require(same(before,inventory())&&outputs().isEmpty(),"denied native craft consumes nothing and creates nothing");
                send(1);
            } else if(stage==2) {
                if(!ModAttachments.recipeData(player).orElseThrow().getLearnedBlueprints().contains(TARGET.toString())){delay=5;return;}
                require(!(player.getMainHandItem().getItem() instanceof BlueprintItem),"survival use consumes physical blueprint");
                var duplicate=BlueprintItem.createBlueprint(TARGET.toString());
                require(BlueprintLearningService.learnPhysicalBlueprint(player,TARGET,duplicate).status()==BlueprintLearningService.Status.ALREADY_LEARNED&&duplicate.getCount()==1,"duplicate learning preserves item");
                var invalid=BlueprintItem.createBlueprint("unknown:missing");
                require(!BlueprintLearningService.learnPhysicalBlueprint(player,ResourceLocation.parse("unknown:missing"),invalid).successful()&&invalid.getCount()==1,"unknown blueprint fails without consumption");
                // The NeoForge serializer and copy-on-death handler must preserve all saved fields.
                var saved=new CompoundTag();player.saveWithoutId(saved);
                var restored=new ServerPlayer(server,player.serverLevel(),player.getGameProfile(),player.clientInformation());restored.load(saved);
                require(ModAttachments.recipeData(restored).orElseThrow().getLearnedBlueprints().contains(TARGET.toString()),"entity save/load retains blueprint attachment");
                var clone=new ServerPlayer(server,player.serverLevel(),player.getGameProfile(),player.clientInformation());
                NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(clone,player,true));
                require(ModAttachments.recipeData(clone).orElseThrow().getLearnedBlueprints().contains(TARGET.toString()),"death clone retains blueprint attachment");
                require(ModAttachments.recipeData(clone).orElseThrow()!=ModAttachments.recipeData(player).orElseThrow(),"death clone owns independent player data");
                open(false);
                var supplied=inventory();player.getInventory().clearContent();
                menu().doCraft(blueprint.getRecipeId(),player);
                require(outputs().isEmpty(),"learned recipe without ingredients creates nothing");
                for(int i=0;i<supplied.size();i++)player.getInventory().setItem(i,supplied.get(i));
                player.inventoryMenu.broadcastFullState();send(3);
            } else if(stage==4) {
                checkCraft("tiered workbench");
                outputs().forEach(ItemEntity::discard);
                player.getInventory().clearContent();fillIngredients();open(true);
                require(CraftingEligibilityService.evaluate(player,menu(),blueprint.getRecipeId()).status()==CraftingEligibilityService.Status.WORKBENCH_TIER_REQUIRED,"native table enforces default tier restriction");
                var before=inventory();menu().doCraft(blueprint.getRecipeId(),player);
                require(same(before,inventory())&&outputs().isEmpty(),"insufficient bench tier consumes nothing and creates nothing");
                send(5);
            } else if(stage==6 && player.connection.isAcceptingMessages()) {
                checkCraft("native TaCZ workbench");
                player.closeContainer();player.setItemInHand(InteractionHand.MAIN_HAND,ItemStack.EMPTY);server.getPlayerList().saveAll();
                if (Boolean.getBoolean("wrb.verifyIntegrated")) {
                    require(!server.isDedicatedServer(), "learning and crafting exercised in an integrated server");
                    open(false);send(8);
                } else {
                    player.connection.disconnect(Component.literal("WRB QA reconnect persistence check"));
                    delay=100;
                }
            } else if(stage==7) {
                require(ModAttachments.recipeData(player).orElseThrow().getLearnedBlueprints().contains(TARGET.toString()),"reconnect reloads learned blueprint from player file");
                open(false);send(8);
            }
        } catch(Throwable error){fail(error);}
    }
    static void acknowledge(int value) {
        if (FeatureVerification.enabled()) { FeatureVerification.acknowledge(value); return; }
        try {
            if(value<0)throw new IllegalStateException("Client check failed at stage "+stage);
            require(value==stage,"client acknowledged stage "+value);
            if(stage==1){stage=2;delay=10;}
            else if(stage==3){stage=4;delay=20;}
            else if(stage==5){
                ModConfigs.BLUEPRINT.externalWorkstationTiers.validateAndSet(Map.of(DefaultAssets.DEFAULT_BLOCK_ID.toString(),com.gamergaming.taczweaponblueprints.progression.workbench.ResearchWorkbenchTier.TIER_3));
                ModConfigs.BLUEPRINT.onUpdateServer(new me.fzzyhmstrs.fzzy_config.event.api.ServerUpdateContext(server,player));
                open(true);send(9);
            }
            else if(stage==9){stage=6;delay=20;}
            else if(stage==8){pass();}
        }catch(Throwable error){fail(error);}
    }
    static void send(int next){stage=next;delay=10;PacketDistributor.sendToPlayer(player,new Probe(next));}
    static GunSmithTableMenu menu(){return (GunSmithTableMenu)player.containerMenu;}
    static void open(boolean nativeBench) {
        player.closeContainer();var level=player.serverLevel();
        level.setBlock(BENCH,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        level.setBlock(BENCH.east(),net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3);
        if(nativeBench){
            var block=BuiltInRegistries.BLOCK.get(ResourceLocation.parse("tacz:gun_smith_table"));
            require(block instanceof AbstractGunSmithTableBlock,"native TaCZ workbench block exists");
            level.setBlock(BENCH,block.defaultBlockState(),3);
            ((GunSmithTableBlockEntity)level.getBlockEntity(BENCH)).setId(DefaultAssets.DEFAULT_BLOCK_ID);
        } else require(CraftingWorkbenchBlock.placeCompleteStructure(level,BENCH,ModBlocks.WORKBENCH_LVL3.get().defaultBlockState()),"tiered workbench places both halves");
        level.getBlockState(BENCH).useWithoutItem(level,player,new BlockHitResult(Vec3.atCenterOf(BENCH),Direction.UP,BENCH,false));
        require(player.containerMenu instanceof GunSmithTableMenu&&menu().stillValid(player),"physical workbench opens valid native menu");
    }
    static void fillIngredients(){
        ingredients=blueprint.getRecipe().getInputs().stream().map(input->input.getIngredient().getItems()[0].copyWithCount(input.getCount())).toList();
        for(var stack:ingredients)require(player.getInventory().add(stack.copy()),"fixture provides craft ingredients");
        player.inventoryMenu.broadcastFullState();
    }
    static List<ItemStack> inventory(){return player.getInventory().items.stream().map(ItemStack::copy).toList();}
    static boolean same(List<ItemStack>a,List<ItemStack>b){for(int i=0;i<a.size();i++)if(!ItemStack.matches(a.get(i),b.get(i)))return false;return true;}
    static List<ItemEntity> outputs(){return player.serverLevel().getEntitiesOfClass(ItemEntity.class,new AABB(-6,76,-6,6,88,6),e->PhysicalWeaponProvenance.from(e.getItem()).isPresent());}
    static void checkCraft(String name){
        var crafted=outputs();
        // Players may pick up the output before the next tick.
        var stacks=new ArrayList<ItemStack>();crafted.forEach(e->stacks.add(e.getItem()));stacks.addAll(player.getInventory().items);
        require(stacks.stream().anyMatch(stack->PhysicalWeaponProvenance.from(stack).filter(p->p.origin()==PhysicalWeaponProvenance.Origin.CRAFTED_SURVIVAL&&p.sourceId().equals(blueprint.getRecipeId())).isPresent()
                &&com.tacz.guns.api.item.IGun.getIGunOrNull(stack)!=null&&com.tacz.guns.api.item.IGun.getIGunOrNull(stack).getGunId(stack).equals(TARGET)),name+" creates correct gun with crafted provenance");
        for(var input:blueprint.getRecipe().getInputs())require(player.getInventory().items.stream().filter(input.getIngredient()::test).mapToInt(ItemStack::getCount).sum()==0,name+" deducts required ingredients");
    }
    static void sharedSerialization() {
        require(net.minecraft.world.entity.EntityType.VILLAGER.create(player.serverLevel())!=null,"villager shared mixins load");
        var offer=com.gamergaming.taczweaponblueprints.villager.VillagerTradeOffers.create(
                ResourceLocation.fromNamespaceAndPath(ID,"fixture"),new ItemStack(net.minecraft.world.item.Items.EMERALD,5),ItemStack.EMPTY,
                BlueprintItem.createBlueprint(TARGET.toString()),8,1,0.05f);
        var tag=com.gamergaming.taczweaponblueprints.villager.OwnedOfferSerialization.save(offer,player.registryAccess());
        var restored=com.gamergaming.taczweaponblueprints.villager.OwnedOfferSerialization.read(tag,player.registryAccess());
        require(((com.gamergaming.taczweaponblueprints.villager.OwnedVillagerOffer)offer).taczweaponblueprints$terms().equals(
                ((com.gamergaming.taczweaponblueprints.villager.OwnedVillagerOffer)restored).taczweaponblueprints$terms()),"owned trade codec retains access terms");
        require(ItemStack.matches(offer.getResult(),restored.getResult()),"owned trade codec retains blueprint components");
    }
    static void require(boolean condition,String name){if(!condition)throw new IllegalStateException(name);checks.add(name);LOG.info("WRB_CORE PASS {}",name);}
    static void pass(){LOG.info("WRB_CORE SUCCESS {} checks",checks.size());write(true,null);finished=true;delay=40;PacketDistributor.sendToPlayer(player,new Probe(99));}
    static void fail(Throwable error){LOG.error("WRB_CORE FAILURE stage "+stage,error);write(false,error.toString());finished=true;delay=20;if(player!=null&&player.connection.isAcceptingMessages())PacketDistributor.sendToPlayer(player,new Probe(-1));}
    static String suite(){return LifecycleVerification.enabled()?LifecycleVerification.suite():Boolean.getBoolean("wrb.verifyIntegrated")?"integrated":ReviewVerification.enabled()?"review-regressions":FeatureVerification.enabled()?"features":"core";}
    static void write(boolean passed,String error){try{var obj=new com.google.gson.JsonObject();RuntimeIdentity.addTo(obj);obj.addProperty("passed",passed);obj.addProperty("suite",suite());obj.addProperty("stage",stage);obj.addProperty("error",error);obj.add("checks",new com.google.gson.Gson().toJsonTree(checks));Files.writeString(Path.of(Boolean.getBoolean("wrb.verifyIntegrated")?"integrated-server-verification.json":"core-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(obj));}catch(Exception e){throw new IllegalStateException(e);}}
}
