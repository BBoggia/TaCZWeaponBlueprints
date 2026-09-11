package com.gamergaming.taczweaponblueprints.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import com.gamergaming.taczweaponblueprints.client.ResearchTreeScreenLayout.Rect;

/** Scene-owned anchors follow their branch through camera motion. Hidden labels need extra clearance to return. */
final class ResearchTreeLandmarkPlacement {
    private static final int ENTER_CLEARANCE = 3;
    private record Anchor(double x, double y, int width, int height, boolean visible) { }
    private final Map<String, Anchor> anchors = new HashMap<>();

    void hide(String id) {
        var old = anchors.get(id);
        if (old != null && old.visible()) anchors.put(id, new Anchor(old.x(), old.y(), old.width(), old.height(), false));
    }
    void hideAll() { List.copyOf(anchors.keySet()).forEach(this::hide); }

    Optional<Rect> place(String id, Rect view, Rect footprint, int originX, int originY, double scale,
            int width, int height, List<Rect> occupied) {
        var old = anchors.get(id);
        if (old != null && old.width() == width && old.height() == height) {
            int x = (int) Math.round(originX + old.x() * scale), y = (int) Math.round(originY + old.y() * scale);
            int gap = old.visible() ? 0 : ENTER_CLEARANCE;
            if (x >= Math.max(view.x()+2, footprint.x()+3)+gap
                    && y >= Math.max(view.y()+2, footprint.y()+3)+gap
                    && x+width <= Math.min(view.right()-2, footprint.right()-3)-gap
                    && y+height <= Math.min(view.bottom()-2, footprint.bottom()-3)-gap) {
                var box = new Rect(x, y, width, height);
                if (ResearchTreeAnnotationPlacement.clear(box, occupied, gap)) {
                    anchors.put(id, new Anchor(old.x(), old.y(), width, height, true));
                    return Optional.of(box);
                }
            }
        }
        var next = ResearchTreeAnnotationPlacement.landmark(view, footprint, width, height, occupied, ENTER_CLEARANCE);
        if (next.isPresent()) {
            var b = next.orElseThrow();
            anchors.put(id, new Anchor((b.x()-originX)/scale, (b.y()-originY)/scale, width, height, true));
        } else hide(id);
        return next;
    }
}
