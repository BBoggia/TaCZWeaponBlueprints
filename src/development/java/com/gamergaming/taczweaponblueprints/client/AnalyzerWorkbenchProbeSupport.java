package com.gamergaming.taczweaponblueprints.client;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import com.gamergaming.taczweaponblueprints.menu.BlueprintRecyclerPreview;

/** Native probe access lives outside the shipped screen and does not assume button coordinates. */
final class AnalyzerWorkbenchProbeSupport {
    private AnalyzerWorkbenchProbeSupport() { }

    static Button button(BlueprintRecyclerScreen screen, String name) throws ReflectiveOperationException {
        var field = BlueprintRecyclerScreen.class.getDeclaredField(name);
        field.setAccessible(true);
        return (Button) field.get(screen);
    }

    static List<Component> facts(BlueprintRecyclerScreen screen) throws ReflectiveOperationException {
        var preview = screen.getMenu().preview();
        var model = BlueprintRecyclerScreenModel.from(preview, false);
        var method = BlueprintRecyclerScreen.class.getDeclaredMethod("explanation", BlueprintRecyclerPreview.class);
        method.setAccessible(true);
        @SuppressWarnings("unchecked") var details = (List<Component>) method.invoke(screen, preview);
        var lines = new ArrayList<>(details);
        var notice = BlueprintRecyclerScreen.class.getDeclaredMethod("notice", BlueprintRecyclerPreview.class, BlueprintRecyclerScreenModel.class);
        notice.setAccessible(true);
        lines.add((Component) notice.invoke(screen, preview, model));
        var overview = AnalyzerWorkbenchContent.overview(preview, model.primaryAction());
        lines.add(overview.heading()); lines.add(overview.subtitle());
        for (var row : overview.rows()) lines.add(row.label().copy().append(" ").append(row.value()));
        return List.copyOf(lines);
    }

    static void openDetails(BlueprintRecyclerScreen screen) throws ReflectiveOperationException {
        var field = BlueprintRecyclerScreen.class.getDeclaredField("detailsOpen"); field.setAccessible(true);
        if (!(boolean) field.get(screen)) {
            var button = button(screen, "detailsButton");
            screen.mouseClicked(button.getX() + 2, button.getY() + 2, 0);
            screen.mouseReleased(button.getX() + 2, button.getY() + 2, 0);
        }
    }
}
