package com.gamergaming.taczweaponblueprints.client.designer;

import static org.junit.jupiter.api.Assertions.*;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class DesignerWorkspaceLayoutTest {
    static Stream<int[]> sizes() { return Stream.of(new int[]{320,240},new int[]{427,240},new int[]{640,360},new int[]{720,450},new int[]{960,540},new int[]{1920,1080}); }
    @ParameterizedTest @MethodSource("sizes") void allPanelCombinationsKeepCanvasAndLibraryUsable(int[] size) {
        for(boolean library:new boolean[]{true,false})for(boolean selected:new boolean[]{true,false})for(boolean problems:new boolean[]{true,false}){
            var layout=DesignerWorkspaceLayout.create(size[0],size[1],library,selected,problems);
            assertTrue(layout.canvasWidth()>=196);
            assertTrue(layout.canvasHeight()>=60);
            assertTrue(layout.canvasX()+layout.canvasWidth()<=size[0]-layout.inspector());
            assertTrue(82+(layout.libraryRows()-1)*22+20<=layout.libraryPagerY());
            assertEquals(0,selected?0:layout.inspector()+layout.lower());
            if(problems)assertEquals(0,layout.lower());
            if(size[0]<700)assertEquals(0,layout.inspector());
        }
    }
}
