package com.gamergaming.taczweaponblueprints.client;

import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import com.gamergaming.taczweaponblueprints.item.BlueprintItem;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTreePublication;
import com.gamergaming.taczweaponblueprints.research.tree.ResearchTechTreeContract.Domain;

/** Designer-only interaction adapter. No menus, purchase packets or gameplay actions. */
public final class DesignerSceneAdapter {
    public final ResearchTreeCanvas canvas = new ResearchTreeCanvas(new ResearchTreeViewState(), new ResearchTreeCanvas.Style(
            0xFF0C141D,0xFF17232E,0xFF344958,0xFF9BACB9,0xFFE1E9ED,0xFF70D8CD,0xFF63848F,
            0xFF183023,0xFF202F39,0xFF291B13,0xFF151A20,0xFF111820,0xFFE4C56A,0xFF9B874E,
            0xFF62C7D9,0xFF477E89,0xFF394552,0xFFE1E9ED,0x700B0F14,0x181D2A35,0x90141920,
            0x10283846,0x50475869,0xC018222C));
    private final ResearchTreeMinimap minimap = new ResearchTreeMinimap();
    private ResearchTreePublication publication;
    /** Geometry is built with a private cache and immutable inputs on the Designer worker. */
    public static ResearchTreeSceneCache.Result prepare(ResearchTreePublication publication, Domain domain, int lineHeight) {
        var cache = new ResearchTreeSceneCache(ignored -> new ResearchTreeBranchSceneEngine(), new ResearchTreeSimpleSceneEngine());
        cache.bind(publication);
        var policy = new ResearchTreeScenePolicy(new ResearchTechTreeLayoutPolicy(20,18,8,20,4,1000,6,6),1024,
                new ResearchTreeScenePolicy.NodeMetrics(96,26+2*lineHeight,3),"designer-compact-"+lineHeight,
                new ResearchTreeScenePolicy.RegionMetrics(44,12,16,80,16,10,2));
        return cache.scene(publication,domain,policy);
    }
    public void bind(ResearchTreeSceneCache.Result result) {
        if (result.geometry().isEmpty()) throw new IllegalArgumentException("Tree geometry is unavailable: " + result.diagnostics());
        publication = result.publication();
        var icons = new HashMap<ResourceLocation,net.minecraft.world.item.ItemStack>();
        result.presentation().orElseThrow().graph().nodes().stream().filter(n -> n.visibility().revealsIcon()).forEach(n -> icons.put(n.blueprintId(),BlueprintItem.createBlueprint(n.blueprintId().toString())));
        canvas.setUnifiedOverview(true); canvas.setSceneContent(result,icons,null,null); canvas.openingView();
    }
    public void render(GuiGraphics graphics, Font font) {
        canvas.render(graphics,font,n -> name(n.blueprintId(),n.nameKey()), n -> 0xFF6F9BA4,
                n -> ResearchTreePresentationContract.StatusSymbol.PREVIEW, id -> Component.literal(id.getPath()));
        minimap.prepare(canvas.bounds().width()>=360&&canvas.bounds().height()>=180?ResearchTreeMinimapMode.AUTOMATIC:ResearchTreeMinimapMode.HIDDEN,canvas,true); minimap.updateViewport(canvas.viewport()); minimap.render(graphics);
    }
    public static Component name(ResourceLocation id,String key) {
        var value = Component.translatable(key);
        return value.getString().equals(key) ? Component.literal(id.getPath().replace('_',' ')) : value;
    }
    public boolean minimapContains(double x,double y) { return minimap.contains(x,y); }
    public boolean click(double x,double y,int button,Consumer<ResourceLocation> selection) {
        if (minimap.contains(x,y)) { minimap.beginNavigation(x,y,button,canvas.viewport()); return true; }
        return canvas.mouseClicked(x,y,button,selection);
    }
    public boolean drag(double x,double y,int button,double dx,double dy) {
        return minimap.navigating() ? minimap.dragNavigation(x,y,button,canvas.viewport()) : canvas.mouseDragged(button,dx,dy);
    }
    public void release(int button) { minimap.endNavigation(button); canvas.mouseReleased(button); }
    public void select(ResourceLocation id) { canvas.setFocusedNode(id); canvas.setAuthoritativeSelection(id); canvas.focusTarget(id); }
    /** Returns every nearby edge; shared buses must not silently choose a requirement group. */
    public List<com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph.Edge> edgesAt(double x,double y){
        if(!canvas.contains(x,y)||minimap.contains(x,y))return List.of();
        double cx=canvas.viewport().canvasX(x-canvas.bounds().x()),cy=canvas.viewport().canvasY(y-canvas.bounds().y()),padding=4/canvas.viewport().scale();
        var result=new LinkedHashSet<com.gamergaming.taczweaponblueprints.research.tree.ResearchTreeGraph.Edge>();
        canvas.scene().ifPresent(scene->{for(var route:scene.routes())for(int i=1;i<route.points().size();i++){
            var a=route.points().get(i-1);var b=route.points().get(i);double dx=b.x()-a.x(),dy=b.y()-a.y(),length=dx*dx+dy*dy;
            double t=length==0?0:Math.max(0,Math.min(1,((cx-a.x())*dx+(cy-a.y())*dy)/length));
            if(Math.hypot(cx-a.x()-t*dx,cy-a.y()-t*dy)<=padding){result.add(route.edge());break;}
        }});return List.copyOf(result);
    }
    public Optional<ResearchTreePublication> publication() { return Optional.ofNullable(publication); }
}
