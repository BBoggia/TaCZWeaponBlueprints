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
    /** Keep branch titles within their own footprint; omit them when no clear slot exists. */
    static Optional<Rect> landmark(Rect view, Rect region, int width, int height, List<Rect> occupied) {
        return landmark(view, region, width, height, occupied, 0);
    }
    static Optional<Rect> landmark(Rect view, Rect region, int width, int height, List<Rect> occupied, int clearance) {
        int left = Math.max(view.x() + 2, region.x() + 3) + clearance;
        int right = Math.min(view.right() - 2, region.right() - 3) - clearance;
        int top = Math.max(view.y() + 2, region.y() + 3) + clearance;
        int bottom = Math.min(view.bottom() - 2, region.bottom() - 3) - clearance;
        if (width <= 0 || height <= 0 || right - left < width || bottom - top < height) return Optional.empty();
        for (int y : new int[]{top, top + height + 4, top + 2 * (height + 4)}) {
            if (y + height > bottom) continue;
            for (int x : new int[]{left, left + (right - left - width) / 2, right - width}) {
                var b = new Rect(x, y, width, height);
                if (clear(b, occupied, clearance)) return Optional.of(b);
            }
        }
        return Optional.empty();
    }
    static boolean clear(Rect box, List<Rect> occupied, int gap) {
        return occupied.stream().noneMatch(o -> box.x() - gap < o.right() && box.right() + gap > o.x()
                && box.y() - gap < o.bottom() && box.bottom() + gap > o.y());
    }
    private ResearchTreeAnnotationPlacement() { }
}
