package com.gamergaming.taczweaponblueprints.client;

import net.neoforged.fml.common.EventBusSubscriber;

import org.lwjgl.glfw.GLFW;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.bus.api.SubscribeEvent;

/** Client-only registration for the rebindable Journal control. */
@EventBusSubscriber(
        modid = TaCZWeaponBlueprints.MODID,
        value = Dist.CLIENT,
        bus = EventBusSubscriber.Bus.MOD)
public final class BlueprintJournalKeyMappings {
    public static final String CATEGORY = "key.categories.taczweaponblueprints";
    public static final String OPEN_JOURNAL_KEY = "key.taczweaponblueprints.open_journal";

    public static final KeyMapping OPEN_JOURNAL = new KeyMapping(
            OPEN_JOURNAL_KEY,
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY);

    private BlueprintJournalKeyMappings() {
    }

    @SubscribeEvent
    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN_JOURNAL);
    }
}
