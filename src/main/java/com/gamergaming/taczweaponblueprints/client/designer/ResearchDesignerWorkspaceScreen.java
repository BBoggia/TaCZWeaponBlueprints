package com.gamergaming.taczweaponblueprints.client.designer;

import java.util.*;
import static com.gamergaming.taczweaponblueprints.client.designer.DesignerText.tr;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.glfw.GLFW;
import com.gamergaming.taczweaponblueprints.client.*;
import com.gamergaming.taczweaponblueprints.capabilities.PlayerRecipeData;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import com.gamergaming.taczweaponblueprints.research.designer.io.*;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;

/** Local authoring workspace. Selection and navigation never invoke gameplay actions. */
public final class ResearchDesignerWorkspaceScreen extends Screen {
    private final Screen parent;
    private DesignerBuildScreen build;
    private final ResearchDesignerSession session;
    private final Optional<ResearchDesignerCapture> capture;
    private final DesignerSceneAdapter scene = new DesignerSceneAdapter();
    private ResearchTreePublication publication;
    private Map<String,String> names=Map.of();
    private boolean recovered;
    private List<ResearchDesignerCompiler.Diagnostic> diagnostics = List.of();
    private String selected, status;
    private Domain domain;
    private Screen editor;
    private DesignerSimulation.State simulation;
    private final Set<String> multiple = new LinkedHashSet<>();
    private String dragNode;
    private boolean help, storageBusy, started, closing, detached, restoreView = true, checkpointAfterStorage, preservedAsCopy;
    private String contextLabel = tr("local_context");
    private int generation;
    private long compiledSequence = -1;
    private DesignerWorkQueue.Handle previewWork;
    private boolean previewBusy;

