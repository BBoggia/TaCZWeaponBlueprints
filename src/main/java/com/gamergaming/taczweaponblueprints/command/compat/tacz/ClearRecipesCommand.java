package com.gamergaming.taczweaponblueprints.command.compat.tacz;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.gamergaming.taczweaponblueprints.init.ModCapabilities;
import com.gamergaming.taczweaponblueprints.network.NetworkHandler;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class ClearRecipesCommand {

    private static final String CLEAR_RECIPES_NAME = "clearRecipes";

    public static LiteralArgumentBuilder<CommandSourceStack> get() {
        LiteralArgumentBuilder<CommandSourceStack> clearRecipes = Commands.literal(CLEAR_RECIPES_NAME);
        clearRecipes.executes(ClearRecipesCommand::clearAllRecipes);
        return clearRecipes;
    }

    private static int clearAllRecipes(CommandContext<CommandSourceStack> context) {
        if (!(context.getSource().getEntity() instanceof ServerPlayer serverPlayer)) {
            context.getSource().sendFailure(Component.translatable(
                    "commands.taczweaponblueprints.clear_tacz_recipes.player_only"));
            return 0;
        }

        int clearedRecipes = serverPlayer.getCapability(ModCapabilities.PLAYER_RECIPE_DATA)
                .map(recipeData -> {
                    int count = recipeData.getLearnedRecipes().size();
                    recipeData.clearRecipes();
                    return count;
                })
                .orElse(0);
        NetworkHandler.syncPlayerRecipeData(serverPlayer);
        context.getSource().sendSuccess(
                () -> Component.translatable(
                        "commands.taczweaponblueprints.clear_tacz_recipes.success",
                        clearedRecipes),
                false);
        return Command.SINGLE_SUCCESS;
    }
}
