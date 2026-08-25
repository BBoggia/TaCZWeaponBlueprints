package com.gamergaming.taczweaponblueprints.client;

import org.lwjgl.glfw.GLFW;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only registration for the rebindable Journal control. */
@Mod.EventBusSubscriber(
        modid = TaCZWeaponBlueprints.MODID,
        value = Dist.CLIENT,
        bus = Mod.EventBusSubscriber.Bus.MOD)
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
