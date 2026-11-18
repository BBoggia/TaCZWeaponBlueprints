package com.gamergaming.taczweaponblueprints.client.settings;

import java.util.*;
import com.gamergaming.taczweaponblueprints.settings.*;
import com.gamergaming.taczweaponblueprints.compat.fzzy_config.BlueprintConfig;
import me.fzzyhmstrs.fzzy_config.screen.PopupWidgetScreen;
import me.fzzyhmstrs.fzzy_config.validation.ValidatedField;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.components.*;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** Category navigation and original Fzzy editors over one detached server draft. */
public final class AdvancedSettingsScreen extends PopupWidgetScreen {
    private final Screen parent;
    private SettingsEditSession session;
    private BlueprintConfig editor;
    private SettingsCatalog.Category category;
    private String query = "";
    private int page;
    private final Map<SettingsCatalog.Category,Integer> pages = new EnumMap<>(SettingsCatalog.Category.class);
    private EditBox search;
    private Button apply, refresh;
    private SettingsTransaction.Status lastStatus;
    private String focusedField;
    private boolean rebuilding;
    private final Map<AbstractWidget, SettingsCatalog.Entry> editors = new LinkedHashMap<>();
    public AdvancedSettingsScreen(Screen parent) { super(text("advanced")); this.parent = parent; }
    private static Component text(String key) { return SettingsLandingScreen.text(key); }
    @Override protected void init() {
        // A Quick Setup edit may have changed this same draft since this screen was last shown.
        ClientSettingsAccess.draft().ifPresent(this::bind);
        rebuild();
    }
    void navigate(SettingsCatalog.Category destination, String searchText) {
        rememberPage();
        category=destination; query=searchText; page=destination==null?0:pages.getOrDefault(destination,0);
    }
    @SuppressWarnings("unchecked") private ValidatedField<Object> field(String id) {
        try { return (ValidatedField<Object>) BlueprintConfig.class.getField(id).get(editor); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException(e); }
    }
    private void bind(SettingsEditSession next) {
        session = next; editor = FzzySettingsAdapter.fromValues(next.preview());
        for (var entry : SettingsCatalog.entries().values()) {
            if (!entry.supported()) continue;
            field(entry.field()).setTranslatableEntryKey(entry.labelKey());
            field(entry.field()).addListener(value -> {
                if (!rebuilding && writable()) {
                    session.set(entry.field(), FzzySettingsAdapter.captureField(editor, entry.field()));
                }
            });
        }
    }
    private List<SettingsCatalog.Entry> matches() {
        String needle = query.strip().toLowerCase(Locale.ROOT);
        return SettingsCatalog.entries().values().stream().filter(SettingsCatalog.Entry::supported)
                .filter(e -> needle.isEmpty() ? e.category()==category : SettingsSearch.matches(e,query,key->Component.translatable(key).getString())).toList();
    }
    private Button button(Component label,int x,int y,int w,Runnable action) {
        return addRenderableWidget(Button.builder(label,b->action.run()).bounds(x,y,w,20).build());
    }
    private void rebuild() {
        rebuilding = true;
        boolean searchFocused = search != null && search.isFocused();
        int cursor = search == null ? query.length() : search.getCursorPosition();
        clearWidgets(); editors.clear();
        int left=16, bodyWidth=width-32;
        search = new EditBox(font,left,43,bodyWidth-92,20,text("search")); search.setMaxLength(128); search.setHint(text("search")); search.setValue(query);
        if (searchFocused) { search.setCursorPosition(Math.min(cursor, query.length())); search.setHighlightPos(search.getCursorPosition()); }
        search.setResponder(s -> { query=s;page=0;rebuild(); }); addRenderableWidget(search);
        if (searchFocused) setFocused(search);
        refresh=button(text(ClientSettingsAccess.needsReview()?"review":"refresh"),width-100,43,84,()->{if(ClientSettingsAccess.pending())return;if(ClientSettingsAccess.needsReview())minecraft.setScreen(new SettingsReviewScreen(this));else ClientSettingsAccess.refresh();});
        if (session != null) {
            if (category == null && query.isBlank()) {
                int i=0;
                for (var c : SettingsCatalog.Category.values()) {
                    if (c==SettingsCatalog.Category.PERSONAL || c==SettingsCatalog.Category.LEGACY) continue;
                    int col=i%2,row=i/2; i++;
                    button(text("category."+c.name().toLowerCase(Locale.ROOT)),left+col*(bodyWidth/2+2),73+row*24,bodyWidth/2-2,()->{category=c;page=pages.getOrDefault(c,0);rebuild();});
                }
            } else {
                var entries=matches(); int rows=Math.max(1,(height-145)/36);
                page=Math.min(page,Math.max(0,(entries.size()-1)/rows));
                for(int i=page*rows;i<Math.min(entries.size(),(page+1)*rows);i++) {
                    var entry=entries.get(i); int y=72+(i-page*rows)*36;
                    Component label=Component.translatable(entry.labelKey());
                    var labelButton=button(Component.literal(font.plainSubstrByWidth(label.getString(),Math.max(50,bodyWidth-166))),left,y,Math.max(60,bodyWidth-160),()->{if(!query.isBlank()){category=entry.category();query="";page=0;int at=matches().indexOf(entry);page=Math.max(0,at/Math.max(1,(height-145)/36));focusedField=entry.field();rebuild();}});
                    String explanation=Component.translatable(entry.labelKey()+".desc").getString();
                    if(!entry.condition().active(editor)) explanation += "\n"+Component.translatable(entry.condition().reasonKey()).getString();

                    labelButton.setTooltip(Tooltip.create(Component.literal(label.getString()+"\n"+explanation)));
                    var widget=SettingsEditors.create(field(entry.field()),()->{if(!rebuilding && writable())session.set(entry.field(),FzzySettingsAdapter.captureField(editor,entry.field()));});
                    widget.setX(width-166);widget.setY(y);widget.setWidth(150);
                    widget.active = writable() && entry.condition().active(editor);
                    var guarded=new SettingsFieldWidget(widget,()->writable() && entry.condition().active(editor), label);
                    addRenderableWidget(guarded); editors.put(guarded,entry);
                    if(!searchFocused && entry.field().equals(focusedField))setFocused(guarded);
                }
                button(text("previous"),left,height-66,80,()->{page=Math.max(0,page-1);rememberPage();rebuild();}).active=page>0;
                button(Component.literal((page+1)+" / "+Math.max(1,(entries.size()+rows-1)/rows)),left+84,height-66,80,()->{}).active=false;
                button(text("next"),left+168,height-66,80,()->{page++;rememberPage();rebuild();}).active=(page+1)*rows<entries.size();
            }
        }
        button(Component.translatable("gui.back"),left,height-29,80,()->{if(category!=null || !query.isBlank()){rememberPage();category=null;query="";page=0;rebuild();}else onClose();});
        apply=button(text("apply"),width-180,height-29,80,()->{if(writable() && session.dirty())ClientSettingsAccess.apply();});
        button(text("discard"),width-96,height-29,80,()->confirmDiscard(false));
        rebuilding=false;
        apply.active=writable() && session.dirty();
        refresh.active=!ClientSettingsAccess.pending();
    }
    private void rememberPage() { if(category!=null && query.isBlank())pages.put(category,page); }
    private boolean writable() {
        return session!=null && session==ClientSettingsAccess.draft().orElse(null) && session.baseline().editable() && !ClientSettingsAccess.pending()
                && ClientSettingsAccess.status().orElse(null)==SettingsTransaction.Status.READY && !ClientSettingsAccess.needsReview();
    }
    @Override public void tick() {
        if(minecraft.getConnection()==null) { minecraft.setScreen(parent);return; }
        var next=ClientSettingsAccess.draft().orElse(null);
        var status=ClientSettingsAccess.status().orElse(null);
        if(next!=null && next!=session){bind(next);rebuild();}
        if(status==SettingsTransaction.Status.APPLIED || status==SettingsTransaction.Status.NO_CHANGES)ClientSettingsAccess.acknowledgeApplied();
        if(status!=lastStatus){lastStatus=status;rebuild();}
        apply.active=writable() && session.dirty();
        refresh.active=!ClientSettingsAccess.pending();
        editors.forEach((w,e)->{w.active=writable() && e.condition().active(editor);if(w.isFocused())focusedField=e.field();});
    }
    private void confirmDiscard(boolean close) {
        if(ClientSettingsAccess.pending())return;
        if(session==null || !session.dirty()){ClientSettingsAccess.discard();if(close)minecraft.setScreen(parent);else{ClientSettingsAccess.open();session=null;editor=null;rebuild();}return;}
        minecraft.setScreen(new ConfirmScreen(ok->{minecraft.setScreen(this);if(ok){ClientSettingsAccess.discard();session=null;editor=null;if(close)minecraft.setScreen(parent);else{ClientSettingsAccess.open();rebuild();}}},text("discard_title"),text("discard_body"),text("discard"),Component.translatable("gui.cancel")));
    }
    @Override public void onClose() {
        if (parent instanceof QuickSetupScreen) minecraft.setScreen(parent);
        else confirmDiscard(true);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void renderContents(GuiGraphics g,int mx,int my,float dt) {
        g.fill(0,0,width,height,0xEE101820);
        super.renderContents(g,mx,my,dt);
        g.drawCenteredString(font,category==null?title:text("category."+category.name().toLowerCase(Locale.ROOT)),width/2,12,0xFFFFFF);
        Component state = ClientSettingsAccess.pending()?text("loading"):ClientSettingsAccess.status().<Component>map(s->Component.translatable(s.translationKey())).orElse(text("loading"));
        if(!ClientSettingsAccess.pending() && session!=null && session.dirty() && ClientSettingsAccess.status().orElse(null)==SettingsTransaction.Status.READY)state=Component.translatable("settings.taczweaponblueprints.ui.pending",session.proposedChanges().size());
        if(session!=null && !session.baseline().editable())state=text("read_only");
        g.drawCenteredString(font,Component.literal(font.plainSubstrByWidth(state.getString(),width-32)),width/2,28,0xBBBBBB);
        if (my >= 28 && my < 39 && font.width(state) > width - 32) g.renderTooltip(font, font.split(state, width - 40), mx, my);
        editors.forEach((w,e)->{if(!e.condition().active(editor))g.drawString(font,font.plainSubstrByWidth(Component.translatable(e.condition().reasonKey()).getString(),width-32),16,w.getY()+23,0xAAAAAA);});
        if(category==SettingsCatalog.Category.INTEGRATIONS && query.isBlank())g.drawString(font,font.plainSubstrByWidth(text("integration_hint").getString(),width-32),16,height-80,0xAAAAAA);
    }
}
