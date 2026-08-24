package com.gamergaming.taczweaponblueprints.command.compat.tacz;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;
import com.gamergaming.taczweaponblueprints.resource.BlueprintDataManager;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

public class ReloadRecipesCommand {

    private static final String RELOAD_RECIPES_NAME = "reloadRecipes";

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        LiteralArgumentBuilder<CommandSourceStack> reloadRecipes = Commands.literal(RELOAD_RECIPES_NAME);
        reloadRecipes.executes(ReloadRecipesCommand::reloadAllRecipes);
        return reloadRecipes;
    }

    private static int reloadAllRecipes(CommandContext<CommandSourceStack> context) {
        if (!BlueprintDataManager.SERVER.initialize(context.getSource().getServer())) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.reload_tacz_recipes.rebuild_failed"));
            return 0;
        }
        context.getSource().getServer().getPlayerList().getPlayers()
                .forEach(NetworkHandler::syncAllPlayerData);
        int blueprintCount = BlueprintDataManager.SERVER.getAllBlueprints().size();
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "commands.taczweaponblueprints.reload_tacz_recipes.success",
                        blueprintCount),
                true);
        return Command.SINGLE_SUCCESS;
    }
}
