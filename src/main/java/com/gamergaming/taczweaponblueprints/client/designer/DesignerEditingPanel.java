package com.gamergaming.taczweaponblueprints.client.designer;

import java.util.*;
import java.util.function.Supplier;
import com.google.gson.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import com.gamergaming.taczweaponblueprints.research.designer.*;
import static com.gamergaming.taczweaponblueprints.research.designer.ResearchDesignerStructure.*;

/** Native modal editor. It is owned by the workspace, so opening a form never ends the project session. */
final class DesignerEditingPanel extends Screen {
    enum Page { MENU, SELECT, CATALOG, MOVE, BRANCHES, BRANCH, CONNECT, GROUPS, CONNECTIONS, REVIEW }
    private final ResearchDesignerWorkspaceScreen host;
    private final List<String> catalogIds;
    private Page page=Page.MENU, returnPage=Page.MENU;
    private String error="", filter="", branchId, groupId, targetId;
    private int offset;
    private boolean all=true;
    private final Set<String> choices=new LinkedHashSet<>();
    private final Map<String,String> inputs=new HashMap<>();
    private Proposal proposal;
    private record Connection(String target,String group,String label) {}
    private List<Connection> connections=List.of();
    private int x,w;
    DesignerEditingPanel(ResearchDesignerWorkspaceScreen host){super(Component.literal(DesignerText.tr("edit.edit_tech_tree")));this.host=host;catalogIds=host.editingCapture().map(c->c.json().getAsJsonObject("catalog").keySet().stream().sorted().toList()).orElse(List.of());}
    private ResearchDesignerProject project(){return host.editingSession().project();}
    private Button button(int x,int y,int width,String label,Runnable action){return addRenderableWidget(Button.builder(Component.literal(label),b->{try{action.run();}catch(RuntimeException e){error=Objects.toString(e.getMessage(),DesignerText.tr("edit.unable_to_complete_this_change"));}}).bounds(x,y,width,20).build());}
    private void show(Page next){page=next;offset=0;error="";rebuildWidgets();}
    private void propose(Supplier<Proposal> action){returnPage=page;proposal=action.get();show(Page.REVIEW);}
    private String input(String key,String fallback){return inputs.getOrDefault(key,fallback);}
    private EditBox field(String key,String fallback,int x,int y,int width,String label){var box=addRenderableWidget(new EditBox(font,x,y,width,20,Component.literal(label)));box.setMaxLength(160);box.setValue(input(key,fallback));box.setResponder(value->inputs.put(key,value));box.setTooltip(Tooltip.create(Component.literal(label)));return box;}
    private int number(String key,String fallback){try{return new java.math.BigDecimal(input(key,fallback)).intValueExact();}catch(RuntimeException invalid){throw new IllegalArgumentException(DesignerText.tr("edit.use_a_whole_number_for")+key+".");}}
    private Optional<String> optional(String value){return value.isBlank()?Optional.empty():Optional.of(value.trim());}
    private List<ResearchDesignerDraft.Node> nodes(){return project().draft().nodes().stream().filter(n->(n.blueprintId().orElse(n.id())+" "+n.blueprintId().map(host::editingName).orElse("")).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList();}
    private void search(int y){var box=new EditBox(font,x,y,w,20,Component.literal(DesignerText.tr("edit.search_names_or_ids")));box.setMaxLength(128);box.setValue(filter);box.setHint(Component.literal(DesignerText.tr("edit.search_names_or_ids")));box.setResponder(value->{filter=value;offset=0;minecraft.execute(()->{rebuildWidgets();children().stream().filter(EditBox.class::isInstance).findFirst().ifPresent(this::setFocused);});});addRenderableWidget(box);}
    private int rows(int top){return Math.max(1,(height-top-86)/22);}
    private void pager(int count,int rows){offset=Math.min(offset,Math.max(0,((count-1)/rows)*rows));button(x,height-78,72,DesignerText.tr("edit.previous"),()->{offset-=rows;rebuildWidgets();}).active=offset>0;button(x+76,height-78,72,DesignerText.tr("edit.next"),()->{offset+=rows;rebuildWidgets();}).active=offset+rows<count;}
    @Override protected void init(){x=Math.max(10,width/2-230);w=Math.min(460,width-20);
        button(x,height-28,72,page==Page.MENU?DesignerText.tr("edit.close"):DesignerText.tr("edit.back"),()->{if(page==Page.MENU)onClose();else show(page==Page.REVIEW?returnPage:Page.MENU);});
        switch(page){
            case MENU->{
                String[] labels={DesignerText.tr("edit.add_blueprints"),DesignerText.tr("edit.move_selected"),DesignerText.tr("edit.requirements"),DesignerText.tr("edit.manage_branches"),DesignerText.tr("edit.create_branch"),DesignerText.tr("edit.arrange_branch"),DesignerText.tr("edit.arrange_whole_tree"),DesignerText.tr("edit.delete_selected"),DesignerText.tr("edit.start_empty_tree"),DesignerText.tr("edit.drag_selected"),DesignerText.tr("edit.select_nodes"),DesignerText.tr("properties.open"),DesignerText.tr("export.text_0")};
                Runnable[] actions={()->{choices.clear();filter="";show(Page.CATALOG);},()->{inputs.clear();show(Page.MOVE);},()->{targetId=host.editingPrimary();if(targetId==null)throw new IllegalArgumentException(DesignerText.tr("edit.select_the_blueprint_that_needs_prerequisites_first"));show(Page.GROUPS);},()->{filter="";show(Page.BRANCHES);},()->{branchId=null;inputs.clear();show(Page.BRANCH);},()->propose(()->arrange(project(),Optional.ofNullable(host.editingBranch()).filter(b->!b.equals("Unassigned")))),()->propose(()->arrange(project(),Optional.empty())),()->propose(()->deleteNodes(project(),host.editingSelection())),()->propose(()->reset(project())),host::beginMove,()->{choices.clear();choices.addAll(host.editingSelection());filter="";show(Page.SELECT);},host::openProperties,host::openExport};
                int half=(w-6)/2;for(int i=0;i<labels.length;i++){var b=button(x+(i%2)*(half+6),48+(i/2)*24,half,labels[i],actions[i]);if(i==1||i==2||i==7)b.active=!host.editingSelection().isEmpty();if(i==0)b.active=host.editingCapture().isPresent();if(i==5)b.active=host.editingBranch()!=null&&!host.editingBranch().equals("Unassigned");}
            }
            case SELECT->{search(42);var values=nodes();int rows=rows(68);pager(values.size(),rows);for(int i=0;i<rows&&offset+i<values.size();i++){var n=values.get(offset+i);button(x,68+i*22,w,(choices.contains(n.id())?"✓ ":"+ ")+n.blueprintId().map(host::editingName).orElse(DesignerText.tr("edit.incomplete_node")),()->{if(!choices.remove(n.id()))choices.add(n.id());rebuildWidgets();});}
                button(x+w-150,height-52,150,DesignerText.tr("edit.use_selection")+choices.size()+")",()->{host.setEditingSelection(choices);show(Page.MENU);});}
            case CATALOG->{
                search(42);var used=new HashSet<String>();project().draft().nodes().forEach(n->n.blueprintId().ifPresent(used::add));
                var ids=catalogIds.stream().filter(id->!used.contains(id)).filter(id->(id+" "+host.editingName(id)).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).sorted().toList();
                int rows=rows(68);pager(ids.size(),rows);for(int i=0;i<rows&&offset+i<ids.size();i++){String id=ids.get(offset+i);button(x,68+i*22,w,(choices.contains(id)?"✓ ":"+ ")+host.editingName(id),()->{if(!choices.remove(id))choices.add(id);rebuildWidgets();}).setTooltip(Tooltip.create(Component.literal(id)));}
                button(x+w-150,height-52,150,DesignerText.tr("edit.review_additions")+choices.size()+")",()->propose(()->insert(project(),host.editingCapture().orElseThrow(),choices,Optional.empty()))).active=!choices.isEmpty();
            }
            case MOVE->{
                if(host.editingPrimary()==null){show(Page.MENU);return;}var node=node(project(),host.editingPrimary());
                field("row",node.placement().json().get("rank").getAsString(),x,58,w/2-3,DesignerText.tr("edit.row_0_1_000_000"));field("order",node.placement().json().get("order").getAsString(),x+w/2+3,58,w/2-3,DesignerText.tr("edit.sibling_order_0_1_000_000"));
                String current=input("branch",node.branchId().orElse(""));
                var ids=DesignerBranchOutline.read(project().draft(),node.placement().json().get("domain").getAsString());
                button(x,102,w,DesignerText.tr("edit.branch")+branchLabel(current),()->{var values=new ArrayList<String>();values.add("");ids.forEach(b->values.add(b.id()));inputs.put("branch",values.get((values.indexOf(current)+1)%values.size()));rebuildWidgets();});
                button(x+w-150,height-52,150,DesignerText.tr("edit.review_move"),()->propose(()->move(project(),host.editingSelection(),number("row",node.placement().json().get("rank").getAsString()),number("order",node.placement().json().get("order").getAsString()),optional(input("branch",node.branchId().orElse(""))))));
            }
            case BRANCHES->{
                search(42);var ids=new ArrayList<DesignerBranchOutline.Entry>();for(String domain:List.of("weapons","ammo","attachments"))ids.addAll(DesignerBranchOutline.read(project().draft(),domain));
                var filtered=ids.stream().filter(b->(b.id()+" "+b.title()).toLowerCase(Locale.ROOT).contains(filter.toLowerCase(Locale.ROOT))).toList();int rows=rows(68);pager(filtered.size(),rows);
                for(int i=0;i<rows&&offset+i<filtered.size();i++){var b=filtered.get(offset+i);button(x,68+i*22,w,"  ".repeat(b.depth())+b.title(),()->{branchId=b.id();inputs.clear();show(Page.BRANCH);}).setTooltip(Tooltip.create(Component.literal(b.id())));}
            }
            case BRANCH->{
                var existing=branchId==null?new JsonObject():branch(project(),branchId);String domain=input("domain",existing.has("domain")?existing.get("domain").getAsString():host.editingDomain());
                field("title",existing.has("title")?existing.get("title").getAsString():DesignerText.tr("edit.new_branch"),x,46,w/2-3,DesignerText.tr("edit.branch_name"));field("order",existing.has("order")?existing.get("order").getAsString():"0",x+w/2+3,46,w/2-3,DesignerText.tr("edit.sibling_order"));
                button(x,70,w,DesignerText.tr("edit.domain")+domain,()->{var domains=List.of("weapons","ammo","attachments");inputs.put("domain",domains.get((domains.indexOf(domain)+1)%3));inputs.put("parent","");rebuildWidgets();}).active=branchId==null;
                String parent=input("parent",existing.has("parent")?existing.get("parent").getAsString():"");
                button(x,94,w,DesignerText.tr("edit.parent")+branchLabel(parent),()->{var options=new ArrayList<String>();options.add("");DesignerBranchOutline.read(project().draft(),domain).stream().filter(b->!b.id().equals(branchId)).forEach(b->options.add(b.id()));inputs.put("parent",options.get((options.indexOf(parent)+1)%options.size()));rebuildWidgets();});
                if(branchId!=null){int half=(w-6)/2;button(x,118,half,DesignerText.tr("edit.copy_empty_template"),()->propose(()->duplicateBranch(project(),branchId,input("title",existing.get("title").getAsString())+" copy")));button(x+half+6,118,half,DesignerText.tr("edit.ungroup_branch"),()->propose(()->deleteBranch(project(),branchId,false)));button(x,142,half,DesignerText.tr("edit.delete_subtree"),()->propose(()->deleteBranch(project(),branchId,true)));button(x+half+6,142,half,DesignerText.tr("edit.arrange_subtree"),()->propose(()->arrange(project(),Optional.of(branchId))));}
                button(x+w-150,height-52,150,DesignerText.tr("edit.review_branch"),()->propose(()->branch(project(),Optional.ofNullable(branchId),input("title",existing.has("title")?existing.get("title").getAsString():DesignerText.tr("edit.new_branch")),domain,optional(parent),number("order",existing.has("order")?existing.get("order").getAsString():"0"))));
            }
            case CONNECTIONS->{int rows=rows(48);pager(connections.size(),rows);for(int i=0;i<rows&&offset+i<connections.size();i++){var c=connections.get(offset+i);button(x,48+i*22,w,c.label(),()->openGroup(c.target(),c.group())).setTooltip(Tooltip.create(Component.literal(c.label())));}}
            case GROUPS->{
                var target=node(project(),targetId);int rows=rows(68);pager(target.requirements().size(),rows);
                button(x,42,w,DesignerText.tr("edit.add_requirement_group"),()->{groupId=null;choices.clear();filter="";all=true;show(Page.CONNECT);});
                for(int i=0;i<rows&&offset+i<target.requirements().size();i++){var group=target.requirements().get(offset+i);button(x,68+i*22,w,DesignerText.tr("edit.requires_any_one")+String.join(", ",group.anyOf().stream().map(host::editingName).toList()),()->openGroup(targetId,group.id())).setTooltip(Tooltip.create(Component.literal(DesignerText.tr("edit.all_groups_must_be_satisfied_alternatives_within_each_group_are_inclusive"))));}
            }
            case CONNECT->{
                button(x,40,w,all?DesignerText.tr("edit.requires_all_of_these"):DesignerText.tr("edit.requires_any_one_of_these_inclusive"),()->{all=!all;rebuildWidgets();}).active=groupId==null;
                search(64);var nodes=nodes().stream().filter(n->!n.id().equals(targetId)&&n.blueprintId().isPresent()).toList();int rows=rows(90);pager(nodes.size(),rows);
                for(int i=0;i<rows&&offset+i<nodes.size();i++){var n=nodes.get(offset+i);String bp=n.blueprintId().orElseThrow();button(x,90+i*22,w,(choices.contains(bp)?"✓ ":"+ ")+host.editingName(bp),()->{if(!choices.remove(bp))choices.add(bp);rebuildWidgets();}).setTooltip(Tooltip.create(Component.literal(bp+" • "+n.placement().json().get("domain").getAsString())));}
                if(groupId!=null)button(x+76,height-28,112,DesignerText.tr("edit.remove_group"),()->propose(()->removeGroup(project(),targetId,groupId)));
                button(x+w-150,height-52,150,DesignerText.tr("edit.review_connection"),()->propose(()->connect(project(),targetId,Optional.ofNullable(groupId),List.copyOf(choices),all))).active=!choices.isEmpty();
            }
            case REVIEW->{
                int rows=rows(48);pager(proposal.changes().size(),rows);
                for(int i=0;i<rows&&offset+i<proposal.changes().size();i++){String detail=proposal.changes().get(offset+i);button(x,48+i*22,w,font.plainSubstrByWidth(detail,w-12),()->{}).setTooltip(Tooltip.create(Component.literal(detail)));}
                button(x+w-150,height-52,150,DesignerText.tr("edit.apply_change"),()->host.applyEditing(proposal));
            }
        }
    }
    private String branchLabel(String id){if(id.isBlank())return DesignerText.tr("edit.none_unassigned");try{var b=branch(project(),id);return b.has("title")?b.get("title").getAsString():id;}catch(RuntimeException ignored){return id;}}
    void chooseConnections(List<com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph.Edge> edges){
        var found=new LinkedHashMap<String,Connection>();for(var edge:edges)for(var n:project().draft().nodes())if(n.blueprintId().filter(edge.dependentId().toString()::equals).isPresent())for(var group:n.requirements())if(group.anyOf().contains(edge.prerequisiteId().toString()))found.put(n.id()+"/"+group.id(),new Connection(n.id(),group.id(),host.editingName(edge.dependentId().toString())+" requires any one: "+String.join(", ",group.anyOf().stream().map(host::editingName).toList())));
        connections=List.copyOf(found.values());show(Page.CONNECTIONS);
    }
    void openRequirements(String id){targetId=id;show(Page.GROUPS);}
    void openGroup(String target,String group){targetId=target;groupId=group;all=false;choices.clear();node(project(),target).requirements().stream().filter(g->g.id().equals(group)).findFirst().orElseThrow().anyOf().forEach(choices::add);filter="";show(Page.CONNECT);}
    void review(Proposal value){proposal=value;returnPage=Page.MENU;show(Page.REVIEW);}
    @Override public void onClose(){host.closeEditing();}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void tick(){}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){g.fill(0,0,width,height,0xFF101C27);g.drawString(font,page==Page.REVIEW?proposal.label():getTitle().getString(),x,12,0x70D8CD,false);
        String caption=switch(page){case MENU->host.editingSelection().size()+" selected • Shift-click or Shift+N/P to add to selection";case MOVE->DesignerText.tr("edit.row_and_sibling_order_costs_and_requirements_stay_unchanged");case GROUPS->DesignerText.tr("edit.every_group_is_required_select_a_group_to_edit_its_alternatives");case CONNECTIONS->DesignerText.tr("edit.several_connections_overlap_here_choose_the_requirement_group");case CONNECT->DesignerText.tr("edit.choose_prerequisites_from_any_domain");case SELECT->DesignerText.tr("edit.choose_nodes_from_any_domain_selection_does_not_edit_the_tree");case CATALOG->DesignerText.tr("edit.unused_blueprints_from_the_captured_catalog");case BRANCH->DesignerText.tr("edit.name_domain_parent_and_sibling_order");case REVIEW->DesignerText.tr("edit.review_all_changes_before_applying_one_undo_restores_this_edit");default->DesignerText.tr("edit.select_a_branch_to_rename_nest_reorder_or_copy");};
        g.drawString(font,font.plainSubstrByWidth(caption,w),x,28,0x9BACB9,false);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){super.render(g,mx,my,delta);
        if(page==Page.MOVE){g.drawString(font,DesignerText.tr("edit.row"),x,46,0x9BACB9,false);g.drawString(font,DesignerText.tr("edit.sibling_order"),x+w/2+3,46,0x9BACB9,false);}
        if(!error.isEmpty()){g.drawString(font,font.plainSubstrByWidth(error,w-84),x+80,height-21,0xFFCA88,false);if(mx>=x&&mx<x+w&&my>=height-34&&my<height)g.renderTooltip(font,font.split(Component.literal(error),w-12),mx,my);}
    }
}
