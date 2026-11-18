package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

class ResearchSheetInputRoutingTest {
    /** Exercise real screen dispatch without constructing a registry-backed menu or Minecraft window. */
    private TechTreeSheetScreen screen(ResearchTreeInspectorBody body) throws Exception {
        Field unsafeField=sun.misc.Unsafe.class.getDeclaredField("theUnsafe"); unsafeField.setAccessible(true);
        var unsafe=(sun.misc.Unsafe)unsafeField.get(null);
        var screen=(TechTreeSheetScreen)unsafe.allocateInstance(TechTreeSheetScreen.class);
        set(screen,"inspectorBody",body); set(screen,"confirmation",new ResearchPurchaseConfirmation());
        set(screen,"scrollY",40);
        return screen;
    }
    private static void set(Object target,String name,Object value) throws Exception {
        var field=target.getClass().getDeclaredField(name); field.setAccessible(true); field.set(target,value);
    }
    private ResearchTreeInspectorBody body() {
        var body=new ResearchTreeInspectorBody(font(),ignored->{},ignored->{},ignored->{});
        return body;
    }
    private Font font() {
        return new Font(ignored->null,false) {
            @Override public int width(FormattedText text) { return 6; }
            @Override public List<FormattedCharSequence> split(FormattedText text,int width) { return List.of(FormattedCharSequence.EMPTY); }
        };
    }
    @Test void inspectorWheelKeyboardAndScrollbarStayInsideTheInspector() throws Exception {
        var body=body(); body.bounds(new ResearchTreeScreenLayout.Rect(200,34,132,26));
        body.content(java.util.Collections.nCopies(20,ResearchTreeInspectorRow.text(
                ResearchTreeInspectorRow.Kind.EXPLANATION,Component.literal("requirement"),1)),0);
        var screen=screen(body);
        assertTrue(screen.mouseScrolled(220,40,0,-1)); assertTrue(body.scroll()>0);
        var scroll=TechTreeSheetScreen.class.getDeclaredField("scrollY");scroll.setAccessible(true);
        assertEquals(40,scroll.getInt(screen),"inspector wheel must not pan the tree");
        body.setFocused(true); assertTrue(screen.keyPressed(269,0,0));
        assertEquals(body.maximumScroll(),body.scroll());
        body.onClick(330,40); assertTrue(body.draggingScrollbar()); screen.setDragging(true);
        assertTrue(screen.mouseReleased(330,40,0));
        assertFalse(body.draggingScrollbar()); assertFalse(screen.isDragging());
    }
}
