package com.gamergaming.taczweaponblueprints.client.designer;

import java.util.*;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeViewport;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import static com.gamergaming.taczweaponblueprints.client.designer.DesignerText.tr;

/** Persistent Build workspace. The canvas is a draft document, never a player publication. */
final class DesignerBuildScreen extends Screen {
    enum Pane { LIBRARY, SELECTED, GROUPS, BRANCHES, BRANCH, REQUIREMENTS, MATERIALS, ITEMS, HELP, PROBLEMS, PROJECT }
    private final ResearchDesignerWorkspaceScreen host;
    private final ResearchTreeViewport camera=new ResearchTreeViewport();
    private final JsonObject catalog,resolved;
    private String currency="RP";
    private final Map<String,DesignerCatalogArt.Art> icons=new HashMap<>();
    private Pane pane=Pane.LIBRARY;
    private DesignerDraftLayout geometry;
    private Map<String,ResearchDesignerDraft.Node> nodes=Map.of();
    private Map<String,DesignerDraftLayout.Tile> placed=Map.of();
    private Set<String> incomplete=Set.of();
    private final Set<String> collapsed=new HashSet<>();
    private long revision=-1;
    private String geometryDigest="";
    private String geometryDomain="",query="",addParent,connectParent,assignTarget,editingGroup,branchId;
    private String dragNode;private double pressX,pressY,dragX,dragY;private boolean moved,panning,initialized;
    private int page,tier,side,inspect,canvasX,canvasW,canvasH,formX,formW;
    private String branchText;
    private int costLabelX=-1,costLabelY;
    private boolean any;
    private final Set<String> requirements=new LinkedHashSet<>();
    private static final int TOP=26, FOOT=4;
    private int libraryOffset,libraryTotal,libraryRows;
    private boolean scrollingCatalog;
    private String lastStatus="";private long statusChanged;
    DesignerBuildScreen(ResearchDesignerWorkspaceScreen host){super(Component.literal("Build"));this.host=host;
        var context=host.editingCapture().map(ResearchDesignerCapture::json).orElseGet(JsonObject::new);
        catalog=context.has("catalog")?context.getAsJsonObject("catalog"):new JsonObject();resolved=context.has("resolved_nodes")?context.getAsJsonObject("resolved_nodes"):new JsonObject();
        host.editingCapture().ifPresent(c->{try{currency=DesignerCapturedSettings.progression(c,new ResourceLocation(project().draft().profile().id())).currencyPolicy().item().map(id->BuiltInRegistries.ITEM.containsKey(id)?BuiltInRegistries.ITEM.get(id).getDescription().getString():id.toString()).orElse("RP");}catch(RuntimeException ignored){currency=tr("build.currency");}});
    }
    private ResearchDesignerSession session(){return host.editingSession();}
    private ResearchDesignerProject project(){return session().project();}
    private String selected(){return host.editingPrimary();}
    private ResearchDesignerDraft.Node selectedNode(){return ResearchDesignerStructure.node(project(),selected());}
    private String name(String id){var v=catalog.getAsJsonObject(id);return v!=null&&v.has("name_key")?com.gamergaming.taczweaponblueprints.client.DesignerSceneAdapter.name(new ResourceLocation(id),v.get("name_key").getAsString()).getString():id;}
    private Button button(int x,int y,int w,String label,Runnable action){var b=new TextButton(x,y,Math.max(20,w),label,action);b.setTooltip(Tooltip.create(Component.literal(label)));return addRenderableWidget(b);}
    private void go(Pane next){pane=next;query="";page=0;libraryOffset=0;rebuildWidgets();}
    private void commit(ResearchDesignerStructure.Proposal proposal){host.applyQuick(proposal);sync();rebuildWidgets();}
    private void sync(){if(revision==session().sequence()&&geometryDomain.equals(host.editingDomain()))return;
        if(revision>=0&&geometryDigest.equals(project().digest())&&geometryDomain.equals(host.editingDomain())){revision=session().sequence();return;}
        revision=session().sequence();geometryDigest=project().digest();geometryDomain=host.editingDomain();geometry=DesignerDraftLayout.create(project().draft(),geometryDomain,collapsed);
        var map=new LinkedHashMap<String,ResearchDesignerDraft.Node>();project().draft().nodes().forEach(n->map.put(n.id(),n));nodes=Map.copyOf(map);
        var positions=new HashMap<String,DesignerDraftLayout.Tile>();geometry.tiles().forEach(t->nodes.get(t.id()).blueprintId().ifPresent(bp->positions.put(bp,t)));placed=Map.copyOf(positions);
        var declared=new HashSet<String>();nodes.values().forEach(n->n.blueprintId().ifPresent(declared::add));
        var missing=new HashSet<String>();nodes.values().forEach(n->{if(n.blueprintId().filter(catalog::has).isEmpty()||n.requirements().stream().anyMatch(r->r.anyOf().isEmpty()||!declared.containsAll(r.anyOf())))missing.add(n.id());});incomplete=Set.copyOf(missing);
        if(assignTarget!=null&&!nodes.containsKey(assignTarget))assignTarget=null;if(addParent!=null&&!nodes.containsKey(addParent))addParent=null;if(connectParent!=null&&!nodes.containsKey(connectParent))connectParent=null;
        camera.configure(Math.max(1,canvasW),Math.max(1,canvasH),geometry.width(),geometry.height());}
    boolean typing(){return getFocused() instanceof EditBox;}
    @Override protected void init(){
        costLabelX=-1;
        side=Math.min(204,Math.max(150,width/4));inspect=width>=820&&selected()!=null&&pane==Pane.LIBRARY?220:0;
        canvasX=side+6;canvasW=Math.max(60,width-canvasX-inspect-6);canvasH=Math.max(40,height-TOP-FOOT);
        sync();camera.configureAnchored(canvasW,canvasH,geometry.width(),geometry.height());
        if(!initialized){initialized=true;var v=session().view();if(!v.equals(ResearchDesignerSession.View.initial()))camera.restore(new ResearchTreeViewport.Snapshot(v.x(),v.y(),v.zoom()));else camera.fit();}
        button(6,3,58,tr("build.project"),()->go(pane==Pane.PROJECT?Pane.LIBRARY:Pane.PROJECT));
        button(68,3,40,tr("save"),host::saveBuild);
        button(112,3,40,tr("undo"),()->{host.undoBuild(false);sync();rebuildWidgets();}).active=session().canUndo();
        button(156,3,40,tr("redo"),()->{host.undoBuild(true);sync();rebuildWidgets();}).active=session().canRedo();
        button(200,3,40,tr("build.test"),host::openSimulation).active=host.editingCapture().isPresent();
        if(selected()!=null){
            int cell=Math.min(90,(canvasW-12)/2);
            button(canvasX+4,TOP+4,cell,tr("build.add_next"),()->{addParent=selected();assignTarget=null;connectParent=null;go(Pane.LIBRARY);}).active=host.editingCapture().isPresent();
            button(canvasX+8+cell,TOP+4,cell,connectParent==null?tr("build.connect"):tr("build.cancel_connect"),()->{connectParent=connectParent==null?selected():null;addParent=null;assignTarget=null;go(Pane.LIBRARY);host.buildStatus(connectParent==null?tr("build.local"):tr("build.pick_target"));});
        }
        int bottom=height-24;
        button(canvasX+4,bottom,Math.min(76,canvasW-96),tr("build.issues",host.buildProblems().stream().filter(d->d.severity()==ResearchDesignerCompiler.Severity.ERROR).count()),()->go(Pane.PROBLEMS));
        button(canvasX+canvasW-88,bottom,36,tr("fit"),camera::fit);
        button(canvasX+canvasW-48,bottom,20,"+",()->camera.zoomProportional(1,canvasW/2.,canvasH/2.));
        button(canvasX+canvasW-24,bottom,20,"−",()->camera.zoomProportional(-1,canvasW/2.,canvasH/2.));
        formX=6;formW=side-8;
        int tab=(formW-6)/3;button(formX,TOP,tab,tr("build.library"),()->{addParent=null;connectParent=null;assignTarget=null;go(Pane.LIBRARY);});button(formX+tab+3,TOP,tab,tr("build.node"),()->go(Pane.SELECTED));button(formX+2*(tab+3),TOP,tab,tr("build.branches"),()->go(Pane.BRANCHES));
        switch(pane){case LIBRARY->library();case SELECTED->inspector(54);case REQUIREMENTS->requirements();case GROUPS->groups();case BRANCHES->branches();case BRANCH->branch();case MATERIALS->materials();case ITEMS->items();case HELP->help();case PROBLEMS->problems();case PROJECT->projectMenu();}
        if(inspect>0){formX=width-inspect+6;formW=inspect-12;inspector(TOP+4);}
    }
    private void search(int y){var box=new EditBox(font,formX,y,formW,20,Component.literal(tr("build.search")));box.setMaxLength(128);box.setValue(query);box.setHint(Component.literal(tr("build.search")));box.setResponder(v->{query=v;page=0;libraryOffset=0;minecraft.execute(()->{if(host.isBuildActive()){rebuildWidgets();children().stream().filter(EditBox.class::isInstance).findFirst().ifPresent(this::setFocused);}});});addRenderableWidget(box);}
    private int count(int y){return Math.max(1,(height-58-y)/26);}
    private void pages(int size,int rows){page=Math.max(0,Math.min(page,Math.max(0,(size-1)/rows)));button(formX,height-54,(formW-4)/2,tr("previous"),()->{page--;rebuildWidgets();}).active=page>0;button(formX+(formW+4)/2,height-54,(formW-4)/2,tr("next"),()->{page++;rebuildWidgets();}).active=(page+1)*rows<size;}
    private String catalogDomain(String id){var c=catalog.getAsJsonObject(id);return c!=null&&c.has("kind")?switch(c.get("kind").getAsString()){case "ammo"->"ammo";case "attachment"->"attachments";default->"weapons";}:"weapons";}
    private int bench(String bp){try{var n=resolved.getAsJsonObject(bp);var p=n.getAsJsonObject("policy").getAsJsonObject("progression");return Integer.parseInt(p.get("research_tier").getAsString().replace("tier_",""));}catch(RuntimeException e){return 0;}}
    private void library(){
        button(formX,52,(formW-4)/2,tr("build."+host.editingDomain()),()->{host.nextBuildDomain();revision=-1;addParent=null;assignTarget=null;connectParent=null;tier=0;libraryOffset=0;sync();rebuildWidgets();camera.fit();});
        button(formX+(formW+4)/2,52,(formW-4)/2,tr("build.filter_tier",tier==0?tr("build.any"):tier),()->{tier=(tier+1)%4;libraryOffset=0;rebuildWidgets();});
        search(76);
        var ids=catalog.keySet().stream().filter(id->catalogDomain(id).equals(host.editingDomain())).filter(id->tier==0||bench(id)==tier).filter(id->(name(id)+" "+id).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).sorted(Comparator.comparing(this::name)).toList();
        libraryRows=Math.max(1,(height-104)/20);libraryTotal=ids.size();libraryOffset=Math.max(0,Math.min(libraryOffset,Math.max(0,libraryTotal-libraryRows)));
        int y=100;
        for(String id:ids.stream().skip(libraryOffset).limit(libraryRows).toList()){
            var existing=project().draft().nodes().stream().filter(n->n.blueprintId().filter(id::equals).isPresent()).findFirst();
            var row=new IconButton(formX,y,formW-8,name(id),id,existing.isPresent()?tr("build.added"):tr("build.add"),()->{
                if(existing.isPresent()){pick(existing.get().id());return;}
                var proposal=assignTarget==null?DesignerAuthoringActions.add(project(),host.editingCapture().orElseThrow(),id,Optional.ofNullable(addParent)):DesignerAuthoringActions.assign(project(),host.editingCapture().orElseThrow(),assignTarget,id);commit(proposal);assignTarget=null;
                String added=project().draft().nodes().stream().filter(n->n.blueprintId().filter(id::equals).isPresent()).findFirst().orElseThrow().id();host.buildSelect(added,false);focus(added);rebuildWidgets();
            });addRenderableWidget(row);y+=20;
        }
    }
    private void projectMenu(){
        button(formX,52,formW,tr("back"),host::onClose);
        button(formX,76,formW,tr("build.export"),host::openExport);
        button(formX,100,formW,tr("build.advanced"),host::openEditing);
        button(formX,124,formW,tr("build.help"),()->go(Pane.HELP));
        button(formX,148,formW,tr("build.add_root"),()->{addParent=null;assignTarget=null;connectParent=null;go(Pane.LIBRARY);}).active=host.editingCapture().isPresent();
    }
    private void scrollLibrary(int delta){var focused=getFocused();String label=focused instanceof AbstractWidget w?w.getMessage().getString():null;libraryOffset=Math.max(0,Math.min(libraryOffset+delta,Math.max(0,libraryTotal-libraryRows)));rebuildWidgets();if(label!=null)children().stream().filter(v->v instanceof AbstractWidget w&&w.getMessage().getString().equals(label)).findFirst().ifPresent(this::setFocused);}
    private void dragScrollbar(double y){int track=Math.max(1,height-104);int thumb=Math.max(12,track*libraryRows/Math.max(1,libraryTotal));libraryOffset=(int)Math.round(Math.max(0,Math.min(1,(y-100-thumb/2.)/Math.max(1,track-thumb)))*Math.max(0,libraryTotal-libraryRows));rebuildWidgets();}
    private ResearchDesignerSession.InputKey costKey(){return new ResearchDesignerSession.InputKey(selected(),"/quick/cost");}
    private JsonObject cost(){return cost(selectedNode());}
    private JsonObject cost(ResearchDesignerDraft.Node node){var local=node.policy().json().getAsJsonObject("research_cost");var fallback=project().draft().profile().definition().json().getAsJsonObject("research_cost");var result=local!=null?local.deepCopy():fallback!=null?fallback.deepCopy():new JsonObject();if(!result.has("points"))result.addProperty("points",0);return result;}
    private void inspector(int y){if(selected()==null||!nodes.containsKey(selected())){button(formX,y,formW,tr("build.help"),()->go(Pane.HELP));return;}
        var n=selectedNode();
        if(n.blueprintId().isEmpty()){button(formX,y,formW,tr("build.assign"),()->{assignTarget=n.id();addParent=null;go(Pane.LIBRARY);}).active=host.editingCapture().isPresent();return;}
        if(height<338){button(formX,y,formW,tr("build.cost_materials"),host::openProperties);y+=24;button(formX,y,formW,tr("build.requirements"),()->go(Pane.GROUPS));y+=24;button(formX,y,formW,tr("build.advanced"),host::openEditing);return;}
        button(formX,y,formW,name(n.blueprintId().orElse(n.id())),()->focus(n.id()));y+=24;
        final int costY=y+12;costLabelX=formX;costLabelY=y;var c=cost();var key=costKey();
        var box=new EditBox(font,formX,costY,Math.max(38,formW-56),20,Component.literal(tr("build.cost")));box.setMaxLength(10);box.setValue(session().pendingInputs().stream().filter(p->p.key().equals(key)).map(ResearchDesignerSession.PendingInput::text).findFirst().orElse(c.get("points").getAsString()));box.setResponder(v->session().pending("Edit cost",key,v));box.setTooltip(Tooltip.create(Component.literal(tr("build.cost_hint"))));addRenderableWidget(box);
        button(formX+formW-52,costY,52,tr("build.apply"),()->{int value=new java.math.BigDecimal(box.getValue()).intValueExact();if(value<0||value>1_000_000_000)throw new IllegalArgumentException(tr("build.whole_cost"));c.addProperty("points",value);host.applyQuickField(DesignerResearchProperties.change(project(),n.id(),DesignerResearchProperties.Field.COST,Optional.of(c)),key);rebuildWidgets();});y=costY+24;
        button(formX,y,formW,tr("build.materials"),()->go(Pane.MATERIALS));y+=24;
        var p=n.policy().json().getAsJsonObject("progression");String raw=p!=null&&p.has("research_tier")?p.get("research_tier").getAsString():"inherit";int level=raw.matches("tier_[123]")?Integer.parseInt(raw.substring(5)):0;
        button(formX,y,formW,tr("build.bench",level==0?tr("build.inherit"):level),()->{int next=(level+1)%4;commit(DesignerResearchProperties.change(project(),n.id(),DesignerResearchProperties.Field.BENCH,next==0?Optional.empty():Optional.of(new JsonPrimitive("tier_"+next))));});y+=24;
        button(formX,y,formW,tr("build.requirements"),()->go(Pane.GROUPS));y+=24;
        button(formX,y,formW,tr("build.make_branch"),()->{branchId=null;branchText=null;go(Pane.BRANCH);});y+=24;
        button(formX,y,formW,tr("build.rules"),host::openProperties);y+=24;
        if(y+20<height-FOOT)button(formX,y,formW,tr("build.delete"),()->{var proposal=ResearchDesignerStructure.deleteNodes(project(),host.editingSelection());if(proposal.changes().stream().anyMatch(v->v.startsWith("Unresolved")))host.reviewBuild(proposal);else{commit(proposal);host.buildSelect(null,false);go(Pane.LIBRARY);}});
    }
    private void groups(){
        if(selected()==null){go(Pane.LIBRARY);return;}
        button(formX,52,formW,tr("build.add_requirement"),()->beginRequirements(null));
        var values=selectedNode().requirements();int rows=count(78);pages(values.size(),rows);int y=78;
        for(var group:values.stream().skip((long)page*rows).limit(rows).toList()){
            String label=(group.anyOf().size()>1?tr("build.either"):tr("build.unlock_after"))+String.join(" / ",group.anyOf().stream().map(this::name).toList());
            button(formX,y,formW,label,()->beginRequirements(group.id()));y+=26;
        }
    }
    private void beginRequirements(String group){if(height<278){host.openEditing();host.openBuildRequirement(group);return;}editingGroup=group;requirements.clear();any=false;if(group!=null){var g=selectedNode().requirements().stream().filter(r->r.id().equals(group)).findFirst().orElseThrow();requirements.addAll(g.anyOf());any=true;}go(Pane.REQUIREMENTS);}
    private void requirements(){if(selected()==null){go(Pane.LIBRARY);return;}button(formX,52,formW,any?tr("build.require_any"):tr("build.require_all"),()->{any=!any;rebuildWidgets();}).active=editingGroup==null;
        search(76);var values=project().draft().nodes().stream().filter(n->!n.id().equals(selected())&&n.blueprintId().isPresent()).filter(n->(name(n.blueprintId().get())+" "+n.blueprintId().get()).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).toList();
        int rows=Math.max(1,(height-FOOT-122-102)/26);pages(values.size(),rows);int y=102;
        for(var n:values.stream().skip((long)page*rows).limit(rows).toList()){String bp=n.blueprintId().get();button(formX,y,formW,(requirements.contains(bp)?"✓ ":"+ ")+name(bp),()->{if(!requirements.remove(bp))requirements.add(bp);rebuildWidgets();});y+=26;}
        button(formX,height-126,formW,tr("build.apply_requirements"),()->{commit(ResearchDesignerStructure.connect(project(),selected(),Optional.ofNullable(editingGroup),List.copyOf(requirements),!any));go(Pane.SELECTED);}).active=!requirements.isEmpty();
        button(formX,height-102,formW,tr("build.existing_requirements"),()->go(Pane.GROUPS));
        if(editingGroup!=null)button(formX,height-78,formW,tr("build.remove_requirement"),()->{commit(ResearchDesignerStructure.removeGroup(project(),selected(),editingGroup));go(Pane.SELECTED);});
    }
    private void branches(){button(formX,52,formW,tr("build.make_branch"),()->{branchId=null;branchText=null;go(Pane.BRANCH);}).active=selected()!=null;
        var entries=DesignerBranchOutline.read(project().draft(),host.editingDomain());int rows=count(80);pages(entries.size(),rows);int y=80;
        for(var b:entries.stream().skip((long)page*rows).limit(rows).toList()){button(formX,y,formW,b.title(),()->{branchId=b.id();branchText=null;go(Pane.BRANCH);geometry.regions().stream().filter(r->r.id().filter(b.id()::equals).isPresent()).findFirst().ifPresent(r->camera.focus(r.x(),0,r.width(),r.height()));});y+=26;}}
    private void branch(){var b=branchId==null?null:ResearchDesignerStructure.branch(project(),branchId);String title=b==null?tr("build.new_branch"):b.has("title")?b.get("title").getAsString():branchId;
        var field=new EditBox(font,formX,64,formW,20,Component.literal(tr("build.branch_name")));field.setMaxLength(78);field.setValue(branchText==null?title:branchText);field.setResponder(v->branchText=v);addRenderableWidget(field);
        button(formX,92,formW,tr("build.apply"),()->{commit(b==null?DesignerAuthoringActions.groupSelection(project(),host.editingSelection(),field.getValue()):ResearchDesignerStructure.branch(project(),Optional.of(branchId),field.getValue(),host.editingDomain(),b.has("parent")?Optional.of(b.get("parent").getAsString()):Optional.empty(),b.has("order")?b.get("order").getAsInt():0));go(Pane.BRANCHES);});
        if(b!=null&&height>=224){button(formX,118,formW,collapsed.contains(branchId)?tr("build.expand"):tr("build.collapse"),()->{if(!collapsed.remove(branchId))collapsed.add(branchId);revision=-1;sync();go(Pane.BRANCHES);});button(formX,144,formW,tr("build.arrange"),()->host.reviewBuild(ResearchDesignerStructure.arrange(project(),Optional.of(branchId))));button(formX,170,formW,tr("build.ungroup"),()->host.reviewBuild(ResearchDesignerStructure.deleteBranch(project(),branchId,false)));}}
    private void materials(){if(selected()==null){go(Pane.LIBRARY);return;}var c=cost();var values=c.has("ingredients")?c.getAsJsonArray("ingredients"):new JsonArray();int rows=count(78);pages(values.size(),rows);button(formX,52,formW,tr("build.add_material"),()->go(Pane.ITEMS)).active=values.size()<6;int y=78;
        for(int i=page*rows;i<values.size()&&i<(page+1)*rows;i++){int index=i;var v=values.get(i).getAsJsonObject();String id=v.has("tag")?"#"+v.get("tag").getAsString():v.getAsJsonArray("items").get(0).getAsString();int count=v.get("count").getAsInt();
            button(formX,y,Math.max(24,formW-72),(id.startsWith("#")?id:itemName(id))+" × "+count,()->host.openProperties()).setTooltip(Tooltip.create(Component.literal(v.toString())));
            button(formX+formW-68,y,20,"−",()->{if(count==1)values.remove(index);else v.addProperty("count",count-1);applyMaterials(c,values);});button(formX+formW-44,y,20,"+",()->{v.addProperty("count",count+1);applyMaterials(c,values);}).active=count<64;button(formX+formW-20,y,20,"×",()->{values.remove(index);applyMaterials(c,values);});y+=26;}}
    private void applyMaterials(JsonObject c,JsonArray values){c.add("ingredients",values);commit(DesignerResearchProperties.change(project(),selected(),DesignerResearchProperties.Field.COST,Optional.of(c)));}
    private void items(){search(52);var facts=host.editingCapture().map(c->c.json().getAsJsonObject("registry_facts").getAsJsonObject("items")).orElseGet(JsonObject::new);var ids=facts.keySet().stream().filter(id->(itemName(id)+" "+id).toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT))).sorted().toList();int rows=count(78);pages(ids.size(),rows);int y=78;
        for(String id:ids.stream().skip((long)page*rows).limit(rows).toList()){button(formX,y,formW,itemName(id),()->{var c=cost();var values=c.has("ingredients")?c.getAsJsonArray("ingredients"):new JsonArray();var v=new JsonObject();var items=new JsonArray();items.add(id);v.add("items",items);v.addProperty("count",1);values.add(v);applyMaterials(c,values);go(Pane.MATERIALS);});y+=26;}}
    private String itemName(String id){var key=new ResourceLocation(id);return BuiltInRegistries.ITEM.containsKey(key)?BuiltInRegistries.ITEM.get(key).getDescription().getString():id;}
    private void problems(){button(formX,52,formW,tr(host.checkingBuild()?"cancel_preview":"retry_preview"),host::toggleBuildCheck);var values=host.buildProblems();int rows=count(78);pages(values.size(),rows);int y=78;for(var d:values.stream().skip((long)page*rows).limit(rows).toList()){button(formX,y,formW,d.cause(),()->{project().draft().nodes().stream().filter(n->n.id().equals(d.objectId())||n.requirements().stream().anyMatch(r->r.id().equals(d.objectId()))).findFirst().ifPresent(n->pick(n.id()));host.buildStatus(d.cause()+" "+d.action());});y+=26;}}
    private void help(){button(formX,52,formW,tr("build.add_root"),()->{addParent=null;go(Pane.LIBRARY);});button(formX,78,formW,tr("build.advanced"),host::openEditing);}
    private void pick(String id){try{if(connectParent!=null){String parent=connectParent;commit(DesignerAuthoringActions.connectRequired(project(),parent,id));connectParent=null;host.buildSelect(id,false);host.buildStatus(tr("build.connected"));}else host.buildSelect(id,hasShiftDown());pane=width<820?Pane.SELECTED:Pane.LIBRARY;focus(id);rebuildWidgets();}catch(RuntimeException e){host.buildStatus(Objects.toString(e.getMessage(),tr("build.failed")));}}
    void focus(String id){sync();geometry.tiles().stream().filter(t->t.id().equals(id)).findFirst().ifPresent(t->camera.focus(t.x()-24,t.y()-24,DesignerDraftLayout.TILE_W+48,DesignerDraftLayout.TILE_H+48));}
    ResearchTreeViewport.Snapshot snapshot(){return camera.snapshot();}
    void clearArt(){icons.clear();}
    void fit(){camera.fit();}
    DesignerDraftLayout geometry(){sync();return geometry;}
    private void icon(GuiGraphics g,String id,int x,int y){if(!catalog.has(id))return;var art=icons.computeIfAbsent(id,bp->DesignerCatalogArt.resolve(new ResourceLocation(bp),catalog.getAsJsonObject(bp)));if(art.texture().isPresent())g.blit(art.texture().get(),x,y,0,0,16,16,16,16);else g.renderItem(art.fallback(),x,y);}
    private void line(GuiGraphics g,String text,int x,int y,int width,int color){g.drawString(font,font.plainSubstrByWidth(text,Math.max(1,width)),x,y,color,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float dt){sync();g.fill(0,0,width,height,0xFF0C141D);g.fill(0,TOP,side,height-FOOT,0xFF172633);if(inspect>0)g.fill(width-inspect,TOP,width,height-FOOT,0xFF172633);
        line(g,(session().dirty()?"* ":"")+project().title(),246,9,width-252,0xFFA9BEC9);
        g.enableScissor(canvasX,TOP,canvasX+canvasW,TOP+canvasH);g.pose().pushPose();g.pose().translate(canvasX,TOP,0);g.pose().scale((float)camera.scale(),(float)camera.scale(),1);g.pose().translate(-camera.panX(),-camera.panY(),0);
        for(var region:geometry.regions()){g.fill(region.x(),8,region.x()+region.width(),region.height(),0xFF12212B);g.renderOutline(region.x(),8,region.width(),region.height()-8,0xFF314C5B);line(g,region.id().isEmpty()?tr("build.starting_area"):region.title(),region.x()+12,20,region.width()-24,0xFF9EBAC6);}
        for(var tile:geometry.tiles())for(var group:nodes.get(tile.id()).requirements())for(String prerequisite:group.anyOf()){var from=placed.get(prerequisite);if(from==null)continue;
            if(Math.max(from.x(),tile.x())+DesignerDraftLayout.TILE_W<camera.panX()||Math.min(from.x(),tile.x())>camera.panX()+canvasW/camera.scale()||Math.max(from.y(),tile.y())+DesignerDraftLayout.TILE_H<camera.panY()||Math.min(from.y(),tile.y())>camera.panY()+canvasH/camera.scale())continue;
            int ax=from.x()+DesignerDraftLayout.TILE_W/2,ay=from.y()+DesignerDraftLayout.TILE_H,bx=tile.x()+DesignerDraftLayout.TILE_W/2,by=tile.y(),mid=(ay+by)/2;int color=group.anyOf().size()>1?0xFFE4C56A:0xFF5E94A7;g.fill(ax-1,Math.min(ay,mid),ax+1,Math.max(ay,mid)+1,color);g.fill(Math.min(ax,bx),mid,Math.max(ax,bx)+1,mid+2,color);g.fill(bx-1,Math.min(mid,by),bx+1,Math.max(mid,by)+1,color);line(g,group.anyOf().size()>1?tr("build.any"):"",bx+4,by-12,60,color);}
        for(var tile:geometry.tiles()){if(tile.x()+DesignerDraftLayout.TILE_W<camera.panX()||tile.x()>camera.panX()+canvasW/camera.scale()||tile.y()+DesignerDraftLayout.TILE_H<camera.panY()||tile.y()>camera.panY()+canvasH/camera.scale())continue;
            var node=nodes.get(tile.id());boolean selected=host.editingSelection().contains(tile.id());boolean issue=incomplete.contains(node.id());
            g.fill(tile.x(),tile.y(),tile.x()+DesignerDraftLayout.TILE_W,tile.y()+DesignerDraftLayout.TILE_H,selected?0xFF254555:0xFF20313F);g.renderOutline(tile.x(),tile.y(),DesignerDraftLayout.TILE_W,DesignerDraftLayout.TILE_H,issue?0xFFE7A25D:selected?0xFF70D8CD:0xFF567585);
            node.blueprintId().ifPresent(bp->icon(g,bp,tile.x()+6,tile.y()+8));line(g,node.blueprintId().map(this::name).orElse(tr("incomplete")),tile.x()+26,tile.y()+8,DesignerDraftLayout.TILE_W-30,0xFFE1E9ED);
            line(g,issue?tr("build.needs_attention"):node.requirements().isEmpty()?tr("build.starting_weapon"):tr("build.after_count",node.requirements().size()),tile.x()+6,tile.y()+28,DesignerDraftLayout.TILE_W-12,issue?0xFFE7A25D:0xFFA9BEC9);
            line(g,cost(node).get("points").getAsString()+" "+currency,tile.x()+6,tile.y()+44,DesignerDraftLayout.TILE_W-12,0xFF78939F);
        }
        if(dragNode!=null&&moved){var drop=geometry.drop(camera.canvasX(dragX-canvasX),camera.canvasY(dragY-TOP));g.renderOutline(drop.x(),drop.y(),DesignerDraftLayout.TILE_W,DesignerDraftLayout.TILE_H,0xFF70D8CD);}
        g.flush();g.pose().popPose();g.disableScissor();
        if(geometry.tiles().isEmpty()){int y=TOP+50;for(var line:font.split(Component.literal(tr("build.empty")),Math.max(50,canvasW-20))){if(y+10<TOP+canvasH)g.drawString(font,line,canvasX+10,y,0xFFA9BEC9,false);y+=12;}}
        if(connectParent!=null||addParent!=null)line(g,tr(connectParent!=null?"build.connect_from":"build.add_after",nodes.get(connectParent!=null?connectParent:addParent).blueprintId().map(this::name).orElse("?")),canvasX+4,TOP+28,canvasW-8,0xFF70D8CD);
        String status=host.buildStatus();if(!status.equals(lastStatus)){lastStatus=status;statusChanged=net.minecraft.Util.getMillis();}
        if(net.minecraft.Util.getMillis()-statusChanged<5000&&!List.of(tr("checking"),tr("build.checked"),tr("build.local"),tr("build.changed"),tr("saved"),tr("recovery_saved")).contains(status)){g.fill(canvasX+3,height-40,canvasX+canvasW-3,height-27,0xF0172633);line(g,status,canvasX+6,height-38,canvasW-12,0xFFE7CA88);}
        super.render(g,mx,my,dt);
        if(my<TOP&&mx>=246)g.renderTooltip(font,font.split(Component.literal(project().title()+" · "+tr(session().dirty()?"unsaved":"saved_short")+"\n"+host.buildStatus()+"\n"+host.buildContext()),Math.min(440,width-20)),mx,my);
        if(pane==Pane.LIBRARY&&libraryTotal>libraryRows){int track=height-104,thumb=Math.max(12,track*libraryRows/libraryTotal);int y=100+(track-thumb)*libraryOffset/Math.max(1,libraryTotal-libraryRows);g.fill(side-7,100,side-3,height-4,0xFF233D4D);g.fill(side-7,y,side-3,y+thumb,0xFF87B6C3);}
        if(costLabelX>=0)line(g,tr("build.cost")+" ("+currency+")",costLabelX,costLabelY,formW,0xFF9BACB9);
        if(pane==Pane.LIBRARY&&libraryTotal==0)line(g,tr("build.no_matches"),6,104,side-12,0xFFA9BEC9);
        if(pane==Pane.HELP){int y=112;for(var line:font.split(Component.literal(tr("build.help_text")),side-16)){if(y+10<height-60)g.drawString(font,line,8,y,0xFFA9BEC9,false);y+=12;}}
        if(mx>=canvasX&&mx<canvasX+canvasW&&my>=TOP&&my<TOP+canvasH&&!moved&&children().stream().noneMatch(w->w.isMouseOver(mx,my)))geometry.hit(camera.canvasX(mx-canvasX),camera.canvasY(my-TOP)).ifPresent(t->{var n=nodes.get(t.id());var info=new ArrayList<Component>();info.add(Component.literal(n.blueprintId().map(this::name).orElse(tr("incomplete"))));for(var r:n.requirements())info.add(Component.literal(tr("build.unlock_after")+String.join(" / ",r.anyOf().stream().map(this::name).toList())));g.renderTooltip(font,info.stream().map(Component::getVisualOrderText).toList(),mx,my);});
    }
    @Override public boolean mouseClicked(double x,double y,int button){if(super.mouseClicked(x,y,button))return true;if(pane==Pane.LIBRARY&&button==0&&x>=side-9&&x<side&&y>=100&&y<height-4&&libraryTotal>libraryRows){scrollingCatalog=true;dragScrollbar(y);return true;}if(x<canvasX||x>=canvasX+canvasW||y<TOP||y>=TOP+canvasH)return false;
        double cx=camera.canvasX(x-canvasX),cy=camera.canvasY(y-TOP);
        if(button==0&&cy>=8&&cy<40){var region=geometry.regions().stream().filter(r->r.id().isPresent()&&cx>=r.x()&&cx<r.x()+r.width()).findFirst();if(region.isPresent()){branchId=region.get().id().get();branchText=null;go(Pane.BRANCH);return true;}}
        var hit=geometry.hit(cx,cy);if(button==0&&hit.isPresent()){if(connectParent!=null){pick(hit.get().id());return true;}dragNode=hit.get().id();pressX=dragX=x;pressY=dragY=y;moved=false;return true;}
        if(button==0||button==2){panning=true;moved=false;pressX=x;pressY=y;return true;}return false;}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){if(scrollingCatalog){dragScrollbar(y);return true;}if(dragNode!=null){dragX=x;dragY=y;moved|=Math.hypot(x-pressX,y-pressY)>5;return true;}if(panning){moved|=Math.hypot(x-pressX,y-pressY)>5;camera.panByScreenDelta(dx,dy);return true;}return super.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){if(scrollingCatalog){scrollingCatalog=false;return true;}if(panning&&!moved&&button==0){host.buildSelect(null,false);if(pane==Pane.SELECTED)pane=Pane.LIBRARY;rebuildWidgets();}panning=false;if(dragNode!=null){String id=dragNode;dragNode=null;if(!moved){pick(id);return true;}moved=false;if(x>=canvasX&&x<canvasX+canvasW&&y>=TOP&&y<TOP+canvasH)try{var drop=geometry.drop(camera.canvasX(x-canvasX),camera.canvasY(y-TOP));commit(DesignerAuthoringActions.reorder(project(),id,drop.rank(),drop.index(),drop.branch()));host.buildSelect(id,false);}catch(RuntimeException e){host.buildStatus(Objects.toString(e.getMessage(),tr("build.failed")));}return true;}return super.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x,double y,double amount){if(pane==Pane.LIBRARY&&x<side&&y>=TOP){scrollLibrary(amount>0?-3:3);return true;}if(x>=canvasX&&x<canvasX+canvasW&&y>=TOP&&y<TOP+canvasH){camera.zoomProportional(amount,x-canvasX,y-TOP);return true;}return super.mouseScrolled(x,y,amount);}
    @Override public boolean keyPressed(int key,int scan,int mods){if(key==GLFW.GLFW_KEY_ESCAPE){if(getFocused() instanceof EditBox box&&box.getMessage().getString().equals(tr("build.cost"))&&selected()!=null){session().discardPending(costKey());rebuildWidgets();return true;}if(connectParent!=null||addParent!=null||assignTarget!=null||dragNode!=null){connectParent=null;addParent=null;assignTarget=null;dragNode=null;moved=false;host.buildStatus(tr("build.local"));rebuildWidgets();return true;}if(pane!=Pane.LIBRARY){go(Pane.LIBRARY);return true;}host.onClose();return true;}
        if(getFocused() instanceof EditBox)return super.keyPressed(key,scan,mods);
        if(pane==Pane.LIBRARY&&getFocused() instanceof IconButton focused&&(key==GLFW.GLFW_KEY_DOWN||key==GLFW.GLFW_KEY_UP)){
            var rows=children().stream().filter(IconButton.class::isInstance).map(IconButton.class::cast).toList();int index=rows.indexOf(focused),next=index+(key==GLFW.GLFW_KEY_DOWN?1:-1);
            if(next>=0&&next<rows.size())setFocused(rows.get(next));else{int old=libraryOffset;scrollLibrary(next<0?-1:1);if(old!=libraryOffset){var current=children().stream().filter(IconButton.class::isInstance).toList();if(!current.isEmpty())setFocused(current.get(next<0?0:current.size()-1));}}return true;
        }
        if(hasControlDown()&&(key==GLFW.GLFW_KEY_Z||key==GLFW.GLFW_KEY_Y)){host.undoBuild(key==GLFW.GLFW_KEY_Y||hasShiftDown());sync();rebuildWidgets();return true;}
        if(pane==Pane.LIBRARY&&(key==GLFW.GLFW_KEY_PAGE_DOWN||key==GLFW.GLFW_KEY_PAGE_UP)){scrollLibrary(key==GLFW.GLFW_KEY_PAGE_DOWN?libraryRows:-libraryRows);return true;}if(key==GLFW.GLFW_KEY_F){camera.fit();return true;}if(key==GLFW.GLFW_KEY_E){host.openEditing();return true;}
        switch(key){case GLFW.GLFW_KEY_LEFT->camera.panByScreenDelta(40,0);case GLFW.GLFW_KEY_RIGHT->camera.panByScreenDelta(-40,0);case GLFW.GLFW_KEY_UP->camera.panByScreenDelta(0,40);case GLFW.GLFW_KEY_DOWN->camera.panByScreenDelta(0,-40);default->{return super.keyPressed(key,scan,mods);}}return true;}
    @Override public void tick(){children().stream().filter(EditBox.class::isInstance).map(EditBox.class::cast).forEach(EditBox::tick);if(revision!=session().sequence())sync();}
    private final class TextButton extends Button {
        TextButton(int x,int y,int w,String label,Runnable action){super(x,y,w,20,Component.literal(label),b->{try{action.run();}catch(RuntimeException e){host.buildStatus(Objects.toString(e.getMessage(),tr("build.failed")));}},DEFAULT_NARRATION);}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){
            g.fill(getX(),getY(),getX()+width,getY()+height,!active?0xFF16232D:isHoveredOrFocused()?0xFF36576A:0xFF233D4D);
            g.renderOutline(getX(),getY(),width,height,isFocused()?0xFF70D8CD:0xFF3C5A6C);
            String text=font.plainSubstrByWidth(getMessage().getString(),Math.max(1,width-8));g.drawString(font,text,getX()+(width-font.width(text))/2,getY()+6,active?0xFFE1E9ED:0xFF78909D,false);
        }
    }
    private final class IconButton extends Button {
        private final String blueprint,annotation;
        IconButton(int x,int y,int w,String label,String blueprint,String annotation,Runnable action){super(x,y,w,18,Component.literal(label),b->action.run(),DEFAULT_NARRATION);this.blueprint=blueprint;this.annotation=annotation;setTooltip(Tooltip.create(Component.literal(label+"\n"+blueprint+"\n"+annotation)));}
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float dt){g.fill(getX(),getY(),getX()+width,getY()+height,isHoveredOrFocused()?0xFF304E60:0xFF213745);icon(g,blueprint,getX()+2,getY()+1);line(g,getMessage().getString(),getX()+21,getY()+5,width-35,0xFFE1E9ED);if(annotation.equals(tr("build.added")))line(g,"✓",getX()+width-10,getY()+5,9,0xFF70D8CD);}
        @Override public void onPress(){try{super.onPress();}catch(RuntimeException e){host.buildStatus(Objects.toString(e.getMessage(),tr("build.failed")));}}
    }
}
