package com.gamergaming.taczweaponblueprints.client;

import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

class ResearchSheetInspectorBodyTest {
    /** Fixed-width metrics let the real scrolling widget lay out text without loading game fonts. */
    private Font font() {
        return new Font(ignored -> null, false) {
            @Override public int width(FormattedText text) { return text.getString().length()*6; }
            @Override public List<FormattedCharSequence> split(FormattedText text, int width) {
                int count = Math.max(1,width/6); String value=text.getString();
                var lines=new ArrayList<FormattedCharSequence>();
                for(int i=0;i<value.length();i+=count) lines.add(Component.literal(value.substring(i,Math.min(value.length(),i+count))).getVisualOrderText());
                return lines;
            }
        };
    }
    @Test void longContentScrollsToTheEndAndClampsWhenContentShrinks() {
        var body=new ResearchTreeInspectorBody(font(), ignored->{}, ignored->{}, ignored->{});
        body.bounds(new ResearchTreeScreenLayout.Rect(10,34,132,170));
        var rows=new ArrayList<ResearchTreeInspectorRow>();
        for(int i=0;i<25;i++) rows.add(ResearchTreeInspectorRow.text(ResearchTreeInspectorRow.Kind.EXPLANATION,
                Component.literal("Long material and prerequisite name " + i),1));
        body.content(rows,0);
        assertTrue(body.maximumScroll()>170);
        body.wheel(-1); assertTrue(body.scroll()>0);
        body.setFocused(true); assertTrue(body.keyPressed(269,0,0)); // End
        assertEquals(body.maximumScroll(),body.scroll());
        body.content(List.of(rows.get(0)),body.scroll()); assertEquals(0,body.scroll());
    }
    @Test void keyboardCanReachBothMaterialPageControls() {
        var page=new AtomicInteger();
        var body=new ResearchTreeInspectorBody(font(),ignored->{},ignored->{},page::set);
        body.bounds(new ResearchTreeScreenLayout.Rect(10,34,132,13));
        body.content(List.of(ResearchTreeInspectorRow.materialPage(Component.literal("Previous materials"),-1,1),
                ResearchTreeInspectorRow.materialPage(Component.literal("Next materials"),1,1)),0);
        body.setFocused(true);
        body.keyPressed(264,0,0); body.keyPressed(257,0,0); assertEquals(-1,page.get());
        body.keyPressed(264,0,0); body.keyPressed(257,0,0); assertEquals(1,page.get());
        assertTrue(body.scroll()>0);
    }
}
