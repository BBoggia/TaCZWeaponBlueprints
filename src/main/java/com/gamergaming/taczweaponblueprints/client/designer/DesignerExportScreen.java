package com.gamergaming.taczweaponblueprints.client.designer;

import java.nio.file.*;
import java.util.*;
import com.google.gson.*;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.io.*;

/** Reviewed local export/import. It never targets a world directory or changes server settings. */
final class DesignerExportScreen extends Screen {
    private final ResearchDesignerWorkspaceScreen host;private final Screen parent;
    private final String page="EXPORT";
    private String reviewedFilename="",reviewedDigest="";
    private ResearchDesignerExport.Prepared prepared;private Path output;private String filename="",error="";
    private List<String> lines=List.of();private int token,offset,x,w;private boolean busy,started,closed;
    private EditBox name;
    DesignerExportScreen(ResearchDesignerWorkspaceScreen host){super(Component.literal(DesignerText.tr("export.text_0")));this.host=host;parent=null;filename=host.editingSession().project().namespace().replace('.','-')+"-"+host.editingSession().project().digest().substring(0,8);}
    DesignerExportScreen(Screen parent){super(Component.literal(DesignerText.tr("export.text_1")));this.parent=parent;host=null;}
    private boolean current(){return !closed&&(host!=null?host.isDetail(this):minecraft.screen==this);}
    private Path game(){return minecraft.gameDirectory.toPath();}
    private Button button(int px,int y,int width,String label,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->{if(!busy)try{action.run();}catch(RuntimeException e){error=Objects.toString(e.getMessage(),"Operation unavailable");}}).bounds(px,y,width,20).build());}
    private <T> void work(java.util.concurrent.Callable<T> call,java.util.function.Consumer<T> success){busy=true;error="";int request=++token;rebuildWidgets();DesignerClientWork.submit(call,result->{if(request!=token||!current())return;busy=false;try{success.accept(result);}catch(RuntimeException e){error=Objects.toString(e.getMessage(),"Operation failed");}if(current())rebuildWidgets();},e->{if(request!=token||!current())return;busy=false;error=Objects.toString(e.getMessage(),"Operation failed");rebuildWidgets();});}
    @Override protected void init(){x=Math.max(10,width/2-230);w=Math.min(460,width-20);
        name=addRenderableWidget(new EditBox(font,x,48,w,20,Component.literal(host==null?DesignerText.tr("export.text_2"):DesignerText.tr("export.text_3"))));name.setMaxLength(host==null?132:64);name.setValue(filename);name.setResponder(v->{filename=v;});
        int rows=Math.max(1,(height-154)/22);offset=Math.min(offset,Math.max(0,((lines.size()-1)/rows)*rows));
        for(int i=0;i<rows&&offset+i<lines.size();i++){String line=lines.get(offset+i);addRenderableWidget(new ReportRow(x,76+i*22,w,line));}
        button(x,height-70,72,DesignerText.tr("export.text_4"),()->{offset-=rows;rebuildWidgets();}).active=offset>0&&!busy;button(x+76,height-70,72,DesignerText.tr("export.text_5"),()->{offset+=rows;rebuildWidgets();}).active=offset+rows<lines.size()&&!busy;
        button(x,height-26,72,DesignerText.tr("export.text_6"),this::onClose);
        button(x+76,height-26,120,DesignerText.tr("export.text_7"),()->{Path directory=game();Path exported=output;work(()->exported!=null?exported:ResearchDesignerExport.localFolder(directory,host==null),p->Util.getPlatform().openFile(p.toFile()));});
        if(host==null){button(x,height-48,140,DesignerText.tr("export.text_8"),()->{String chosen=filename;Path directory=game();work(()->ResearchDesignerExport.reviewImport(directory,chosen),value->{reviewedFilename=chosen;reviewedDigest=value.bundleDigest();lines=List.of("Ready to import: "+value.checkpoint().project().title(),"Nodes: "+value.checkpoint().project().draft().nodes().size(),"Namespace: "+value.checkpoint().project().namespace(),"Captured context included: "+value.capture().isPresent(),"Creates a new local project. Stable content IDs are retained.","Current checkpoint is retained. Session Undo history is not shared.");offset=0;});});button(x+w-140,height-48,140,DesignerText.tr("export.text_9"),()->{if(!filename.equals(reviewedFilename))throw new IllegalStateException("Review this bundle before importing it");String chosen=filename,expected=reviewedDigest;Path directory=game();work(()->{var session=ResearchDesignerExport.importProject(directory,chosen,expected);return new ImportedSession(session,new ResearchDesignerCaptureStore(directory).load(session.project()));},value->minecraft.setScreen(new ResearchDesignerWorkspaceScreen(parent,value.session(),value.capture(),false)));});}

        else button(x+w-140,height-48,140,output==null?DesignerText.tr("export.text_10"):DesignerText.tr("export.text_11"),()->{if(prepared==null)throw new IllegalStateException("Wait for export validation");if(!prepared.projectDigest().equals(host.editingSession().project().digest()))throw new IllegalStateException("Project changed; reopen export review");String chosen=filename;Path directory=game();var value=prepared;work(()->ResearchDesignerExport.publish(directory,chosen,value),path->{output=path;lines=new ArrayList<>(List.of("Exported locally. Server activation has not been attempted.",path.toString(),"datapack.zip — installable datapack","editable-project.zip — editable project and capture","export-review.json — dependencies and settings","INSTALL.txt — installation and profile selection"));offset=0;});}).active=prepared!=null&&output==null&&!busy;
        if(!started){started=true;if(host==null)lines=List.of(DesignerText.tr("export.text_15"),DesignerText.tr("export.text_16"),DesignerText.tr("export.text_17"),DesignerText.tr("export.text_18"),DesignerText.tr("export.text_19"));else{var checkpoint=host.editingSession().checkpoint();var capture=host.editingCapture();work(()->ResearchDesignerExport.prepare(checkpoint,capture),value->{prepared=value;var review=DesignerJson.parse(value.review(),DesignerArchive.MAX_ENTRY);var items=new ArrayList<String>();items.add(DesignerText.tr("export.text_20"));items.add("Minecraft 1.20.1 • datapack format 15");items.add("Required active profile: "+review.get("active_profile").getAsString());items.add(DesignerText.tr("export.text_21"));for(String key:List.of("dependencies","required_settings","captured_dependencies","captured_settings"))if(review.has(key))flatten(items,key,review.get(key));review.getAsJsonArray("compatibility_notices").forEach(n->items.add(n.getAsString()));items.add(DesignerText.tr("export.text_22"));items.add(DesignerText.tr("export.text_23"));lines=List.copyOf(items);});}}
    }
    /** Focusable evidence rows expose full text through keyboard narration and hover. */
    private final class ReportRow extends AbstractWidget {
        ReportRow(int x,int y,int width,String text){super(x,y,width,20,Component.literal(text));setTooltip(Tooltip.create(getMessage()));}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float delta){g.fill(getX(),getY(),getX()+width,getY()+height,isHoveredOrFocused()?0xFF253D4D:0xFF172B39);g.drawString(font,font.plainSubstrByWidth(getMessage().getString(),width-12),getX()+6,getY()+6,0xCDD9E0,false);}
        @Override protected void updateWidgetNarration(net.minecraft.client.gui.narration.NarrationElementOutput output){output.add(net.minecraft.client.gui.narration.NarratedElementType.TITLE,getMessage());}
    }
    private record ImportedSession(ResearchDesignerSession session,Optional<ResearchDesignerCapture> capture){}
    private static void flatten(List<String> lines,String prefix,JsonElement value){if(lines.size()>=1024)return;if(value.isJsonObject()&&!value.getAsJsonObject().entrySet().isEmpty())value.getAsJsonObject().entrySet().forEach(e->flatten(lines,prefix+" / "+e.getKey(),e.getValue()));else if(value.isJsonArray()&&value.getAsJsonArray().size()>0)for(int i=0;i<value.getAsJsonArray().size();i++)flatten(lines,prefix+" / "+(i+1),value.getAsJsonArray().get(i));else lines.add(prefix+": "+value);}
    @Override public void tick(){name.tick();}
    @Override public void onClose(){closed=true;token++;if(host!=null)host.closeDetail();else minecraft.setScreen(parent);}
    @Override public void removed(){closed=true;token++;}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){g.fill(0,0,width,height,0xFF101C27);g.drawString(font,font.plainSubstrByWidth(getTitle().getString(),w),x,12,0x70D8CD,false);g.drawString(font,font.plainSubstrByWidth(busy?DesignerText.tr("export.text_12"):host==null?DesignerText.tr("export.text_13"):DesignerText.tr("export.text_14"),w),x,30,0x9BACB9,false);super.render(g,mx,my,delta);if(!error.isEmpty()){g.drawString(font,font.plainSubstrByWidth(error,w-200),x+200,height-20,0xFFCA88,false);if(my>height-38)g.renderTooltip(font,font.split(Component.literal(error),w-12),mx,my);}}
}
