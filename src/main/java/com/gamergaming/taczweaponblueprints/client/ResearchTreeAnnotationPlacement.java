package com.gamergaming.taczweaponblueprints.client;

import java.util.List;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeScreenLayout.Rect;

/** Fixed-size annotations stay inside the canvas and never cover a higher-priority label. */
final class ResearchTreeAnnotationPlacement {
    static Optional<Rect> place(Rect view, int x, int y, int width, int height, List<Rect> occupied) {
        if (width <= 0 || height <= 0 || width > view.width()-4 || height > view.height()-4) return Optional.empty();
        int left = Math.max(view.x()+2,Math.min(x,view.right()-width-2));
        int top = Math.max(view.y()+2,Math.min(y,view.bottom()-height-2));
        for (int offset : new int[]{0,height+3,-height-3,2*(height+3)}) {
            if (top+offset < view.y() || top+offset+height > view.bottom()) continue;
            var b = new Rect(left,top+offset,width,height);
            if (view.contains(b) && occupied.stream().noneMatch(b::overlaps)) return Optional.of(b);
        }
        return Optional.empty();
    }
    private ResearchTreeAnnotationPlacement() { }
}
