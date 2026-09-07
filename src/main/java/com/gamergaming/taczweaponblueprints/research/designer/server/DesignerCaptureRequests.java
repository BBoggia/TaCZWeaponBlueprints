package com.gamergaming.taczweaponblueprints.research.designer.server;

import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.DesignerCaptureTransfer.*;
import com.gamergaming.taczweaponblueprints.network.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import java.util.*;

@Mod.EventBusSubscriber(modid="taczweaponblueprints",bus=Mod.EventBusSubscriber.Bus.FORGE)
public final class DesignerCaptureRequests {
    private record Pending(UUID request,DesignerCaptureTransfer transfer){}
    private static final Map<UUID,Pending> ACTIVE=new HashMap<>();
    private static final Map<UUID,Long> LAST_START=new HashMap<>();
    private DesignerCaptureRequests(){}
    private static long now(){return System.nanoTime()/1_000_000;}
    public static void handle(ServerPlayer player,Request request){
        long now=now();UUID playerId=player.getUUID();
        if(!player.hasPermissions(2)){ACTIVE.remove(playerId);send(player,Reply.status(request.id(),Status.DENIED));return;}
        if(request.action()==Action.START){
            if(request.index()!=0){send(player,Reply.status(request.id(),Status.INVALID));return;}
            if(now-LAST_START.getOrDefault(playerId,Long.MIN_VALUE/2)<5000||ACTIVE.size()>=2){send(player,Reply.status(request.id(),Status.BUSY));return;}
            LAST_START.put(playerId,now);ACTIVE.remove(playerId);
            try {
                var transfer=DesignerCaptureTransfer.begin(request.id(),player.hasPermissions(2),now,
                        () -> ResearchDesignerCaptureService.capture(player.server,request.id()).bytes()).orElseThrow();
                ACTIVE.put(playerId,new Pending(request.id(),transfer));
                send(player,transfer.handle(new Request(request.id(),Action.NEXT,0),player.hasPermissions(2),now()));
            } catch(RuntimeException e){
                ACTIVE.remove(playerId);
                var status=Status.RETRY;
                if(e instanceof IllegalArgumentException) status=e.getMessage()!=null && (e.getMessage().contains("limit") || e.getMessage().contains("Too many"))
                        ? Status.LIMIT_EXCEEDED : Status.UNSUPPORTED;
                send(player,Reply.status(request.id(),status));
            }
            return;
        }
        var pending=ACTIVE.get(playerId);
        if(pending==null){send(player,Reply.status(request.id(),Status.EXPIRED));return;}
        var reply=pending.transfer().handle(request,player.hasPermissions(2),now);
        if(pending.transfer().closed())ACTIVE.remove(playerId);send(player,reply);
    }
    private static void send(ServerPlayer player,Reply reply){NetworkHandler.INSTANCE.send(PacketDistributor.PLAYER.with(()->player),new DesignerCaptureReplyPacket(reply));}
    @SubscribeEvent public static void tick(TickEvent.ServerTickEvent event){
        if(event.phase!=TickEvent.Phase.END)return;
        var iterator=ACTIVE.entrySet().iterator();while(iterator.hasNext()){
            var entry=iterator.next();var player=event.getServer().getPlayerList().getPlayer(entry.getKey());
            if(player==null||!player.hasPermissions(2)||entry.getValue().transfer().expired(now())){
                iterator.remove();if(player!=null)send(player,Reply.status(entry.getValue().request(),player.hasPermissions(2)?Status.EXPIRED:Status.DENIED));
            }
        }
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){ACTIVE.remove(event.getEntity().getUUID());LAST_START.remove(event.getEntity().getUUID());}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){ACTIVE.clear();LAST_START.clear();}
}
