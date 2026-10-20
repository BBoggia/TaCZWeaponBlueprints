package com.gamergaming.taczweaponblueprints.verification;

import com.gamergaming.taczweaponblueprints.client.*;
import com.gamergaming.taczweaponblueprints.client.designer.*;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.network.*;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.world.inventory.*;
import net.neoforged.neoforge.network.PacketDistributor;
import static com.gamergaming.taczweaponblueprints.verification.ClientVerification.*;

final class FeatureClientVerification {
    static boolean capturing;
    static boolean emi(){return net.neoforged.fml.ModList.get().isLoaded("emi");}
    static boolean jei(){return net.neoforged.fml.ModList.get().isLoaded("jei");}
    static void tick(Minecraft mc) throws Exception {
        int stage=expected;
        mc.getToasts().clear();
        if(wait<20)return;
        if(stage==101) {
            if(!(mc.screen instanceof ResearchBenchScreen) || !(mc.player.containerMenu instanceof ResearchBenchMenu menu) || !ClientResearchState.hasPublication())return;
            if(emi()&&!EmiFeatureVerification.ready() || jei()&&!JeiFeatureVerification.ready())return;
            if(emi())EmiFeatureVerification.verify();
            if(jei())JeiFeatureVerification.verify();
            require(!ClientResearchState.publication().graph().nodes().isEmpty(),"research tree and Journal arrive as a client publication");
            for(var kind:java.util.List.of("gun","ammo","attachment"))require(ClientResearchState.publication().graph().nodes().stream().anyMatch(n->{var data=com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager.CLIENT.getBlueprintData(n.blueprintId().toString());return data!=null && data.getKind().serializedName().equals(kind);}),"enabled research domain reaches client: "+kind);
            screenshot(mc,"research-tree");
            PacketDistributor.sendToServer(new ResearchBenchActionPacket(menu.containerId,101,ResearchBenchResearchAction.SELECT,Optional.of(CoreVerification.TARGET)));
        } else if(stage==103) {
            if(!(mc.player.containerMenu instanceof ResearchBenchMenu menu) || !menu.preview().researchable())return;
            require(menu.preview().routeFingerprint().isPresent() && menu.preview().unlockCount()>1,"client receives complete priced route and commit fingerprint");
            screenshot(mc,"research-selection");
            PacketDistributor.sendToServer(new ResearchBenchActionPacket(menu.containerId,103,ResearchBenchResearchAction.RESEARCH,Optional.of(CoreVerification.TARGET),menu.preview().routeFingerprint()));
        } else if(stage==105) {
            if(!(mc.screen instanceof BlueprintRecyclerScreen) || !(mc.player.containerMenu instanceof BlueprintRecyclerMenu menu) || !menu.preview().actionable())return;
            var preview=menu.preview();require(preview.presentation().stationTier()==2 && preview.presentation().weapon().isPresent(),"client receives Analyzer tier, odds, payment and action preview");
            verifyAnalyzerScrolling((BlueprintRecyclerScreen)mc.screen);
            if(emi())EmiFeatureVerification.analyzer();
            screenshot(mc,"analyzer");
            PacketDistributor.sendToServer(new BlueprintRecyclerActionPacket(menu.containerId,105,BlueprintRecyclerActionContract.Action.REVERSE_ENGINEER,preview.inputId().orElseThrow(),preview.inputCount(),preview.stateToken()));
        } else if(stage==107) {
            if(!(mc.screen instanceof MerchantScreen) || !(mc.player.containerMenu instanceof MerchantMenu menu) || menu.getSlot(2).getItem().isEmpty())return;
            require(!menu.getOffers().isEmpty(),"client receives owned villager offers and payable output");screenshot(mc,"merchant");
            mc.gameMode.handleInventoryMouseClick(menu.containerId,2,0,ClickType.PICKUP,mc.player);
        } else if(stage==109) {
            if(!mc.player.hasPermissions(2))return;
            if(!capturing){capturing=true;ClientDesignerCapture.request();return;}
            if(ClientDesignerCapture.pending())return;
            var capture=ClientDesignerCapture.captured().orElseThrow(()->new IllegalStateException("capture failed: "+ClientDesignerCapture.status()));
            require(ClientDesignerCapture.status().orElseThrow()==DesignerCaptureTransfer.Status.COMPLETE,"operator captures server catalog through chunked NeoForge payloads");
            var project=ResearchDesignerConversion.createCustom(capture,"feature_export","Feature export").project();
            var session=new ResearchDesignerSession(project);
            var prepared=ResearchDesignerExport.prepare(session.checkpoint(),Optional.of(capture));
            require(ResearchDesignerExport.readProject(prepared.projectBundle()).checkpoint().project().equals(project),"network capture compiles and exports an editable project");
            require(!ResearchDesignerExport.resources(DesignerArchive.read(prepared.datapack())).isEmpty(),"network capture exports valid Minecraft 1.21.1 datapack");
            Files.write(Path.of("feature-datapack.zip"),prepared.datapack());
            mc.setScreen(new ResearchDesignerWorkspaceScreen(null,session,Optional.of(capture),false));
        } else if(stage==111) {
            if(mc.player.hasPermissions(2))return;
            ClientDesignerCapture.request();
            require(ClientDesignerCapture.captured().isEmpty() && ClientDesignerCapture.status().orElseThrow()==DesignerCaptureTransfer.Status.DENIED,"revoked operator access clears capture and blocks fresh request");
            PacketDistributor.sendToServer(new DesignerCaptureRequestPacket(new DesignerCaptureTransfer.Request(UUID.randomUUID(),DesignerCaptureTransfer.Action.START,0)));
            screenshot(mc,"designer");mc.setScreen(null);
        } else throw new IllegalStateException("unexpected feature stage "+stage);
        expected=0;PacketDistributor.sendToServer(new CoreVerification.Probe(stage));
    }
    private static void verifyAnalyzerScrolling(BlueprintRecyclerScreen screen) throws ReflectiveOperationException {
        var open=BlueprintRecyclerScreen.class.getDeclaredField("detailsOpen");open.setAccessible(true);
        var offset=BlueprintRecyclerScreen.class.getDeclaredField("detailScroll");offset.setAccessible(true);
        var lines=BlueprintRecyclerScreen.class.getDeclaredMethod("detailLines");lines.setAccessible(true);
        require(((List<?>)lines.invoke(screen)).size()>7,"Analyzer fixture has scrollable explanations");
        double x=screen.width/2.,y=(screen.height-BlueprintRecyclerMenu.Layout.PANEL_HEIGHT)/2.+35;
        try {
            open.setBoolean(screen,true);
            require(screen.mouseScrolled(x,y,0,-1)&&offset.getInt(screen)==1,"Analyzer details scroll down one line");
            for(double horizontal:new double[]{-1,0,1})
                require(!screen.mouseScrolled(x,y,horizontal,0)&&offset.getInt(screen)==1,
                        "Analyzer details ignore horizontal/zero scroll: "+horizontal);
            require(screen.mouseScrolled(x,y,1,1)&&offset.getInt(screen)==0,"Analyzer diagonal input uses vertical direction");
            screen.mouseScrolled(x,y,0,1);
            require(offset.getInt(screen)==0,"Analyzer scroll stops at first line");
        } finally { open.setBoolean(screen,false);offset.setInt(screen,0); }
    }
    static void screenshot(Minecraft mc,String name)throws Exception{
        try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){
            if(name.equals("research-tree")) {
                int gold=0;
                // Exclude the rail/header/minimap; the central canvas must contain
                // visible node borders, not just a populated off-screen model.
                for(int y=image.getHeight()/4;y<image.getHeight()*3/4;y++)for(int x=image.getWidth()/5;x<image.getWidth()*4/5;x++){
                    int color=image.getPixelRGBA(x,y), r=color&255,g=(color>>8)&255,b=(color>>16)&255;
                    if(r>160 && g>120 && b<160 && r>b+40)gold++;
                }
                require(gold>100,"Research Bench draws visible nodes in the central canvas: "+gold+" gold pixels");
            }
            image.writeToFile(Path.of("feature-"+name+".png"));
        }
    }
}
