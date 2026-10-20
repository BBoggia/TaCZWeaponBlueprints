package com.gamergaming.taczweaponblueprints.client.designer;

import java.nio.file.*;
import java.util.*;
import static com.gamergaming.taczweaponblueprints.client.designer.DesignerText.tr;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.io.*;

/** Local project chooser, independently accessible from the bench and usable without a world. */
public final class ResearchDesignerChooserScreen extends Screen {
    private final Screen parent;
    private EditBox namespace,titleBox;
    private List<Entry> entries = List.of(); private int offset; private UUID selected;
    private boolean busy,capturing,emptyCapture,needsRefresh=true; private String status = "";
    private record Entry(UUID id,String title,boolean recovery) {}
    public ResearchDesignerChooserScreen(Screen parent) { super(Component.translatable("gui.taczweaponblueprints.designer.open")); this.parent=parent; }
    private Path game() { return minecraft.gameDirectory.toPath(); }
    @Override protected void init() {
        String ns=namespace==null?"custom_research":namespace.getValue(), title=titleBox==null?tr("default_title"):titleBox.getValue();
        int x=Math.max(8,width/2-180), w=Math.min(360,width-16);
        namespace=addRenderableWidget(new EditBox(font,x,48,w/2-4,20,Component.literal(tr("namespace_field")))); namespace.setMaxLength(48);namespace.setValue(ns);
        titleBox=addRenderableWidget(new EditBox(font,x+w/2+4,48,w/2-4,20,Component.literal(tr("title_field"))));titleBox.setMaxLength(80);titleBox.setValue(title);
        boolean operator=minecraft.player!=null&&minecraft.player.hasPermissions(2);
        var customize=button(x,76,w,tr("build.customize_server"),()->{emptyCapture=false;ClientDesignerCapture.request();capturing=true;status=tr("requesting");});customize.active=operator;customize.setTooltip(Tooltip.create(Component.literal(tr("build.capture_hint"))));
        var empty=button(x,100,w,tr("build.empty_server"),()->{emptyCapture=true;ClientDesignerCapture.request();capturing=true;status=tr("requesting");});empty.active=operator;empty.setTooltip(Tooltip.create(Component.literal(tr("build.capture_hint"))));
        int rows=Math.max(1,(height-218)/22);
        for(int i=0;i<rows&&offset+i<entries.size();i++) {
            var entry=entries.get(offset+i);
            button(x,146+i*22,w,(entry.id().equals(selected)?"> ":"")+entry.title()+(entry.recovery()?tr("recovery_suffix"):""),()->{selected=entry.id();rebuildWidgets();});
        }
        button(x,height-60,78,tr("open_saved"),()->open(false)).active=selected!=null;
        button(x+82,height-60,88,tr("recover"),()->open(true)).active=selected!=null&&entries.stream().anyMatch(e->e.id().equals(selected)&&e.recovery());
        button(x+w-100,height-60,48,tr("previous"),()->{offset=Math.max(0,offset-rows);rebuildWidgets();}).active=offset>0;
        button(x+w-48,height-60,48,tr("next"),()->{offset+=rows;rebuildWidgets();}).active=offset+rows<entries.size();
        button(x,height-34,78,tr("back"),this::onClose);
        button(x+82,height-34,64,tr("build.blank_button"),()->create(false)).setTooltip(Tooltip.create(Component.literal(tr("build.blank_offline"))));
        button(x+w-100,height-34,100,DesignerText.tr("export.import_project"),()->minecraft.setScreen(new DesignerExportScreen(this)));
        if(needsRefresh&&!busy){needsRefresh=false;refresh();}
    }
    private Button button(int x,int y,int w,String text,Runnable action){return addRenderableWidget(Button.builder(Component.literal(text),b->{if(!busy&&!capturing)action.run();}).bounds(x,y,w,20).build());}
    private void refresh(){
        busy=true;status=tr("loading");Path game=game();
        DesignerClientWork.submit(()->{
            var store=new ResearchDesignerProjectStore(game);var root=game.resolve("taczweaponblueprints/designer/projects");var result=new ArrayList<Entry>();
            try(var paths=Files.list(root)) {
                for(var path:paths.filter(p->Files.isDirectory(p,LinkOption.NOFOLLOW_LINKS)).sorted().limit(257).toList()) {
                    if(result.size()==256)break;
                    UUID id;try{id=UUID.fromString(path.getFileName().toString());}catch(IllegalArgumentException invalid){continue;}
                    try{var loaded=store.load(id);String title=loaded.document().flatMap(ResearchDesignerProject.Opened::editable).map(ResearchDesignerProject::title)
                            .orElseGet(()->loaded.recovery().map(c->c.project().title()).orElse(id.toString()));result.add(new Entry(id,title,loaded.recovery().isPresent()));}
                    catch(Exception invalid){result.add(new Entry(id,tr("unreadable_prefix")+id.toString().substring(0,8),false));}
                }
            }return List.copyOf(result);
        },result->{busy=false;entries=result;status=result.isEmpty()?tr("no_projects"):tr("choose");rebuildWidgets();},this::failed);
    }
    private void create(boolean captured){
        if(busy)return;String ns=namespace.getValue().trim(),title=titleBox.getValue().trim();
        var source=captured?ClientDesignerCapture.captured():Optional.<ResearchDesignerCapture>empty();
        if(captured&&source.isEmpty()){status=tr("capture_unavailable");return;}
        busy=true;Path game=game();boolean startEmpty=captured&&emptyCapture;
        DesignerClientWork.submit(()->{
            ResearchDesignerProject project=source.isPresent()?ResearchDesignerConversion.createCustom(source.get(),ns,title).project():ResearchDesignerProject.create(ns,title);
            if(startEmpty)project=ResearchDesignerStructure.reset(project).result();
            var session=new ResearchDesignerSession(project);var store=new ResearchDesignerProjectStore(game);
            if(source.isPresent())new ResearchDesignerCaptureStore(game).save(source.get());store.save(session);return session;
        },session->{busy=false;if(minecraft.screen==this)minecraft.setScreen(new ResearchDesignerWorkspaceScreen(this,session,source,false));},this::failed);
    }
    private void open(boolean recovery){
        if(busy||selected==null)return;busy=true;UUID id=selected;Path game=game();
        DesignerClientWork.submit(()->{var loaded=new ResearchDesignerProjectStore(game).load(id);return loaded.session(recovery);},session->{
            DesignerClientWork.submit(()->new ResearchDesignerCaptureStore(game).load(session.project()),capture->{busy=false;
                if(minecraft.screen==this)minecraft.setScreen(new ResearchDesignerWorkspaceScreen(this,session,capture,recovery));},this::failed);
        },this::failed);
    }
    private void failed(Exception error){busy=false;status=tr("open_failed")+Objects.toString(error.getMessage(),error.getClass().getSimpleName());}
    @Override public void tick(){super.tick();if(capturing){
        if(ClientDesignerCapture.captured().isPresent()){capturing=false;create(true);}
        else if(!ClientDesignerCapture.pending()){capturing=false;status=tr("capture_prefix")+ClientDesignerCapture.status().map(Enum::name).orElse(tr("cancelled"))+tr("capture_requirement");}
    }}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,0xFF0C141D);g.drawCenteredString(font,getTitle(),width/2,16,0xFFE4ECEF);
        int x=Math.max(8,width/2-180);g.drawString(font,tr("namespace"),x,36,0xFFA4BAC7);g.drawString(font,tr("title"),x+Math.min(360,width-16)/2+4,36,0xFFA4BAC7);
        g.drawString(font,tr("saved_projects"),x,134,0xFFE4ECEF);}
    @Override public void render(GuiGraphics g,int mx,int my,float partial){super.render(g,mx,my,partial);
        g.drawString(font,font.plainSubstrByWidth(status,width-16),8,height-10,0xFFA4BAC7);if(my>=height-12)g.renderTooltip(font,font.split(Component.literal(status),width-20),mx,my);
    }
    @Override public void removed(){needsRefresh=true;ClientDesignerCapture.cancel();capturing=false;super.removed();}
    @Override public void onClose(){ClientDesignerCapture.cancel();minecraft.setScreen(parent instanceof ResearchDesignerWorkspaceScreen?null:parent);}
    @Override public boolean isPauseScreen(){return false;}
}
