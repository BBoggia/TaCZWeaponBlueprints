package com.gamergaming.taczweaponblueprints.client;

import java.util.*;
import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.client.ui.*;
import com.gamergaming.taczweaponblueprints.init.ModItems;
import com.gamergaming.taczweaponblueprints.menu.*;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerActionContract.Action;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerMenu.Layout;
import com.gamergaming.taczweaponblueprints.network.*;
import com.gamergaming.taczweaponblueprints.progression.*;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.narration.*;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.*;
import net.minecraft.core.registries.BuiltInRegistries;

/** Horizontal workstation overview with an optional, independently scrollable explanation. */
public final class BlueprintRecyclerScreen extends AbstractContainerScreen<BlueprintRecyclerMenu>
        implements BlueprintRecyclerActionResultListener {
    private static final int TEXT=UiTheme.TEXT, MUTED=UiTheme.TEXT_MUTED;
    private static final ResourceLocation BLUEPRINT_ICON=TaCZWeaponBlueprints.loc("textures/item/blueprint_base.png");
    private static final String ANALYZER="gui.taczweaponblueprints.analyzer.";
    private static final int DETAIL_LINES=7, PRIMARY_X=196, PRIMARY_WIDTH=112, DETAILS_X=140;
    private final BlueprintRecyclerRequestTracker requests=new BlueprintRecyclerRequestTracker();
    private final BlueprintRecyclerFeedbackState feedback=new BlueprintRecyclerFeedbackState();
    private Button primaryButton, modeButton, detailsButton, closeButton;
    private Optional<Action> primaryAction=Optional.empty(), preferredAction=Optional.empty(), confirmationAction=Optional.empty();
    private int confirmationTicks, detailScroll;
    private boolean detailsOpen, pointerOverDetails;
    private BlueprintRecyclerPreview observedPreview=BlueprintRecyclerPreview.EMPTY;
    private final List<HoverText> hoverTexts=new ArrayList<>();
    private record HoverText(int x,int y,int width,int height,Component text) { }

    public BlueprintRecyclerScreen(BlueprintRecyclerMenu menu,Inventory inventory,Component title) {
        super(menu,inventory,title);imageWidth=Layout.PANEL_WIDTH;imageHeight=Layout.PANEL_HEIGHT;
    }
    @Override protected void init() {
        super.init(); observedPreview=menu.preview();
        primaryButton=addRenderableWidget(UiButton.primary(leftPos+PRIMARY_X,topPos+Layout.ACTION_Y,PRIMARY_WIDTH,20,
                Component.empty(),b->primaryAction.ifPresent(this::request)));
        detailsButton=addRenderableWidget(UiButton.secondary(leftPos+DETAILS_X,topPos+Layout.ACTION_Y,48,20,
                w("details"),b->{detailsOpen=!detailsOpen;detailScroll=0;updateControls();triggerImmediateNarration(true);}));
        modeButton=addRenderableWidget(UiButton.secondary(leftPos+198,topPos+5,62,16,Component.empty(),b->{
            var model=BlueprintRecyclerScreenModel.from(menu.preview(),requests.pending());
            preferredAction=primaryAction.equals(model.primaryAction())?model.secondaryAction():model.primaryAction();
            clearConfirmation();feedback.clear();detailScroll=0;updateControls();triggerImmediateNarration(true);
        }));
        closeButton=addRenderableWidget(UiButton.secondary(leftPos+298,topPos+5,16,16,Component.literal("×"),b->onClose()));
        closeButton.setTooltip(Tooltip.create(Component.translatable("gui.done")));
        primaryButton.setTabOrderGroup(0); modeButton.setTabOrderGroup(1); detailsButton.setTabOrderGroup(2); closeButton.setTabOrderGroup(3);
        updateControls();setFocused(null);
    }
    @Override
    protected void containerTick() {
        super.containerTick();
        BlueprintRecyclerPreview currentPreview = menu.preview();
        if (!currentPreview.equals(observedPreview)) {
            clearConfirmation();
            if (!currentPreview.inputId().equals(observedPreview.inputId()) || currentPreview.inputKind() != observedPreview.inputKind()) {
                detailScroll = 0; detailsOpen = false; preferredAction = Optional.empty();
            }
            feedback.reconcile(currentPreview);
            observedPreview = currentPreview;
            triggerImmediateNarration(true);
        }
        if (requests.tick()) {
            feedback.show(
                    BlueprintRecyclerActionContract.ResultCode.TRANSACTION_FAILED,
                    currentPreview);
            triggerImmediateNarration(true);
        } else if (feedback.tick()) {
            triggerImmediateNarration(true);
        }
        if (confirmationTicks > 0 && --confirmationTicks == 0) {
            confirmationAction = Optional.empty();
        }
        updateControls();
    }

    @Override
    public void onClose() {
        menu.setWorkstationSlotsVisible(true);
        requests.clear();
        feedback.clear();
        clearConfirmation();
        super.onClose();
    }

    @Override
    public void acceptRecyclerActionResult(
            int requestId,
            BlueprintRecyclerActionContract.ActionResult result) {
        if (!requests.accept(requestId, result)) {
            return;
        }
        feedback.accept(result, menu.preview());
        detailScroll = 0;
        updateControls();
        triggerImmediateNarration(true);
    }

    private void request(BlueprintRecyclerActionContract.Action action) {
        BlueprintRecyclerPreview preview = menu.preview();
        if (action == BlueprintRecyclerActionContract.Action.RECOVER_POINTS
                && !preview.alreadyKnown()
                && confirmationAction.filter(action::equals).isEmpty()) {
            confirmationAction = Optional.of(action);
            confirmationTicks = 100;
            updateControls();
            triggerImmediateNarration(true);
            return;
        }
        clearConfirmation();
        ResourceLocation inputId = preview.inputId().orElse(null);
        Optional<BlueprintRecyclerRequestTracker.Request> started =
                requests.begin(
                        action,
                        inputId,
                        preview.inputCount(),
                        preview.stateToken());
        if (started.isEmpty()) {
            return;
        }
        BlueprintRecyclerRequestTracker.Request request = started.orElseThrow();
        feedback.clear();
        detailScroll = 0;
        try {
            net.neoforged.neoforge.network.PacketDistributor.sendToServer(new BlueprintRecyclerActionPacket(
                    menu.containerId,
                    request.requestId(),
                    request.action(),
                    request.inputId(),
                    request.inputCount(),
                    request.stateToken()));
        } catch (RuntimeException exception) {
            requests.clear();
            feedback.show(
                    BlueprintRecyclerActionContract.ResultCode.TRANSACTION_FAILED,
                    menu.preview());
            TaCZWeaponBlueprints.LOGGER.warn(
                    "Failed to send Blueprint Recycler request {} for {}",
                    request.requestId(), request.inputId(), exception);
            triggerImmediateNarration(true);
        }
        updateControls();
    }

    private void updateControls() {
        if(primaryButton==null)return;
        menu.setWorkstationSlotsVisible(!detailsOpen);
        var p=menu.preview();var model=BlueprintRecyclerScreenModel.from(p,requests.pending());
        primaryAction=AnalyzerWorkbenchContent.selected(model,preferredAction);
        primaryButton.visible=true;primaryButton.active=primaryAction.isPresent()&&model.controlsEnabled();
        primaryButton.setMessage(primaryAction.map(this::actionLabel).orElse(w("unavailable")));
        primaryButton.setTooltip(Tooltip.create(actionExplanation(p)));
        modeButton.visible=!detailsOpen&&model.secondaryAction().isPresent();modeButton.active=modeButton.visible&&!requests.pending();
        var alternate=primaryAction.equals(model.primaryAction())?model.secondaryAction():model.primaryAction();
        modeButton.setMessage(alternate.map(a->w(a==Action.RECOVER_POINTS?"choose_recovery":a==Action.REVERSE_ENGINEER?"choose_analysis":a==Action.REDEEM_STACK?"choose_stack":"choose_one")).orElse(Component.empty()));
        modeButton.setTooltip(alternate.map(a->Tooltip.create(w("switch_action",actionLabel(a)))).orElse(null));
        detailsButton.setMessage(w(detailsOpen?"back":"details"));
        detailsButton.active=p.inputKind()!=BlueprintRecyclerPreview.InputKind.EMPTY||detailsOpen;
        if(getFocused()==modeButton&&!modeButton.visible)setFocused(detailsButton);
    }
    private Component actionLabel(Action action) {
        if(confirmationAction.filter(action::equals).isPresent())return w("confirm_recovery");
        return switch(action) {
            case ANALYZE_DATA -> w("analyze_one");
            case REVERSE_ENGINEER -> w("analyze_weapon");
            case RECOVER_POINTS -> w("recover");
            case REDEEM -> w("redeem_one");
            case REDEEM_STACK -> w("redeem_stack");
            case RECYCLE -> w("recycle");
            case ARCHIVE_FRAGMENTS -> w(menu.preview().fragmentEvaluation().filter(f->f.createsBlueprint()).isPresent()?"reconstruct":"archive");
        };
    }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick) {
        pointerOverDetails=overDetails(mouseX,mouseY);hoverTexts.clear();super.render(g,mouseX,mouseY,partialTick);
        if(!detailsOpen||mouseY>=topPos+Layout.ACTION_Y)renderTooltip(g,mouseX,mouseY);
        for(var hover:hoverTexts)if(mouseX>=leftPos+hover.x()&&mouseX<leftPos+hover.x()+hover.width()
                &&mouseY>=topPos+hover.y()&&mouseY<topPos+hover.y()+hover.height()) {
            g.renderTooltip(font,font.split(hover.text(),Math.min(260,width-16)),mouseX,mouseY);break;
        }
    }
    @Override protected void renderBg(GuiGraphics g,float delta,int mouseX,int mouseY) {
        UiDraw.raisedPanel(g,leftPos,topPos,imageWidth,imageHeight);
        UiDraw.rule(g,leftPos+4,topPos+24,imageWidth-8);
        UiDraw.rule(g,leftPos+8,topPos+106,imageWidth-16);
        UiDraw.rule(g,leftPos+8,topPos+132,imageWidth-16);
        slot(g,Layout.INPUT_X,Layout.INPUT_Y,28);
        slot(g,Layout.OUTPUT_X,Layout.OUTPUT_Y,28);
        for(int row=0;row<3;row++)for(int col=0;col<9;col++)slot(g,Layout.PLAYER_X+col*Layout.PLAYER_SPACING,Layout.PLAYER_Y+row*Layout.PLAYER_SPACING,20);
        for(int col=0;col<9;col++)slot(g,Layout.PLAYER_X+col*Layout.PLAYER_SPACING,Layout.HOTBAR_Y,20);
    }
    /** A recessed well centred on a 16px item slot. */
    private void slot(GuiGraphics g,int x,int y,int size) {
        int pad=(size-16)/2;
        UiDraw.field(g,leftPos+x-pad,topPos+y-pad,size,size);
    }
    @Override protected void renderLabels(GuiGraphics g,int mouseX,int mouseY) {
        var p=menu.preview();var model=BlueprintRecyclerScreenModel.from(p,requests.pending());
        line(g,Component.translatable("block.taczweaponblueprints.blueprint_recycler"),12,9,modeButton.visible?180:246,TEXT);
        int tier=p.presentation().stationTier()>0?p.presentation().stationTier():menu.analyzerTier().map(t->t.level()).orElse(0);
        if(tier>0 && com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.stationSetup().analyzer() != com.gamergaming.taczweaponblueprints.progression.StationSetup.AnalyzerMode.SINGLE){
            Component label=w("tier",tier);
            UiDraw.tag(g,font,label,294-font.width(label)-6,8);
        }
        line(g,playerInventoryTitle,12,136,160,MUTED);
        if(detailsOpen)renderDetails(g,p);else renderOverview(g,p,model);
        renderCost(g,p);
    }
    private void renderOverview(GuiGraphics g,BlueprintRecyclerPreview p,BlueprintRecyclerScreenModel model) {
        Component name=inputName(p);
        wrapped(g,name,12,28,68,2,TEXT);
        centered(g,Component.translatable("gui.taczweaponblueprints.blueprint_recycler.input"),40,49,60,MUTED);
        centered(g,Component.translatable("gui.taczweaponblueprints.blueprint_recycler.output"),280,49,54,MUTED);
        if(p.inputKind()==BlueprintRecyclerPreview.InputKind.EMPTY&&visibleFeedback(p).isPresent()) {
            line(g,tr("result.title"),86,30,164,TEXT);
            feedback.visibleTarget().ifPresent(target->line(g,targetName(target),86,42,164,MUTED));
            wrapped(g,notice(p,model),86,57,164,4,noticeColor());
            return;
        }
        var overview=AnalyzerWorkbenchContent.overview(p,primaryAction);
        line(g,overview.heading(),86,30,164,TEXT);
        if(p.inputKind()==BlueprintRecyclerPreview.InputKind.EMPTY||p.inputKind()==BlueprintRecyclerPreview.InputKind.INVALID) {
            wrapped(g,overview.subtitle(),86,47,164,4,MUTED);return;
        }
        line(g,overview.subtitle(),86,42,164,MUTED);
        overview.progress().ifPresent(progress->{
            int barX=86,barY=53,barWidth=164;
            g.fill(barX,barY,barX+barWidth,barY+3,UiTheme.STEEL_DARKEST);
            int saved=(int)Math.min(barWidth,(long)barWidth*progress.saved()/progress.threshold());
            int after=(int)Math.min(barWidth,(long)barWidth*(progress.saved()+progress.incoming())/progress.threshold());
            g.fill(barX,barY,barX+saved,barY+3,UiTheme.TEXT_MUTED);
            g.fill(barX+saved,barY,barX+after,barY+3,UiTheme.AMBER);
        });
        int y=overview.progress().isPresent()?59:55;
        for(var row:overview.rows()) {
            renderIcon(g,row.icon(),86,y-1);
            int valueWidth=font.width(row.value());int labelWidth=148-(valueWidth==0?0:valueWidth+6);
            line(g,row.label(),102,y,labelWidth,TEXT);
            if(valueWidth>0)line(g,row.value(),250-valueWidth,y,valueWidth,UiTheme.MANILA);
            y+=13;
        }
        Component notice=notice(p,model);int color=noticeColor();
        if (notice.getString().isEmpty()) { notice = ammunitionReturn(p).orElse(Component.empty()); color = MUTED; }
        if(!notice.getString().isEmpty())line(g,notice,12,95,296,color);
    }
    /** Green for a finished action, red for a failed one, amber for a warning the player should read first. */
    private int noticeColor() {
        var code=feedback.visibleCode();
        if(code.isPresent())return code.orElseThrow()==BlueprintRecyclerActionContract.ResultCode.SUCCESS?UiTheme.GREEN:UiTheme.RED;
        return requests.pending()?MUTED:UiTheme.AMBER_HIGHLIGHT;
    }
    private void renderIcon(GuiGraphics g,AnalyzerWorkbenchContent.Icon kind,int x,int y) {
        if(kind==AnalyzerWorkbenchContent.Icon.BLUEPRINT) {
            // A category icon has no target; do not create an invalid blueprint for the item renderer.
            g.blit(BLUEPRINT_ICON,x,y,12,12,0,0,64,64,64,64);return;
        }
        ItemStack stack=switch(kind) {
            case BLUEPRINT -> new ItemStack(ModItems.BLUEPRINT_ITEM.get());
            case FRAGMENT -> new ItemStack(ModItems.BLUEPRINT_FRAGMENT.get());
            case CURRENCY -> ClientResearchCurrency.icon();
            case NONE -> ItemStack.EMPTY;
        };
        if(stack.isEmpty())return;
        g.pose().pushPose();g.pose().translate(x,y,0);g.pose().scale(.75f,.75f,1);g.renderItem(stack,0,0);g.pose().popPose();
    }
    private Optional<Component> ammunitionReturn(BlueprintRecyclerPreview p) {
        if (p.inputKind()!=BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM) return Optional.empty();
        return com.gamergaming.taczweaponblueprints.progression.AnalyzerAmmoRecovery.plan(menu.inputStack())
                .filter(plan->plan.count()>0).map(plan->Component.translatable(
                        "gui.taczweaponblueprints.blueprint_recycler.ammo_return",plan.count(),plainName(plan.ammunition().getHoverName())));
    }
    /** Pack-authored colour codes would override the notice colour, so the name is shown plain. */
    private static String plainName(Component name) {
        String plain=net.minecraft.ChatFormatting.stripFormatting(name.getString());
        return plain==null?"":plain;
    }
    private Component inputName(BlueprintRecyclerPreview p) {
        if(p.inputKind()==BlueprintRecyclerPreview.InputKind.EMPTY)return Component.empty();
        if(p.inputKind()==BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM||p.inputKind()==BlueprintRecyclerPreview.InputKind.BLUEPRINT_FRAGMENT||p.inputKind()==BlueprintRecyclerPreview.InputKind.BLUEPRINT)
            return p.inputId().<Component>map(this::targetName).orElse(menu.inputStack().getHoverName());
        return menu.inputStack().getHoverName();
    }
    private Component notice(BlueprintRecyclerPreview p,BlueprintRecyclerScreenModel model) {
        var visible=visibleFeedback(p);
        if(visible.isPresent()&&feedback.visibleFragments().isPresent()) {
            var result=feedback.visibleFragments().orElseThrow();
            return w(result.blueprintCreated()?"receipt_blueprint":"receipt_archive",result.archived(),result.retained(),result.saved());
        }
        if(requests.pending()||visible.isPresent())return status(model,visible);
        if(confirmationAction.isPresent())return w("confirm_warning");
        if(!model.controlsEnabled())return ClientResearchCurrency.text(model.statusKey());
        if(p.customizationWillBeLost())return tr("modified");
        if(p.presentation().weapon().filter(w->w.blueprintLearningDisabled()).isPresent()&&!AnalyzerWorkbenchContent.recovery(p,primaryAction))return tr("learning.disabled");
        if(p.alreadyKnown()&&primaryAction.filter(a->a==Action.REVERSE_ENGINEER).isPresent())return w("known_copy");
        return Component.empty();
    }
    private void renderCost(GuiGraphics g,BlueprintRecyclerPreview p) {
        if(p.inputKind()==BlueprintRecyclerPreview.InputKind.EMPTY||p.inputKind()==BlueprintRecyclerPreview.InputKind.INVALID)return;
        var cost=AnalyzerWorkbenchContent.cost(p,primaryAction);
        // The complete cost tooltip owns this strip, including shortened material summaries.
        hoverTexts.add(new HoverText(12,109,120,21,actionExplanation(p)));
        line(g,w(cost.currency()==0?"free":"cost",cost.currency()),12,111,120,cost.currency()>p.pointBalance()?UiTheme.RED:MUTED);
        Component consumption=consumption(p,cost);
        if(!cost.materials().isEmpty())consumption=w("consumes_materials",cost.input());
        line(g,consumption,12,121,120,MUTED);
    }
    private Component consumption(BlueprintRecyclerPreview p,AnalyzerWorkbenchContent.Cost cost) {
        if(p.inputKind()==BlueprintRecyclerPreview.InputKind.BLUEPRINT_FRAGMENT)
            return w(cost.input()==0?"uses_saved":"deposit",cost.input());
        return w(primaryAction.filter(a->a==Action.REDEEM_STACK).isPresent()?"consumes_up_to":"consumes",cost.input());
    }
    private Component actionExplanation(BlueprintRecyclerPreview p) {
        var cost=AnalyzerWorkbenchContent.cost(p,primaryAction);
        var result=consumption(p,cost).copy();
        result.append("\n").append(w(cost.currency()==0?"free":"cost",cost.currency()));
        if(cost.currency()>0)result.append("\n").append(w("balance",p.pointBalance()));
        for(var material:cost.materials())result.append("\n").append(materialLine(material));
        if(p.customizationWillBeLost())result.append("\n").append(tr("modified"));
        if(primaryAction.filter(a->a==Action.RECOVER_POINTS).isPresent()&&!p.alreadyKnown())result.append("\n").append(w("confirm_warning"));
        if(primaryAction.filter(a->a==Action.REDEEM_STACK).isPresent())result.append("\n").append(w("stack_stops"));
        return result;
    }
    private Component clipped(Component component, int width) {
        String value = component.getString();
        if (font.width(value) <= width) {
            return component;
        }
        String suffix = "…";
        return Component.literal(
                font.plainSubstrByWidth(value, Math.max(0, width - font.width(suffix))) + suffix);
    }

    private Optional<BlueprintRecyclerActionContract.ResultCode> visibleFeedback(
            BlueprintRecyclerPreview preview) {
        feedback.reconcile(preview);
        return feedback.visibleCode();
    }

    private Component status(
            BlueprintRecyclerScreenModel model,
            Optional<BlueprintRecyclerActionContract.ResultCode> visibleFeedback) {
        if (requests.pending()) {
            return Component.translatable(
                    "gui.taczweaponblueprints.blueprint_recycler.processing");
        }
        if (visibleFeedback.isPresent() && feedback.visibleOutcome().isPresent()) {
            var outcome = feedback.visibleOutcome().orElseThrow();
            return tr("result." + outcome.kind().name().toLowerCase(java.util.Locale.ROOT), outcome.count());
        }
        if (visibleFeedback.isPresent() && feedback.visibleFragments().isPresent()) {
            var f = feedback.visibleFragments().orElseThrow();
            return tr(f.blueprintCreated() ? "result.reconstructed" : "result.archived", f.archived(), f.retained(), f.saved());
        }
        return visibleFeedback
                .map(result -> ClientResearchCurrency.text(
                        BlueprintRecyclerScreenModel.resultKey(result)))
                .orElseGet(() -> ClientResearchCurrency.text(model.statusKey()));
    }

    private void clearConfirmation() {
        confirmationAction = Optional.empty();
        confirmationTicks = 0;
    }

    private Component ingredientLabel(
            BlueprintRecyclerPreview.IngredientPreview ingredient) {
        if (ingredient.tag().isPresent()) {
            return Component.literal("#" + ingredient.tag().orElseThrow());
        }
        MutableComponent label = Component.empty();
        int shown = Math.min(3, ingredient.items().size());
        for (int index = 0; index < shown; index++) {
            if (index > 0) {
                label.append(" / ");
            }
            ResourceLocation id = ingredient.items().get(index);
            Item item = BuiltInRegistries.ITEM.get(id);
            label.append(item == null ? Component.literal(id.toString()) : item.getDescription());
        }
        if (ingredient.items().size() > shown) {
            label.append(Component.translatable(
                    "gui.taczweaponblueprints.blueprint_recycler.reverse.alternatives_more",
                    ingredient.items().size() - shown));
        }
        return label;
    }

    private Component targetName(ResourceLocation id) {
        var data = BlueprintDataManager.presentationCatalog().getBlueprintData(id.toString());
        if (data == null) return Component.literal(id.toString());
        var name = ClientResearchCurrency.text(data.getNameKey());
        return name.getString().equals(data.getNameKey()) ? Component.literal(id.toString()) : name;
    }

    private static Component tr(String key,Object... values){return ClientResearchCurrency.text(ANALYZER+key,values);}
    private static Component w(String key,Object... values){return AnalyzerWorkbenchContent.text(key,values);}
    private Component materialLine(BlueprintRecyclerPreview.IngredientPreview material) {
        return Component.translatable("gui.taczweaponblueprints.blueprint_recycler.reverse.material",ingredientLabel(material),material.inventoryAvailable(),material.required());
    }
    private List<Component> explanation(BlueprintRecyclerPreview p) {
        var lines=new ArrayList<Component>();
        if(p.inputKind()!=BlueprintRecyclerPreview.InputKind.EMPTY)lines.add(inputName(p));
        if(p.presentation().data().isPresent()) {
            var data=p.presentation().data().orElseThrow();
            lines.add(ResearchDataPresentationText.tr(data.allowLearned()?"duplicates_allowed":"duplicates_excluded"));
            lines.add(ResearchDataPresentationText.tr(data.allowUndiscovered()?"undiscovered_allowed":"discovered_only"));
            for(var station:data.stations())if(com.gamergaming.taczweaponblueprints.init.ModConfigs.BLUEPRINT.stationSetup().analyzer() != com.gamergaming.taczweaponblueprints.progression.StationSetup.AnalyzerMode.SINGLE && station.tier()!=p.presentation().stationTier())lines.add(station.available()
                    ?ResearchDataPresentationText.tr("station_compare",station.tier(),ResearchDataPresentationText.full(station),ResearchDataPresentationText.quantity(station))
                    :ResearchDataPresentationText.tr("station_unavailable",station.tier()));
            if(data.partialProgressBias())lines.add(ResearchDataPresentationText.tr("partial_bias"));
            rewardDetails(lines,data.reward());
        }
        if(p.inputKind()==BlueprintRecyclerPreview.InputKind.PHYSICAL_ITEM) {
            ammunitionReturn(p).ifPresent(line->{lines.add(line);lines.add(Component.translatable(
                    "gui.taczweaponblueprints.blueprint_recycler.ammo_overflow"));});
            boolean recovery=AnalyzerWorkbenchContent.recovery(p,primaryAction);
            p.presentation().weapon().ifPresent(weapon->{
                if(!recovery&&weapon.threshold()>0) {
                    lines.add(tr("saved",weapon.saved(),weapon.threshold()));
                    lines.add(tr("archive",weapon.totalSaved(),weapon.cap()));
                    if(weapon.saved()>=weapon.threshold())lines.add(tr("stored_set"));
                }
                if(!recovery)weapon.reward().ifPresent(reward->rewardDetails(lines,reward));
            });
            if(recovery&&p.reverseEngineeringStatus().filter(s->s!=BlueprintReverseEngineeringService.Status.READY&&s!=BlueprintReverseEngineeringService.Status.RECOVERY_MODE_DISABLED).isPresent())
                lines.add(tr("analysis_status",ClientResearchCurrency.text(BlueprintRecyclerScreenModel.analysisStatusKey(p))));
            lines.add(Component.translatable("gui.taczweaponblueprints.blueprint_recycler.reverse.origin."+p.weaponOrigin().name().toLowerCase(Locale.ROOT)));
        }
        if(p.inputKind()==BlueprintRecyclerPreview.InputKind.BLUEPRINT_FRAGMENT) {
            var fragment=p.fragmentEvaluation().orElseThrow();
            if(fragment.completionMode()==com.gamergaming.taczweaponblueprints.progression.fragment.BlueprintFragmentPolicy.CompletionMode.TARGETED_RESEARCH_BOOST)
                lines.add(w("discount_fragments"));
            p.presentation().fragments().ifPresent(f->{
                lines.add(tr("archive",f.totalSaved(),f.cap()));
                f.outputReward().ifPresent(reward->rewardDetails(lines,reward));
                lines.add(tr("fragment.separate"));
            });
            if(fragment.rejected()>0)lines.add(w("left_in_input",fragment.rejected()));
        }
        var cost=AnalyzerWorkbenchContent.cost(p,primaryAction);
        for(var material:cost.materials())lines.add(materialLine(material));
        if(cost.currency()>0)lines.add(w("balance",p.pointBalance()));
        if(lines.isEmpty())lines.add(w("supported_inputs"));
        // Components retain translation/style identity; deduplicate by rendered message for narration too.
        var unique=new LinkedHashMap<String,Component>();
        for(var line:lines)unique.putIfAbsent(line.getString(),line);
        return List.copyOf(unique.values());
    }
    private void rewardDetails(List<Component> lines,com.gamergaming.taczweaponblueprints.progression.analyzer.AnalysisRewardClass reward) {
        lines.add(tr(reward.recyclable()?"recyclable":"protected"));
        lines.add(tr("learning."+(reward.learningMode()==PhysicalBlueprintLearningMode.BYPASS_TREE_PREREQUISITES?"bypass":"prerequisites")));
    }
    private List<FormattedCharSequence> detailLines() {
        var lines=new ArrayList<FormattedCharSequence>();
        for(var text:explanation(menu.preview()))lines.addAll(font.split(text,imageWidth-36));
        return lines;
    }
    private void renderDetails(GuiGraphics g,BlueprintRecyclerPreview p) {
        // Finish the item-count text batch before covering the raised container items.
        g.flush();
        g.pose().pushPose();g.pose().translate(0,0,300);
        UiDraw.field(g,8,26,imageWidth-16,79);
        var lines=detailLines();detailScroll=Math.max(0,Math.min(detailScroll,Math.max(0,lines.size()-DETAIL_LINES)));
        for(int i=0;i<DETAIL_LINES&&i+detailScroll<lines.size();i++)g.drawString(font,lines.get(i+detailScroll),14,30+i*10,TEXT,false);
        if(lines.size()>DETAIL_LINES) {
            int thumb=Math.max(6,70*DETAIL_LINES/lines.size());int offset=(70-thumb)*detailScroll/(lines.size()-DETAIL_LINES);
            g.fill(imageWidth-14,30,imageWidth-12,100,UiTheme.STEEL_LOW);g.fill(imageWidth-14,30+offset,imageWidth-12,30+offset+thumb,MUTED);
        }
        g.flush();
        g.pose().popPose();
    }
    private void line(GuiGraphics g,Component text,int x,int y,int width,int color) {
        if(width<=0||text.getString().isEmpty())return;
        g.drawString(font,clipped(text,width),x,y,color,false);
        if(font.width(text)>width)hoverTexts.add(new HoverText(x,y,width,10,text));
    }
    private void wrapped(GuiGraphics g,Component text,int x,int y,int width,int rows,int color) {
        var parts=font.split(text,width);
        for(int i=0;i<Math.min(rows,parts.size());i++)g.drawString(font,parts.get(i),x,y+10*i,color,false);
        if(parts.size()>rows)hoverTexts.add(new HoverText(x,y,width,rows*10,text));
    }
    private void centered(GuiGraphics g,Component text,int x,int y,int width,int color) {
        Component visible=clipped(text,width);g.drawString(font,visible,x-font.width(visible)/2,y,color,false);
        if(font.width(text)>width)hoverTexts.add(new HoverText(x-width/2,y,width,10,text));
    }
    private boolean overDetails(double x,double y) {return detailsOpen&&x>=leftPos+8&&x<leftPos+imageWidth-8&&y>=topPos+24&&y<topPos+106;}
    @Override public boolean mouseClicked(double x,double y,int button) {
        // Details covers real workstation slots. Never let a click reach an obscured slot.
        if(overDetails(x,y))return true;return super.mouseClicked(x,y,button);
    }
    @Override public boolean mouseDragged(double x,double y,int button,double dx,double dy) {
        if(overDetails(x,y))return true;return super.mouseDragged(x,y,button,dx,dy);
    }
    @Override public boolean mouseReleased(double x,double y,int button) {
        if(overDetails(x,y))return super.mouseReleased(leftPos+imageWidth-5,topPos+imageHeight-5,button);return super.mouseReleased(x,y,button);
    }
    @Override public boolean mouseScrolled(double x, double y, double horizontal, double amount) {
        if (amount == 0) return false;
        if(overDetails(x,y)){scrollDetails(amount>0?-1:1);return true;}return super.mouseScrolled(x,y,horizontal, amount);
    }
    @Override public boolean keyPressed(int key,int scan,int modifiers) {
        if(detailsOpen&&key==256){detailsOpen=false;updateControls();setFocused(detailsButton);return true;}
        if(detailsOpen&&(key==266||key==267)){scrollDetails(key==266?-DETAIL_LINES:DETAIL_LINES);return true;}
        if(pointerOverDetails&&(Arrays.stream(minecraft.options.keyHotbarSlots).anyMatch(binding->binding.matches(key,scan))
                ||minecraft.options.keyDrop.matches(key,scan)||minecraft.options.keySwapOffhand.matches(key,scan)))return true;
        return super.keyPressed(key,scan,modifiers);
    }
    private void scrollDetails(int amount) {
        detailScroll=Math.max(0,Math.min(Math.max(0,detailLines().size()-DETAIL_LINES),detailScroll+amount));triggerImmediateNarration(true);
    }
    private Component detailsNarration() {
        var p=menu.preview();var model=BlueprintRecyclerScreenModel.from(p,requests.pending());
        var overview=AnalyzerWorkbenchContent.overview(p,primaryAction);
        var result=title.copy().append("\n").append(inputName(p)).append("\n").append(overview.heading()).append("\n").append(overview.subtitle());
        for(var row:overview.rows())result.append("\n").append(row.label()).append(" ").append(row.value());
        result.append("\n").append(notice(p,model)).append("\n").append(actionExplanation(p));
        if(detailsOpen)for(var line:explanation(p))result.append("\n").append(line);
        return result;
    }
    @Override protected void updateNarrationState(NarrationElementOutput output) {
        super.updateNarrationState(output);output.add(NarratedElementType.HINT,detailsNarration());
    }
}
