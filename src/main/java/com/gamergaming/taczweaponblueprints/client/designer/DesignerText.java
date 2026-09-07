package com.gamergaming.taczweaponblueprints.client.designer;

import net.minecraft.network.chat.Component;

final class DesignerText {
    static String tr(String key, Object... arguments) {
        return Component.translatable("gui.taczweaponblueprints.designer." + key, arguments).getString();
    }
    private DesignerText() {}
}
