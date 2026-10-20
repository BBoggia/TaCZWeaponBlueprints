package com.gamergaming.taczweaponblueprints.init;

import net.neoforged.fml.common.EventBusSubscriber;

import com.gamergaming.taczweaponblueprints.TaCZWeaponBlueprints;
import com.gamergaming.taczweaponblueprints.command.RootCommand;

import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;

@EventBusSubscriber(modid = TaCZWeaponBlueprints.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class CommandRegistry {
    private CommandRegistry() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        RootCommand.register(event.getDispatcher());
    }
}