    public ResearchDesignerWorkspaceScreen(Screen parent, ResearchDesignerSession session,
            Optional<ResearchDesignerCapture> capture, boolean recovered) {
        super(Component.translatable("gui.taczweaponblueprints.designer.open")); this.parent=parent;this.session=session;this.capture=capture;
        this.recovered=recovered;
        domain=Domain.valueOf(session.view().domain().toUpperCase(Locale.ROOT)); selected=session.view().selection().orElse(null);
        status=recovered?tr("recover_status"):tr("local");
    }
    private Button button(int x,int y,int w,String text,Runnable action) {
        return addRenderableWidget(Button.builder(Component.literal(text),b->action.run()).bounds(x,y,w,20).build());
    }
    @Override protected void init() {
        detached=false;
        multiple.removeIf(id->session.project().draft().nodes().stream().noneMatch(n->n.id().equals(id)));
        if(selected!=null&&session.project().draft().nodes().stream().noneMatch(n->n.id().equals(selected)))selected=null;
        if(build==null)build=new DesignerBuildScreen(this);
        build.init(minecraft,width,height);
        if(editor!=null)editor.init(minecraft,width,height);
        if(!started){started=true;compile();loadContextAge();}
    }
    void buildStatus(String value){status=value;}
    String buildStatus(){return status;}
    List<ResearchDesignerCompiler.Diagnostic> buildProblems(){return diagnostics;}
    boolean isBuildActive(){return !detached&&editor==null&&minecraft.screen==this;}
    void saveBuild(){store(true);}
    boolean checkingBuild(){return previewBusy;}
    void toggleBuildCheck(){if(previewBusy){if(previewWork!=null)previewWork.cancel();generation++;previewBusy=false;status=tr("preview_cancelled");}else compile();refreshIdleWidgets();}
    String buildContext(){return contextLabel+" · "+minecraft.gameDirectory.toPath().resolve("taczweaponblueprints/designer/projects/"+session.project().projectId());}
    void buildSelect(String id,boolean append){
        if(!append)multiple.clear();
        if(id!=null){if(append&&!multiple.add(id))multiple.remove(id);else multiple.add(id);}
        selected=id!=null&&multiple.contains(id)?id:multiple.stream().findFirst().orElse(null);
    }
    void nextBuildDomain(){domain=Domain.values()[(domain.ordinal()+1)%Domain.values().length];buildSelect(null,false);compile();}
    void undoBuild(boolean redo){if(redo&&session.canRedo())session.redo();else if(!redo&&session.canUndo())session.undo();compile();rebuildWidgets();}
    void applyQuick(ResearchDesignerStructure.Proposal proposal){proposal.commit(session);status=DesignerText.tr("build.changed");compile();rebuildWidgets();}
    void applyQuickField(ResearchDesignerStructure.Proposal proposal,ResearchDesignerSession.InputKey key){
        if(!proposal.baseDigest().equals(session.project().digest()))throw new IllegalStateException("The project changed. Try again.");
        var value=proposal.result().json();session.resolvePending(proposal.label(),key,(source,refs)->{source.keySet().clear();value.entrySet().forEach(e->source.add(e.getKey(),e.getValue().deepCopy()));});
        status=DesignerText.tr("build.changed");compile();rebuildWidgets();
    }
    void reviewBuild(ResearchDesignerStructure.Proposal proposal){openEditing();((DesignerEditingPanel)editor).review(proposal);}
    void openBuildRequirements(){((DesignerEditingPanel)editor).openRequirements(selected);}
    void openBuildRequirement(String group){if(group==null)((DesignerEditingPanel)editor).openRequirements(selected);else ((DesignerEditingPanel)editor).openGroup(selected,group);}
    private void refreshIdleWidgets(){if(editor!=null||build==null||!build.typing())rebuildWidgets();}
    void openSimulation(){openProperties();((DesignerPolicyPanel)editor).openSimulation();}
    private String nodeName(String id){return DesignerSceneAdapter.name(ResourceLocation.parse(id),names.getOrDefault(id,id)).getString();}
    private void compile() {
        if(previewWork!=null)previewWork.cancel();previewBusy=true;
        int token=++generation;var checkpoint=session.checkpoint();compiledSequence=session.sequence();status=tr("checking");
        var requestedDomain=domain;int lineHeight=font.lineHeight;publication=null;
        previewWork=DesignerClientWork.submit(()->{
            var context=capture.map(DesignerCapturedContext::new);
            var catalog=context.map(DesignerCapturedContext::catalog).orElse(Map.of());
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            var result=ResearchDesignerCompiler.compile(checkpoint,catalog,capture);
            if(Thread.currentThread().isInterrupted())throw new java.util.concurrent.CancellationException();
            ResearchTreePublication tree=null;
            if(result.candidate().isPresent()&&context.isPresent()) {
                var candidate=result.candidate().get();var facts=context.get();var knowledge=new PlayerRecipeData();
                catalog.keySet().forEach(id->knowledge.discoverBlueprint(id.toString()));
                tree=ResearchDesignerPreview.evaluate(candidate,catalog,new ResearchDesignerPreview.Scenario(
                    DesignerCapturedSettings.progression(capture.get(),candidate.profile()),DesignerCapturedSettings.features(capture.get()),
                    knowledge,facts::blocked,facts::exempt,Optional.empty(),false)).tree();
            }
            var labels=new TreeMap<String,String>();catalog.forEach((id,data)->labels.put(id.toString(),data.getNameKey()));
            return new Compilation(result,tree,tree==null?null:DesignerSceneAdapter.prepare(tree,requestedDomain,lineHeight),Map.copyOf(labels));
        },result->{if(token!=generation||closing||detached)return;previewBusy=false;diagnostics=result.result.diagnostics();publication=result.tree;names=result.names;
            status=DesignerText.tr(result.result.candidate().isPresent()?"build.checked":"build.invalid_draft");
            if(result.scene!=null)try {scene.bind(result.scene);
                if(restoreView&&!session.view().equals(ResearchDesignerSession.View.initial())) {var v=session.view();scene.canvas.viewport().restore(new ResearchTreeViewport.Snapshot(v.x(),v.y(),v.zoom()));}
                restoreView=false;
                if(selected!=null)session.project().draft().nodes().stream().filter(n->n.id().equals(selected)).flatMap(n->n.blueprintId().stream()).findFirst().ifPresent(id->scene.canvas.setAuthoritativeSelection(ResourceLocation.parse(id)));
            }catch(RuntimeException error){publication=null;status=tr("graph_failed")+error.getMessage();}
            refreshIdleWidgets();
        },error->{if(token==generation&&!closing&&!detached){previewBusy=false;status=tr("preview_failed")+error.getMessage();publication=null;refreshIdleWidgets();}});
    }
    private record Compilation(ResearchDesignerCompiler.Result result,ResearchTreePublication tree,ResearchTreeSceneCache.Result scene,Map<String,String> names) {}
    private void loadContextAge() {
        if(capture.isEmpty())return;
        var id=capture.get().json().get("capture_id").getAsString();var root=minecraft.gameDirectory.toPath();
        DesignerClientWork.submit(()->java.nio.file.Files.getLastModifiedTime(root.resolve("taczweaponblueprints/designer/captures/"+id+".json"),java.nio.file.LinkOption.NOFOLLOW_LINKS).toInstant(),
            time->{contextLabel=tr("context_saved")+java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(java.time.ZoneId.systemDefault()).format(time);},
            error->{contextLabel=tr("context_no_date");});
    }
    private void saveCopy() {
        if(storageBusy)return;storageBusy=true;var checkpoint=session.checkpoint();var currentView=view();var game=minecraft.gameDirectory.toPath();
        DesignerClientWork.submit(()->{var detachedSession=ResearchDesignerSession.restore(checkpoint,checkpoint.savedDigest(),checkpoint.recoveryDigest());detachedSession.view(currentView);
            var copy=new ResearchDesignerProjectStore(game).saveAs(detachedSession);new ResearchDesignerProjectStore(game).saveView(copy.project().projectId(),currentView);return copy;
        },copy->{storageBusy=false;status=tr("copy_saved")+copy.project().projectId();
            if(!detached&&session.sequence()==checkpoint.sequence()) {preservedAsCopy=true;minecraft.setScreen(new ResearchDesignerWorkspaceScreen(parent,copy,capture,false));}
            else if(closing||detached){checkpointAfterStorage=false;store(false);}else rebuildWidgets();},
            error->{storageBusy=false;closing=false;status=tr("copy_failed")+error.getMessage();if(!detached)rebuildWidgets();});
    }
    private void store(boolean save) {
        if(storageBusy)return;storageBusy=true;
        var checkpoint=session.checkpoint();var view=view();var game=minecraft.gameDirectory.toPath();
        DesignerClientWork.submit(()->{var store=new ResearchDesignerProjectStore(game);
            String receipt=save?store.save(checkpoint):store.recoverableCheckpoint(checkpoint);
            String viewError=null;try{store.saveView(checkpoint.project().projectId(),view);}catch(Exception error){viewError=error.getMessage();}
            return new Storage(receipt,viewError);
        },result->{storageBusy=false;if(save){session.saved(checkpoint,result.receipt);recovered=false;}else session.checkpointWritten(checkpoint,result.receipt);
            status=result.viewError==null?(save?tr("saved"):tr("recovery_saved")):tr("view_failed")+result.viewError;
            if(checkpointAfterStorage||((closing||detached)&&checkpoint.sequence()!=session.sequence())){checkpointAfterStorage=false;store(false);}
            else if(closing&&!detached)finishClose();else if(!detached)refreshIdleWidgets();
        },error->{storageBusy=false;closing=false;
            if(error instanceof ResearchDesignerProjectStore.SaveFailure failure)session.checkpointWritten(checkpoint,failure.recoveryDigest());
            status=tr("save_failed")+error.getMessage();
            if(!detached)rebuildWidgets();else minecraft.getToasts().addToast(net.minecraft.client.gui.components.toasts.SystemToast.multiline(minecraft,net.minecraft.client.gui.components.toasts.SystemToast.SystemToastId.PACK_LOAD_FAILURE,Component.literal(tr("save_failed_title")),Component.literal(Objects.toString(error.getMessage(),tr("unknown_error")))));});
    }
    private record Storage(String receipt,String viewError) {}
    private ResearchDesignerSession.View view(){var v=build==null?scene.canvas.viewport().snapshot():build.snapshot();return new ResearchDesignerSession.View(v.panX(),v.panY(),v.scale(),Optional.ofNullable(selected),domain.name().toLowerCase(Locale.ROOT));}
    @Override public void tick(){if(editor!=null)editor.tick();else if(build!=null)build.tick();if(!storageBusy&&session.claimAutosave(System.nanoTime()))store(false);if(compiledSequence!=session.sequence())compile();}
    @Override public void onClose(){if(editor!=null){closeEditing();return;}if(help){help=false;return;}if(closing)return;closing=true;if(!storageBusy)store(false);}
    @Override public void removed(){if(editor!=null)editor.removed();detached=true;generation++;if(previewWork!=null)previewWork.cancel();previewBusy=false;session.view(view());if(!preservedAsCopy){if(!storageBusy)store(false);else checkpointAfterStorage=true;}super.removed();}
    private void finishClose(){session.view(view());minecraft.setScreen(parent);}
    ResearchDesignerSession editingSession(){return session;}
    Optional<ResearchDesignerCapture> editingCapture(){return capture;}
    String editingPrimary(){return selected!=null?selected:multiple.stream().sorted().findFirst().orElse(null);}
    String editingDomain(){return domain.name().toLowerCase(Locale.ROOT);}
    String editingBranch(){return selected==null?null:ResearchDesignerStructure.node(session.project(),selected).branchId().orElse(null);}
    Set<String> editingSelection(){return multiple.isEmpty()?(selected==null?Set.of():Set.of(selected)):Set.copyOf(multiple);}
    String editingName(String id){return nodeName(id);}
    void openEditing(){dragNode=null;scene.release(0);if(editor!=null)editor.removed();editor=new DesignerEditingPanel(this);editor.init(minecraft,width,height);minecraft.getNarrator().sayNow(Component.literal(tr("edit.edit_tech_tree")));}
    DesignerSimulation.State simulationState(){if(simulation==null)simulation=DesignerSimulation.State.empty(capture.map(c->DesignerCapturedSettings.progression(c,ResourceLocation.parse(session.project().draft().profile().id())).treeResearchResultMode()).orElse(com.gamergaming.taczweaponblueprints.progression.TreeResearchResultMode.DIRECT_LEARN));return simulation;}
    void simulationState(DesignerSimulation.State value){simulation=value;}
    boolean isDetail(Screen value){return editor==value&&!detached&&!closing;}
    void closeDetail(){closeEditing();}
    void openExport(){if(editor!=null)editor.removed();editor=new DesignerExportScreen(this);editor.init(minecraft,width,height);}
    void openProperties(){if(editor!=null)editor.removed();editor=new DesignerPolicyPanel(this);editor.init(minecraft,width,height);}
    void closeEditing(){if(editor!=null)editor.removed();editor=null;rebuildWidgets();}
    void applyEditing(ResearchDesignerStructure.Proposal proposal){proposal.commit(session);session.view(view());restoreView=true;if(editor!=null)editor.removed();editor=null;compile();rebuildWidgets();}
    void setEditingSelection(Collection<String> ids){multiple.clear();multiple.addAll(ids);selected=multiple.stream().findFirst().orElse(null);}
    void beginMove(){closeEditing();status=DesignerText.tr("build.drag_hint");}
    public void refreshResources(){session.view(view());restoreView=true;if(build!=null)build.clearArt();compile();loadContextAge();rebuildWidgets();}
    @Override public boolean isPauseScreen(){return false;}
        @Override public void render(GuiGraphics g,int mx,int my,float partial){if(editor!=null)editor.render(g,mx,my,partial);else if(build!=null)build.render(g,mx,my,partial);}
    @Override public boolean mouseClicked(double x,double y,int button){return editor!=null?editor.mouseClicked(x,y,button):build.mouseClicked(x,y,button);}
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy){return editor!=null?editor.mouseDragged(x,y,button,dx,dy):build.mouseDragged(x,y,button,dx,dy);}
    @Override public boolean mouseReleased(double x,double y,int button){return editor!=null?editor.mouseReleased(x,y,button):build.mouseReleased(x,y,button);}
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double delta){return editor!=null?editor.mouseScrolled(x,y,horizontal, delta):build.mouseScrolled(x,y,horizontal, delta);}
    @Override public boolean charTyped(char c,int mods){return editor!=null?editor.charTyped(c,mods):build.charTyped(c,mods);}
    @Override public boolean keyPressed(int key,int scan,int mods){
        if(hasControlDown()&&key==GLFW.GLFW_KEY_S){if(hasShiftDown())saveCopy();else store(true);return true;}
        return editor!=null?editor.keyPressed(key,scan,mods):build.keyPressed(key,scan,mods);
    }
    @Override public boolean keyReleased(int key,int scan,int mods){return editor!=null?editor.keyReleased(key,scan,mods):build.keyReleased(key,scan,mods);}
}
