package com.gamergaming.taczweaponblueprints.client.designer;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.io.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.*;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/** Disposable, opt-in native-screen control. No world or server is opened. */
@EventBusSubscriber(modid="taczweaponblueprints", value=Dist.CLIENT)
public final class DesignerWorkspaceClientProbe {
    private static final String ROOT=System.getProperty("tacz.designerWorkspaceQA.root");
    private static boolean ready;
    private static UUID lastProject;
    private static Object field(Object object,String name) throws Exception {var f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
    private static Screen active(Minecraft mc) throws Exception {if(mc.screen instanceof ResearchDesignerWorkspaceScreen){var editor=field(mc.screen,"editor");if(editor instanceof Screen screen)return screen;var build=field(mc.screen,"build");if(build instanceof Screen screen)return screen;}return mc.screen;}
    private static int activeHeight(Minecraft mc){return mc.screen.height;}
    private static ResearchDesignerSession session(Minecraft mc) throws Exception {return (ResearchDesignerSession)field(mc.screen,"session");}
    @SubscribeEvent public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post event) {
        if(ROOT==null)return;
        var mc=Minecraft.getInstance();if(mc.getOverlay()!=null)return;
        var root=Path.of(ROOT);
        try {
            if(!ready&&mc.screen!=null){ready=true;Files.writeString(root.resolve("ready.json"),"{\"ready\":true}");}
            var command=root.resolve("control.json");if(!ready||!Files.isRegularFile(command))return;
            if(Files.size(command)>4096)throw new IllegalArgumentException("Oversized control");
            var input=JsonParser.parseString(Files.readString(command)).getAsJsonObject();Files.delete(command);
            String action=input.get("action").getAsString();var report=new JsonObject();
            switch(action) {
                case "build_click", "build_drag" -> {
                    var build=(DesignerBuildScreen)field(mc.screen,"build");var camera=(com.gamergaming.taczweaponblueprints.client.ResearchTreeViewport)field(build,"camera");
                    var node=session(mc).project().draft().nodes().stream().filter(n->n.blueprintId().filter(input.get("blueprint").getAsString()::equals).isPresent()).findFirst().orElseThrow();
                    build.focus(node.id());var tile=build.geometry().tiles().stream().filter(t->t.id().equals(node.id())).findFirst().orElseThrow();
                    int left=(int)field(build,"canvasX");double x=left+camera.viewportX(tile.x()+45),y=26+camera.viewportY(tile.y()+24);
                    mc.screen.mouseClicked(x,y,0);
                    if(action.equals("build_drag")){double tx=x+input.get("dx").getAsDouble()*camera.scale(),ty=y+input.get("dy").getAsDouble()*camera.scale();for(int i=1;i<=10;i++)mc.screen.mouseDragged(x+(tx-x)*i/10,y+(ty-y)*i/10,0,(tx-x)/10,(ty-y)/10);mc.screen.mouseReleased(tx,ty,0);}else mc.screen.mouseReleased(x,y,0);
                }
                case "open" -> {
                    String fixture=input.get("fixture").getAsString();
                    var capture=ResearchDesignerCapture.open(Files.readAllBytes(Path.of(System.getProperty("tacz.designerWorkspaceQA.capture"))));
                    if(fixture.equals("missing_art")) {
                        var source=JsonParser.parseString(capture.json().toString().replace("tacz:aa12","qa_designer:missing_art")).getAsJsonObject();
                        var revisions=source.getAsJsonObject("revisions").deepCopy();
                        for(String key:List.of("format","capture_id","source_kind","disclosure","session_token","revisions","dependency_fingerprint"))source.remove(key);
                        capture=ResearchDesignerCapture.create(UUID.randomUUID(),revisions,source);
                    }
                    var project=fixture.equals("empty")?ResearchDesignerProject.create("qa_designer","Empty research tree"):
                        ResearchDesignerConversion.createCustom(capture,"qa_designer","Captured tree with a deliberately long project title for native layout checks").project();
                    if(fixture.equals("small")||fixture.equals("missing")) {
                        var json=project.json();var nodes=json.getAsJsonObject("draft").getAsJsonArray("nodes");var keep=new JsonArray();
                        for(int i=0;i<nodes.size()&&keep.size()<5;i++){var node=nodes.get(i).deepCopy().getAsJsonObject();if(!node.getAsJsonObject("placement").get("domain").getAsString().equals("weapons"))continue;node.add("requirements",new JsonArray());
                            if(fixture.equals("missing")&&keep.isEmpty())node.remove("blueprint_id");keep.add(node);}
                        json.getAsJsonObject("draft").add("nodes",keep);
                        var branchIds=new HashSet<String>();keep.forEach(n->{if(n.getAsJsonObject().has("branch_id"))branchIds.add(n.getAsJsonObject().get("branch_id").getAsString());});
                        var tree=json.getAsJsonObject("draft").getAsJsonObject("tree").getAsJsonObject("definition");
                        var retained=new JsonArray();if(tree.has("branches"))tree.getAsJsonArray("branches").forEach(b->{if(branchIds.contains(b.getAsJsonObject().get("id").getAsString()))retained.add(b);});
                        tree.add("branches",retained);project=ResearchDesignerProject.fromJson(json);
                    }
                    lastProject=project.projectId();var session=new ResearchDesignerSession(project);var store=new ResearchDesignerProjectStore(root);store.save(session);
                    new ResearchDesignerCaptureStore(root).save(capture);
                    mc.setScreen(new ResearchDesignerWorkspaceScreen(new ResearchDesignerChooserScreen(new TitleScreen()),session,fixture.equals("empty")?Optional.empty():Optional.of(capture),false));
                }
                case "connect" -> net.minecraft.client.gui.screens.ConnectScreen.startConnecting(new TitleScreen(),mc,net.minecraft.client.multiplayer.resolver.ServerAddress.parseString("127.0.0.1:25579"),new net.minecraft.client.multiplayer.ServerData("Disposable Designer acceptance","127.0.0.1:25579",net.minecraft.client.multiplayer.ServerData.Type.OTHER), false, null);
                case "integrated" -> mc.createWorldOpenFlows().openWorld("designer-disposable-world", () -> mc.setScreen(new TitleScreen()));
                case "leave_server" -> {if(mc.level!=null)mc.level.disconnect();mc.disconnect();mc.setScreen(new TitleScreen());}
                case "capture_request" -> ClientDesignerCapture.request();
                case "server_reload" -> mc.player.connection.sendCommand("reload");
                case "bench_select" -> {var method=mc.screen.getClass().getDeclaredMethod("selectTreeNode",net.minecraft.resources.ResourceLocation.class);method.setAccessible(true);method.invoke(mc.screen,net.minecraft.resources.ResourceLocation.parse(input.get("blueprint").getAsString()));}
                case "bench_research" -> {var button=(Button)field(mc.screen,"primaryResearchButton");if(!button.active)throw new IllegalStateException("Research button disabled");mc.screen.mouseClicked(button.getX()+2,button.getY()+2,0);mc.screen.mouseReleased(button.getX()+2,button.getY()+2,0);}
                case "scroll" -> mc.screen.mouseScrolled(input.get("x").getAsDouble(),input.get("y").getAsDouble(), 0,input.get("amount").getAsDouble());
                case "click" -> {mc.screen.mouseClicked(input.get("x").getAsDouble(),input.get("y").getAsDouble(),0);mc.screen.mouseReleased(input.get("x").getAsDouble(),input.get("y").getAsDouble(),0);}
                case "state" -> {}
                case "edit" -> session(mc).execute("Test title edit",ResearchDesignerEdits.title(input.get("title").getAsString()));
                case "reopen" -> {
                    var session=new ResearchDesignerProjectStore(root).load(lastProject).session(input.has("recovery")&&input.get("recovery").getAsBoolean());
                    mc.setScreen(new ResearchDesignerWorkspaceScreen(new ResearchDesignerChooserScreen(new TitleScreen()),session,new ResearchDesignerCaptureStore(root).load(session.project()),input.has("recovery")&&input.get("recovery").getAsBoolean()));
                }
                case "conflict" -> {
                    var other=new ResearchDesignerProjectStore(root).load(lastProject).session(false);other.execute("External edit",ResearchDesignerEdits.title("External change must survive"));new ResearchDesignerProjectStore(root).save(other);
                }
                case "disconnect" -> mc.setScreen(new TitleScreen());
                case "language" -> {mc.getLanguageManager().setSelected(input.get("code").getAsString());mc.reloadResourcePacks();}
                case "chooser" -> mc.setScreen(new ResearchDesignerChooserScreen(new TitleScreen()));
                case "resize" -> {
                    int w=input.get("width").getAsInt(),h=input.get("height").getAsInt(),scale=input.get("scale").getAsInt();
                    if(w<640||w>2200||h<480||h>1400||scale<1||scale>4)throw new IllegalArgumentException("Size out of range");
                    GLFW.glfwSetWindowSize(mc.getWindow().getWindow(),w,h);mc.options.guiScale().set(scale);mc.resizeDisplay();
                }
                case "screenshot" -> Screenshot.grab(root.toFile(),input.get("name").getAsString()+".png",mc.getMainRenderTarget(),ignored->{});
                case "button" -> {
                    String label=input.get("label").getAsString();var button=active(mc).children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.getMessage().getString().equals(label)).findFirst().orElseThrow();
                    if(!button.active)throw new IllegalStateException("Button disabled");mc.screen.mouseClicked(button.getX()+button.getWidth()/2.,button.getY()+10,0);mc.screen.mouseReleased(button.getX()+2,button.getY()+2,0);
                }
                case "key" -> {int key=input.get("key").getAsInt();mc.screen.keyPressed(key,0,0);mc.screen.keyReleased(key,0,0);}
                case "keyboard_button", "keyboard_field" -> {
                    var screen=active(mc);String label=input.get("label").getAsString();boolean focused=false;
                    for(int i=0;i<screen.children().size()+2;i++){var current=screen.getFocused();if(current instanceof AbstractWidget widget&&widget.getMessage().getString().equals(label)){focused=true;break;}mc.screen.keyPressed(GLFW.GLFW_KEY_TAB,0,0);}
                    if(!focused)throw new IllegalStateException("Keyboard could not reach "+label);
                    if(action.equals("keyboard_button"))mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0);
                    else{mc.screen.keyPressed(GLFW.GLFW_KEY_END,0,0);for(int i=0;i<160;i++)mc.screen.keyPressed(GLFW.GLFW_KEY_BACKSPACE,0,0);for(char c:input.get("value").getAsString().toCharArray())mc.screen.charTyped(c,0);}
                }
                case "drag_node" -> {
                    var scene=(com.gamergaming.taczweaponblueprints.client.DesignerSceneAdapter)field(mc.screen,"scene");
                    var a=scene.canvas.nodeBounds(net.minecraft.resources.ResourceLocation.parse(input.get("source").getAsString())).orElseThrow();
                    var b=scene.canvas.nodeBounds(net.minecraft.resources.ResourceLocation.parse(input.get("target").getAsString())).orElseThrow();
                    var v=scene.canvas.viewport();var bounds=scene.canvas.bounds();
                    double ax=bounds.x()+v.viewportX(a.x()+a.width()/2),ay=bounds.y()+v.viewportY(a.y()+a.height()/2);
                    double bx=bounds.x()+v.viewportX(b.x()+b.width()/2),by=bounds.y()+v.viewportY(b.y()+b.height()/2);
                    long sequence=session(mc).sequence();mc.screen.keyPressed(GLFW.GLFW_KEY_M,0,0);mc.screen.mouseClicked(ax,ay,0);
                    for(int i=1;i<=40;i++)mc.screen.mouseDragged(ax+(bx-ax)*i/40,ay+(by-ay)*i/40,0,(bx-ax)/40,(by-ay)/40);
                    if(session(mc).sequence()!=sequence)throw new IllegalStateException("Drag motion mutated the document");
                    mc.screen.mouseReleased(bx,by,0);
                    if(session(mc).sequence()!=sequence)throw new IllegalStateException("Drop bypassed review");
                }
                case "field" -> active(mc).children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).filter(box->box.getMessage().getString().equals(input.get("label").getAsString())).findFirst().orElseThrow().setValue(input.get("value").getAsString());
                case "choose_index" -> {var buttons=active(mc).children().stream().filter(Button.class::isInstance).map(Button.class::cast).filter(b->b.active&&b.getY()>=42&&b.getY()<activeHeight(mc)-80).toList();var button=buttons.get(input.get("index").getAsInt());mc.screen.mouseClicked(button.getX()+button.getWidth()/2.,button.getY()+button.getHeight()/2.,0);mc.screen.mouseReleased(button.getX()+1,button.getY()+1,0);}
                case "search" -> active(mc).children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).findFirst().orElseThrow().setValue(input.get("text").getAsString());
                case "reload" -> mc.reloadResourcePacks();
                case "close" -> mc.stop();
                default -> throw new IllegalArgumentException("Unknown control");
            }
            if(mc.screen!=null){report.addProperty("width",mc.screen.width);report.addProperty("height",mc.screen.height);report.addProperty("screen",mc.screen.getClass().getSimpleName());
                var widgets=new JsonArray();for(var child:active(mc).children())if(child instanceof AbstractWidget widget){var item=new JsonObject();item.addProperty("label",widget.getMessage().getString());item.addProperty("x",widget.getX());item.addProperty("y",widget.getY());item.addProperty("w",widget.getWidth());item.addProperty("h",widget.getHeight());item.addProperty("active",widget.active);widgets.add(item);}report.add("widgets",widgets);}
            if(mc.screen instanceof ResearchDesignerWorkspaceScreen){var session=session(mc);var state=new JsonObject();state.addProperty("project",session.project().projectId().toString());state.addProperty("title",session.project().title());state.addProperty("dirty",session.dirty());
                var build=(DesignerBuildScreen)field(mc.screen,"build");state.addProperty("draft_visible",build.geometry().tiles().size());state.addProperty("build_pane",field(build,"pane").toString());state.addProperty("build_typing",build.typing());
                state.addProperty("sequence",session.sequence());state.addProperty("selected",(String)field(mc.screen,"selected"));state.addProperty("domain",field(mc.screen,"domain").toString());state.addProperty("status",(String)field(mc.screen,"status"));
                var tree=(com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication)field(mc.screen,"publication");state.addProperty("graph_nodes",tree==null?0:tree.graph().nodes().size());
                var scene=(com.gamergaming.taczweaponblueprints.client.DesignerSceneAdapter)field(mc.screen,"scene");state.add("camera",new Gson().toJsonTree(scene.canvas.viewport().snapshot()));
                if(field(mc.screen,"editor")!=null){var editor=field(mc.screen,"editor");state.addProperty("editor_page",field(editor,"page").toString());state.addProperty("editor_error",(String)field(editor,"error"));if(editor instanceof DesignerPolicyPanel){state.add("simulation",new Gson().toJsonTree(field(editor,"scenario")));var simulated=(DesignerSimulation.Result)field(editor,"result");if(simulated!=null){state.addProperty("simulation_ready",simulated.ready());state.add("simulation_checks",new Gson().toJsonTree(simulated.checks()));state.addProperty("simulated_public_nodes",simulated.evaluation().tree().graph().nodes().size());}state.addProperty("detail_busy",(boolean)field(editor,"busy"));}}
                state.add("draft",session.project().json().get("draft"));
                state.addProperty("draft_nodes",session.project().draft().nodes().size());state.addProperty("diagnostics",((List<?>)field(mc.screen,"diagnostics")).size());report.add("workspace",state);
            }
            report.addProperty("connected",mc.getConnection()!=null);report.addProperty("operator",mc.player!=null&&mc.player.hasPermissions(2));report.addProperty("capture_pending",ClientDesignerCapture.pending());report.addProperty("capture_present",ClientDesignerCapture.captured().isPresent());report.addProperty("capture_status",ClientDesignerCapture.status().map(Object::toString).orElse("none"));
            if(mc.screen instanceof com.gamergaming.taczweaponblueprints.client.ResearchBenchScreen bench)report.add("bench_preview",DesignerValues.evidence(bench.getMenu().preview()));
            report.addProperty("status","ok");Files.writeString(root.resolve("control-result.json"),report.toString());
        }catch(Exception error){try{Files.writeString(root.resolve("control-result.json"),new Gson().toJson(Map.of("status","failed","error",error.toString())));}catch(Exception ignored){}error.printStackTrace();}
    }
}
