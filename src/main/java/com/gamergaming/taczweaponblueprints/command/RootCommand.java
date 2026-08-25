package com.gamergaming.taczweaponblueprints.command;

import com.gamergaming.taczweaponblueprints.command.compat.tacz.ClearRecipesCommand;
import com.gamergaming.taczweaponblueprints.command.compat.tacz.ReloadRecipesCommand;
import com.gamergaming.taczweaponblueprints.command.sub.BlueprintLootCommand;
import com.gamergaming.taczweaponblueprints.command.sub.BlueprintProgressionCommand;
import com.gamergaming.taczweaponblueprints.command.sub.BlueprintResearchCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;

public class RootCommand {
    public static final String COMMAND_NAME = "gg";

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(COMMAND_NAME)
                .requires((source -> source.hasPermission(2)));

        root.then(ClearRecipesCommand.get());
        root.then(ReloadRecipesCommand.get());
        root.then(BlueprintLootCommand.get());
        root.then(BlueprintProgressionCommand.get());
        root.then(BlueprintResearchCommand.get());

        dispatcher.register(root);
    }
    
}
