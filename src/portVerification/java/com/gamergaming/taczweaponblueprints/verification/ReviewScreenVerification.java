package com.gamergaming.taczweaponblueprints.verification;

import com.gamergaming.taczweaponblueprints.client.designer.ResearchDesignerChooserScreen;
import com.gamergaming.taczweaponblueprints.client.designer.ResearchDesignerWorkspaceScreen;

import com.gamergaming.taczweaponblueprints.client.BlueprintJournalScreen;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

/** Real rendered screens; no production instrumentation or project publication. */
@EventBusSubscriber(modid="wrb_core_verification",value=Dist.CLIENT)
public final class ReviewScreenVerification {
    private static final String[] NAMES = {"journal", "chooser", "build", "editing", "properties", "export", "import"};
    private static final List<String> checks = new ArrayList<>();
    private static int phase=-1, ticks, frames, backgrounds;
    private static String failure;
    private static boolean done;
    private static Screen active;
    private static ResearchDesignerWorkspaceScreen workspace;
    private static boolean enabled(){return Boolean.getBoolean("wrb.verifyReviewFixes") && !done;}
    @SubscribeEvent public static void before(ScreenEvent.Render.Pre event){if(enabled() && event.getScreen()==active)backgrounds=0;}
    @SubscribeEvent public static void background(ScreenEvent.BackgroundRendered event){if(enabled() && phase>=0)backgrounds++;}
    @SubscribeEvent public static void after(ScreenEvent.Render.Post event){
        if(!enabled() || phase<0 || event.getScreen()!=active)return;
        frames++;
        int expected=phase==0?1:0;
        if(backgrounds!=expected)failure=NAMES[phase]+" default background count: "+backgrounds+", expected "+expected;
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post event){
        if(!enabled())return;
        var mc=Minecraft.getInstance();
        try {
            if(failure!=null)throw new IllegalStateException(failure);
            if(mc.getOverlay()!=null)return;
            if(phase<0){if(mc.screen instanceof TitleScreen && ++ticks>=30)open(mc,0);return;}
            if(mc.screen!=active)throw new IllegalStateException("Unexpected screen during "+NAMES[phase]);
            if(++ticks<40)return;
            if(frames<1)throw new IllegalStateException("No rendered frames for "+NAMES[phase]);
            if(phase==2)verifyLibraryScrolling();
            if(phase==5){
                var editorField=ResearchDesignerWorkspaceScreen.class.getDeclaredField("editor");editorField.setAccessible(true);
                var editor=editorField.get(workspace);
                var busy=editor.getClass().getDeclaredField("busy");busy.setAccessible(true);
                if(busy.getBoolean(editor) && ticks<400)return;
                var prepared=editor.getClass().getDeclaredField("prepared");prepared.setAccessible(true);
                if(prepared.get(editor)==null)throw new IllegalStateException("Export screen failed to prepare captured project");
                var lines=editor.getClass().getDeclaredField("lines");lines.setAccessible(true);
                if(!((List<?>)lines.get(editor)).contains("Minecraft 1.21.1 • datapack format 48"))
                    throw new IllegalStateException("Export screen shows incorrect target version");
                checks.add("export screen prepares captured server project and displays 1.21.1 format 48");
            }
            if(phase==6){
                var lines=active.getClass().getDeclaredField("lines");lines.setAccessible(true);
                var first=((List<?>)lines.get(active)).getFirst();
                if(active.children().stream().noneMatch(widget -> widget instanceof net.minecraft.client.gui.components.AbstractWidget row
                        && row.getMessage().getString().equals(first)))
                    throw new IllegalStateException("Import instructions are absent on first open");
                checks.add("import instructions visible on first open without refresh");
            }
            try(var image=Screenshot.takeScreenshot(mc.getMainRenderTarget())){image.writeToFile(Path.of("review-"+NAMES[phase]+".png"));}
            checks.add(NAMES[phase]+" rendered with correct background count; screenshot saved");
            if(phase+1<NAMES.length)open(mc,phase+1);else finish(mc,null);
        }catch(Throwable error){finish(mc,error);}
    }
    private static void verifyLibraryScrolling() throws ReflectiveOperationException {
        var buildField=ResearchDesignerWorkspaceScreen.class.getDeclaredField("build");buildField.setAccessible(true);
        var build=buildField.get(workspace);
        var offset=build.getClass().getDeclaredField("libraryOffset");offset.setAccessible(true);
        if(!workspace.mouseScrolled(20,120,0,-1) || offset.getInt(build)!=3)
            throw new IllegalStateException("Designer library must scroll down three rows");
        for(double horizontal:new double[]{-1,0,1}) {
            if(workspace.mouseScrolled(20,120,horizontal,0) || offset.getInt(build)!=3)
                throw new IllegalStateException("Horizontal/zero input moved or consumed Designer library scroll");
        }
        if(!workspace.mouseScrolled(20,120,1,1) || offset.getInt(build)!=0)
            throw new IllegalStateException("Diagonal input must use vertical scroll direction");
        workspace.mouseScrolled(20,120,0,1);
        if(offset.getInt(build)!=0)throw new IllegalStateException("Designer library scrolled above its first row");
        checks.add("Designer library ignores horizontal/zero scroll and preserves vertical, diagonal and boundary behavior");
    }
    private static void open(Minecraft mc,int next) throws Exception {
        phase=next;ticks=0;frames=0;backgrounds=0;
        switch(phase){
            case 0 -> mc.setScreen(new BlueprintJournalScreen());
            case 1 -> mc.setScreen(new ResearchDesignerChooserScreen(new TitleScreen()));
            case 2 -> {
                var bundle=java.nio.file.Path.of(System.getProperty("wrb.reviewServerDirectory", mc.gameDirectory.toPath().toRealPath().getParent().resolve("verification-server").toString())).resolve("review-project.zip");
                var report=com.google.gson.JsonParser.parseString(Files.readString(bundle.resolveSibling("core-verification.json"))).getAsJsonObject();
                if(!report.get("passed").getAsBoolean() || !"review-regressions".equals(report.get("suite").getAsString()))
                    throw new IllegalStateException("Run the server review checks successfully before the client checks");
                var imported=com.gamergaming.taczweaponblueprints.research.designer.io.ResearchDesignerExport.readProject(Files.readAllBytes(bundle));
                workspace=new ResearchDesignerWorkspaceScreen(new TitleScreen(),new ResearchDesignerSession(
                        imported.checkpoint().project().copyAs(UUID.randomUUID())),imported.capture(),false);
                mc.setScreen(workspace);
            }
            case 3 -> openDetail("openEditing");
            case 4 -> openDetail("openProperties");
            case 5 -> openDetail("openExport");
            case 6 -> {
                var constructor=Class.forName("com.gamergaming.taczweaponblueprints.client.designer.DesignerExportScreen").getDeclaredConstructor(Screen.class);
                constructor.setAccessible(true);mc.setScreen((Screen)constructor.newInstance(new TitleScreen()));
            }
        }
        active=mc.screen;
    }
    private static void openDetail(String name) throws ReflectiveOperationException {
        var method=ResearchDesignerWorkspaceScreen.class.getDeclaredMethod(name);
        method.setAccessible(true);method.invoke(workspace);
    }
    private static void finish(Minecraft mc,Throwable error){
        done=true;
        try{
            var report=new com.google.gson.JsonObject();RuntimeIdentity.addTo(report);report.addProperty("passed",error==null);report.addProperty("suite","review-regressions");
            report.add("checks",new com.google.gson.Gson().toJsonTree(checks));
            if(error!=null){report.addProperty("error",error.toString());com.mojang.logging.LogUtils.getLogger().error("WRB_REVIEW CLIENT FAILURE",error);}
            Files.writeString(Path.of("core-verification.json"),new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(report));
        }catch(Exception writeError){throw new IllegalStateException(writeError);}finally{mc.stop();}
    }
}
