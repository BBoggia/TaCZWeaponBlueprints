package com.gamergaming.taczweaponblueprints.client.designer;

import net.neoforged.fml.common.EventBusSubscriber;

import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.DesignerCaptureTransfer.*;
import com.gamergaming.taczweaponblueprints.network.*;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import java.util.*;

/** Client facade for the later Designer screen. Never uses the ordinary player's disclosed tree stream. */
@EventBusSubscriber(modid="taczweaponblueprints",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.GAME)
public final class ClientDesignerCapture {
    private static UUID request; private static DesignerCaptureTransfer.Receiver receiver;
    private static ResearchDesignerCapture captured; private static Status status;
    private ClientDesignerCapture(){}
    private static long now(){return System.nanoTime()/1_000_000;}
    private static boolean authorized(){var player=Minecraft.getInstance().player;return player!=null&&player.hasPermissions(2)&&Minecraft.getInstance().getConnection()!=null;}
    public static void request(){
        cancel();captured=null;
        if(!authorized()){status=Status.DENIED;return;}
        request=UUID.randomUUID();receiver=new DesignerCaptureTransfer.Receiver(request,now());status=null;
        send(new Request(request,Action.START,0));
    }
    public static void cancel(){if(request!=null&&Minecraft.getInstance().getConnection()!=null)send(new Request(request,Action.CANCEL,0));clearPending();}
    private static void send(Request value){net.neoforged.neoforge.network.PacketDistributor.sendToServer(new DesignerCaptureRequestPacket(value));}
    public static void accept(Reply reply){
        if(request==null||!request.equals(reply.id()))return;
        if(!authorized()){clear();status=Status.DENIED;return;}
        try {
            if(reply.status()==Status.COMPLETE){captured=receiver.finish(reply,true,now());status=Status.COMPLETE;clearPending();return;}
            var next=receiver.accept(reply,true,now());status=reply.status();
            if(next.isPresent())send(next.get());else if(reply.status()!=Status.CHUNK)clearPending();
        } catch(RuntimeException e){cancel();captured=null;status=Status.INVALID;}
    }
    public static Optional<ResearchDesignerCapture> captured(){return authorized()?Optional.ofNullable(captured):Optional.empty();}
    public static Optional<Status> status(){return Optional.ofNullable(status);}
    public static boolean pending(){return request!=null;}
    private static void clearPending(){if(receiver!=null)receiver.clear();receiver=null;request=null;}
    public static void clear(){clearPending();captured=null;status=null;}
    @SubscribeEvent public static void logout(ClientPlayerNetworkEvent.LoggingOut event){clear();}
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){

        if(!authorized()){clearPending();captured=null;status=Minecraft.getInstance().getConnection()==null?null:Status.DENIED;return;}
        if(receiver!=null&&receiver.expired(now())){cancel();status=Status.EXPIRED;}
    }
}
